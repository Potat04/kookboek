package nl.potat04.kookboek

import nl.potat04.kookboek.data.AppRelease
import nl.potat04.kookboek.data.UpdateApk
import nl.potat04.kookboek.data.UpdateError
import nl.potat04.kookboek.data.UpdateFailure
import nl.potat04.kookboek.data.UpdatePackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateApkTest {
    @get:Rule val files = TemporaryFolder()
    private val release = AppRelease("v1.0.3", "", "", 3, null)
    private val installed = UpdatePackage("nl.potat04.kookboek", 3, "1.0.2", 33, setOf("release"), setOf("release"))
    private val incoming = installed.copy(versionCode = 4, versionName = "1.0.3")

    @Test fun `accepts newer release with matching signer`() {
        UpdateApk.verifyPackage(installed, incoming, release, 36)
    }

    @Test fun `rejects another app and misleading or old versions`() {
        listOf(
            incoming.copy(name = "another.app"),
            incoming.copy(versionCode = 3),
            incoming.copy(versionCode = 2),
            incoming.copy(versionName = "1.0.4"),
            incoming.copy(versionName = "malformed"),
            incoming.copy(minSdk = 37),
        ).forEach { candidate ->
            assertEquals(UpdateError.INVALID_APK, assertThrows(UpdateFailure::class.java) {
                UpdateApk.verifyPackage(installed, candidate, release, 36)
            }.reason)
        }
    }

    @Test fun `rejects debug signing and missing signing data`() {
        listOf(
            incoming.copy(signers = setOf("debug"), signingHistory = setOf("debug")),
            incoming.copy(signers = emptySet(), signingHistory = emptySet()),
        ).forEach { candidate ->
            assertEquals(UpdateError.SIGNATURE, assertThrows(UpdateFailure::class.java) {
                UpdateApk.verifyPackage(installed, candidate, release, 36)
            }.reason)
        }
    }

    @Test fun `accepts forward signing rotation but not rollback`() {
        val rotated = incoming.copy(signers = setOf("new"), signingHistory = setOf("release", "new"))
        UpdateApk.verifyPackage(installed, rotated, release, 36)
        assertEquals(UpdateError.SIGNATURE, assertThrows(UpdateFailure::class.java) {
            UpdateApk.verifyPackage(installed.copy(signers = setOf("new")), incoming, release, 36)
        }.reason)
    }

    @Test fun `multiple signers must match exactly`() {
        val multiple = installed.copy(signers = setOf("one", "two"))
        UpdateApk.verifyPackage(multiple, incoming.copy(signers = setOf("two", "one")), release, 36)
        assertEquals(UpdateError.SIGNATURE, assertThrows(UpdateFailure::class.java) {
            UpdateApk.verifyPackage(multiple, incoming.copy(signers = setOf("one")), release, 36)
        }.reason)
    }

    @Test fun `verifies bytes against GitHub checksum and rejects corrupted or partial files`() {
        val file = files.newFile().apply { writeText("abc") }
        val hashed = release.copy(sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        UpdateApk.verifyFile(file, hashed)
        file.writeText("abd")
        assertEquals(UpdateError.INVALID_APK, assertThrows(UpdateFailure::class.java) {
            UpdateApk.verifyFile(file, hashed)
        }.reason)
        file.writeText("ab")
        assertThrows(UpdateFailure::class.java) { UpdateApk.verifyFile(file, release) }
        file.delete()
        assertThrows(UpdateFailure::class.java) { UpdateApk.verifyFile(file, release) }
    }
}
