"""HTTP adapter: uploaded WAV bytes -> local paths -> harmonica_eval CLI."""
from __future__ import annotations

import asyncio
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid
import wave

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

app = FastAPI(title="D3200 audio analysis adapter")
analysis_lock = asyncio.Lock()
MAX_FILE_BYTES = 32 * 1024 * 1024
RECEIVED_ROOT = Path(__file__).resolve().parent / "received"


def algorithm_repository() -> Path:
    configured = os.environ.get("HARMONICA_REPO")
    # Default layout: <parent>/d3200-integration and <parent>/harmonica-audio-eval.
    repository = (Path(configured) if configured else
                  Path(__file__).resolve().parents[2] / "harmonica-audio-eval").resolve()
    if not (repository / "harmonica_eval" / "__main__.py").is_file():
        raise HTTPException(503, "HARMONICA_REPO 未指向可用的 harmonica-audio-eval")
    return repository


def validate_wav(path: Path) -> None:
    try:
        with wave.open(str(path), "rb") as audio:
            channels = audio.getnchannels()
            width = audio.getsampwidth()
            rate = audio.getframerate()
            frames = audio.getnframes()
            if width != 2 or channels not in (1, 2) or rate <= 0:
                raise ValueError("仅接收 PCM16、单/双声道 WAV")
            duration = frames / rate
            if not 45 <= duration <= 120:
                raise ValueError("每段 WAV 必须为 45–120 秒")
            if len(audio.readframes(frames)) != frames * channels * width:
                raise ValueError("WAV 数据不完整")
    except (wave.Error, EOFError, ZeroDivisionError) as error:
        raise ValueError("文件不是有效的 PCM WAV") from error


def inspect_wav(path: Path) -> dict:
    """Validate a PCM16 WAV for transport testing without algorithm constraints."""
    try:
        with wave.open(str(path), "rb") as audio:
            channels = audio.getnchannels()
            width = audio.getsampwidth()
            rate = audio.getframerate()
            frames = audio.getnframes()
            if width != 2 or channels not in (1, 2) or rate <= 0:
                raise ValueError("仅接收 PCM16、单/双声道 WAV")
            if len(audio.readframes(frames)) != frames * channels * width:
                raise ValueError("WAV 数据不完整")
        return {
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "sample_rate": rate,
            "channels": channels,
            "bits_per_sample": width * 8,
            "frames": frames,
            "duration_seconds": round(frames / rate, 3),
        }
    except (wave.Error, EOFError, ZeroDivisionError) as error:
        raise ValueError("文件不是有效的 PCM WAV") from error


async def save_upload(upload: UploadFile, destination: Path) -> None:
    size = 0
    with destination.open("xb") as output:
        while chunk := await upload.read(64 * 1024):
            size += len(chunk)
            if size > MAX_FILE_BYTES:
                raise HTTPException(413, "单个 WAV 不能超过 32 MiB")
            output.write(chunk)
    try:
        await asyncio.to_thread(validate_wav, destination)
    except ValueError as error:
        raise HTTPException(422, str(error)) from error


async def save_transport_upload(upload: UploadFile, destination: Path) -> dict:
    size = 0
    with destination.open("xb") as output:
        while chunk := await upload.read(64 * 1024):
            size += len(chunk)
            if size > MAX_FILE_BYTES:
                raise HTTPException(413, "单个 WAV 不能超过 32 MiB")
            output.write(chunk)
    try:
        return await asyncio.to_thread(inspect_wav, destination)
    except ValueError as error:
        raise HTTPException(422, str(error)) from error


def run_algorithm(repository: Path, reference: Path, practice: Path, output: Path) -> tuple[int, dict, str]:
    process = subprocess.run(
        [sys.executable, "-m", "harmonica_eval",
         "--reference", str(reference), "--practice", str(practice),
         "--out", str(output)],
        cwd=repository,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=600,
        env={**os.environ, "PYTHONIOENCODING": "utf-8", "PYTHONDONTWRITEBYTECODE": "1"},
    )
    metrics = json.loads(output.read_text(encoding="utf-8")) if output.is_file() else {}
    return process.returncode, metrics, process.stderr[-4000:]


def _number(value):
    return float(value) if isinstance(value, (int, float)) and math.isfinite(value) else None


def mobile_result(metrics: dict) -> dict:
    """Preserve the teammate's four-field mobile contract.

    Score formula ownership remains with the adapter/algorithm owners; missing values stay null.
    """
    scalar = {item.get("key"): item.get("value") for item in metrics.get("scalars", [])}
    pitch_cents = _number(scalar.get("pitch.median_abs_cents"))
    on_time_ratio = _number(scalar.get("timing.on_time_ratio"))
    dynamics_db = _number(scalar.get("dynamics.median_db"))
    pitch = None if pitch_cents is None else round(max(0.0, min(100.0, 100 - pitch_cents / 10)), 1)
    rhythm = None if on_time_ratio is None else round(max(0.0, min(1.0, on_time_ratio)) * 100, 1)
    dynamics = None if dynamics_db is None else round(max(0.0, min(100.0, 100 - abs(dynamics_db) * 8)), 1)
    hints = []
    if pitch_cents is not None and pitch_cents > 50:
        hints.append(f"音准中位绝对偏差 {pitch_cents:.0f} 音分")
    if on_time_ratio is not None and on_time_ratio < 0.5:
        hints.append(f"节奏死区内比例 {on_time_ratio * 100:.0f}%")
    if dynamics_db is not None and abs(dynamics_db) > 3:
        hints.append(f"中位能量差 {dynamics_db:.1f} dB")
    return {"pitch": pitch, "rhythm": rhythm, "dynamics": dynamics,
            "message": "；".join(hints) if hints else "分析完成"}


@app.post("/analyze")
async def analyze(reference: UploadFile = File(...), practice: UploadFile = File(...)):
    repository = algorithm_repository()
    try:
        # The current algorithm writes shared runtime state; serialize requests for the demo.
        async with analysis_lock:
            with tempfile.TemporaryDirectory(prefix="d3200-analysis-") as directory:
                root = Path(directory)
                reference_path = root / "reference.wav"
                practice_path = root / "practice.wav"
                await save_upload(reference, reference_path)
                await save_upload(practice, practice_path)
                try:
                    code, metrics, stderr = await asyncio.to_thread(
                        run_algorithm, repository, reference_path, practice_path,
                        root / "metrics.json")
                except subprocess.TimeoutExpired as error:
                    raise HTTPException(504, "算法运行超过 600 秒") from error
                if code != 0 or not metrics:
                    raise HTTPException(422, {"error": "算法未成功完成", "metrics": metrics,
                                              "stderr": stderr})
                return JSONResponse(mobile_result(metrics))
    finally:
        await reference.close()
        await practice.close()


@app.post("/upload-test")
async def upload_test(reference: UploadFile = File(...), practice: UploadFile = File(...)):
    """Receive and retain two WAVs without importing or running the algorithm."""
    request_id = f"{time.strftime('%Y%m%d-%H%M%S')}-{uuid.uuid4().hex[:8]}"
    root = RECEIVED_ROOT / request_id
    root.mkdir(parents=True, exist_ok=False)
    try:
        reference_info = await save_transport_upload(reference, root / "reference.wav")
        practice_info = await save_transport_upload(practice, root / "practice.wav")
        return {
            "ok": True,
            "request_id": request_id,
            "saved_directory": str(root),
            "reference": reference_info,
            "practice": practice_info,
        }
    except Exception:
        for path in root.glob("*"):
            path.unlink(missing_ok=True)
        root.rmdir()
        raise
    finally:
        await reference.close()
        await practice.close()


@app.get("/health")
def health():
    configured = os.environ.get("HARMONICA_REPO")
    repository = (Path(configured) if configured else
                  Path(__file__).resolve().parents[2] / "harmonica-audio-eval").resolve()
    return {"service": "audio_http_adapter", "upload_test": True,
            "algorithm_available": (repository / "harmonica_eval" / "__main__.py").is_file()}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
