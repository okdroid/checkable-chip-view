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
import android.graphics.Paint.Style.STROKE
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.text.Layout.Alignment.ALIGN_NORMAL
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AnimationUtils
import android.widget.Checkable
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.content.res.getColorOrThrow
import androidx.core.content.res.getDimensionOrThrow
import androidx.core.content.res.getDimensionPixelSizeOrThrow
import androidx.core.content.res.getDrawableOrThrow
import androidx.core.graphics.ColorUtils
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
        private const val SELECTING_DURATION = 350L
        private const val DESELECTING_DURATION = 200L
    }

    /**
     * Sets the background color when the widget is checked.
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
    var checkedTextColor: Int? = null

    /**
     * Sets the text to be displayed.
     */
    var text: CharSequence = ""
        set(value) {
            field = value
            updateContentDescription()
            requestLayout()
        }

    var showIcons: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
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

    private val clear: Drawable

    private val touchFeedback: Drawable

    private lateinit var textLayout: StaticLayout

    private var progressAnimator: ValueAnimator? = null

    private val checkAnimationInterpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)

    init {
        val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.CheckableChipView,
                R.attr.checkableChipViewStyle,
                R.style.Widget_ChipView
        )
        outlinePaint.apply {
            color = a.getColorOrThrow(R.styleable.CheckableChipView_android_strokeColor)
            strokeWidth = a.getDimensionOrThrow(R.styleable.CheckableChipView_outlineWidth)
            style = STROKE
        }
        defaultTextColor = a.getColorOrThrow(R.styleable.CheckableChipView_android_textColor)
        textPaint.apply {
            color = defaultTextColor
            textSize = a.getDimensionOrThrow(R.styleable.CheckableChipView_android_textSize)
        }

        clear = a.getDrawableOrThrow(R.styleable.CheckableChipView_clearIcon).apply {
            setBounds(
                    -intrinsicWidth / 2, -intrinsicHeight / 2, intrinsicWidth / 2, intrinsicHeight / 2
            )
        }
        touchFeedback = a.getDrawableOrThrow(R.styleable.CheckableChipView_foreground).apply {
            callback = this@CheckableChipView
        }
        padding = a.getDimensionPixelSizeOrThrow(R.styleable.CheckableChipView_android_padding)
        isChecked = a.getBoolean(R.styleable.CheckableChipView_android_checked, false)
        showIcons = a.getBoolean(R.styleable.CheckableChipView_showIcons, true)
        a.recycle()
        clipToOutline = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val nonTextWidth = (4 * padding) +
                (2 * outlinePaint.strokeWidth).toInt() +
                if (showIcons) clear.intrinsicWidth else 0
        val availableTextWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec) - nonTextWidth
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec) - nonTextWidth
            MeasureSpec.UNSPECIFIED -> Int.MAX_VALUE
            else -> Int.MAX_VALUE
        }
        createLayout(availableTextWidth)
        val w = nonTextWidth + textLayout.textWidth()
        val h = padding + textLayout.height + padding
        setMeasuredDimension(w, h)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, h / 2f)
            }
        }
        touchFeedback.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val strokeWidth = outlinePaint.strokeWidth
        val iconRadius = clear.intrinsicWidth / 2f
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
        if (showIcons) {
            // Draws beyond bounds and relies on clipToOutline to enforce pill shape
            val dotRadius = lerp(
                    strokeWidth + iconRadius,
                    width.toFloat(),
                    progress
            )
            canvas.drawCircle(strokeWidth + padding + iconRadius, height / 2f, dotRadius, dotPaint)
        } else {
            canvas.drawRoundRect(
                    halfStroke,
                    halfStroke,
                    width - halfStroke,
                    height - halfStroke,
                    rounding,
                    rounding,
                    dotPaint
            )
        }

        // Text
        val textX = if (showIcons) {
            lerp(
                    strokeWidth + padding + clear.intrinsicWidth + padding,
                    strokeWidth + padding * 2f,
                    progress
            )
        } else {
            strokeWidth + padding * 2f
        }
        val selectedColor = checkedTextColor
        textPaint.color = if (selectedColor != null && selectedColor != 0 && progress > 0) {
            ColorUtils.blendARGB(defaultTextColor, selectedColor, progress)
        } else {
            defaultTextColor
        }
        canvas.withTranslation(
                x = textX,
                y = (height - textLayout.height) / 2f
        ) {
            textLayout.draw(canvas)
        }

        // Clear icon
        if (showIcons && progress > 0f) {
            canvas.withTranslation(
                    x = width - strokeWidth - padding - iconRadius,
                    y = height / 2f
            ) {
                canvas.withScale(progress, progress) {
                    clear.draw(canvas)
                }
            }
        }

        // Touch feedback
        touchFeedback.draw(canvas)
    }

    /**
     * Starts the animation to enable/disable a filter and invokes a function when done.
     */
    fun setCheckedAnimated(checked: Boolean, onEnd: (() -> Unit)?) {
        val newProgress = if (checked) 1f else 0f
        if (newProgress != progress) {
            progressAnimator?.cancel()
            progressAnimator = ValueAnimator.ofFloat(progress, newProgress).apply {
                addUpdateListener {
                    progress = it.animatedValue as Float
                }
                doOnEnd {
                    progress = newProgress
                    onEnd?.invoke()
                }
                interpolator = checkAnimationInterpolator
                duration = if (checked) SELECTING_DURATION else DESELECTING_DURATION
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
        return super.verifyDrawable(who) || who == touchFeedback
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        touchFeedback.state = drawableState
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        touchFeedback.jumpToCurrentState()
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        touchFeedback.setHotspot(x, y)
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
