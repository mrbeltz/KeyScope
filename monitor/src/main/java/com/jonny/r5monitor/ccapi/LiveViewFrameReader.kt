package com.jonny.r5monitor.ccapi

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** The framing on the scroll endpoint is not what this reader expects, so it should not be used. */
class MalformedFrameException(message: String) : IOException(message)

/**
 * Reads the chunked body of `shooting/liveview/scroll`, which is how CCAPI streams live view
 * without a request per frame.
 *
 * Each frame is laid out as
 *
 * ```
 * FF 00        start ID
 * tt           data type: 00 = JPEG image, 01 = live view info (JSON, scrolldetail only)
 * ss ss ss ss  payload size, big-endian
 * ...          payload
 * FF FF        end ID
 * ```
 *
 * Nothing here tries to resynchronise after a bad marker. A stream that does not frame the way we
 * expect is a firmware that does something different, and polling `flip` one JPEG at a time is a
 * better answer than guessing at where the next frame starts.
 */
class LiveViewFrameReader(
    private val input: InputStream,
    private val maxFrameBytes: Int = 8 shl 20
) {

    class Frame(val type: Int, val data: ByteArray)

    /** The next frame, or null when the camera closes the stream cleanly between frames. */
    fun next(): Frame? {
        val first = input.read()
        if (first < 0) return null
        val second = readByte()
        if (first != 0xFF || second != 0x00) {
            throw MalformedFrameException("Expected a start marker, got %02X %02X".format(first, second))
        }
        val type = readByte()
        val size = (readByte() shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
        if (size < 0 || size > maxFrameBytes) {
            throw MalformedFrameException("Implausible frame size $size")
        }
        val data = ByteArray(size)
        readFully(data)
        val end1 = readByte()
        val end2 = readByte()
        if (end1 != 0xFF || end2 != 0xFF) {
            throw MalformedFrameException("Expected an end marker, got %02X %02X".format(end1, end2))
        }
        return Frame(type, data)
    }

    private fun readByte(): Int {
        val b = input.read()
        if (b < 0) throw EOFException("Live view stream ended mid-frame")
        return b
    }

    private fun readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) throw EOFException("Live view stream ended mid-frame")
            offset += n
        }
    }

    companion object {
        const val TYPE_IMAGE = 0x00
        const val TYPE_INFO = 0x01
    }
}
