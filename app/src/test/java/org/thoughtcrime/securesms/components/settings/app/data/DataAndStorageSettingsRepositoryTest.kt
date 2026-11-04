package org.thoughtcrime.securesms.components.settings.app.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SettingsValues.NotificationDeliveryMethod
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.UnifiedPushValues

class DataAndStorageSettingsRepositoryTest {
  private var method = NotificationDeliveryMethod.FCM
  private var accountPushAvailable = true
  private var fcmAvailable = false
  private var unifiedPushAvailable = false

  @Before
  fun setup() {
    mockkObject(SignalStore)
    val account = mockk<AccountValues>()
    val settings = mockk<SettingsValues>()
    val unifiedPush = mockk<UnifiedPushValues>()
    every { SignalStore.account } returns account
    every { SignalStore.settings } returns settings
    every { SignalStore.unifiedpush } returns unifiedPush
    every { account.pushAvailable } answers { accountPushAvailable }
    every { account.canReceiveFcm } answers { fcmAvailable }
    every { settings.preferredNotificationMethod } answers { this@DataAndStorageSettingsRepositoryTest.method }
    every { unifiedPush.isAvailableOrAirGapped } answers { unifiedPushAvailable }
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun `registered UnifiedPush is effective without FCM or a Play Services installation check`() {
    method = NotificationDeliveryMethod.UNIFIEDPUSH
    unifiedPushAvailable = true
    assertTrue(DataAndStorageSettingsRepository.isPushAvailable())
  }

  @Test
  fun `FCM selection requires the actual FCM capability rather than a different available method`() {
    unifiedPushAvailable = true
    assertFalse(DataAndStorageSettingsRepository.isPushAvailable())
    fcmAvailable = true
    assertTrue(DataAndStorageSettingsRepository.isPushAvailable())
  }

  @Test
  fun `websocket selection and pending UnifiedPush ignore stale FCM availability`() {
    fcmAvailable = true
    method = NotificationDeliveryMethod.WEBSOCKET
    assertFalse(DataAndStorageSettingsRepository.isPushAvailable())
    method = NotificationDeliveryMethod.UNIFIEDPUSH
    assertFalse(DataAndStorageSettingsRepository.isPushAvailable())
  }

  @Test
  fun `account push readiness remains required`() {
    fcmAvailable = true
    accountPushAvailable = false
    assertFalse(DataAndStorageSettingsRepository.isPushAvailable())
  }
}
