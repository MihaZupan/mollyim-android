/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.updates

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppUpdatesSettingsScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()
  private val events = mutableListOf<AppUpdatesSettingsEvent>()

  @Test
  fun `managed Molly channel shows its real controls`() {
    setContent(managedState())
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_AUTO_UPDATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_INCLUDE_BETA).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_STORE_MANAGED).assertDoesNotExist()
  }

  @Test
  fun `store channel shows version and guidance without update controls`() {
    setContent(managedState().copy(manageMollyUpdates = false))
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_VERSION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_STORE_MANAGED).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_AUTO_UPDATE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_INCLUDE_BETA).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_CHECK_FOR_UPDATES).assertDoesNotExist()
  }

  @Test
  fun `managed switches preserve original enable and beta events`() {
    setContent(managedState())
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_AUTO_UPDATE).performClick()
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_INCLUDE_BETA).performClick()
    assertThat(events).contains(AppUpdatesSettingsEvent.AutoUpdateToggled(false))
    assertThat(events).contains(AppUpdatesSettingsEvent.IncludeBetaToggled(true))
  }

  @Test
  fun `manual check is disabled when periodic checking is off`() {
    setContent(managedState().copy(autoUpdateEnabled = false))
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_CHECK_FOR_UPDATES).assertIsNotEnabled()
  }

  @Test
  fun `enabled manual check emits only the manual request`() {
    setContent(managedState())
    composeTestRule.onNodeWithTag(AppUpdatesSettingsTestTags.ROW_CHECK_FOR_UPDATES).performClick()
    assertThat(events).containsExactly(AppUpdatesSettingsEvent.ScreenResumed, AppUpdatesSettingsEvent.CheckForUpdatesClicked)
  }

  @Test
  fun `host localized timestamps and accessible descriptions are not replaced with Signal formatting`() {
    setContent(managedState())
    composeTestRule.onNodeWithText("Localized last check").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Accessible last check").assertExists()
  }

  @Test
  fun `showing the screen only requests state refresh`() {
    setContent(managedState())
    assertThat(events).containsExactly(AppUpdatesSettingsEvent.ScreenResumed)
  }

  private fun managedState() = AppUpdatesSettingsState(versionName = "Molly", autoUpdateEnabled = true, manageMollyUpdates = true)

  private fun setContent(state: AppUpdatesSettingsState) {
    composeTestRule.setContent {
      AppUpdatesSettingsScreen(
        state = state,
        onEvent = { events += it },
        labels = AppUpdatesSettingsLabels(
          version = "Version",
          storeManagedMessage = "Use your app store",
          autoUpdates = "Molly update checks",
          autoUpdatesDescription = "Check releases and ask before installation",
          includeBeta = "Include beta updates",
          includeBetaDescription = "Beta releases may contain bugs",
          checkForUpdates = "Check for updates",
          lastChecked = "Localized last check",
          lastCheckedContentDescription = "Accessible last check"
        )
      )
    }
  }
}
