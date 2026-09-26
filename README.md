# D3200 → WAV → 现有算法入口

这是独立的外围接入工程，不是对 harmonica-audio-eval 内核的改造。
目标：真实录音豆采集 → 可播放且时长正确的 WAV → 两个文件进入现有 CLI → 原有指标返回。
当前状态：接入代码与调试台已创建；未经 Android 编译和 D3200 真机验证，不能称为已经跑通。

## 文件地图

| 位置 | 文件 | 工作 |
|---|---|---|
| 手机 device/ | SoundcoreDevice.kt | 官方 SDK 初始化、扫描连接、录音、离线下载；实时请求预留 |
| 手机 device/ | OfficialBusinessCallback.kt | 按官方 Demo 的真实签名适配业务回调 |
| 手机 audio/ | OpusDecoder.kt | 调用官方 opus-lib AAR 的解码器 |
| 手机 audio/ | AudioPipeline.kt | 固定帧 raw Opus 文件转 PCM16 WAV；不解析任意 BLE 分片或 Ogg |
| 手机 integration/ | AnalysisFacade.kt | 按队友合同上传 reference/practice，接收 JSON |
| 手机 ui/ | MainActivity.kt | 临时接入调试台，交接后可由队友 App 替换 |
| 电脑 adapter/ | main.py | 合并接口队友原型：接收文件、落盘并调用现有 CLI |
| 电脑 adapter/ | test_server.py | 格式、传输、路径隔离和返回合同测试，不冒充算法实测 |

手机源码位于 android/app/src/main/java/demo/d3200/。

## 架构参考与复用来源

- [Anker 官方 SDK Demo](https://github.com/AnkerInnovations/SoundcoreSDKDemo)：唯一设备 SDK 来源。固定到 69d3065284003686b89c6e6bf4172dca796973f1。Gradle 配置、Wrapper、回调签名和业务调用来自该版本；LICENSE/NOTICE 保留。
- [Google Android architecture-samples](https://github.com/android/architecture-samples)：借鉴展示层与数据操作分离、异步处理和独立测试；不照搬 Hilt/Room/Repository。
- [Nordic Android BLE Library](https://github.com/nordicsemi/Android-BLE-Library)：借鉴将设备操作集中在管理器及通过回调报告状态。没有加入 Nordic 依赖，也不绕过 Anker SDK 操作 GATT。

官方 AAR 不提交到 Git，setup-official-sdk.ps1 从官方仓库检出中复制它们，并核对固定提交。
当前 SHA256：module_spplink-release.aar = 0357023A9B2A0267CDD98F9FED46A1E4DA80BCDD5A51D843F6AE2C64EE860BF0；opus-lib-0.0.2.aar = 0CB5DDB09ABC9E55CF35B7E3EA003817B2F45C9F32E7F589B449F285B7670887。
正式赛事 SDK 若不同，先对照文档/签名再替换，不保证跨版本兼容。

## 第一步：打开手机工程

1. 安装 Android Studio，使用其 JDK 17，安装 Android SDK 36。打开本目录下 android/。
2. 在本目录运行 `./setup-official-sdk.ps1`。已有官方检出时可传 `-OfficialCheckout 官方仓库路径`。
3. 将 sdk-credentials.example.json 复制为 android/app/src/main/assets/sdk-credentials.json，填赛事方发放的真实参数。licenseJson 是原始许可证的 JSON 字符串，不能自行修改或伪造签名。此文件已忽略，不提交。
4. Gradle 同步后构建 Debug，将 App 安装在真实 Android 手机上。命令行可在 android/ 执行 `./gradlew.bat :app:assembleDebug`。
5. 调试台按顺序授权、初始化、扫描、填写目标设备地址、连接、录音。以设备回调为准，不以按钮点击作为成功。

此工程复用官方较完整的依赖集，先确保二进制运行依赖齐全，再考虑裁剪。没有后台录音服务；联调期间保持 App 前台。SDK 若要求设备安全绑定，请先使用官方 Demo 完成绑定；本工程没有猜测或绕过绑定流程。
SDK 凭据放 assets 仅用于受控本地 Demo，不适合正式发布 APK。

## 第二步：得到真实 WAV

1. 录制同一首曲目 45–120 秒。
2. 暂停后检查设备状态和离线文件列表；暂停是否结束文件仍需真机确认。选择明确的文件序号，不自动取“最后一个”。
3. 下载并等待 SDK 文件完成回调。回调名 onDecodeFileCompleted 不足以证明文件是 WAV，先核对真实内容。
4. 若 SDK 产出可读 WAV，直接导出/选择该 WAV；不重复解码。
5. 若产出 raw Opus，核对帧边界、采样率、声道、解码返回计数。官方转码示例是 16000 Hz / 2 声道 / PCM16 / 160 字节固定块；不自动将其当作真机格式。
6. 填入已确认参数才运行转码。不能把 Ogg/Opus 容器文件、带协议头的数据或任意回调分片按 160 字节切割。
7. 试听、核对实际时长与声道；转码不能靠补零、复制音频、改算法 profile 通过验收。

手机 WAV 保存在 App 私有 files/ 中，可通过 Android Studio Device Explorer 导出。实时接口目前只预留请求，不宣称已完成实时 PCM 流或丢包补偿。
Opus native decode 返回的是每声道采样数还是总交错采样数，必须核对 AAR/真机；界面显式选择，禁止猜测。16k 重采样到44.1k不会恢复高频信息，真实口琴音高表现由算法队友一起验证。

## 第三步：先手动调用，不急着联网

把 reference.wav 与 practice.wav 放到电脑，在队友工程根目录执行：

```powershell
python -m harmonica_eval --reference reference.wav --practice practice.wav --out data/out/d3200/metrics.json
```

这是队友已有入口，不修改内部代码。检查原有 state、scalars、series、null 和错误。合成音频测试通过不等于 D3200 通过。

## 第四步：自动上传（电脑外围服务）

使用能够运行队友框架的同一个 Python 环境安装 adapter/requirements.txt 中的外围依赖；不要改队友 requirements/冻结契约。

```powershell
python -m pip install -r adapter/requirements.txt
$env:HARMONICA_REPO = 'D:\Agent_Work\video\harmonica-audio-eval'
python adapter/main.py
```

手机与电脑必须能互通，填写 `http://电脑局域网IP:8000/analyze`，不能填手机的 localhost。
调试台选择参考 WAV 与练习 WAV 后上传。请求是 multipart/form-data，字段固定为 reference/practice。
服务每次创建独立临时目录，固定文件名，显式指定本次 metrics.json，调用 CLI 时不拼接 shell；处理结束后删除临时音频。每文件最多32 MiB，分析超时600秒，单进程串行。
当前返回沿用接口队友的 pitch/rhythm/dynamics/message 四字段合同。评分公式仍须算法负责人确认；缺失指标保持 null，不用默认高分掩盖失败。
HTTP 422 会携带算法失败信息；手机会显示失败正文，不伪装成功。

若只验收本工程负责的 WAV 上传，不运行算法，启动同一个 `adapter/main.py` 后将手机地址填写为
`http://电脑局域网IP:8000/upload-test`。该入口仍按正式合同接收 `reference` 与 `practice`，但只校验
PCM16 WAV、保存到 `adapter/received/<request_id>/`，并返回大小、时长、采样率、声道和 SHA-256。
它不导入、不调用 `harmonica_eval`，也不限制音频必须达到算法要求的 45–120 秒。

仅限可信局域网开发联调：HTTP 没有加密或鉴权，音频会走明文。不要暴露公网；正式部署必须增加 HTTPS、鉴权、反向代理的请求体限制、并发/速率限制及凭据管理。不要自动改防火墙设置。

## 测试与尚未完成

电脑传输测试（额外安装 httpx==0.28.1）：在 adapter/ 执行 `python -m unittest -v test_server`。
Android 构建、官方 AAR 真机初始化、绑定、录音文件结束语义、实际 Opus 格式、试听与真实分析均需要独立验证。
完整验收是“真实设备音频进入现有框架并得到有效指标”，不是“目录和接口已生成”。

### 本次实际检查（2026-09-26）

- setup-official-sdk.ps1 使用已有官方检出运行成功，两个 AAR 已放入本地 android/app/libs/，来源提交及哈希已核对。
- 电脑接头的4项 unittest 全部通过：时长限制、截断数据、分析入口合同，以及不调用算法的纯上传入口。
- Android assembleDebug 已尝试，因没有 JAVA_HOME/java 而失败；尚未进入 Kotlin 编译阶段，因此 API 编译兼容性未验证。
- 没有录音豆、正式授权与真实参考录音；没有执行真实设备录音、解码和算法端到端验收。
- 队友仓库本次没有修改，也没有推送 GitHub。

先补齐 Android Studio/JDK 与正式 SDK 授权，再做真机验收。调试台中的参数确认不是替代实测的办法。
