/*
 * Copyright 2026 Molly
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import org.signal.registration.RegistrationActivityLifecycle
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.WindowUtil

class AppRegistrationActivityLifecycle(private val activity: ComponentActivity) : RegistrationActivityLifecycle {

  companion object {
    @JvmStatic
    fun install() {
      RegistrationActivityLifecycle.install(::AppRegistrationActivityLifecycle)
    }
  }

  private var keyCleared = false
  private val clearKeyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      keyCleared = true
      activity.window.decorView.visibility = View.INVISIBLE
      activity.finishAndRemoveTask()
    }
  }

  init {
    ContextCompat.registerReceiver(
      activity,
      clearKeyReceiver,
      IntentFilter(KeyCachingService.CLEAR_KEY_EVENT),
      KeyCachingService.KEY_PERMISSION,
      null,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  override fun isAdmitted(): Boolean {
    return !keyCleared &&
      MasterSecretUtil.isPassphraseInitialized(activity) &&
      !KeyCachingService.isLocked() &&
      !ScreenLockController.shouldLockScreenAtStart()
  }

  override fun authenticationIntent(): Intent = Intent(activity, RegistrationLockActivity::class.java)

  override fun onResume() {
    val admitted = isAdmitted()
    if (admitted) {
      WindowUtil.initializeScreenshotSecurity(activity, activity.window)
    } else {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (Build.VERSION.SDK_INT >= 33) {
      activity.setRecentsScreenshotEnabled(admitted && !ScreenLockController.autoLock)
    }
  }

  override fun onPermissionsChanged() {
    activity.startService(
      Intent(activity, KeyCachingService::class.java).setAction(KeyCachingService.LOCALE_CHANGE_EVENT)
    )
  }

  override fun onDestroy() {
    activity.unregisterReceiver(clearKeyReceiver)
  }
}
