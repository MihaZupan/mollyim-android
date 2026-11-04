package org.thoughtcrime.securesms.components.settings.app.help

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
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.util.TextSecurePreferences

fun EntryProviderScope<NavKey>.registerHelpRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.HelpRoute.Settings> { route ->
    HelpFragmentEntry(R.id.helpSettingsFragment, bundleOf("start_category_index" to route.startCategoryIndex))
  }
  detailEntry<AppSettingsRoute.HelpRoute.Help> {
    HelpFragmentEntry(R.id.helpFragment)
  }
  detailEntry<AppSettingsRoute.HelpRoute.Licenses> {
    HelpFragmentEntry(R.id.licenseFragment)
  }
  detailEntry<AppSettingsRoute.HelpRoute.DebugLog> {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
      if (TextSecurePreferences.isLogEnabled(context)) {
        context.startActivity(Intent(context, SubmitDebugLogActivity::class.java))
      }
      parentEventEmitter(AppSettingsNavigationEvent.NavigateBack)
    }
  }
}

@Composable
private fun HelpFragmentEntry(destination: Int, startArgs: Bundle = Bundle.EMPTY) {
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)
  AndroidNavHostFragment(
    clazz = HelpSettingsNavHostFragment::class.java,
    modifier = Modifier.fillMaxSize(),
    arguments = bundleOf(HelpSettingsNavHostFragment.START_DESTINATION to destination, HelpSettingsNavHostFragment.START_ARGUMENTS to startArgs),
    onUpdate = { backPressedState.attach(it) }
  )
}

class HelpSettingsNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {
  companion object {
    const val START_DESTINATION = "help.start.destination"
    const val START_ARGUMENTS = "help.start.arguments"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val args = requireArguments()
    val graph = navController.navInflater.inflate(R.navigation.app_settings_with_change_number)
    graph.setStartDestination(args.getInt(START_DESTINATION))
    navController.setGraph(graph, args.getBundle(START_ARGUMENTS))
    super.onCreate(savedInstanceState)
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> = navController.currentBackStackEntryFlow.map {
    if (navController.previousBackStackEntry != null) FragmentBackPressedInfo.Enabled { navController.popBackStack() } else FragmentBackPressedInfo.Disabled
  }
}
