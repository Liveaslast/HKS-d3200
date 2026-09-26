"""Android bridge for the existing harmonica_eval Host API."""

from __future__ import annotations

import json
import os
import traceback

# Android intentionally uses the NumPy/SciPy backend verified by the upstream probe.
os.environ["DSH_FEATURE_BACKEND"] = "native"


def _enum_name(value):
    return getattr(value, "name", str(value)) if value is not None else None


def analyze(reference_path: str, practice_path: str) -> str:
    """Run the existing Host -> Core/Ports -> Algorithms chain on two local WAV paths."""
    try:
        from harmonica_eval.host.app import build_default_app

        app = build_default_app()
        session_id = app.create_session("v1")
        app.set_reference(session_id, reference_path)
        app.set_practice(session_id, practice_path)
        app.build_surface(session_id)
        app.run_algorithms(session_id)
        view = app.build_view(session_id)

        payload = {
            "ok": view.error_code is None and bool(view.scalars or view.series),
            "schema_version": "CONTRACT-UI-v2",
            "state": _enum_name(view.state),
            "error_code": getattr(view.error_code, "value", view.error_code),
            "error_detail": view.error_detail,
            "scalars": [
                {
                    "key": item.key,
                    "label": item.label,
                    "value": item.value,
                    "unit": item.unit,
                    "threshold": item.threshold,
                }
                for item in view.scalars
            ],
            # Keep the Java bridge small: the UI needs series metadata, not every plot point.
            "series": [
                {
                    "key": item.key,
                    "label": item.label,
                    "unit": item.unit,
                    "timeline_basis": _enum_name(item.timeline_basis),
                    "n_points": len(item.values),
                }
                for item in view.series
            ],
        }
    except Exception as error:
        payload = {
            "ok": False,
            "error": f"{type(error).__name__}: {error}",
            "traceback": traceback.format_exc(),
        }
    return json.dumps(payload, ensure_ascii=False, allow_nan=False)
