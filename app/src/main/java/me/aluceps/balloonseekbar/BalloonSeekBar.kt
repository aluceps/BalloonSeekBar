package me.aluceps.balloonseekbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.databinding.BindingAdapter
import java.util.*
import kotlin.math.round
import kotlin.math.truncate

interface OnChangeListener {
    fun progress(percentage: Float)
    fun progress(value: Int)
}

class BalloonSeekBar @JvmOverloads constructor(
        context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // スライダーの基本的な設定値
    private var valueMax = DEFAULT_MAX
    private var valueCurrent = DEFAULT_VALUE
    private var colorBackground = DEFAULT_BACKGROUND
    private var colorForeground = DEFAULT_FOREGROUND
    private var backgroundStrokeWidth = DEFAULT_BACKGROUND_STROKE_WIDTH
    private val foregroundStrokeWidth by lazy { backgroundStrokeWidth * FOREGROUND_BIAS }
    private val backgroundRadius by lazy { backgroundStrokeWidth / 2 }
    private val foregroundRadius by lazy { foregroundStrokeWidth / 2 }

    // つまみの設定値
    private val thumbRadius = DEFAULT_THUMB_RADIUS
    private val thumbBorderWidth = DEFAULT_THUMB_BORDER_WIDTH
    private var resourceThumb: Drawable? = null
    private var resourceThumbScale = DEFAULT_RESOURCE_SCALE

    // 吹き出しの設定値
    private var balloonTextSize = DEFAULT_TEXT_SIZE
    private var balloonTextColor = DEFAULT_TEXT_COLOR
    private val balloonHeight by lazy { valueTextSizeF * 1.5f }
    private val valueTextSize by lazy { balloonTextSize.toInt() }
    private val valueTextSizeF by lazy { balloonTextSize }
    private var resourceBalloon: Drawable? = null
    private var resourceBalloonScale = DEFAULT_RESOURCE_SCALE

    init {
        context?.obtainStyledAttributes(attrs, R.styleable.BalloonSeekBar, defStyleAttr, 0)?.apply {
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_max, DEFAULT_MAX).let { valueMax = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_value, DEFAULT_VALUE).let { valueCurrent = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_background, DEFAULT_BACKGROUND).let { colorBackground = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_foreground, DEFAULT_FOREGROUND).let { colorForeground = it }
            getDimension(R.styleable.BalloonSeekBar_balloon_seekbar_stroke_width, DEFAULT_BACKGROUND_STROKE_WIDTH).let { backgroundStrokeWidth = it }
            getDrawable(R.styleable.BalloonSeekBar_balloon_seekbar_thumb)?.let { resourceThumb = it }
            getFloat(R.styleable.BalloonSeekBar_balloon_seekbar_thumb_scale, DEFAULT_RESOURCE_SCALE).let { resourceThumbScale = it }
            getDimension(R.styleable.BalloonSeekBar_balloon_seekbar_text_size, DEFAULT_TEXT_SIZE).let { balloonTextSize = it }
            getInt(R.styleable.BalloonSeekBar_balloon_seekbar_text_color, DEFAULT_TEXT_COLOR).let { balloonTextColor = it }
            getDrawable(R.styleable.BalloonSeekBar_balloon_seekbar_balloon)?.let { resourceBalloon = it }
            getFloat(R.styleable.BalloonSeekBar_balloon_seekbar_balloon_scale, DEFAULT_RESOURCE_SCALE).let { resourceBalloonScale = it }
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
            strokeWidth = thumbBorderWidth
        }
    }

    private val paintText by lazy {
        Paint().apply {
            isAntiAlias = true
            color = balloonTextColor
            textSize = valueTextSizeF
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    private val paintBalloon by lazy {
        Paint().apply {
            isAntiAlias = true
            color = Color.argb(30, 0, 0, 0)
            style = Paint.Style.FILL
        }
    }

    // SeekBar のサイズをもっておくためのもの
    private val contentSize = BalloonView(0, 0, 0, 0)

    // 動く SeekBar の高さの中心にベースの SeekBar を配置するためそれぞれの高さを考慮する
    private val rectBackground by lazy {
        contentSize.let { v ->
            RectF(v.leftF, v.topF + (foregroundStrokeWidth - backgroundStrokeWidth), v.rightF, v.topF + backgroundStrokeWidth)
        }
    }

    // 動く SeekBar の初期値はゼロなのでx軸は左端に揃える
    private val rectForeground by lazy {
        contentSize.let { v ->
            RectF(v.leftF, v.topF, v.leftF, v.topF + foregroundStrokeWidth)
        }
    }

    // つまみの情報
    private val thumbY by lazy { rectBackground.top + backgroundStrokeWidth / 2 }
    private val thumbBitmap by lazy { resourceThumb?.createBitmap(resourceThumbScale) }
    private val balloonBitmap by lazy { resourceBalloon?.createBitmap(resourceBalloonScale) }

    private var seekBarProgress = 0f
    private val currentPercentage get() = contentSize.let { (seekBarProgress - it.leftF) / it.widthF }
    private val currentValue get() = valueMax * currentPercentage

    private var listener: OnChangeListener? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // レイアウト時のサイズを初期化する
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // 何度も計算する内容ではないので初回の onLayout のみ
        if (!changed) return

        // テキストが見きれないようにテキストの幅を考慮してサイズを設定する
        contentSize.apply {
            this.top = paddingTop + balloonHeight.toInt()
            this.left = paddingLeft + valueTextSize
            this.bottom = measuredHeight - paddingBottom + balloonHeight.toInt()
            this.right = measuredWidth - paddingRight - valueTextSize
        }

        // 初期値は SeekBar の左端にする
        seekBarProgress = contentSize.leftF
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let { c ->
            drawBackgroundSeekBar(c)
            drawForegroundSeekBar(c)
            drawThumb(c)

            // 完全なゼロのときは表示しない
            if (currentPercentage > 0f) {
                drawBalloon(c)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (thumbBitmap != null && thumbBitmap?.isRecycled == false) {
            thumbBitmap?.recycle()
        }
    }

    // つまみをタッチする時は SeekBar の描画領域に限定して
    // つまんだまま動かすときは SeekBar のx軸の範囲内なら許容する
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> if (rectBackground.top <= event.y && rectBackground.bottom >= event.y) updateProgress(event.x)
            MotionEvent.ACTION_MOVE -> updateProgress(event.x)
            MotionEvent.ACTION_UP -> Unit
            MotionEvent.ACTION_CANCEL -> Unit
        }
        return true
    }

    private fun measureWidth(measureSpec: Int): Int {
        var size = paddingStart + paddingEnd
        size += width
        return resolveSizeAndState(size, measureSpec, 0)
    }

    private fun measureHeight(measureSpec: Int): Int {
        var size = paddingTop + paddingBottom
        size += height + round(backgroundStrokeWidth + balloonHeight).toInt()
        return resolveSizeAndState(size, measureSpec, 0)
    }

    private fun drawBackgroundSeekBar(canvas: Canvas) {
        canvas.drawRoundRect(rectBackground, backgroundRadius, backgroundRadius, paintBackground)
    }

    private fun drawForegroundSeekBar(canvas: Canvas) {
        canvas.drawRoundRect(rectForeground.also {
            it.right = seekBarProgress
        }, foregroundRadius, foregroundRadius, paintForeground)
    }

    private fun drawThumb(canvas: Canvas) {
        if (thumbBitmap == null) {
            canvas.drawCircle(seekBarProgress, thumbY, thumbRadius, paintThumb)
            canvas.drawCircle(seekBarProgress, thumbY, thumbRadius, paintThumbBorder)
        } else {
            // 画像のサイズを考慮して位置を決める
            thumbBitmap?.let { b ->
                val src = Rect(0, 0, b.width, b.height)
                val dest = Rect((seekBarProgress - b.width).toInt(), (thumbY - b.height).toInt(), (seekBarProgress + b.width).toInt(), (thumbY + b.height).toInt())
                canvas.drawBitmap(b, src, dest, null)
            }
        }
    }

    private fun drawBalloon(canvas: Canvas) {
        val text = "%d".format(currentValue.toInt())
        val textRect = Rect().also { paintText.getTextBounds("7", 0, 1, it) }

        // 数字によってテキストの幅が変動し吹き出しも同じ動きになる
        // 吹き出しのサイズはなるべく一定にしたいので7の幅をベースに計算
        val (width, bias) = when (text.length) {
            1 -> textRect.width().toFloat() to 0.5f
            2 -> textRect.width() * 1.5f to 0.75f
            else -> textRect.width() * 2f to 0.82f
        }

        // 吹き出しを描画
        var textHeightBias = 1f
        if (balloonBitmap == null) {
            val baloonRect = RectF(0f, 0f, width * 2f, valueTextSizeF * 1.2f)
            baloonRect.offset(seekBarProgress - width, thumbY - valueTextSizeF * 2)
            canvas.drawRoundRect(baloonRect, thumbRadius, thumbRadius, paintBalloon)
        } else {
            textHeightBias = 1.2f
            val y = thumbY - valueTextSizeF * textHeightBias
            balloonBitmap?.let { b ->
                val src = Rect(0, 0, b.width, b.height)
                val dest = Rect((seekBarProgress - b.width).toInt(), (y - b.height).toInt(), (seekBarProgress + b.width).toInt(), (y + b.height).toInt())
                canvas.drawBitmap(b, src, dest, null)
            }
        }

        // テキストはつまみの位置に合わせるため `テキストの幅/2` を考慮する
        // x軸を調整しやすいようにテキストは予め paint 側で左寄せにしておく
        canvas.drawText(text, seekBarProgress - width * bias, thumbY - valueTextSizeF * textHeightBias, paintText)
    }

    private fun updateProgress(x: Float) {
        when {
            // 範囲外
            contentSize.left > x -> {
                seekBarProgress = contentSize.leftF
                listener?.progress(currentPercentage)
                listener?.progress(truncate(currentValue).toInt())
            }
            contentSize.right < x -> {
                seekBarProgress = contentSize.rightF
                listener?.progress(currentPercentage)
                listener?.progress(truncate(currentValue).toInt())
            }
            // 範囲内
            contentSize.left <= x && contentSize.right >= x -> {
                seekBarProgress = x
                listener?.progress(currentPercentage)
                listener?.progress(truncate(currentValue).toInt())
            }
        }
    }

    fun setOnChangeListenr(listener: OnChangeListener) {
        this.listener = listener
    }

    fun setMaxValue(value: Int) {
        valueMax = value
    }

    private fun Drawable.createBitmap(scale: Float) =
            Bitmap.createBitmap((intrinsicWidth * scale).toInt(), (intrinsicHeight * scale).toInt(), Bitmap.Config.ARGB_8888).also {
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
        val width get() = right - left
        val height get() = bottom - top
    }

    companion object {
        private const val DEFAULT_MAX = 100
        private const val DEFAULT_VALUE = 0
        private const val DEFAULT_BACKGROUND = Color.GRAY
        private const val DEFAULT_FOREGROUND = Color.GREEN
        private const val DEFAULT_BACKGROUND_STROKE_WIDTH = 0f
        private const val DEFAULT_THUMB_RADIUS = 24f
        private const val DEFAULT_THUMB_BORDER_WIDTH = 4f
        private const val DEFAULT_RESOURCE_SCALE = 1f
        private const val DEFAULT_TEXT_SIZE = 12f
        private const val DEFAULT_TEXT_COLOR = Color.WHITE
        private const val FOREGROUND_BIAS = 1.1f
    }
}

@BindingAdapter("balloon_seekbar_max")
fun BalloonSeekBar.setMaxValue(value: Int?) {
    if (value == null) return
    setMaxValue(value)
}