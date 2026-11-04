package org.thoughtcrime.securesms.components.settings.app.privacy

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsState
import org.signal.appsettings.privacy.PrivacySettingsState.Dialog
import org.signal.appsettings.privacy.PrivacySettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

class PrivacySettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  @Suppress("UNUSED_PARAMETER")
  scrollToPaymentLock: Boolean = false
) : EventDrivenViewModel<PrivacySettingsEvent>(TAG) {
  companion object {
    private val TAG = Log.tag(PrivacySettingsViewModel::class)
  }

  private val _state = MutableStateFlow(PrivacySettingsState())
  private val _actions = Channel<PrivacySettingsAction>(Channel.BUFFERED)
  val state: StateFlow<PrivacySettingsState> = _state.asStateFlow()
  val actions: Flow<PrivacySettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()
  }

  override suspend fun processEvent(event: PrivacySettingsEvent) {
    when (event) {
      PrivacySettingsEvent.ScreenResumed -> {
        refresh()
        _state.update { it.copy(blockedCount = PrivacySettingsRepository.getBlockedCount()) }
      }
      PrivacySettingsEvent.SettingsChanged -> refresh()
      PrivacySettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      PrivacySettingsEvent.ScrolledToPaymentLock -> Unit
      PrivacySettingsEvent.PhoneNumberClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.PhoneNumberPrivacy)
      PrivacySettingsEvent.BlockedClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.BlockedUsers)
      is PrivacySettingsEvent.ReadReceiptsToggled -> {
        if (PrivacySettingsRepository.canChangeMessagingSettings()) {
          PrivacySettingsRepository.setReadReceiptsEnabled(event.enabled)
          refresh()
        }
      }
      is PrivacySettingsEvent.TypingIndicatorsToggled -> {
        if (PrivacySettingsRepository.canChangeMessagingSettings()) {
          PrivacySettingsRepository.setTypingIndicatorsEnabled(event.enabled)
          refresh()
        }
      }
      PrivacySettingsEvent.DefaultTimerClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.ExpiringMessages)
      is PrivacySettingsEvent.BlockUnknownToggled -> {
        PrivacySettingsRepository.setBlockUnknownEnabled(event.enabled)
        refresh()
      }
      is PrivacySettingsEvent.PassphraseLockToggled ->
        _actions.send(PrivacySettingsAction.ChangePassphraseProtection(event.enabled))
      PrivacySettingsEvent.ChangePassphraseClicked -> {
        if (_state.value.passphraseLock) _actions.send(PrivacySettingsAction.LaunchChangePassphrase)
      }
      PrivacySettingsEvent.PassphraseTriggersClicked -> _actions.send(PrivacySettingsAction.ChoosePassphraseTriggers)
      is PrivacySettingsEvent.PassphraseTriggersSelected -> {
        PrivacySettingsRepository.setPassphraseLockTriggers(event.triggers)
        refresh()
      }
      PrivacySettingsEvent.PassphraseLockTimeoutClicked,
      PrivacySettingsEvent.PassphraseTimeoutIntervalClicked -> _actions.send(PrivacySettingsAction.ChoosePassphraseLockTimeout)
      is PrivacySettingsEvent.PassphraseLockTimeoutSelected -> {
        PrivacySettingsRepository.setPassphraseLockTimeout(event.seconds)
        refresh()
      }
      // Old Signal events must never take its obsolete-passphrase repair/disable-service path.
      PrivacySettingsEvent.DisablePassphraseClicked ->
        _actions.send(PrivacySettingsAction.ChangePassphraseProtection(false))
      PrivacySettingsEvent.DisablePassphraseConfirmed -> Unit
      is PrivacySettingsEvent.PassphraseTimeoutToggled,
      is PrivacySettingsEvent.PassphraseTimeoutSelected -> Unit
      PrivacySettingsEvent.ScreenLockClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.ScreenLock)
      PrivacySettingsEvent.DisabledScreenLockClicked -> _state.update { it.copy(snackbar = Snackbar.ScreenLockNeedsDeviceSecurity) }
      is PrivacySettingsEvent.ScreenSecurityToggled -> {
        if (!PrivacySettingsRepository.isScreenSecurityForced()) {
          PrivacySettingsRepository.setScreenSecurityEnabled(event.enabled)
        }
        refresh()
        _actions.send(PrivacySettingsAction.ApplyScreenSecurity(_state.value.screenSecurity))
      }
      is PrivacySettingsEvent.IncognitoKeyboardToggled -> {
        PrivacySettingsRepository.setIncognitoKeyboardEnabled(event.enabled)
        refresh()
      }
      PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked -> _actions.send(PrivacySettingsAction.OpenIncognitoKeyboardLearnMore)
      PrivacySettingsEvent.EnablePaymentLockClicked,
      PrivacySettingsEvent.PaymentLockDisableAuthenticated,
      PrivacySettingsEvent.OpenDeviceSecuritySettingsClicked -> Unit
      PrivacySettingsEvent.DeviceSecuritySettingsUnavailable -> _state.update { it.copy(snackbar = Snackbar.DeviceSecuritySettingsUnavailable) }
      PrivacySettingsEvent.AdvancedClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.Advanced)
      PrivacySettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = Dialog.None) }
      PrivacySettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = Snackbar.None) }
    }
  }

  private fun refresh() {
    _state.update {
      it.copy(
        hasPhoneNumber = PrivacySettingsRepository.hasPhoneNumber(),
        readReceipts = PrivacySettingsRepository.isReadReceiptsEnabled(),
        typingIndicators = PrivacySettingsRepository.isTypingIndicatorsEnabled(),
        universalExpireTimer = PrivacySettingsRepository.getUniversalExpireTimer(),
        isObsoletePassphraseEnabled = false,
        isDeviceSecure = PrivacySettingsRepository.isDeviceSecure(),
        screenLock = PrivacySettingsRepository.isScreenLockEnabled(),
        screenSecurity = PrivacySettingsRepository.isScreenSecurityEnabled() || PrivacySettingsRepository.isScreenSecurityForced(),
        screenSecurityForced = PrivacySettingsRepository.isScreenSecurityForced(),
        incognitoKeyboard = PrivacySettingsRepository.isIncognitoKeyboardEnabled(),
        passphraseLock = PrivacySettingsRepository.isPassphraseLockEnabled(),
        passphraseLockTriggers = PrivacySettingsRepository.getPassphraseLockTriggers(),
        passphraseLockTimeoutSeconds = PrivacySettingsRepository.getPassphraseLockTimeout(),
        blockUnknown = PrivacySettingsRepository.isBlockUnknownEnabled(),
        canChangeMessagingSettings = PrivacySettingsRepository.canChangeMessagingSettings()
      )
    }
  }
}
