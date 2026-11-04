/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenLockSettingsScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()
  private val events = mutableListOf<ScreenLockSettingsEvent>()

  @Test
  fun `enable click requests host enrollment instead of claiming authentication succeeded`() {
    setContent(false)
    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK).performClick()
    assertThat(events).containsExactly(ScreenLockSettingsEvent.ScreenLockToggleRequested(true))
  }

  @Test
  fun `disable click requests the existing Molly disable policy`() {
    setContent(true)
    composeTestRule.onNodeWithTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK).performClick()
    assertThat(events).containsExactly(ScreenLockSettingsEvent.ScreenLockToggleRequested(false))
  }

  @Test
  fun `stock timeout controls are not offered for the fixed Molly controller policy`() {
    setContent(true)
    composeTestRule.onNodeWithTag("row-custom-timeout").assertDoesNotExist()
    composeTestRule.onNodeWithTag("row-timeout-300").assertDoesNotExist()
    assertThat(events).isEmpty()
  }

  @Test
  fun `navigation stays event driven`() {
    setContent(false)
    composeTestRule.onNodeWithContentDescription("Close").performClick()
    assertThat(events).containsExactly(ScreenLockSettingsEvent.NavigateBackClicked)
  }

  private fun setContent(enabled: Boolean) {
    composeTestRule.setContent {
      ScreenLockSettingsScreen(state = ScreenLockSettingsState(enabled), onEvent = { events += it })
    }
  }
}
