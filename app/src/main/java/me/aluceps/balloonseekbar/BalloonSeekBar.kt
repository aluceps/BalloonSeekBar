package me.aluceps.balloonseekbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.*
import kotlin.math.truncate

interface OnChangeListener {
    fun progress(percentage: Float)
    fun progress(value: Int)
}

class BalloonSeekBar @JvmOverloads constructor(
        context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var valueMax = DEFAULT_MAX
    private var valueCurrent = DEFAULT_VALUE
    private var colorBackground = DEFAULT_BACKGROUND
    private var colorForeground = DEFAULT_FOREGROUND
    private var backgroundStrokeWidth = DEFAULT_BACKGROUND_STROKE_WIDTH
    private val foregroundStrokeWidth by lazy { backgroundStrokeWidth * FOREGROUND_BIAS }
    private val backgroundRadius by lazy { backgroundStrokeWidth / 2 }
    private val foregroundRadius by lazy { foregroundStrokeWidth / 2 }
    private val thumbRadius = DEFAULT_THUMB_RADIUS
    private var resourceThumb: Drawable? = null

    init {
        context?.obtainStyledAttributes(attrs, R.styleable.BalloonSeekBar, defStyleAttr, 0)?.apply {
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_max, DEFAULT_MAX).let { valueMax = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_value, DEFAULT_VALUE).let { valueCurrent = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_background, DEFAULT_BACKGROUND).let { colorBackground = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_foreground, DEFAULT_FOREGROUND).let { colorForeground = it }
            getDimension(R.styleable.BalloonSeekBar_balloon_seekbar_stroke_width, DEFAULT_BACKGROUND_STROKE_WIDTH).let { backgroundStrokeWidth = it }
            getDrawable(R.styleable.BalloonSeekBar_balloon_seekbar_thumb)?.let { resourceThumb = it }
        }?.recycle()

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

    private val paintThumb by lazy {
        Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }

    private val paintThumbBorder by lazy {
        Paint().apply {
            isAntiAlias = true
            color = Color.argb(20, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
    }

    // View のサイズをもっておくためのもの
    private val contentSize = BalloonView(0, 0, 0, 0)
    private val contentDiff by lazy { foregroundStrokeWidth - backgroundStrokeWidth }

    // 前景よりも背景のほうが細いので縦位置を前景の中心に寄せる
    private val rectBackground by lazy { contentSize.let { v -> RectF(v.leftF, v.topF + contentDiff, v.rightF, v.topF + backgroundStrokeWidth) } }
    // はじめは SeekBar の値はゼロなので右端は開始点に設定する
    private val rectForeground by lazy { contentSize.let { v -> RectF(v.leftF, v.topF, v.leftF, v.topF + foregroundStrokeWidth) } }

    // 初期値はゼロなので View の左端に設定する
    private var currentX = paddingLeft.toFloat()
    private val currentProgress get() = contentSize.let { (currentX - it.leftF) / it.widthF }
    private val currentValue get() = valueMax * currentProgress

    private val thumbY by lazy { rectBackground.top + backgroundStrokeWidth / 2 }
    private val thumbBitmap by lazy { resourceThumb?.createBitmap() }

    private var listener: OnChangeListener? = null

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
            drawThumb(c)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> if (rectBackground.top <= event.y && rectBackground.bottom >= event.y) {
                moveProgress(event.x)
            }
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

    private fun drawThumb(canvas: Canvas) {
        if (thumbBitmap == null) {
            canvas.drawCircle(currentX, thumbY, thumbRadius, paintThumb)
            canvas.drawCircle(currentX, thumbY, thumbRadius, paintThumbBorder)
        } else {
            thumbBitmap?.let {
                val src = Rect(0, 0, it.width, it.height)
                val dest = Rect((currentX - it.width).toInt(), (thumbY - it.height).toInt(), (currentX + it.width).toInt(), (thumbY + it.height).toInt())
                canvas.drawBitmap(it, src, dest, null)
            }
        }
    }

    private fun moveProgress(x: Float) {
        when {
            // 範囲外
            contentSize.left > x -> {
                currentX = contentSize.leftF
                listener?.progress(currentProgress)
                listener?.progress(truncate(currentValue).toInt())
            }
            contentSize.right < x -> {
                currentX = contentSize.rightF
                listener?.progress(currentProgress)
                listener?.progress(truncate(currentValue).toInt())
            }
            // 範囲内
            contentSize.left <= x && contentSize.right >= x -> {
                currentX = x
                listener?.progress(currentProgress)
                listener?.progress(truncate(currentValue).toInt())
            }
        }
    }

    fun setOnChangeListenr(listener: OnChangeListener) {
        this.listener = listener
    }

    private fun Drawable.createBitmap() =
            Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888).also {
                Canvas(it).let { c ->
                    setBounds(0, 0, c.width, c.height)
                    draw(c)
                }
            }

    data class BalloonView(var top: Int, var left: Int, var bottom: Int, var right: Int) {
        val topF get() = top.toFloat()
        val leftF get() = left.toFloat()
        val bottomF get() = bottom.toFloat()
        val rightF get() = right.toFloat()
        val widthF get() = rightF - leftF
        val heightF get() = bottomF - topF
    }

    companion object {
        private const val DEFAULT_MAX = 100
        private const val DEFAULT_VALUE = 0
        private const val DEFAULT_BACKGROUND = Color.GRAY
        private const val DEFAULT_FOREGROUND = Color.GREEN
        private const val DEFAULT_BACKGROUND_STROKE_WIDTH = 0f
        private const val DEFAULT_THUMB_RADIUS = 24f
        private const val FOREGROUND_BIAS = 1.1f
    }
}