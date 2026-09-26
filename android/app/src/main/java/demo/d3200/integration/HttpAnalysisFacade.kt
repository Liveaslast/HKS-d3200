package demo.d3200.integration

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

/** Optional diagnostic fallback: uploads both WAV files to the desktop adapter. */
object HttpAnalysisFacade {
    fun analyze(endpoint: String, reference: File, practice: File): JSONObject {
        require(reference.isFile && practice.isFile)
        require(endpoint.startsWith("http://") || endpoint.startsWith("https://"))
        val boundary = "D3200-${UUID.randomUUID()}"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 610_000
            connection.doOutput = true
            connection.setChunkedStreamingMode(64 * 1024)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { output ->
                for ((name, file) in listOf("reference" to reference, "practice" to practice)) {
                    output.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$name.wav\"\r\n" +
                        "Content-Type: audio/wav\r\n\r\n").toByteArray())
                    file.inputStream().use { it.copyTo(output) }
                    output.write("\r\n".toByteArray())
                }
                output.write("--$boundary--\r\n".toByteArray())
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            check(status in 200..299) { "HTTP $status: $body" }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
