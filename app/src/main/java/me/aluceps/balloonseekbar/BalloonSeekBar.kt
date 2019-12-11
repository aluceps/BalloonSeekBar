package me.aluceps.balloonseekbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.*

class BalloonSeekBar @JvmOverloads constructor(
        context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var valueMax = DEFAULT_MAX
    private var valueCurrent = DEFAULT_VALUE
    private var colorBackground = DEFAULT_BACKGROUND
    private var colorForeground = DEFAULT_FOREGROUND
    private var backgroundStrokeWidth = DEFAULT_BACKGROUND_STROKE_WIDTH
    private val foregroundStrokeWidth get() = backgroundStrokeWidth * FOREGROUND_BIAS
    private val backgroundRadius get() = backgroundStrokeWidth / 2
    private val foregroundRadius get() = foregroundStrokeWidth / 2

    init {
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

    private val paintBackground by lazy {
        Paint().apply {
            isAntiAlias = true
            color = colorBackground
            style = Paint.Style.FILL
            strokeWidth = backgroundStrokeWidth
        }
    }

    private val paintForeground by lazy {
        Paint().apply {
            isAntiAlias = true
            color = colorForeground
            style = Paint.Style.FILL
            strokeWidth = foregroundStrokeWidth
        }
    }

    private val contentSize = BalloonView(0, 0, 0, 0)
    private val contentDiff = foregroundStrokeWidth - backgroundStrokeWidth

    // 前景よりも背景のほうが細いので縦位置を前景の中心に寄せる
    private val rectBackground by lazy {
        contentSize.toFloat().let { v -> RectF(v.left, v.top + contentDiff, v.right, v.top + backgroundStrokeWidth) }
    }

    // はじめは SeekBar の値はゼロなので右端は開始点に設定する
    private val rectForeground by lazy {
        contentSize.toFloat().let { v -> RectF(v.left, v.top, v.left, v.top + foregroundStrokeWidth) }
    }

    // 初期値はゼロなので View の左端に設定する
    private var currentX = paddingLeft.toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        contentSize.apply {
            top = paddingTop
            left = paddingLeft
            bottom = measuredHeight - paddingBottom
            right = measuredWidth - paddingRight
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let { c ->
            drawBackgroundStroke(c)
            drawForegroundStroke(c)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> moveProgress(event.x)
            MotionEvent.ACTION_MOVE -> moveProgress(event.x)
            MotionEvent.ACTION_UP -> Unit
            MotionEvent.ACTION_CANCEL -> Unit
        }
        return true
    }

    private fun drawBackgroundStroke(canvas: Canvas) {
        canvas.drawRoundRect(rectBackground, backgroundRadius, backgroundRadius, paintBackground)
    }

    private fun drawForegroundStroke(canvas: Canvas) {
        rectForeground.apply {
            right = currentX
        }.let {
            canvas.drawRoundRect(it, foregroundRadius, foregroundRadius, paintForeground)
        }
    }

    private fun moveProgress(value: Float) {
        when {
            // 範囲外
            contentSize.left > value -> {
                currentX = contentSize.toFloat().left
            }
            contentSize.right < value -> {
                currentX = contentSize.toFloat().right
            }
            // 範囲内
            contentSize.left <= value && contentSize.right >= value -> {
                currentX = value
            }
        }
    }

    data class BalloonView(var top: Int, var left: Int, var bottom: Int, var right: Int) {
        val width = right - left
        val height = bottom - top
        fun toFloat() = BalloonViewF(top.toFloat(), left.toFloat(), bottom.toFloat(), right.toFloat())
    }

    data class BalloonViewF(var top: Float, var left: Float, var bottom: Float, var right: Float) {
        val width = right - left
        val height = bottom - top
    }

    companion object {
        private const val DEFAULT_MAX = 100
        private const val DEFAULT_VALUE = 0
        private const val DEFAULT_BACKGROUND = Color.GRAY
        private const val DEFAULT_FOREGROUND = Color.GREEN
        private const val DEFAULT_BACKGROUND_STROKE_WIDTH = 0f
        private const val FOREGROUND_BIAS = 1.1f
    }
}