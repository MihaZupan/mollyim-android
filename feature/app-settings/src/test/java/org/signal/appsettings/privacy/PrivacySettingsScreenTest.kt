/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PrivacySettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<PrivacySettingsEvent>()

  /** Everything but the [PrivacySettingsEvent.ScreenResumed] the screen sends as soon as it's shown. */
  private val interactionEvents: List<PrivacySettingsEvent>
    get() = events.filterNot { it == PrivacySettingsEvent.ScreenResumed }

  @Test
  fun `resuming the screen emits ScreenResumed and nothing else`() {
    setContent(PrivacySettingsState())

    assertThat(events).containsExactly(PrivacySettingsEvent.ScreenResumed)
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(PrivacySettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `phone number row is shown only with a phone number`() {
    setContent(PrivacySettingsState(hasPhoneNumber = false))

    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PHONE_NUMBER).assertDoesNotExist()
  }

  @Test
  fun `clicking the phone number row emits PhoneNumberClicked`() {
    setContent(PrivacySettingsState(hasPhoneNumber = true))

    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PHONE_NUMBER).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.PhoneNumberClicked)
  }

  @Test
  fun `the blocked row shows the blocked count`() {
    setContent(PrivacySettingsState(blockedCount = 3))

    composeTestRule.onNodeWithText("3 contacts or groups").assertIsDisplayed()
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_BLOCKED).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.BlockedClicked)
  }

  @Test
  fun `toggling read receipts emits ReadReceiptsToggled`() {
    setContent(PrivacySettingsState(readReceipts = true))

    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_READ_RECEIPTS).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.ReadReceiptsToggled(false))
  }

  @Test
  fun `toggling typing indicators emits TypingIndicatorsToggled`() {
    setContent(PrivacySettingsState(typingIndicators = false))

    scrollTo(PrivacySettingsTestTags.ROW_TYPING_INDICATORS)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_TYPING_INDICATORS).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.TypingIndicatorsToggled(true))
  }

  @Test
  fun `clicking the default timer row emits DefaultTimerClicked`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.ROW_DEFAULT_TIMER)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_DEFAULT_TIMER).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.DefaultTimerClicked)
  }

  @Test
  fun `clicking screen lock on a secure device emits ScreenLockClicked`() {
    setContent(PrivacySettingsState(isDeviceSecure = true))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_LOCK)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_LOCK).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.ScreenLockClicked)
  }

  @Test
  fun `clicking screen lock on an insecure device emits DisabledScreenLockClicked`() {
    setContent(PrivacySettingsState(isDeviceSecure = false))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_LOCK)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_LOCK).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.DisabledScreenLockClicked)
  }

  @Test
  fun `screen lock summary shows the timeout when screen lock is on`() {
    setContent(PrivacySettingsState(screenLock = true, screenLockTimeout = 5.minutes))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_LOCK)
    composeTestRule.onNodeWithText("5 minutes").assertIsDisplayed()
  }

  @Test
  fun `screen lock summary shows off when the device is insecure`() {
    setContent(PrivacySettingsState(isDeviceSecure = false, screenLock = true))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_LOCK)
    composeTestRule.onNodeWithText("Off").assertIsDisplayed()
  }

  @Test
  fun `passphrase rows replace screen lock while the legacy passphrase is enabled`() {
    setContent(PrivacySettingsState(isObsoletePassphraseEnabled = true))

    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_LOCK).assertDoesNotExist()

    scrollTo(PrivacySettingsTestTags.ROW_ENABLE_PASSPHRASE)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_ENABLE_PASSPHRASE).performClick()
    scrollTo(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE).performClick()
    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_TIMEOUT)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PASSPHRASE_TIMEOUT).performClick()
    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_TIMEOUT_INTERVAL)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PASSPHRASE_TIMEOUT_INTERVAL).performClick()

    assertThat(interactionEvents).containsExactly(
      PrivacySettingsEvent.DisablePassphraseClicked,
      PrivacySettingsEvent.ChangePassphraseClicked,
      PrivacySettingsEvent.PassphraseTimeoutToggled(true),
      PrivacySettingsEvent.PassphraseTimeoutIntervalClicked
    )
  }

  @Test
  fun `toggling screen security emits ScreenSecurityToggled`() {
    setContent(PrivacySettingsState(screenSecurity = false))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_SECURITY)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_SECURITY).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.ScreenSecurityToggled(true))
  }

  @Test
  fun `toggling incognito keyboard emits IncognitoKeyboardToggled`() {
    setContent(PrivacySettingsState(incognitoKeyboard = true))

    scrollTo(PrivacySettingsTestTags.ROW_INCOGNITO_KEYBOARD)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_INCOGNITO_KEYBOARD).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.IncognitoKeyboardToggled(false))
  }

  @Test
  fun `stale wallet lock flags do not add a control or emit events`() {
    setContent(PrivacySettingsState(paymentLock = true, scrollToPaymentLock = true))

    scrollTo(PrivacySettingsTestTags.ROW_ADVANCED)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PAYMENT_LOCK).assertDoesNotExist()

    assertThat(interactionEvents).isEmpty()
  }

  @Test
  fun `clicking the advanced row emits AdvancedClicked`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.ROW_ADVANCED)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_ADVANCED).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.AdvancedClicked)
  }

  @Test
  fun `confirming the disable passphrase dialog emits DisablePassphraseConfirmed`() {
    setContent(PrivacySettingsState(isObsoletePassphraseEnabled = true, dialog = PrivacySettingsState.Dialog.ConfirmDisablePassphrase))

    composeTestRule.onNodeWithText("Disable").performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.DisablePassphraseConfirmed)
  }

  @Test
  fun `a stale wallet dialog is absent and leaves screen lock usable`() {
    setContent(PrivacySettingsState(dialog = PrivacySettingsState.Dialog.PaymentLockNeedsDeviceSecurity))

    composeTestRule.onNodeWithText("Turn On").assertDoesNotExist()
    composeTestRule.onNodeWithText("Not Now").assertDoesNotExist()

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_LOCK)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_LOCK).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.ScreenLockClicked)
  }

  @Test
  fun `the default timer row shows the abbreviated timer`() {
    setContent(PrivacySettingsState(universalExpireTimer = 1.days))

    scrollTo(PrivacySettingsTestTags.ROW_DEFAULT_TIMER)
    composeTestRule.onNodeWithText("1d").assertIsDisplayed()
  }

  @Test
  fun `clicking learn more under incognito keyboard emits IncognitoKeyboardLearnMoreClicked`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.TEXT_INCOGNITO_KEYBOARD_DISCLAIMER)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.TEXT_INCOGNITO_KEYBOARD_DISCLAIMER).performFirstLinkClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked)
  }

  @Test
  fun `cancelling the disable passphrase dialog emits DialogDismissed`() {
    setContent(PrivacySettingsState(isObsoletePassphraseEnabled = true, dialog = PrivacySettingsState.Dialog.ConfirmDisablePassphrase))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.DialogDismissed)
  }

  @Test
  fun `confirming the passphrase timeout dialog emits PassphraseTimeoutSelected`() {
    setContent(PrivacySettingsState(isObsoletePassphraseEnabled = true, obsoletePassphraseTimeout = 30.minutes, dialog = PrivacySettingsState.Dialog.PassphraseTimeout))

    composeTestRule.onNodeWithText("Set").performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.PassphraseTimeoutSelected(30.minutes))
  }

  @Test
  fun `a snackbar in state is shown and then reported as dismissed`() {
    setContent(PrivacySettingsState(snackbar = PrivacySettingsState.Snackbar.ScreenLockNeedsDeviceSecurity))

    composeTestRule.onNodeWithText("To use screen lock set a PIN, pattern, or password on this device.").assertIsDisplayed()
    composeTestRule.waitUntil(timeoutMillis = 10_000) { interactionEvents.isNotEmpty() }

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.SnackbarDismissed)
  }

  @Test
  fun `the passphrase not set snackbar explains there is no passphrase`() {
    setContent(PrivacySettingsState(snackbar = PrivacySettingsState.Snackbar.PassphraseNotSet))

    composeTestRule.onNodeWithText("You haven't set a passphrase yet!").assertIsDisplayed()
  }

  @Test
  fun `the settings unavailable snackbar explains the failure`() {
    setContent(PrivacySettingsState(snackbar = PrivacySettingsState.Snackbar.DeviceSecuritySettingsUnavailable))

    composeTestRule.onNodeWithText("Failed to navigate to system settings").assertIsDisplayed()
  }

  private fun setContent(state: PrivacySettingsState) {
    composeTestRule.setContent {
      // The biometrics helper needs a FragmentActivity to show its prompt. Inspection mode makes it pass straight through.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        PrivacySettingsScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
