package org.thoughtcrime.securesms.components.settings.app.privacy

import kotlinx.coroutines.withContext
import org.signal.core.util.ServiceUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.TextSecurePreferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * All of the storage access behind [PrivacySettingsViewModel].
 */
object PrivacySettingsRepository {

  suspend fun getBlockedCount(): Int {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.getBlocked().size
    }
  }

  fun hasPhoneNumber(): Boolean = !SignalStore.account.isPhoneNumberless

  fun isDeviceSecure(): Boolean = ServiceUtil.getKeyguardManager(AppDependencies.application).isKeyguardSecure

  fun isReadReceiptsEnabled(): Boolean = SignalStore.settings.isReadReceiptsEnabled

  suspend fun setReadReceiptsEnabled(enabled: Boolean) {
    SignalStore.settings.isReadReceiptsEnabled = enabled
    syncConfiguration()
  }

  fun isTypingIndicatorsEnabled(): Boolean = SignalStore.settings.isTypingIndicatorsEnabled

  suspend fun setTypingIndicatorsEnabled(enabled: Boolean) {
    SignalStore.settings.isTypingIndicatorsEnabled = enabled
    syncConfiguration()

    if (!enabled) {
      AppDependencies.typingStatusRepository.clear()
    }
  }

  fun getUniversalExpireTimer(): Duration = SignalStore.settings.universalExpireTimer.seconds

  fun isScreenLockEnabled(): Boolean = TextSecurePreferences.isBiometricScreenLockEnabled(AppDependencies.application)

  fun isScreenSecurityEnabled(): Boolean = SignalStore.settings.isScreenSecurityEnabled

  fun isScreenSecurityForced(): Boolean = ScreenLockController.alwaysSetSecureFlagOnResume

  fun canChangeMessagingSettings(): Boolean = SignalStore.account.isPrimaryDevice

  fun setScreenSecurityEnabled(enabled: Boolean) {
    SignalStore.settings.isScreenSecurityEnabled = enabled
  }

  fun isIncognitoKeyboardEnabled(): Boolean = SignalStore.settings.isIncognitoKeyboardEnabled

  fun setIncognitoKeyboardEnabled(enabled: Boolean) {
    SignalStore.settings.isIncognitoKeyboardEnabled = enabled
  }

  /** The retired wallet lock is unavailable; preserve its stored keys without reading or rewriting them. */
  fun isPaymentLockEnabled(): Boolean = false

  @Suppress("UNUSED_PARAMETER")
  fun setPaymentLockEnabled(enabled: Boolean) = Unit

  fun isPassphraseLockEnabled(): Boolean = TextSecurePreferences.isPassphraseLockEnabled(AppDependencies.application)

  fun setPassphraseLockEnabled(enabled: Boolean) = TextSecurePreferences.setPassphraseLockEnabled(AppDependencies.application, enabled)

  fun getPassphraseLockTriggers(): Set<String> = TextSecurePreferences.getPassphraseLockTrigger(AppDependencies.application).triggers

  fun setPassphraseLockTriggers(triggers: Set<String>) {
    SecurePreferenceManager.getSecurePreferences(AppDependencies.application).edit()
      .putStringSet(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER, triggers).apply()
  }

  fun getPassphraseLockTimeout(): Long = TextSecurePreferences.getPassphraseLockTimeout(AppDependencies.application)

  fun setPassphraseLockTimeout(seconds: Long) {
    SecurePreferenceManager.getSecurePreferences(AppDependencies.application).edit()
      .putLong(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT, seconds).apply()
  }

  fun isBlockUnknownEnabled(): Boolean = TextSecurePreferences.isBlockUnknownEnabled(AppDependencies.application)

  fun setBlockUnknownEnabled(enabled: Boolean) = TextSecurePreferences.setBlockUnknownEnabled(AppDependencies.application, enabled)

  private suspend fun syncConfiguration() {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          SignalStore.settings.isReadReceiptsEnabled,
          SignalStore.settings.isTypingIndicatorsEnabled,
          SignalStore.settings.isShowUnidentifiedDeliveryIndicatorsEnabled,
          SignalStore.settings.isLinkPreviewsEnabled
        )
      )
    }
  }
}
