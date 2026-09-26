package demo.d3200.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import demo.d3200.audio.AudioPipeline
import demo.d3200.device.SoundcoreDevice
import demo.d3200.integration.AnalysisFacade
import java.io.File
import java.util.concurrent.Executors

/** Hardware/WAV and upload test harness. It is not the product UI. */
class MainActivity : Activity() {
    private lateinit var layout: LinearLayout
    private lateinit var output: TextView
    private lateinit var source: EditText
    private lateinit var device: SoundcoreDevice
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var reference: File
    private var practice: File? = null
    private var player: MediaPlayer? = null

    private fun log(message: String) = runOnUiThread { output.append("$message\n") }

    private fun section(title: String, description: String) {
        layout.addView(TextView(this).apply {
            text = title; textSize = 21f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(30, 55, 80)); setPadding(0, 28, 0, 6)
        })
        layout.addView(TextView(this).apply {
            text = description; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, 0, 0, 10)
        })
    }

    private fun input(hint: String, value: String = "") = EditText(this).also {
        it.hint = hint; it.setText(value); it.textSize = 16f; layout.addView(it)
    }

    private fun button(title: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            text = title; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 5, 0, 5) }
            setOnClickListener {
                try { action() } catch (error: Exception) { log(error.message ?: "操作失败") }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = SoundcoreDevice(application)
        reference = File(filesDir, "reference.wav")
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48)
        }
        setContentView(ScrollView(this).apply { addView(layout) })

        layout.addView(TextView(this).apply {
            text = "D3200 音频接入调试台"; textSize = 26f
            setTypeface(typeface, Typeface.BOLD); setTextColor(Color.BLACK)
        })
        layout.addView(TextView(this).apply {
            text = "A/B 验证录音豆到 WAV；C 验证 WAV 上传到队友 main.py。"
            textSize = 15f; setPadding(0, 5, 0, 4)
        })

        section("A · 设备与录音", "需要 D3200 和正式授权。每一步以 SDK 回调为准。")
        button("申请蓝牙权限") {
            requestPermissions(if (Build.VERSION.SDK_INT >= 31)
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
        button("初始化正式 SDK") {
            device.initialize(::log, { path -> runOnUiThread { source.setText(path) } }, { files ->
                log("可下载录音（填写左侧序号）：\n${files.joinToString("\n")}")
            })
        }
        val address = input("D3200 MAC；先扫描，在日志中查看")
        button("扫描 D3200") { device.scan({ mac, description -> log("发现 $mac $description") }, ::log) }
        button("连接指定设备") { device.connect(address.text.toString().trim(), ::log) }
        button("开始／恢复录音") { device.startRecord(); log("录音命令已发送，等待状态回调") }
        button("暂停录音（不代表文件一定结束）") { device.pauseRecord() }
        button("查询设备录音文件") { device.listRecordings() }
        val index = input("要下载的录音列表序号")
        button("下载指定录音") {
            device.download(index.text.toString().toInt(), File(filesDir, "download"))
        }

        section("B · Opus → WAV 验证", "只有确认下载结果是官方 fixed-frame raw Opus 后才转码；SDK 若已给 WAV，直接选择。")
        source = input("SDK 完成回调的源文件路径")
        button("选择待测试的 raw Opus 文件") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                // Some vendor file pickers report .opus/.wav as application/octet-stream.
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 12)
        }
        val sampleRate = input("采样率", "16000")
        val channels = input("声道数", "2")
        val packetBytes = input("固定帧字节数", "160")
        val confirmed = CheckBox(this).apply { text = "已确认源文件格式和帧边界" }
        layout.addView(confirmed)
        val perChannel = CheckBox(this).apply { text = "AAR decode 返回每声道采样数" }
        layout.addView(perChannel)
        button("把确认后的 raw Opus 转为 practice.wav") {
            check(confirmed.isChecked) { "必须先确认真实文件格式，不能按示例参数猜测" }
            val path = source.text.toString().trim()
            val rate = sampleRate.text.toString().toInt()
            val ch = channels.text.toString().toInt()
            val block = packetBytes.text.toString().toInt()
            val countsPerChannel = perChannel.isChecked
            worker.execute {
                try {
                    val wav = AudioPipeline.toWav(File(path),
                        File(filesDir, "practice-${System.currentTimeMillis()}.wav"),
                        rate, ch, block, countsPerChannel)
                    practice = wav
                    log("已生成 ${wav.absolutePath}；必须试听并核对时长")
                } catch (error: Exception) { log("转码失败：${error.message}") }
            }
        }
        button("选择已有 practice.wav（无设备时测试上传）") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 11)
        }
        button("试听刚生成或选择的 practice.wav") {
            val file = checkNotNull(practice) { "尚未生成或选择 practice.wav" }
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath); prepare(); start()
                setOnCompletionListener { it.release(); if (player === it) player = null }
            }
            log("开始试听 ${file.name}")
        }

        section("C · WAV 上传联调", "只测试 HTTP 接收，不运行算法；multipart 字段仍为 reference 和 practice。")
        button("选择 reference.wav") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 10)
        }
        val endpoint = input("完整地址，例如 http://192.168.1.20:8000/upload-test")
        button("上传两段 WAV 并显示接收结果") {
            val practiceFile = checkNotNull(practice) { "尚未生成或选择 practice.wav" }
            check(reference.isFile) { "先选择 reference.wav" }
            val url = endpoint.text.toString().trim()
            worker.execute {
                try { log("接口返回：\n${AnalysisFacade.analyze(url, reference, practiceFile).toString(2)}") }
                catch (error: Exception) { log("上传失败：${error.message}") }
            }
        }

        section("运行日志", "这里记录设备回调、文件路径、转码和 HTTP 结果。")
        output = TextView(this).apply {
            textSize = 15f; setTextColor(Color.BLACK); setBackgroundColor(Color.rgb(240, 243, 246))
            setPadding(16, 16, 16, 16); minHeight = 180
        }
        layout.addView(output)
        log("调试台已启动。没有授权时可直接在 B/C 选择已有 WAV 测试上传。")
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in 10..12) return
        val uri = data?.data ?: return
        worker.execute {
            try {
                val target = when (requestCode) {
                    10 -> reference
                    11 -> File(filesDir, "selected-practice.wav")
                    else -> File(filesDir, "selected-source.opus")
                }
                checkNotNull(contentResolver.openInputStream(uri)).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (requestCode == 11) practice = target
                if (requestCode == 12) runOnUiThread { source.setText(target.absolutePath) }
                log("已导入 ${target.name}（${target.length()} bytes）")
            } catch (error: Exception) { log("文件导入失败：${error.message}") }
        }
    }

    override fun onDestroy() {
        player?.release(); device.disconnect(); worker.shutdown(); super.onDestroy()
    }
}
