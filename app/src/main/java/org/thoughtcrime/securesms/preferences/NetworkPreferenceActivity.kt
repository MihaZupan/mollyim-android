/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commitNow
import org.signal.registration.RegistrationActivityLifecycle
import org.thoughtcrime.securesms.PassphraseActivity
import org.thoughtcrime.securesms.PassphraseCreateActivity
import org.thoughtcrime.securesms.PassphrasePromptActivity
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter

/**
 * Network configuration is also needed before account registration is complete.
 */
class NetworkPreferenceActivity : PassphraseActivity() {

  companion object {
    private const val FRAGMENT_TAG = "network_preferences"

    @JvmStatic
    fun newIntent(context: Context): Intent = Intent(context, NetworkPreferenceActivity::class.java)
  }

  private val theme = DynamicNoActionBarTheme()
  private var activityLifecycle: RegistrationActivityLifecycle? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    theme.onCreate(this)
    activityLifecycle = RegistrationActivityLifecycle.create(this)
    val admitted = activityLifecycle?.isAdmitted() == true
    // Fragment restoration invokes the network fragment's private-dependency constructor.
    super.onCreate(if (admitted) savedInstanceState else null)

    if (!admitted) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    if (activityLifecycle == null) {
      finishAndRemoveTask()
      return
    }

    val destination = when {
      !MasterSecretUtil.isPassphraseInitialized(this) -> PassphraseCreateActivity::class.java
      KeyCachingService.isLocked() -> PassphrasePromptActivity::class.java
      else -> null
    }

    if (destination != null) {
      startActivity(Intent(this, destination).putExtra("next_intent", newIntent(this)))
      finish()
      return
    }

    SystemWindowInsetsSetter.attach(
      findViewById<View>(android.R.id.content),
      this,
      WindowInsetsCompat.Type.systemBars()
    )
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
    activityLifecycle?.onResume()
  }

  override fun onPostResume(screenLocked: Boolean) {
    super.onPostResume(screenLocked)
    if (!isFinishing && !screenLocked && activityLifecycle?.isAdmitted() == true &&
      supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) == null
    ) {
      supportFragmentManager.commitNow {
        replace(android.R.id.content, NetworkPreferenceFragment(), FRAGMENT_TAG)
      }
    }
  }

  override fun onDestroy() {
    activityLifecycle?.onDestroy()
    activityLifecycle = null
    super.onDestroy()
  }
}
