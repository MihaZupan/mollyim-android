package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import im.molly.app.base.ApkInfo
import org.signal.appsettings.help.HelpSettingsAction
import org.signal.appsettings.help.HelpSettingsEvent
import org.signal.appsettings.help.HelpSettingsScreen
import org.signal.appsettings.help.HelpSettingsViewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.TextSecurePreferences

@Composable
fun HelpSettingsEntry(onAction: (HelpSettingsAction) -> Unit) {
  val context = LocalContext.current.applicationContext
  val viewModel: HelpSettingsViewModel = viewModel {
    HelpSettingsViewModel(
      versionName = ApkInfo.versionName,
      readLogEnabled = { TextSecurePreferences.isLogEnabled(context) },
      writeLogEnabled = { enabled ->
        TextSecurePreferences.setLogEnabled(context, enabled)
        Log.setLogging(enabled)
        if (!enabled) Log.wipeLogs()
      }
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  LifecycleResumeEffect(Unit) {
    viewModel.onEvent(HelpSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }
  CollectActions(viewModel.actions) { onAction(it) }
  HelpSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    websiteLabel = stringResource(R.string.HelpSettingsFragment__molly_im_website),
    logEnableLabel = stringResource(R.string.preferences__enable_debug_log),
    disableLogConfirmation = stringResource(R.string.HelpSettingsFragment_disable_and_delete_debug_log)
  )
}
