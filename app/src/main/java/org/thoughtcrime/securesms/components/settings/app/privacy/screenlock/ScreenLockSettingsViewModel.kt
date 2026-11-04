/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.signal.appsettings.screenlock.ScreenLockSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

class ScreenLockSettingsViewModel : EventDrivenViewModel<ScreenLockSettingsEvent>(Log.tag(ScreenLockSettingsViewModel::class)) {
  private val _state = MutableStateFlow(ScreenLockSettingsState())
  private val _actions = Channel<ScreenLockSettingsAction>(Channel.BUFFERED)
  val state: StateFlow<ScreenLockSettingsState> = _state.asStateFlow()
  val actions: Flow<ScreenLockSettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()
  }

  override suspend fun processEvent(event: ScreenLockSettingsEvent) {
    when (event) {
      ScreenLockSettingsEvent.ScreenResumed -> refresh()
      ScreenLockSettingsEvent.NavigateBackClicked -> _actions.send(ScreenLockSettingsAction.NavigateBack)
      is ScreenLockSettingsEvent.ScreenLockToggleRequested -> {
        if (event.enabled) {
          _actions.send(ScreenLockSettingsAction.EnableBiometricScreenLock)
        } else {
          setEnabled(false)
        }
      }
      is ScreenLockSettingsEvent.ScreenLockToggled -> setEnabled(event.enabled)
    }
  }

  private suspend fun setEnabled(enabled: Boolean) {
    ScreenLockSettingsRepository.setScreenLockEnabled(enabled)
    refresh()
    _actions.send(ScreenLockSettingsAction.ApplyScreenSecurity)
  }

  private fun refresh() {
    _state.value = ScreenLockSettingsState(isScreenLockEnabled = ScreenLockSettingsRepository.isScreenLockEnabled())
  }
}
