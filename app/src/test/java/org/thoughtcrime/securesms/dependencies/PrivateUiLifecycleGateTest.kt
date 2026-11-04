package org.thoughtcrime.securesms.dependencies

import android.app.Application
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.service.UnreadReminderManager

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [27])
class PrivateUiLifecycleGateTest {
  @Before
  fun setup() {
    mockkStatic(AppDependencies::class)
    every { AppDependencies.isInitialized } returns true
    assertTrue(KeyCachingService.isLocked())
  }

  @After
  fun teardown() {
    unmockkStatic(AppDependencies::class)
  }

  @Test
  fun `private UI remains protected after the cached key is absent`() {
    assertTrue(CoreUiDependenciesProvider.provideIsIncognitoKeyboardEnabled())
    assertTrue(CoreUiDependenciesProvider.provideIsScreenSecurityEnabled())
  }

  @Test
  fun `locked unread reminder alarms do not reach private dependencies`() {
    UnreadReminderManager.UnreadReminderAlarm().onReceive(mockk<Context>(), mockk<Intent>())
  }
}
