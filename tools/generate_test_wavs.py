"""Generate transport-only PCM16 fixtures; these are not D3200 recordings."""
from pathlib import Path
import wave

import numpy as np

ROOT = Path(__file__).resolve().parents[1] / "test-audio"
ROOT.mkdir(exist_ok=True)
SAMPLE_RATE = 44_100
DURATION = 45
time = np.arange(SAMPLE_RATE * DURATION, dtype=np.float64) / SAMPLE_RATE
envelope = 0.35 + 0.65 * (0.5 + 0.5 * np.sin(2 * np.pi * 2 * time))

for name, detune in (("reference", 1.0), ("practice", 1.06)):
    signal = sum(
        np.sin(2 * np.pi * (220 * detune) * harmonic * time) / harmonic
        for harmonic in (1, 2, 3)
    ) * envelope
    signal *= 0.707 / np.max(np.abs(signal))
    pcm = np.clip(np.rint(signal * 32767), -32768, 32767).astype("<i2")
    destination = ROOT / f"{name}.wav"
    with wave.open(str(destination), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(pcm.tobytes())
    print(destination, destination.stat().st_size)
