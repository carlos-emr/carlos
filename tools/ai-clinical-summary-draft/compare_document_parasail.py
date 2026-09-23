#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Paid synthetic-only comparison of document summary modes and reading volume on Parasail."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import statistics
import time
import uuid

import document_summary as document
import document_fidelity as fidelity
import document_distill as distill
import openrouter_agent as agent
from compare_document_models import CASES

# Fixed before evaluating the reference approach. Disjoint from the original six notes.
HOLDOUT = [("NHSSYN001", 7, "holdout-1"), ("NHSSYN001", 12, "holdout-2"),
           ("NHSSYN002", 2, "holdout-3"), ("NHSSYN002", 8, "holdout-4"),
           ("NHSSYN003", 3, "holdout-5"), ("NHSSYN003", 9, "holdout-6")]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument('--holdout', action='store_true')
    selection.add_argument('--patient-range', nargs=2, type=int, metavar=('FIRST', 'LAST'),
                           help='Longest note per synthetic patient in inclusive range 1–50')
    selection.add_argument('--next-patients', action='store_true',
                           help='Longest complete note from each of NHSSYN014–NHSSYN023')
    selection.add_argument('--new-patients', action='store_true',
                           help='Longest complete note from each of NHSSYN004–NHSSYN013')
    parser.add_argument('--note-rank', type=int, choices=(1, 2), default=1,
                        help='Use the longest or second-longest note per patient in a range')
    parser.add_argument('--case', choices=[c[2] for c in CASES + HOLDOUT]
                        + [f'NHSSYN{n:03d}' for n in range(1, 51)])
    parser.add_argument('--modes', nargs='+', choices=('baseline', 'references', 'fidelity', 'brief', 'distill', 'balanced'), default=['baseline', 'references'])
    parser.add_argument('--reasoning-tokens', type=int, choices=(0, 512, 1024), default=0)
    parser.add_argument('--repeats', type=int, choices=(1, 2, 3), default=2)
    args = parser.parse_args()
    if args.reasoning_tokens and any(mode in ('distill', 'balanced') for mode in args.modes):
        parser.error('--reasoning-tokens is only supported by the historical single-pass modes')
    if args.patient_range and not 1 <= args.patient_range[0] <= args.patient_range[1] <= 50:
        parser.error('--patient-range must be ordered within 1–50')
    config = dict(agent.read_config(agent.runtime_directory() / 'openrouter/config.json'),
                  model='qwen/qwen3.5-35b-a3b', provider='parasail', cache_seconds=0,
                  timeout_seconds=90, temperature=0, max_tokens=4096)
    notes, _ = agent.committed_notes()
    report = {'started_at': datetime.now(timezone.utc).isoformat(),
              'model': config['model'], 'provider': config['provider'],
              'reference_reasoning_tokens': args.reasoning_tokens,
              'note_rank': args.note_rank,
              'settings': {k: config[k] for k in ('temperature', 'max_tokens', 'timeout_seconds', 'cache_seconds')},
              'prompts': {'baseline': document.PROMPT, 'references': document.REFERENCE_PROMPT,
                          'fidelity': fidelity.EXTRACTIVE_PROMPT, 'brief': fidelity.PROMPT, 'distill': distill.PROMPT, 'distill_review': distill.REVIEW_PROMPT, 'balanced': distill.CONTEXT_PROMPT},
              'cases': [], 'runs': []}
    selected = HOLDOUT if args.holdout else CASES
    if args.new_patients or args.next_patients or args.patient_range:
        selected = []
        first, last = args.patient_range or ((14, 23) if args.next_patients else (4, 13))
        for n in range(first, last + 1):
            fixture = f'NHSSYN{n:03d}'
            bodies = [b for f, _, b in notes if f == fixture]
            index = sorted(range(len(bodies)), key=lambda i: len(bodies[i]), reverse=True)[args.note_rank - 1]
            selected.append((fixture, index, fixture))
    report['selection'] = (f'longest-note-per-patient-{args.patient_range}' if args.patient_range else
                           'longest-note-from-each-of-ten-new-patients' if args.new_patients
                           else 'longest-note-from-each-of-next-ten-patients' if args.next_patients
                           else 'holdout' if args.holdout else 'development')
    for fixture, index, label in selected:
        if args.case and label != args.case:
            continue
        body = [b for f, _, b in notes if f == fixture][index]
        report['cases'].append({'case': label, 'fixture': fixture, 'note_index': index,
                                'sha256': hashlib.sha256(body.encode()).hexdigest(), 'text': body})
    if not report["cases"]:
        parser.error("--case must belong to the selected patient/note set")
    for repeat in range(args.repeats):
        for i, case in enumerate(report['cases']):
            modes = list(dict.fromkeys(args.modes))
            if (i + repeat) % 2:
                modes.reverse()
            for mode in modes:
                if mode not in args.modes:
                    continue
                body = case['text']
                request = {'contract_version': 1, 'request_id': str(uuid.uuid4()),
                           'workflow': 'single-document-summary', 'data_classification': 'clinical-document',
                           'instructions': document.PROMPT, 'output_schema': document.SCHEMA,
                           'sources': [{'id': 'document', 'title': 'Document', 'text': body}]}
                # Same disclosure boundary as run_document, before any request construction or network access.
                document.validate_request(request, notes, config['request_bytes'])
                refs = mode != 'baseline'
                payload, passages = ((None, None) if mode in ('distill', 'balanced') else
                                     fidelity.completion_payload(config, body, compact=mode == 'brief') if mode in ('fidelity', 'brief')
                                     else document.completion_payload(config, body, references=refs))
                if refs and args.reasoning_tokens:
                    payload['reasoning'] = {'enabled': True, 'exclude': True,
                                            'max_tokens': args.reasoning_tokens}
                calls = []

                def transport(settings, endpoint, request_payload):
                    response = agent.api_request(settings, endpoint, request_payload)
                    calls.append({'provider': response.get('provider'), 'model': response.get('model'),
                                  'usage': response.get('usage'),
                                  'finish_reason': (response.get('choices') or [{}])[0].get('finish_reason')})
                    return response

                gateway = agent.Gateway(config, transport=transport)
                gateway.deadline = time.monotonic() + 540
                row = {'mode': mode, 'case': case['case'], 'repeat': repeat + 1, 'calls': calls}
                started = time.monotonic()
                try:
                    if mode in ('distill', 'balanced'):
                        row['review_trace'] = []
                        output = distill.run(config, body, gateway.complete, row['review_trace'], protect=mode == 'balanced')
                    else:
                        raw = gateway.complete(payload)
                        row['raw_output'] = raw
                        output = (fidelity.resolve(raw, passages) if mode in ('fidelity', 'brief')
                                  else document.resolve_references(raw, passages) if refs else raw)
                    row['output'] = output
                    document.validate_output(output, body)
                    row['accepted'] = True
                    row['source_words'] = len(body.split())
                    row['point_words'] = sum(len(p['text'].split()) for p in output['points'])
                    row['point_characters'] = sum(len(p['text']) for p in output['points'])
                    row['word_ratio'] = round(row['point_words'] / row['source_words'], 4)
                except agent.pipeline.OutputLimitError:
                    row.update(accepted=False, error='Document completion exceeded output limit')
                except (ValueError, OSError, agent.UpstreamError) as error:
                    row.update(accepted=False, error=str(error))
                row['seconds'] = round(time.monotonic() - started, 3)
                report['runs'].append(row)
                agent.private_write(args.output, json.dumps(report, indent=2) + '\n')
                print(json.dumps({k: v for k, v in row.items() if k not in ('calls', 'output', 'raw_output', 'review_trace')}), flush=True)
    report['finished_at'] = datetime.now(timezone.utc).isoformat()
    report['summary'] = {}
    for mode in dict.fromkeys(args.modes):
        rows = [r for r in report['runs'] if r['mode'] == mode]
        report['summary'][mode] = {
            'accepted': sum(r['accepted'] for r in rows), 'runs': len(rows),
            'median_word_ratio': statistics.median(r['word_ratio'] for r in rows if r['accepted']) if any(r['accepted'] for r in rows) else None,
            'median_seconds': round(statistics.median(r['seconds'] for r in rows), 3),
            'completion_tokens': sum(c['usage']['completion_tokens'] for r in rows for c in r['calls']),
            'cost_usd': sum(c['usage']['cost'] for r in rows for c in r['calls'])}
    agent.private_write(args.output, json.dumps(report, indent=2) + '\n')
    print(json.dumps(report['summary'], indent=2))


if __name__ == '__main__':
    main()
