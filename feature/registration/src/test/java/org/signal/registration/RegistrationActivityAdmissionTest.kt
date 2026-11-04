/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RegistrationActivityAdmissionTest {

  @Before
  fun setUp() {
    mockkObject(RegistrationActivityLifecycle.Companion, RegistrationDependencies.Companion, RegistrationRepository.Companion)
    every { RegistrationDependencies.get() } throws AssertionError("Late registration dependencies accessed before admission")
    every { RegistrationRepository.create(any()) } throws AssertionError("Private registration content created before admission")
  }

  @After
  fun tearDown() {
    unmockkObject(RegistrationActivityLifecycle.Companion, RegistrationDependencies.Companion, RegistrationRepository.Companion)
  }

  @Test
  fun `missing early host finishes with a hidden secure window without private initialization`() {
    every { RegistrationActivityLifecycle.create(any()) } returns null
    val controller = Robolectric.buildActivity(RegistrationActivity::class.java)

    try {
      val activity = controller.create().get()

      assertThat(activity.isFinishing).isTrue()
      assertHiddenAndSecure(activity)
      verify(exactly = 0) { RegistrationDependencies.get() }
      verify(exactly = 0) { RegistrationRepository.create(any()) }
    } finally {
      controller.destroy()
    }
  }

  @Test
  fun `authentication success cannot resume queued permissions when re-admission is rejected`() {
    val host = mockk<RegistrationActivityLifecycle>(relaxed = true)
    val authenticationIntent = Intent(ApplicationProvider.getApplicationContext<Application>(), RegistrationActivity::class.java)
      .setAction("org.signal.registration.TEST_AUTHENTICATION")
    every { RegistrationActivityLifecycle.create(any()) } returns host
    every { host.isAdmitted() } returns false
    every { host.authenticationIntent() } returns authenticationIntent
    val proceed = mockk<() -> Unit>(relaxed = true)
    val controller = Robolectric.buildActivity(RegistrationActivity::class.java)

    try {
      val activity = controller.create().start().resume().get()
      // Deliver the navigation callback without creating a private registration screen.
      activity.javaClass.getDeclaredMethod("onPermissionsChanged", Function0::class.java).apply {
        isAccessible = true
        invoke(activity, proceed)
      }

      verify(exactly = 0) { host.onPermissionsChanged() }
      verify(exactly = 0) { proceed.invoke() }

      shadowOf(activity).receiveResult(authenticationIntent, Activity.RESULT_OK, null)

      assertHiddenAndSecure(activity)
      verify(exactly = 0) { host.onPermissionsChanged() }
      verify(exactly = 0) { proceed.invoke() }
      verify(exactly = 0) { RegistrationDependencies.get() }
      verify(exactly = 0) { RegistrationRepository.create(any()) }

      shadowOf(activity).receiveResult(authenticationIntent, Activity.RESULT_CANCELED, null)
      assertThat(activity.isFinishing).isTrue()
    } finally {
      controller.pause().stop().destroy()
    }

    verify(exactly = 0) { host.onPermissionsChanged() }
    verify(exactly = 0) { proceed.invoke() }
  }

  private fun assertHiddenAndSecure(activity: RegistrationActivity) {
    assertThat(activity.window.decorView.visibility).isEqualTo(View.INVISIBLE)
    assertThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0).isTrue()
  }
}
