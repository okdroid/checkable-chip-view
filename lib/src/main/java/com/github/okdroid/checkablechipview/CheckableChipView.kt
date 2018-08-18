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

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Paint.Style.STROKE
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.support.annotation.CallSuper
import android.support.annotation.ColorInt
import android.support.v4.graphics.ColorUtils
import android.text.Layout.Alignment.ALIGN_NORMAL
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AnimationUtils
import android.widget.Checkable
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.res.*
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation

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
        private const val CHECKING_DURATION = 350L
        private const val UNCHECKING_DURATION = 200L
    }

    /**
     * Sets the indicator and background color when the widget is checked.
     */
    var checkedColor: Int = 0xffff00ff.toInt()
        set(value) {
            if (field != value) {
                field = value
                dotPaint.color = value
                postInvalidateOnAnimation()
            }
        }

    /**
     * Sets the text color to be used when the widget is not checked.
     */
    @ColorInt
    var defaultTextColor: Int = Color.BLACK
        set(value) {
            if (field != value) {
                field = value
                textPaint.color = value
                postInvalidateOnAnimation()
            }
        }

    /**
     * Sets the text color to be used when the widget is checked.
     */
    var checkedTextColor: Int = Color.TRANSPARENT
        set(value) {
            if (field != value) {
                field = value
                postInvalidateOnAnimation()
            }
        }

    /**
     * Sets the text to be displayed.
     */
    var text: CharSequence = ""
        set(value) {
            field = value
            updateContentDescription()
            requestLayout()
        }

    /**
     * Sets the textSize to be displayed.
     */
    var textSize: Float = 0f
        set(value) {
            if (value < 0f) {
                IllegalArgumentException("textSize can't be negative")
            }
            field = value
            textPaint.textSize = value
            updateContentDescription()
            requestLayout()
        }

    /**
     * Controls the color of the outline
     */
    var outlineColor: Int = 0
        set(value) {
            if (field != value) {
                field = value
                outlinePaint.color = value
                postInvalidateOnAnimation()
            }
        }

    /**
     * Controls the stroke width of the outline
     */
    var outlineWidth: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                outlinePaint.strokeWidth = value
                postInvalidateOnAnimation()
            }
        }

    private var progress = 0f
        set(value) {
            if (field != value) {
                field = value
                postInvalidateOnAnimation()
                if (value == 0f || value == 1f) {
                    updateContentDescription()
                }
            }
        }

    private val padding: Int

    private val outlinePaint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val dotPaint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val clearDrawable: Drawable

    private val touchFeedbackDrawable: Drawable

    private lateinit var textLayout: StaticLayout

    private val progressAnimator: ValueAnimator by lazy {
        ObjectAnimator.ofFloat().apply {
            interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)
        }
    }

    init {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.CheckableChipView,
            R.attr.checkableChipViewStyle,
            R.style.Widget_ChipView
        )

        outlinePaint.style = STROKE
        outlineColor = a.getColorOrThrow(R.styleable.CheckableChipView_outlineColor)
        outlineWidth = a.getDimensionOrThrow(R.styleable.CheckableChipView_outlineWidth)

        checkedColor = a.getColor(R.styleable.CheckableChipView_android_color, checkedColor)
        checkedTextColor = a.getColor(R.styleable.CheckableChipView_checkedTextColor, Color.TRANSPARENT)
        defaultTextColor = a.getColorOrThrow(R.styleable.CheckableChipView_android_textColor)

        text = a.getStringOrThrow(R.styleable.CheckableChipView_android_text)
        textSize = a.getDimension(R.styleable.CheckableChipView_android_textSize, TextView(context).textSize)

        clearDrawable = a.getDrawableOrThrow(R.styleable.CheckableChipView_clearIcon).apply {
            setBounds(
                -intrinsicWidth / 2, -intrinsicHeight / 2, intrinsicWidth / 2, intrinsicHeight / 2
            )
        }
        touchFeedbackDrawable = a.getDrawableOrThrow(R.styleable.CheckableChipView_foreground).apply {
            callback = this@CheckableChipView
        }
        padding = a.getDimensionPixelSizeOrThrow(R.styleable.CheckableChipView_android_padding)
        isChecked = a.getBoolean(R.styleable.CheckableChipView_android_checked, false)
        a.recycle()
        clipToOutline = true
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
                outline.setRoundRect(0, 0, width, height, height / 2f)
            }
        }
        touchFeedbackDrawable.setBounds(0, 0, width, height)
    }

    @CallSuper
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeWidth = outlinePaint.strokeWidth
        val iconRadius = clearDrawable.intrinsicWidth / 2f
        val halfStroke = strokeWidth / 2f
        val rounding = (height - strokeWidth) / 2f
        
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

        // Tag color dot/background
        // Draws beyond bounds and relies on clipToOutline to enforce pill shape
        val dotRadius = lerp(
            strokeWidth + iconRadius,
            Math.max(width.toFloat(), height.toFloat()),
            progress
        )
        canvas.drawCircle(strokeWidth + padding + iconRadius, height / 2f, dotRadius, dotPaint)

        // Text
        val textX = lerp(
            strokeWidth + padding + clearDrawable.intrinsicWidth + padding,
            strokeWidth + padding * 2f,
            progress
        )

        textPaint.color = when {
            checkedTextColor == 0 -> defaultTextColor
            else -> ColorUtils.blendARGB(defaultTextColor, checkedTextColor, progress)
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
                x = width - strokeWidth - padding - iconRadius,
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
                removeAllUpdateListeners()
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

    override fun isChecked() = progress == 1f

    override fun toggle() {
        progress = if (progress == 0f) 1f else 0f
    }

    override fun setChecked(checked: Boolean) {
        progress = if (checked) 1f else 0f
    }

    override fun verifyDrawable(who: Drawable?): Boolean {
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

    private fun createLayout(textWidth: Int) {
        textLayout = if (SDK_INT >= M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth).build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, textPaint, textWidth, ALIGN_NORMAL, 1f, 0f, true)
        }
    }

    private fun updateContentDescription() {
        val desc = if (isChecked) R.string.a11y_filter_applied else R.string.a11y_filter_not_applied
        contentDescription = resources.getString(desc, text)
    }
}
