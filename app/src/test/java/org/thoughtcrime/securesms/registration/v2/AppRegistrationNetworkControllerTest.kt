package org.thoughtcrime.securesms.registration.v2

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.net.NetworkManager
import org.thoughtcrime.securesms.net.Networking
import org.thoughtcrime.securesms.net.SocksProxy
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [27])
class AppRegistrationNetworkControllerTest {

  private val context: Application = ApplicationProvider.getApplicationContext()
  private val api = mockk<RegistrationApiV2>()
  private val networkManager = mockk<NetworkManager>()
  private val proxy = SocksProxy("localhost", 9050)
  private var previousEnabled = false
  private var previousProxy: SocksProxy? = null
  private var seenNetworkConfig = false
  private lateinit var controller: AppRegistrationNetworkController

  @Before
  fun setUp() {
    previousEnabled = Networking.isEnabled
    previousProxy = Networking.socksProxy
    Networking.isEnabled = false
    Networking.socksProxy = proxy
    mockkStatic(AppDependencies::class, TextSecurePreferences::class)
    every { AppDependencies.networkManager } returns networkManager
    every { TextSecurePreferences.setHasSeenNetworkConfig(context, true) } answers {
      seenNetworkConfig = true
    }
    every { networkManager.setNetworkEnabled(true) } answers {
      assertTrue(seenNetworkConfig)
      Networking.isEnabled = true
    }
    controller = AppRegistrationNetworkController(context, api)
  }

  @After
  fun tearDown() {
    unmockkAll()
    Networking.isEnabled = previousEnabled
    Networking.socksProxy = previousProxy
  }

  @Test
  fun `constructing registration dependencies does not enable networking`() {
    assertFalse(Networking.isEnabled)
    assertFalse(seenNetworkConfig)
    verify(exactly = 0) { networkManager.setNetworkEnabled(any()) }
  }

  @Test
  fun `creating the first session enables networking before the request`() = runBlocking {
    val response = RequestResult.Success(mockk<SessionMetadata>())
    coEvery { api.createVerificationSession("+15555550101", null, null, null) } answers {
      assertNetworkEnabled()
      response
    }

    assertSame(response, controller.createSession("+15555550101", null, null, null))
  }

  @Test
  fun `resuming a session enables networking before the request`() = runBlocking {
    val response = RequestResult.Success(mockk<SessionMetadata>())
    coEvery { api.getSessionStatus("session") } answers {
      assertNetworkEnabled()
      response
    }

    assertSame(response, controller.getSession("session"))
  }

  @Test
  fun `quick restore provisioning enables networking only when collected`() {
    assertProvisioningEnablesNetwork(controller.startProvisioning())
  }

  @Test
  fun `linked device provisioning enables networking only when collected`() {
    assertProvisioningEnablesNetwork(controller.startLinkDeviceProvisioning(false))
  }

  private fun assertProvisioningEnablesNetwork(flow: Flow<*>) {
    assertFalse(Networking.isEnabled)
    val stopBeforeSocketCreation = IOException("Stop before opening a provisioning socket")
    every { AppDependencies.signalServiceNetworkAccess } answers {
      assertNetworkEnabled()
      throw stopBeforeSocketCreation
    }

    val failure = assertThrows(IOException::class.java) {
      runBlocking { flow.collect() }
    }

    assertEquals(stopBeforeSocketCreation.message, failure.message)
    assertTrue(generateSequence<Throwable>(failure) { it.cause }.take(8).any { it === stopBeforeSocketCreation })
    assertNetworkEnabled()
  }

  private fun assertNetworkEnabled() {
    assertTrue(seenNetworkConfig)
    assertTrue(Networking.isEnabled)
    assertSame(proxy, Networking.socksProxy)
  }
}
