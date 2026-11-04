package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.crypto.MasterSecret
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.TextSecurePreferences

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SettingsValuesMigrationTest {
  private lateinit var context: Context
  private lateinit var legacy: SharedPreferences
  private lateinit var store: KeyValueStore
  private var wasInstrumentation = false

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext<Application>()
    legacy = context.getSharedPreferences("molly-settings-migration-fixture", Context.MODE_PRIVATE)
    legacy.edit().clear().commit()
    store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())
    wasInstrumentation = Environment.IS_INSTRUMENTATION
    Environment.IS_INSTRUMENTATION = false
    mockkStatic(SecurePreferenceManager::class)
    mockkStatic(KeyCachingService::class)
    every { SecurePreferenceManager.getSecurePreferences(any()) } returns legacy
    every { KeyCachingService.getMasterSecret() } returns mockk<MasterSecret>()
  }

  @After
  fun teardown() {
    Environment.IS_INSTRUMENTATION = wasInstrumentation
    unmockkAll()
  }

  @Test
  fun `updater scheduling facade uses migrated KV rather than reviving the legacy timer`() {
    legacy.edit().putLong("pref_update_apk_refresh_time", 1234L).commit()
    val updates = ApkUpdateValues(store, context)
    mockkObject(SignalStore.Companion)
    every { SignalStore.apkUpdate } returns updates

    assertEquals(1234L, TextSecurePreferences.getUpdateApkRefreshTime(context))
    TextSecurePreferences.setUpdateApkRefreshTime(context, 5678L)
    assertEquals(5678L, updates.refreshTime)
    assertEquals(1234L, legacy.getLong("pref_update_apk_refresh_time", 0))
    assertEquals(5678L, ApkUpdateValues(store, context).refreshTime)
  }

  @Test
  fun `push readiness ignores stale capabilities belonging to an unselected delivery method`() {
    val settings = SettingsValues(store, context)
    val unifiedPush = mockk<UnifiedPushValues>()
    mockkObject(SignalStore.Companion)
    every { SignalStore.settings } returns settings
    every { SignalStore.unifiedpush } returns unifiedPush
    every { unifiedPush.isAvailableOrAirGapped } returns true
    val account = AccountValues(store, context)
    account.fcmEnabled = true
    account.fcmToken = "synthetic-token"
    settings.preferredNotificationMethod = SettingsValues.NotificationDeliveryMethod.FCM
    assertTrue(account.pushAvailable)

    settings.preferredNotificationMethod = SettingsValues.NotificationDeliveryMethod.WEBSOCKET
    assertFalse(account.pushAvailable)

    settings.preferredNotificationMethod = SettingsValues.NotificationDeliveryMethod.UNIFIEDPUSH
    assertTrue(account.pushAvailable)
    every { unifiedPush.isAvailableOrAirGapped } returns false
    assertFalse(account.pushAvailable)

    settings.preferredNotificationMethod = SettingsValues.NotificationDeliveryMethod.FCM
    account.fcmEnabled = false
    every { unifiedPush.isAvailableOrAirGapped } returns true
    assertFalse(account.pushAvailable)
  }

  @Test
  fun `database bootstrap uses original Molly secrets instead of upstream preferences`() {
    val upstream = PreferenceManager.getDefaultSharedPreferences(context)
    upstream.edit().putString("pref_database_encrypted_secret", "unrelated-secret").commit()
    legacy.edit()
      .putString("pref_database_encrypted_secret", "original-database-secret")
      .putString("pref_attachment_encrypted_secret", "original-attachment-secret")
      .putInt("pref_app_migration_version", 17)
      .commit()

    PlainTextKeyValueStore.init(context)

    assertEquals("original-database-secret", PlainTextKeyValueStore.databaseEncryptedSecret)
    assertEquals("original-attachment-secret", PlainTextKeyValueStore.attachmentEncryptedSecret)
    assertEquals(17, PlainTextKeyValueStore.appMigrationVersion)
    PlainTextKeyValueStore.databaseEncryptedSecret = "rewrapped-database-secret"
    assertEquals("rewrapped-database-secret", legacy.getString("pref_database_encrypted_secret", null))
    assertEquals("unrelated-secret", upstream.getString("pref_database_encrypted_secret", null))
  }

  @Test
  fun `imports from Molly file and preserves every existing destination`() {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("pref_screen_security", false).commit()
    legacy.edit()
      .putBoolean("pref_screen_security", true)
      .putBoolean("pref_read_receipts", false)
      .putLong("pref_backup_next_time", 1234)
      .putStringSet("pref_media_download_mobile", setOf("video"))
      .putString("pref_theme", "dark")
      .commit()
    store.beginWrite()
      .putBoolean(SettingsValues.READ_RECEIPTS_ENABLED, true)
      .putBoolean("settings.passphrase.disabled", false)
      .putLong("settings.backups.next.time", 9876)
      .putBlob("settings.media.download.mobile", org.thoughtcrime.securesms.database.model.databaseprotos.SignalStoreList(contents = listOf("audio")).encode())
      .commit()

    val settings = SettingsValues(store, context)

    assertTrue(settings.isScreenSecurityEnabled)
    assertTrue(settings.isReadReceiptsEnabled)
    assertFalse(settings.passphraseDisabled)
    assertEquals(9876L, settings.localBackupNextTime)
    assertEquals(setOf("audio"), settings.mobileMediaDownloadAllowed)
    assertEquals(SettingsValues.Theme.DARK, settings.theme)
    assertTrue(legacy.getBoolean("pref_screen_security", false))
  }

  @Test
  fun `compatibility writes and subsequent launches use KV without changing legacy secrets`() {
    legacy.edit().putBoolean("pref_read_receipts", false).putString("pref_repeat_alerts", "3").commit()
    val settings = SettingsValues(store, context)
    val prefs = MollySettingsPreferences(legacy) { settings }

    prefs.edit().putBoolean("pref_read_receipts", true).putString("pref_repeat_alerts", "7").commit()
    assertTrue(settings.isReadReceiptsEnabled)
    assertEquals(7, settings.messageNotificationsRepeatAlerts)
    assertEquals("7", prefs.getString("pref_repeat_alerts", null))
    assertFalse(legacy.getBoolean("pref_read_receipts", true))
    assertEquals("3", legacy.getString("pref_repeat_alerts", null))
    assertTrue(SettingsValues(store, context).isReadReceiptsEnabled)

    prefs.edit().putString("pref_theme", "dark").putBoolean("pref_system_emoji", true).commit()
    assertEquals("dark", TextSecurePreferences.getTheme(context))
    assertTrue(TextSecurePreferences.isSystemEmojiPreferred(context))
  }

  @Test
  fun `restore requires source provenance and restored KV wins over legacy duplicates`() {
    val settings = SettingsValues(store, context)
    settings.isReadReceiptsEnabled = true
    settings.isTypingIndicatorsEnabled = true
    settings.isTurnOnly = true
    legacy.edit()
      .putBoolean("pref_read_receipts", false)
      .putBoolean("pref_typing_indicators", false)
      .putBoolean("pref_turn_only", false)
      .putString("pref_theme", "dark")
      .commit()

    settings.restoreLegacySharedPrefsAfterBackupRestore()
    assertTrue(settings.isReadReceiptsEnabled)
    assertEquals("dark", legacy.getString("pref_theme", null))
    settings.restoreLegacySharedPrefsAfterBackupRestore(
      setOf("pref_read_receipts", "pref_typing_indicators", "pref_theme"),
      setOf(SettingsValues.TYPING_INDICATORS_ENABLED)
    )
    assertFalse(settings.isReadReceiptsEnabled)
    assertTrue(settings.isTypingIndicatorsEnabled)
    assertTrue(settings.isTurnOnly)
    assertEquals(SettingsValues.Theme.DARK, settings.theme)
  }

  @Test
  fun `numeric legacy settings support strings ints and encrypted type mismatch defaults`() {
    legacy.edit().putString("pref_repeat_alerts", "4").putInt("pref_message_body_text_size", 21).commit()
    assertEquals(4, LegacySharedPrefs.getIntegerFromString(context, "pref_repeat_alerts", 0))
    assertEquals(21, LegacySharedPrefs.getIntegerFromString(context, "pref_message_body_text_size", 16))
    legacy.edit().putString("pref_repeat_alerts", "invalid").commit()
    assertEquals(0, LegacySharedPrefs.getIntegerFromString(context, "pref_repeat_alerts", 0))

    val encrypted = mockk<SharedPreferences>()
    every { encrypted.getString("font", null) } returns null
    every { encrypted.getInt("font", 16) } returns 23
    every { SecurePreferenceManager.getSecurePreferences(any()) } returns encrypted
    assertEquals(23, LegacySharedPrefs.getIntegerFromString(context, "font", 16))
  }

  @Test
  fun `bootstrap reads work while migration refuses a missing master secret`() {
    legacy.edit().putString("pref_theme", "dark").putBoolean("pref_passphrase_lock", true).commit()
    every { KeyCachingService.getMasterSecret() } returns null
    assertEquals("dark", TextSecurePreferences.getTheme(context))
    assertTrue(TextSecurePreferences.isPassphraseLockEnabled(context))
    org.junit.Assert.assertThrows(IllegalStateException::class.java) {
      SettingsValues(store, context)
    }
    assertFalse(store.containsKey("molly.settings.legacyMigrationComplete"))
  }
}
