"""PC-only live Gemini checks. Never prints or stores the API key."""
import argparse
import base64
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
from food_server import ApiError, GeminiRecognizer, validate_input


def saved_setting(name):
    value = os.environ.get(name, "")
    if not value and sys.platform == "win32":
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as settings:
                value = winreg.QueryValueEx(settings, name)[0]
        except OSError:
            pass
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", help="Override model for this check only")
    parser.add_argument("--image", type=Path, help="Optional food JPEG/PNG/WebP to test after text succeeds")
    args = parser.parse_args()
    key = saved_setting("GEMINI_API_KEY")
    model = args.model or saved_setting("GEMINI_MODEL") or "gemini-3.5-flash-lite"
    try:
        recognizer = GeminiRecognizer(key, model)
    except ValueError as error:
        parser.error(str(error))
    print("Model:", model, flush=True)
    # Independent minimal REST payload, without the app's prompt/schema or local HTTP server.
    request = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent",
        data=json.dumps({"contents": [{"parts": [{"text": "Reply with OK."}]}]}).encode(),
        headers={"Content-Type": "application/json", "x-goog-api-key": key})
    try:
        with recognizer._open(request) as response:
            raw = response.read(262145)
            if len(raw) > 262144:
                raise ValueError("Oversized response")
            baseline = json.loads(raw)
            if not baseline.get("candidates"):
                raise ValueError("Missing model response")
        print("PASS minimal REST request (no app/server/schema)", flush=True)
    except urllib.error.HTTPError as error:
        try:
            detail = json.loads(error.read(65536)).get("error", {})
            message = str(detail.get("message", "")).replace(key, "[REDACTED]")
            print("FAIL minimal REST:", error.code, detail.get("status", ""), message, flush=True)
        except (ValueError, TypeError):
            print("FAIL minimal REST: HTTP", error.code, flush=True)
        print("Stopped before food recognition; this failure also occurs without the application code.")
        return 1
    except (OSError, ValueError) as error:
        print("FAIL minimal REST:", type(error).__name__, flush=True)
        return 1
    try:
        meal = recognizer(validate_input({"text": "비빔밥"}))
        print("PASS structured food recognition:", json.dumps(meal, ensure_ascii=True), flush=True)
        if args.image:
            mime = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp"}.get(args.image.suffix.lower())
            raw = args.image.read_bytes()
            meal = recognizer(validate_input({"image": {"mimeType": mime, "data": base64.b64encode(raw).decode()}}))
            print("PASS image recognition:", json.dumps(meal, ensure_ascii=True), flush=True)
    except ApiError as error:
        print("FAIL food recognition:", error.status, error.code, flush=True)
        return 1
    except OSError as error:
        print("FAIL image file:", type(error).__name__, flush=True)
        return 1
    print("PC checks passed. Next verify /food/recognize, then the Android app.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
