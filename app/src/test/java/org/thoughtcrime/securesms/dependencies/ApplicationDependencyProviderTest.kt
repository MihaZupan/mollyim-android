package org.thoughtcrime.securesms.dependencies

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.AppForegroundObserver
import org.signal.libsignal.net.Network
import org.signal.network.config.SignalServiceConfiguration
import org.thoughtcrime.securesms.net.SignalWebSocketHealthMonitor
import org.thoughtcrime.securesms.net.Networking
import java.io.IOException
import java.util.function.Supplier

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class ApplicationDependencyProviderTest {
  private var networkingWasEnabled = false

  @Before
  fun disableNetworking() {
    networkingWasEnabled = Networking.isEnabled
    Networking.isEnabled = false
  }

  @After
  fun restoreNetworking() {
    Networking.isEnabled = networkingWasEnabled
  }

  @Test
  fun `foreground websocket construction does not enter network dependency graph`() {
    mockkStatic(AppForegroundObserver::class)
    mockkConstructor(SignalWebSocketHealthMonitor::class)
    try {
      every { AppForegroundObserver.isForegrounded() } returns true
      justRun { anyConstructed<SignalWebSocketHealthMonitor>().monitor(any()) }
      val provider = ApplicationDependencyProvider(ApplicationProvider.getApplicationContext())
      val configuration = mockk<Supplier<SignalServiceConfiguration>>()
      val network = mockk<Supplier<Network>>()

      provider.provideAuthWebSocket(configuration, network)
      provider.provideUnauthWebSocket(configuration, network)

      verify(exactly = 0) { configuration.get() }
      verify(exactly = 0) { network.get() }
    } finally {
      unmockkConstructor(SignalWebSocketHealthMonitor::class)
      unmockkStatic(AppForegroundObserver::class)
    }
  }

  @Test
  fun `provided HTTP client rejects socket creation while locked`() {
    val client = ApplicationDependencyProvider(mockk<Application>()).provideOkHttpClient()

    assertThrows(IOException::class.java) {
      client.socketFactory.createSocket().close()
    }
  }

  @Test
  fun `provided HTTP client rejects DNS lookup while locked`() {
    val client = ApplicationDependencyProvider(mockk<Application>()).provideOkHttpClient()

    assertThrows(IOException::class.java) {
      client.dns.lookup("localhost")
    }
  }
}
