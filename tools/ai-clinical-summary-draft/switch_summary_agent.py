#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Switch only the existing isolated tomcat-8081 development summarizer runtime."""
import argparse
import os
from pathlib import Path
import re
import signal
import subprocess
import time
from urllib.request import ProxyHandler, build_opener

from openrouter_agent import DEFAULTS, NoRedirect, loads, private_write, read_config, runtime_directory
from validate_artifact import require

PREFIX = "clinical.ai_summary_generation."


def settings(agent, port=DEFAULTS["port"], model=DEFAULTS["model"]):
    values = {"clinical.ai_summary_prototype.enabled": "true", PREFIX + "enabled": "true",
              PREFIX + "agent": agent}
    if agent == "openrouter":
        require(re.fullmatch(r"[a-z0-9._-]+/[a-z0-9._-]+", model), "Invalid model ID")
        values.update({PREFIX + "agent": "http", PREFIX + "http.port": str(port),
                       PREFIX + "http.path": "/v1/clinical-summary",
                       PREFIX + "http.name": "OpenRouter / " + model,
                       PREFIX + "http.timeoutSeconds": "600"})
    else:
        require(agent == "ollama", "Unknown agent")
        values.update({PREFIX + "ollama.port": "11436", PREFIX + "ollama.model": "qwen3.5:2b",
                       PREFIX + "ollama.timeoutSeconds": "1800"})
    return values


def update_properties(original, values):
    lines = [line for line in original.splitlines()
             if not any(re.match(r"^\s*" + re.escape(key) + r"\s*[:=\s]", line) for key in values)]
    return "\n".join(lines) + "\n" + "\n".join(key + "=" + value for key, value in values.items()) + "\n"


def runtime_processes(base, proc_root=Path("/proc")):
    matches = []
    expected = ("-Dcatalina.base=" + str(base)).encode()
    for process in proc_root.iterdir():
        if not process.name.isdigit():
            continue
        try:
            args = (process / "cmdline").read_bytes().split(b"\0")
            if expected in args and b"org.apache.catalina.startup.Bootstrap" in args:
                matches.append(int(process.name))
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            pass
    return matches


def local_get(port, path):
    with build_opener(ProxyHandler({}), NoRedirect()).open(
            f"http://127.0.0.1:{port}{path}", timeout=3) as response:
        return response.read(65536)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("agent", choices=("openrouter", "ollama"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    base = runtime_directory() / "tomcat-8081"
    properties = base / "conf/carlos-summary.properties"
    runner = base / "run-summary.sh"
    try:
        require(properties.is_file() and runner.is_file(), "The isolated local runtime must already exist")
        config = read_config(runtime_directory() / "openrouter/config.json") if args.agent == "openrouter" else {}
        values = settings(args.agent, config.get("port", DEFAULTS["port"]), config.get("model", DEFAULTS["model"]))
        pids = runtime_processes(base)
        require(len(pids) <= 1, "Multiple matching Tomcat instances; refusing ambiguous restart")
        if args.dry_run:
            print(f"Would update {properties} and restart only {base}; {len(pids)} matching process(es).")
            print("\n".join(key + "=" + value for key, value in values.items()))
            return
        if args.agent == "openrouter":
            health = loads(local_get(config["port"], "/health"))
            require(health.get("service") == "carlos-openrouter-synthetic"
                    and health.get("model") == config["model"]
                    and health.get("provider") == config["provider"], "Start the configured OpenRouter gateway first")
        else:
            local_get(11436, "/api/tags")
        original = properties.read_text()
        backup = properties.with_name("carlos-summary.properties.before-agent-switch")
        private_write(backup, original)
        # Complete the shutdown before changing properties. Never kill an unrelated Java process.
        for pid in pids:
            require(runtime_processes(base) == [pid], "Runtime process changed; retry")
            os.kill(pid, signal.SIGTERM)
        print("Waiting for the summarizer's Tomcat to stop...", flush=True)
        deadline = time.monotonic() + 45
        while runtime_processes(base):
            require(time.monotonic() < deadline, "Tomcat did not stop; configuration was not changed")
            time.sleep(1)
        private_write(properties, update_properties(original, values))
        with (base / "logs/catalina.out").open("ab") as log:
            child = subprocess.Popen([str(runner)], stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                     start_new_session=True, close_fds=True)
        print("Starting the summarizer; this usually takes about a minute...", flush=True)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            require(child.poll() is None, "Tomcat exited; see its local log")
            try:
                local_get(8081, "/carlos/")
                print(f"Ready with {args.agent}: http://127.0.0.1:8080/carlos/")
                return
            except OSError:
                time.sleep(2)
        raise ValueError("Tomcat startup timed out; see its local log")
    except (OSError, ValueError, TypeError, KeyError):
        parser.exit(1, "Switch did not complete. Start the chosen gateway first and check the isolated runtime.\n"
                    f"Tomcat log: {base}/logs/catalina.out\n"
                    "To switch back, run this command with ollama. No other app is restarted.\n")


if __name__ == "__main__":
    main()
