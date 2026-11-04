/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.registration.RegistrationActivityLifecycle
import org.thoughtcrime.securesms.PassphrasePromptActivity
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.service.KeyCachingService

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NetworkPreferenceActivityTest {

  @Before
  fun setUp() {
    mockkObject(RegistrationActivityLifecycle.Companion)
    mockkStatic(MasterSecretUtil::class, PlainTextKeyValueStore::class, AppDependencies::class)
    every { PlainTextKeyValueStore.language } returns "zz"
    every { AppDependencies.networkManager } throws AssertionError("Private network access before admission")
  }

  @After
  fun tearDown() {
    PrivateRestoreProbe.rejectCreation = false
    unmockkStatic(MasterSecretUtil::class, PlainTextKeyValueStore::class, AppDependencies::class)
    unmockkObject(RegistrationActivityLifecycle.Companion)
  }

  @Test
  fun `missing admission host finishes without restoring private fragments`() {
    every { RegistrationActivityLifecycle.create(any()) } returns null
    val savedState = stateWithPrivateFragment()
    val controller = Robolectric.buildActivity(NetworkPreferenceActivity::class.java)

    try {
      val activity = controller.create(savedState).get()

      assertThat(activity.isFinishing).isTrue()
      assertThat(activity.supportFragmentManager.fragments).isEmpty()
      assertThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0).isTrue()
      verify(exactly = 0) { AppDependencies.networkManager }
    } finally {
      controller.destroy()
    }
  }

  @Test
  fun `uncached key routes through passphrase prompt back to standalone network configuration`() {
    val host = mockk<RegistrationActivityLifecycle>(relaxed = true)
    every { RegistrationActivityLifecycle.create(any()) } returns host
    every { host.isAdmitted() } returns false
    // Only initialization metadata is simulated; no secret is supplied or generated.
    every { MasterSecretUtil.isPassphraseInitialized(any()) } returns true
    assertThat(KeyCachingService.isLocked()).isTrue()
    val savedState = stateWithPrivateFragment()
    val controller = Robolectric.buildActivity(NetworkPreferenceActivity::class.java)

    try {
      val activity = controller.create(savedState).get()
      val prompt = requireNotNull(shadowOf(activity).nextStartedActivity)
      val continuation = requireNotNull(IntentCompat.getParcelableExtra(prompt, "next_intent", Intent::class.java))

      assertThat(prompt.component?.className).isEqualTo(PassphrasePromptActivity::class.java.name)
      assertThat(continuation.component?.className).isEqualTo(NetworkPreferenceActivity::class.java.name)
      assertThat(activity.isFinishing).isTrue()
      assertThat(activity.supportFragmentManager.fragments).isEmpty()
      verify(exactly = 0) { AppDependencies.networkManager }
    } finally {
      controller.destroy()
    }

    verify(exactly = 1) { host.onDestroy() }
  }

  private fun stateWithPrivateFragment(): Bundle {
    PrivateRestoreProbe.rejectCreation = false
    val controller = Robolectric.buildActivity(FragmentActivity::class.java).create()
    val savedState = Bundle()
    try {
      controller.get().supportFragmentManager.beginTransaction()
        .add(PrivateRestoreProbe(), "private_restore_probe")
        .commitNow()
      controller.saveInstanceState(savedState)
    } finally {
      controller.destroy()
    }
    PrivateRestoreProbe.rejectCreation = true
    return savedState
  }

  class PrivateRestoreProbe : Fragment() {
    companion object {
      var rejectCreation = false
    }

    init {
      check(!rejectCreation) { "Private fragment restored before admission" }
    }
  }
}
