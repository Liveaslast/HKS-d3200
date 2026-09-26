package demo.d3200.device

import android.app.Application
import com.oceanwing.soundcore.spplink.SoundcoreSDK
import com.oceanwing.soundcore.spplink.log.LogInterface
import com.oceanwing.soundcore.spplink.model.*
import com.oceanwing.soundcore.spplink.listener.SoundcoreConnectionCallback
import com.oceanwing.soundcore.spplink.service.auth.*
import com.oceanwing.soundcore.spplink.service.search.OnReceiveScanResult
import com.oceanwing.soundcore.spplink.service.audio.model.FileInfoModel
import java.io.File
import org.json.JSONObject
import com.oceanwing.soundcore.spplink.service.audio.model.OfflineFileData
import com.oceanwing.soundcore.spplink.listener.FileTransferError

/** SDK calls adapted from the official Android Demo, not a reimplementation of BLE. */
class SoundcoreDevice(private val application: Application) {
    private val uuid = "020cf5da-0000-1000-8000-00805f9b34fb"
    @Volatile private var authorized = false
    @Volatile private var mac: String? = null
    private var recordings: List<FileInfoModel> = emptyList()

    fun initialize(log: (String) -> Unit, onFile: (String) -> Unit, onFiles: (List<String>) -> Unit) {
        initializeFromAssets(object : OfficialBusinessCallback() {
            override fun onReceiveOfflineFile(macAddress: String, uuid: String,
                offlineFileData: OfflineFileData, isAllAudioFiles: Boolean) {
                recordings = offlineFileData.fileInfoList.toList()
                onFiles(recordings.mapIndexed { index, file -> "$index: $file" })
            }
            override fun onAudioRecordStatusChanged(macAddress: String, uuid: String, status: Int,
                recordingDuration: Int, fileID: Int) {
                log("设备录音状态=$status，时长=$recordingDuration，文件=$fileID（状态码以正式文档为准）")
            }
            override fun onDecodeFileCompleted(macAddress: String, uuid: String, fileID: Int, filePath: String) {
                onFile(filePath)
                log("SDK 文件回调完成：$fileID；必须核对文件类型及完整性")
            }
            override fun onTransferFileError(macAddress: String, uuid: String, fileID: Int, errorType: FileTransferError) {
                log("文件 $fileID 传输失败：$errorType")
            }
            override fun onReceiveOfflineFileError(macAddress: String, uuid: String, errorCode: Int) {
                log("查询文件失败：$errorCode")
            }
            override fun onCommandRejectedByAuthentication(macAddress: String, uuid: String,
                commandGroup: Int, commandId: Int, rejectCode: Int) {
                log("设备鉴权拒绝指令：$rejectCode；请按官方 Demo 完成绑定")
            }
        }) { log(if (it) "SDK 鉴权成功" else "SDK 鉴权失败") }
    }

    fun scan(onDevice: (String, String) -> Unit, log: (String) -> Unit) = scan(object : OnReceiveScanResult {
        override fun onScanSuccess(device: BlueDeviceModel) {
            val address = device.macAddress
            if (address == null) log("扫描到缺少 MAC 地址的设备，已忽略")
            else onDevice(address, device.toString())
        }
        override fun onScanError(result: Int) { log("扫描失败：$result") }
    })

    fun connect(address: String, log: (String) -> Unit) = connect(address, object : SoundcoreConnectionCallback {
        override fun onConnected(macAddress: String?, deviceName: String?) { log("设备连接成功") }
        override fun onDisconnected(macAddress: String?, systemCode: Int?, systemMsg: String?) {
            log("设备断开：$systemCode $systemMsg")
        }
        override fun onError(macAddress: String?, exception: SdkException) { log("连接失败：$exception") }
    })

    fun download(index: Int, directory: File) = download(recordings[index], directory)

    fun initializeFromAssets(callback: OfficialBusinessCallback, onAuth: (Boolean) -> Unit) {
        val data = application.assets.open("sdk-credentials.json").bufferedReader().use { JSONObject(it.readText()) }
        val config = SDKInitConfig(context = application,
            userId = data.getString("userId"), deviceId = data.getString("deviceId"),
            token = data.getString("token"), licenseJson = data.getString("licenseJson"),
            license_signature = data.getString("license_signature"),
            environment = SDKEnvironment.valueOf(data.getString("environment")),
            isDebug = false, firmwareLogFilePath = File(application.filesDir, "firmware-log").absolutePath,
            androidUDPPingTimeIntval = 100L, androidWIFIReceiveThreadSwitch = true)
        initialize(config, callback, onAuth)
    }

    fun initialize(config: SDKInitConfig, callback: OfficialBusinessCallback, onAuth: (Boolean) -> Unit) {
        val quietLog = object : LogInterface {
            override fun v(tag: String?, message: String?) {}
            override fun d(tag: String?, message: String?) {}
            override fun i(tag: String?, message: String?) {}
            override fun w(tag: String?, message: String?) {}
            override fun e(tag: String?, message: String?) {}
        }
        SoundcoreSDK.proxy.initSDK(quietLog, config, object : OnAuthResultListener {
            override fun onAuthResult(result: AuthResult) {
                authorized = result.success
                if (authorized) SoundcoreSDK.proxy.registerBusinessCallback(callback)
                onAuth(authorized)
            }
        })
    }

    fun scan(listener: OnReceiveScanResult) {
        check(authorized) { "SDK 尚未鉴权成功" }
        SoundcoreSDK.proxy.startScan(bleScan = true, sppScan = false,
            filters = arrayListOf(uuid), onReceiveScanResult = listener)
    }

    fun connect(address: String, listener: SoundcoreConnectionCallback) {
        check(authorized) { "SDK 尚未鉴权成功" }
        SoundcoreSDK.proxy.connect(address, uuid, object : SoundcoreConnectionCallback {
            override fun onConnected(macAddress: String?, deviceName: String?) {
                mac = macAddress ?: address
                listener.onConnected(macAddress, deviceName)
            }
            override fun onDisconnected(macAddress: String?, systemCode: Int?, systemMsg: String?) {
                mac = null
                listener.onDisconnected(macAddress, systemCode, systemMsg)
            }
            override fun onError(macAddress: String?, exception: SdkException) {
                mac = null
                listener.onError(macAddress, exception)
            }
        })
    }

    private fun connectedMac() = checkNotNull(mac) { "录音豆未连接" }
    fun startRecord() = SoundcoreSDK.proxy.resumeRecord(connectedMac(), uuid)
    // Pause is NOT claimed to close the current device file. Confirm on real hardware.
    fun pauseRecord() = SoundcoreSDK.proxy.pauseRecord(connectedMac(), uuid)
    fun listRecordings() = SoundcoreSDK.proxy.getAllAudioRecordFiles(connectedMac(), uuid)
    fun download(file: FileInfoModel, directory: File) {
        check(directory.isDirectory || directory.mkdirs())
        SoundcoreSDK.proxy.startSyncAudioFile(connectedMac(), uuid, arrayListOf(file),
            directory.absolutePath + "/", 120_000)
    }
    fun requestRealtime(directory: File) {
        check(directory.isDirectory || directory.mkdirs())
        SoundcoreSDK.proxy.acquireRealtimeAudioData(connectedMac(), uuid, directory.absolutePath + "/")
    }
    fun disconnect() {
        mac?.let { SoundcoreSDK.proxy.disconnect(it, uuid) }
        mac = null
    }
}
