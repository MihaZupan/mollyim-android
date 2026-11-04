/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.AndroidNavHostFragment
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfo
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfoProvider
import org.thoughtcrime.securesms.compose.FragmentBackPressedState
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity

/**
 * Registers the routes within the appearance section of app settings.
 */
fun EntryProviderScope<NavKey>.registerAppearanceRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.AppearanceRoute.Appearance> {
    AppearanceSettingsEntry(parentEventEmitter = parentEventEmitter)
  }
  detailEntry<AppSettingsRoute.AppearanceRoute.Wallpaper> {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
      context.startActivity(Intent(context, ChatWallpaperActivity::class.java))
      parentEventEmitter(AppSettingsNavigationEvent.NavigateBack)
    }
  }
  detailEntry<AppSettingsRoute.AppearanceRoute.AppIconSelection> {
    AppIconEntry()
  }
}

@Composable
private fun AppIconEntry() {
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)
  AndroidNavHostFragment(
    clazz = AppearanceSettingsNavHostFragment::class.java,
    modifier = Modifier.fillMaxSize(),
    arguments = bundleOf(AppearanceSettingsNavHostFragment.START_DESTINATION to R.id.appIconSelectionFragment),
    onUpdate = { backPressedState.attach(it) }
  )
}

class AppearanceSettingsNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {
  companion object {
    const val START_DESTINATION = "appearance.start.destination"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val graph = navController.navInflater.inflate(R.navigation.app_settings_with_change_number)
    graph.setStartDestination(requireArguments().getInt(START_DESTINATION))
    navController.setGraph(graph, null)
    super.onCreate(savedInstanceState)
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> = navController.currentBackStackEntryFlow.map {
    if (navController.previousBackStackEntry != null) FragmentBackPressedInfo.Enabled { navController.popBackStack() } else FragmentBackPressedInfo.Disabled
  }
}
