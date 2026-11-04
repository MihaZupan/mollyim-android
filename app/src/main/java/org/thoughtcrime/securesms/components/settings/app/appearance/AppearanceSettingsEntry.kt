/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsEvent
import org.signal.appsettings.appearance.AppearanceSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.SplashScreenUtil
import org.signal.appsettings.R as AppSettingsR

/**
 * Hosts [AppearanceSettingsScreen] and carries out its [AppearanceSettingsAction]s. The view model navigates by sending
 * events to [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun AppearanceSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: AppearanceSettingsViewModel = viewModel { AppearanceSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val activity = LocalActivity.current
  LifecycleResumeEffect(Unit) {
    viewModel.onEvent(AppearanceSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is AppearanceSettingsAction.ApplySplashScreenTheme -> SplashScreenUtil.setSplashScreenThemeIfNecessary(activity, action.theme.toSettingsTheme(), action.dynamicColors)
    }
  }

  AppearanceSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    languageLabels = stringArrayResource(AppSettingsR.array.language_entries),
    languageValues = stringArrayResource(AppSettingsR.array.language_values),
    dynamicColorsTitle = stringResource(R.string.preferences_appearance__dynamic_colors),
    dynamicColorsDescription = stringResource(R.string.preferences_appearance__use_system_colors_for_the_app_theme)
  )
}
