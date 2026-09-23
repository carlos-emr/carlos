#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Paid, uncached comparison of three hosted MoE stacks on six committed synthetic notes.

Uses the unchanged document prompt/schema/validator and the existing private credential file.
Does not alter the running gateway configuration. Raw reports contain synthetic text and output,
never credentials or reasoning traces. Latency compares hosted stacks, not isolated hardware.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import statistics
import time
import uuid

import document_summary as document
import openrouter_agent as agent

# Note numbers are zero-based positions within each committed fixture. Selection is fixed before
# seeing model outputs: short prose, narrative investigations, multiline fields, and medication plans.
CASES = [("NHSSYN001", 0, "triage"), ("NHSSYN001", 3, "investigations"),
         ("NHSSYN001", 4, "multiline-assessment"), ("NHSSYN002", 13, "medication-plan"),
         ("NHSSYN002", 16, "postoperative-plan"), ("NHSSYN003", 14, "discharge-plan")]
STACKS = [("qwen35-parasail", "qwen/qwen3.5-35b-a3b", "parasail"),
          ("qwen30-siliconflow", "qwen/qwen3-30b-a3b-instruct-2507", "siliconflow"),
          ("nemotron30-crusoe", "nvidia/nemotron-3-nano-30b-a3b", "crusoe")]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--repeats', type=int, choices=range(1, 4), default=2)
    args = parser.parse_args()
    base = agent.read_config(agent.runtime_directory() / 'openrouter/config.json')
    notes, _labels = agent.committed_notes()
    cases = []
    for fixture, index, label in CASES:
        body = [body for chart, _date, body in notes if chart == fixture][index]
        cases.append({'case': label, 'fixture': fixture, 'note_index': index,
                      'characters': len(body), 'sha256': hashlib.sha256(body.encode()).hexdigest(),
                      'text': body})
    report = {'started_at': datetime.now(timezone.utc).isoformat(),
              'prompt_sha256': hashlib.sha256(document.PROMPT.encode()).hexdigest(),
              'schema_sha256': hashlib.sha256(json.dumps(document.SCHEMA, sort_keys=True).encode()).hexdigest(),
              'settings': {'temperature': 0, 'max_tokens': 4096, 'timeout_seconds': 90,
                           'reasoning': False, 'output_cache': False, 'provider_fallbacks': False,
                           'data_collection': 'deny', 'zdr': True},
              'caveats': ['Different providers/quantizations/hardware; this is a hosted-stack comparison.',
                          'Qwen3 Instruct is non-reasoning: omit unsupported reasoning parameter.',
                          'Validation is not clinical accuracy; no automatic repair or prompt tuning.',
                          'Upstream input-prefix caching may occur; reported cached tokens are retained.'],
              'cases': cases, 'runs': []}
    for repeat in range(args.repeats):
        for case_index, case in enumerate(cases):
            rotation = (repeat + case_index) % len(STACKS)
            for label, model, provider in STACKS[rotation:] + STACKS[:rotation]:
                config = dict(base, model=model, provider=provider, cache_seconds=0,
                              timeout_seconds=90, temperature=0, max_tokens=4096)
                calls = []
                raw_output = []

                def transport(settings, endpoint, payload):
                    payload = dict(payload)
                    if model == 'qwen/qwen3-30b-a3b-instruct-2507':
                        payload.pop('reasoning', None)
                    started = time.monotonic()
                    result = agent.api_request(settings, endpoint, payload)
                    usage = result.get('usage') or {}
                    choice = (result.get('choices') or [{}])[0]
                    calls.append({'seconds': round(time.monotonic() - started, 3),
                                  'provider': result.get('provider'), 'model': result.get('model'),
                                  'prompt_tokens': usage.get('prompt_tokens'),
                                  'completion_tokens': usage.get('completion_tokens'),
                                  'cached_tokens': (usage.get('prompt_tokens_details') or {}).get('cached_tokens'),
                                  'reasoning_tokens': (usage.get('completion_tokens_details') or {}).get('reasoning_tokens'),
                                  'cost_usd': usage.get('cost'), 'finish_reason': choice.get('finish_reason')})
                    content = (choice.get('message') or {}).get('content')
                    if isinstance(content, str):
                        try:
                            raw_output.append(agent.loads(content))
                        except ValueError:
                            pass
                    return result

                gateway = agent.Gateway(config, transport=transport)
                request = {'contract_version': 1, 'request_id': str(uuid.uuid4()),
                           'workflow': 'single-document-summary', 'data_classification': 'clinical-document',
                           'instructions': document.PROMPT, 'output_schema': document.SCHEMA,
                           'sources': [{'id': 'document', 'title': 'Document', 'text': case['text']}]}
                row = {'stack': label, 'case': case['case'], 'repeat': repeat + 1, 'calls': calls}
                started = time.monotonic()
                try:
                    gateway.run_document(request)
                    row['accepted'] = True
                except (ValueError, OSError, agent.UpstreamError) as failure:
                    row.update(accepted=False, error=str(failure))
                row['seconds'] = round(time.monotonic() - started, 3)
                if raw_output:
                    row['output'] = raw_output[-1]
                    points = row['output'].get('points', []) if isinstance(row['output'], dict) else []
                    excerpts = [e for p in points if isinstance(p, dict) for e in p.get('evidence', [])
                                if isinstance(e, str)]
                    row['points'] = len(points)
                    row['excerpts'] = len(excerpts)
                    row['nonverbatim_excerpts'] = sum(e not in case['text'] for e in excerpts)
                report['runs'].append(row)
                agent.private_write(args.output, json.dumps(report, indent=2) + '\n')
                print(json.dumps({k: v for k, v in row.items() if k not in ('calls', 'output')}), flush=True)
    report['finished_at'] = datetime.now(timezone.utc).isoformat()
    report['summary'] = {}
    for label, _model, _provider in STACKS:
        rows = [r for r in report['runs'] if r['stack'] == label]
        accepted = [r for r in rows if r['accepted']]
        costs = [c['cost_usd'] for r in rows for c in r['calls'] if isinstance(c.get('cost_usd'), (int, float))]
        report['summary'][label] = {'runs': len(rows), 'accepted': len(accepted),
            'median_seconds_all': round(statistics.median(r['seconds'] for r in rows), 3),
            'range_seconds_all': [min(r['seconds'] for r in rows), max(r['seconds'] for r in rows)],
            'median_seconds_accepted': round(statistics.median(r['seconds'] for r in accepted), 3) if accepted else None,
            'reported_cost_usd': round(sum(costs), 6),
            'nonverbatim_excerpts': sum(r.get('nonverbatim_excerpts', 0) for r in rows),
            'total_excerpts': sum(r.get('excerpts', 0) for r in rows)}
    agent.private_write(args.output, json.dumps(report, indent=2) + '\n')
    print(json.dumps(report['summary'], indent=2))


if __name__ == '__main__':
    main()
