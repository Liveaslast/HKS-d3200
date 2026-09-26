# D3200 Android 音频接入

本工程负责把 Soundcore D3200 的录音接入 Android，并与
[harmonica-audio-eval](https://github.com/jijiwu3526/harmonica-audio-eval) 对接。

设备能力只使用 [Anker 官方 SoundcoreSDKDemo](https://github.com/AnkerInnovations/SoundcoreSDKDemo)
提供的 Android SDK、AAR 与示例，不在算法工程中引入蓝牙、SDK 或 Opus 细节。

## 一、工程边界

```text
d3200-integration
负责：D3200 → Android → Opus → PCM16 WAV → 分析入口

harmonica-audio-eval
负责：reference.wav + practice.wav → 音高/节奏/力度指标
```

两个工程保持独立。本工程通过稳定的 WAV 输入边界调用算法，不修改
`harmonica-audio-eval` 的 Core、Ports 与 Algorithms。

## 二、当前工作链

当前已经跑通的是电脑辅助链路：

```text
Android 选择 reference.wav 与 practice.wav
        ↓ HTTP multipart/form-data
adapter/main.py
        ↓ 保存为电脑本地临时文件
harmonica-audio-eval 的 Python CLI
        ↓ metrics.json
adapter/main.py 返回 JSON
        ↓
Android 显示结果
```

接口如下：

```text
POST /upload-test   只验证 WAV 上传、保存与格式，不运行算法
POST /analyze       接收 WAV，调用电脑本地 harmonica-audio-eval

multipart 字段：reference、practice
```

这条链路用于联调，不是最终移动端部署。当前算法仍运行在电脑 Python 进程中。

## 三、完整目标链

最终验收目标是不依赖电脑和局域网服务：

```text
D3200
  ↓ Anker Android SDK
扫描、连接、开始/停止录音、下载录音
  ↓
fixed-frame raw Opus
  ↓ 官方 opus-lib AAR
PCM16
  ↓ 写入 WAV
practice.wav
  ├──────── reference.wav
  ↓
Android 本地算法模块
  ↓
AnalysisResult
  ↓
Android UI
```

只有在平板断开电脑后仍能独立完成这条链，才算整体部署完成。

## 四、Android 本地算法落地路径

`harmonica-audio-eval` 当前是 Python 工程。仓库中的 Web UI 可以被手机浏览器访问，
但计算仍发生在电脑上，不等于算法已部署到 Android。

移动端落地需要按以下顺序完成：

```text
1. 冻结输入输出
   analyze(reference.wav, practice.wav) → AnalysisResult

2. 保留现有算法结构
   Host → Core/Ports → Algorithms

3. 替换桌面依赖
   soundfile/librosa/命令行入口 → Android 音频读取与移动端实现

4. 形成 Android 可调用模块
   Kotlin/C++/JNI，或经验证可用的 Android Python 运行时

5. 数值对拍
   同一组 WAV 分别在 Python 与 Android 运行，逐项比较指标和错误行为

6. 接入 App
   Opus → WAV → 本地 analyze() → UI
```

技术路线需由 Android 与算法实现共同确定。在完成真机打包和数值对拍前，
“架构可移植”不能写成“已经在移动端运行”。

## 五、当前状态

| 环节 | 状态 |
|---|---|
| Android 调试 App 构建、安装和文件选择 | 已验证 |
| Android 上传两份 WAV 到 `/upload-test` | 已验证 |
| `main.py` 调用电脑本地算法并返回 JSON | 已验证 |
| 官方 Soundcore SDK/AAR 接入代码 | 已准备，待正式凭据与真机验证 |
| D3200 扫描、连接、录音和文件下载 | 未验证 |
| 真实 D3200 raw Opus → 可播放 WAV | 未验证 |
| `harmonica-audio-eval` 在 Android 本地运行 | 未实现 |
| 断开电脑后的完整闭环 | 未实现 |

当前结论：**WAV 上传与电脑算法接入已经跑通；真实 D3200 音频链路和 Android 本地算法部署尚未完成。**

## 六、代码位置

```text
android/app/src/main/java/demo/d3200/
├── device/       官方 SDK：初始化、扫描、连接、录音、下载
├── audio/        opus-lib 解码与 PCM16 WAV 写入
├── integration/ 当前 HTTP 对接层；本地算法完成后由本地调用替代
└── ui/           真机联调界面

adapter/
├── main.py       电脑辅助模式的 HTTP 接收与算法调用
└── test_server.py
```

## 七、下一步

1. 取得 D3200 与正式 SDK 凭据，跑通录音文件下载。
2. 用真实 raw Opus 验证采样率、声道、帧长、解码返回计数及 WAV 时长。
3. 确定 `harmonica-audio-eval` 的 Android 实现路线。
4. 完成 Android 本地算法模块与 Python 基准对拍。
5. 移除对 `main.py`、电脑 IP 和局域网的运行依赖，完成独立真机验收。
