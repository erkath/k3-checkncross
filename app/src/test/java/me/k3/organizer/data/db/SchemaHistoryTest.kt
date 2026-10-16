package me.k3.organizer.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards against changing a table without a migration.
 *
 * Room re-exports the schema of the current [OrganizerDatabase] version on every build. If an
 * entity changes but the version does not, that file silently changes too, and an installed app
 * crashes on start ("Room cannot verify the data integrity"). This test pins the schema hash of
 * every version that ever shipped, so such a change fails the build instead.
 *
 * Changing a table, the right way:
 *  1. bump `version` in [OrganizerDatabase] and add a migration (an `AutoMigration` or a `Migration`);
 *  2. build: Room writes `<new version>.json` next to the old ones;
 *  3. add the new version's identityHash from that file to [SHIPPED].
 */
class SchemaHistoryTest {
    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val dir = File("schemas/${OrganizerDatabase::class.java.name}")

    @Test
    fun shippedSchemas_neverChange() {
        for ((version, hash) in SHIPPED) {
            val file = File(dir, "$version.json")
            assertTrue("schema of version $version is missing: $file", file.exists())
            assertEquals(
                "Schema of version $version changed. Do not edit a shipped version: " +
                    "bump the database version and add a migration (see SchemaHistoryTest).",
                hash,
                identityHash(file),
            )
        }
    }

    @Test
    fun everyExportedVersion_isPinned() {
        val exported = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension.toInt() }.toSet()
        val unpinned = exported - SHIPPED.keys
        if (unpinned.isNotEmpty()) {
            fail("Schema versions $unpinned are not in SchemaHistoryTest.SHIPPED; add their identityHash there.")
        }
    }

    private fun identityHash(file: File): String =
        Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"").find(file.readText())?.groupValues?.get(1)
            ?: error("no identityHash in $file")

    companion object {
        /** Version → identityHash from `app/schemas/.../<version>.json`. Append only. */
        val SHIPPED = mapOf(
            1 to "eb630d9bd0881c457e11a5b829064e66",
            2 to "c60302ccc9cb888b440f917ceff7c700",
        )
    }
}
