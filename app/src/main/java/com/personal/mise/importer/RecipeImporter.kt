package com.personal.mise.importer

import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.ai.AiRecipeTranslator
import com.personal.mise.ai.RecipeText
import com.personal.mise.bowl.SeasoningDictionary
import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
import com.personal.mise.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

open class ImportException(message: String) : Exception(message)

/** 网站返回了错误状态码 */
class HttpStatusException(val code: Int) : ImportException(
    if (code == 429) "网站暂时限制了访问（HTTP 429），过几分钟再试" else "打开链接失败（HTTP $code）",
)

/**
 * 自动打开被网站拦住了（限流、验证页），可以让用户在网页里打开 [url]，手动过了验证再导入
 */
class NeedsVerificationException(val url: String, message: String) : ImportException(message)

/** aiOrganized：内容已经是 AI 整理过的（步骤已经分好，不用再拆） */
data class ImportResult(val draft: RecipeDraft, val warnings: List<String>, val aiOrganized: Boolean = false)

/**
 * 在手机上直接抓取下厨房网页、解析内容、把图片下载到本地。
 * 带菜谱 JSON-LD 的英文网站也能解析，解析出来是英文时交给 AI 翻译成中文。
 * 小红书笔记排版随意，抓到标题、正文和配图后交给 AI 整理。
 */
class RecipeImporter(
    private val images: ImageStore,
    private val organizer: AiRecipeOrganizer,
    private val browser: WebPageLoader,
    private val translator: AiRecipeTranslator,
    /** 填了 AI 的 Key 才翻译英文菜谱 */
    private val hasAiKey: () -> Boolean,
    private val cookieJar: CookieJar = MemoryCookieJar(),
) {

    // 下厨房有防爬虫验证：第一次访问只下发 cookie（或返回验证页），带上 cookie 再访问才是正常页面。
    // 没有 cookie jar 时每次请求都是「第一次」，就会出现要手动重试好几次才成功的情况。
    // cookie 和 WebView 共用，WebView 里过了验证，直接请求也能用上。

    /** 返回过 429/403 的网站 →（到什么时候之前不再直接请求, 状态码）；冷却期内再请求只会延长限流 */
    private val blocked = ConcurrentHashMap<String, Pair<Long, Int>>()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun import(sharedText: String): ImportResult = withContext(Dispatchers.IO) {
        val url = UrlExtractor.firstUrl(sharedText) ?: throw ImportException("没有在文字里找到链接")
        if (XiaohongshuParser.isNoteUrl(url)) return@withContext importXiaohongshu(url)

        // 先直接抓；被防爬虫拦住（429、验证页）时先试 MIP 页面，再用 WebView 打开，让它跑完验证
        val direct = runCatching { fetchAndParse(url, MOBILE_UA) }
        val urlRecipeId = XiachufangParser.recipeId(url)
        val (finalUrl, parsed) = direct.getOrNull()?.takeUnless { it.second.isEmpty }
            ?: urlRecipeId?.let { fetchMip(it) }
            ?: loadInBrowser(url)
            ?: throw blockedError(url, direct.exceptionOrNull())
        (cookieJar as? WebViewCookieJar)?.flush()
        buildResult(finalUrl, parsed, XiachufangParser.recipeId(finalUrl) ?: urlRecipeId)
    }

    /**
     * 用户在网页里手动过了验证，用网页里渲染出来的内容导入。
     * 验证拿到的 cookie 已经存在 WebView 里，之前的限流冷却也清掉，后面补抓电脑版、下次导入都能直接请求。
     */
    suspend fun importPage(sourceUrl: String, pageUrl: String, html: String): ImportResult = withContext(Dispatchers.IO) {
        blocked.clear()
        (cookieJar as? WebViewCookieJar)?.flush()
        buildResult(pageUrl, XiachufangParser.parse(html, pageUrl), XiachufangParser.recipeId(pageUrl) ?: XiachufangParser.recipeId(sourceUrl))
    }

    /** 直接请求、MIP、WebView 都没拿到菜谱时报的错；网络本身不通就直说，被拦住了就提示可以去网页里验证 */
    private fun blockedError(url: String, directError: Throwable?): Throwable = when {
        directError is HttpStatusException && directError.code in BLOCKED_CODES -> NeedsVerificationException(
            url,
            "下厨房暂时限制了访问（HTTP ${directError.code}），马上重试还会被拦。可以点「网页验证」在网页里打开，过了验证会自动导入",
        )
        directError != null -> directError
        else -> NeedsVerificationException(
            url,
            "没能从这个页面解析出菜谱，可能是下厨房要验证。可以点「网页验证」在网页里打开，过了验证会自动导入",
        )
    }

    private suspend fun buildResult(finalUrl: String, firstParsed: ParsedRecipe, recipeId: String?): ImportResult {
        var parsed = firstParsed

        // 手机版页面解析不全、或者步骤一张图都没有（多半是步骤退回了不带图的 JSON-LD）时，
        // 用电脑版页面再试一次（电脑版结构已经验证过）
        val missingStepImages = parsed.steps.isNotEmpty() && parsed.steps.none { it.imageUrl != null }
        if ((!parsed.isComplete || missingStepImages) && recipeId != null) {
            runCatching {
                fetchAndParse(XiachufangParser.desktopUrl(recipeId), DESKTOP_UA).second
            }.getOrNull()?.let { if (it.score > parsed.score) parsed = it }
        }

        if (parsed.isEmpty) {
            throw ImportException("没能从这个页面解析出菜谱，请确认是下厨房的菜谱链接")
        }

        val warnings = mutableListOf<String>()
        if (parsed.title.isNullOrBlank()) warnings += "没有解析到标题，请手动填写"
        if (parsed.ingredients.isEmpty()) warnings += "没有解析到用料，请手动添加"
        if (parsed.steps.isEmpty()) warnings += "没有解析到步骤，请手动添加"

        val original = RecipeText(parsed.title.orEmpty(), parsed.servings, parsed.ingredients, parsed.steps.map { it.text })
        val english = AiRecipeTranslator.isEnglish(original)
        if (english && !hasAiKey()) warnings += "这是英文菜谱，填写 AI 的 API Key 后导入会自动翻译成中文"
        val (text, downloaded) = coroutineScope {
            // 翻译和下载图片同时进行
            val translateJob = async {
                if (!english || !hasAiKey()) original
                else runCatching { translator.translate(original) }
                    .onFailure { warnings += "英文菜谱没能翻译成中文：${it.message ?: it.javaClass.simpleName}" }
                    .getOrDefault(original)
            }
            val coverJob = async { parsed.coverUrl?.let { download(it) } }
            val stepJobs = parsed.steps.map { step -> async { step.imageUrl?.let { download(it) } } }
            translateJob.await() to (coverJob.await() to stepJobs.awaitAll())
        }
        val (cover, stepImages) = downloaded
        if (parsed.coverUrl != null && cover == null) warnings += "封面图下载失败"
        if (parsed.coverUrl == null) warnings += "没有找到封面图"
        val failedSteps = parsed.steps.indices.filter { parsed.steps[it].imageUrl != null && stepImages[it] == null }
        if (failedSteps.isNotEmpty()) {
            warnings += "第 ${failedSteps.joinToString("、") { (it + 1).toString() }} 步的图片下载失败"
        }

        val draft = RecipeDraft(
            title = text.title,
            coverImage = cover,
            sourceUrl = recipeId?.let { XiachufangParser.desktopUrl(it) } ?: finalUrl,
            servings = text.servings,
            ingredients = text.ingredients.map { (name, amount) ->
                IngredientDraft(
                    name = name,
                    amountText = amount,
                    kind = if (SeasoningDictionary.isSeasoning(name)) IngredientKind.SEASONING else IngredientKind.MAIN,
                )
            },
            steps = text.steps.mapIndexed { i, step -> StepDraft(text = step, image = stepImages[i]) },
        )
        return ImportResult(draft, warnings)
    }

    private suspend fun importXiaohongshu(url: String): ImportResult {
        // 直接请求常被小红书当成爬虫（短链靠 JS 跳转、没 cookie 时不给笔记数据），
        // 拿不到时用 WebView 当真浏览器再打开一次；浏览器里没登录也能看到正文，只是盖着登录弹窗
        val direct = runCatching { fetchPage(url, DESKTOP_UA).let { (finalUrl, html) -> XiaohongshuParser.parse(html, finalUrl) } }
        val note = direct.getOrNull()?.takeUnless { it.isEmpty }
            ?: loadXhsInBrowser(url)
            ?: throw ImportException("没能打开这篇小红书笔记（可能需要登录或笔记已删除）。可以在小红书里复制笔记正文，用「AI 整理笔记」导入，或者截图用图片导入")

        val urls = note.imageUrls.take(MAX_XHS_IMAGES)
        val saved = coroutineScope { urls.map { async { download(it, XHS_REFERER) } }.awaitAll() }.filterNotNull()
        // 第一张配图当封面，其余的只给 AI 看，整理完就删掉
        val recipe = try {
            organizer.organizeXhs(note.title, note.desc, saved.mapNotNull { runCatching { images.file(it).readBytes() }.getOrNull() })
        } catch (e: Exception) {
            saved.forEach { images.delete(it) }
            throw e
        }
        saved.drop(1).forEach { images.delete(it) }

        val result = AiRecipeOrganizer.toImportResult(recipe, url)
        val warnings = result.warnings.toMutableList()
        if (saved.size < urls.size) warnings += "有 ${urls.size - saved.size} 张配图下载失败，AI 没看到上面的内容"
        if (saved.isEmpty() && urls.isNotEmpty()) warnings += "封面图下载失败"
        if (note.imageUrls.size > MAX_XHS_IMAGES) warnings += "笔记图片太多，AI 只看了前 $MAX_XHS_IMAGES 张"
        return ImportResult(result.draft.copy(coverImage = saved.firstOrNull()), warnings, aiOrganized = true)
    }

    private suspend fun loadXhsInBrowser(url: String): XhsNote? {
        val (finalUrl, html) = runCatching {
            browser.load(url, userAgent = DESKTOP_UA) { pageUrl, html ->
                withContext(Dispatchers.Default) { !XiaohongshuParser.parse(html, pageUrl).isEmpty }
            }
        }.getOrNull() ?: return null
        return XiaohongshuParser.parse(html, finalUrl).takeUnless { it.isEmpty }
    }

    /** 给百度用的 MIP 页面，主站限流时一般还能打开 */
    private fun fetchMip(recipeId: String): Pair<String, ParsedRecipe>? =
        XiachufangParser.mipUrls(recipeId).firstNotNullOfOrNull { mipUrl ->
            runCatching {
                val (finalUrl, html) = fetchPage(mipUrl, MOBILE_UA)
                finalUrl to XiachufangParser.parse(html, finalUrl)
            }.getOrNull()?.takeUnless { it.second.isEmpty }
        }

    private suspend fun loadInBrowser(url: String): Pair<String, ParsedRecipe>? {
        val (finalUrl, html) = runCatching {
            browser.load(url, userAgent = MOBILE_UA) { pageUrl, html ->
                withContext(Dispatchers.Default) { !XiachufangParser.parse(html, pageUrl).isEmpty }
            }
        }.getOrNull() ?: return null
        return finalUrl to XiachufangParser.parse(html, finalUrl)
    }

    /** 抓页面并解析；解析不出东西（多半是防爬虫验证页）时带着拿到的 cookie 自动重试几次 */
    private suspend fun fetchAndParse(url: String, userAgent: String): Pair<String, ParsedRecipe> {
        var result: Pair<String, ParsedRecipe>? = null
        for (attempt in 0 until PAGE_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_DELAY_MS * attempt)
            val (finalUrl, html) = try {
                fetchPage(url, userAgent)
            } catch (e: ImportException) {
                // 429/403 是被限流或拦截了，马上重试只会更糟
                if (attempt == PAGE_ATTEMPTS - 1 || e is HttpStatusException && e.code in BLOCKED_CODES) throw e
                continue
            }
            val parsed = XiachufangParser.parse(html, finalUrl)
            result = finalUrl to parsed
            if (!parsed.isEmpty) break
        }
        return result ?: throw ImportException("打开链接失败")
    }

    private fun fetchPage(url: String, userAgent: String): Pair<String, String> {
        val host = url.toHttpUrlOrNull()?.host ?: throw ImportException("链接格式不对")
        blocked[host]?.let { (until, code) ->
            if (System.currentTimeMillis() < until) throw HttpStatusException(code)
            blocked.remove(host)
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code in BLOCKED_CODES) blocked[host] = System.currentTimeMillis() + BLOCK_COOLDOWN_MS to response.code
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                return response.request.url.toString() to response.body.string()
            }
        } catch (e: IOException) {
            throw ImportException("网络连接失败：${e.message ?: "请检查网络"}")
        } catch (e: IllegalArgumentException) {
            throw ImportException("链接格式不对")
        }
    }

    private suspend fun download(url: String, referer: String = XCF_REFERER): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .header("Referer", referer)
            .build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body.bytes()
        }
        images.saveBytes(bytes)
    }.getOrNull()

    companion object {
        private const val PAGE_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 800L
        private val BLOCKED_CODES = setOf(403, 429)
        private const val BLOCK_COOLDOWN_MS = 3 * 60_000L
        private const val XCF_REFERER = "https://www.xiachufang.com/"
        private const val XHS_REFERER = "https://www.xiaohongshu.com/"

        /** 小红书一篇最多 18 张图，菜谱的用料步骤一般在前几张 */
        private const val MAX_XHS_IMAGES = 9
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }
}
