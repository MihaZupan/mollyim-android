/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLockSettingsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private var screenLockEnabled = false

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(ScreenLockSettingsRepository)
    every { ScreenLockSettingsRepository.isScreenLockEnabled() } answers { screenLockEnabled }
    every { ScreenLockSettingsRepository.setScreenLockEnabled(any()) } answers { screenLockEnabled = firstArg() }
  }

  @After
  fun teardown() {
    Dispatchers.resetMain()
    unmockkObject(ScreenLockSettingsRepository)
  }

  @Test
  fun `enable request requires Molly authentication without changing the setting`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(ScreenLockSettingsEvent.ScreenLockToggleRequested(true))

    verify(exactly = 0) { ScreenLockSettingsRepository.setScreenLockEnabled(any()) }
    assertThat(viewModel.state.value.isScreenLockEnabled).isFalse()
    assertThat(actions).containsExactly(ScreenLockSettingsAction.EnableBiometricScreenLock)
  }

  @Test
  fun `successful biometric callback stores the setting and reapplies screenshot security`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(ScreenLockSettingsEvent.ScreenLockToggled(true))

    verify { ScreenLockSettingsRepository.setScreenLockEnabled(true) }
    assertThat(viewModel.state.value.isScreenLockEnabled).isTrue()
    assertThat(actions).containsExactly(ScreenLockSettingsAction.ApplyScreenSecurity)
  }

  @Test
  fun `disabling keeps Molly policy without requiring an extra authentication`() = runTest(testDispatcher) {
    screenLockEnabled = true
    val viewModel = ScreenLockSettingsViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(ScreenLockSettingsEvent.ScreenLockToggleRequested(false))

    assertThat(viewModel.state.value.isScreenLockEnabled).isFalse()
    assertThat(actions).containsExactly(ScreenLockSettingsAction.ApplyScreenSecurity)
  }

  @Test
  fun `resuming refreshes the actual biometric preference`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()
    screenLockEnabled = true

    viewModel.onEvent(ScreenLockSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.isScreenLockEnabled).isTrue()
  }

  @Test
  fun `back navigates without changing lock policy`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(ScreenLockSettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(ScreenLockSettingsAction.NavigateBack)
    verify(exactly = 0) { ScreenLockSettingsRepository.setScreenLockEnabled(any()) }
  }

  private fun TestScope.collectActions(viewModel: ScreenLockSettingsViewModel): List<ScreenLockSettingsAction> {
    val actions = mutableListOf<ScreenLockSettingsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }
    return actions
  }
}
