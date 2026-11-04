/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.thoughtcrime.securesms.PassphraseActivity
import org.thoughtcrime.securesms.PassphraseCreateActivity
import org.thoughtcrime.securesms.PassphrasePromptActivity
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

/**
 * Uses the app's biometric lifecycle without its account-state routing back into registration.
 */
class RegistrationLockActivity : PassphraseActivity() {

  private val theme = DynamicNoActionBarTheme()
  private lateinit var activityLifecycle: AppRegistrationActivityLifecycle

  override fun onCreate(savedInstanceState: Bundle?) {
    theme.onCreate(this)
    super.onCreate(savedInstanceState)
    activityLifecycle = AppRegistrationActivityLifecycle(this)

    val destination = when {
      !MasterSecretUtil.isPassphraseInitialized(this) -> PassphraseCreateActivity::class.java
      KeyCachingService.isLocked() -> PassphrasePromptActivity::class.java
      else -> null
    }

    if (destination != null) {
      startActivity(
        Intent(this, destination).putExtra("next_intent", Intent(this, RegistrationLockActivity::class.java))
      )
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  override fun onPostResume(screenLocked: Boolean) {
    super.onPostResume(screenLocked)
    if (!isFinishing && !screenLocked && activityLifecycle.isAdmitted()) {
      setResult(Activity.RESULT_OK)
      finish()
    }
  }

  override fun onDestroy() {
    activityLifecycle.onDestroy()
    super.onDestroy()
  }
}
