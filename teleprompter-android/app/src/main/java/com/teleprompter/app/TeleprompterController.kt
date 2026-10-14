package com.teleprompter.app

import android.view.Choreographer
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision scrolling engine for Android teleprompter.
 * Uses Choreographer frame callbacks to ensure 60Hz/120Hz ultra-smooth sub-pixel scrolling
 * without jitter or battery drain when paused.
 */
class TeleprompterController(
    private val scrollView: ScrollView,
    private val textView: TextView,
    private val onStateChanged: (isPlaying: Boolean, speed: Float) -> Unit
) : Choreographer.FrameCallback {

    var isPlaying: Boolean = false
        private set

    /**
     * Speed factor: 1.0x to 10.0x.
     * 1.0x ≈ 25 dp/second (slow, deliberate reading)
     * 3.0x ≈ 75 dp/second (normal conversational pace)
     * 10.0x ≈ 250 dp/second (fast scan)
     */
    var speed: Float = 3.0f
        set(value) {
            field = max(1.0f, min(10.0f, value))
            onStateChanged(isPlaying, field)
        }

    var fontSizeSp: Float = 22f
        set(value) {
            field = max(14f, min(40f, value))
            textView.textSize = field
        }

    var isMirrored: Boolean = false
        set(value) {
            field = value
            textView.scaleX = if (value) -1f else 1f
        }

    private var lastFrameTimeNanos: Long = 0L
    private var accumulatedScrollPx: Float = 0f
    private val density = scrollView.resources.displayMetrics.density

    fun start() {
        if (isPlaying) return
        isPlaying = true
        lastFrameTimeNanos = 0L
        accumulatedScrollPx = 0f
        Choreographer.getInstance().postFrameCallback(this)
        onStateChanged(true, speed)
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        Choreographer.getInstance().removeFrameCallback(this)
        onStateChanged(false, speed)
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else start()
    }

    fun resetToTop() {
        pause()
        scrollView.smoothScrollTo(0, 0)
        accumulatedScrollPx = 0f
    }

    fun increaseSpeed(delta: Float = 0.5f) {
        speed += delta
    }

    fun decreaseSpeed(delta: Float = 0.5f) {
        speed -= delta
    }

    fun setText(paragraphs: List<String>) {
        val fullText = paragraphs.joinToString(separator = "\n\n")
        textView.text = fullText
        resetToTop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isPlaying) return

        if (lastFrameTimeNanos > 0L) {
            val deltaSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f

            // Calculate pixels to scroll this frame: (Base 25 dp/s) * speed * density
            val pixelsPerSecond = (25f * speed) * density
            accumulatedScrollPx += pixelsPerSecond * deltaSeconds

            if (accumulatedScrollPx >= 1f) {
                val pxToScroll = accumulatedScrollPx.toInt()
                scrollView.scrollBy(0, pxToScroll)
                accumulatedScrollPx -= pxToScroll
            }

            // Check if reached bottom
            val maxScrollY = textView.bottom - scrollView.height
            if (maxScrollY > 0 && scrollView.scrollY >= maxScrollY) {
                pause()
                return
            }
        }

        lastFrameTimeNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun destroy() {
        pause()
    }
}
