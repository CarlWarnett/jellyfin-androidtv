package org.jellyfin.androidtv.ui.shared.rail

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncherHelper
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destination
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioNowPlayingFragment
import org.jellyfin.androidtv.ui.playback.CustomPlaybackOverlayFragment
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.player.photo.PhotoPlayerFragment
import org.jellyfin.androidtv.ui.player.video.VideoPlayerFragment
import org.jellyfin.androidtv.ui.search.SearchFragment
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

/** Colours of the "Jellyfin Clean" look. */
object CleanColors {
	val Rail = Color(0xFF111114)
	val Text = Color(0xFFF5F5F7)
	val TextSecondary = Color(0xFFA1A1A6)
	val Selected = Color(0x14FFFFFF)
	val Focused = Color(0xFFF5F5F7)
	val OnFocused = Color(0xFF0B0B0D)
	// Jellyfin brand colours
	val Accent = Color(0xFFAA5CC3)
	val AccentBlue = Color(0xFF00A4DC)
}

/** Lets the activity move focus into the menu (Back from content, Left at the edge). */
class NavRailState {
	val focusRequester = FocusRequester()
	var hasFocus by mutableStateOf(false)
		internal set
	var visible by mutableStateOf(true)
		internal set
	/** True on Home, Search and a library's main screen; Back there moves to the menu instead of going back. */
	var onTopLevelScreen by mutableStateOf(true)
		internal set

	fun requestFocus() = runCatching { focusRequester.requestFocus() }.isSuccess
}

/** Width the content leaves free for the closed menu. */
val NAV_RAIL_COLLAPSED_WIDTH = 72.dp
private val COLLAPSED_WIDTH = NAV_RAIL_COLLAPSED_WIDTH
private val EXPANDED_WIDTH = 240.dp

/**
 * The menu down the left-hand side: Home, Search, each library, then Settings and Switch user.
 * It shows icons only while the content has focus and opens up with labels when it gets focus.
 */
@Composable
fun NavRail(
	state: NavRailState,
	modifier: Modifier = Modifier,
) {
	val navigationRepository = koinInject<NavigationRepository>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val activity = LocalActivity.current
	val scope = rememberCoroutineScope()

	val destination by navigationRepository.currentDestination.collectAsState()
	val views by remember { userViewsRepository.views }.collectAsState(emptyList())

	state.visible = destination?.showsRail() ?: true
	if (!state.visible) return

	val width by animateDpAsState(if (state.hasFocus) EXPANDED_WIDTH else COLLAPSED_WIDTH, label = "railWidth")
	val selected = destination?.selectedKey()
	state.onTopLevelScreen = selected != null

	// Closed: a thin strip of icons. Open: a rounded panel floating over the (dimmed) content.
	Column(
		modifier = modifier
			.fillMaxHeight()
			.padding(if (state.hasFocus) 16.dp else 0.dp)
			.width(width)
			.clip(RoundedCornerShape(if (state.hasFocus) 18.dp else 0.dp))
			.background(if (state.hasFocus) CleanColors.Rail else Color.Transparent)
			.padding(horizontal = 12.dp, vertical = 20.dp)
			.focusRequester(state.focusRequester)
			.focusRestorer()
			.onFocusChanged { state.hasFocus = it.hasFocus }
			.focusGroup(),
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		// Jellyfin logo: the full-colour wordmark when open, the mark on its own when closed
		Box(
			contentAlignment = Alignment.CenterStart,
			modifier = Modifier
				.padding(start = 12.dp, bottom = 16.dp)
				.height(28.dp),
		) {
			if (state.hasFocus) {
				Image(
					painter = painterResource(R.drawable.app_logo),
					contentDescription = "Jellyfin",
					modifier = Modifier.height(26.dp),
				)
			} else {
				Icon(
					painter = painterResource(R.drawable.ic_jellyfin),
					contentDescription = "Jellyfin",
					tint = CleanColors.Accent,
					modifier = Modifier.size(24.dp),
				)
			}
		}

		Column(
			modifier = Modifier
				.weight(1f)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			RailItem(
				icon = painterResource(R.drawable.ic_house),
				label = stringResource(R.string.lbl_home),
				expanded = state.hasFocus,
				selected = selected == KEY_HOME,
				onClick = {
					if (selected != KEY_HOME) navigationRepository.reset(Destinations.home, clearHistory = true)
				},
			)
			RailItem(
				icon = painterResource(R.drawable.ic_search),
				label = stringResource(R.string.lbl_search),
				expanded = state.hasFocus,
				selected = selected == KEY_SEARCH,
				onClick = { if (selected != KEY_SEARCH) navigationRepository.navigate(Destinations.search()) },
			)
			for (view in views) {
				RailItem(
					icon = painterResource(view.railIcon()),
					label = view.name.orEmpty(),
					expanded = state.hasFocus,
					selected = selected == view.id.toString(),
					onClick = {
						if (selected != view.id.toString()) scope.launch {
							navigationRepository.navigate(ItemLauncherHelper.getUserViewDestination(view))
						}
					},
				)
			}
		}

		RailItem(
			icon = painterResource(R.drawable.ic_settings),
			label = stringResource(R.string.lbl_settings),
			expanded = state.hasFocus,
			selected = false,
			onClick = { settingsViewModel.show() },
		)
		RailItem(
			icon = painterResource(R.drawable.ic_users),
			label = stringResource(R.string.lbl_switch_user),
			expanded = state.hasFocus,
			selected = false,
			onClick = {
				mediaManager.clearAudioQueue()
				sessionRepository.destroyCurrentSession()
				activity?.startActivity(ActivityDestinations.startup(activity))
				activity?.finishAfterTransition()
			},
		)
	}
}

@Composable
private fun RailItem(
	icon: Painter,
	label: String,
	expanded: Boolean,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }
	val background = when {
		focused -> CleanColors.Focused
		selected -> CleanColors.Selected
		else -> Color.Transparent
	}
	val content = when {
		focused -> CleanColors.OnFocused
		selected -> CleanColors.Text
		else -> CleanColors.TextSecondary
	}

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.height(38.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(background)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp),
	) {
		Icon(painter = icon, contentDescription = if (expanded) null else label, tint = content, modifier = Modifier.size(18.dp))
		if (expanded) {
			Spacer(Modifier.width(12.dp))
			Text(
				text = label,
				color = content,
				fontSize = 14.sp,
				fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

private const val KEY_HOME = "home"
private const val KEY_SEARCH = "search"

/** Which menu item a destination belongs to: home, search, or a library's id. */
private fun Destination.Fragment.selectedKey(): String? = when (fragment) {
	HomeFragment::class -> KEY_HOME
	SearchFragment::class -> KEY_SEARCH
	else -> arguments.getString(Extras.Folder)?.let { json ->
		Regex("\"Id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"").find(json)?.groupValues?.get(1)?.let { id ->
			// Library ids come as 32 hex digits or with dashes; compare in the dashed form
			if (id.length == 32) "${id.substring(0, 8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-${id.substring(16, 20)}-${id.substring(20)}" else id
		}?.lowercase()
	}
}

/** Players are full screen, so the menu is hidden there. (Compared by class: names are obfuscated in release builds.) */
private fun Destination.Fragment.showsRail(): Boolean = fragment !in setOf(
	CustomPlaybackOverlayFragment::class,
	VideoPlayerFragment::class,
	PhotoPlayerFragment::class,
	AudioNowPlayingFragment::class,
)

private fun BaseItemDto.railIcon(): Int = when (collectionType) {
	CollectionType.MOVIES -> R.drawable.ic_movie
	CollectionType.TVSHOWS -> R.drawable.ic_tv
	CollectionType.MUSIC -> R.drawable.ic_music_album
	CollectionType.LIVETV -> R.drawable.ic_tv_guide
	CollectionType.PHOTOS, CollectionType.HOMEVIDEOS -> R.drawable.ic_photos
	else -> R.drawable.ic_folder
}
