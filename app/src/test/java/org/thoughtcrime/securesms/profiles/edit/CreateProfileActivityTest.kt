/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.edit

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
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
import org.thoughtcrime.securesms.PassphraseCreateActivity
import org.thoughtcrime.securesms.PassphrasePromptActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CreateProfileActivityTest {

  @Before
  fun setUp() {
    mockkObject(RegistrationActivityLifecycle.Companion, SignalStore)
    mockkStatic(MasterSecretUtil::class, TextSecurePreferences::class, PlainTextKeyValueStore::class)
    every { PlainTextKeyValueStore.language } returns "zz"
    every { SignalStore.account } throws AssertionError("Private account accessed before profile admission")
    every { SignalStore.registration } throws AssertionError("Profile host entered account-state routing")
    every { TextSecurePreferences.isDynamicColorsEnabled(any()) } returns false
  }

  @After
  fun tearDown() {
    PrivateRestoreProbe.rejectCreation = false
    unmockkStatic(MasterSecretUtil::class, TextSecurePreferences::class, PlainTextKeyValueStore::class)
    unmockkObject(RegistrationActivityLifecycle.Companion, SignalStore)
  }

  @Test
  fun `missing admission host finishes securely without restoring or inflating private fragments`() {
    every { RegistrationActivityLifecycle.create(any()) } returns null
    val savedState = stateWithPrivateFragment()
    val controller = Robolectric.buildActivity(CreateProfileActivity::class.java)

    try {
      val activity = controller.create(savedState).get()

      assertThat(activity.isFinishing).isTrue()
      assertThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0).isTrue()
      assertThat(activity.supportFragmentManager.fragments).isEmpty()
      verify(exactly = 0) { SignalStore.account }
      verify(exactly = 0) { SignalStore.registration }
    } finally {
      controller.destroy()
    }
  }

  @Test
  fun `uncached key preserves group editing and next intent through passphrase prompt`() {
    assertThat(KeyCachingService.isLocked()).isTrue()
    assertPassphraseRoute(initialized = true, destination = PassphrasePromptActivity::class.java)
  }

  @Test
  fun `uninitialized passphrase routes explicit creation without private profile access`() {
    assertPassphraseRoute(initialized = false, destination = PassphraseCreateActivity::class.java)
  }

  private fun assertPassphraseRoute(initialized: Boolean, destination: Class<out Activity>) {
    val host = mockk<RegistrationActivityLifecycle>(relaxed = true)
    every { RegistrationActivityLifecycle.create(any()) } returns host
    every { host.isAdmitted() } returns false
    // Simulate initialization metadata only, never key availability or secret material.
    every { MasterSecretUtil.isPassphraseInitialized(any()) } returns initialized
    val nextIntent = Intent("org.thoughtcrime.securesms.TEST_AFTER_PROFILE")
    val intent = CreateProfileActivity.getIntentForUserProfile(ApplicationProvider.getApplicationContext())
      .putExtra(CreateProfileActivity.GROUP_ID, "existing-group-id")
      .putExtra(CreateProfileActivity.SHOW_TOOLBAR, true)
      .putExtra(CreateProfileActivity.NEXT_BUTTON_TEXT, R.string.save)
      .putExtra(CreateProfileActivity.IS_DESCRIPTION_FOCUSED, true)
      .putExtra(CreateProfileActivity.EXCLUDE_SYSTEM, true)
      .putExtra(CreateProfileActivity.NEXT_INTENT, nextIntent)
    val savedState = stateWithPrivateFragment()
    val controller = Robolectric.buildActivity(CreateProfileActivity::class.java, intent)

    try {
      val activity = controller.create(savedState).get()
      val unlockIntent = requireNotNull(shadowOf(activity).nextStartedActivity)
      val continuation = requireNotNull(IntentCompat.getParcelableExtra(unlockIntent, CreateProfileActivity.NEXT_INTENT, Intent::class.java))
      val afterProfile = requireNotNull(IntentCompat.getParcelableExtra(continuation, CreateProfileActivity.NEXT_INTENT, Intent::class.java))

      assertThat(unlockIntent.component?.className).isEqualTo(destination.name)
      assertThat(continuation.component?.className).isEqualTo(CreateProfileActivity::class.java.name)
      assertThat(continuation.getStringExtra(CreateProfileActivity.GROUP_ID)).isEqualTo("existing-group-id")
      assertThat(continuation.getBooleanExtra(CreateProfileActivity.SHOW_TOOLBAR, false)).isTrue()
      assertThat(continuation.getIntExtra(CreateProfileActivity.NEXT_BUTTON_TEXT, 0)).isEqualTo(R.string.save)
      assertThat(continuation.getBooleanExtra(CreateProfileActivity.IS_DESCRIPTION_FOCUSED, false)).isTrue()
      assertThat(continuation.getBooleanExtra(CreateProfileActivity.EXCLUDE_SYSTEM, false)).isTrue()
      assertThat(afterProfile.action).isEqualTo(nextIntent.action)
      assertThat(activity.isFinishing).isTrue()
      assertThat(activity.supportFragmentManager.fragments).isEmpty()
      verify(exactly = 0) { SignalStore.account }
      verify(exactly = 0) { SignalStore.registration }
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
      check(!rejectCreation) { "Private fragment restored before profile admission" }
    }
  }
}
