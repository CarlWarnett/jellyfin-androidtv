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
import org.jellyfin.androidtv.R

/**
 * Always-visible A–Z strip down the right of a library. Picking a letter shows only titles starting
 * with it ("All" shows everything again), so the bottom of a long library is a couple of presses away.
 */
class AlphaJumpStrip @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
	var onLetterSelected: (letter: String) -> Unit = {}

	private val buttons = mutableListOf<TextView>()

	init {
		orientation = VERTICAL
		gravity = Gravity.CENTER_HORIZONTAL

		val letters = listOf("#") + resources.getString(R.string.byletter_letters).map { it.toString() }
		for (letter in letters) {
			val button = TextView(context).apply {
				text = if (letter == "#") "All" else letter
				tag = letter
				gravity = Gravity.CENTER
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
				typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
				isFocusable = true
				isClickable = true
				background = pill()
				setOnClickListener { onLetterSelected(letter) }
				setOnFocusChangeListener { _, _ -> updateColors() }
			}
			buttons += button
			addView(button, LayoutParams(dp(34), dp(15)))
		}
		setSelected(null)
	}

	private var current: String? = null

	/** Marks the active letter (null or "#" = All). */
	fun setSelected(letter: String?) {
		current = letter?.takeIf { it.isNotEmpty() } ?: "#"
		updateColors()
	}

	private fun updateColors() {
		for (button in buttons) {
			val active = button.tag == current
			button.isActivated = active
			button.setTextColor(
				when {
					button.isFocused -> Color.parseColor("#0B0B0D")
					active -> Color.parseColor("#3DD6C4")
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
