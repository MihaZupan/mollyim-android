/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment
import org.thoughtcrime.securesms.PassphraseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TimeDurationPickerDialog
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ConversationUtil
import org.signal.core.ui.util.ExpirationUtil
import org.thoughtcrime.securesms.util.WindowUtil
import kotlin.time.Duration.Companion.seconds
import org.signal.appsettings.R as AppSettingsR

private val TAG = Log.tag(PrivacySettingsViewModel::class)

/**
 * Hosts [PrivacySettingsScreen] and carries out its [PrivacySettingsAction]s. The view model navigates by sending
 * events to [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun PrivacySettingsEntry(
  scrollToPaymentLock: Boolean,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: PrivacySettingsViewModel = viewModel {
    PrivacySettingsViewModel(
      parentEventEmitter = parentEventEmitter,
      scrollToPaymentLock = scrollToPaymentLock
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = LocalActivity.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val incognitoLearnMore = stringResource(AppSettingsR.string.preferences__incognito_keyboard_learn_more)
  CollectActions(viewModel.actions) { action ->
    when (action) {
      PrivacySettingsAction.LaunchChangePassphrase -> showPassphraseDialog(activity, null, viewModel)
      is PrivacySettingsAction.ChangePassphraseProtection -> showPassphraseDialog(activity, action.enabled, viewModel)
      PrivacySettingsAction.ChoosePassphraseTriggers -> {
        val values = context.resources.getStringArray(R.array.pref_passphrase_lock_trigger_entries)
        val labels = context.resources.getStringArray(R.array.pref_passphrase_lock_trigger_values)
        val checked = values.map { it in viewModel.state.value.passphraseLockTriggers }.toBooleanArray()
        MaterialAlertDialogBuilder(context)
          .setTitle(R.string.preferences__automatic_lockdown)
          .setMultiChoiceItems(labels, checked) { _, index, selected -> checked[index] = selected }
          .setPositiveButton(android.R.string.ok) { _, _ ->
            viewModel.onEvent(PrivacySettingsEvent.PassphraseTriggersSelected(values.filterIndexed { index, _ -> checked[index] }.toSet()))
          }
          .setNegativeButton(android.R.string.cancel, null)
          .show()
      }
      PrivacySettingsAction.ChoosePassphraseLockTimeout -> {
        val host = activity as? PassphraseActivity ?: return@CollectActions
        val manager = host.supportFragmentManager
        manager.clearFragmentResult(TimeDurationPickerDialog.RESULT_DURATION)
        manager.setFragmentResultListener(TimeDurationPickerDialog.RESULT_DURATION, lifecycleOwner) { _, bundle ->
          val seconds = bundle.getLong(TimeDurationPickerDialog.RESULT_KEY_DURATION_SECONDS)
          viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutSelected(seconds))
          manager.clearFragmentResultListener(TimeDurationPickerDialog.RESULT_DURATION)
        }
        TimeDurationPickerDialog.create(viewModel.state.value.passphraseLockTimeoutSeconds.seconds).show(manager, null)
      }
      PrivacySettingsAction.OpenIncognitoKeyboardLearnMore -> CommunicationActions.openBrowserLink(context, incognitoLearnMore)
      PrivacySettingsAction.OpenDeviceSecuritySettings -> {
        if (!openDeviceSecuritySettings(context)) {
          viewModel.onEvent(PrivacySettingsEvent.DeviceSecuritySettingsUnavailable)
        }
      }
      is PrivacySettingsAction.ApplyScreenSecurity -> activity?.let { WindowUtil.initializeScreenshotSecurity(context, it.window) }
    }
  }

  PrivacySettingsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    screenLockSummaryOverride = stringResource(R.string.PrivacySettingsFragment__lock_molly_access_with_fingerprint_or_face_recognition),
    blockedContactRows = {
      item {
        Rows.ToggleRow(
          checked = state.blockUnknown,
          text = stringResource(R.string.preferences__block_unknown),
          label = stringResource(R.string.preferences__block_users_youve_never_been_in_contact_with_and_who_are_not_saved_in_your_contacts),
          onCheckChanged = { viewModel.onEvent(PrivacySettingsEvent.BlockUnknownToggled(it)) }
        )
      }
    },
    dataAtRestRows = {
      item {
        Dividers.Default()
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsFragment_data_at_rest))
      }
      item {
        Rows.ToggleRow(
          checked = state.passphraseLock,
          text = stringResource(R.string.preferences__passphrase_lock),
          label = stringResource(R.string.preferences__protect_molly_database_with_a_passphrase),
          onCheckChanged = { viewModel.onEvent(PrivacySettingsEvent.PassphraseLockToggled(it)) }
        )
      }
      item {
        Rows.TextRow(
          text = stringResource(AppSettingsR.string.preferences__change_passphrase),
          enabled = state.passphraseLock,
          onClick = { viewModel.onEvent(PrivacySettingsEvent.ChangePassphraseClicked) }
        )
      }
      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__automatic_lockdown),
          enabled = state.passphraseLock,
          onClick = { viewModel.onEvent(PrivacySettingsEvent.PassphraseTriggersClicked) }
        )
      }
      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__device_lock_timeout),
          label = if (state.passphraseLockTimeoutSeconds <= 0) {
            stringResource(R.string.AppProtectionPreferenceFragment_instant)
          } else {
            ExpirationUtil.getExpirationDisplayValue(context, state.passphraseLockTimeoutSeconds.toInt())
          },
          enabled = state.passphraseLock && PassphraseLockTriggerPreference(state.passphraseLockTriggers).isTimeoutEnabled,
          onClick = { viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutClicked) }
        )
      }
    }
  )
}

private fun openDeviceSecuritySettings(context: Context): Boolean {
  val intent = when {
    Build.VERSION.SDK_INT >= 30 -> Intent(Settings.ACTION_BIOMETRIC_ENROLL)
    Build.VERSION.SDK_INT >= 28 -> Intent(Settings.ACTION_FINGERPRINT_ENROLL)
    else -> Intent(Settings.ACTION_SECURITY_SETTINGS)
  }

  return try {
    context.startActivity(intent)
    true
  } catch (e: ActivityNotFoundException) {
    Log.w(TAG, "Failed to navigate to system settings.", e)
    false
  }
}

private fun showPassphraseDialog(activity: Activity?, enabled: Boolean?, viewModel: PrivacySettingsViewModel) {
  val host = activity as? PassphraseActivity ?: return
  val dialog = if (enabled == null) {
    ChangePassphraseDialogFragment.newInstance()
  } else {
    ChangePassphraseDialogFragment.newInstance(
      if (enabled) ChangePassphraseDialogFragment.MODE_ENABLE else ChangePassphraseDialogFragment.MODE_DISABLE
    )
  }
  dialog.setMasterSecretChangedListener { masterSecret ->
    if (enabled == null) {
      masterSecret.close()
      Toast.makeText(host, R.string.preferences__passphrase_changed, Toast.LENGTH_LONG).show()
    } else {
      PrivacySettingsRepository.setPassphraseLockEnabled(enabled)
      host.setMasterSecret(masterSecret)
      ConversationUtil.refreshRecipientShortcuts()
    }
    viewModel.onEvent(PrivacySettingsEvent.SettingsChanged)
  }
  dialog.show(host.supportFragmentManager, "ChangePassphraseDialogFragment")
}
