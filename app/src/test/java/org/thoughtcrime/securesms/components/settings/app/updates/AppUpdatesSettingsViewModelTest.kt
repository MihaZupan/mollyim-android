/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.signal.appsettings.updates.AppUpdatesSettingsAction
import org.signal.appsettings.updates.AppUpdatesSettingsEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdatesSettingsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private var managed = true
  private var enabled = false
  private var beta = false
  private var lastChecked = 1_700_000_000_000L

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(AppUpdatesSettingsRepository)
    every { AppUpdatesSettingsRepository.isManagedUpdateChannel() } answers { managed }
    every { AppUpdatesSettingsRepository.getVersionName() } returns "Molly"
    every { AppUpdatesSettingsRepository.getLastSuccessfulCheck() } answers { lastChecked }
    every { AppUpdatesSettingsRepository.isUpdateApkEnabled() } answers { enabled }
    every { AppUpdatesSettingsRepository.isIncludeBetaEnabled() } answers { beta }
    every { AppUpdatesSettingsRepository.setUpdateApkEnabled(any()) } answers { enabled = firstArg() }
    every { AppUpdatesSettingsRepository.setIncludeBetaEnabled(any()) } answers { beta = firstArg() }
    every { AppUpdatesSettingsRepository.dismissInstallPrompt() } just Runs
    every { AppUpdatesSettingsRepository.checkForUpdates() } just Runs
  }

  @After
  fun teardown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `constructing and resuming reads original flags and timestamp without checking`() = runTest(testDispatcher) {
    enabled = true
    beta = true
    val viewModel = AppUpdatesSettingsViewModel()
    assertThat(viewModel.state.value.manageMollyUpdates).isTrue()
    assertThat(viewModel.state.value.autoUpdateEnabled).isTrue()
    assertThat(viewModel.state.value.includeBetaEnabled).isTrue()
    assertThat(viewModel.state.value.lastCheckedTime).isEqualTo(lastChecked.milliseconds)

    lastChecked = 0
    viewModel.onEvent(AppUpdatesSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.lastCheckedTime).isEqualTo(Duration.ZERO)
    verify(exactly = 0) { AppUpdatesSettingsRepository.checkForUpdates() }
  }

  @Test
  fun `store channel ignores stale events and preserves private preferences`() = runTest(testDispatcher) {
    managed = false
    enabled = true
    val viewModel = AppUpdatesSettingsViewModel()

    viewModel.onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(false))
    viewModel.onEvent(AppUpdatesSettingsEvent.IncludeBetaToggled(true))
    viewModel.onEvent(AppUpdatesSettingsEvent.CheckForUpdatesClicked)

    assertThat(viewModel.state.value.manageMollyUpdates).isFalse()
    assertThat(enabled).isTrue()
    assertThat(beta).isFalse()
    verify(exactly = 0) { AppUpdatesSettingsRepository.setUpdateApkEnabled(any()) }
    verify(exactly = 0) { AppUpdatesSettingsRepository.setIncludeBetaEnabled(any()) }
    verify(exactly = 0) { AppUpdatesSettingsRepository.dismissInstallPrompt() }
    verify(exactly = 0) { AppUpdatesSettingsRepository.checkForUpdates() }
  }

  @Test
  fun `explicit managed enable checks but disabling only saves the original flag`() = runTest(testDispatcher) {
    val viewModel = AppUpdatesSettingsViewModel()

    viewModel.onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(true))
    assertThat(viewModel.state.value.autoUpdateEnabled).isTrue()
    viewModel.onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(false))

    assertThat(viewModel.state.value.autoUpdateEnabled).isFalse()
    verify(exactly = 1) { AppUpdatesSettingsRepository.checkForUpdates() }
  }

  @Test
  fun `beta selection preserves Molly dismissal and explicit check even when periodic checks are off`() = runTest(testDispatcher) {
    val viewModel = AppUpdatesSettingsViewModel()

    viewModel.onEvent(AppUpdatesSettingsEvent.IncludeBetaToggled(true))

    assertThat(viewModel.state.value.includeBetaEnabled).isTrue()
    assertThat(enabled).isFalse()
    verify { AppUpdatesSettingsRepository.dismissInstallPrompt() }
    verify(exactly = 1) { AppUpdatesSettingsRepository.checkForUpdates() }
  }

  @Test
  fun `manual check requires managed channel and the original enable preference`() = runTest(testDispatcher) {
    val viewModel = AppUpdatesSettingsViewModel()
    viewModel.onEvent(AppUpdatesSettingsEvent.CheckForUpdatesClicked)
    verify(exactly = 0) { AppUpdatesSettingsRepository.checkForUpdates() }

    enabled = true
    viewModel.onEvent(AppUpdatesSettingsEvent.CheckForUpdatesClicked)
    verify(exactly = 1) { AppUpdatesSettingsRepository.checkForUpdates() }
  }

  @Test
  fun `navigation stays event driven`() = runTest(testDispatcher) {
    val viewModel = AppUpdatesSettingsViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(AppUpdatesSettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(AppUpdatesSettingsAction.NavigateBack)
  }

  private fun TestScope.collectActions(viewModel: AppUpdatesSettingsViewModel): List<AppUpdatesSettingsAction> {
    val actions = mutableListOf<AppUpdatesSettingsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }
    return actions
  }
}
