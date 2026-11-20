package org.jellyfin.androidtv.ui.browsing

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import org.jellyfin.androidtv.ui.home.HomeInfoHeader
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Works out, for a library sorted by name, where each letter starts: one light request for the
 * sort names of all items (no images), in the same order and with the same filters as the grid.
 * Calls back on the main thread with letter -> position of its first item ("#" for digits/symbols).
 */
fun BrowseGridFragment.loadAlphaIndex(
	api: ApiClient,
	query: GetItemsRequest,
	onLoaded: (Map<String, Int>) -> Unit,
) {
	lifecycleScope.launch {
		runCatching {
			val response = withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					query.copy(
						startIndex = 0,
						limit = null,
						nameStartsWith = null,
						fields = listOf(ItemFields.SORT_NAME),
						enableImages = false,
						enableUserData = false,
						enableTotalRecordCount = false,
					)
				).content
			}
			val index = LinkedHashMap<String, Int>()
			response.items.forEachIndexed { position, item ->
				index.putIfAbsent(alphaKey(item.sortName ?: item.name), position)
			}
			index
		}.onSuccess(onLoaded).onFailure { Timber.w(it, "Could not load the A–Z index") }
	}
}

/** The A–Z strip letter a title belongs to. */
fun alphaKey(name: String?): String {
	val first = name?.trim()?.firstOrNull()?.uppercaseChar() ?: return "#"
	return if (first in 'A'..'Z') first.toString() else "#"
}

/** The letter whose section contains [position]. */
fun alphaKeyAt(index: Map<String, Int>, position: Int): String? =
	index.entries.filter { it.value <= position }.maxByOrNull { it.value }?.key

/** Shows the Home-style info panel for the selected item above the grid. */
fun setupInfoHeader(view: ComposeView, selected: MutableStateFlow<BaseItemDto?>) {
	view.setContent {
		val item by selected.collectAsState()
		HomeInfoHeader(item = item, modifier = Modifier.fillMaxSize())
	}
}
