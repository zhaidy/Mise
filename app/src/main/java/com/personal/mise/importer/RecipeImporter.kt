package com.personal.mise.importer

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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ImportException(message: String) : Exception(message)

data class ImportResult(val draft: RecipeDraft, val warnings: List<String>)

/** 在手机上直接抓取下厨房网页、解析内容、把图片下载到本地。 */
class RecipeImporter(private val images: ImageStore) {

    // 下厨房有防爬虫验证：第一次访问只下发 cookie（或返回验证页），带上 cookie 再访问才是正常页面。
    // 没有 cookie jar 时每次请求都是「第一次」，就会出现要手动重试好几次才成功的情况。
    private val cookieJar = object : CookieJar {
        private val store = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store["${it.domain}|${it.path}|${it.name}"] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.values.removeAll { it.expiresAt < now }
            return store.values.filter { it.matches(url) }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun import(sharedText: String): ImportResult = withContext(Dispatchers.IO) {
        val url = UrlExtractor.firstUrl(sharedText) ?: throw ImportException("没有在文字里找到链接")

        val (finalUrl, firstParsed) = fetchAndParse(url, MOBILE_UA)
        var parsed = firstParsed
        val recipeId = XiachufangParser.recipeId(finalUrl) ?: XiachufangParser.recipeId(url)

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

        val (cover, stepImages) = coroutineScope {
            val coverJob = async { parsed.coverUrl?.let { download(it) } }
            val stepJobs = parsed.steps.map { step -> async { step.imageUrl?.let { download(it) } } }
            coverJob.await() to stepJobs.awaitAll()
        }
        if (parsed.coverUrl != null && cover == null) warnings += "封面图下载失败"
        if (parsed.coverUrl == null) warnings += "没有找到封面图"
        val failedSteps = parsed.steps.indices.filter { parsed.steps[it].imageUrl != null && stepImages[it] == null }
        if (failedSteps.isNotEmpty()) {
            warnings += "第 ${failedSteps.joinToString("、") { (it + 1).toString() }} 步的图片下载失败"
        }

        val draft = RecipeDraft(
            title = parsed.title.orEmpty(),
            coverImage = cover,
            sourceUrl = recipeId?.let { XiachufangParser.desktopUrl(it) } ?: finalUrl,
            servings = parsed.servings,
            ingredients = parsed.ingredients.map { (name, amount) ->
                IngredientDraft(
                    name = name,
                    amountText = amount,
                    kind = if (SeasoningDictionary.isSeasoning(name)) IngredientKind.SEASONING else IngredientKind.MAIN,
                )
            },
            steps = parsed.steps.mapIndexed { i, step -> StepDraft(text = step.text, image = stepImages[i]) },
        )
        ImportResult(draft, warnings)
    }

    /** 抓页面并解析；解析不出东西（多半是防爬虫验证页）时带着拿到的 cookie 自动重试几次 */
    private suspend fun fetchAndParse(url: String, userAgent: String): Pair<String, ParsedRecipe> {
        var result: Pair<String, ParsedRecipe>? = null
        for (attempt in 0 until PAGE_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_DELAY_MS * attempt)
            val (finalUrl, html) = try {
                fetchPage(url, userAgent)
            } catch (e: ImportException) {
                if (attempt == PAGE_ATTEMPTS - 1) throw e else continue
            }
            val parsed = XiachufangParser.parse(html, finalUrl)
            result = finalUrl to parsed
            if (!parsed.isEmpty) break
        }
        return result ?: throw ImportException("打开链接失败")
    }

    private fun fetchPage(url: String, userAgent: String): Pair<String, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ImportException("打开链接失败（HTTP ${response.code}）")
                return response.request.url.toString() to response.body.string()
            }
        } catch (e: IOException) {
            throw ImportException("网络连接失败：${e.message ?: "请检查网络"}")
        } catch (e: IllegalArgumentException) {
            throw ImportException("链接格式不对")
        }
    }

    private suspend fun download(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .header("Referer", "https://www.xiachufang.com/")
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
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }
}
