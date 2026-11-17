#!/usr/bin/env python3
"""Detached local server lifecycle; no system settings or login jobs are changed."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / ".run"
PID = RUN / "server.pid"


def load_env():
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


def running_pid():
    try:
        pid = int(PID.read_text())
        command = subprocess.check_output(["ps", "-p", str(pid), "-o", "command="], text=True)
        # Never signal a recycled PID belonging to another command.
        return pid if str(Path(__file__).resolve()) in command and "--serve" in command else None
    except (OSError, ValueError, subprocess.CalledProcessError):
        return None


def serve():
    load_env()
    keep_awake = None
    if sys.platform == "darwin":
        keep_awake = subprocess.Popen(["/usr/bin/caffeinate", "-i", "-w", str(os.getpid())])
    child = subprocess.Popen([sys.executable, "-m", "uvicorn", "fitfuture.app:app", "--host",
                              os.getenv("SERVER_HOST", "127.0.0.1"), "--port", os.getenv("SERVER_PORT", "8000")], cwd=ROOT)

    def stop(signum, frame):
        child.terminate()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        return child.wait()
    finally:
        if keep_awake:
            keep_awake.terminate()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", nargs="?", choices=["start", "stop", "status"])
    parser.add_argument("--serve", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.serve:
        return serve()
    load_env()
    RUN.mkdir(exist_ok=True)
    pid = running_pid()
    if args.action == "stop":
        if pid:
            os.kill(pid, signal.SIGTERM)
            for _ in range(50):
                if running_pid() is None:
                    break
                time.sleep(.1)
        PID.unlink(missing_ok=True)
        print("Server stopped.")
    elif args.action == "start":
        if pid:
            print(f"Already running (PID {pid}).")
            return 0
        with (RUN / "server.log").open("ab") as log:
            process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "--serve"],
                                       cwd=ROOT, stdout=log, stderr=log, stdin=subprocess.DEVNULL,
                                       start_new_session=True)
        PID.write_text(str(process.pid))
        for _ in range(30):
            time.sleep(.2)
            if process.poll() is not None:
                print("Server failed to start. See .run/server.log.")
                return 1
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{os.getenv('SERVER_PORT', '8000')}/health", timeout=.3) as response:
                    health = json.load(response)
                print(f"Background server PID {process.pid}; health={health}; logs=.run/server.log")
                return 0
            except OSError:
                pass
        print("Startup not confirmed. See .run/server.log.")
        return 1
    else:
        print(f"Running (PID {pid})" if pid else "Stopped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
