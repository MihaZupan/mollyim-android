package org.thoughtcrime.securesms.notifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.ServiceUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class NotificationChannelsTest {

  @Test
  fun `service channels can be created before unlock without encrypted storage`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    SignalStore.testInject(null)

    NotificationChannels.ensureServiceChannels(application)

    val manager = ServiceUtil.getNotificationManager(application)
    for (id in listOf(NotificationChannels.LOCKED_STATUS, NotificationChannels.BACKGROUND)) {
      val channel = manager.getNotificationChannel(id)
      assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
      assertFalse(channel.canShowBadge())
    }
  }

  @Test
  fun `creating service channels preserves existing user settings`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val manager = ServiceUtil.getNotificationManager(application)
    val channel = NotificationChannel(NotificationChannels.BACKGROUND, "Custom background", NotificationManager.IMPORTANCE_NONE)
    channel.setSound(null, null)
    manager.createNotificationChannel(channel)

    NotificationChannels.ensureServiceChannels(application)
    NotificationChannels.ensureServiceChannels(application)

    val existing = manager.getNotificationChannel(NotificationChannels.BACKGROUND)
    assertEquals(NotificationManager.IMPORTANCE_NONE, existing.importance)
    assertEquals(null, existing.sound)
  }
}
