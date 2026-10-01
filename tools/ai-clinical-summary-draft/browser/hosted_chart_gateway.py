#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Isolated synthetic trial: hosted extraction with a persistent, validated result cache."""
import argparse
import hashlib
import json
from http.server import HTTPServer
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import chart_updates
import document_distill
import openrouter_agent as agent
from local_chart_gateway import allowed_notes


class Gateway(agent.Gateway):
    def __init__(self, config, cache, **kwargs):
        super().__init__(config, **kwargs)
        self.cache_directory = Path(cache)
        self.chart_config = dict(self.config, max_tokens=8192)
        # This trial alone also accepts the exact, locally rebuilt NHSSYN005 compilation.
        # The inherited single-document summary route keeps its original note allow-list.
        self.trial_notes = allowed_notes()
        self.implementation = hashlib.sha256(b''.join(Path(module.__file__).read_bytes()
            for module in (chart_updates, document_distill, agent, agent.pipeline))).hexdigest()

    def cache_file(self, request):
        key = {'request': {k: v for k, v in request.items() if k != 'request_id'},
               'implementation': self.implementation,
               'settings': {k: self.chart_config[k] for k in ('model', 'provider', 'temperature', 'max_tokens')}}
        digest = hashlib.sha256(json.dumps(key, sort_keys=True).encode()).hexdigest()
        return self.cache_directory / (digest + '.json')

    def run_chart_updates(self, request):
        chart_updates.validate_request(request, self.trial_notes, self.config['request_bytes'])
        path = self.cache_file(request)
        if path.exists():
            output = agent.loads(path.read_text())
            chart_updates.validate_output(output, request['sources'][0]['text'])
            self.cache_hits += 1
        else:
            self.deadline = time.monotonic() + 540
            try:
                response = chart_updates.run(self.chart_config, request, self.trial_notes, self.complete)
            except agent.pipeline.OutputLimitError:
                raise agent.UpstreamError('Proposal completion exceeded its output limit; no proposals accepted') from None
            output = response['output']
            agent.private_write(path, json.dumps(output) + '\n')
        return {'contract_version': 1, 'request_id': request['request_id'],
                'status': 'completed', 'output': output}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=11440)
    parser.add_argument('--config', type=Path)
    parser.add_argument('--cache', type=Path, required=True)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error('Invalid loopback port')
    config = agent.read_config(args.config or agent.runtime_directory() / 'openrouter/config.json')
    config.update(request_bytes=50000)
    gateway = Gateway(config, args.cache)
    with HTTPServer(('127.0.0.1', args.port), agent.handler_for(gateway)) as server:
        print(f'Hosted synthetic chart trial: {config["model"]}, {config["provider"]}, port {args.port}', flush=True)
        server.serve_forever()


if __name__ == '__main__':
    main()
