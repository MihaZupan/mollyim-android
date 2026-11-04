package org.thoughtcrime.securesms.components.settings.app

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
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity

fun EntryProviderScope<NavKey>.registerRoleSettingsRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.AccountRoute.Account> {
    RoleSettingsEntry(R.id.accountSettingsFragment)
  }
  detailEntry<AppSettingsRoute.AccountRoute.ManageProfile> {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
      context.startActivity(Intent(context, EditProfileActivity::class.java))
      parentEventEmitter(AppSettingsNavigationEvent.NavigateBack)
    }
  }
  detailEntry<AppSettingsRoute.UsernameLinkRoute.UsernameLink> {
    RoleSettingsEntry(R.id.username_link_settings)
  }
  detailEntry<AppSettingsRoute.LinkDeviceRoute.LinkDevice> {
    if (SignalStore.account.isPrimaryDevice && !SignalStore.account.isLinkedDevice) {
      RoleSettingsEntry(R.id.linkDeviceFragment)
    } else {
      LaunchedEffect(Unit) { parentEventEmitter(AppSettingsNavigationEvent.NavigateBack) }
    }
  }
  detailEntry<AppSettingsRoute.DataAndStorageRoute.Proxy> {
    RoleSettingsEntry(R.id.networkPreferenceFragment)
  }
}

@Composable
private fun RoleSettingsEntry(destination: Int) {
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)
  AndroidNavHostFragment(
    clazz = RoleSettingsNavHostFragment::class.java,
    modifier = Modifier.fillMaxSize(),
    arguments = bundleOf(RoleSettingsNavHostFragment.START_DESTINATION to destination),
    onUpdate = { backPressedState.attach(it) }
  )
}

class RoleSettingsNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {
  companion object {
    const val START_DESTINATION = "role.settings.start.destination"
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
