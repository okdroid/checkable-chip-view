/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.okdroid.checkablechipview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.support.annotation.CallSuper
import android.support.v4.graphics.ColorUtils
import android.text.Layout.Alignment.ALIGN_NORMAL
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AnimationUtils
import android.widget.Checkable
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.res.*
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

/**
 * A custom view for displaying filters. Allows a custom presentation of the tag color and selection
 * state.
 */
class CheckableChipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Checkable {

    companion object {
        private const val CHECKING_DURATION = 350L // ms
        private const val UNCHECKING_DURATION = 200L // ms
    }

    /**
     * Sets the indicator and background color when the widget is checked.
     */
    var checkedColor: Int by viewProperty(0) { indicatorPaint.color = it }

    /**
     * Sets the text color to be used when the widget is not checked.
     */
    var defaultTextColor: Int by viewProperty(0) { textPaint.color = it }
    /**
     * Sets the text color to be used when the widget is checked.
     */
    var checkedTextColor: Int by viewProperty(0)

    /**
     * Sets the text to be displayed.
     */
    var text: CharSequence by viewProperty("", requestLayout = true)

    /**
     * Sets the textSize to be displayed.
     */
    var textSize: Float by viewProperty(0f, requestLayout = true) { textPaint.textSize = it }

    /**
     * Controls the color of the outline.
     */
    var outlineColor: Int by viewProperty(0) { outlinePaint.color = it }

    /**
     * Controls the stroke width of the outline.
     */
    var outlineWidth: Float by viewProperty(0f) { outlinePaint.strokeWidth = it }

    /**
     * Controls the corner radius of the outline. If null the outline will be pill-shaped.
     */
    var outlineCornerRadius: Float? by viewProperty(null)

    /**
     * Sets the listener to be called when the checked state changes.
     */
    var onCheckedChangeListener: ((view: CheckableChipView, checked: Boolean) -> Unit)? = null

    private var progress: Float by viewProperty(0f) {
        if (it == 0f || it == 1f) {
            onCheckedChangeListener?.invoke(this, isChecked)
        }
    }

    private var padding: Int = 0
    private val outlinePaint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private lateinit var clearDrawable: Drawable
    private lateinit var touchFeedbackDrawable: Drawable

    private lateinit var textLayout: StaticLayout

    private val progressAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat().apply {
            interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)
        }
    }

    init {
        clipToOutline = true
        isClickable = true

        context.withStyledAttributes(
            set = attrs,
            attrs = R.styleable.CheckableChipView,
            defStyleAttr = R.attr.checkableChipViewStyle,
            defStyleRes = R.style.Widget_ChipView
        ) {
            outlineColor = getColorOrThrow(R.styleable.CheckableChipView_outlineColor)
            outlineWidth = getDimensionOrThrow(R.styleable.CheckableChipView_outlineWidth)
            if (hasValue(R.styleable.CheckableChipView_outlineCornerRadius)) {
                outlineCornerRadius = getDimensionOrThrow(R.styleable.CheckableChipView_outlineCornerRadius)
            }

            checkedColor = getColor(R.styleable.CheckableChipView_android_color, checkedColor)
            checkedTextColor = getColor(R.styleable.CheckableChipView_checkedTextColor, Color.TRANSPARENT)
            defaultTextColor = getColorOrThrow(R.styleable.CheckableChipView_android_textColor)

            text = getStringOrThrow(R.styleable.CheckableChipView_android_text)
            textSize = getDimension(R.styleable.CheckableChipView_android_textSize, TextView(context).textSize)

            clearDrawable = getDrawableOrThrow(R.styleable.CheckableChipView_clearIcon).apply {
                setBounds(
                    -intrinsicWidth / 2, -intrinsicHeight / 2, intrinsicWidth / 2, intrinsicHeight / 2
                )
            }
            touchFeedbackDrawable = getDrawableOrThrow(R.styleable.CheckableChipView_foreground).apply {
                callback = this@CheckableChipView
            }
            padding = getDimensionPixelSizeOrThrow(R.styleable.CheckableChipView_android_padding)
            isChecked = getBoolean(R.styleable.CheckableChipView_android_checked, false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        // width
        val nonTextWidth = (4 * padding) + (2 * outlinePaint.strokeWidth).toInt() + clearDrawable.intrinsicWidth
        val availableTextWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec) - nonTextWidth
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec) - nonTextWidth
            MeasureSpec.UNSPECIFIED -> Int.MAX_VALUE
            else -> Int.MAX_VALUE
        }
        createLayout(availableTextWidth)
        val desiredWidth = nonTextWidth + textLayout.textWidth()
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> Math.min(MeasureSpec.getSize(widthMeasureSpec), desiredWidth)
            MeasureSpec.UNSPECIFIED -> desiredWidth
            else -> desiredWidth
        }

        // height
        val desiredHeight = padding + textLayout.height + padding
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> Math.min(MeasureSpec.getSize(heightMeasureSpec), desiredHeight)
            MeasureSpec.UNSPECIFIED -> desiredHeight
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, width, height, outlineCornerRadius ?: (height / 2f))
            }
        }
        touchFeedbackDrawable.setBounds(0, 0, width, height)
    }

    @CallSuper
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        outlinePaint.apply {
            strokeWidth = outlineWidth
            color = outlineColor
        }
        val iconRadius = clearDrawable.intrinsicWidth / 2f
        val halfStroke = outlineWidth / 2f
        val rounding = outlineCornerRadius ?: (height - outlineWidth) / 2f

        // Outline
        if (progress < 1f) {
            canvas.drawRoundRect(
                halfStroke,
                halfStroke,
                width - halfStroke,
                height - halfStroke,
                rounding,
                rounding,
                outlinePaint
            )
        }

        // Draws beyond bounds and relies on clipToOutline to enforce shape
        val initialIndicatorSize = clearDrawable.intrinsicWidth.toFloat()
        val indicatorCenterX = outlineWidth + padding + padding / 2f + initialIndicatorSize / 2f
        val indicatorCenterY = height / 2f

        val indicatorSize = lerp(
            initialIndicatorSize,
            Math.max((width - indicatorCenterX) * 2f, (height - indicatorCenterY) * 2f),
            progress
        )

        val indicatorSizeHalf = indicatorSize / 2f

        val indicatorRounding = (rounding / (height - outlineWidth)) * (indicatorSizeHalf * 2f)
        indicatorPaint.color = checkedColor

        canvas.drawRoundRect(
            indicatorCenterX - indicatorSizeHalf,
            indicatorCenterY - indicatorSizeHalf,
            indicatorCenterX + indicatorSizeHalf,
            indicatorCenterY + indicatorSizeHalf,
            indicatorRounding,
            indicatorRounding,
            indicatorPaint
        )

        // Text
        val textX = lerp(
            indicatorCenterX + initialIndicatorSize / 2f + padding,
            outlineWidth + padding + padding / 2f,
            progress
        )

        textPaint.apply {
            textSize = this@CheckableChipView.textSize
            color = when {
                checkedTextColor == 0 -> defaultTextColor
                else -> ColorUtils.blendARGB(defaultTextColor, checkedTextColor, progress)
            }
        }

        canvas.withTranslation(
            x = textX,
            y = (height - textLayout.height) / 2f
        ) {
            textLayout.draw(this)
        }

        // Clear icon
        if (progress > 0f) {
            canvas.withTranslation(
                x = width - outlineWidth - padding - iconRadius,
                y = height / 2f
            ) {
                canvas.withScale(progress, progress) {
                    clearDrawable.draw(canvas)
                }
            }
        }

        // Touch feedback
        touchFeedbackDrawable.draw(canvas)
    }

    /**
     * Starts the animation to enable/disable a filter and invokes a function when done.
     */
    fun setCheckedAnimated(checked: Boolean, onEnd: (() -> Unit)?) {
        val newProgress = if (checked) 1f else 0f
        if (newProgress != progress) {
            progressAnimator.apply {
                removeAllListeners()
                cancel()
                setFloatValues(progress, newProgress)
                duration = if (checked) CHECKING_DURATION else UNCHECKING_DURATION
                addUpdateListener {
                    progress = it.animatedValue as Float
                }
                doOnEnd {
                    progress = newProgress
                    onEnd?.invoke()
                }
                start()
            }
        }
    }

    override fun performClick(): Boolean {
        setCheckedAnimated(!isChecked, null)
        val handled = super.performClick()
        if (!handled) {
            playSoundEffect(SoundEffectConstants.CLICK)
        }
        return handled
    }

    override fun isChecked() = progress == 1f

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun setChecked(checked: Boolean) {
        progress = if (checked) 1f else 0f
    }

    private fun createLayout(textWidth: Int) {
        textLayout = if (SDK_INT >= M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth).build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, textPaint, textWidth, ALIGN_NORMAL, 1f, 0f, true)
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || who == touchFeedbackDrawable
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        touchFeedbackDrawable.state = drawableState
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        touchFeedbackDrawable.jumpToCurrentState()
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        touchFeedbackDrawable.setHotspot(x, y)
    }

    /**
     * Observable delegate, which prevents view property from setting the same value and after that requests layout if [requestLayout] is true,
     * or calls [postInvalidateOnAnimation] otherwise.
     *
     * @param default default initial value for a view property
     * @param requestLayout defines whether this view calls [requestLayout] or [postInvalidateOnAnimation] after changing a view property
     * @param afterChangeActions custom actions to be performed after changing a view property
     */
    private fun <T> viewProperty(
        default: T,
        requestLayout: Boolean = false,
        afterChangeActions: ((newValue: T) -> Unit)? = null
    ) = object : ObservableProperty<T>(default) {

        override fun beforeChange(property: KProperty<*>, oldValue: T, newValue: T): Boolean = newValue != oldValue

        override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) {
            afterChangeActions?.invoke(newValue)
            if (requestLayout) {
                requestLayout()
            } else {
                postInvalidateOnAnimation()
            }
        }
    }
}