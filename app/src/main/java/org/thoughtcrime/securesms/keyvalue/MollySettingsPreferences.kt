package org.thoughtcrime.securesms.keyvalue

import android.content.SharedPreferences
import org.thoughtcrime.securesms.database.model.databaseprotos.SignalStoreList

/**
 * Compatibility view for legacy preference consumers. Migration and backup import must use the
 * original SecurePreferenceManager preferences, not this view, to avoid reading back migrated values.
 * Only the explicitly non-secret bootstrap getters may be used before SignalStore is unlocked.
 */
class MollySettingsPreferences(
  private val legacy: SharedPreferences,
  private val settings: () -> SettingsValues = { SignalStore.settings }
) : SharedPreferences by legacy {

  companion object {
    internal val booleanKeys = mapOf(
      "pref_system_emoji" to SettingsValues.PREFER_SYSTEM_EMOJI,
      "pref_enter_sends" to SettingsValues.ENTER_KEY_SENDS,
      "pref_backup_enabled" to SettingsValues.BACKUPS_ENABLED,
      "pref_delivery_report_sms" to SettingsValues.SMS_DELIVERY_REPORTS_ENABLED,
      "pref_wifi_sms" to SettingsValues.WIFI_CALLING_COMPATIBILITY_MODE_ENABLED,
      "pref_key_enable_notifications" to SettingsValues.MESSAGE_NOTIFICATIONS_ENABLED,
      "pref_key_vibrate" to SettingsValues.MESSAGE_VIBRATE_ENABLED,
      "pref_key_inthread_notifications" to SettingsValues.MESSAGE_IN_CHAT_SOUNDS_ENABLED,
      "pref_call_notifications" to SettingsValues.CALL_NOTIFICATIONS_ENABLED,
      "pref_call_vibrate" to SettingsValues.CALL_VIBRATE_ENABLED,
      "pref_enable_new_contacts_notifications" to SettingsValues.NOTIFY_WHEN_CONTACT_JOINS_SIGNAL,
      "pref_screen_security" to SettingsValues.SCREEN_SECURITY_ENABLED,
      "pref_incognito_keyboard" to SettingsValues.INCOGNITO_KEYBOARD_ENABLED,
      "pref_read_receipts" to SettingsValues.READ_RECEIPTS_ENABLED,
      "pref_typing_indicators" to SettingsValues.TYPING_INDICATORS_ENABLED,
      "pref_show_unidentifed_delivery_indicators" to SettingsValues.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS,
      "pref_universal_unidentified_access" to SettingsValues.UNIVERSAL_UNIDENTIFIED_ACCESS,
      "pref_turn_only" to SettingsValues.ALWAYS_RELAY_CALLS,
      "pref_disable_passphrase" to "settings.passphrase.disabled",
      "pref_timeout_passphrase" to "settings.passphrase.timeout.enabled",
      "pref_android_screen_lock" to "settings.screen.lock.enabled",
      "pref_successful_directory" to "misc.has_successfully_retrieved_directory",
      "pref_needs_full_contact_sync" to "misc.needs_full_contact_sync",
      "pref_service_outage" to "misc.service_outage",
      "pref_prompted_push_registration" to "registration.has_prompted_push_registration"
    )
    internal val stringKeys = mapOf(
      "pref_theme" to SettingsValues.THEME,
      "pref_key_ringtone" to SettingsValues.MESSAGE_NOTIFICATION_SOUND,
      "pref_led_color" to SettingsValues.MESSAGE_LED_COLOR,
      "pref_led_blink" to SettingsValues.MESSAGE_LED_BLINK_PATTERN,
      "pref_led_blink_custom" to "settings.message.led.blink.custom",
      "pref_notification_privacy" to SettingsValues.MESSAGE_NOTIFICATION_PRIVACY,
      "pref_call_ringtone" to SettingsValues.CALL_RINGTONE,
      "pref_media_keyboard_mode" to "settings.media.keyboard.mode"
    )
    internal val integerKeys = mapOf(
      "pref_message_body_text_size" to SettingsValues.MESSAGE_FONT_SIZE,
      "pref_repeat_alerts" to SettingsValues.MESSAGE_REPEAT_ALERTS,
      "pref_notification_priority" to "settings.message.notification.priority",
      "pref_timeout_interval" to "settings.passphrase.timeout",
      "pref_job_manager_version" to "misc.job_manager_version",
      "pref_notification_channel_version" to "misc.notification_channel_version",
      "pref_notification_messages_channel_version" to "misc.notification_messages_channel_version"
    )
    internal val longKeys = mapOf(
      "pref_backup_next_time" to "settings.backups.next.time",
      "pref_android_screen_lock_timeout" to "settings.screen.lock.timeout",
      "pref_last_full_contact_sync_time" to "misc.last_full_contact_sync_time",
      "pref_last_outage_check_time" to "misc.last_outage_check_time",
      "pref_signed_pre_key_rotation_time" to "misc.signed_prekey_rotation_time",
      "pref_registration_lock_last_reminder_time_post_kbs" to "pin.last_successful_entry",
      "pref_registration_lock_next_reminder_interval" to "pin.interval_index",
      "pref_update_apk_refresh_time" to "apk_update.refresh_time"
    )
    internal val stringSetKeys = mapOf(
      "pref_media_download_mobile" to "settings.media.download.mobile",
      "pref_media_download_wifi" to "settings.media.download.wifi",
      "pref_media_download_roaming" to "settings.media.download.roaming"
    )
    internal val migratedKeys = booleanKeys + stringKeys + integerKeys + longKeys + stringSetKeys
  }

  override fun contains(key: String): Boolean =
    migratedKeys[key]?.let { settings().store.containsKey(it) } ?: legacy.contains(key)

  override fun getBoolean(key: String, defValue: Boolean): Boolean =
    booleanKeys[key]?.let { settings().store.getBoolean(it, defValue) } ?: legacy.getBoolean(key, defValue)

  override fun getInt(key: String, defValue: Int): Int =
    integerKeys[key]?.let { settings().store.getInteger(it, defValue) } ?: legacy.getInt(key, defValue)

  override fun getLong(key: String, defValue: Long): Long =
    longKeys[key]?.let { settings().store.getLong(it, defValue) } ?: legacy.getLong(key, defValue)

  override fun getString(key: String, defValue: String?): String? {
    integerKeys[key]?.let { return settings().store.getInteger(it, defValue?.toIntOrNull() ?: 0).toString() }
    val target = stringKeys[key] ?: return legacy.getString(key, defValue)
    return settings().store.getString(target, defValue)
  }

  override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
    val target = stringSetKeys[key] ?: return legacy.getStringSet(key, defValues)
    val value = settings().store.getBlob(target, null) ?: return defValues
    return SignalStoreList.ADAPTER.decode(value).contents.toSet()
  }

  override fun edit(): SharedPreferences.Editor = Editor()

  private inner class Editor : SharedPreferences.Editor {
    private val raw = legacy.edit()
    private val changes = linkedMapOf<String, Any?>()

    private fun put(key: String, value: Any?, writeLegacy: () -> Unit): SharedPreferences.Editor {
      if (key in migratedKeys) changes[key] = value else writeLegacy()
      return this
    }

    override fun putBoolean(key: String, value: Boolean) = put(key, value) { raw.putBoolean(key, value) }
    override fun putInt(key: String, value: Int) = put(key, value) { raw.putInt(key, value) }
    override fun putLong(key: String, value: Long) = put(key, value) { raw.putLong(key, value) }
    override fun putFloat(key: String, value: Float) = put(key, value) { raw.putFloat(key, value) }
    override fun putString(key: String, value: String?) = put(key, value) { raw.putString(key, value) }
    override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet()) { raw.putStringSet(key, values) }
    override fun remove(key: String) = put(key, null) { raw.remove(key) }
    override fun clear(): SharedPreferences.Editor = throw UnsupportedOperationException("Clearing legacy preferences is not supported")

    private fun writeSettings(blocking: Boolean) {
      if (changes.isEmpty()) return
      val values = settings()
      val writer = values.store.beginWrite()
      for ((key, value) in changes) {
        val target = migratedKeys.getValue(key)
        when {
          key == "pref_theme" -> values.theme = if (value == null) SettingsValues.Theme.SYSTEM else SettingsValues.Theme.deserialize(value as String)
          key == "pref_system_emoji" -> values.isPreferSystemEmoji = value as? Boolean ?: false
          value == null -> writer.remove(target)
          key in booleanKeys -> writer.putBoolean(target, value as Boolean)
          key in integerKeys -> writer.putInteger(target, if (value is String) value.toInt() else value as Int)
          key in longKeys -> writer.putLong(target, (value as Number).toLong())
          key in stringSetKeys -> {
            @Suppress("UNCHECKED_CAST")
            writer.putBlob(target, SignalStoreList(contents = (value as Set<String>).toList()).encode())
          }
          else -> writer.putString(target, value as String)
        }
      }
      if (blocking) writer.commit() else writer.apply()
      changes.clear()
    }

    override fun commit(): Boolean {
      writeSettings(blocking = true)
      return raw.commit()
    }

    override fun apply() {
      writeSettings(blocking = false)
      raw.apply()
    }
  }
}
