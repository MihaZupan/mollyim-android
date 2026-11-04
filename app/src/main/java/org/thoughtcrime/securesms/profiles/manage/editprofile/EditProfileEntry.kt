/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.signal.appsettings.editprofile.EditProfileAction
import org.signal.appsettings.editprofile.EditProfileEvent
import org.signal.appsettings.editprofile.EditProfileScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.RegistrationIntents
import org.thoughtcrime.securesms.util.PlayStoreUtil

/**
 * Hosts [EditProfileScreen] and carries out its [EditProfileAction]s. The view model navigates by sending events to
 * [parentEventEmitter], so this works from whichever host owns the back stack.
 *
 * @param resultEvents events the screens this one opens report back with, such as a newly picked avatar.
 */
@Composable
fun EditProfileEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  resultEvents: Flow<EditProfileEvent> = emptyFlow()
) {
  val viewModel: EditProfileViewModel = viewModel { EditProfileViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val self = remember { Recipient.self() }
  val context = LocalContext.current

  LaunchedEffect(resultEvents) {
    resultEvents.collect(viewModel::onEvent)
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      EditProfileAction.OpenAvatarPreview -> context.startActivity(AvatarPreviewActivity.intentFromRecipientId(context, Recipient.self().id))
      EditProfileAction.OpenAppUpdate -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
      EditProfileAction.StartReregistration -> context.startActivity(
        if (EditProfileRepository.isLinkedDevice()) RegistrationIntents.newIntentForReLinkDevice(context) else RegistrationIntents.newIntentForReRegistration(context)
      )
    }
  }

  EditProfileScreen(
    state = state,
    onEvent = viewModel::onEvent,
    avatar = { AvatarImage(recipient = self, modifier = Modifier.fillMaxSize()) }
  )
}
