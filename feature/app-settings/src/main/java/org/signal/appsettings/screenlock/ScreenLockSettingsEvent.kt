/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

sealed interface ScreenLockSettingsEvent {
  data object ScreenResumed : ScreenLockSettingsEvent
  data object NavigateBackClicked : ScreenLockSettingsEvent
  data class ScreenLockToggleRequested(val enabled: Boolean) : ScreenLockSettingsEvent
  /** Only the host's successful Molly biometric callback may enable the lock. */
  data class ScreenLockToggled(val enabled: Boolean) : ScreenLockSettingsEvent
}
