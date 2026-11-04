/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.content.Intent
import androidx.activity.ComponentActivity

/**
 * Installed by the host before private registration dependencies are available.
 */
interface RegistrationActivityLifecycle {
  fun isAdmitted(): Boolean
  fun authenticationIntent(): Intent
  fun onResume()
  fun onPermissionsChanged()
  fun onDestroy()

  companion object {
    private var factory: ((ComponentActivity) -> RegistrationActivityLifecycle)? = null

    @JvmStatic
    fun install(factory: (ComponentActivity) -> RegistrationActivityLifecycle) {
      this.factory = factory
    }

    fun create(activity: ComponentActivity): RegistrationActivityLifecycle? = factory?.invoke(activity)
  }
}
