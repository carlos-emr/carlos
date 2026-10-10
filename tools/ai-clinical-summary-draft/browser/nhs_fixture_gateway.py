#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Fixed NHS proposals for application integration testing; this does not run a model.

Only three complete, checksum-verified committed NHS notes are accepted. No network
calls, chart writes or model credentials are used. Start explicitly on loopback and
configure only an isolated CARLOS test instance to use its port.
"""
import argparse
import hashlib
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import chart_updates
from openrouter_agent import committed_notes

# Shared with ChartUpdateNhsFixtureGatewayUnitTest, which runs every passage through the server's own
# validation (ChartUpdateProposals.validate), so these cannot drift from its passage rules again.
FIXTURE = json.loads((Path(__file__).resolve().parent / 'nhs_fixture_proposals.json').read_text(encoding='utf-8'))
CASES = {name: (case['note_index'], case['proposals']) for name, case in FIXTURE['cases'].items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=11438)
    args = parser.parse_args()
    notes, _ = committed_notes()
    outputs = {}
    for fixture, (index, proposals) in CASES.items():
        source = [body for key, _date, body in notes if key == fixture][index]
        if hashlib.sha256(source.encode('utf-8')).hexdigest() != FIXTURE['cases'][fixture]['source_sha256']:
            raise ValueError('Committed synthetic note changed; update nhs_fixture_proposals.json')
        output = {'proposals': proposals}
        chart_updates.validate_output(output, source)
        outputs[source] = output

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            try:
                if self.path != chart_updates.PATH:
                    raise ValueError('Unknown operation')
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 50000:
                    raise ValueError('Invalid request size')
                request = json.loads(self.rfile.read(length))
                chart_updates.validate_request(request, notes, 50000)
                output = outputs[request['sources'][0]['text']]
                body = json.dumps({'contract_version': 1, 'request_id': request['request_id'],
                                   'status': 'completed', 'output': output}).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (KeyError, TypeError, ValueError):
                self.send_error(400, 'Expected a configured complete NHS synthetic fixture')

        def log_message(self, _format, *args):
            pass  # Never print request bodies, paths, or source text.

    with HTTPServer(('127.0.0.1', args.port), Handler) as server:
        print(f'Fixed synthetic proposals (NO MODEL): 127.0.0.1:{args.port}', flush=True)
        server.serve_forever()


if __name__ == '__main__':
    main()
