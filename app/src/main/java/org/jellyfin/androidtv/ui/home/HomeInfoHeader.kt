package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PersonKind
import org.koin.compose.koinInject

/** The item selected in the Home rows, shown in the info panel above them. */
object HomeSelection {
	val item = MutableStateFlow<BaseItemDto?>(null)
}

private val TextPrimary = Color(0xFFF5F5F7)
private val TextSecondary = Color(0xFFD1D1D6)

/**
 * Plex-style panel at the top of Home: logo (or title), year, runtime, genres, rating, a two-line
 * summary and the main cast of whatever is selected in the rows below.
 */
@Composable
fun HomeInfoHeader(
	item: BaseItemDto?,
	modifier: Modifier = Modifier,
) {
	val imageHelper = koinInject<ImageHelper>()

	Column(
		modifier = modifier.padding(start = 48.dp, top = 28.dp, end = 48.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (item == null) return@Column

		val isEpisode = item.type == BaseItemKind.EPISODE
		val title = (if (isEpisode) item.seriesName else null) ?: item.name.orEmpty()
		val logoUrl = remember(item.id) { imageHelper.getLogoImageUrl(item, 600) }
		var logoFailed by remember(item.id) { mutableStateOf(false) }

		if (logoUrl != null && !logoFailed) {
			AsyncImage(
				model = logoUrl,
				contentDescription = title,
				contentScale = ContentScale.Fit,
				alignment = Alignment.CenterStart,
				onError = { logoFailed = true },
				modifier = Modifier
					.height(70.dp)
					.widthIn(max = 340.dp),
			)
		} else {
			Text(
				text = title,
				color = TextPrimary,
				fontSize = 32.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		// Episode: "S5 E3 · Home"
		if (isEpisode) {
			val number = listOfNotNull(
				item.parentIndexNumber?.let { "S$it" },
				item.indexNumber?.let { "E$it" },
			).joinToString(" ")
			Text(
				text = listOf(number, item.name.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
				color = TextPrimary,
				fontSize = 16.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Row(
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			val meta = listOfNotNull(
				item.productionYear?.toString(),
				item.runTimeTicks?.let { formatRuntime(it) },
				item.genres?.take(2)?.takeIf { it.isNotEmpty() }?.joinToString(", "),
			)
			for (part in meta) Text(part, color = TextSecondary, fontSize = 14.sp)
			item.officialRating?.let { rating ->
				Text(
					text = rating,
					color = TextSecondary,
					fontSize = 12.sp,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier
						.border(1.dp, TextSecondary, RoundedCornerShape(4.dp))
						.padding(horizontal = 5.dp, vertical = 1.dp),
				)
			}
		}

		item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
			Text(
				text = overview,
				color = TextPrimary,
				fontSize = 14.sp,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.widthIn(max = 620.dp),
			)
		}

		item.people
			?.filter { it.type == PersonKind.ACTOR }
			?.take(3)
			?.mapNotNull { it.name }
			?.takeIf { it.isNotEmpty() }
			?.let { cast -> Text(cast.joinToString(", "), color = TextSecondary, fontSize = 13.sp, maxLines = 1) }
	}
}

private fun formatRuntime(ticks: Long): String {
	val minutes = ticks / 600_000_000
	return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
