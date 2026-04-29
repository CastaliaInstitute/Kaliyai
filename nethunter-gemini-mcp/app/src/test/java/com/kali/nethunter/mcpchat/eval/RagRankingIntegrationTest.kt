package com.kali.nethunter.mcpchat.eval

import androidx.test.core.app.ApplicationProvider
import com.kali.nethunter.mcpchat.data.OfflineRagStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Integration test: [OfflineRagStore] cosine ranking against the committed multi-chunk fixture DB
 * (built by `python -m kaliyai_rag.cli refresh-ranking-fixture`). Dim must match Python fixture (64).
 *
 * Does **not** call Gemini — validates JVM SQLite + [VectorMath] parity with kaliyai-rag/sqlite_search.py.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.DEFAULT_MANIFEST_NAME)
class RagRankingIntegrationTest {

    private var tmpDb: File? = null

    @After
    fun tearDown() {
        tmpDb?.delete()
        tmpDb = null
    }

    private fun loadQueryFloats(label: String): Pair<FloatArray, Int> {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/ranking/query_vectors.json")
            ?: error("Missing test resource fixtures/ranking/query_vectors.json")
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        val dim = json.getInt("embedding_dim")
        val arr = json.getJSONArray(label)
        assertEquals(dim, arr.length())
        val out = FloatArray(dim) { i -> arr.getDouble(i).toFloat() }
        return out to dim
    }

    private fun openFixtureStore(): OfflineRagStore {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inp = javaClass.classLoader!!.getResourceAsStream("fixtures/ranking/kaliyai_ranking.db")
            ?: error("Missing test resource fixtures/ranking/kaliyai_ranking.db")
        tmpDb = File(ctx.cacheDir, "kaliyai_ranking_${System.nanoTime()}.db")
        inp.use { input -> tmpDb!!.outputStream().use { input.copyTo(it) } }
        return OfflineRagStore.openReadOnly(tmpDb!!.absolutePath)
            ?: error("failed to open ranking fixture db")
    }

    @Test
    fun rankOne_matchesGovChunk_whenQueryMatchesChunkZeroEmbedding() {
        val (vec, _) = loadQueryFloats("same_as_chunk_0")
        val store = openFixtureStore()
        try {
            val hits = store.search(vec, topK = 5, domain = null)
            assertEquals(5, hits.size)
            val meta = JSONObject(hits[0].metaJson)
            assertEquals("gov-0", meta.getString("fixture_id"))
            assertTrue(hits[0].body.contains("GOVERNANCE_TARGET"))
        } finally {
            store.close()
        }
    }

    @Test
    fun domainFilter_excludesAll_returnsEmpty() {
        val (vec, _) = loadQueryFloats("same_as_chunk_0")
        val store = openFixtureStore()
        try {
            val hits = store.search(vec, topK = 5, domain = "nonexistent_domain_xyz")
            assertTrue(hits.isEmpty())
        } finally {
            store.close()
        }
    }
}
