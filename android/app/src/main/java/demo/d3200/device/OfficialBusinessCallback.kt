package demo.d3200.device

import com.oceanwing.soundcore.spplink.listener.*
import com.oceanwing.soundcore.spplink.service.audio.model.OfflineFileData
import com.oceanwing.soundcore.spplink.service.deviceinfo.DeviceInfo
import com.oceanwing.soundcore.spplink.service.devicelog.DeviceLogFileInfo
import com.oceanwing.soundcore.spplink.service.wifi.WifiState

// Signatures adapted from Anker D3200EventManager at 69d3065 (Apache-2.0).
// Unused SDK capabilities deliberately have no demo behavior.
open class OfficialBusinessCallback : SoundcoreBusinessCallback {
    override fun onAudioRecordStatusChanged(
        macAddress: String,
        uuid: String,
        status: Int,
        recordingDuration: Int,
        fileID:Int
    ) {}
    override fun onReceiveOfflineFile(
        macAddress: String,
        uuid: String,
        offlineFileData: OfflineFileData,
        isAllAudioFiles: Boolean
    ) {}
    override fun onReceiveOfflineFileError(
        macAddress: String,
        uuid: String,
        errorCode: Int
    ) {}
    override fun onAudioRecordDuration(macAddress: String, uuid: String, durationMs: Long) {}
    override fun onTransferFileStatusChanged(
        macAddress: String,
        uuid: String,
        fileID: Int,
        status: Int,
        markTimeStampList: ArrayList<Int>
    ) {}
    override fun onDecodeFileCompleted(
        macAddress: String,
        uuid: String,
        fileID: Int,
        filePath: String
    ) {}
    override fun onDecodeFileMarkers(
        macAddress: String,
        uuid: String,
        fileID: Int,
        markerTimes: IntArray
    ) {}
    override fun onTransferFileTransmitting(macAddress: String, uuid: String, fileID: Int) {}
    override fun onTransferFileError(
        macAddress: String,
        uuid: String,
        fileID: Int,
        errorType: FileTransferError
    ) {}
    override fun onOtaError(macAddress: String, uuid: String, errorCode: Int) {}
    override fun onTransferFileSupplementProgress(
        macAddress: String,
        uuid: String,
        fileID: Int,
        total: Int,
        current: Int
    ) {}
    override fun onTransferFileProgress(
        macAddress: String,
        uuid: String,
        fileID: Int,
        total: Int,
        current: Int,
        sequenceNumber: Int,
        isMark: Boolean
    ) {}
    override fun onReceiveAudioFragment(
        macAddress: String,
        uuid: String,
        fileID: Int,
        audioData: ByteArray,
        isAppendPreAudio: Boolean,
        sequenceNumber: Int,
        isMark: Boolean
    ) {}
    override fun onGetDeviceInfo(macAddress: String, uuid: String, info: DeviceInfo) {}
    override fun onGetDeviceInfoError(macAddress: String, uuid: String, errorCode: Int) {}
    override fun onDeleteFileResult(
        macAddress: String,
        uuid: String,
        fileID: Int,
        isSuccess: Boolean
    ) {}
    override fun onSendBindingDeviceCMDSuccess(macAddress: String, uuid: String) {}
    override fun onSendUnbindingDeviceCMDSuccess(macAddress: String, uuid: String) {}
    override fun onDeviceBindConfirm(macAddress: String, uuid: String) {}
    override fun onBindingKeyReady(macAddress: String, uuid: String, bindingKey: ByteArray) {}
    override fun onAuthResult(macAddress: String, uuid: String, isSuccess: Boolean) {}
    override fun onCommandRejectedByAuthentication(macAddress: String, uuid: String, commandGroup: Int, commandId: Int, rejectCode: Int) {}
    override fun onDeviceLogEnableCollectionResult(
        macAddress: String,
        uuid: String,
        success: Boolean
    ) {}
    override fun onDeviceLogRequestListResult(
        macAddress: String,
        uuid: String,
        success: Boolean,
        fileCount: Int,
        fileList: List<DeviceLogFileInfo>,
        logCollectionStatus: Boolean
    ) {}
    override fun onDeviceLogTransferResult(
        macAddress: String,
        uuid: String,
        success: Boolean,
        code: Int
    ) {}
    override fun onDeviceLogDeleteResult(
        macAddress: String,
        uuid: String,
        success: Boolean
    ) {}
    override fun onDeviceLogDumping(
        macAddress: String,
        uuid: String,
        success: Boolean,
        packetSequence: Int,
        fileTimestamp: Int,
        transferStatus: Int,
        progress: Int,
        fragmentData: ByteArray
    ) {}
    override fun onBindingDeviceError(macAddress: String, uuid: String, errorCode: Int) {}
    override fun onSyncTimeResult(
        macAddress: String,
        uuid: String,
        result: Boolean
    ) {}
    override fun onDeviceChargingStatusChanged(
        macAddress: String,
        uuid: String,
        deviceChargingStatus: Int,
        chargingBoxChargingStatus: Int
    ) {}
    override fun onBatteryInfoChanged(
        macAddress: String,
        uuid: String,
        deviceBattery: Int,
        chargingBoxBattery: Int
    ) {}
    override fun onResetDeviceResult(
        macAddress: String,
        uuid: String,
        result: Boolean
    ) {}
    override fun onFindMyEnableResult(
        macAddress: String,
        uuid: String,
        success: Boolean
    ) {}
    override fun onReceiveWaitingDeviceOpenWIFI(macAddress: String, uuid: String) {}
    override fun onWifiScanned(macAddress: String, uuid: String) {}
    override fun onConnectWIFIError(
        macAddress: String,
        uuid: String,
        errorCode: Int
    ) {}
    override fun onConnectWIFIStatusChanged(
        macAddress: String,
        uuid: String,
        status: WifiState
    ) {}
    override fun onCloseSyncFileByWIFI(macAddress: String, uuid: String) {}
    override fun onRealtimeTransferSwitch(
        macAddress: String,
        uuid: String,
        fileID: Int,
        filePath: String?
    ) {}
    override fun onDeviceTransferStateChanged(macAddress: String, uuid: String, state: DeviceTransferState) {}
    override fun onConnectSPPLinkStatusChanged(
        macAddress: String,
        uuid: String,
        isConnect: Boolean
    ) {}
}
