/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsState.Dialog
import org.signal.appsettings.privacy.PrivacySettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private var readReceipts = false
  private var typingIndicators = false
  private var screenSecurity = false
  private var incognitoKeyboard = false
  private var deviceSecure = true
  private var passphraseEnabled = false
  private var passphraseTriggers = setOf("timeout")
  private var passphraseTimeout = 300L
  private var screenSecurityForced = false
  private var canChangeMessagingSettings = true
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(PrivacySettingsRepository)

    coEvery { PrivacySettingsRepository.getBlockedCount() } returns 4
    every { PrivacySettingsRepository.hasPhoneNumber() } returns true
    every { PrivacySettingsRepository.isDeviceSecure() } answers { deviceSecure }
    every { PrivacySettingsRepository.isReadReceiptsEnabled() } answers { readReceipts }
    coEvery { PrivacySettingsRepository.setReadReceiptsEnabled(any()) } answers { readReceipts = firstArg() }
    every { PrivacySettingsRepository.isTypingIndicatorsEnabled() } answers { typingIndicators }
    coEvery { PrivacySettingsRepository.setTypingIndicatorsEnabled(any()) } answers { typingIndicators = firstArg() }
    every { PrivacySettingsRepository.getUniversalExpireTimer() } returns 1.days
    every { PrivacySettingsRepository.isScreenLockEnabled() } returns false
    every { PrivacySettingsRepository.isScreenSecurityEnabled() } answers { screenSecurity }
    every { PrivacySettingsRepository.setScreenSecurityEnabled(any()) } answers { screenSecurity = firstArg() }
    every { PrivacySettingsRepository.isIncognitoKeyboardEnabled() } answers { incognitoKeyboard }
    every { PrivacySettingsRepository.setIncognitoKeyboardEnabled(any()) } answers { incognitoKeyboard = firstArg() }
    every { PrivacySettingsRepository.isPassphraseLockEnabled() } answers { passphraseEnabled }
    every { PrivacySettingsRepository.setPassphraseLockEnabled(any()) } answers { passphraseEnabled = firstArg() }
    every { PrivacySettingsRepository.getPassphraseLockTriggers() } answers { passphraseTriggers }
    every { PrivacySettingsRepository.setPassphraseLockTriggers(any()) } answers { passphraseTriggers = firstArg() }
    every { PrivacySettingsRepository.getPassphraseLockTimeout() } answers { passphraseTimeout }
    every { PrivacySettingsRepository.setPassphraseLockTimeout(any()) } answers { passphraseTimeout = firstArg() }
    every { PrivacySettingsRepository.isBlockUnknownEnabled() } returns false
    every { PrivacySettingsRepository.setBlockUnknownEnabled(any()) } just Runs
    every { PrivacySettingsRepository.isScreenSecurityForced() } answers { screenSecurityForced }
    every { PrivacySettingsRepository.canChangeMessagingSettings() } answers { canChangeMessagingSettings }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(PrivacySettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    readReceipts = true

    val state = createViewModel().state.value

    assertThat(state.readReceipts).isTrue()
    assertThat(state.universalExpireTimer).isEqualTo(1.days)
    assertThat(state.scrollToPaymentLock).isFalse()
    assertThat(state.isDeviceSecure).isTrue()
    assertThat(state.hasPhoneNumber).isTrue()
  }

  @Test
  fun `ScreenResumed loads the blocked count`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.blockedCount).isEqualTo(4)
  }

  @Test
  fun `ScreenResumed picks up settings changed while away`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    deviceSecure = false

    viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.isDeviceSecure).isFalse()
  }

  @Test
  fun `toggling a setting does not navigate`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))
    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(true))

    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `legacy wallet scroll requests remain inert`() = runTest(testDispatcher) {
    val viewModel = createViewModel(scrollToPaymentLock = true)
    val initialState = viewModel.state.value
    val actions = collectActions(viewModel.actions)
    assertThat(initialState.scrollToPaymentLock).isFalse()

    viewModel.onEvent(PrivacySettingsEvent.ScrolledToPaymentLock)

    assertThat(viewModel.state.value).isEqualTo(initialState)
    assertThat(actions).isEmpty()
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `ReadReceiptsToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))

    coVerify { PrivacySettingsRepository.setReadReceiptsEnabled(true) }
    assertThat(viewModel.state.value.readReceipts).isTrue()
  }

  @Test
  fun `TypingIndicatorsToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.TypingIndicatorsToggled(true))

    coVerify { PrivacySettingsRepository.setTypingIndicatorsEnabled(true) }
    assertThat(viewModel.state.value.typingIndicators).isTrue()
  }

  @Test
  fun `ScreenSecurityToggled stores the setting and applies it to the window`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(true))

    assertThat(viewModel.state.value.screenSecurity).isTrue()
    assertThat(actions).containsExactly(PrivacySettingsAction.ApplyScreenSecurity(true))
  }

  @Test
  fun `IncognitoKeyboardToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.IncognitoKeyboardToggled(true))

    assertThat(viewModel.state.value.incognitoKeyboard).isTrue()
  }

  @Test
  fun `retired wallet events do not access storage navigate or emit actions`() = runTest(testDispatcher) {
    val retiredEvents = listOf(
      PrivacySettingsEvent.EnablePaymentLockClicked,
      PrivacySettingsEvent.PaymentLockDisableAuthenticated,
      PrivacySettingsEvent.OpenDeviceSecuritySettingsClicked
    )

    for (secure in listOf(true, false)) {
      deviceSecure = secure
      val viewModel = createViewModel()
      val actions = collectActions(viewModel.actions)
      viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)
      val initialState = viewModel.state.value

      for (event in retiredEvents) {
        viewModel.onEvent(event)
        assertThat(viewModel.state.value).isEqualTo(initialState)
      }

      assertThat(actions).isEmpty()
      assertThat(navigationEvents).isEmpty()
    }

    verify(exactly = 0) { PrivacySettingsRepository.isPaymentLockEnabled() }
    verify(exactly = 0) { PrivacySettingsRepository.setPaymentLockEnabled(any()) }
  }

  @Test
  fun `DisabledScreenLockClicked shows a snackbar until it is dismissed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DisabledScreenLockClicked)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.ScreenLockNeedsDeviceSecurity)

    viewModel.onEvent(PrivacySettingsEvent.SnackbarDismissed)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
  }

  @Test
  fun `passphrase toggles request the cryptographic dialog without changing lock policy`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockToggled(false))

    assertThat(actions).containsExactly(PrivacySettingsAction.ChangePassphraseProtection(false))
    assertThat(viewModel.state.value.passphraseLock).isTrue()
    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockEnabled(any()) }
  }

  @Test
  fun `obsolete Signal disable event cannot bypass Molly cryptographic dialog`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DisablePassphraseConfirmed)

    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockEnabled(any()) }
    assertThat(viewModel.state.value.passphraseLock).isTrue()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `legacy disable click still requires the Molly cryptographic dialog`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()

    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(PrivacySettingsEvent.DisablePassphraseClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.ChangePassphraseProtection(false))
    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockEnabled(any()) }
  }

  @Test
  fun `ChangePassphraseClicked launches the change flow when a passphrase is set`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ChangePassphraseClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.LaunchChangePassphrase)
  }

  @Test
  fun `ChangePassphraseClicked tells the user when no passphrase is set`() = runTest(testDispatcher) {
    passphraseEnabled = false
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ChangePassphraseClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `DeviceSecuritySettingsUnavailable tells the user`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DeviceSecuritySettingsUnavailable)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.DeviceSecuritySettingsUnavailable)
  }

  @Test
  fun `trigger selection preserves Molly automatic lockdown modes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTriggersSelected(setOf("timeout", "screen_off")))

    assertThat(viewModel.state.value.passphraseLockTriggers).isEqualTo(setOf("timeout", "screen_off"))
  }

  @Test
  fun `Molly timeout selection preserves seconds without stock minute interpretation`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutSelected(7200L))

    verify { PrivacySettingsRepository.setPassphraseLockTimeout(7200L) }
    assertThat(viewModel.state.value.passphraseLockTimeoutSeconds).isEqualTo(7200L)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `requesting a Molly timeout picker does not change the stored timeout`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.ChoosePassphraseLockTimeout)
    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockTimeout(any()) }
  }

  @Test
  fun `obsolete Signal timeout events cannot reinterpret Molly timeout policy`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutToggled(true))
    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutSelected(2.hours))

    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockTimeout(any()) }
    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockTriggers(any()) }
  }

  @Test
  fun `DialogDismissed clears the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutIntervalClicked)
    viewModel.onEvent(PrivacySettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `row clicks navigate to their routes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.PhoneNumberClicked)
    viewModel.onEvent(PrivacySettingsEvent.BlockedClicked)
    viewModel.onEvent(PrivacySettingsEvent.DefaultTimerClicked)
    viewModel.onEvent(PrivacySettingsEvent.ScreenLockClicked)
    viewModel.onEvent(PrivacySettingsEvent.AdvancedClicked)

    assertThat(navigationEvents).containsExactly(
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.PhoneNumberPrivacy),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.BlockedUsers),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.ExpiringMessages),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.ScreenLock),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.Advanced)
    )
    assertThat(actions).isEmpty()
  }

  @Test
  fun `IncognitoKeyboardLearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.OpenIncognitoKeyboardLearnMore)
  }

  @Test
  fun `forced screenshot security cannot be disabled from settings`() = runTest(testDispatcher) {
    screenSecurityForced = true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(false))

    verify(exactly = 0) { PrivacySettingsRepository.setScreenSecurityEnabled(any()) }
    assertThat(viewModel.state.value.screenSecurity).isTrue()
    assertThat(actions).containsExactly(PrivacySettingsAction.ApplyScreenSecurity(true))
  }

  @Test
  fun `linked device cannot change primary-only messaging settings`() = runTest(testDispatcher) {
    canChangeMessagingSettings = false
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))
    viewModel.onEvent(PrivacySettingsEvent.TypingIndicatorsToggled(true))

    coVerify(exactly = 0) { PrivacySettingsRepository.setReadReceiptsEnabled(any()) }
    coVerify(exactly = 0) { PrivacySettingsRepository.setTypingIndicatorsEnabled(any()) }
  }

  private fun createViewModel(scrollToPaymentLock: Boolean = false): PrivacySettingsViewModel {
    return PrivacySettingsViewModel(
      parentEventEmitter = { navigationEvents += it },
      scrollToPaymentLock = scrollToPaymentLock
    )
  }

  private fun TestScope.collectActions(actions: Flow<PrivacySettingsAction>): List<PrivacySettingsAction> {
    val collected = mutableListOf<PrivacySettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
