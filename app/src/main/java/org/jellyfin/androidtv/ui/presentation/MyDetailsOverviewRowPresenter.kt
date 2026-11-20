package org.jellyfin.androidtv.ui.presentation

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.api.BaseItemKind

class MyDetailsOverviewRowPresenter(
	private val markdownRenderer: MarkdownRenderer,
) : RowPresenter() {
	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
	) : RowPresenter.ViewHolder(detailRowView) {
		private val binding get() = detailRowView.binding

		fun setItem(row: MyDetailsOverviewRow) {
			setTitle(row.item.name)

			InfoLayoutHelper.addInfoRow(view.context, row.item, row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex), binding.fdMainInfoRow, false)
			binding.fdGenreRow.text = row.item.genres?.joinToString(" / ")

			infoItems[0] = row.infoItem1?.label to row.infoItem1?.value
			infoItems[1] = row.infoItem2?.label to row.infoItem2?.value
			infoItems[2] = row.infoItem3?.label to row.infoItem3?.value
			renderExtraInfo()

			binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)

			setSummary(row.summary)

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 9
				binding.fdGenreRow.isVisible = false
			}

			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)

				binding.fdButtonRow.addView(button)
			}
		}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
		}

		fun setSummary(summary: String?) {
			binding.fdSummaryText.text = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
		}

		fun setInfoValue3(text: String?) {
			infoItems[2] = infoItems[2]?.first to text
			renderExtraInfo()
		}

		// Directed by / Seasons / Runs / Ends as one line under the synopsis; empty ones are left out
		private val infoItems = arrayOfNulls<Pair<String?, String?>>(3)

		private fun renderExtraInfo() {
			val text = android.text.SpannableStringBuilder()
			for ((label, value) in infoItems.filterNotNull()) {
				if (label.isNullOrBlank() || value.isNullOrBlank()) continue
				if (text.isNotEmpty()) text.append("      ")
				val start = text.length
				text.append(label.uppercase())
				text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
				text.append("  ")
				val valueStart = text.length
				text.append(value)
				text.setSpan(android.text.style.ForegroundColorSpan(0xB3FFFFFF.toInt()), valueStart, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			binding.fdExtraInfo.text = text
			binding.fdExtraInfo.isVisible = text.isNotEmpty()
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailRowView(parent.context)
		viewHolder = ViewHolder(view, markdownRenderer)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is MyDetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
