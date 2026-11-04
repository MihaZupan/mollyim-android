/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.TextSecurePreferences

object ScreenLockSettingsRepository {
  fun isScreenLockEnabled(): Boolean = TextSecurePreferences.isBiometricScreenLockEnabled(AppDependencies.application)

  fun setScreenLockEnabled(enabled: Boolean) {
    TextSecurePreferences.setBiometricScreenLockEnabled(AppDependencies.application, enabled)
    ScreenLockController.enableAutoLock(enabled)
    ScreenLockController.lockScreenAtStart = false
  }
}
