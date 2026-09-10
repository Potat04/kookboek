package nl.potat04.kookboek

import nl.potat04.kookboek.data.Backup
import nl.potat04.kookboek.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the backup that decide things, tested without Android: which file wins
 * a merge, which files go when the folder gets too full, and which names out of a zip
 * are allowed anywhere near the images directory.
 */
class BackupTest {

    @Test
    fun `the file name carries the day it was written`() {
        // The shape is the point: sortable, and never a translated month name, whatever
        // language the app happens to be in when the daily job fires.
        val name = Backup.fileName(1_788_004_800_000L)
        assertTrue(name, Backup.isBackupName(name))
        assertTrue(name, name.startsWith("kookboek-20") && name.endsWith(".zip"))
    }

    @Test
    fun `only our own daily files count as backups`() {
        assertTrue(Backup.isBackupName("kookboek-2026-09-10.zip"))
        assertFalse(Backup.isBackupName("kookboek-2026-09-10 (1).zip"))
        assertFalse(Backup.isBackupName("kookboek.zip"))
        assertFalse(Backup.isBackupName("holiday-photos.zip"))
        assertFalse(Backup.isBackupName("kookboek-2026-09-10.zip.tmp"))
    }

    @Test
    fun `a week of backups stays and the older ones go`() {
        val names = (1..10).map { "kookboek-2026-09-%02d.zip".format(it) }
        val stale = Backup.stale(names, keep = 7)
        assertEquals(
            listOf(
                "kookboek-2026-09-03.zip",
                "kookboek-2026-09-02.zip",
                "kookboek-2026-09-01.zip",
            ),
            stale,
        )
    }

    @Test
    fun `nothing else in the folder is ever touched`() {
        val names = listOf("taxes.pdf", "kookboek-2020-01-01.zip", "photos", "kookboek.zip")
        assertEquals(emptyList<String>(), Backup.stale(names, keep = 7))
        assertEquals(listOf("kookboek-2020-01-01.zip"), Backup.stale(names, keep = 0))
    }

    @Test
    fun `a name with a path in it never reaches the images directory`() {
        assertNull(Backup.imageName("../../databases/kookboek.db"))
        assertNull(Backup.imageName("nested/photo.jpg"))
        assertNull(Backup.imageName("nested\\photo.jpg"))
        assertNull(Backup.imageName(".."))
        assertNull(Backup.imageName("  "))
        assertEquals("abc-123.jpg", Backup.imageName("abc-123.jpg"))
    }

    @Test
    fun `a hand edit outranks the date it was saved`() {
        val saved = Recipe(title = "Soep", addedAt = 100)
        val edited = saved.copy(editedAt = 500)
        assertEquals(100L, Backup.stamp(saved))
        assertEquals(500L, Backup.stamp(edited))
        assertTrue(Backup.stamp(edited) > Backup.stamp(saved))
    }

    @Test
    fun `an untouched import keeps the date it came in on`() {
        // A recipe imported later but never edited still has to beat an older copy,
        // otherwise a restore would quietly drop everything added since the backup.
        val older = Recipe(title = "Soep", addedAt = 100)
        val newer = Recipe(title = "Soep", addedAt = 900)
        assertTrue(Backup.stamp(newer) > Backup.stamp(older))
    }
}
