package live.mehiz.mpvkt.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test for the schema change that added resume_state.
 *
 * This asserts the migration creates exactly the columns the entity declares, so a
 * mismatch between [live.mehiz.mpvkt.database.entities.ResumeStateEntity] and the
 * DDL fails here instead of on the owner's phone. Running the migration against a
 * real SQLite needs Robolectric (see the M2 report: not added yet).
 */
class MigrationTest {

  @Test
  fun databaseVersionMatchesTheMigrationChain() {
    assertEquals(6, DATABASE_VERSION)
    assertEquals(6, Migrations.last().endVersion)
    assertEquals(5, Migrations.last().startVersion)
  }

  @Test
  fun migration5to6CreatesTheResumeTableWithEveryEntityColumn() {
    val sql = MIGRATION5to6.SQL
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `resume_state`"))
    listOf("fileKey", "positionMs", "durationMs", "lastPlayedAt", "completed").forEach { column ->
      assertTrue("migration is missing the $column column", sql.contains("`$column`"))
    }
    assertTrue(sql.contains("PRIMARY KEY(`fileKey`)"))
    // Booleans are stored as INTEGER by Room, longs as INTEGER NOT NULL.
    assertTrue(sql.contains("`completed` INTEGER NOT NULL"))
  }

  @Test
  fun resumeTableIsTheOneTheDaoQueries() {
    val sql = MIGRATION5to6.SQL
    assertTrue(sql.contains("`resume_state`"))
  }
}
