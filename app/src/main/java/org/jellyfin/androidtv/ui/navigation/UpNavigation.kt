package org.jellyfin.androidtv.ui.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment
import org.jellyfin.androidtv.ui.browsing.GenericFolderFragment
import org.jellyfin.androidtv.ui.itemdetail.FullDetailsFragment
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncherHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

/**
 * Back as "up a level" instead of "back the way you came":
 * episode → season → series → its library → Home, and movie → its library → Home.
 */
class UpNavigation(
	private val api: ApiClient,
	private val userViewsRepository: UserViewsRepository,
) {
	sealed interface Target {
		/** Go to the Home screen (and clear the history). */
		data object Home : Target

		/** Go to this screen. */
		data class Screen(val destination: Destination.Fragment) : Target

		/** Not a screen with a parent: use the normal back behaviour. */
		data object Default : Target
	}

	/** Whether two destinations show the same screen (same fragment and same item or library). */
	fun sameScreen(a: Destination.Fragment, b: Destination.Fragment): Boolean {
		if (a.fragment != b.fragment) return false
		fun key(d: Destination.Fragment) = d.arguments.getString("ItemId")
			?: d.arguments.getString(Extras.Folder)?.let { Regex("\"Id\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
		return key(a) != null && key(a) == key(b)
	}

	/** Screens Back works on as "up a level". */
	fun handles(destination: Destination.Fragment?): Boolean = destination?.fragment in setOf(
		FullDetailsFragment::class,
		GenericFolderFragment::class,
		BrowseGridFragment::class,
	)

	suspend fun parentOf(destination: Destination.Fragment): Target = runCatching {
		when (destination.fragment) {
			// A library is the top: up from there is Home
			BrowseGridFragment::class -> Target.Home

			// A season's episode list: up to the series
			GenericFolderFragment::class -> {
				val folder = destination.arguments.getString(Extras.Folder)
					?.let { Json.decodeFromString(BaseItemDto.serializer(), it) }
				val seriesId = folder?.seriesId
				if (folder?.type == BaseItemKind.SEASON && seriesId != null) Target.Screen(Destinations.itemDetails(seriesId))
				else Target.Default
			}

			FullDetailsFragment::class -> {
				// Live TV, recordings and timers keep the normal back
				if (destination.arguments.containsKey("ChannelId") || destination.arguments.containsKey("SeriesTimer")) return Target.Default
				val itemId = destination.arguments.getString("ItemId")?.let(UUID::fromString) ?: return Target.Default
				val item = withContext(Dispatchers.IO) { api.userLibraryApi.getItem(itemId = itemId).content }
				when (item.type) {
					BaseItemKind.EPISODE -> {
						val seasonId = item.seasonId
						val seriesId = item.seriesId
						when {
							seasonId != null -> {
								val season = withContext(Dispatchers.IO) { api.userLibraryApi.getItem(itemId = seasonId).content }
								Target.Screen(Destinations.folderBrowser(season))
							}

							seriesId != null -> Target.Screen(Destinations.itemDetails(seriesId))
							else -> libraryOf(itemId)
						}
					}

					BaseItemKind.SEASON -> item.seriesId?.let { Target.Screen(Destinations.itemDetails(it)) } ?: libraryOf(itemId)
					BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO, BaseItemKind.BOX_SET -> libraryOf(itemId)
					else -> Target.Default
				}
			}

			else -> Target.Default
		}
	}.getOrElse {
		Timber.w(it, "Could not work out the parent screen, using normal back")
		Target.Default
	}

	/** The library (one of the user's views) an item lives in, or Home if it can't be found. */
	private suspend fun libraryOf(itemId: UUID): Target {
		val ancestors = withContext(Dispatchers.IO) { api.libraryApi.getAncestors(itemId = itemId).content }
		val views = userViewsRepository.views.first()
		val view = views.firstOrNull { view -> ancestors.any { it.id == view.id } } ?: return Target.Home
		return Target.Screen(ItemLauncherHelper.getUserViewDestination(view))
	}
}
