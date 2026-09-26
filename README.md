# D3200 移动端音频接入工程

这是独立的外围接入工程，不是对 harmonica-audio-eval 内核的改造。
最终目标：真实录音豆采集 → 可播放且时长正确的 WAV → **算法在移动端本机运行** → UI 显示结果。

> **当前结论：整体项目尚未成功。** 已跑通的是“Android 上传 WAV → 电脑 `main.py` →
> 电脑本地算法 → JSON 返回”的**电脑辅助联调链路**。真实 D3200 的 Opus 转 WAV 尚未真机验证，
> `harmonica-audio-eval` 也尚未部署到 Android；只要分析仍依赖电脑，本项目就不满足最终成功标准。

## 当前完成度

| 链路 | 状态 | 已有证据／缺口 |
|---|---|---|
| Android 调试 App 构建、安装、启动 | ✅ 已验证 | 已在 Lenovo TB375FC / Android 16 打开并操作 |
| Android 选择 reference/practice WAV | ✅ 已验证 | 两份 WAV 均能导入 App |
| Android → `main.py /upload-test` | ✅ 已验证 | HTTP 返回 `ok: true`；电脑保存并校验两份 PCM16 WAV |
| `main.py /analyze` → 本地算法 CLI | ✅ 已验证 | 真实 HTTP POST 返回 200；算法产出指标 JSON |
| 官方 SDK/AAR 接入代码与 Opus 解码代码 | 🟡 已准备 | 来自固定版本的 Anker 官方 Demo，但没有正式 License 与真机音频 |
| D3200 扫描、连接、开始录音、下载文件 | ❌ 未验证 | 缺录音豆与正式 SDK 凭据 |
| D3200 真实 raw Opus → 可播放 WAV | ❌ 未验证 | 缺真机生成的 raw Opus；不能用普通 Ogg Opus 代替 |
| 算法直接在 Android 本机运行 | ❌ 未实现 | 现有实现为 Python + NumPy/SciPy/librosa/soundfile；没有 Android 算法模块/AAR/JNI |
| 脱离电脑完成录音、分析和显示 | ❌ 未实现 | 当前 `/analyze` 必须依赖电脑 Python 进程 |

### 两种“跑通”不能混为一谈

当前已经跑通的是：

```text
Android 选择测试 WAV
        ↓ HTTP
电脑 adapter/main.py
        ↓ 子进程
电脑 harmonica-audio-eval
        ↓ JSON
Android
```

最终才算成功的是：

```text
D3200 → Android 官方 SDK → raw Opus → PCM16 WAV
      → Android 本地算法 → AnalysisResult → Android UI
```

最终链路中不应要求现场电脑、局域网 HTTP 服务或电脑 Python 环境。若团队最终裁定使用云端算法，
那是另一种产品方案，必须重新定义成功标准；本 README 当前按“算法落在移动端”验收。

## 文件地图

| 位置 | 文件 | 工作 |
|---|---|---|
| 手机 device/ | SoundcoreDevice.kt | 官方 SDK 初始化、扫描连接、录音、离线下载；实时请求预留 |
| 手机 device/ | OfficialBusinessCallback.kt | 按官方 Demo 的真实签名适配业务回调 |
| 手机 audio/ | OpusDecoder.kt | 调用官方 opus-lib AAR 的解码器 |
| 手机 audio/ | AudioPipeline.kt | 固定帧 raw Opus 文件转 PCM16 WAV；不解析任意 BLE 分片或 Ogg |
| 手机 integration/ | AnalysisFacade.kt | 当前电脑辅助模式：按合同上传 reference/practice，接收 JSON |
| 手机 ui/ | MainActivity.kt | 临时接入调试台，交接后可由队友 App 替换 |
| 电脑 adapter/ | main.py | 电脑辅助联调接头：接收文件、落盘并调用现有 CLI；最终移动端本地方案不需要它 |
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

## 第三步：电脑辅助联调（过渡方案，不是最终部署）

把 reference.wav 与 practice.wav 放到电脑，在队友工程根目录执行：

```powershell
python -m harmonica_eval --reference reference.wav --practice practice.wav --out data/out/d3200/metrics.json
```

这是队友已有入口，不修改内部代码。检查原有 state、scalars、series、null 和错误。合成音频测试通过不等于 D3200 通过。

## 第四步：自动上传（电脑外围服务，过渡方案）

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
Android 最新源码仍需每次改动后重新安装回归。官方 AAR 真机初始化、绑定、录音文件结束语义、
实际 Opus 格式和真实录音试听均需要独立验证。

### 接下来必须完成

1. **D3200 真机链路**：取得设备、正式 License/凭据，验证扫描、连接、录音、文件列表和下载回调。
2. **真实 Opus 规格**：确认下载文件确为官方 fixed-frame raw Opus，并核对 16000 Hz、2 声道、
   160 字节帧和解码返回计数；生成 WAV 后试听、核对时长与文件头。
3. **Android 本地算法技术选型**：由算法与移动端负责人共同决定 Kotlin/C++ 重写、JNI，或受控的
   Android Python 运行时；不能把“源码理论可移植”写成“已经部署”。
4. **移动端算法移植与对拍**：在 Android 上实现音频读取、重采样、特征、对齐和指标，使用同一组
   WAV 与电脑 Python 基准逐项比较数值和错误行为。
5. **最终整合**：删除对电脑 IP、`main.py` 和局域网的强依赖，实现 D3200 → WAV → 本地分析 → UI。
6. **真机验收**：断开电脑和开发网络，在平板上独立完成一次完整录音、分析与结果展示。

在第 1～6 项完成前，只能称为“外围接口和电脑辅助 Demo 已部分跑通”。

### 本次实际检查（2026-09-26）

- setup-official-sdk.ps1 使用已有官方检出运行成功，两个 AAR 已放入本地 android/app/libs/，来源提交及哈希已核对。
- 电脑接头的 4 项 unittest 全部通过：时长限制、截断数据、分析入口合同，以及不调用算法的纯上传入口。
- 用户已在 Android 16 平板安装并打开调试 App，成功导入两份测试 WAV，并通过 `/upload-test` 上传。
- 已向真实运行的 `/analyze` 发送两份 WAV；HTTP 200，`main.py` 成功调用本地
  `harmonica-audio-eval` 并返回 pitch/rhythm/dynamics JSON。
- 上述成功只证明电脑辅助模式；没有录音豆、正式授权和真实 D3200 raw Opus。
- 队友算法仓库包含手机可访问的 Web UI，但计算仍在电脑 Python 进程；没有 Android 本地算法部署。
- 队友仓库本次没有修改，也没有推送 GitHub。

下一优先级不是继续美化 HTTP Demo，而是取得 D3200 完成真实 Opus → WAV 验收，并由团队明确 Android
本地算法移植的负责人和技术路线。调试台中的参数确认不能替代真机实测。
