package com.kinepulse.player.ui.player

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.kinepulse.player.data.prefs.AppPrefs
import kotlin.math.abs

class PlayerGestureHelper(
    context: Context,
    private val prefs: AppPrefs,
    private val listener: Listener
) {
    interface Listener {
        fun onSingleTap()
        fun onDoubleTapSeek(forward: Boolean)
        fun onDoubleTapCenterPlayPause()
        fun onBrightnessSwipe(deltaRatio: Float)
        fun onVolumeSwipe(deltaRatio: Float)
        fun onHorizontalSeekPreview(deltaMs: Long)
        fun onHorizontalSeekCommit(deltaMs: Long)
        fun onPinchZoom(scaleFactor: Float)
        fun onFastplayStateChanged(active: Boolean, speed: Float)
        fun onThreeFingerScreenshot()
    }

    private var viewWidth = 1
    private var viewHeight = 1
    private var isSeekingHorizontal = false
    private var isFastplayActive = false
    private var accumulatedSeekDeltaMs = 0L
    private val edgeExclusionPx = 32f * context.resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!prefs.enableScaleGesture) return false
            listener.onPinchZoom(detector.scaleFactor)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val x = e.x
            val leftZone = viewWidth * 0.35f
            val rightZone = viewWidth * 0.65f
            when {
                x < leftZone && prefs.enableDoubleTapSeek -> listener.onDoubleTapSeek(false)
                x > rightZone && prefs.enableDoubleTapSeek -> listener.onDoubleTapSeek(true)
                prefs.enableDoubleTapPlay -> listener.onDoubleTapCenterPlayPause()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (prefs.enableFastplay) {
                isFastplayActive = true
                listener.onFastplayStateChanged(true, prefs.fastplaySpeed)
            }
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val start = e1 ?: return false
            if (start.x < edgeExclusionPx || start.x > viewWidth - edgeExclusionPx ||
                start.y < edgeExclusionPx || start.y > viewHeight - edgeExclusionPx
            ) {
                return false
            }

            if (abs(distanceX) > abs(distanceY) && prefs.enableSwipeSeek) {
                isSeekingHorizontal = true
                val deltaMs = ((-distanceX / viewWidth) * 90_000L).toLong()
                accumulatedSeekDeltaMs += deltaMs
                listener.onHorizontalSeekPreview(accumulatedSeekDeltaMs)
                return true
            } else if (!isSeekingHorizontal) {
                val deltaRatio = distanceY / (viewHeight * 0.75f)
                if (start.x < viewWidth / 2f && prefs.enableBrightnessGesture) {
                    listener.onBrightnessSwipe(deltaRatio)
                    return true
                } else if (start.x >= viewWidth / 2f && prefs.enableVolumeGesture) {
                    listener.onVolumeSwipe(deltaRatio)
                    return true
                }
            }
            return false
        }
    })

    fun setSurfaceSize(width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount == 3) {
            if (prefs.screenshotMode == "gesture" || prefs.screenshotMode == "both") {
                listener.onThreeFingerScreenshot()
                return true
            }
        }
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (isFastplayActive) {
                isFastplayActive = false
                listener.onFastplayStateChanged(false, 1.0f)
            }
            if (isSeekingHorizontal) {
                isSeekingHorizontal = false
                listener.onHorizontalSeekCommit(accumulatedSeekDeltaMs)
                accumulatedSeekDeltaMs = 0L
            }
        }
        return true
    }
}
