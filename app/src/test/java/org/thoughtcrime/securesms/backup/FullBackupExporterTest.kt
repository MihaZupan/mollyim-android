package org.thoughtcrime.securesms.backup

import android.database.Cursor
import io.mockk.every
import io.mockk.mockk
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.thoughtcrime.securesms.database.MessageTable

class FullBackupExporterTest {

  @Test
  fun `started disappearing messages are excluded even with a long remaining lifetime`() {
    val db = mockk<SQLiteDatabase>()
    assertFalse(isNonExpiring(db, 1, System.currentTimeMillis(), 30L * 24 * 60 * 60 * 1000, 0))
  }

  @Test
  fun `original message is excluded when its latest edit has started disappearing`() {
    val db = mockk<SQLiteDatabase>()
    val cursor = mockk<Cursor>(relaxed = true)
    val values = mapOf(
      MessageTable.ID to 2L,
      MessageTable.EXPIRE_STARTED to System.currentTimeMillis(),
      MessageTable.EXPIRES_IN to 30L * 24 * 60 * 60 * 1000,
      MessageTable.LATEST_REVISION_ID to 0L
    )
    val columns = values.keys.toList()
    every { cursor.moveToFirst() } returns true
    every { cursor.getColumnIndexOrThrow(any()) } answers { columns.indexOf(firstArg()) }
    every { cursor.getLong(any()) } answers { values.getValue(columns[firstArg()]) }
    every { db.query(MessageTable.TABLE_NAME, any(), any(), any(), null, null, null) } returns cursor

    assertFalse(isNonExpiring(db, 1, 0, 0, 2))
  }

  private fun isNonExpiring(db: SQLiteDatabase, id: Long, started: Long, duration: Long, revision: Long): Boolean {
    val method = FullBackupExporter::class.java.getDeclaredMethod(
      "isNonExpiringMessage", SQLiteDatabase::class.java,
      Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
      Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
    )
    method.isAccessible = true
    return method.invoke(null, db, id, started, duration, revision) as Boolean
  }

  @Test
  fun `computeTableOrder - empty`() {
    val order = FullBackupExporter.computeTableOrder(mapOf())

    assertEquals(listOf<String>(), order)
  }

  /**
   * A B C
   */
  @Test
  fun `computeTableOrder - no dependencies`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "A" to setOf(),
        "B" to setOf(),
        "C" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   * C
   * |
   * B
   * |
   * A
   */
  @Test
  fun `computeTableOrder - single chain`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "C" to setOf("B"),
        "B" to setOf("A")
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   *   ┌──F──┐    G   H
   * ┌─B   ┌─E─┐
   * A     C   D
   */
  @Test
  fun `computeTableOrder - complex 1`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "F" to setOf("B", "E"),
        "B" to setOf("A"),
        "E" to setOf("C", "D"),
        "G" to setOf(),
        "H" to setOf(),
        "A" to setOf(),
        "C" to setOf(),
        "D" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   *   ┌────I────┐
   *   │    |    │
   * ┌─C─┐  E  ┌─H─┐
   * │   │  |  │   │
   * A   B  D  F   G
   */
  @Test
  fun `computeTableOrder - complex 2`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "I" to setOf("C", "E", "H"),
        "C" to setOf("A", "B"),
        "E" to setOf("D"),
        "H" to setOf("F", "G"),
        "A" to setOf(),
        "B" to setOf(),
        "D" to setOf(),
        "F" to setOf(),
        "G" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I"), order)
  }

  /**
   * ┌─C─┐  E  ┌─H─┐
   * │   │  |  │   │
   * A   B  D  F   G
   */
  @Test
  fun `computeTableOrder - multiple roots`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "C" to setOf("A", "B"),
        "E" to setOf("D"),
        "H" to setOf("F", "G"),
        "A" to setOf(),
        "B" to setOf(),
        "D" to setOf(),
        "F" to setOf(),
        "G" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   * ┌─C─┐  D  ┌─E─┐
   * │   │  |  │   │
   * A   B  A  A   B
   */
  @Test
  fun `computeTableOrder - multiple roots, dupes across graphs`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "C" to setOf("A", "B"),
        "D" to setOf("A"),
        "E" to setOf("A", "B"),
        "A" to setOf(),
        "B" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E"), order)
  }
}
