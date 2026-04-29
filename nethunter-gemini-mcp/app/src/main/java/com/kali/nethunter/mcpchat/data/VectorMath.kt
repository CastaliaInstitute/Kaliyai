package com.kali.nethunter.mcpchat.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Pure helpers for offline RAG (tests run on JVM without SQLite). */
object VectorMath {

    fun l2Normalize(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) {
            s += (x * x).toDouble()
        }
        val n = sqrt(s).toFloat().coerceAtLeast(1e-12f)
        return FloatArray(v.size) { i -> v[i] / n }
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "dim mismatch: ${a.size} vs ${b.size}" }
        var s = 0.0
        for (i in a.indices) {
            s += (a[i] * b[i]).toDouble()
        }
        return s.toFloat()
    }

    /** Gemini / Python export: little-endian float32 blob. */
    fun floatsFromBlob(blob: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        val n = blob.size / 4
        val out = FloatArray(n)
        for (i in 0 until n) {
            out[i] = bb.float
        }
        return out
    }
}
