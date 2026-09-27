package com.jonny.r5monitor

import com.jonny.r5monitor.ccapi.LiveViewFrameReader
import com.jonny.r5monitor.ccapi.MalformedFrameException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

class LiveViewFrameReaderTest {

    private fun frame(type: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(0x00)
        out.write(type)
        val n = payload.size
        out.write(n ushr 24 and 255); out.write(n ushr 16 and 255); out.write(n ushr 8 and 255); out.write(n and 255)
        out.write(payload)
        out.write(0xFF); out.write(0xFF)
        return out.toByteArray()
    }

    /** Hands out at most a few bytes per read, the way a chunked socket does. */
    private class Trickle(bytes: ByteArray) : InputStream() {
        private val inner = ByteArrayInputStream(bytes)
        override fun read() = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, minOf(len, 3))
    }

    @Test
    fun readsConsecutiveFramesAndThenEnds() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val info = "{\"histogram\":[]}".toByteArray()
        val reader = LiveViewFrameReader(Trickle(frame(0, jpeg) + frame(1, info) + frame(0, jpeg)))

        val a = reader.next()!!
        assertEquals(LiveViewFrameReader.TYPE_IMAGE, a.type)
        assertArrayEquals(jpeg, a.data)
        val b = reader.next()!!
        assertEquals(LiveViewFrameReader.TYPE_INFO, b.type)
        assertArrayEquals(info, b.data)
        assertArrayEquals(jpeg, reader.next()!!.data)
        assertNull(reader.next())
    }

    @Test
    fun payloadMayContainMarkerBytes() {
        // FF FF inside a JPEG must not be mistaken for the end marker; the size decides.
        val payload = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00)
        val reader = LiveViewFrameReader(ByteArrayInputStream(frame(0, payload)))
        assertArrayEquals(payload, reader.next()!!.data)
    }

    @Test(expected = MalformedFrameException::class)
    fun rejectsABareJpegStream() {
        LiveViewFrameReader(ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0))).next()
    }

    @Test(expected = MalformedFrameException::class)
    fun rejectsAMissingEndMarker() {
        val good = frame(0, byteArrayOf(1, 2, 3))
        good[good.size - 1] = 0x00
        LiveViewFrameReader(ByteArrayInputStream(good)).next()
    }

    @Test(expected = MalformedFrameException::class)
    fun rejectsAnImplausibleSize() {
        val bytes = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x7F, 0x00, 0x00, 0x00)
        LiveViewFrameReader(ByteArrayInputStream(bytes)).next()
    }

    @Test(expected = EOFException::class)
    fun truncatedFrameIsAnEof() {
        val good = frame(0, ByteArray(100))
        LiveViewFrameReader(ByteArrayInputStream(good.copyOf(50))).next()
    }
}
