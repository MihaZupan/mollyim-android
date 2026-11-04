package org.thoughtcrime.securesms.backup

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.core.database.SqlUtil
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.crypto.AttachmentSecret
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.eventbus.EventBus
import org.thoughtcrime.securesms.backup.proto.BackupFrame
import org.thoughtcrime.securesms.backup.proto.SharedPreference
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import org.thoughtcrime.securesms.crypto.MasterSecret
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.jobs.LocalBackupJobApi29
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.service.NotificationController
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class PassphraseLockTimeoutBackupTest {
  private lateinit var context: Context
  private lateinit var preferences: SharedPreferences
  private lateinit var settings: SettingsValues
  private var previousPreferences: Any? = null
  private val preferencesField = SecurePreferenceManager::class.java.getDeclaredField("securePreferences").apply { isAccessible = true }
  private val attachmentSecret = AttachmentSecret(null, null, ByteArray(32))

  @Before
  fun setUp() {
    val namespace = "timeout-backup-${UUID.randomUUID()}-"
    context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
      override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(namespace + name, mode)
    }
    previousPreferences = preferencesField.get(null)
    preferencesField.set(null, null)
    val encryptionKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val macKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    KeyCachingService.setMasterSecret(MasterSecret(encryptionKey, macKey))
    encryptionKey.fill(0)
    macKey.fill(0)
    preferences = TextSecurePreferences.getSharedPreferences(context)
    assertTrue(preferences.edit().remove(TIMEOUT).putString(CONTROL, "synthetic control").commit())
    val wasInstrumentation = Environment.IS_INSTRUMENTATION
    try {
      // Keep the real settings facade, without unrelated startup migrations.
      Environment.IS_INSTRUMENTATION = true
      settings = SettingsValues(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor()), context)
    } finally {
      Environment.IS_INSTRUMENTATION = wasInstrumentation
    }
    mockkStatic(SignalStore::class)
    every { staticGetter<SettingsValues>(SignalStore::class.java, "settings") } returns settings
  }

  @After
  fun tearDown() {
    unmockkAll()
    preferencesField.set(null, previousPreferences)
    KeyCachingService.clearMasterSecret()
  }

  @Test
  fun `encrypted long and legacy integer survive real wire and preference import`() {
    for (legacyInt in listOf(false, true)) {
      if (legacyInt) {
        assertTrue(preferences.edit().putInt(TIMEOUT, 300).commit())
      } else {
        assertTrue(preferences.edit().putLong(TIMEOUT, 300L).commit())
      }
      assertEquals(300L, TextSecurePreferences.getPassphraseLockTimeout(context))
      assertCiphertextStored()
      roundTrip(300L)
    }
  }

  @Test
  fun `zero and signed integer bounds survive the existing integer wire field`() {
    for (value in listOf(Int.MIN_VALUE.toLong(), 0L, Int.MAX_VALUE.toLong())) {
      assertTrue(preferences.edit().putLong(TIMEOUT, value).commit())
      roundTrip(value)
    }
  }

  @Test
  fun `absent timeout stays absent and uses default zero`() {
    assertFalse(preferences.contains(TIMEOUT))
    assertEquals(0L, TextSecurePreferences.getPassphraseLockTimeout(context))
    assertTrue(TextSecurePreferences.getPreferencesToSaveToBackup(context).none { it.key == TIMEOUT })
    val frames = exportSnapshot().first
    assertTrue(frames.none { it.preference?.key == TIMEOUT })
    assertFalse(preferences.contains(TIMEOUT))
  }

  @Test
  fun `actual exporter emits timeout before modern key values and counts the snapshot`() {
    val absent = exportSnapshot()
    assertTrue(preferences.edit().putLong(TIMEOUT, 300L).commit())
    val (frames, event) = exportSnapshot()
    val timeout = frames.single { it.preference?.key == TIMEOUT }.preference!!
    assertEquals(300, timeout.integerValue)
    assertTrue(frames.indexOfFirst { it.preference != null } < frames.indexOfFirst { it.keyValue != null })
    assertEquals("modern value", frames.single { it.keyValue != null }.keyValue!!.stringValue)
    assertEquals(absent.second.count + 1, event.count)
    assertEquals(absent.second.estimatedTotalCount + 1, event.estimatedTotalCount)
    assertEquals(frames.size.toLong(), event.count)
    importPreference(timeout)
    assertEquals(300L, TextSecurePreferences.getPassphraseLockTimeout(context))
  }

  @Test
  fun `out of range longs reject before any header without modifying encrypted storage`() {
    for (value in listOf(Int.MIN_VALUE.toLong() - 1, Int.MAX_VALUE.toLong() + 1, Long.MIN_VALUE, Long.MAX_VALUE)) {
      assertTrue(preferences.edit().putLong(TIMEOUT, value).commit())
      val ciphertext = rawTimeout()
      assertCiphertextStored()
      assertThrows(TextSecurePreferences.InvalidPassphraseLockTimeoutException::class.java) {
        TextSecurePreferences.getPreferencesToSaveToBackup(context)
      }
      val bytes = ByteArrayOutputStream()
      val failure = assertThrows(FullBackupExporter.InvalidTimeoutException::class.java) {
        FullBackupExporter.transfer(context, attachmentSecret, mockk(), bytes, PASSPHRASE)
      }
      assertTrue(failure.cause is TextSecurePreferences.InvalidPassphraseLockTimeoutException)
      assertEquals(0, bytes.size())
      assertEquals(value, TextSecurePreferences.getPassphraseLockTimeout(context))
      assertEquals(ciphertext, rawTimeout())
      assertEquals("synthetic control", preferences.getString(CONTROL, null))
    }
  }

  @Test
  fun `compatibility is restricted to timeout integers and does not coerce other types`() {
    assertTrue(preferences.edit().putInt(CONTROL, 300).putString(TIMEOUT, "300").commit())
    assertEquals(-1L, preferences.getLong(CONTROL, -1))
    assertEquals(0L, TextSecurePreferences.getPassphraseLockTimeout(context))
    assertTrue(preferences.edit().putLong(CONTROL, 300L).commit())
    assertEquals(-1, preferences.getInt(CONTROL, -1))
  }

  @Test
  fun `file job reports nonfatal overflow and cleans only its temporary output`() {
    val directory = File(context.cacheDir, "timeout-${UUID.randomUUID()}").apply { assertTrue(mkdir()) }
    val retained = File(directory, "retained.backup").apply { writeText("synthetic retained backup") }
    val notification = prepareJob(false)
    mockkStatic(StorageUtil::class, BackupVerifier::class)
    every { StorageUtil.getOrCreateBackupDirectory() } returns directory
    shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    val events = mutableListOf<BackupEvent>()
    val rejectedOutputs = mutableListOf<File>()
    val subscription = EventBus.default.subscribe(BackupEvent::class.java) {
      events += it
      if (it.type == BackupEvent.Type.FINISHED) {
        rejectedOutputs += directory.listFiles()!!.filter { file -> file.name.endsWith(".tmp") }
        assertEquals(1, rejectedOutputs.size)
        assertEquals(0L, rejectedOutputs.single().length())
      }
    }
    try {
      val ciphertext = storeOverflow()
      val job = LocalBackupJob.Factory().create(Job.Parameters.Builder().build(), null).apply { setContext(context) }
      assertNonfatal(job.run())
      assertFailurePresentedOnce(events)
      assertFalse(rejectedOutputs.single().exists())
      assertEquals(listOf(retained.name), directory.list()!!.toList())
      assertEquals("synthetic retained backup", retained.readText())
      assertEquals(ciphertext, rawTimeout())
      verify(exactly = 1) { notification.close() }
      verify(exactly = 0) { BackupVerifier.verifyFile(any(), any(), any(), any()) }
      verify(exactly = 0) { BackupUtil.deleteOldBackups() }
    } finally {
      subscription.unsubscribe()
      assertTrue(retained.delete())
      assertTrue(directory.delete())
    }
  }

  @Test
  @Config(sdk = [36])
  fun `scoped job closes empty output and reports one nonfatal failure`() = runScopedOverflow()

  @Test
  @Config(sdk = [36])
  fun `provider refusal to delete does not turn rejection into success`() = runScopedOverflow(deleteResult = false)

  @Test
  @Config(sdk = [36])
  fun `provider delete security failure is suppressed on timeout rejection`() = runScopedOverflow(cleanupFailure = SecurityException("synthetic delete"))

  @Test
  @Config(sdk = [36])
  fun `provider lookup security failure is suppressed on timeout rejection`() = runScopedOverflow(lookupFailure = SecurityException("synthetic lookup"))

  @Test
  @Config(sdk = [36])
  fun `output close IOException is suppressed on timeout rejection`() = runScopedOverflow(closeFailure = IOException("synthetic close"))

  @Test
  @Config(sdk = [36])
  fun `output close SecurityException is suppressed on timeout rejection`() = runScopedOverflow(closeFailure = SecurityException("synthetic close"))

  @Test
  @Config(sdk = [36])
  fun `unrelated exporter runtime failure remains fatal`() = runScopedOverflow(openFailure = IllegalStateException("synthetic provider bug"))

  private fun exportSnapshot(): Pair<List<BackupFrame>, BackupEvent> {
    mockkStatic(SqlUtil::class, AvatarHelper::class, KeyValueDatabase::class, AppDependencies::class)
    val db = mockk<SQLiteDatabase>()
    val empty = mockk<Cursor>(relaxed = true)
    every { db.version } returns 1
    every { db.rawQuery(any(), null) } returns empty
    every { SqlUtil.getAllTables(db) } returns emptyList()
    every { AvatarHelper.getAvatarCount(context) } returns 0
    every { AvatarHelper.getAvatars(context) } returns emptyList()
    val keyValues = mockk<KeyValueDatabase>()
    val application = RuntimeEnvironment.getApplication()
    every { staticGetter<Application>(AppDependencies::class.java, "getApplication") } returns application
    every { KeyValueDatabase.getInstance(application) } returns keyValues
    every { keyValues.dataSet } returns KeyValueDataSet().apply { putString("synthetic-modern-key", "modern value") }
    every { staticGetter<List<String>>(SignalStore::class.java, "getKeysToIncludeInBackup") } returns listOf("synthetic-modern-key")
    val events = mutableListOf<BackupEvent>()
    val subscription = EventBus.default.subscribe(BackupEvent::class.java) { events += it }
    val bytes = ByteArrayOutputStream()
    try {
      FullBackupExporter.transfer(context, attachmentSecret, db, bytes, PASSPHRASE)
    } finally {
      subscription.unsubscribe()
    }
    val reader = BackupRecordInputStream(ByteArrayInputStream(bytes.toByteArray()), PASSPHRASE)
    val frames = mutableListOf<BackupFrame>()
    do {
      val frame = reader.readFrame()
      frames += frame
    } while (frame.end != true)
    return frames to events.single { it.type == BackupEvent.Type.FINISHED }
  }

  private fun prepareJob(scoped: Boolean): NotificationController {
    mockkStatic(BackupUtil::class, BackupFileIOError::class, NotificationChannels::class, AttachmentSecretProvider::class, SignalDatabase::class)
    val backup = mockk<BackupValues>()
    every { staticGetter<BackupValues>(SignalStore::class.java, "backup") } returns backup
    every { backup.v1BackupPassphrase } returns PASSPHRASE
    every { BackupUtil.isUserSelectionRequired(context) } returns scoped
    every { BackupUtil.getBackupFilenamePrefix() } returns "synthetic"
    every { BackupUtil.deleteOldBackups() } just runs
    every { BackupFileIOError.clearNotification(context) } just runs
    every { BackupFileIOError.postNotificationForException(context, any()) } just runs
    every { NotificationChannels.supported() } returns false
    val constructor = NotificationChannels::class.java.getDeclaredConstructor(Application::class.java).apply { isAccessible = true }
    every { NotificationChannels.getInstance() } returns constructor.newInstance(RuntimeEnvironment.getApplication())
    // Its default channel must initialize outside MockK's recording context.
    Class.forName(GenericForegroundService::class.java.name, true, GenericForegroundService::class.java.classLoader)
    mockkStatic(GenericForegroundService::class)
    val notification = mockk<NotificationController>(relaxUnitFun = true)
    val startForeground = GenericForegroundService::class.java.getDeclaredMethod("startForegroundTask", Context::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType)
    every { startForeground.invoke(null, context, any<String>(), any<String>(), any<Int>()) as NotificationController } returns notification
    val provider = mockk<AttachmentSecretProvider>()
    every { AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore) } returns provider
    every { provider.orCreateAttachmentSecret } returns attachmentSecret
    every { staticGetter<SQLiteDatabase>(SignalDatabase::class.java, "getBackupDatabase") } returns mockk()
    return notification
  }

  private fun runScopedOverflow(
    deleteResult: Boolean = true,
    cleanupFailure: SecurityException? = null,
    lookupFailure: SecurityException? = null,
    closeFailure: Exception? = null,
    openFailure: RuntimeException? = null,
  ) {
    val resolver = mockk<ContentResolver>()
    context = object : ContextWrapper(context) {
      override fun getContentResolver(): ContentResolver = resolver
    }
    val notification = prepareJob(true)
    val directoryUri = Uri.parse("content://synthetic-backup/tree/owned")
    val outputUri = Uri.parse("content://synthetic-backup/document/owned")
    val directory = mockk<DocumentFile>()
    val temporary = mockk<DocumentFile>()
    settings.signalBackupDirectory = directoryUri
    mockkStatic(DocumentFile::class, BackupVerifier::class)
    every { DocumentFile.fromTreeUri(context, directoryUri) } returns directory
    every { directory.canWrite() } returns true
    every { directory.listFiles() } returns emptyArray()
    val temporaryName = slot<String>()
    every { directory.createFile("application/octet-stream", capture(temporaryName)) } returns temporary
    every { directory.findFile(any()) } answers {
      if (temporaryName.isCaptured && firstArg<String>() == temporaryName.captured) {
        if (lookupFailure != null) throw lookupFailure
        temporary
      } else {
        null
      }
    }
    every { temporary.uri } returns outputUri
    val output = object : ByteArrayOutputStream() {
      var closes = 0
      override fun close() {
        closes++
        if (closeFailure != null) throw closeFailure
        super.close()
      }
    }
    every { resolver.openOutputStream(outputUri) } answers {
      if (openFailure != null) throw openFailure
      output
    }
    every { temporary.delete() } answers {
      assertEquals(if (openFailure == null) 1 else 0, output.closes)
      if (cleanupFailure != null) throw cleanupFailure
      deleteResult
    }
    val events = mutableListOf<BackupEvent>()
    val subscription = EventBus.default.subscribe(BackupEvent::class.java) { events += it }
    try {
      val ciphertext = storeOverflow()
      val job = LocalBackupJobApi29.Factory().create(Job.Parameters.Builder().build(), null).apply { setContext(context) }
      val result = job.run()
      if (openFailure != null) {
        assertTrue(result.isFailure)
        assertSame(openFailure, runtimeException(result))
        assertTrue(events.isEmpty())
        verify(exactly = 0) { BackupFileIOError.postNotificationForException(context, any()) }
      } else {
        assertNonfatal(result)
        val failure = assertFailurePresentedOnce(events)
        for (suppressed in listOfNotNull(cleanupFailure, lookupFailure, closeFailure)) {
          assertTrue(failure.suppressed.any { it === suppressed })
        }
        assertEquals(1, output.closes)
        assertEquals(0, output.size())
        assertThrows(IOException::class.java) { BackupRecordInputStream(ByteArrayInputStream(output.toByteArray()), PASSPHRASE) }
      }
      assertEquals(ciphertext, rawTimeout())
      assertEquals(Int.MAX_VALUE.toLong() + 1, TextSecurePreferences.getPassphraseLockTimeout(context))
      verify(exactly = 1) { notification.close() }
      verify(exactly = 0) { temporary.renameTo(any()) }
      verify(exactly = 0) { resolver.openInputStream(any()) }
      verify(exactly = 0) { BackupVerifier.verifyFile(any(), any(), any(), any()) }
      verify(exactly = 0) { BackupUtil.deleteOldBackups() }
      verify(exactly = if (lookupFailure == null) 1 else 0) { temporary.delete() }
    } finally {
      subscription.unsubscribe()
    }
  }

  private fun storeOverflow(): Any? {
    assertTrue(preferences.edit().putLong(TIMEOUT, Int.MAX_VALUE.toLong() + 1).commit())
    assertCiphertextStored()
    return rawTimeout()
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> staticGetter(type: Class<*>, name: String): T {
    // Match the Java callers' @JvmStatic bridges, not Kotlin's companion accessors.
    return type.getDeclaredMethod(name).invoke(null) as T
  }

  private fun assertNonfatal(result: Job.Result) {
    assertTrue(result.isFailure)
    assertFalse(result.isRetry)
    assertFalse(result.isSuccess)
    assertNull("JobRunner must have no RuntimeException to rethrow", runtimeException(result))
  }

  private fun runtimeException(result: Job.Result): Any? = Job.Result::class.java.getDeclaredMethod("getException").apply { isAccessible = true }.invoke(result)

  private fun assertFailurePresentedOnce(events: List<BackupEvent>): IOException {
    val finished = events.single()
    assertEquals(BackupEvent.Type.FINISHED, finished.type)
    assertEquals(0L, finished.count)
    assertEquals(0L, finished.estimatedTotalCount)
    val failure = slot<IOException>()
    verify(exactly = 1) { BackupFileIOError.postNotificationForException(context, capture(failure)) }
    assertTrue(failure.captured is FullBackupExporter.InvalidTimeoutException)
    assertTrue(failure.captured.cause is TextSecurePreferences.InvalidPassphraseLockTimeoutException)
    return failure.captured
  }

  private fun rawTimeout(): Any? = context.getSharedPreferences(SecurePreferenceManager.getSecurePreferencesName(), Context.MODE_PRIVATE).all[TIMEOUT]

  private fun assertCiphertextStored() {
    assertTrue(rawTimeout() is String)
    assertFalse(rawTimeout() == "300")
  }

  private fun roundTrip(value: Long) {
    val selected = TextSecurePreferences.getPreferencesToSaveToBackup(context).single { it.key == TIMEOUT }
    assertEquals(value.toInt(), selected.integerValue)
    val bytes = ByteArrayOutputStream()
    val writer = BackupFrameOutputStream(bytes, PASSPHRASE)
    try {
      writer.write(selected)
    } finally {
      writer.close()
    }
    val decoded = BackupRecordInputStream(ByteArrayInputStream(bytes.toByteArray()), PASSPHRASE).readFrame().preference!!
    assertEquals(selected, decoded)
    importPreference(decoded)
    assertEquals(value, TextSecurePreferences.getPassphraseLockTimeout(context))
    assertEquals("synthetic control", preferences.getString(CONTROL, null))
    assertCiphertextStored()
  }

  private fun importPreference(preference: SharedPreference) {
    assertTrue(preferences.edit().remove(TIMEOUT).commit())
    // Exercise the real handler without the unrelated post-restore logging/settings lifecycle.
    val method = FullBackupImporter::class.java.getDeclaredMethod("processPreference", Context::class.java, SharedPreference::class.java)
    method.isAccessible = true
    assertEquals(true, method.invoke(null, context, preference))
  }

  companion object {
    private const val TIMEOUT = TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT
    private const val CONTROL = "timeout-backup-synthetic-control"
    private const val PASSPHRASE = "000000000000000000000000000000"
  }
}
