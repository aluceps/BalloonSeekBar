package me.aluceps.balloonseekbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import java.util.*

class BalloonSeekBar @JvmOverloads constructor(
        context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        setup(context, attrs, defStyleAttr)
    }

    private var valueMax = DEFAULT_MAX
    private var valueCurrent = DEFAULT_VALUE
    private var colorBackground = DEFAULT_BACKGROUND
    private var colorForeground = DEFAULT_FOREGROUND
    private var backgroundStrokeWidth = DEFAULT_BACKGROUND_STROKE_WIDTH

    private fun setup(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) {
        context?.obtainStyledAttributes(attrs, R.styleable.BalloonSeekBar, defStyleAttr, 0)?.apply {
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_max, DEFAULT_MAX).let { valueMax = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_value, DEFAULT_VALUE).let { valueCurrent = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_background, DEFAULT_BACKGROUND).let { colorBackground = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_foreground, DEFAULT_FOREGROUND).let { colorForeground = it }
            getDimension(R.styleable.BalloonSeekBar_balloon_seekbar_stroke_width, DEFAULT_BACKGROUND_STROKE_WIDTH).let { backgroundStrokeWidth = it }
        }?.let {
            it.recycle()
        }

        Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    post { invalidate() }
                }
            }, 10, 10)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DEFAULT_MAX = 100
        private const val DEFAULT_VALUE = 0
        private const val DEFAULT_BACKGROUND = Color.GRAY
        private const val DEFAULT_FOREGROUND = Color.GREEN
        private const val DEFAULT_BACKGROUND_STROKE_WIDTH = 0f
    }
}