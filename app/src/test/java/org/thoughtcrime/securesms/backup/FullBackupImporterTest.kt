package org.thoughtcrime.securesms.backup

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.backup.proto.SharedPreference
import org.thoughtcrime.securesms.crypto.EncryptedPreferences
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import java.io.IOException
import java.lang.reflect.InvocationTargetException

class FullBackupImporterTest {

  @After
  fun cleanupMocks() = unmockkAll()

  @Test
  fun `restore provenance accepts only committed values in the original preference store`() {
    val context = mockk<Context>()
    val preferences = mockk<SharedPreferences>()
    val foreign = mockk<EncryptedPreferences>()
    val editor = mockk<SharedPreferences.Editor>()
    val foreignEditor = mockk<EncryptedPreferences.Editor>()
    mockkStatic(SecurePreferenceManager::class)
    mockkStatic(EncryptedPreferences::class)
    every { SecurePreferenceManager.getSecurePreferences(context) } returns preferences
    every { EncryptedPreferences.create(context, "unrelated-file") } returns foreign
    every { preferences.edit() } returns editor
    every { editor.putBoolean("pref_read_receipts", false) } returns editor
    every { editor.commit() } returns true
    every { foreign.edit() } returns foreignEditor
    every { foreignEditor.putBoolean("pref_read_receipts", false) } returns foreignEditor
    every { foreignEditor.commit() } returns true
    val frame = SharedPreference.Builder()
      .file_(SecurePreferenceManager.getSecurePreferencesName())
      .key("pref_read_receipts")
      .booleanValue(false)
      .build()

    assertTrue(importPreference(context, frame))
    assertFalse(importPreference(context, frame.newBuilder().file_("unrelated-file").build()))
    assertFalse(importPreference(context, SharedPreference.Builder().file_(frame.file_).key(frame.key).build()))
    verify(exactly = 1) { editor.commit() }
    verify(exactly = 1) { foreignEditor.commit() }
  }

  @Test
  fun `failed preference persistence aborts instead of authorizing legacy migration`() {
    val context = mockk<Context>()
    val preferences = mockk<SharedPreferences>()
    val editor = mockk<SharedPreferences.Editor>()
    mockkStatic(SecurePreferenceManager::class)
    every { SecurePreferenceManager.getSecurePreferences(context) } returns preferences
    every { preferences.edit() } returns editor
    every { editor.putBoolean("pref_read_receipts", false) } returns editor
    every { editor.commit() } returns false
    val frame = SharedPreference.Builder()
      .file_(SecurePreferenceManager.getSecurePreferencesName())
      .key("pref_read_receipts")
      .booleanValue(false)
      .build()

    val failure = assertThrows(InvocationTargetException::class.java) { importPreference(context, frame) }
    assertTrue(failure.cause is IOException)
  }

  private fun importPreference(context: Context, frame: SharedPreference): Boolean {
    val method = FullBackupImporter::class.java.getDeclaredMethod("processPreference", Context::class.java, SharedPreference::class.java)
    method.isAccessible = true
    return method.invoke(null, context, frame) as Boolean
  }

  @Test
  fun `computeTableOrder - empty`() {
    val order = FullBackupImporter.computeTableDropOrder(mapOf())

    assertEquals(listOf<String>(), order)
  }

  /**
   * A B C
   */
  @Test
  fun `computeTableOrder - no dependencies`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf(),
        "B" to setOf(),
        "C" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   * A
   * |
   * B
   * |
   * C
   */
  @Test
  fun `computeTableOrder - single chain`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("B"),
        "B" to setOf("C")
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   *   ┌──A──┐    B   C
   * ┌─D   ┌─E─┐
   * F     G   H
   */
  @Test
  fun `computeTableOrder - complex 1`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D", "E"),
        "B" to setOf(),
        "C" to setOf(),
        "D" to setOf("F"),
        "E" to setOf("G", "H"),
        "F" to setOf(),
        "G" to setOf(),
        "H" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   *   ┌────A────┐
   *   │    |    │
   * ┌─B─┐  C  ┌─D─┐
   * │   │  |  │   │
   * E   F  G  H   I
   */
  @Test
  fun `computeTableOrder - complex 2`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("B", "C", "D"),
        "B" to setOf("E", "F"),
        "C" to setOf("G"),
        "D" to setOf("H", "I"),
        "E" to setOf(),
        "F" to setOf(),
        "G" to setOf(),
        "H" to setOf(),
        "I" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I"), order)
  }

  /**
   * ┌─A─┐  B  ┌─C─┐
   * │   │  |  │   │
   * D   E  F  G   H
   */
  @Test
  fun `computeTableOrder - multiple roots`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D", "E"),
        "B" to setOf("F"),
        "C" to setOf("G", "H"),
        "D" to setOf(),
        "E" to setOf(),
        "F" to setOf(),
        "G" to setOf(),
        "H" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   * ┌─A─┐  B  ┌─C─┐
   * │   │  |  │   │
   * D   E  D  D   E
   */
  @Test
  fun `computeTableOrder - multiple roots, dupes across graphs`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D", "E"),
        "B" to setOf("D"),
        "C" to setOf("D", "E"),
        "D" to setOf(),
        "E" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E"), order)
  }

  /**
   * A  B
   * │  │
   * D  C
   *    │
   *    D
   */
  @Test
  fun `computeTableOrder - multiple roots, new dependencies across roots`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D"),
        "B" to setOf("C"),
        "C" to setOf("D"),
        "D" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D"), order)
  }
}
