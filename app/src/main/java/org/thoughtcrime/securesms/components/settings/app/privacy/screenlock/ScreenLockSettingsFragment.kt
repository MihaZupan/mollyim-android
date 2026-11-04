package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.signal.appsettings.screenlock.ScreenLockSettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment
import org.thoughtcrime.securesms.util.WindowUtil
import org.signal.appsettings.R as AppSettingsR

class ScreenLockSettingsFragment : ComposeFragment() {
  private val viewModel: ScreenLockSettingsViewModel by viewModels()
  private val biometricEnrollment = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    authenticate()
  }

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(ScreenLockSettingsEvent.ScreenResumed)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectActions(viewModel.actions) { action ->
      when (action) {
        ScreenLockSettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        ScreenLockSettingsAction.EnableBiometricScreenLock -> enableBiometricScreenLock()
        ScreenLockSettingsAction.ApplyScreenSecurity -> {
          (activity as? PassphraseActivity)?.blockRecentAppsScreenshot(viewModel.state.value.isScreenLockEnabled)
          WindowUtil.initializeScreenshotSecurity(requireContext(), requireActivity().window)
        }
      }
    }
    ScreenLockSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent,
      description = stringResource(R.string.PrivacySettingsFragment__lock_molly_access_with_fingerprint_or_face_recognition),
      toggleLabel = stringResource(AppSettingsR.string.preferences_app_protection__screen_lock)
    )
  }

  private fun enableBiometricScreenLock() {
    when (BiometricDialogFragment.checkBiometricAvailability(requireContext(), BiometricDialogFragment.BIOMETRIC_AUTHENTICATORS_ALLOWED)) {
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        Toast.makeText(context, R.string.PrivacySettingsFragment__no_biometric_features_available_on_this_device, Toast.LENGTH_LONG).show()
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
        if (Build.VERSION.SDK_INT >= 30) {
          try {
            biometricEnrollment.launch(
              Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricDialogFragment.BIOMETRIC_AUTHENTICATORS_ALLOWED)
            )
          } catch (e: ActivityNotFoundException) {
            Log.w(Log.tag(ScreenLockSettingsFragment::class), "Failed to navigate to system settings.", e)
            Toast.makeText(context, AppSettingsR.string.PrivacySettingsFragment__failed_to_navigate_to_system_settings, Toast.LENGTH_SHORT).show()
          }
        } else {
          Toast.makeText(context, R.string.PrivacySettingsFragment__please_first_setup_your_biometrics_in_android_settings, Toast.LENGTH_LONG).show()
        }
      }
      else -> authenticate()
    }
  }

  private fun authenticate() {
    BiometricDialogFragment.authenticate(requireActivity(), object : BiometricDialogFragment.Listener {
      override fun onSuccess(): Boolean {
        viewModel.onEvent(ScreenLockSettingsEvent.ScreenLockToggled(true))
        return true
      }

      override fun onError(errString: CharSequence): Boolean {
        Toast.makeText(context, errString, Toast.LENGTH_LONG).show()
        return true
      }
    })
  }
}
