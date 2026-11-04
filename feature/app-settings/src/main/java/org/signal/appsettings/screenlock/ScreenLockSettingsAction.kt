/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

sealed interface ScreenLockSettingsAction {
  data object NavigateBack : ScreenLockSettingsAction
  data object EnableBiometricScreenLock : ScreenLockSettingsAction
  data object ApplyScreenSecurity : ScreenLockSettingsAction
}
