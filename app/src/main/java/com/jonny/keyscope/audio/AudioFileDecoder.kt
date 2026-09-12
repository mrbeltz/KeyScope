package com.jonny.keyscope.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.jonny.keyscope.dsp.Resampler
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes any audio file Android can open into a mono stream at the analysis rate.
 *
 * Samples are handed out block by block rather than returned as one array: a ten minute track at
 * the source rate is over a hundred megabytes of float, and the analysis only ever needs a window
 * at a time.
 */
object AudioFileDecoder {

    private const val TIMEOUT_US = 10_000L

    class DecodeFailure(message: String) : Exception(message)

    /** Human-readable file name for display, falling back to the last path segment. */
    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index) ?: "Untitled"
                }
            }
        }
        return uri.lastPathSegment ?: "Untitled"
    }

    /**
     * @param onBlock receives mono samples at [targetRate]; returning false stops the decode early.
     * @return the decoded duration in seconds.
     */
    fun decode(
        context: Context,
        uri: Uri,
        targetRate: Int,
        onBlock: (FloatArray, Int) -> Boolean
    ): Float {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) throw DecodeFailure("No audio track in this file")

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var resampler = Resampler(sourceRate, targetRate)
            var mono = FloatArray(0)
            var resampled = FloatArray(0)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var produced = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // The real rate and channel count are only reliable once decoding starts.
                        val output = codec.outputFormat
                        sourceRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        resampler = Resampler(sourceRate, targetRate)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outputIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val frames = downmix(buffer, channels) { needed ->
                                    if (mono.size < needed) mono = FloatArray(needed)
                                    mono
                                }
                                if (frames > 0) {
                                    val capacity = resampler.maxOutput(frames)
                                    if (resampled.size < capacity) resampled = FloatArray(capacity)
                                    val count = resampler.process(mono, frames, resampled)
                                    if (count > 0) {
                                        produced += count
                                        if (!onBlock(resampled, count)) outputDone = true
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
            return produced.toFloat() / targetRate
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Averages the channels into [provide]'s buffer. Stereo is summed rather than taking one side,
     * because plenty of records put the bass or the pads hard to one channel.
     */
    private fun downmix(
        buffer: ByteBuffer,
        channels: Int,
        provide: (Int) -> FloatArray
    ): Int {
        val safeChannels = channels.coerceAtLeast(1)
        val order = buffer.order()
        buffer.order(ByteOrder.nativeOrder())
        val shorts = buffer.asShortBuffer()
        val total = shorts.remaining()
        val frames = total / safeChannels
        if (frames <= 0) {
            buffer.order(order)
            return 0
        }
        val out = provide(frames)
        var index = 0
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until safeChannels) {
                sum += shorts.get(index++) / 32768f
            }
            out[frame] = sum / safeChannels
        }
        buffer.order(order)
        return frames
    }
}
