package com.kinepulse.player.ui.player

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView
import kotlin.math.min
import kotlin.math.roundToInt

enum class AspectRatioMode(val id: Int, val label: String) {
    BEST_FIT(0, "Best fit"),
    FIT_SCREEN(1, "Fit screen"),
    FILL(2, "Fill (Crop)"),
    RATIO_16_9(3, "16:9"),
    RATIO_4_3(4, "4:3"),
    RATIO_16_10(5, "16:10"),
    RATIO_235_1(6, "2.35:1"),
    CENTER(7, "Center (Original)")
}

class ResizableVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    private var videoWidthPx: Int = 0
    private var videoHeightPx: Int = 0
    var aspectMode: AspectRatioMode = AspectRatioMode.BEST_FIT
        set(value) {
            field = value
            requestLayout()
        }

    var userZoomScale: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 3.0f)
            requestLayout()
        }

    fun setVideoDimensions(width: Int, height: Int) {
        this.videoWidthPx = width
        this.videoHeightPx = height
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (videoWidthPx <= 0 || videoHeightPx <= 0 || parentWidth <= 0 || parentHeight <= 0) {
            setMeasuredDimension(parentWidth, parentHeight)
            return
        }

        val naturalAspect = videoWidthPx.toFloat() / videoHeightPx.toFloat()
        val parentAspect = parentWidth.toFloat() / parentHeight.toFloat()

        var measuredW = parentWidth
        var measuredH = parentHeight

        when (aspectMode) {
            AspectRatioMode.BEST_FIT -> {
                if (naturalAspect > parentAspect) {
                    measuredH = (parentWidth / naturalAspect).roundToInt()
                } else {
                    measuredW = (parentHeight * naturalAspect).roundToInt()
                }
            }
            AspectRatioMode.FIT_SCREEN -> {
                measuredW = parentWidth
                measuredH = parentHeight
            }
            AspectRatioMode.FILL -> {
                if (naturalAspect > parentAspect) {
                    measuredW = (parentHeight * naturalAspect).roundToInt()
                } else {
                    measuredH = (parentWidth / naturalAspect).roundToInt()
                }
            }
            AspectRatioMode.RATIO_16_9 -> applyFixedRatio(16f / 9f, parentWidth, parentHeight, parentAspect) { w, h ->
                measuredW = w; measuredH = h
            }
            AspectRatioMode.RATIO_4_3 -> applyFixedRatio(4f / 3f, parentWidth, parentHeight, parentAspect) { w, h ->
                measuredW = w; measuredH = h
            }
            AspectRatioMode.RATIO_16_10 -> applyFixedRatio(16f / 10f, parentWidth, parentHeight, parentAspect) { w, h ->
                measuredW = w; measuredH = h
            }
            AspectRatioMode.RATIO_235_1 -> applyFixedRatio(2.35f, parentWidth, parentHeight, parentAspect) { w, h ->
                measuredW = w; measuredH = h
            }
            AspectRatioMode.CENTER -> {
                measuredW = min(videoWidthPx, parentWidth)
                measuredH = min(videoHeightPx, parentHeight)
            }
        }

        val scaledW = (measuredW * userZoomScale).roundToInt().coerceAtLeast(1)
        val scaledH = (measuredH * userZoomScale).roundToInt().coerceAtLeast(1)
        setMeasuredDimension(scaledW, scaledH)
    }

    private inline fun applyFixedRatio(
        targetAspect: Float,
        parentW: Int,
        parentH: Int,
        parentAspect: Float,
        out: (Int, Int) -> Unit
    ) {
        if (targetAspect > parentAspect) {
            out(parentW, (parentW / targetAspect).roundToInt())
        } else {
            out((parentH * targetAspect).roundToInt(), parentH)
        }
    }
}
