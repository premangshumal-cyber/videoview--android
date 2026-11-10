package com.kinepulse.player.player.subtitle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class SubtitleRendererView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var cues: List<SubtitleCue> = emptyList()
    private var activeText: String = ""
    private var styleConfig = SubtitleStyleConfig()

    private val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgRect = RectF()

    init {
        applyStyle(styleConfig)
    }

    fun setCues(newCues: List<SubtitleCue>) {
        cues = newCues
        invalidate()
    }

    fun applyStyle(config: SubtitleStyleConfig) {
        styleConfig = config
        val pxSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            config.textSizeSp,
            resources.displayMetrics
        )
        val tf = if (config.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        fillPaint.textSize = pxSize
        fillPaint.typeface = tf
        fillPaint.color = mergeAlpha(config.textColorRgb, config.textOpacity)
        if (config.hasShadow) {
            fillPaint.setShadowLayer(
                6f, 2f, 2f,
                mergeAlpha(config.shadowColorRgb, config.shadowOpacity)
            )
        } else {
            fillPaint.clearShadowLayer()
        }

        strokePaint.textSize = pxSize
        strokePaint.typeface = tf
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = config.outlineSizePx * resources.displayMetrics.density
        strokePaint.color = mergeAlpha(config.outlineColorRgb, config.outlineOpacity)

        bgPaint.color = mergeAlpha(config.bgColorRgb, config.bgOpacity)
        invalidate()
    }

    fun updatePlaybackPosition(positionMs: Long, delayMs: Long) {
        if (cues.isEmpty()) {
            if (activeText.isNotEmpty()) {
                activeText = ""
                invalidate()
            }
            return
        }
        val effectiveTime = positionMs - delayMs
        val matched = cues.filter { effectiveTime in it.startTimeMs..it.endTimeMs }
            .joinToString("\n") { it.text }
        if (matched != activeText) {
            activeText = matched
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeText.isEmpty() || width <= 0 || height <= 0) return

        val maxTextWidth = (width * 0.86f).toInt().coerceAtLeast(100)
        val fillLayout = StaticLayout.Builder.obtain(activeText, 0, activeText.length, fillPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        val bottomMargin = 38f * resources.displayMetrics.density
        val transX = (width - maxTextWidth) / 2f
        val transY = height - fillLayout.height - bottomMargin

        canvas.save()
        canvas.translate(transX, transY)

        if (styleConfig.hasBackground) {
            val pad = 8f * resources.displayMetrics.density
            bgRect.set(-pad, -pad, maxTextWidth + pad, fillLayout.height + pad)
            canvas.drawRoundRect(bgRect, pad, pad, bgPaint)
        }

        if (styleConfig.hasOutline) {
            val strokeLayout = StaticLayout.Builder.obtain(activeText, 0, activeText.length, strokePaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            strokeLayout.draw(canvas)
        }

        fillLayout.draw(canvas)
        canvas.restore()
    }

    private fun mergeAlpha(rgb: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            (rgb shr 16) and 0xFF,
            (rgb shr 8) and 0xFF,
            rgb and 0xFF
        )
    }
}
