package com.personal.mise.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.personal.mise.ai.AiSettings
import com.personal.mise.data.CookLog
import com.personal.mise.data.RecipeRepository
import com.personal.mise.data.RecipeStamp
import com.personal.mise.image.ImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom

/**
 * 共享厨房：同一个厨房里的手机共用一份菜谱。
 *
 * 云端是 kitchens/{邀请码}/recipes/{uid}（每道菜一个文档）、kitchens/{邀请码}/today/{uid}（今日菜单）、
 * kitchens/{邀请码}/history/{菜谱 uid_日期}（做菜记录）、kitchens/{邀请码}/images/{文件名}（图片）
 * 和 kitchens/{邀请码}/aiKeys/{服务商}（AI 的 API Key）。本地和云端任何一边变了，
 * 就逐条比较 updatedAt，新的覆盖旧的。Firestore 自带离线缓存，没网时的改动联网后补传。
 */
class KitchenSync(
    context: Context,
    private val repository: RecipeRepository,
    private val images: ImageStore,
    private val aiSettings: AiSettings,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("kitchen", Context.MODE_PRIVATE)
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _kitchenCode = MutableStateFlow(prefs.getString(KEY_CODE, null))
    /** 当前加入的厨房邀请码；没加入为 null */
    val kitchenCode: StateFlow<String?> = _kitchenCode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** 最近一次同步出错的原因；同步正常时为 null */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start() {
        scope.launch {
            _kitchenCode.collectLatest { code ->
                _error.value = null
                if (code != null) syncForever(code)
            }
        }
    }

    /** 新建一个厨房并加入，返回邀请码 */
    suspend fun create(): String {
        val uid = signIn()
        val code = KitchenCodes.generate(SecureRandom())
        remote {
            firestore.collection(KITCHENS).document(code)
                .set(mapOf("members" to listOf(uid), "createdAt" to FieldValue.serverTimestamp()))
                .await()
        }
        setCode(code)
        return code
    }

    /** 用邀请码加入别人的厨房 */
    suspend fun join(input: String) {
        val code = KitchenCodes.normalize(input) ?: throw KitchenException("邀请码是 ${KitchenCodes.LENGTH} 位字母和数字")
        val uid = signIn()
        remote {
            firestore.collection(KITCHENS).document(code).update("members", FieldValue.arrayUnion(uid)).await()
        }
        setCode(code)
    }

    /** 退出只是不再同步，本机的菜谱都留着 */
    fun leave() = setCode(null)

    private fun setCode(code: String?) {
        prefs.edit().putString(KEY_CODE, code).apply()
        _kitchenCode.value = code
    }

    private suspend fun signIn(): String = remote {
        auth.currentUser?.uid ?: requireNotNull(auth.signInAnonymously().await().user).uid
    }

    /** 联网操作：超时或出错都转成能直接给人看的提示 */
    private suspend fun <T> remote(block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw KitchenException("连不上服务器，检查一下网络")
    } catch (e: FirebaseFirestoreException) {
        throw KitchenException(
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> "邀请码不对"
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "没有权限：邀请码不对，或者 Firestore 规则还没配好"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "连不上服务器，检查一下网络"
                else -> "出错了：${e.message}"
            }
        )
    } catch (e: FirebaseAuthException) {
        throw KitchenException("登录失败：${e.message}")
    }

    private suspend fun syncForever(code: String) {
        while (true) {
            try {
                signIn()
                sync(code, firestore.collection(KITCHENS).document(code))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                _error.value = (e as? KitchenException)?.message ?: e.message ?: e.javaClass.simpleName
            }
            delay(RETRY_MS)
        }
    }

    /** 菜谱、今日菜单、做菜记录、图片、AI Key 各自同步，任何一个出错就整体重来 */
    private suspend fun sync(code: String, kitchen: DocumentReference) {
        coroutineScope {
            launch {
                syncCollection<SyncedRecipe>(
                    collection = kitchen.collection(RECIPES),
                    localStamps = repository.observeStamps(),
                    parse = { uid, data -> SyncedRecipe.fromMap(uid, data) },
                    updatedAt = { it.updatedAt },
                    apply = { repository.applyRemote(it) },
                    load = { uid -> repository.loadForSync(uid)?.toMap() },
                )
            }
            launch {
                syncCollection<SyncedTodayEntry>(
                    collection = kitchen.collection(TODAY),
                    localStamps = repository.observeTodayStamps(),
                    parse = { uid, data -> SyncedTodayEntry.fromMap(uid, data) },
                    updatedAt = { it.updatedAt },
                    apply = { repository.applyRemoteToday(it) },
                    load = { uid -> repository.loadTodayForSync(uid)?.toMap() },
                )
            }
            launch {
                syncCollection<CookLog>(
                    collection = kitchen.collection(HISTORY),
                    localStamps = repository.observeCookLogStamps(),
                    parse = { uid, data -> SyncedCookLog.fromMap(uid, data) },
                    updatedAt = { it.updatedAt },
                    apply = { repository.applyRemoteCookLog(it) },
                    load = { uid -> repository.loadCookLogForSync(uid)?.let(SyncedCookLog::toMap) },
                )
            }
            launch { syncImages(code, kitchen.collection(IMAGES)) }
            launch {
                syncCollection<SyncedAiKey>(
                    collection = kitchen.collection(AI_KEYS),
                    localStamps = aiSettings.observeKeyStamps(),
                    parse = { id, data -> SyncedAiKey.fromMap(id, data) },
                    updatedAt = { it.updatedAt },
                    apply = { aiSettings.applyRemoteKey(it.provider, it.key, it.updatedAt) },
                    load = { id -> SyncedAiKey.local(aiSettings, id)?.toMap() },
                )
            }
        }
    }

    /**
     * 图片：kitchens/{邀请码}/images/{文件名}，一张图一个文档。文件名是 UUID、内容不会变，
     * 所以不用比新旧：本机有、还没传过的就上传，菜谱用到但本机没有的就下载。
     * 不监听整个集合，免得把所有图片都下载一遍。对方的图片可能还没传上来，缺图时隔一会儿再试。
     */
    @OptIn(FlowPreview::class)
    private suspend fun syncImages(code: String, collection: CollectionReference) {
        repository.observeImagePaths().debounce(LOCAL_DEBOUNCE_MS).collectLatest { paths ->
            var wait = IMAGE_RETRY_FIRST_MS
            while (syncImagesOnce(code, collection, paths) > 0) {
                delay(wait)
                wait = (wait * 3).coerceAtMost(IMAGE_RETRY_MAX_MS)
            }
        }
    }

    /** 返回还没办好的图片数 */
    private suspend fun syncImagesOnce(code: String, collection: CollectionReference, paths: List<String>): Int {
        val key = KEY_UPLOADED_PREFIX + code
        val done = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        var pending = 0
        for (path in paths) {
            if (!ImageStore.isSyncPath(path)) continue
            val name = path.removePrefix("${ImageStore.DIR}/")
            val doc = collection.document(name)
            val ok = try {
                withTimeout(IMAGE_TIMEOUT_MS) {
                    if (images.file(path).isFile) {
                        if (name in done) return@withTimeout true
                        val bytes = images.bytesForSync(path) ?: return@withTimeout true // 读不出来的坏图跳过
                        // 不等服务器确认：写入先进本地缓存，没网时联网后自动补传；等的话每次重试都会多排一份
                        doc.set(mapOf("data" to Blob.fromBytes(bytes))).addOnFailureListener { e ->
                            Log.w(TAG, "image upload failed: $name", e)
                            forgetDone(key, name)
                        }
                        true
                    } else {
                        val bytes = doc.get().await().getBlob("data")?.toBytes()
                        bytes != null && images.saveSynced(path, bytes)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "image sync failed: $name", e)
                false
            }
            if (ok) done += name else pending++
        }
        prefs.edit().putStringSet(key, done).apply()
        return pending
    }

    /** 上传最终失败时，下次再传 */
    private fun forgetDone(key: String, name: String) {
        val done = prefs.getStringSet(key, emptySet()).orEmpty() - name
        prefs.edit().putStringSet(key, done).apply()
    }

    /**
     * 一个集合里每个文档对应本地一条记录（文档 id 是菜谱 uid，做菜记录是记录的 uid）。
     * 本地或云端有变化就逐条比较 updatedAt，新的覆盖旧的。
     */
    @OptIn(FlowPreview::class)
    private suspend fun <T> syncCollection(
        collection: CollectionReference,
        localStamps: Flow<List<RecipeStamp>>,
        parse: (String, Map<String, Any?>) -> T?,
        updatedAt: (T) -> Long,
        apply: suspend (T) -> Unit,
        load: suspend (String) -> Map<String, Any?>?,
    ) {
        combine(snapshots(collection), localStamps.debounce(LOCAL_DEBOUNCE_MS)) { snap, local -> snap to local }
            .conflate()
            .collect { (snap, local) ->
                val remote = snap.documents.mapNotNull { doc -> doc.data?.let { parse(doc.id, it) }?.let { doc.id to it } }.toMap()
                val mine = local.associate { it.uid to it.updatedAt }

                remote.forEach { (uid, r) ->
                    val t = mine[uid]
                    if (t == null || updatedAt(r) > t) apply(r)
                }

                // 只有确定看到的是服务器上的数据才上传，免得拿旧缓存覆盖对方刚改的
                if (snap.metadata.isFromCache) return@collect
                _error.value = null
                mine.forEach { (uid, t) ->
                    val theirs = remote[uid]
                    if (theirs == null || t > updatedAt(theirs)) {
                        val data = load(uid) ?: return@forEach
                        // 不等服务器确认：写入先进本地缓存，没网时联网后自动补传
                        collection.document(uid).set(data)
                            .addOnFailureListener { e -> _error.value = "上传失败：${e.message}" }
                    }
                }
            }
    }

    private fun snapshots(collection: CollectionReference): Flow<QuerySnapshot> = callbackFlow {
        val registration = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) close(e) else if (snap != null) trySend(snap)
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "KitchenSync"
        const val KEY_CODE = "code"
        const val KITCHENS = "kitchens"
        const val RECIPES = "recipes"
        const val TODAY = "today"
        const val IMAGES = "images"
        const val HISTORY = "history"
        const val AI_KEYS = "aiKeys"
        const val KEY_UPLOADED_PREFIX = "images_done_"
        const val IMAGE_TIMEOUT_MS = 60_000L
        const val IMAGE_RETRY_FIRST_MS = 5_000L
        const val IMAGE_RETRY_MAX_MS = 5 * 60_000L
        const val TIMEOUT_MS = 15_000L
        const val RETRY_MS = 30_000L
        const val LOCAL_DEBOUNCE_MS = 500L
    }
}

class KitchenException(message: String) : Exception(message)

object KitchenCodes {
    const val LENGTH = 8

    /** 去掉了容易看错的 0/O、1/I/L */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: java.util.Random): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** 去空格、转大写；格式不对返回 null */
    fun normalize(input: String): String? {
        val code = input.filterNot { it.isWhitespace() || it == '-' }.uppercase()
        return code.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }
}
