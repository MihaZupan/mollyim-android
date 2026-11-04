/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.home.AppSettingsAction
import org.signal.appsettings.home.AppSettingsEvent
import org.signal.appsettings.home.AppSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.banner.BannerManager
import org.thoughtcrime.securesms.banner.banners.DeprecatedBuildBanner
import org.thoughtcrime.securesms.banner.banners.UnauthorizedBanner
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageMedium
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Hosts [AppSettingsScreen] and carries out its [AppSettingsAction]s. The view model navigates by sending events to
 * [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun AppSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  selectedRoute: AppSettingsRoute? = null
) {
  val viewModel: AppSettingsViewModel = viewModel { AppSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val self by remember { AppSettingsRepository.observeSelf() }.collectAsStateWithLifecycle(initialValue = null)
  val context = LocalContext.current

  val bannerManager = remember {
    BannerManager(
      banners = listOf(
        DeprecatedBuildBanner(),
        UnauthorizedBanner(context)
      )
    )
  }

  val donateUrl = stringResource(R.string.donate_url)
  CollectActions(viewModel.actions) { action ->
    when (action) {
      AppSettingsAction.OpenDonateUrl -> CommunicationActions.openBrowserLink(context, donateUrl)
    }
  }

  AppSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    selectedRoute = selectedRoute,
    avatar = { self?.let { SelfAvatar(it) } },
    banner = { bannerManager.Banner() },
    profileExtra = {
      state.linkedDeviceCount?.let { count ->
        Text(
          text = if (count > 0) context.resources.getQuantityString(R.plurals.BioTextPreference_n_linked_devices, count, count) else stringResource(R.string.BioTextPreference_no_linked_devices),
          modifier = Modifier.clickable(enabled = state.isPrimaryDevice && !state.isLinkedDevice && state.isRegisteredAndUpToDate) {
            viewModel.onEvent(AppSettingsEvent.LinkedDevicesClicked)
          }
        )
      }
    },
    networkRow = {
      Rows.TextRow(
        text = stringResource(R.string.preferences__network),
        icon = SignalIcons.Data.painter,
        onClick = { viewModel.onEvent(AppSettingsEvent.NetworkClicked) }
      )
    }
  )
}

@Composable
private fun SelfAvatar(self: Recipient) {
  Box {
    AvatarImage(
      recipient = self,
      modifier = Modifier.fillMaxSize()
    )

    val featuredBadge by rememberRecipientField(self) { featuredBadge }
    featuredBadge?.let { badge ->
      BadgeImageMedium(
        badge = badge,
        modifier = Modifier
          .size(24.dp)
          .align(Alignment.BottomEnd)
      )
    }
  }
}
