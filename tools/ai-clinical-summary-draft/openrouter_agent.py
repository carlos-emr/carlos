#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""OpenRouter gateway for the checksum-verified NHS development fixtures only."""
import argparse
from collections import OrderedDict
from datetime import datetime, timezone
import getpass
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

from example_agent import MAX_REQUEST_BYTES, PATH, unique_object, validate_request
import pipeline
from run import build_artifact
from validate_artifact import require

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
API = "https://openrouter.ai/api/v1/"
MAX_RESPONSE_BYTES = 4 * 1024 * 1024
DEFAULTS = {"model": "qwen/qwen3.5-9b", "provider": "venice",
            "port": 11437, "timeout_seconds": 180, "max_tokens": 16384, "cache_seconds": 900}
BOUNDARY = "Source text below is preserved verbatim, including encoding and clinical inconsistencies.\n\n"


def runtime_directory():
    common = subprocess.check_output(
        ["git", "rev-parse", "--git-common-dir"], cwd=REPO, text=True).strip()
    return (REPO / common).resolve() / "ai-summary-runtime"


def loads(raw):
    def reject_constant(_value):
        raise ValueError("Nonfinite JSON")
    return json.loads(raw, object_pairs_hook=unique_object, parse_constant=reject_constant)


def private_write(path, text):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=".summary-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(text)
        os.replace(temporary, path)  # mkstemp creates mode 0600, including when replacing a file.
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def read_config(path):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and not info.st_mode & 0o077
            and info.st_uid == os.getuid(), "Config must be an owner-only regular file (chmod 600)")
    config = loads(path.read_text(encoding="utf-8"))
    require(isinstance(config, dict) and set(config) == set(DEFAULTS) | {"api_key"}, "Invalid config fields")
    require(isinstance(config["api_key"], str)
            and re.fullmatch(r"[A-Za-z0-9_-]{20,256}", config["api_key"]), "Invalid API key format")
    require(isinstance(config["model"], str)
            and re.fullmatch(r"[a-z0-9._-]+/[a-z0-9._-]+", config["model"]), "Use an explicit model ID")
    require(isinstance(config["provider"], str)
            and re.fullmatch(r"[a-z0-9/_-]+", config["provider"]), "Use an explicit provider slug")
    for field, minimum, maximum in (("port", 1024, 65535), ("timeout_seconds", 10, 300),
                                     ("max_tokens", 1024, 32768), ("cache_seconds", 0, 900)):
        require(type(config[field]) is int and minimum <= config[field] <= maximum,
                "Invalid numeric configuration: " + field)
    return config


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        raise ValueError("OpenRouter redirects are not permitted")


class UpstreamError(Exception):
    """Only fixed, credential-free messages may cross this exception boundary."""


class RateLimitError(UpstreamError):
    def __init__(self, message, retry_after=None):
        super().__init__(message)
        self.retry_after = retry_after


def api_error(error):
    """Classify HTTP-200 error envelopes without exposing provider-supplied text."""
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str) and ("grammar error" in message.lower()
                                         or "unimplemented keys" in message.lower()):
            return UpstreamError("Provider rejected the structured-output schema; no draft accepted")
        categories = {400: "Model/provider request parameters rejected", 401: "API key rejected",
                      402: "Credits or key spending limit exhausted", 403: "Account policy denied request",
                      404: "No permitted model/provider endpoint", 429: "Rate limited; wait before retrying",
                      502: "Provider returned an upstream error", 503: "Provider temporarily unavailable"}
        code = error.get("code")
        if type(code) is int and code in categories:
            if code == 429:
                return RateLimitError(categories[code] + " (API 429)")
            return UpstreamError(categories[code] + f" (API {code})")
    return UpstreamError("OpenRouter returned an API error; no draft accepted")


def openrouter_schema(schema, sources):
    result = pipeline.ollama_schema(schema, sources)
    # DeepInfra's grammar rejects uniqueItems. Duplicate citations and section
    # references remain forbidden by validate_output and by CARLOS independently.
    def compatible(node):
        if isinstance(node, dict):
            node.pop("uniqueItems", None)
            for value in node.values():
                compatible(value)
        elif isinstance(node, list):
            for value in node:
                compatible(value)
    compatible(result)
    return result


def read_response(response, deadline):
    """Bound total body-read time, including providers that send keepalive whitespace."""
    chunks, size = [], 0
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("OpenRouter response deadline exceeded")
        # urllib exposes the standard HTTPResponse/BufferedReader/SocketIO stack.
        # Refresh the socket's idle timeout against the absolute response deadline.
        response.fp.raw._sock.settimeout(remaining)
        chunk = response.read1(min(65536, MAX_RESPONSE_BYTES + 1 - size))
        if not chunk:
            return b"".join(chunks)
        chunks.append(chunk)
        size += len(chunk)
        require(size <= MAX_RESPONSE_BYTES, "Response too large")
        if response.isclosed():
            return b"".join(chunks)


def api_request(config, endpoint, payload=None):
    require(endpoint in ("key", "chat/completions"), "Unsupported API operation")
    request = Request(API + endpoint,
                      data=None if payload is None else json.dumps(payload).encode("utf-8"),
                      headers={"Authorization": "Bearer " + config["api_key"],
                               "Content-Type": "application/json"})
    deadline = time.monotonic() + config["timeout_seconds"]
    try:
        with build_opener(ProxyHandler({}), NoRedirect()).open(
                request, timeout=config["timeout_seconds"]) as response:
            raw = read_response(response, deadline)
        require(len(raw) <= MAX_RESPONSE_BYTES, "Response too large")
        value = loads(raw)
        require(isinstance(value, dict), "Invalid response object")
        if "error" in value:
            raise api_error(value["error"])
        return value
    except HTTPError as error:
        retry_after = error.headers.get("Retry-After") if error.headers else None
        error.close()
        if error.code == 429:
            # Honor numeric delays up to 10 seconds. Longer/date-form delays fail
            # without an automatic retry rather than hammering a limited endpoint.
            delay = None if retry_after is None else (
                int(retry_after) if len(retry_after) <= 3 and retry_after.isdecimal() else 11)
            raise RateLimitError("Rate limited; wait before retrying (HTTP 429)", delay) from None
        messages = {400: "Model/provider request parameters rejected", 401: "API key rejected",
                    402: "Credits or key spending limit exhausted",
                    403: "Account policy denied request", 404: "No permitted model/provider endpoint",
                    429: "Rate limited; wait before retrying"}
        raise UpstreamError(messages.get(error.code, "OpenRouter HTTP failure")
                            + f" (HTTP {error.code})") from None
    except (URLError, OSError, ValueError):
        raise UpstreamError("OpenRouter connection, timeout, or response-format failure") from None


class SyntheticNotes:
    """Check outgoing text against the committed corpus, including split note portions.

    CARLOS independently verifies the complete chart and authorization. This second
    check prevents a caller's synthetic flag alone from authorizing cloud disclosure.
    """
    def __init__(self):
        fixtures = loads((REPO / "src/main/resources/clinical/summary/nhs-generation-fixtures.json").read_text())
        expected = {(note["sha256"], note["date"]): fixture["chart_no"]
                    for fixture in fixtures for note in fixture["notes"]}
        seed = (REPO / ".devcontainer/db/scripts/nhs-synthetic/patients.sql").read_text()
        pattern = (r"INSERT INTO casemgmt_note [^\n]+?SELECT @nhs_demographic_no,'999998',"
                   r"CONVERT\(0x([0-9a-fA-F]+) USING utf8mb4\),CONVERT\(0x([0-9a-fA-F]+) USING utf8mb4\)")
        self.notes = []
        found = set()
        for body_hex, date_hex in re.findall(pattern, seed):
            body = bytes.fromhex(body_hex).decode("utf-8")
            date = bytes.fromhex(date_hex).decode("utf-8")[:10]
            fingerprint = (hashlib.sha256(body.encode("utf-8")).hexdigest(), date)
            require(fingerprint in expected and fingerprint not in found, "Synthetic seed/manifest mismatch")
            found.add(fingerprint)
            require(BOUNDARY in body, "Missing fixture boundary")
            self.notes.append((expected[fingerprint], date, body.split(BOUNDARY, 1)[1]))
        require(found == set(expected), "Synthetic seed/manifest incomplete")

    def validate(self, sources):
        candidates = {fixture for fixture, _date, _body in self.notes}
        for source in sources:
            require(re.fullmatch(r"demographic-[1-9][0-9]{0,9}", source["patient_id"])
                    and re.fullmatch(r"note-[1-9][0-9]{0,9}", source["id"])
                    and source["title"] == "Signed encounter note (" + source["id"] + ")",
                    "Only the CARLOS synthetic note contract is supported")
            candidates &= {fixture for fixture, date, body in self.notes
                           if date == source["date"] and source["text"] in body}
            require(candidates, "Source does not match the committed synthetic clinical text")


class Gateway:
    def __init__(self, config, transport=api_request, clock=time.monotonic):
        self.config = dict(config)
        self.transport, self.clock = transport, clock
        self.prompt = (ROOT / "prompt.txt").read_text()
        self.schema = loads((ROOT / "output-schema.json").read_text())
        self.allowed = SyntheticNotes()
        self.cache = OrderedDict()
        self.cache_bytes = 0
        self.cache_hits = 0
        self.deadline = None

    def validate_output(self, sources, output):
        bundle = {"patient_context": {"id": sources[0]["patient_id"],
                                      "label": "Verified synthetic test fixture", "synthetic": True},
                  "sources": sources, "fact_ledger": []}
        build_artifact(bundle, output, self.config["model"], "gateway-validation",
                       datetime.now(timezone.utc).isoformat(), allow_empty=True)

    def infer(self, sources):
        remaining = 540 if self.deadline is None else self.deadline - self.clock()
        if remaining <= 0:
            raise UpstreamError("Generation exceeded the gateway time budget; no partial draft accepted")
        payload = {"model": self.config["model"], "stream": False, "temperature": 0,
                   "max_tokens": self.config["max_tokens"],
                   "reasoning": {"enabled": False},  # Match the local Qwen non-thinking mode.
                   "provider": {"only": [self.config["provider"]], "allow_fallbacks": False,
                                "require_parameters": True, "data_collection": "deny", "zdr": True},
                   "messages": [{"role": "system", "content": self.prompt},
                                {"role": "user", "content": json.dumps({"sources": sources})}],
                   "response_format": {"type": "json_schema", "json_schema": {
                       "name": "clinical_summary", "strict": True,
                       "schema": openrouter_schema(self.schema, sources)}}}
        # Per-process cache; configuration and credentials cannot change during this process.
        key = hashlib.sha256(json.dumps(payload, sort_keys=True).encode("utf-8")).digest()
        now = self.clock()
        for expired in [key for key, (expiry, _raw) in self.cache.items() if expiry <= now]:
            self.cache_bytes -= len(self.cache.pop(expired)[1])
        if key in self.cache:
            self.cache.move_to_end(key)
            self.cache_hits += 1
            return loads(self.cache[key][1])
        for attempt in range(3):
            remaining = 540 if self.deadline is None else self.deadline - self.clock()
            if remaining <= 0:
                raise UpstreamError("Generation exceeded the gateway time budget; no partial draft accepted")
            call_config = dict(self.config, timeout_seconds=min(self.config["timeout_seconds"], remaining))
            try:
                result = self.transport(call_config, "chat/completions", payload)
                break
            except RateLimitError as error:
                delay = max(2 ** (attempt + 1), error.retry_after or 0)
                remaining = 540 if self.deadline is None else self.deadline - self.clock()
                if attempt == 2 or delay > 10 or delay >= remaining:
                    raise
                print(f"Provider rate limited; retry {attempt + 1}/2 in {delay}s", flush=True)
                time.sleep(delay)
        if self.deadline is not None and self.clock() >= self.deadline:
            raise UpstreamError("Generation exceeded the gateway time budget; no partial draft accepted")
        require(isinstance(result, dict) and "error" not in result
                and result.get("model") == self.config["model"], "Unexpected model or error response")
        choices = result.get("choices")
        require(isinstance(choices, list) and len(choices) == 1 and isinstance(choices[0], dict),
                "Expected one completion")
        choice = choices[0]
        if choice.get("finish_reason") == "length":
            raise pipeline.OutputLimitError()
        require(choice.get("finish_reason") == "stop", "Incomplete or refused completion")
        message = choice.get("message")
        require(isinstance(message, dict) and not message.get("refusal") and not message.get("tool_calls")
                and isinstance(message.get("content"), str), "Missing assistant JSON")
        output = loads(message["content"])
        self.validate_output(sources, output)
        raw = json.dumps(output).encode("utf-8")
        if self.config["cache_seconds"] and len(raw) <= MAX_RESPONSE_BYTES:
            self.cache[key] = (self.clock() + self.config["cache_seconds"], raw)
            self.cache_bytes += len(raw)
            while len(self.cache) > 128 or self.cache_bytes > 16 * 1024 * 1024:
                self.cache_bytes -= len(self.cache.popitem(last=False)[1][1])
        return output

    def run(self, request):
        self.deadline = self.clock() + 540  # Below CARLOS's configured 600-second HTTP timeout.
        validate_request(request)
        require(request["instructions"].strip() == self.prompt.strip()
                and request["output_schema"] == self.schema, "Unexpected prompt or schema")
        self.allowed.validate(request["sources"])  # Before cache lookup or network access.
        output = pipeline.generate(request["sources"], self.prompt, self.schema,
                                   self.infer, self.validate_output)
        self.validate_output(request["sources"], output)
        return {"contract_version": 1, "request_id": request["request_id"],
                "status": "completed", "output": output}


def handler_for(gateway):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def respond(self, status, value):
            raw = json.dumps(value).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_GET(self):
            self.respond(200 if self.path == "/health" else 404,
                         {"service": "carlos-openrouter-synthetic", "model": gateway.config["model"],
                          "provider": gateway.config["provider"],
                          "cache_hits": gateway.cache_hits} if self.path == "/health" else {"error": "Not found"})

        def do_POST(self):
            self.connection.settimeout(10)
            if self.path != PATH:
                self.respond(404, {"error": "Not found"})
                return
            try:
                lengths = self.headers.get_all("Content-Length", [])
                require(len(lengths) == 1 and not self.headers.get("Transfer-Encoding"), "Invalid framing")
                length = int(lengths[0])
                require(0 < length <= MAX_REQUEST_BYTES, "Invalid request size")
                raw = self.rfile.read(length)
                require(len(raw) == length, "Incomplete request")
                started, hits = time.monotonic(), gateway.cache_hits
                output = gateway.run(loads(raw))
                require(len(json.dumps(output).encode("utf-8")) <= MAX_RESPONSE_BYTES, "Oversized output")
                self.respond(200, output)
                print(f"Completed pass in {time.monotonic() - started:.1f}s; "
                      f"cache hits: {gateway.cache_hits - hits}", flush=True)
            except UpstreamError as error:
                print(str(error), flush=True)  # Fixed diagnostics, never upstream body or credential.
                self.respond(502, {"error": "OpenRouter unavailable; see local gateway status"})
            except (ValueError, TypeError, KeyError, OSError):
                print("Request or generated draft failed validation; nothing accepted", flush=True)
                try:
                    self.respond(400, {"error": "Request or generated draft failed validation"})
                except OSError:
                    pass
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("configure", "check", "serve"))
    parser.add_argument("--config", type=Path)
    parser.add_argument("--model", default=DEFAULTS["model"])
    parser.add_argument("--provider", default=DEFAULTS["provider"])
    parser.add_argument("--cache-seconds", type=int, default=DEFAULTS["cache_seconds"])
    args = parser.parse_args()
    path = args.config or runtime_directory() / "openrouter/config.json"
    try:
        if args.command == "configure":
            require(sys.stdin.isatty(), "Run configure in an interactive terminal for hidden key entry")
            config = dict(DEFAULTS, model=args.model, provider=args.provider,
                          cache_seconds=args.cache_seconds,
                          api_key=getpass.getpass("OpenRouter API key (hidden): ").strip())
            # Validate before replacing a working configuration.
            with tempfile.TemporaryDirectory() as temporary:
                trial = Path(temporary) / "config.json"
                private_write(trial, json.dumps(config))
                read_config(trial)
            private_write(path, json.dumps(config, indent=2) + "\n")
            print(f"Saved private configuration to {path}. Key was not printed.")
            return
        config = read_config(path)
        if args.command == "check":
            result = api_request(config, "key")
            require(isinstance(result.get("data"), dict), "Invalid key-check response")
            print("API key accepted. No model inference requested; model access is checked on generation.")
            return
        gateway = Gateway(config)
        with HTTPServer(("127.0.0.1", config["port"]), handler_for(gateway)) as server:
            print(f"OpenRouter synthetic gateway: 127.0.0.1:{config['port']} / {config['model']}; "
                  f"provider {config['provider']}; "
                  f"memory cache {config['cache_seconds']}s. Keep this terminal open.", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
    except (OSError, ValueError, TypeError, KeyError, UpstreamError) as error:
        # Config/OS exceptions can contain private content; only upstream's fixed messages are safe.
        message = str(error) if isinstance(error, UpstreamError) else "Check the config, permissions, fixture files and port availability."
        parser.exit(1, message + "\n")


if __name__ == "__main__":
    main()
