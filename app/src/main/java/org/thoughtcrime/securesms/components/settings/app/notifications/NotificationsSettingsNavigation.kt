package org.thoughtcrime.securesms.components.settings.app.notifications

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.AndroidNavHostFragment
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfo
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfoProvider
import org.thoughtcrime.securesms.compose.FragmentBackPressedState

/**
 * Keep the existing notification/profile fragment flows inside the modern detail pane, including
 * Molly's actual UnifiedPush fragment. The stable-ID wrapper preserves their nested saved state.
 */
fun EntryProviderScope<NavKey>.registerNotificationsRoutes() {
  detailEntry<AppSettingsRoute.NotificationsRoute.Notifications> {
    NotificationsEntry(R.id.notificationsSettingsFragment)
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.PlayServicesProblem> { route ->
    NotificationsEntry(R.id.notificationsSettingsFragment, bundleOf("play_services_error_code" to route.errorCode))
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.UnifiedPushSettings> {
    NotificationsEntry(R.id.notificationUnifiedPushFragment)
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.MutedNotifications> {
    NotificationsEntry(R.id.mutedNotificationsFragment)
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.NotificationProfiles> {
    NotificationsEntry(R.id.notificationProfilesFragment)
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.EditProfile> { route ->
    NotificationsEntry(R.id.editNotificationProfileFragment, bundleOf("profileId" to route.profileId))
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.ProfileDetails> { route ->
    NotificationsEntry(R.id.notificationProfileDetailsFragment, bundleOf("profileId" to route.profileId))
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.AddAllowedMembers> { route ->
    NotificationsEntry(R.id.addAllowedMembersFragment, bundleOf("profileId" to route.profileId))
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.SelectRecipients> { route ->
    NotificationsEntry(R.id.selectRecipientsFragment, bundleOf("profileId" to route.profileId, "currentSelection" to route.currentSelection))
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.EditSchedule> { route ->
    NotificationsEntry(R.id.editNotificationProfileScheduleFragment, bundleOf("profileId" to route.profileId, "createMode" to route.createMode))
  }
  detailEntry<AppSettingsRoute.NotificationsRoute.Created> { route ->
    NotificationsEntry(R.id.notificationProfileCreatedFragment, bundleOf("profileId" to route.profileId))
  }
}

@Composable
private fun NotificationsEntry(destination: Int, startArgs: Bundle = Bundle.EMPTY) {
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)
  AndroidNavHostFragment(
    clazz = NotificationsSettingsNavHostFragment::class.java,
    modifier = Modifier.fillMaxSize(),
    arguments = bundleOf(
      NotificationsSettingsNavHostFragment.START_DESTINATION to destination,
      NotificationsSettingsNavHostFragment.START_ARGUMENTS to startArgs
    ),
    onUpdate = { backPressedState.attach(it) }
  )
}

class NotificationsSettingsNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {
  companion object {
    const val START_DESTINATION = "notifications.start.destination"
    const val START_ARGUMENTS = "notifications.start.arguments"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val arguments = requireArguments()
    val graph = navController.navInflater.inflate(R.navigation.app_settings_with_change_number)
    graph.setStartDestination(arguments.getInt(START_DESTINATION))
    navController.setGraph(graph, arguments.getBundle(START_ARGUMENTS))
    super.onCreate(savedInstanceState)
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> {
    return navController.currentBackStackEntryFlow.map {
      if (navController.previousBackStackEntry != null) {
        FragmentBackPressedInfo.Enabled { navController.popBackStack() }
      } else {
        FragmentBackPressedInfo.Disabled
      }
    }
  }
}
