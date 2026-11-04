/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.appsettings.updates.AppUpdatesSettingsAction
import org.signal.appsettings.updates.AppUpdatesSettingsEvent
import org.signal.appsettings.updates.AppUpdatesSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.milliseconds

class AppUpdatesSettingsViewModel : EventDrivenViewModel<AppUpdatesSettingsEvent>(Log.tag(AppUpdatesSettingsViewModel::class)) {
  private val _state = MutableStateFlow(readState())
  private val _actions = Channel<AppUpdatesSettingsAction>(Channel.BUFFERED)
  val state: StateFlow<AppUpdatesSettingsState> = _state.asStateFlow()
  val actions: Flow<AppUpdatesSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: AppUpdatesSettingsEvent) {
    when (event) {
      AppUpdatesSettingsEvent.ScreenResumed -> refresh()
      AppUpdatesSettingsEvent.NavigateBackClicked -> _actions.send(AppUpdatesSettingsAction.NavigateBack)
      is AppUpdatesSettingsEvent.AutoUpdateToggled -> {
        if (AppUpdatesSettingsRepository.isManagedUpdateChannel()) {
          AppUpdatesSettingsRepository.setUpdateApkEnabled(event.enabled)
          if (event.enabled) AppUpdatesSettingsRepository.checkForUpdates()
        }
        refresh()
      }
      is AppUpdatesSettingsEvent.IncludeBetaToggled -> {
        if (AppUpdatesSettingsRepository.isManagedUpdateChannel()) {
          AppUpdatesSettingsRepository.setIncludeBetaEnabled(event.enabled)
          AppUpdatesSettingsRepository.dismissInstallPrompt()
          AppUpdatesSettingsRepository.checkForUpdates()
        }
        refresh()
      }
      AppUpdatesSettingsEvent.CheckForUpdatesClicked -> {
        if (AppUpdatesSettingsRepository.isManagedUpdateChannel() && AppUpdatesSettingsRepository.isUpdateApkEnabled()) {
          AppUpdatesSettingsRepository.checkForUpdates()
        }
      }
    }
  }

  private fun refresh() {
    _state.value = readState()
  }

  private fun readState(): AppUpdatesSettingsState = AppUpdatesSettingsState(
    versionName = AppUpdatesSettingsRepository.getVersionName(),
    lastCheckedTime = AppUpdatesSettingsRepository.getLastSuccessfulCheck().milliseconds,
    autoUpdateEnabled = AppUpdatesSettingsRepository.isUpdateApkEnabled(),
    includeBetaEnabled = AppUpdatesSettingsRepository.isIncludeBetaEnabled(),
    manageMollyUpdates = AppUpdatesSettingsRepository.isManagedUpdateChannel()
  )
}
