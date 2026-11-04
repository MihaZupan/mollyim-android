package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.core.util.eventbus.EventBus
import org.signal.devicetransfer.TransferStatus
import org.thoughtcrime.securesms.crypto.MasterSecret
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.migrations.ApplicationMigrations

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [27])
class KeyCachingServiceTest {

  private lateinit var service: KeyCachingService

  @Before
  fun setUp() {
    KeyCachingService.clearMasterSecret()
    EventBus.default.removeStickyEvent(TransferStatus::class.java)
    mockkStatic(ApplicationMigrations::class)
    every { ApplicationMigrations.isUpdate(any()) } returns false

    // Only represent cached state; do not generate or load any key material.
    KeyCachingService.setMasterSecret(mockk<MasterSecret>(relaxed = true))
    service = Robolectric.buildService(KeyCachingService::class.java).get()
  }

  @After
  fun tearDown() {
    EventBus.default.removeStickyEvent(TransferStatus::class.java)
    KeyCachingService.clearMasterSecret()
    unmockkStatic(ApplicationMigrations::class)
  }

  @Test
  fun `replayed cache request while locked does not access encrypted storage`() {
    KeyCachingService.clearMasterSecret()
    SignalStore.testInject(null)

    assertEquals(KeyCachingService.START_NOT_STICKY, service.onStartCommand(Intent(service, KeyCachingService::class.java), 0, 1))

    assertTrue(KeyCachingService.isLocked())
    assertTrue(shadowOf(service).isStoppedBySelf)
  }

  @Test
  fun `explicit lock is excluded during device transfer`() {
    assertTransferPreventsLock(KeyCachingService.CLEAR_KEY_ACTION)
  }

  @Test
  fun `passphrase expiration is excluded during device transfer`() {
    val action = KeyCachingService::class.java.getDeclaredField("PASSPHRASE_EXPIRED_EVENT").apply {
      isAccessible = true
    }.get(null) as String

    assertTransferPreventsLock(action)
  }

  private fun assertTransferPreventsLock(action: String) {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val clearEventsBefore = shadowOf(application).broadcastIntents.count { it.action == KeyCachingService.CLEAR_KEY_EVENT }
    assertFalse(KeyCachingService.isLocked())
    EventBus.default.postSticky(TransferStatus.ready())

    service.onStartCommand(Intent(action), 0, 1)

    assertFalse(KeyCachingService.isLocked())
    assertEquals(clearEventsBefore, shadowOf(application).broadcastIntents.count { it.action == KeyCachingService.CLEAR_KEY_EVENT })

    EventBus.default.removeStickyEvent(TransferStatus::class.java)
    service.onStartCommand(Intent(KeyCachingService.CLEAR_KEY_ACTION), 0, 2)

    assertTrue(KeyCachingService.isLocked())
    val clearEvents = shadowOf(application).broadcastIntents.filter { it.action == KeyCachingService.CLEAR_KEY_EVENT }
    assertEquals(clearEventsBefore + 1, clearEvents.size)
    assertFalse(clearEvents.last().getBooleanExtra(KeyCachingService.EXTRA_KEY_EXPIRED, true))
  }
}
