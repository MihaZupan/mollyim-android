package org.thoughtcrime.securesms.notifications

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.crypto.MasterSecret
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobs.RestoreNotificationsJob
import org.thoughtcrime.securesms.service.KeyCachingService

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [27])
class RestoreNotificationsReceiverTest {

  private lateinit var jobManager: JobManager

  @Before
  fun setUp() {
    KeyCachingService.clearMasterSecret()
    mockkStatic(AppDependencies::class)
    jobManager = mockk(relaxed = true)
    every { AppDependencies.jobManager } returns jobManager
  }

  @After
  fun tearDown() {
    KeyCachingService.clearMasterSecret()
    unmockkStatic(AppDependencies::class)
  }

  @Test
  fun `locked boot and update do not access private job dependencies`() {
    assertTrue(KeyCachingService.isLocked())

    receiveBootAndUpdate()

    verify(exactly = 0) { AppDependencies.jobManager }
    verify(exactly = 0) { jobManager.add(any()) }
  }

  @Test
  fun `unlocked boot and update still enqueue notification restoration`() {
    // Only represent cached state; do not generate or load any key material.
    KeyCachingService.setMasterSecret(mockk<MasterSecret>(relaxed = true))
    assertFalse(KeyCachingService.isLocked())

    receiveBootAndUpdate()

    verify(exactly = 2) { jobManager.add(ofType<RestoreNotificationsJob>()) }
  }

  private fun receiveBootAndUpdate() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val receiver = RestoreNotificationsReceiver()
    receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
  }
}
