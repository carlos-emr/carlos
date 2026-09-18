#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Run one uncached, paid OpenRouter quality trial on a committed NHS synthetic fixture."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import time

import openrouter_agent as agent
import pipeline


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', choices=('NHSSYN001', 'NHSSYN002', 'NHSSYN003'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--config', type=Path)
    parser.add_argument('--temperature', type=float)
    parser.add_argument('--request-bytes', type=int)
    parser.add_argument('--reasoning-tokens', type=int)
    parser.add_argument('--timeout-seconds', type=int)
    parser.add_argument('--section-passes', action=argparse.BooleanOptionalAction, default=None)
    parser.add_argument('--section-workers', type=int)
    parser.add_argument('--model')
    parser.add_argument('--provider')
    args = parser.parse_args()
    config = agent.read_config(args.config or agent.runtime_directory() / 'openrouter/config.json')
    for field in ('temperature', 'request_bytes', 'reasoning_tokens', 'timeout_seconds',
                  'section_passes', 'section_workers', 'model', 'provider'):
        value = getattr(args, field)
        if value is not None:
            config[field] = value
    config['cache_seconds'] = 0
    with tempfile.TemporaryDirectory() as directory:
        trial = Path(directory) / 'config.json'
        agent.private_write(trial, json.dumps(config))
        config = agent.read_config(trial)
    calls = []

    def sections_of(payload):
        """Identify a pass by the sections its response schema allows."""
        schema = payload['response_format']['json_schema']['schema']['properties']['sections']
        return schema['items']['properties']['id']['enum']

    def transport(settings, endpoint, payload):
        started = time.monotonic()
        try:
            result = agent.api_request(settings, endpoint, payload)
        except Exception as failure:
            # A failed pass costs wall time and must stay visible; reasons are fixed categories.
            calls.append({'seconds': round(time.monotonic() - started, 3),
                          'sections': sections_of(payload),
                          'error': f'{type(failure).__name__}: {failure}'})
            raise
        usage = result.get('usage') or {}
        calls.append({'seconds': round(time.monotonic() - started, 3),
                      'sections': sections_of(payload),
                      'prompt_tokens': usage.get('prompt_tokens'),
                      'completion_tokens': usage.get('completion_tokens'),
                      'reasoning_tokens': (usage.get('completion_tokens_details') or {}).get('reasoning_tokens'),
                      'cost_usd': usage.get('cost'),
                      'finish_reasons': [choice.get('finish_reason') for choice in result.get('choices', [])]})
        return result

    gateway = agent.Gateway(config, transport=transport)
    sources = [{'id': f'note-{i + 1}', 'patient_id': 'demographic-3001',
                'title': f'Signed encounter note (note-{i + 1})', 'date': date, 'text': body}
               for i, (fixture, date, body) in enumerate(gateway.allowed.notes) if fixture == args.fixture]
    gateway.allowed.validate(sources)
    report = {'fixture': args.fixture, 'settings': {k: v for k, v in config.items() if k != 'api_key'},
              'prompt_sha256': hashlib.sha256(gateway.prompt.encode()).hexdigest(),
              'sources': sources, 'calls': calls,
              'clinical_accuracy': 'Unverified; inspect every statement and omission against the sources.'}
    started = time.monotonic()
    gateway.deadline = started + 540
    try:
        output = gateway.generate(sources)
        gateway.validate_output(sources, output)
        report['output'] = output
        report['status'] = 'structurally_valid'
    except (ValueError, OSError, agent.UpstreamError) as failure:
        report['status'] = 'rejected'
        # Rejection reasons are fixed host/gateway categories, never source text or upstream bodies.
        report['error'] = f'{type(failure).__name__}: {failure}'
    report['seconds'] = round(time.monotonic() - started, 3)
    agent.private_write(args.output, json.dumps(report, indent=2) + '\n')
    print(json.dumps({'status': report['status'], 'seconds': report['seconds'], 'calls': calls,
                      'error': report.get('error'),
                      'claims': len(report.get('output', {}).get('claims', [])), 'report': str(args.output)}))
    return 0 if report['status'] == 'structurally_valid' else 1


if __name__ == '__main__':
    raise SystemExit(main())
