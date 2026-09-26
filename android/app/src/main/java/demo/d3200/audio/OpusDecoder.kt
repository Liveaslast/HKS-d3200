package demo.d3200.audio

import com.anker.lib_opus.OpusUtils

/** Official AudioTranscoder settings; the operator must confirm these on the device. */
class OpusDecoder(val sampleRate: Int, val channels: Int) {
    private val native = OpusUtils.getInstant()
    private val handle = native.createDecoder(sampleRate, channels)
    init { require(handle != 0L) { "Opus decoder 初始化失败" } }

    fun decode(packet: ByteArray, samplesPerChannelReturn: Boolean): ShortArray {
        // Opus supports up to 120 ms per packet. Allocate interleaved sample capacity.
        val pcm = ShortArray(sampleRate * channels * 120 / 1000)
        val count = native.decode(handle, packet, packet.size, pcm, pcm.size)
        val total = if (samplesPerChannelReturn) count * channels else count
        check(count > 0 && total <= pcm.size) { "Opus decode 返回 $count" }
        return pcm.copyOf(total)
    }
    fun close() = native.destroyDecoder(handle)
}
