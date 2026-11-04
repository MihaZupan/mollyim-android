/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpSettingsViewModelTest {

  companion object {
    private const val VERSION_NAME = "8.33.0"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state contains the version name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(HelpSettingsState(versionName = VERSION_NAME))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = assertEventEmits(HelpSettingsEvent.NavigateBackClicked, HelpSettingsAction.NavigateBack)

  @Test
  fun `SupportCenterClicked opens the support center`() = assertEventEmits(HelpSettingsEvent.SupportCenterClicked, HelpSettingsAction.OpenSupportCenter)

  @Test
  fun `Molly website and version clicks expose their existing destinations`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(HelpSettingsEvent.WebsiteClicked)
    viewModel.onEvent(HelpSettingsEvent.VersionClicked)

    assertThat(actions).containsExactly(HelpSettingsAction.OpenWebsite, HelpSettingsAction.NavigateToAppUpdates)
  }

  @Test
  fun `ContactUsClicked navigates to contact us`() = assertEventEmits(HelpSettingsEvent.ContactUsClicked, HelpSettingsAction.NavigateToContactUs)

  @Test
  fun `VersionLongClicked copies the version name`() = assertEventEmits(HelpSettingsEvent.VersionLongClicked, HelpSettingsAction.CopyVersionToClipboard(VERSION_NAME))

  @Test
  fun `DebugLogClicked navigates to the debug log`() = assertEventEmits(HelpSettingsEvent.DebugLogClicked, HelpSettingsAction.NavigateToDebugLog)

  @Test
  fun `LicensesClicked navigates to licenses`() = assertEventEmits(HelpSettingsEvent.LicensesClicked, HelpSettingsAction.NavigateToLicenses)

  @Test
  fun `TermsAndPrivacyPolicyClicked opens the terms and privacy policy`() = assertEventEmits(HelpSettingsEvent.TermsAndPrivacyPolicyClicked, HelpSettingsAction.OpenTermsAndPrivacyPolicy)

  @Test
  fun `disable and wipe callback requires confirmation and cancellation changes nothing`() = runTest(testDispatcher) {
    var enabled = true
    val writes = mutableListOf<Boolean>()
    val viewModel = HelpSettingsViewModel(
      VERSION_NAME,
      readLogEnabled = { enabled },
      writeLogEnabled = { value -> writes += value; enabled = value }
    )
    val actions = collectActions(viewModel.actions)
    assertThat(writes).isEmpty()

    viewModel.onEvent(HelpSettingsEvent.LogEnabledChanged(false))
    assertThat(viewModel.state.value.confirmDisableLogging).isTrue()
    assertThat(writes).isEmpty()
    viewModel.onEvent(HelpSettingsEvent.DialogDismissed)
    viewModel.onEvent(HelpSettingsEvent.DisableLoggingConfirmed)
    assertThat(writes).isEmpty()

    viewModel.onEvent(HelpSettingsEvent.LogEnabledChanged(false))
    viewModel.onEvent(HelpSettingsEvent.DisableLoggingConfirmed)
    viewModel.onEvent(HelpSettingsEvent.DebugLogClicked)
    assertThat(writes).containsExactly(false)
    assertThat(viewModel.state.value.logEnabled).isFalse()
    assertThat(viewModel.state.value.confirmDisableLogging).isFalse()
    assertThat(actions).isEmpty()
  }

  @Test
  fun `refreshing consent never enables logging and stale debug clicks are rejected`() = runTest(testDispatcher) {
    var enabled = true
    val writes = mutableListOf<Boolean>()
    val viewModel = HelpSettingsViewModel(VERSION_NAME, readLogEnabled = { enabled }, writeLogEnabled = { writes += it })
    val actions = collectActions(viewModel.actions)
    enabled = false

    viewModel.onEvent(HelpSettingsEvent.ScreenResumed)
    viewModel.onEvent(HelpSettingsEvent.DebugLogClicked)

    assertThat(viewModel.state.value.logEnabled).isFalse()
    assertThat(writes).isEmpty()
    assertThat(actions).isEmpty()
  }

  @Test
  fun `explicit enable persists the choice without opening or submitting a log`() = runTest(testDispatcher) {
    var enabled = false
    val writes = mutableListOf<Boolean>()
    val viewModel = HelpSettingsViewModel(VERSION_NAME, readLogEnabled = { enabled }, writeLogEnabled = { writes += it; enabled = it })
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(HelpSettingsEvent.LogEnabledChanged(true))

    assertThat(writes).containsExactly(true)
    assertThat(viewModel.state.value.logEnabled).isTrue()
    assertThat(actions).isEmpty()
  }

  private fun assertEventEmits(event: HelpSettingsEvent, expected: HelpSettingsAction) = runTest(testDispatcher) {
    val viewModel = createViewModel(logEnabled = event == HelpSettingsEvent.DebugLogClicked)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(event)

    assertThat(actions).containsExactly(expected)
  }

  private fun createViewModel(logEnabled: Boolean = false): HelpSettingsViewModel =
    HelpSettingsViewModel(versionName = VERSION_NAME, readLogEnabled = { logEnabled })

  private fun TestScope.collectActions(actions: Flow<HelpSettingsAction>): List<HelpSettingsAction> {
    val collected = mutableListOf<HelpSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
