package com.kali.nethunter.mcpchat.eval

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.kali.nethunter.mcpchat.data.OfflineRagStore
import com.kali.nethunter.mcpchat.data.VectorMath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SQLite schema parity with [kaliyai_rag.sqlite_export] / [OfflineRagStore]: cosine search + domain filter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.DEFAULT_MANIFEST_NAME)
class RagOfflineStoreEvalTest {

    private var dbFile: File? = null

    @After
    fun tearDown() {
        dbFile?.delete()
        dbFile = null
    }

    @Test
    fun search_returnsHighestCosine_and_domainFilter() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = File(ctx.cacheDir, "rag_offline_eval_${System.nanoTime()}.db")
        val dim = 32
        val a = FloatArray(dim) { if (it == 0) 1f else 0f }
        val b = FloatArray(dim) { if (it == 1) 1f else 0f }
        val blobA = floatsToLeBlob(a)
        val blobB = floatsToLeBlob(b)

        SQLiteDatabase.openOrCreateDatabase(dbFile!!, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE chunks (
                  _id INTEGER PRIMARY KEY AUTOINCREMENT,
                  chunk_index INTEGER NOT NULL DEFAULT 0,
                  body TEXT NOT NULL,
                  meta TEXT NOT NULL,
                  embedding_dim INTEGER NOT NULL,
                  embedding BLOB NOT NULL
                )
                """.trimIndent(),
            )
            db.insert(
                "chunks",
                null,
                ContentValues().apply {
                    put("chunk_index", 0)
                    put("body", "chunk A governance")
                    put("meta", """{"source":"eval","domain":"governance"}""")
                    put("embedding_dim", dim)
                    put("embedding", blobA)
                },
            )
            db.insert(
                "chunks",
                null,
                ContentValues().apply {
                    put("chunk_index", 1)
                    put("body", "chunk B other")
                    put("meta", """{"source":"eval","domain":"other"}""")
                    put("embedding_dim", dim)
                    put("embedding", blobB)
                },
            )
        }

        val store = OfflineRagStore.openReadOnly(dbFile!!.absolutePath)
            ?: throw AssertionError("failed to open eval db")
        try {
            val hitsAll = store.search(a, topK = 2, domain = null)
            assertEquals(2, hitsAll.size)
            assertTrue(hitsAll[0].similarity >= hitsAll[1].similarity)
            assertEquals(1.0f, hitsAll[0].similarity, 0.001f)

            val hitsGov = store.search(a, topK = 4, domain = "governance")
            assertEquals(1, hitsGov.size)
            assertTrue(hitsGov[0].body.contains("governance"))

            val hitsMiss = store.search(a, topK = 4, domain = "nonexistent_domain_xyz")
            assertEquals(0, hitsMiss.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun vectorMath_matchesOfflineBlobLayout() {
        val v = floatArrayOf(0.5f, -0.25f, 2f)
        val blob = floatsToLeBlob(v)
        val back = VectorMath.floatsFromBlob(blob)
        assertEquals(v.size, back.size)
        for (i in v.indices) {
            assertEquals(v[i], back[i], 1e-5f)
        }
        assertEquals(1f, VectorMath.dot(VectorMath.l2Normalize(v), VectorMath.l2Normalize(v)), 0.02f)
    }

    private fun floatsToLeBlob(values: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (x in values) {
            bb.putFloat(x)
        }
        return bb.array()
    }
}
