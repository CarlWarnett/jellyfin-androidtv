package org.jellyfin.androidtv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A–Z strip down the right of a library sorted by name. Only letters that have titles are shown;
 * picking one scrolls the library to where that letter starts (nothing is filtered out).
 */
class AlphaJumpStrip @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
	var onLetterSelected: (letter: String) -> Unit = {}

	private val buttons = mutableListOf<TextView>()
	private var current: String? = null

	init {
		orientation = VERTICAL
		gravity = Gravity.CENTER_HORIZONTAL
	}

	/** Shows one button per letter, in the given order ("#" = digits and symbols). */
	fun setLetters(letters: Collection<String>) {
		val hadFocus = hasFocus()
		removeAllViews()
		buttons.clear()
		for (letter in letters) {
			val button = TextView(context).apply {
				text = letter
				tag = letter
				gravity = Gravity.CENTER
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
				typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
				isFocusable = true
				isClickable = true
				background = pill()
				contentDescription = if (letter == "#") "Numbers and symbols" else letter
				setOnClickListener { onLetterSelected(letter) }
				setOnFocusChangeListener { _, _ -> updateColors() }
			}
			buttons += button
			addView(button, LayoutParams(dp(34), dp(15)))
		}
		updateColors()
		if (hadFocus) buttons.firstOrNull { it.tag == current }?.requestFocus() ?: buttons.firstOrNull()?.requestFocus()
	}

	/** Highlights the letter of the section the grid is in. */
	fun setSelected(letter: String?) {
		if (letter == current) return
		current = letter
		updateColors()
	}

	private fun updateColors() {
		for (button in buttons) {
			button.setTextColor(
				when {
					button.isFocused -> Color.parseColor("#0B0B0D")
					button.tag == current -> Color.parseColor("#AA5CC3")
					else -> Color.parseColor("#A1A1A6")
				}
			)
		}
	}

	private fun pill() = StateListDrawable().apply {
		addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
			cornerRadius = dp(8).toFloat()
			setColor(Color.parseColor("#F5F5F7"))
		})
		addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
	}

	private fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
