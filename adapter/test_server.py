import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import wave

from fastapi.testclient import TestClient
from main import app, validate_wav


def wav_bytes(seconds=45):
    stream = io.BytesIO()
    with wave.open(stream, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(b"\x00\x00" * 16000 * seconds)
    return stream.getvalue()


class AdapterTests(unittest.TestCase):
    # Synthetic silence tests transport ONLY, never prove real algorithm success.
    def test_wav_duration(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "sample.wav"
            path.write_bytes(wav_bytes())
            validate_wav(path)
            path.write_bytes(wav_bytes(3))
            with self.assertRaises(ValueError):
                validate_wav(path)

    def test_truncated_wav(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "sample.wav"
            path.write_bytes(wav_bytes()[:-2])
            with self.assertRaises(ValueError):
                validate_wav(path)

    def test_upload_and_mobile_result(self):
        with tempfile.TemporaryDirectory() as folder:
            repo = Path(folder)
            (repo / "harmonica_eval").mkdir()
            (repo / "harmonica_eval" / "__main__.py").touch()
            with patch.dict(os.environ, {"HARMONICA_REPO": folder}):
                with TestClient(app) as client:
                    files = {name: ("ignored.wav", wav_bytes(), "audio/wav") for name in ("reference", "practice")}
                    payload = {"schema_version": "v1", "state": "DATA_READY", "scalars": [
                        {"key": "pitch.median_abs_cents", "value": 100.0},
                        {"key": "timing.on_time_ratio", "value": 0.75},
                        {"key": "dynamics.median_db", "value": -2.0}], "series": []}
                    def fake_run(repository, reference, practice, output):
                        self.assertEqual(repository, repo.resolve())
                        self.assertTrue(reference.is_file() and practice.is_file())
                        self.assertNotEqual(reference.parent, repo)
                        return 0, payload, ""
                    with patch("main.run_algorithm", side_effect=fake_run):
                        response = client.post("/analyze", files=files)
                        self.assertEqual(response.status_code, 200)
                        self.assertEqual(response.json(), {"pitch": 90.0, "rhythm": 75.0,
                            "dynamics": 84.0, "message": "音准中位绝对偏差 100 音分"})
                    short = {name: ("short.wav", wav_bytes(2), "audio/wav") for name in ("reference", "practice")}
                    self.assertEqual(client.post("/analyze", files=short).status_code, 422)

    def test_upload_only_does_not_run_algorithm(self):
        from main import RECEIVED_ROOT
        files = {name: (f"{name}.wav", wav_bytes(2), "audio/wav")
                 for name in ("reference", "practice")}
        with TestClient(app) as client, patch("main.RECEIVED_ROOT", Path(tempfile.mkdtemp())):
            with patch("main.run_algorithm") as run_algorithm:
                response = client.post("/upload-test", files=files)
                self.assertEqual(response.status_code, 200)
                payload = response.json()
                self.assertTrue(payload["ok"])
                self.assertEqual(payload["practice"]["sample_rate"], 16000)
                self.assertEqual(payload["practice"]["duration_seconds"], 2.0)
                self.assertEqual(len(payload["practice"]["sha256"]), 64)
                self.assertTrue((Path(payload["saved_directory"]) / "practice.wav").is_file())
                run_algorithm.assert_not_called()


if __name__ == "__main__":
    unittest.main()
