package com.alhosan.checker.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.Subscription
import com.alhosan.checker.ui.i18n.*

/**
 * Renders the result card to a Bitmap using Android's native Canvas API.
 *
 * This replaces the html2canvas approach from the HTML reference and the
 * Compose GraphicsLayer capture (which requires experimental Compose 1.7+ APIs).
 * Using native Canvas means it works on any Compose version and is fully
 * under our control for layout and styling.
 *
 * Output dimensions: 1080 x 1620 pixels (3:4.5 aspect, suitable for sharing).
 */
object ResultImageRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1700
    private const val PADDING = 50f
    private const val CARD_RADIUS = 36f

    // Colors matching the app theme
    private val COLOR_BG = Color.BLACK
    private val COLOR_CARD_BG = Color.parseColor("#0A0A0A")
    private val COLOR_BORDER = Color.parseColor("#1F1A0F")
    private val COLOR_ACCENT = Color.parseColor("#D4AF37")
    private val COLOR_ACCENT_LIGHT = Color.parseColor("#FFDF00")
    private val COLOR_TEXT_DIM = Color.parseColor("#A0A0A0")
    private val COLOR_TEXT_WHITE = Color.WHITE
    private val COLOR_GREEN = Color.parseColor("#00E676")
    private val COLOR_RED = Color.parseColor("#FF1744")
    private val COLOR_DIVIDER = Color.parseColor("#1F1A0F")

    /**
     * Render the subscription data to a shareable Bitmap.
     */
    fun render(subscription: Subscription, lang: AppLang): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BG }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        var y = PADDING + 40f

        // App title (top)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(lang.splash, WIDTH / 2f, y, titlePaint)
        y += 80f

        // Card background
        val cardRect = RectF(
            PADDING, y,
            WIDTH - PADDING, HEIGHT - PADDING
        )
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_CARD_BG }
        canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, cardPaint)

        // Card border (gold)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, borderPaint)

        y += 50f

        // Section: Host / Username / Password (stacked)
        y = drawStackedField(canvas, lang.lHost, subscription.host, y, isLtr = true)
        y = drawDivider(canvas, y)
        y = drawStackedField(canvas, lang.lUser, subscription.username, y)
        y = drawDivider(canvas, y)
        y = drawStackedField(canvas, lang.lPass, subscription.password, y)

        y += 30f

        // Section: Created / Expiry (stacked)
        y = drawStackedField(canvas, lang.lCreated, subscription.created, y)
        y = drawDivider(canvas, y)
        y = drawStackedField(canvas, lang.lExpiry, subscription.expiry, y)

        y += 30f

        // Section: Status + Trial (side by side)
        y = drawStatusRow(canvas, subscription, lang, y)

        y += 30f

        // Section: Active connections / Max connections
        y = drawStackedField(canvas, lang.lDevices, subscription.activeCons, y)
        y = drawDivider(canvas, y)
        y = drawStackedField(canvas, lang.lMaxCons, subscription.maxCons, y)

        y += 30f

        // Section: Content counts
        y = drawContentCounts(canvas, subscription, lang, y)

        return bitmap
    }

    private fun drawStackedField(
        canvas: Canvas,
        label: String,
        value: String,
        y: Float,
        isLtr: Boolean = false
    ): Float {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Label
        canvas.drawText(label, WIDTH / 2f, y, labelPaint)

        // Value (truncate if too long)
        val maxValueWidth = (WIDTH - 2 * PADDING - 100).toFloat()
        val displayValue = ellipsize(value, valuePaint, maxValueWidth)
        canvas.drawText(displayValue, WIDTH / 2f, y + 50f, valuePaint)

        return y + 80f
    }

    private fun drawDivider(canvas: Canvas, y: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIVIDER
            strokeWidth = 2f
        }
        val startX = WIDTH * 0.15f
        val endX = WIDTH * 0.85f
        canvas.drawLine(startX, y, endX, y, paint)
        return y + 20f
    }

    private fun drawStatusRow(
        canvas: Canvas,
        subscription: Subscription,
        lang: AppLang,
        y: Float
    ): Float {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Left half: Status
        val leftCenter = WIDTH * 0.25f
        canvas.drawText(lang.lStatus, leftCenter - labelPaint.measureText(lang.lStatus) / 2, y, labelPaint)

        val statusText = if (subscription.isActive) lang.on else lang.off
        val statusColor = if (subscription.isActive) COLOR_GREEN else COLOR_RED
        val statusTextColor = if (subscription.isActive) Color.BLACK else Color.WHITE

        // Status badge background
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
        val badgeWidth = 220f
        val badgeHeight = 56f
        val badgeRect = RectF(
            leftCenter - badgeWidth / 2,
            y + 20f,
            leftCenter + badgeWidth / 2,
            y + 20f + badgeHeight
        )
        canvas.drawRoundRect(badgeRect, 28f, 28f, badgePaint)

        valuePaint.color = statusTextColor
        canvas.drawText(statusText, leftCenter, y + 20f + badgeHeight / 2 + 12f, valuePaint)

        // Right half: Trial
        val rightCenter = WIDTH * 0.75f
        canvas.drawText(lang.lTrial, rightCenter - labelPaint.measureText(lang.lTrial) / 2, y, labelPaint)
        val trialText = if (subscription.isTrial) lang.yes else lang.no
        valuePaint.color = COLOR_TEXT_WHITE
        canvas.drawText(trialText, rightCenter, y + 60f, valuePaint)

        return y + 100f
    }

    private fun drawContentCounts(
        canvas: Canvas,
        subscription: Subscription,
        lang: AppLang,
        y: Float
    ): Float {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Three columns: Channels | Movies | Series
        val col1 = WIDTH * 0.2f
        val col2 = WIDTH * 0.5f
        val col3 = WIDTH * 0.8f

        // Labels
        canvas.drawText(lang.lChannels, col1, y, labelPaint)
        canvas.drawText(lang.lMovies, col2, y, labelPaint)
        canvas.drawText(lang.lSeries, col3, y, labelPaint)

        // Counts
        canvas.drawText(formatCount(subscription.liveCount), col1, y + 80f, countPaint)
        canvas.drawText("|", col2, y + 80f, sepPaint)
        canvas.drawText(formatCount(subscription.movieCount), col3 - 20f, y + 80f, countPaint)
        // Re-draw series at col3 (the | is at col2)
        canvas.drawText(formatCount(subscription.seriesCount), col3, y + 80f, countPaint)

        return y + 130f
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
        return text.substring(0, end) + "…"
    }
}
