package org.thoughtcrime.securesms.components.settings.app.updates

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry

fun EntryProviderScope<NavKey>.registerAppUpdatesRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.AppUpdates> {
    AppUpdatesSettingsEntry { parentEventEmitter(AppSettingsNavigationEvent.NavigateBack) }
  }
}
