package org.jellyfin.androidtv.ui.shared.rail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.dialog.DialogBase

/** Asked when Back is pressed on the menu of the Home screen, instead of closing the app straight away. */
@Composable
fun ExitDialog(
	visible: Boolean,
	onExit: () -> Unit,
	onDismiss: () -> Unit,
) {
	DialogBase(visible = visible, onDismissRequest = onDismiss) {
		val stayFocus = remember { FocusRequester() }
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(24.dp),
			modifier = Modifier
				.width(420.dp)
				.background(Color(0xFF1C1C21), RoundedCornerShape(20.dp))
				.padding(32.dp),
		) {
			Text("Exit Jellyfin?", color = CleanColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Button(
					onClick = onDismiss,
					modifier = Modifier.focusRequester(stayFocus),
					colors = ButtonDefaults.colors(focusedContainerColor = CleanColors.Focused, focusedContentColor = CleanColors.OnFocused),
				) { Text("Stay") }
				Button(
					onClick = onExit,
					colors = ButtonDefaults.colors(focusedContainerColor = CleanColors.Focused, focusedContentColor = CleanColors.OnFocused),
				) { Text("Exit") }
			}
		}
		LaunchedEffect(Unit) { runCatching { stayFocus.requestFocus() } }
	}
}
