package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.shared.rail.NAV_RAIL_COLLAPSED_WIDTH
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import kotlinx.coroutines.flow.map
import org.jellyfin.androidtv.ui.shared.rail.ExitDialog
import org.jellyfin.androidtv.ui.shared.rail.NavRail
import org.jellyfin.androidtv.ui.shared.rail.NavRailState
import androidx.fragment.app.Fragment
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideLocalInteractionTracker
import org.jellyfin.androidtv.ui.composable.compat.AppNavigationHost
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.settings.compat.MainActivitySettings
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.applyTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

private const val COMPOSE_ROOT_VIEW = "androidx.compose.ui.platform.AndroidComposeView"

class MainActivity : FragmentActivity() {
	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val interactionTrackerViewModel by viewModel<InteractionTrackerViewModel>()
	private val workManager by inject<WorkManager>()
	private val navRailState = NavRailState()

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		if (!validateAuthentication()) return

		interactionTrackerViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		if (savedInstanceState == null && navigationRepository.canGoBack) navigationRepository.reset(clearHistory = true)

		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach {
				interactionTrackerViewModel.notifyInteraction(canCancel = false, userInitiated = false)
			}.launchIn(lifecycleScope)

		setContent {
			JellyfinTheme {
				ProvideLocalInteractionTracker(
					interactionTracker = { interactionTrackerViewModel.notifyInteraction(false, userInitiated = true) }
				) {
					AppBackground()
					// The menu floats over the content (like Plex), so opening it doesn't move anything
					Box(Modifier.fillMaxSize()) {
						AppNavigationHost(
							navigationRepository = navigationRepository,
							modifier = Modifier
								.fillMaxSize()
								.padding(start = if (navRailState.visible) NAV_RAIL_COLLAPSED_WIDTH else 0.dp),
						)
						if (navRailState.hasFocus) {
							Box(
								Modifier
									.fillMaxSize()
									.background(Color.Black.copy(alpha = 0.55f))
							)
						}
						NavRail(state = navRailState)
					}

					// Back: from a main screen's content go to the menu; on the Home menu ask before exiting.
					// Registered after AppNavigationHost, so these win over its "go back" handler when enabled.
					val canGoBack by remember {
						navigationRepository.currentDestination.map { navigationRepository.canGoBack }
					}.collectAsState(navigationRepository.canGoBack)
					var showExit by remember { mutableStateOf(false) }
					BackHandler(enabled = navRailState.visible && !navRailState.hasFocus && navRailState.onTopLevelScreen) {
						if (!navRailState.requestFocus()) navigationRepository.goBack()
					}
					BackHandler(enabled = navRailState.visible && navRailState.hasFocus && !canGoBack) {
						showExit = true
					}
					ExitDialog(
						visible = showExit,
						onExit = { finish() },
						onDismiss = { showExit = false },
					)

					InAppScreensaver()
					MainActivitySettings()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		if (!validateAuthentication()) return

		applyTheme()

		interactionTrackerViewModel.activityPaused = false
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	override fun onPause() {
		super.onPause()

		interactionTrackerViewModel.activityPaused = true
	}

	override fun onStop() {
		super.onStop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		lifecycleScope.launch(Dispatchers.IO) {
			Timber.i("MainActivity stopped")
			sessionRepository.restoreSession(destroyOnly = true)
		}
	}

	// Left in the old leanback screens (Home rows, library grids) is handled here, before Compose
	// sees it: Compose would jump straight to the menu whenever the card on the left is scrolled off
	// screen. Move to the card on the left if there is one, otherwise open the menu.
	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN
			&& navRailState.visible && !navRailState.hasFocus) {
			val focused = currentFocus
			if (focused != null && focused !is AbstractComposeView && focused::class.java.name != COMPOSE_ROOT_VIEW) {
				when (moveLeftInGrid(focused)) {
					true -> return true
					false -> if (navRailState.requestFocus()) return true
					null -> Unit
				}
			}
		}
		return super.dispatchKeyEvent(event)
	}

	/** true = moved to the item on the left, false = already at the grid's left edge, null = not in a leanback grid. */
	private fun moveLeftInGrid(focused: View): Boolean? {
		var view: View? = focused
		while (view != null) {
			when (view) {
				is HorizontalGridView -> {
					FocusFinder.getInstance().findNextFocus(view, focused, View.FOCUS_LEFT)?.let { return it.requestFocus(View.FOCUS_LEFT) }
					if (view.canScrollHorizontally(-1) && view.selectedPosition > 0) {
						view.setSelectedPositionSmooth(view.selectedPosition - 1)
						return true
					}
					return false
				}

				is VerticalGridView -> {
					FocusFinder.getInstance().findNextFocus(view, focused, View.FOCUS_LEFT)?.let { return it.requestFocus(View.FOCUS_LEFT) }
					return false
				}
			}
			view = view.parent as? View
		}

		// Other classic views (A–Z strip, toolbars): look for something to the left within the screen
		var screen: View = focused
		while (true) {
			val parent = screen.parent as? View ?: break
			if (parent is AbstractComposeView || parent::class.java.name.startsWith("androidx.compose.ui")) break
			screen = parent
		}
		if (screen === focused) return null
		FocusFinder.getInstance().findNextFocus(screen as? android.view.ViewGroup ?: return null, focused, View.FOCUS_LEFT)
			?.let { return it.requestFocus(View.FOCUS_LEFT) }
		return false
	}

	// Forward key events to fragments

	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean = supportFragmentManager.fragments
		.any { it.onKeyEvent(keyCode, event) }

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)
}
