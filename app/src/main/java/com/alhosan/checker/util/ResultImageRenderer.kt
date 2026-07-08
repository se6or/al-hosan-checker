package com.alhosan.checker.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.Subscription
import com.alhosan.checker.ui.i18n.*

/**
 * High-quality PNG renderer for the result screen.
 *
 * The app UI is Compose, but exported images are rendered here with Android's
 * native Canvas so the saved PNG is crisp, deterministic, and independent from
 * screenshots. The layout mirrors the requested ResultScreen arrangement:
 * - Main server information: current-language labels on the right, values below
 *   on the right, and copy buttons are NOT drawn in the exported image.
 * - Created/expiry and device counters: labels on the right, values opposite on
 *   the left.
 * - Status/trial: kept as a compact horizontal row.
 */
object ResultImageRenderer {

    // High-resolution export for a sharper PNG in galleries/sharing.
    private const val WIDTH = 2160
    private const val HEIGHT = 2500
    private const val MARGIN = 100f
    private const val INNER_PAD = 56f
    private const val CAPSULE_RADIUS = 42f
    private const val CARD_RADIUS = 58f

    private val COLOR_BG = Color.BLACK
    private val COLOR_CARD_BG = Color.parseColor("#050505")
    private val COLOR_CAPSULE_TOP = Color.parseColor("#080808")
    private val COLOR_CAPSULE_BOTTOM = Color.parseColor("#121212")
    private val COLOR_BORDER = Color.parseColor("#1F1A0F")
    private val COLOR_ACCENT = Color.parseColor("#D4AF37")
    private val COLOR_TEXT_DIM = Color.parseColor("#A0A0A0")
    private val COLOR_TEXT_WHITE = Color.WHITE
    private val COLOR_GREEN = Color.parseColor("#00E676")
    private val COLOR_RED = Color.parseColor("#FF1744")
    private val COLOR_SEPARATOR = Color.parseColor("#333333")

    private data class Field(
        val icon: IconKind,
        val label: String,
        val value: String
    )

    private enum class IconKind { Server, User, Key, Calendar, CalendarEnd, Devices, Group, Status, Trial }

    fun render(subscription: Subscription, lang: AppLang): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(COLOR_BG)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT
            textSize = 78f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }

        val iconAtRight = lang == AppLang.AR

        var y = 145f
        canvas.drawText(lang.splash, WIDTH / 2f, y, titlePaint)
        y += 92f

        val cardRect = RectF(MARGIN, y, WIDTH - MARGIN, HEIGHT - MARGIN)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_CARD_BG }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, cardPaint)
        canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, borderPaint)

        y += 70f
        val left = cardRect.left + 42f
        val right = cardRect.right - 42f
        val width = right - left
        val gap = 28f

        y = drawPrimaryInfoCapsule(
            canvas = canvas,
            top = y,
            left = left,
            width = width,
            fields = listOf(
                Field(IconKind.Server, lang.lHost, subscription.host),
                Field(IconKind.User, lang.lUser, subscription.username),
                Field(IconKind.Key, lang.lPass, subscription.password)
            ),
            iconAtRight = iconAtRight
        ) + gap

        y = drawSideBySideCapsule(
            canvas = canvas,
            top = y,
            left = left,
            width = width,
            fields = listOf(
                Field(IconKind.Calendar, lang.lCreated, subscription.created),
                Field(IconKind.CalendarEnd, lang.lExpiry, subscription.expiry)
            ),
            iconAtRight = iconAtRight
        ) + gap

        y = drawStatusTrialCapsule(canvas, subscription, lang, y, left, width, iconAtRight) + gap

        y = drawSideBySideCapsule(
            canvas = canvas,
            top = y,
            left = left,
            width = width,
            fields = listOf(
                Field(IconKind.Devices, lang.lDevices, subscription.activeCons),
                Field(IconKind.Group, lang.lMaxCons, subscription.maxCons)
            ),
            iconAtRight = iconAtRight
        ) + gap

        drawContentCountsCapsule(canvas, subscription, lang, y, left, width)

        return bitmap
    }

    private fun drawPrimaryInfoCapsule(
        canvas: Canvas,
        top: Float,
        left: Float,
        width: Float,
        fields: List<Field>,
        iconAtRight: Boolean
    ): Float {
        val rowHeight = 176f
        val height = INNER_PAD + fields.size * rowHeight + (fields.size - 1) * 26f
        drawCapsule(canvas, left, top, width, height)

        val labelPaint = labelPaint(Paint.Align.RIGHT)
        val valuePaint = valuePaint(Paint.Align.RIGHT, 40f)
        val rightX = left + width - INNER_PAD
        val valueMaxWidth = width - INNER_PAD * 2
        var y = top + INNER_PAD + 36f

        fields.forEachIndexed { index, field ->
            drawIconBeforeRightLabel(canvas, field.icon, field.label, rightX, y - 9f, labelPaint, iconAtRight)
            val displayValue = ellipsize(field.value, valuePaint, valueMaxWidth)
            canvas.drawText(displayValue, rightX, y + 62f, valuePaint)

            y += rowHeight
            if (index < fields.lastIndex) {
                drawDivider(canvas, left, width, y - 34f)
                y += 26f
            }
        }

        return top + height
    }

    private fun drawSideBySideCapsule(
        canvas: Canvas,
        top: Float,
        left: Float,
        width: Float,
        fields: List<Field>,
        iconAtRight: Boolean
    ): Float {
        val rowHeight = 118f
        val height = INNER_PAD + fields.size * rowHeight + (fields.size - 1) * 24f
        drawCapsule(canvas, left, top, width, height)

        val labelPaint = labelPaint(Paint.Align.RIGHT)
        val valuePaint = valuePaint(Paint.Align.LEFT, 40f)
        val leftX = left + INNER_PAD
        val rightX = left + width - INNER_PAD
        val valueMaxWidth = width * 0.48f
        var y = top + INNER_PAD + 42f

        fields.forEachIndexed { index, field ->
            val displayValue = ellipsize(field.value, valuePaint, valueMaxWidth)
            canvas.drawText(displayValue, leftX, y, valuePaint)
            drawIconBeforeRightLabel(canvas, field.icon, field.label, rightX, y - 8f, labelPaint, iconAtRight)

            y += rowHeight
            if (index < fields.lastIndex) {
                drawDivider(canvas, left, width, y - 44f)
                y += 24f
            }
        }

        return top + height
    }

    private fun drawStatusTrialCapsule(
        canvas: Canvas,
        subscription: Subscription,
        lang: AppLang,
        top: Float,
        left: Float,
        width: Float,
        iconAtRight: Boolean
    ): Float {
        val height = 154f
        drawCapsule(canvas, left, top, width, height)

        val labelPaint = labelPaint(Paint.Align.LEFT)
        val valuePaint = valuePaint(Paint.Align.LEFT, 38f)
        val centerY = top + height / 2f + 13f
        val leftGroupX = left + INNER_PAD
        val rightGroupX = left + width * 0.58f

        // Status group
        val statusLabelX: Float
        val statusAfterLabelX: Float
        if (iconAtRight) {
            statusLabelX = leftGroupX
            canvas.drawText(lang.lStatus, statusLabelX, centerY, labelPaint)
            statusAfterLabelX = statusLabelX + labelPaint.measureText(lang.lStatus) + 28f
            drawMiniIcon(canvas, IconKind.Status, statusAfterLabelX + 18f, centerY - 12f)
        } else {
            drawMiniIcon(canvas, IconKind.Status, leftGroupX + 18f, centerY - 12f)
            statusLabelX = leftGroupX + 54f
            canvas.drawText(lang.lStatus, statusLabelX, centerY, labelPaint)
            statusAfterLabelX = statusLabelX + labelPaint.measureText(lang.lStatus)
        }

        val statusText = if (subscription.isActive) lang.on else lang.off
        val badgeColor = if (subscription.isActive) COLOR_GREEN else COLOR_RED
        val badgeTextColor = if (subscription.isActive) Color.BLACK else Color.WHITE
        val badgeLeft = statusAfterLabelX + if (iconAtRight) 58f else 24f
        drawBadge(canvas, badgeLeft, centerY - 44f, statusText, badgeColor, badgeTextColor)

        // Trial group
        val trialLabelX: Float
        val trialAfterLabelX: Float
        if (iconAtRight) {
            trialLabelX = rightGroupX
            canvas.drawText(lang.lTrial, trialLabelX, centerY, labelPaint)
            trialAfterLabelX = trialLabelX + labelPaint.measureText(lang.lTrial) + 28f
            drawMiniIcon(canvas, IconKind.Trial, trialAfterLabelX + 18f, centerY - 12f)
        } else {
            drawMiniIcon(canvas, IconKind.Trial, rightGroupX + 18f, centerY - 12f)
            trialLabelX = rightGroupX + 54f
            canvas.drawText(lang.lTrial, trialLabelX, centerY, labelPaint)
            trialAfterLabelX = trialLabelX + labelPaint.measureText(lang.lTrial)
        }
        valuePaint.color = COLOR_TEXT_WHITE
        canvas.drawText(
            if (subscription.isTrial) lang.yes else lang.no,
            trialAfterLabelX + if (iconAtRight) 58f else 26f,
            centerY,
            valuePaint
        )

        return top + height
    }

    private fun drawContentCountsCapsule(
        canvas: Canvas,
        subscription: Subscription,
        lang: AppLang,
        top: Float,
        left: Float,
        width: Float
    ): Float {
        val height = 220f
        drawCapsule(canvas, left, top, width, height)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SEPARATOR
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }

        val col1 = left + width * 0.18f
        val sep1 = left + width * 0.34f
        val col2 = left + width * 0.50f
        val sep2 = left + width * 0.66f
        val col3 = left + width * 0.82f
        val labelY = top + 78f
        val countY = top + 155f

        canvas.drawText(lang.lChannels, col1, labelY, labelPaint)
        canvas.drawText(lang.lMovies, col2, labelY, labelPaint)
        canvas.drawText(lang.lSeries, col3, labelY, labelPaint)

        canvas.drawText(formatCount(subscription.liveCount), col1, countY, countPaint)
        canvas.drawText("|", sep1, countY, sepPaint)
        canvas.drawText(formatCount(subscription.movieCount), col2, countY, countPaint)
        canvas.drawText("|", sep2, countY, sepPaint)
        canvas.drawText(formatCount(subscription.seriesCount), col3, countY, countPaint)

        return top + height
    }

    private fun drawCapsule(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val rect = RectF(left, top, left + width, top + height)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_CAPSULE_BOTTOM }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_CAPSULE_TOP }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.drawRoundRect(rect, CAPSULE_RADIUS, CAPSULE_RADIUS, fillPaint)
        canvas.drawRoundRect(RectF(rect.left, rect.top, rect.right, rect.top + height * 0.52f), CAPSULE_RADIUS, CAPSULE_RADIUS, highlightPaint)
        canvas.drawRoundRect(rect, CAPSULE_RADIUS, CAPSULE_RADIUS, strokePaint)
    }

    private fun drawDivider(canvas: Canvas, left: Float, width: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            strokeWidth = 2.4f
        }
        canvas.drawLine(left + width * 0.08f, y, left + width * 0.92f, y, paint)
    }

    private fun drawIconBeforeRightLabel(
        canvas: Canvas,
        kind: IconKind,
        label: String,
        rightX: Float,
        centerY: Float,
        labelPaint: Paint,
        iconAtRight: Boolean
    ) {
        if (iconAtRight) {
            // Arabic: the visual beginning of the title is the right side, so
            // place the icon there before the title text.
            drawMiniIcon(canvas, kind, rightX - 18f, centerY)
            canvas.drawText(label, rightX - 54f, centerY + 13f, labelPaint)
        } else {
            canvas.drawText(label, rightX, centerY + 13f, labelPaint)
            val iconX = rightX - labelPaint.measureText(label) - 34f
            drawMiniIcon(canvas, kind, iconX, centerY)
        }
    }

    private fun drawMiniIcon(canvas: Canvas, kind: IconKind, cx: Float, cy: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT
            style = Paint.Style.FILL
        }

        when (kind) {
            IconKind.Server, IconKind.Status -> {
                canvas.drawLine(cx - 16f, cy + 14f, cx - 16f, cy - 2f, stroke)
                canvas.drawLine(cx, cy + 14f, cx, cy - 12f, stroke)
                canvas.drawLine(cx + 16f, cy + 14f, cx + 16f, cy - 22f, stroke)
            }
            IconKind.User, IconKind.Group -> {
                canvas.drawCircle(cx, cy - 12f, 10f, stroke)
                canvas.drawArc(RectF(cx - 22f, cy, cx + 22f, cy + 34f), 200f, 140f, false, stroke)
                if (kind == IconKind.Group) {
                    canvas.drawCircle(cx - 25f, cy - 7f, 7f, stroke)
                    canvas.drawCircle(cx + 25f, cy - 7f, 7f, stroke)
                }
            }
            IconKind.Key -> {
                canvas.drawCircle(cx - 12f, cy - 2f, 11f, stroke)
                canvas.drawLine(cx, cy - 2f, cx + 28f, cy - 2f, stroke)
                canvas.drawLine(cx + 18f, cy - 2f, cx + 18f, cy + 10f, stroke)
                canvas.drawLine(cx + 28f, cy - 2f, cx + 28f, cy + 10f, stroke)
            }
            IconKind.Calendar, IconKind.CalendarEnd -> {
                val r = RectF(cx - 22f, cy - 22f, cx + 22f, cy + 24f)
                canvas.drawRoundRect(r, 7f, 7f, stroke)
                canvas.drawLine(cx - 22f, cy - 8f, cx + 22f, cy - 8f, stroke)
                canvas.drawCircle(cx - 9f, cy + 6f, 3.5f, fill)
                canvas.drawCircle(cx + 9f, cy + 6f, 3.5f, fill)
                if (kind == IconKind.CalendarEnd) canvas.drawLine(cx + 7f, cy + 18f, cx + 18f, cy + 18f, stroke)
            }
            IconKind.Devices -> {
                canvas.drawRoundRect(RectF(cx - 24f, cy - 18f, cx + 12f, cy + 16f), 6f, 6f, stroke)
                canvas.drawRoundRect(RectF(cx + 5f, cy - 5f, cx + 26f, cy + 22f), 5f, 5f, stroke)
            }
            IconKind.Trial -> {
                canvas.drawLine(cx - 10f, cy - 24f, cx - 10f, cy + 3f, stroke)
                canvas.drawLine(cx + 10f, cy - 24f, cx + 10f, cy + 3f, stroke)
                canvas.drawArc(RectF(cx - 22f, cy - 2f, cx + 22f, cy + 34f), 0f, 180f, false, stroke)
                canvas.drawCircle(cx, cy + 11f, 4f, fill)
            }
        }
    }

    private fun drawBadge(
        canvas: Canvas,
        left: Float,
        top: Float,
        text: String,
        bgColor: Int,
        textColor: Int
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        val width = textPaint.measureText(text).coerceAtLeast(118f) + 84f
        val height = 70f
        val rect = RectF(left, top, left + width, top + height)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
        canvas.drawText(text, rect.centerX(), rect.centerY() + 14f, textPaint)
    }

    private fun labelPaint(align: Paint.Align): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT_DIM
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = align
        isSubpixelText = true
    }

    private fun valuePaint(align: Paint.Align, size: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT_WHITE
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = align
        isSubpixelText = true
    }

    private fun formatCount(count: String): String {
        return try {
            val n = count.toLong()
            String.format("%,d", n)
        } catch (_: Exception) {
            count
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return if (end <= 0) "…" else text.substring(0, end) + "…"
    }
}
