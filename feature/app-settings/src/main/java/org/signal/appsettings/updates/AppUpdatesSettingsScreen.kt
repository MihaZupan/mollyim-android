/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.updates

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons

@VisibleForTesting
object AppUpdatesSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_VERSION = "row-version"
  const val ROW_STORE_MANAGED = "row-store-managed"
  const val ROW_AUTO_UPDATE = "row-auto-update"
  const val ROW_INCLUDE_BETA = "row-include-beta"
  const val ROW_CHECK_FOR_UPDATES = "row-check-for-updates"
}

@Composable
fun AppUpdatesSettingsScreen(
  state: AppUpdatesSettingsState,
  onEvent: (AppUpdatesSettingsEvent) -> Unit,
  labels: AppUpdatesSettingsLabels,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(AppUpdatesSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_updates__title),
    onNavigationClick = { onEvent(AppUpdatesSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(AppUpdatesSettingsTestTags.SCROLLER)
    ) {
      if (!state.manageMollyUpdates) {
        item {
          Rows.TextRow(text = labels.storeManagedMessage, modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_STORE_MANAGED))
        }
      }
      item {
        Rows.TextRow(text = labels.version, label = state.versionName, modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_VERSION))
      }
      if (state.manageMollyUpdates) {
        item {
          Rows.ToggleRow(
            checked = state.autoUpdateEnabled,
            text = labels.autoUpdates,
            label = labels.autoUpdatesDescription,
            onCheckChanged = { onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(it)) },
            modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_AUTO_UPDATE)
          )
        }
        item {
          Rows.ToggleRow(
            checked = state.includeBetaEnabled,
            text = labels.includeBeta,
            label = labels.includeBetaDescription,
            onCheckChanged = { onEvent(AppUpdatesSettingsEvent.IncludeBetaToggled(it)) },
            modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_INCLUDE_BETA)
          )
        }
        item {
          Rows.TextRow(
            text = labels.checkForUpdates,
            label = labels.lastChecked,
            enabled = state.autoUpdateEnabled,
            onClick = { onEvent(AppUpdatesSettingsEvent.CheckForUpdatesClicked) },
            modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_CHECK_FOR_UPDATES).semantics {
              contentDescription = labels.lastCheckedContentDescription
            }
          )
        }
      }
    }
  }
}
