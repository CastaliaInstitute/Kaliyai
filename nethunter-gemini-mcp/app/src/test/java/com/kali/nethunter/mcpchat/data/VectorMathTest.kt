package com.kali.nethunter.mcpchat.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorMathTest {

    @Test
    fun l2Normalize_unitLength() {
        val v = floatArrayOf(3f, 4f)
        val n = VectorMath.l2Normalize(v)
        assertEquals(1f, VectorMath.dot(n, n), 1e-5f)
    }

    @Test
    fun floatsFromBlob_roundTrip() {
        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bb.putFloat(1.5f)
        bb.putFloat(-2f)
        val out = VectorMath.floatsFromBlob(bb.array())
        assertEquals(2, out.size)
        assertEquals(1.5f, out[0], 0f)
        assertEquals(-2f, out[1], 0f)
    }
}
