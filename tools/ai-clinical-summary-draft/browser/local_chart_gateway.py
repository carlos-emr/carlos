#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Local-model trial gateway: exact synthetic sources only, cached validated proposals."""
import argparse
import hashlib
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import sys
import time
from urllib.request import Request, ProxyHandler, build_opener

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import chart_updates
from openrouter_agent import committed_notes, loads, private_write, NoRedirect

MODEL = 'qwen3.5:2b'
OPTIONS = {'temperature': 0, 'num_ctx': 16384, 'num_predict': 4096, 'num_thread': 4}


def allowed_notes():
    notes, _ = committed_notes()
    selected = [(date, body) for key, date, body in notes if key == 'NHSSYN005']
    assert len(selected) == 37
    combined = '\n\n'.join(f'=== Source note {i+1} | {date} ===\n{body}'
                           for i, (date, body) in enumerate(selected))
    # Rebuild from the checksum-verified corpus, never trust uploaded file metadata.
    return (*notes, ('NHSSYN005-COMPILED', selected[-1][0], combined))


class Gateway:
    def __init__(self, cache, port=11434):
        if not 1024 <= port <= 65535:
            raise ValueError('Invalid local model port')
        self.url = f'http://127.0.0.1:{port}'
        self.cache = Path(cache)
        self.notes = allowed_notes()
        self.opener = build_opener(ProxyHandler({}), NoRedirect())

    def call(self, route, data=None):
        body = None if data is None else json.dumps(data).encode()
        request = Request(self.url + route, data=body, headers={'Content-Type': 'application/json'})
        with self.opener.open(request, timeout=1800) as response:
            raw = response.read(4 * 1024 * 1024 + 1)
        if len(raw) > 4 * 1024 * 1024:
            raise ValueError('Model response exceeds limit')
        return loads(raw)

    def generate(self, request):
        chart_updates.validate_request(request, self.notes, 50000)
        source = request['sources'][0]['text']
        info = self.call('/api/show', {'model': MODEL})
        if info.get('remote_model') or info.get('remote_host'):
            raise ValueError('A local model is required')
        tag = next(row for row in self.call('/api/tags')['models'] if row['name'] == MODEL)
        identity = {'model': MODEL, 'digest': tag['digest'], 'version': self.call('/api/version')['version']}
        payload = {'model': MODEL, 'system': request['instructions'],
                   'prompt': json.dumps({'sources': request['sources']}), 'stream': False, 'think': False,
                   'keep_alive': '10m', 'format': request['output_schema'], 'options': OPTIONS}
        key = hashlib.sha256(json.dumps([identity, payload], sort_keys=True).encode()).hexdigest()
        cached = self.cache / (key + '.json')
        if cached.exists():
            record = loads(cached.read_text())
            output = record['output']
            chart_updates.validate_output(output, source)
            print('Validated local model cache hit', flush=True)
        else:
            start = time.monotonic()
            raw_path = self.cache / (key + '-raw.json')
            if raw_path.exists():
                raw = loads(raw_path.read_text())
            else:
                raw = self.call('/api/generate', payload)
                # Keep rejected responses too; retrying the UI must not silently generate again.
                private_write(raw_path, json.dumps(raw, indent=2) + '\n')
            if raw.get('model') != MODEL or raw.get('done') is not True or raw.get('done_reason') != 'stop':
                raise ValueError('Incomplete local model response')
            output = loads(raw['response'])
            chart_updates.validate_output(output, source)
            record = {'identity': identity, 'options': OPTIONS, 'sourceSha256': hashlib.sha256(source.encode()).hexdigest(),
                      'promptSha256': hashlib.sha256(request['instructions'].encode()).hexdigest(),
                      'seconds': round(time.monotonic() - start, 3),
                      'promptTokens': raw.get('prompt_eval_count'), 'outputTokens': raw.get('eval_count'),
                      'output': output}
            private_write(cached, json.dumps(record, indent=2) + '\n')
            print(f"Validated live local model result: {len(output['proposals'])} proposals", flush=True)
        return {'contract_version': 1, 'request_id': request['request_id'], 'status': 'completed', 'output': output}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=11439)
    parser.add_argument('--ollama-port', type=int, default=11434)
    parser.add_argument('--cache', type=Path, required=True)
    args = parser.parse_args()
    gateway = Gateway(args.cache, args.ollama_port)

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            try:
                if self.path != chart_updates.PATH:
                    raise ValueError('Unknown operation')
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 50000:
                    raise ValueError('Invalid request size')
                result = gateway.generate(loads(self.rfile.read(length)))
                body = json.dumps(result).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except Exception as error:
                print('Local trial rejected request:', type(error).__name__, flush=True)
                self.send_error(502, 'Local model unavailable or output failed validation; nothing saved')

        def log_message(self, _format, *args):
            pass

    with HTTPServer(('127.0.0.1', args.port), Handler) as server:
        print(f'Local synthetic trial gateway: {MODEL}, port {args.port}', flush=True)
        server.serve_forever()


if __name__ == '__main__':
    main()
