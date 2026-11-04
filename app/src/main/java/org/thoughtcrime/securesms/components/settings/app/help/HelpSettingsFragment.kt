/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.help.HelpSettingsAction
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.Util
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

class HelpSettingsFragment : ComposeFragment() {
  @Composable
  override fun FragmentContent() {
    HelpSettingsEntry(::handleAction)
  }

  private fun handleAction(action: HelpSettingsAction) {
    when (action) {
      HelpSettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      HelpSettingsAction.OpenWebsite -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.website_url))
      HelpSettingsAction.OpenSupportCenter -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.support_center_url))
      HelpSettingsAction.NavigateToAppUpdates -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_appUpdatesFragment)
      HelpSettingsAction.NavigateToContactUs -> findNavController().safeNavigate(
        R.id.action_helpSettingsFragment_to_helpFragment,
        bundleOf("start_category_index" to (arguments?.getInt("start_category_index") ?: 0))
      )
      is HelpSettingsAction.CopyVersionToClipboard -> {
        Util.copyToClipboard(requireContext(), action.versionName)
        Toast.makeText(requireContext(), AppSettingsR.string.HelpSettingsFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show()
      }
      HelpSettingsAction.NavigateToDebugLog -> {
        if (TextSecurePreferences.isLogEnabled(requireContext())) findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
      }
      HelpSettingsAction.NavigateToLicenses -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_licenseFragment)
      HelpSettingsAction.OpenTermsAndPrivacyPolicy -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.terms_and_privacy_policy_url))
    }
  }
}
