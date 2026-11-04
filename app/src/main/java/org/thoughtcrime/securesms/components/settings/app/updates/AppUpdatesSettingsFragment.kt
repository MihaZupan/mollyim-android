/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment

class AppUpdatesSettingsFragment : ComposeFragment() {
  @Composable
  override fun FragmentContent() {
    AppUpdatesSettingsEntry { requireActivity().onBackPressedDispatcher.onBackPressed() }
  }
}
