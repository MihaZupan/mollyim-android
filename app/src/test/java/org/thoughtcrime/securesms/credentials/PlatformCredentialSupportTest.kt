package org.thoughtcrime.securesms.credentials

import android.app.Application
import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.passwordmanager.CredentialManagerError
import org.signal.passwordmanager.PasskeyAssertionResult
import org.signal.passwordmanager.PasskeyCreationResult
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.passwordmanager.SignalPasskeyManager

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class PlatformCredentialSupportTest {
  private val context = mockk<Context>()

  @Test
  fun `legacy Google-only providers are not advertised or treated as successful`() = runBlocking {
    assertFalse(SignalCredentialManager.isSupported(context))
    assertFalse(SignalPasskeyManager.isSupported(context))
    assertFalse(SignalCredentialManager.isGooglePasswordManagerDefault(context))
    assertNull(SignalCredentialManager.getCredential(context))
    assertTrue(SignalCredentialManager.saveCredential(context, "fixture", "fixture") is CredentialManagerError.MissingCredentialManager)
    assertEquals(
      PasskeyCreationResult.NoProviderAvailable,
      SignalPasskeyManager.createPasskey(context, "example.invalid", "fixture", byteArrayOf(), "fixture", listOf(-7), emptyList())
    )
    assertEquals(
      PasskeyAssertionResult.NoCredentialAvailable,
      SignalPasskeyManager.getPasskeyAssertion(context, "example.invalid", byteArrayOf(), 30, emptyList())
    )
  }

  @Test
  @Config(sdk = [27])
  fun `minimum supported Android does not query a missing legacy provider`() {
    assertFalse(SignalCredentialManager.isSupported(context))
    assertFalse(SignalPasskeyManager.isSupported(context))
  }

  @Test
  @Config(sdk = [34])
  fun `platform providers remain available without querying Google accounts`() {
    assertTrue(SignalCredentialManager.isSupported(context))
    assertTrue(SignalPasskeyManager.isSupported(context))
  }
}
