package org.thoughtcrime.securesms.components.settings.app.updates

import im.molly.app.base.ApkInfo
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.apkupdate.ApkUpdateNotifications
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

object AppUpdatesSettingsRepository {
  fun isManagedUpdateChannel(): Boolean = BuildConfig.MANAGE_MOLLY_UPDATES
  fun getVersionName(): String = ApkInfo.versionName
  fun getLastSuccessfulCheck(): Long = SignalStore.apkUpdate.lastSuccessfulCheck
  fun isUpdateApkEnabled(): Boolean = TextSecurePreferences.isUpdateApkEnabled(AppDependencies.application)
  fun isIncludeBetaEnabled(): Boolean = TextSecurePreferences.isUpdateApkIncludeBetaEnabled(AppDependencies.application)

  fun setUpdateApkEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkEnabled(AppDependencies.application, enabled)
  }

  fun setIncludeBetaEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkIncludeBetaEnabled(AppDependencies.application, enabled)
  }

  fun dismissInstallPrompt() {
    ApkUpdateNotifications.dismissInstallPrompt(AppDependencies.application)
  }

  /** Called only by explicit managed-channel settings events, never by constructing/resuming this UI. */
  fun checkForUpdates() {
    if (!isManagedUpdateChannel()) return
    ApkUpdateRefreshListener.scheduleIfAllowed(AppDependencies.application)
    AppDependencies.jobManager.add(ApkUpdateJob())
  }
}
