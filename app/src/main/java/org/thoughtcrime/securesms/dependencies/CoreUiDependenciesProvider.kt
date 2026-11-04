/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.signal.core.ui.CoreUiDependencies
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.BackupUtil

object CoreUiDependenciesProvider : CoreUiDependencies.Provider {
  override fun provideBackupBaseDirName(): String = BackupUtil.getBackupBaseDirName()

  override fun provideIsIncognitoKeyboardEnabled(): Boolean {
    // Neither privacy setting is a plaintext bootstrap exemption.
    return !AppDependencies.isInitialized ||
      KeyCachingService.isLocked() ||
      SignalStore.settings.isIncognitoKeyboardEnabled
  }

  override fun provideIsScreenSecurityEnabled(): Boolean {
    return ScreenLockController.alwaysSetSecureFlagOnResume ||
      !AppDependencies.isInitialized ||
      KeyCachingService.isLocked() ||
      SignalStore.settings.isScreenSecurityEnabled
  }
}
