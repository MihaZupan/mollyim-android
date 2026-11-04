/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object ScreenLockSettingsTestTags {
  const val SCROLLER = "scroller"
  const val SWITCH_SCREEN_LOCK = "switch-screen-lock"
}

/** Molly's biometric lock uses the controller's existing background policy, not a configurable timeout. */
@Composable
fun ScreenLockSettingsScreen(
  state: ScreenLockSettingsState,
  onEvent: (ScreenLockSettingsEvent) -> Unit,
  modifier: Modifier = Modifier,
  description: String? = null,
  toggleLabel: String? = null
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_protection__screen_lock),
    onNavigationClick = { onEvent(ScreenLockSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(ScreenLockSettingsTestTags.SCROLLER)
    ) {
      item {
        Icon(
          painter = painterResource(R.drawable.ic_screen_lock),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(vertical = 24.dp)
        )
      }
      item {
        Text(
          text = description ?: stringResource(R.string.preferences_app_protection__screen_lock),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
        )
      }
      item {
        Rows.ToggleRow(
          checked = state.isScreenLockEnabled,
          text = toggleLabel ?: stringResource(R.string.preferences_app_protection__screen_lock),
          onCheckChanged = { onEvent(ScreenLockSettingsEvent.ScreenLockToggleRequested(it)) },
          modifier = Modifier.testTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK)
        )
      }
    }
  }
}
