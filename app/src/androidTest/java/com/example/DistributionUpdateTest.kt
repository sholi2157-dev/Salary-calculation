package com.example

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.WorkUpdates
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only on disposable emulator, against the real non-debuggable release package. */
@RunWith(AndroidJUnit4::class)
class DistributionUpdateTest {
    @Test fun retainedAcrossRealPackageUpdate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("com.aistudio.worktracker.qztvdw.distribution", context.packageName)
        assertTrue(BuildConfig.LOCAL_DISTRIBUTION)
        assertFalse(BuildConfig.ACCOUNTS_ENABLED || BuildConfig.CLOUD_SYNC_ENABLED || BuildConfig.VERSIONED_SYNC_ENABLED)
        assertEquals(0, context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
        @Suppress("DEPRECATION") val installedCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        val stage = InstrumentationRegistry.getArguments().getString("stage")!!
        val db = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        val dao = db.workDao()
        val evidence = context.getSharedPreferences("update_fixture_evidence", 0)
        if (stage == "seed") {
            context.getSharedPreferences("personal_ai_setup", 0).edit().putBoolean("offered_local_device", true).commit()
            assertTrue(dao.getEntriesList().isEmpty())
            dao.insertCategory(WorkCategory(name = "ILS category", defaultRate = 40.0))
            dao.insertCategory(WorkCategory(name = "USD category", defaultRate = 45.75))
            dao.insertWorker(WorkerDirectory(name = "Synthetic worker"))
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "USD category"))
            dao.setLocalPreference(WorkLocalPreference("default_currency", "$"))
            dao.setLocalPreference(WorkLocalPreference("categoryCurrency:USD category", "$"))
            dao.insertEntry(WorkEntry(category = "ILS category", date = 1700000000000, isTimeRange = false,
                hours = 2.0, hourlyRate = 40.0, totalEarnings = 79.97, notes = "saved imported amount", isPaid = true))
            dao.insertEntry(WorkEntry(category = "USD category", date = 1700100000000, isTimeRange = true,
                startTime = "22:00", endTime = "06:00", hours = 7.5, hourlyRate = 45.75, totalEarnings = 343.12,
                currency = "$", notes = "overnight with break"))
            dao.insertEntry(WorkEntry(category = "USD category", date = 1700200000000, isTimeRange = false,
                hours = 2.0, hourlyRate = 50.0, totalEarnings = 100.0, currency = "$", notes = "group",
                isGroupShift = true, employerRate = 65.0, workerRate = 30.0,
                groupWorkersJson = """[{"name":"Synthetic worker","hours":3,"isPaid":false,"workerRate":31.25,"employerRate":67.5}]"""))
            context.getExternalFilesDir(null)!!.mkdirs()
            assertTrue(evidence.edit().putString("snapshot", dao.exportSnapshot())
                .putString("identities", db.syncDao().pending().toString()).putInt("versionA", installedCode).commit())
        } else {
            assertEquals(evidence.getString("snapshot", null), dao.exportSnapshot())
            assertEquals(evidence.getString("identities", null), db.syncDao().pending().toString())
            File(context.getExternalFilesDir(null), "$stage-before.json").writeText(dao.exportSnapshot())
            assertEquals(3, dao.getEntriesList().size)
            val old = WorkBackup.decode(evidence.getString("snapshot", null)!!)
            assertEquals(0, dao.importBackup(old))
            assertEquals(evidence.getString("snapshot", null), dao.exportSnapshot())
            if (stage == "updater") {
                val bytes = File(context.getExternalFilesDir(null), "candidate-b.apk").readBytes()
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                val nextCode = installedCode + 1L
                val manifest = """{"versionCode":$nextCode,"versionName":"test B","apkUrl":"${WorkUpdates.BASE}download/private-test/candidate-b.apk","sha256":"$hash"}"""
                var status = 200
                var manifestBody = manifest
                val transport = OkHttpClient.Builder().addInterceptor { chain ->
                    val payload = if (chain.request().url.toString() == WorkUpdates.MANIFEST) manifestBody.toByteArray() else bytes
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                        .message("Synthetic transport; no public release").body(payload.toResponseBody()).build()
                }.build()
                val release = WorkUpdates.checkWithClient(context, transport)!!
                assertEquals(nextCode, release.code)
                val file = WorkUpdates.downloadWithClient(context, release, transport)
                assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
                val intent = WorkUpdates.installerIntent(context, file)
                assertEquals("content", intent.data!!.scheme)
                assertEquals("application/vnd.android.package-archive", intent.type)
                assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertNotNull(context.packageManager.resolveActivity(intent, 0))
                context.contentResolver.openInputStream(intent.data!!)!!.use { assertTrue(it.read() >= 0) }
                try { WorkUpdates.downloadWithClient(context, release.copy(sha256 = "0".repeat(64)), transport); fail("wrong hash accepted") } catch (_: IllegalStateException) { }
                try { WorkUpdates.downloadWithClient(context, release.copy(code = nextCode + 1), transport); fail("wrong version accepted") } catch (_: IllegalStateException) { }
                assertFalse(context.cacheDir.listFiles()!!.any { it.name.endsWith(".pending.apk") })
                manifestBody = manifest.replace("\"versionCode\":$nextCode", "\"versionCode\":$installedCode")
                assertNull(WorkUpdates.checkWithClient(context, transport))
                status = 404
                assertNull(WorkUpdates.checkWithClient(context, transport))
                status = 503
                try { WorkUpdates.checkWithClient(context, transport); fail("server failure accepted") } catch (_: IllegalStateException) { }
                assertEquals(evidence.getString("snapshot", null), dao.exportSnapshot())
            } else if (stage == "verify") {
                assertTrue(installedCode > evidence.getInt("versionA", Int.MAX_VALUE))
                val original = dao.getEntriesList().first()
                dao.updateEntry(WorkEntryEdits.apply(original, original.copy(notes = "edited after update")))
                assertEquals(original.totalEarnings, dao.getEntryById(original.id)!!.totalEarnings, 0.0)
                val exported = WorkBackup.decode(dao.exportSnapshot())
                assertEquals(3, exported.entries.size)
                assertEquals("$", exported.localPreferences["default_currency"])
                assertTrue(exported.entries.any { it.notes == "edited after update" })
                assertEquals(mapOf("₪" to 79.97, "$" to 443.12), WorkMoney.totals(exported.entries))
                File(context.getExternalFilesDir(null), "verify-after-edit.json").writeText(dao.exportSnapshot())
            } else assertEquals("restart", stage)
        }
    }
}
