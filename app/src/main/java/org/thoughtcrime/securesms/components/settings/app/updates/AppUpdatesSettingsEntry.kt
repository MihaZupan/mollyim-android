package org.thoughtcrime.securesms.components.settings.app.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.updates.AppUpdatesSettingsAction
import org.signal.appsettings.updates.AppUpdatesSettingsLabels
import org.signal.appsettings.updates.AppUpdatesSettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import kotlin.time.Duration
import org.signal.appsettings.R as AppSettingsR

@Composable
fun AppUpdatesSettingsEntry(onNavigateBack: () -> Unit) {
  val viewModel: AppUpdatesSettingsViewModel = viewModel { AppUpdatesSettingsViewModel() }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val relativeTime = remember(state.lastCheckedTime, context) {
    if (state.lastCheckedTime > Duration.ZERO) {
      DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), state.lastCheckedTime.inWholeMilliseconds)
    } else {
      null
    }
  }
  val lastChecked = stringResource(R.string.AppUpdatesSettingsFragment__last_checked_s, relativeTime?.first ?: stringResource(R.string.preferences__never))
  val lastCheckedDescription = relativeTime?.second?.let { stringResource(R.string.AppUpdatesSettingsFragment__last_checked_s, it) } ?: lastChecked
  CollectActions(viewModel.actions) {
    when (it) {
      AppUpdatesSettingsAction.NavigateBack -> onNavigateBack()
    }
  }
  AppUpdatesSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    labels = AppUpdatesSettingsLabels(
      version = stringResource(AppSettingsR.string.HelpSettingsFragment__version),
      storeManagedMessage = stringResource(R.string.HelpSettingsFragment_for_updates_please_check_your_app_store),
      autoUpdates = stringResource(R.string.preferences__autoupdate_molly),
      autoUpdatesDescription = stringResource(R.string.preferences__periodically_check_for_new_releases_and_ask_to_install_them),
      includeBeta = stringResource(R.string.preferences__include_beta_updates),
      includeBetaDescription = stringResource(R.string.preferences__beta_versions_are_intended_for_testing_purposes_and_may_contain_bugs),
      checkForUpdates = stringResource(R.string.EnableAppUpdatesMegaphone_check_for_updates),
      lastChecked = lastChecked,
      lastCheckedContentDescription = lastCheckedDescription
    )
  )
}
