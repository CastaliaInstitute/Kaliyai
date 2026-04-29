package com.kali.nethunter.mcpchat.data

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.PriorityQueue

/**
 * Read-only SQLite corpus produced by `python -m kaliyai_rag.cli export-sqlite` (kaliyai-rag/).
 * Place the .db file on removable storage (SD card) and set the path in settings.
 *
 * Search uses full scan + cosine similarity (no ANN index), suitable for modest corpora on-device.
 */
class OfflineRagStore private constructor(private val db: SQLiteDatabase) {

    data class Hit(
        val body: String,
        val metaJson: String,
        val similarity: Float,
    )

    fun close() {
        runCatching { db.close() }
    }

    /**
     * @param queryEmbedding raw embedding from Gemini embedContent (same model/dim as corpus)
     * @param domain optional filter: meta JSON field `domain` must equal this string
     */
    fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        domain: String?,
    ): List<Hit> {
        val q = VectorMath.l2Normalize(queryEmbedding)
        val k = topK.coerceIn(1, 64)
        val pq = PriorityQueue<Hit>(k + 1, compareBy { it.similarity })
        val sql = "SELECT body, meta, embedding_dim, embedding FROM chunks"
        db.rawQuery(sql, null).use { c ->
            val iBody = c.getColumnIndexOrThrow("body")
            val iMeta = c.getColumnIndexOrThrow("meta")
            val iDim = c.getColumnIndexOrThrow("embedding_dim")
            val iEmb = c.getColumnIndexOrThrow("embedding")
            while (c.moveToNext()) {
                val metaStr = c.getString(iMeta) ?: "{}"
                if (domain != null) {
                    val d = runCatching { JSONObject(metaStr).optString("domain", "") }.getOrDefault("")
                    if (d != domain) continue
                }
                val dim = c.getInt(iDim)
                val blob = c.getBlob(iEmb) ?: continue
                val vec = VectorMath.floatsFromBlob(blob)
                if (vec.size != dim || vec.size != q.size) {
                    Log.w(TAG, "skip row: dim mismatch (row=${vec.size}, query=${q.size})")
                    continue
                }
                val nv = VectorMath.l2Normalize(vec)
                val sim = VectorMath.dot(q, nv).coerceIn(-1f, 1f)
                val body = c.getString(iBody).orEmpty()
                pq.offer(Hit(body = body, metaJson = metaStr, similarity = sim))
                if (pq.size > k) {
                    pq.poll()
                }
            }
        }
        return pq.sortedByDescending { it.similarity }
    }

    companion object {
        private const val TAG = "OfflineRagStore"

        /** Opens [path] read-only. Returns null if missing or invalid. */
        fun openReadOnly(path: String): OfflineRagStore? {
            val f = File(path.trim())
            if (!f.isFile || !f.canRead()) {
                Log.w(TAG, "not a readable file: $path")
                return null
            }
            return runCatching {
                val db = SQLiteDatabase.openDatabase(
                    f.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='chunks'",
                    null,
                ).use { c ->
                    if (!c.moveToFirst()) {
                        db.close()
                        return null
                    }
                }
                OfflineRagStore(db)
            }.getOrElse { e ->
                Log.e(TAG, "open failed: ${e.message}")
                null
            }
        }
    }
}
