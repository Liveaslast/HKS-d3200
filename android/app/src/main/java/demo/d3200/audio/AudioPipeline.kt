package demo.d3200.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline raw-Opus conversion only. Not an Ogg demuxer or BLE-fragment parser. */
object AudioPipeline {
    fun toWav(source: File, destination: File, sampleRate: Int, channels: Int,
              packetBytes: Int, samplesPerChannelReturn: Boolean): File {
        require(sampleRate > 0 && channels in 1..2 && packetBytes > 0)
        require(source.isFile && source.length() > 0 && source.length() % packetBytes == 0L) {
            "音频不符合已确认的固定 Opus 帧格式；不可补零或猜测帧边界"
        }
        require(!destination.exists()) { "拒绝覆盖已有 WAV" }
        require(source.canonicalPath != destination.canonicalPath)
        val decoder = OpusDecoder(sampleRate, channels)
        var completed = false
        try {
            RandomAccessFile(destination, "rw").use { out ->
                out.write(ByteArray(44))
                source.inputStream().buffered().use { input ->
                    val packet = ByteArray(packetBytes)
                    while (true) {
                        var read = 0
                        while (read < packetBytes) {
                            val n = input.read(packet, read, packetBytes - read)
                            if (n < 0) break
                            read += n
                        }
                        if (read == 0) break
                        check(read == packetBytes) { "Opus 文件尾部不完整" }
                        val pcm = decoder.decode(packet, samplesPerChannelReturn)
                        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        pcm.forEach { bytes.putShort(it) }
                        out.write(bytes.array())
                    }
                }
                val size = out.length() - 44
                check(size in 1..(0xffffffffL - 36))
                check(size % (channels * 2) == 0L)
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray()).putInt((size + 36).toInt())
                header.put("WAVEfmt ".toByteArray()).putInt(16).putShort(1)
                header.putShort(channels.toShort()).putInt(sampleRate)
                header.putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
                header.put("data".toByteArray()).putInt(size.toInt())
                out.seek(0); out.write(header.array())
                completed = true
            }
        } finally {
            decoder.close()
            if (!completed) destination.delete()
        }
        return destination
    }
}
