/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class ScreenLockSettingsLayoutTest : SignalLayoutStressTest() {
  @Test
  fun `off`() = assertPassesLayoutChecks {
    ScreenLockSettingsScreen(state = ScreenLockSettingsState(false), onEvent = {})
  }

  @Test
  fun `on`() = assertPassesLayoutChecks {
    ScreenLockSettingsScreen(state = ScreenLockSettingsState(true), onEvent = {})
  }
}
