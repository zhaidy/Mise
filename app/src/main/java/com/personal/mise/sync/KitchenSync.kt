package com.personal.mise.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.personal.mise.data.RecipeRepository
import com.personal.mise.data.RecipeStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
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
 * 云端是 kitchens/{邀请码}/recipes/{uid}，每道菜一个文档。本地和云端任何一边变了，
 * 就逐道菜比较 updatedAt，新的覆盖旧的。Firestore 自带离线缓存，没网时的改动联网后补传。
 */
class KitchenSync(
    context: Context,
    private val repository: RecipeRepository,
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
                sync(firestore.collection(KITCHENS).document(code).collection(RECIPES))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                _error.value = (e as? KitchenException)?.message ?: e.message ?: e.javaClass.simpleName
            }
            delay(RETRY_MS)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun sync(recipes: CollectionReference) {
        combine(snapshots(recipes), repository.observeStamps().debounce(LOCAL_DEBOUNCE_MS)) { snap, local -> snap to local }
            .conflate()
            .collect { (snap, local) -> reconcile(recipes, snap, local) }
    }

    private fun snapshots(recipes: CollectionReference): Flow<QuerySnapshot> = callbackFlow {
        val registration = recipes.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) close(e) else if (snap != null) trySend(snap)
        }
        awaitClose { registration.remove() }
    }

    private suspend fun reconcile(recipes: CollectionReference, snap: QuerySnapshot, local: List<RecipeStamp>) {
        val remote = snap.documents.mapNotNull { doc -> doc.data?.let { SyncedRecipe.fromMap(doc.id, it) } }.associateBy { it.uid }
        val localStamps = local.associate { it.uid to it.updatedAt }

        remote.values.forEach { r ->
            val mine = localStamps[r.uid]
            if (mine == null || r.updatedAt > mine) repository.applyRemote(r)
        }

        // 只有确定看到的是服务器上的数据才上传，免得拿旧缓存覆盖对方刚改的
        if (snap.metadata.isFromCache) return
        _error.value = null
        localStamps.forEach { (uid, updatedAt) ->
            val theirs = remote[uid]
            if (theirs == null || updatedAt > theirs.updatedAt) {
                val data = repository.loadForSync(uid) ?: return@forEach
                // 不等服务器确认：写入先进本地缓存，没网时联网后自动补传
                recipes.document(uid).set(data.toMap())
                    .addOnFailureListener { e -> _error.value = "上传失败：${e.message}" }
            }
        }
    }

    private companion object {
        const val TAG = "KitchenSync"
        const val KEY_CODE = "code"
        const val KITCHENS = "kitchens"
        const val RECIPES = "recipes"
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
