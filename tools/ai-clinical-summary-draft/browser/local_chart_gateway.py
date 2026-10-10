#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Local-model trial gateway: exact synthetic sources only, cached validated proposals."""
import argparse
import hashlib
import json
from http.client import HTTPConnection, HTTPException
from http.server import HTTPServer
from pathlib import Path
import socket
import sys
from threading import Timer
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import chart_updates
import document_distill
import openrouter_agent as agent
from openrouter_agent import committed_notes, loads, private_write

MODEL = 'qwen3.5:2b'
REQUEST_TIMEOUT_SECONDS = 1800
OPTIONS = {'temperature': 0, 'num_ctx': 16384, 'num_predict': 4096, 'num_thread': 4}


def allowed_notes():
    notes, _ = committed_notes()
    selected = [(date, body) for key, date, body in notes if key == 'NHSSYN005']
    if len(selected) != 37: raise ValueError('Unexpected synthetic corpus')
    combined = '\n\n'.join(f'=== Source note {i+1} | {date} ===\n{body}'
                           for i, (date, body) in enumerate(selected))
    # Rebuild from the checksum-verified corpus, never trust uploaded file metadata.
    return (*notes, ('NHSSYN005-COMPILED', selected[-1][0], combined))


class Gateway:
    def __init__(self, cache, port=11434):
        if not 1024 <= port <= 65535:
            raise ValueError('Invalid local model port')
        self.port = port
        self.cache = Path(cache)
        self.notes = allowed_notes()
        self.cache_hits = 0
        self.deadline = None
        self.config = dict(agent.DEFAULTS, model=MODEL, provider='local', request_bytes=50000,
                           max_tokens=OPTIONS['num_predict'])
        self.implementation = hashlib.sha256(b''.join(Path(module.__file__).read_bytes()
            for module in (chart_updates, document_distill, agent, agent.pipeline)) + Path(__file__).read_bytes()).hexdigest()

    def call(self, route, data=None):
        body = None if data is None else json.dumps(data).encode()
        deadline = self.deadline if self.deadline is not None else time.monotonic() + REQUEST_TIMEOUT_SECONDS
        def remaining():
            seconds = deadline - time.monotonic()
            if seconds <= 0:
                raise agent.UpstreamError('Local model request deadline exceeded')
            return seconds
        connection, timer = None, None
        try:
            # Direct loopback connection: never use proxies or follow redirects.
            connection = HTTPConnection('127.0.0.1', self.port, timeout=remaining())
            connection.connect()
            connected_socket = connection.sock
            def expire():
                # Interrupt blocking header/body reads, including slowly arriving bytes.
                try:
                    connected_socket.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            timer = Timer(remaining(), expire)
            timer.daemon = True
            timer.start()
            connection.request('GET' if body is None else 'POST', route, body=body,
                               headers={'Content-Type': 'application/json'})
            with connection.getresponse() as response:
                if not 200 <= response.status < 300:
                    raise agent.UpstreamError('Local model returned an unsuccessful status')
                raw = response.read(4 * 1024 * 1024 + 1)
            remaining()  # A deadline-triggered EOF must never become a successful reply.
            if len(raw) > 4 * 1024 * 1024:
                raise ValueError('Model response exceeds limit')
            result = loads(raw)
            if not isinstance(result, dict):
                raise ValueError('Invalid model response envelope')
            return result
        except (OSError, ValueError, HTTPException):
            raise agent.UpstreamError('Local model connection, timeout, or response-format failure') from None
        finally:
            if timer is not None:
                timer.cancel()
            if connection is not None:
                connection.close()

    def generate(self, request):
        chart_updates.validate_request(request, self.notes, 50000)
        self.deadline = time.monotonic() + REQUEST_TIMEOUT_SECONDS
        source = request['sources'][0]['text']
        info = self.call('/api/show', {'model': MODEL})
        if info.get('remote_model') or info.get('remote_host'):
            raise ValueError('A local model is required')
        models = self.call('/api/tags').get('models')
        if not isinstance(models, list) or any(not isinstance(row, dict) for row in models):
            raise agent.UpstreamError('Invalid local model inventory')
        tag = next((row for row in models if row.get('name') == MODEL), None)
        if tag is None:
            raise agent.UpstreamError('Configured local model is unavailable')
        version = self.call('/api/version').get('version')
        if not isinstance(tag.get('digest'), str) or not tag['digest'] or not isinstance(version, str) or not version:
            raise agent.UpstreamError('Invalid local model identity')
        identity = {'model': MODEL, 'digest': tag['digest'], 'version': version}
        key = hashlib.sha256(json.dumps([identity, OPTIONS, self.implementation,
            {k: v for k, v in request.items() if k != 'request_id'}], sort_keys=True).encode()).hexdigest()
        cached = self.cache / (key + '.json')
        if cached.exists():
            output = loads(cached.read_text())['output']
            chart_updates.validate_output(output, source)
            self.cache_hits += 1
        else:
            def complete(completion):
                payload = {'model': MODEL, 'system': completion['messages'][0]['content'],
                           'prompt': completion['messages'][1]['content'], 'stream': False, 'think': False,
                           'keep_alive': '10m', 'format': completion['response_format']['json_schema']['schema'],
                           'options': OPTIONS}
                raw_key = hashlib.sha256(json.dumps([identity, self.implementation, payload], sort_keys=True).encode()).hexdigest()
                raw_path = self.cache / (raw_key + '-raw.json')
                if raw_path.exists():
                    raw = loads(raw_path.read_text())
                else:
                    raw = self.call('/api/generate', payload)
                    private_write(raw_path, json.dumps(raw) + '\n')
                if not isinstance(raw, dict) or raw.get('model') != MODEL or raw.get('done') is not True or raw.get('done_reason') != 'stop' or not isinstance(raw.get('response'), str):
                    raise agent.UpstreamError('Incomplete local model response')
                try:
                    return loads(raw['response'])
                except ValueError:
                    raise agent.UpstreamError('Invalid local model completion') from None
            # Both backends use host-owned ranges and review every candidate against full source.
            output = chart_updates.run(self.config, request, self.notes, complete)['output']
            private_write(cached, json.dumps({'identity': identity, 'output': output}) + '\n')
        return {'contract_version': 1, 'request_id': request['request_id'], 'status': 'completed', 'output': output}

    run_chart_updates = generate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=11439)
    parser.add_argument('--ollama-port', type=int, default=11434)
    parser.add_argument('--cache', type=Path, required=True)
    args = parser.parse_args()
    gateway = Gateway(args.cache, args.ollama_port)

    class Handler(agent.handler_for(gateway)):
        def do_POST(self):
            if self.path != chart_updates.PATH:
                self.respond(404, {'error': 'Unknown operation'})
                return
            super().do_POST()

    with HTTPServer(('127.0.0.1', args.port), Handler) as server:
        print(f'Local synthetic trial gateway: {MODEL}, port {args.port}', flush=True)
        server.serve_forever()


if __name__ == '__main__':
    main()
