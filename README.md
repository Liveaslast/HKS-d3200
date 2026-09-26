# D3200 Android 音频接入

本工程负责把 Soundcore D3200 的录音送入 Android，并接入
[harmonica-audio-eval](https://github.com/jijiwu3526/harmonica-audio-eval)。

完整链路：

**D3200 → 官方 Soundcore Android SDK → raw Opus → PCM16 WAV（practice.wav）
→ harmonica-audio-eval Host → Core / 12 个数据端口 / Algorithms → JSON
→ Android UI**

## 接口结论

`harmonica-audio-eval` **有接口**，但要区分三层：

1. 对外输入参数实际是两份本地音频文件路径 `uri: str`，不是名为“WAV 接口”的
   专用类型；文件由 `soundfile` 解码。当前联调统一使用 `reference.wav`、
   `practice.wav`，以免把 D3200 的 raw Opus 误当作可直接解码的音频文件。
2. Host 接口依次执行 `create_session`、`set_reference`、`set_practice`、
   `build_surface`、`run_algorithms`、`build_view`。
3. 算法本身不直接读取 WAV；Core 先把 WAV 转成 PCM 和 12 个数据端口，
   Algorithms 再读取这些端口。

因此本工程没有改动算法工程的 Core、Ports 或 Algorithms，只增加
`mobile_bridge.py`，在 Android 内按原有 Host 接口完成调用。

## 两种运行路径

### 正式路径：Android 本地分析

`AnalysisFacade.kt` 通过 Chaquopy 调用 `mobile_bridge.py`，算法直接在平板运行，
不需要电脑 IP、`main.py` 或局域网。

### 诊断路径：HTTP

`HttpAnalysisFacade.kt` 仍可把两份 WAV 上传到 `adapter/main.py`，只用于比较、
排错和旧流程兼容，不是正式部署依赖。

## 当前状态

| 链路 | 状态 |
|---|---|
| 选择两份 WAV 并上传到电脑 HTTP 接收端 | 已验证 |
| `mobile_bridge.py` 调用完整 Host/Algorithms | 已在 Windows Python 验证 |
| 上游 Android 探针构建 12/12 数据端口 | 上游已在 Pixel 7 验证 |
| 本工程 APK 内运行完整算法并返回 JSON | 已在联想平板验证 |
| 官方 Soundcore SDK 初始化与接口封装 | 已准备，待正式凭据 |
| 真实 D3200 录音下载与 raw Opus 参数确认 | 待设备 |
| 真实 raw Opus → 可播放 WAV | 待设备 |

## 目录

| 位置 | 任务 |
|---|---|
| **android/app/src/main/java/demo/d3200/device/** | 官方 SDK：初始化、连接、录音、下载 |
| **android/app/src/main/java/demo/d3200/audio/** | Opus 解码、PCM16 与 WAV |
| **android/app/src/main/java/demo/d3200/integration/** | Android 本地算法入口与 HTTP 诊断入口 |
| **android/app/src/main/java/demo/d3200/ui/** | 真机调试界面 |
| **android/app/src/main/python/mobile_bridge.py** | 原有 Host 接口的 Android 调用桥 |
| **adapter/main.py** | 可选的电脑 HTTP 诊断服务 |

## 调试界面

调试 App 按链路分为四区：

| 区域 | 用途 | 当前怎么用 |
|---|---|---|
| A · 设备与录音 | SDK 初始化、扫描、连接、录音和下载 | 等 D3200 与正式授权后验证 |
| B · Opus → WAV | 导入 raw Opus、填写已确认的音频参数并生成 `practice.wav` | 无设备时可直接选择已有 `practice.wav`；不要猜 raw Opus 参数 |
| C · Android 本地算法 | 选择 `reference.wav`，把它和 `practice.wav` 交给本地算法 | 已在联想平板跑通，是当前正式分析入口 |
| D · HTTP 诊断后备 | 把两份 WAV 发给电脑端 `main.py` | 只在排错或对照时使用，正式链路不需要 |

底部“运行日志”用于查看 SDK 回调、文件路径、转码状态与算法 JSON。现阶段正常
验证顺序是：在 B 选择 `practice.wav` → 在 C 选择 `reference.wav` → 点击本地分析。

## 本机首次构建

Android 构建期需要 Python 3.10。在 `android/local.properties` 中保留 Android
SDK 路径，并按本机位置增加：

- **Python 3.10 路径：** python310.path = D:\Anaconda\envs\harmonica-android-build\python.exe
- **算法工程路径：** harmonica.repo = D:\Agent_Work\video\harmonica-audio-eval

然后在 `android` 目录构建 `assembleDebug`。本工程已在联想平板使用两份现成
WAV 得到包含 `ok`、`scalars`、`series` 的 JSON，Android 本地算法链已经完成
验证。D3200 链路仍需等设备与正式授权后单独验收。
