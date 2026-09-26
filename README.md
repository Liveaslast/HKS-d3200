# D3200 Android 音频接入

本工程负责把 Soundcore D3200 的录音送入 Android，并接入
[harmonica-audio-eval](https://github.com/jijiwu3526/harmonica-audio-eval)。

完整链路：

**D3200 → 官方 Soundcore Android SDK → raw Opus → PCM16 WAV（practice.wav）
→ harmonica-audio-eval Host → Core / 12 个数据端口 / Algorithms → JSON
→ Android UI**

## 接口结论

`harmonica-audio-eval` **有接口**。从 App 到算法，接口分为下面三层：

### 1. App 交给算法工程什么？——两份音频的本地路径

App 不需要把整份 WAV 逐字节塞给算法，只要告诉算法两份文件在平板上的位置：

- `reference.wav`：标准示范音频；
- `practice.wav`：D3200 录到并完成解码的练习音频。

接口参数名是 `uri: str`，意思就是“文件路径字符串”。算法工程使用 `soundfile`
打开路径并读取音频。它并不按文件扩展名强制限定 WAV，但 D3200 下载得到的 raw
Opus 没有标准音频封装，不能直接传入，所以双方统一用 WAV 交接最稳妥。

### 2. Host 做什么？——负责按顺序组织一次完整分析

Host 可以理解为算法工程的“总调度员”。App 把两份路径交给 Host 后，Host 负责：

1. 创建本次分析任务（`create_session`）；
2. 登记标准音频（`set_reference`）；
3. 登记练习音频（`set_practice`）；
4. 读取两份音频并准备算法数据（`build_surface`）；
5. 运行音高、节奏和力度算法（`run_algorithms`）；
6. 整理成 App 能显示的结果（`build_view`）。

App 只调用这个流程，不需要分别调用每个音频算法。

### 3. 算法真正读取什么？——Core 生成的标准数据

音高、节奏和力度算法不会各自重复打开 WAV。Core 统一读取两份音频，把它们转换、
对齐并整理成 PCM 波形及 12 类标准数据。Algorithms 只读取这些已经准备好的数据，
最后产生分数、指标和曲线。

因此职责关系是：**App 提供两份文件路径 → Host 调度流程 → Core 准备统一数据
→ Algorithms 计算结果 → Host 把结果交回 App。**

本工程据此没有改动算法工程的 Core、Ports 或 Algorithms，只增加
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

## 开发者重新编译 APK（普通使用可跳过）

本节只供需要修改源码并重新生成 APK 的开发者使用。Python 3.10 和算法工程路径
只在电脑打包 APK 时使用；它们的运行组件随后会被装进 APK。已经安装 APK 的平板
可以独立运行算法，不需要连接电脑，也不需要在平板安装 Python。

首次在一台新电脑编译时，在 `android/local.properties` 中保留 Android SDK 路径，
并按该电脑的实际位置增加：

- **Python 3.10 路径：** python310.path = D:\Anaconda\envs\harmonica-android-build\python.exe
- **算法工程路径：** harmonica.repo = D:\Agent_Work\video\harmonica-audio-eval

然后在 `android` 目录执行 `assembleDebug` 生成 APK。普通使用者拿到 APK 后只需
安装，不必重复这一过程。

本工程已在联想平板使用两份现成 WAV 得到包含 `ok`、`scalars`、`series` 的
JSON，Android 本地算法链已经完成验证。D3200 链路仍需等设备与正式授权后单独验收。
