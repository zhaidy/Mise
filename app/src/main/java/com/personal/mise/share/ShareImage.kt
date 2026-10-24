package com.personal.mise.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.personal.mise.data.Freshness
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.MenuSection
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.RecipeMenu
import com.personal.mise.data.tagList
import com.personal.mise.image.ImageStore
import com.personal.mise.ui.theme.Light
import com.personal.mise.ui.theme.LightExtra
import com.personal.mise.ui.theme.coverFor
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.CalorieText
import com.personal.mise.util.StepPhase
import com.personal.mise.util.TimeText
import com.personal.mise.util.UnitText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val unsafeChars = Regex("[\\\\/:*?\"<>|\\s]+")

/** 分享图片的文件名用菜名，去掉文件系统不认的字符；空了就叫 mise */
fun shareFileName(name: String): String =
    name.replace(unsafeChars, "_").trim('_', '.').take(40).ifEmpty { "mise" }

/**
 * 把菜谱、菜单画成一张长图，发给别人或存进相册。
 * 直接用 Canvas 画，不截屏：长菜谱一屏放不下，而且图要固定浅色、不带按钮。
 */
object ShareImage {
    private const val WIDTH = 1080
    private const val PAD = 72f
    private const val CONTENT = WIDTH - 2 * PAD

    /** 先录到 Picture 里，画完才知道多高；再大也不超过这么高，免得撑爆内存 */
    private const val MAX_HEIGHT = 30_000
    /** 成图最多这么多像素（约 64MB），超了整张等比缩小 */
    private const val MAX_PIXELS = 16_000_000f

    private const val SHARE_DIR = "shared"
    private const val FOOTER = "— 来自 Mise 菜谱 —"

    private val paper = Light.background.toArgb()
    private val ink = Light.onSurface.toArgb()
    private val muted = Light.onSurfaceVariant.toArgb()
    private val primary = Light.primary.toArgb()
    private val secondary = Light.secondary.toArgb()
    private val tertiary = Light.tertiary.toArgb()
    private val bowlBg = Light.tertiaryContainer.toArgb()
    private val onBowl = Light.onTertiaryContainer.toArgb()
    private val outline = Light.outline.toArgb()
    private val outlineVariant = Light.outlineVariant.toArgb()
    private val pillBg = Light.surfaceContainerHigh.toArgb()

    private val serifBlack: Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SERIF, 900, false) else Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val serif: Typeface = Typeface.SERIF
    private val sansBold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    /** 菜谱长图：封面、用料、调料碗、步骤（带步骤图），用量按当前换算倍率 */
    suspend fun recipe(full: RecipeFull, factor: Double, images: ImageStore): Bitmap = withContext(Dispatchers.Default) {
        val sheet = Sheet()
        val c = sheet.canvas
        val recipe = full.recipe

        // 封面：有图裁成横幅，没图用和应用里一样的纯色首字
        val cover = imageFile(images, recipe.coverImage)?.let { decode(it, WIDTH) }
        val coverHeight = if (cover != null) 680f else 420f
        if (cover != null) {
            drawCropped(c, cover, RectF(0f, 0f, WIDTH.toFloat(), coverHeight), 0f)
        } else {
            val colors = LightExtra.coverFor(recipe.title)
            c.drawRect(0f, 0f, WIDTH.toFloat(), coverHeight, fill(colors.background.toArgb()))
            val initial = text(220f, colors.content.toArgb(), serifBlack).apply { textAlign = Paint.Align.CENTER }
            val fm = initial.fontMetrics
            c.drawText(recipe.title.trim().take(1).ifEmpty { "菜" }, WIDTH / 2f, coverHeight / 2f - (fm.ascent + fm.descent) / 2, initial)
        }
        // 下面的纸面圆角往上盖住封面一截
        c.drawRoundRect(RectF(0f, coverHeight - 44f, WIDTH.toFloat(), coverHeight + 88f), 44f, 44f, fill(paper))
        sheet.y = coverHeight + 24f

        sheet.paragraph(recipe.title, text(76f, ink, serifBlack), spacing = 1.1f)
        sheet.y += 18f

        val pills = buildList {
            recipe.category?.takeIf { it.isNotBlank() }?.let { cat ->
                add(recipe.subcategory?.takeIf { it.isNotBlank() }?.let { "$cat › $it" } ?: cat)
            }
            recipe.servings?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (full.steps.isNotEmpty()) add("${full.steps.size} 步")
            recipe.calories?.let { add(CalorieText.describe(it, recipe.servings, factor)) }
            if (factor != 1.0) add("用量 ${AmountScaler.label(factor)}")
        }
        sheet.pills(pills, text(32f, ink, Typeface.DEFAULT), pillBg)
        if (recipe.tagList.isNotEmpty()) {
            sheet.y += 14f
            sheet.paragraph(recipe.tagList.joinToString("  ") { "#$it" }, text(32f, primary, Typeface.DEFAULT))
        }

        val mains = full.ingredients.filter { it.kind != IngredientKind.SEASONING }
        val seasonings = full.ingredients.filter { it.kind == IngredientKind.SEASONING }
        sheet.section("主料", primary)
        sheet.ingredients(mains.map { it.name to amount(it.amountText, factor) })
        sheet.section("调料", secondary)
        sheet.ingredients(seasonings.map { it.name to amount(it.amountText, factor) })

        val cookStart = StepPhase.cookStart(full.steps.map { it.text })
        if (full.bowls.isNotEmpty()) {
            sheet.section("调料碗", tertiary)
            full.bowls.forEach { bowl ->
                val index = full.steps.indexOfFirst { it.id == bowl.bowl.stepId }
                val subtitle = if (index >= 0) StepPhase.bowlStepLabel(index, cookStart, full.steps[index].text) else null
                sheet.bowl(bowl.bowl.label, subtitle, bowl.items.map { it.name to amount(it.amountText, factor) })
                sheet.y += 16f
            }
        }

        if (full.steps.isNotEmpty()) {
            sheet.section("步骤", secondary)
            sheet.y += 8f
            full.steps.forEachIndexed { index, step ->
                if (cookStart != null && (index == 0 || index == cookStart)) {
                    val prep = index == 0
                    sheet.pills(
                        listOf(if (prep) "备料 · 开火前" else "开火"),
                        text(30f, if (prep) muted else Light.onPrimary.toArgb(), sansBold),
                        if (prep) pillBg else primary,
                    )
                    sheet.y += 24f
                }
                sheet.step(
                    number = index + 1,
                    body = step.text,
                    bowls = full.bowlsForStep(step.id).map { b -> b.bowl.label to b.items.map { it.name to amount(it.amountText, factor) } },
                    timer = step.timerSeconds?.let { "计时 ${TimeText.describe(it)}" },
                    images = step.images.mapNotNull { path -> imageFile(images, path)?.let { decode(it, CONTENT.toInt()) } },
                )
            }
        }

        recipe.sourceUrl?.takeIf { it.isNotBlank() }?.let {
            sheet.y += 8f
            sheet.paragraph("来源：$it", text(28f, muted, Typeface.DEFAULT))
        }
        sheet.footer()
        sheet.finish()
    }

    /**
     * 菜单图：和「菜单」视图一个样子，按分类分区。
     * withDates 为 false 时不写「上次下厨」，只是一份菜名单，适合发给客人点菜。
     */
    suspend fun menu(sections: List<MenuSection>, images: ImageStore, withDates: Boolean, today: LocalDate = LocalDate.now()): Bitmap =
        withContext(Dispatchers.Default) {
            val sheet = Sheet()
            val c = sheet.canvas
            sheet.y = 88f

            sheet.centered("M E N U", text(30f, primary, sansBold).apply { letterSpacing = 0.1f })
            sheet.y += 10f
            sheet.centered("家宴菜单", text(84f, ink, serifBlack).apply { letterSpacing = 0.25f })
            sheet.y += 6f
            sheet.centered(today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)), text(32f, muted, serif).apply { letterSpacing = 0.08f })
            sheet.y += 40f
            // 两条横线中间一个菱形
            val mid = WIDTH / 2f
            val line = stroke(outline, 2f)
            c.drawLine(PAD + 80f, sheet.y, mid - 34f, sheet.y, line)
            c.drawLine(mid + 34f, sheet.y, WIDTH - PAD - 80f, sheet.y, line)
            sheet.centeredAt("◆", text(22f, primary, Typeface.DEFAULT), sheet.y)
            sheet.y += 36f
            if (withDates) {
                sheet.legend(listOf(Freshness.FRESH to "一周内做过", Freshness.STALE to "一个多月没做", Freshness.NEVER to "还没做过"))
            }

            val sectionPaint = text(48f, ink, serifBold).apply { letterSpacing = 0.12f }
            val countPaint = text(28f, primary, sansBold)
            val subPaint = text(28f, muted, Typeface.DEFAULT).apply { letterSpacing = 0.05f }
            sections.forEach { section ->
                sheet.y += 64f
                val baseline = sheet.y - sectionPaint.fontMetrics.ascent
                c.drawText(section.name, PAD, baseline, sectionPaint)
                var x = PAD + sectionPaint.measureText(section.name) + 24f
                val count = "${section.count} 道"
                c.drawText(count, x, baseline, countPaint)
                x += countPaint.measureText(count) + 24f
                val lineY = baseline - (sectionPaint.textSize * 0.32f)
                c.drawLine(x, lineY, WIDTH - PAD, lineY, stroke(outline, 2f))
                sheet.y = baseline + sectionPaint.fontMetrics.descent + 8f

                section.groups.forEach { group ->
                    group.sub?.let {
                        sheet.y += 24f
                        sheet.line("—  $it  —", subPaint, PAD)
                        sheet.y += 4f
                    }
                    group.items.forEach { item ->
                        val recipe = item.recipe
                        val meta = (recipe.tagList + listOfNotNull(recipe.calories?.let(CalorieText::format))).joinToString(" · ")
                        sheet.dish(
                            title = recipe.title,
                            meta = meta,
                            whenText = if (withDates) RecipeMenu.whenText(item, today) else null,
                            freshness = item.freshness,
                            thumb = imageFile(images, recipe.coverImage)?.let { decode(it, 200) },
                            coverTitle = recipe.title,
                        )
                    }
                }
            }
            if (sections.isEmpty()) {
                sheet.y += 60f
                sheet.centered("还没有菜", text(36f, muted, serif))
            }
            sheet.y += 40f
            if (withDates) {
                sheet.centered("价格栏写的是 · 上次下厨", text(26f, muted, serif).apply { letterSpacing = 0.15f })
                sheet.y += 10f
            }
            sheet.footer()
            sheet.finish()
        }

    /** 存到相册的 Pictures/Mise 下；Android 10 以下要存储权限，不支持，返回 false */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${shareFileName(name)}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Mise")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        }.getOrDefault(false)
        if (ok) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        ok
    }

    /** 写到 cache/shared/ 下，再用系统分享面板发出去（微信、QQ、存到文件……） */
    suspend fun share(context: Context, bitmap: Bitmap, name: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            // 每次只留最新一张，旧的分享图没用了
            dir.listFiles()?.forEach { it.delete() }
            File(dir, "${shareFileName(name)}.png").also { f ->
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享图片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun amount(text: String, factor: Double) = UnitText.display(AmountScaler.scale(text, factor))

    private fun imageFile(images: ImageStore, path: String?): File? =
        path?.takeIf { it.isNotBlank() }?.let { images.file(it) }?.takeIf { it.isFile }

    /** 按目标宽度抽样解码，大图不整张读进内存 */
    private fun decode(file: File, targetWidth: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** 居中裁切铺满 dst，可带圆角 */
    private fun drawCropped(c: Canvas, bitmap: Bitmap, dst: RectF, radius: Float) {
        val scale = max(dst.width() / bitmap.width, dst.height() / bitmap.height)
        val w = dst.width() / scale
        val h = dst.height() / scale
        val left = (bitmap.width - w) / 2
        val top = (bitmap.height - h) / 2
        val src = Rect(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt())
        c.save()
        if (radius > 0) c.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
        c.drawBitmap(bitmap, src, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    private fun text(size: Float, color: Int, typeface: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    /** 名称和用量之间的点线 */
    private fun dots(color: Int, width: Float, gap: Float) = stroke(color, width).apply {
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(0.1f, gap), 0f)
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, spacing: Float = 1.2f, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()

    private fun freshnessColor(freshness: Freshness) = when (freshness) {
        Freshness.FRESH -> secondary
        Freshness.NORMAL -> ink
        Freshness.STALE -> tertiary
        Freshness.NEVER -> muted
    }

    /** 一张往下画的纸，y 是当前画到的高度 */
    private class Sheet {
        private val picture = Picture()
        val canvas: Canvas = picture.beginRecording(WIDTH, MAX_HEIGHT)
        var y = 0f

        fun draw(layout: StaticLayout, x: Float, top: Float = y) {
            canvas.save()
            canvas.translate(x, top)
            layout.draw(canvas)
            canvas.restore()
        }

        fun paragraph(text: String, paint: TextPaint, x: Float = PAD, width: Float = CONTENT, spacing: Float = 1.25f) {
            val l = layout(text, paint, width.toInt(), spacing)
            draw(l, x)
            y += l.height
        }

        /** 单行，太长省略 */
        fun line(text: String, paint: TextPaint, x: Float, width: Float = CONTENT) {
            val l = layout(text, paint, width.toInt(), maxLines = 1)
            draw(l, x)
            y += l.height
        }

        fun centered(text: String, paint: TextPaint) {
            val fm = paint.fontMetrics
            centeredAt(text, paint, y - fm.ascent - (fm.descent - fm.ascent) / 2)
            y += fm.descent - fm.ascent
        }

        /** 文字中线落在 centerY 上 */
        fun centeredAt(text: String, paint: TextPaint, centerY: Float) {
            val fm = paint.fontMetrics
            val w = paint.measureText(text)
            canvas.drawText(text, (WIDTH - w) / 2, centerY - (fm.ascent + fm.descent) / 2, paint)
        }

        /** 一排小胶囊，放不下就换行 */
        fun pills(texts: List<String>, paint: TextPaint, background: Int) {
            if (texts.isEmpty()) return
            val h = 64f
            val padX = 26f
            val gap = 14f
            var x = PAD
            val bg = fill(background)
            texts.forEach { t ->
                val w = min(paint.measureText(t) + 2 * padX, CONTENT)
                if (x > PAD && x + w > WIDTH - PAD) {
                    x = PAD
                    y += h + gap
                }
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, bg)
                val fm = paint.fontMetrics
                val label = TextUtils.ellipsize(t, paint, w - 2 * padX, TextUtils.TruncateAt.END).toString()
                canvas.drawText(label, x + padX, y + h / 2 - (fm.ascent + fm.descent) / 2, paint)
                x += w + gap
            }
            y += h
        }

        /** 分段标题：左边一小段色条 + 衬线字 */
        fun section(title: String, accent: Int) {
            y += 64f
            val paint = text(48f, ink, serifBold)
            val fm = paint.fontMetrics
            val h = fm.descent - fm.ascent
            canvas.drawRoundRect(RectF(PAD, y + h / 2 - 4f, PAD + 44f, y + h / 2 + 4f), 4f, 4f, fill(accent))
            canvas.drawText(title, PAD + 66f, y - fm.ascent, paint)
            y += h + 12f
        }

        /** 用料表：名称 ······ 用量 */
        fun ingredients(rows: List<Pair<String, String>>) {
            if (rows.isEmpty()) {
                paragraph("无", text(36f, muted, Typeface.DEFAULT))
                return
            }
            val name = text(38f, ink, Typeface.DEFAULT)
            val amount = text(38f, muted, sansBold)
            val leader = dots(outline, 3f, 12f)
            rows.forEach { (n, a) ->
                val amountW = min(amount.measureText(a), CONTENT * 0.45f)
                val nameL = layout(n, name, (CONTENT - amountW - 60f).toInt())
                val rowH = max(nameL.height.toFloat(), 56f) + 20f
                val top = y + (rowH - nameL.height) / 2
                draw(nameL, PAD, top)
                val fm = amount.fontMetrics
                // 点线和用量对齐名称的第一行
                val firstLineMid = top + nameL.getLineBaseline(0) + (fm.ascent + fm.descent) / 2
                val nameEnd = PAD + nameL.getLineWidth(0) + 20f
                val amountX = WIDTH - PAD - amountW
                if (amountX - 20f > nameEnd) canvas.drawLine(nameEnd, firstLineMid, amountX - 20f, firstLineMid, leader)
                val shown = TextUtils.ellipsize(a, amount, amountW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(shown, amountX, top + nameL.getLineBaseline(0), amount)
                y += rowH
            }
        }

        /** 调料碗卡片：芥末色浅底，碗名 + 每样调料 */
        fun bowl(label: String, subtitle: String?, items: List<Pair<String, String>>, x: Float = PAD, width: Float = CONTENT, small: Boolean = false) {
            val padX = if (small) 30f else 40f
            val padY = if (small) 24f else 32f
            val titlePaint = if (small) text(32f, onBowl, sansBold) else text(40f, onBowl, serifBold)
            val subPaint = text(30f, onBowl, sansBold)
            val itemPaint = text(if (small) 32f else 34f, onBowl, Typeface.DEFAULT)
            val amountPaint = text(if (small) 32f else 34f, onBowl, sansBold)
            val inner = width - 2 * padX
            val indent = if (small) 0f else 52f

            val subW = subtitle?.let { subPaint.measureText(it) + 20f } ?: 0f
            val titleL = layout(if (small) "倒入$label" else label, titlePaint, (inner - subW - indent).toInt())
            val rows = items.map { (n, a) ->
                val aw = min(amountPaint.measureText(a), inner * 0.45f)
                Triple(layout(n, itemPaint, (inner - indent - aw - 24f).toInt()), a, aw)
            }
            val rowGap = 8f
            val height = padY * 2 + titleL.height + rows.sumOf { (l, _, _) -> l.height + rowGap.toDouble() }.toFloat() + if (rows.isEmpty()) 0f else 8f
            canvas.drawRoundRect(RectF(x, y, x + width, y + height), if (small) 28f else 36f, if (small) 28f else 36f, fill(bowlBg))

            var cy = y + padY
            if (!small) {
                // 碗的小图标：一个半圆
                val r = 16f
                val cx = x + padX + r
                val mid = cy + titleL.height / 2f
                canvas.drawArc(RectF(cx - r, mid - r, cx + r, mid + r), 0f, 180f, true, fill(onBowl))
                canvas.drawRoundRect(RectF(cx - r - 4f, mid - 3f, cx + r + 4f, mid + 1f), 2f, 2f, fill(onBowl))
            }
            draw(titleL, x + padX + indent, cy)
            subtitle?.let { canvas.drawText(it, x + width - padX - subPaint.measureText(it), cy + titleL.getLineBaseline(0), subPaint) }
            cy += titleL.height + 8f
            rows.forEach { (l, a, aw) ->
                draw(l, x + padX + indent, cy)
                val shown = TextUtils.ellipsize(a, amountPaint, aw, TextUtils.TruncateAt.END).toString()
                canvas.drawText(shown, x + width - padX - aw, cy + l.getLineBaseline(0), amountPaint)
                cy += l.height + rowGap
            }
            y += height
        }

        /** 一步：左边黑底圆圈编号，右边文字、这一步倒的调料碗、计时、步骤图 */
        fun step(number: Int, body: String, bowls: List<Pair<String, List<Pair<String, String>>>>, timer: String?, images: List<Bitmap>) {
            val r = 30f
            val textX = PAD + 2 * r + 28f
            val textW = WIDTH - PAD - textX
            canvas.drawCircle(PAD + r, y + r, r, fill(ink))
            val num = text(32f, paper, serifBold).apply { textAlign = Paint.Align.CENTER }
            val fm = num.fontMetrics
            canvas.drawText("$number", PAD + r, y + r - (fm.ascent + fm.descent) / 2, num)

            val start = y
            y += 6f
            paragraph(body, text(38f, ink, Typeface.DEFAULT), x = textX, width = textW, spacing = 1.35f)
            bowls.forEach { (label, items) ->
                y += 16f
                bowl(label, null, items, x = textX, width = textW, small = true)
            }
            timer?.let {
                y += 16f
                val save = y
                // pills() 从 PAD 起排，这里自己画一个
                val p = text(30f, onBowl, sansBold)
                val w = p.measureText(it) + 52f
                canvas.drawRoundRect(RectF(textX, save, textX + w, save + 60f), 30f, 30f, fill(bowlBg))
                val f = p.fontMetrics
                canvas.drawText(it, textX + 26f, save + 30f - (f.ascent + f.descent) / 2, p)
                y = save + 60f
            }
            images.forEach {
                y += 18f
                val h = textW * 3f / 4f
                drawCropped(canvas, it, RectF(textX, y, textX + textW, y + h), 28f)
                y += h
            }
            y = max(y, start + 2 * r) + 44f
        }

        /** 菜单上的一道菜：缩略图、菜名 ······ 上次做的时间，下面一行标签和热量 */
        fun dish(title: String, meta: String, whenText: String?, freshness: Freshness, thumb: Bitmap?, coverTitle: String) {
            y += 18f
            val size = 104f
            val dst = RectF(PAD, y, PAD + size, y + size)
            if (thumb != null) {
                drawCropped(canvas, thumb, dst, 18f)
            } else {
                val colors = LightExtra.coverFor(coverTitle)
                canvas.drawRoundRect(dst, 18f, 18f, fill(colors.background.toArgb()))
                val p = text(48f, colors.content.toArgb(), serifBlack).apply { textAlign = Paint.Align.CENTER }
                val f = p.fontMetrics
                canvas.drawText(coverTitle.trim().take(1).ifEmpty { "菜" }, dst.centerX(), dst.centerY() - (f.ascent + f.descent) / 2, p)
            }

            val x = PAD + size + 30f
            val right = WIDTH - PAD
            val titlePaint = text(42f, ink, serifBold)
            val never = freshness == Freshness.NEVER
            val whenPaint = text(if (never) 32f else 36f, freshnessColor(freshness), if (never) Typeface.create(serif, Typeface.ITALIC) else serifBold)
            val metaPaint = text(28f, muted, Typeface.DEFAULT)
            val tf = titlePaint.fontMetrics
            val titleH = tf.descent - tf.ascent
            val metaH = if (meta.isNotEmpty()) metaPaint.fontMetrics.let { it.descent - it.ascent } + 6f else 0f
            val top = y + (size - titleH - metaH) / 2
            val baseline = top - tf.ascent

            val whenW = whenText?.let { whenPaint.measureText(it) } ?: 0f
            val titleRoom = right - x - (if (whenText != null) whenW + 40f else 0f)
            val shownTitle = TextUtils.ellipsize(title, titlePaint, titleRoom, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shownTitle, x, baseline, titlePaint)
            if (whenText != null) {
                val titleEnd = x + titlePaint.measureText(shownTitle) + 16f
                val lineY = baseline - 4f
                if (right - whenW - 16f > titleEnd) canvas.drawLine(titleEnd, lineY, right - whenW - 16f, lineY, dots(outline, 4f, 12f))
                canvas.drawText(whenText, right - whenW, baseline, whenPaint)
            }
            if (meta.isNotEmpty()) {
                val shownMeta = TextUtils.ellipsize(meta, metaPaint, right - x, TextUtils.TruncateAt.END).toString()
                canvas.drawText(shownMeta, x, baseline + tf.descent + 6f - metaPaint.fontMetrics.ascent, metaPaint)
            }
            y += size
        }

        fun legend(items: List<Pair<Freshness, String>>) {
            val p = text(26f, muted, Typeface.DEFAULT)
            val dot = 7f
            val gap = 36f
            val widths = items.map { (_, label) -> 2 * dot + 10f + p.measureText(label) }
            var x = (WIDTH - widths.sum() - gap * (items.size - 1)) / 2
            val f = p.fontMetrics
            val mid = y + 18f
            items.forEachIndexed { i, (freshness, label) ->
                canvas.drawCircle(x + dot, mid, dot, fill(freshnessColor(freshness)))
                canvas.drawText(label, x + 2 * dot + 10f, mid - (f.ascent + f.descent) / 2, p)
                x += widths[i] + gap
            }
            y += 36f
        }

        fun footer() {
            y += 48f
            canvas.drawLine(PAD, y, WIDTH - PAD, y, stroke(outlineVariant, 2f))
            y += 36f
            centered(FOOTER, text(26f, muted, serif).apply { letterSpacing = 0.15f })
            y += 56f
        }

        /** 按画到的高度出图，太大就整体缩小 */
        fun finish(): Bitmap {
            picture.endRecording()
            val height = ceil(y).toInt().coerceIn(1, MAX_HEIGHT)
            val scale = min(1f, sqrt(MAX_PIXELS / (WIDTH.toFloat() * height)))
            val bitmap = Bitmap.createBitmap((WIDTH * scale).toInt(), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(paper)
                scale(scale, scale)
                clipRect(0, 0, WIDTH, height)
                drawPicture(picture)
            }
            return bitmap
        }
    }
}
