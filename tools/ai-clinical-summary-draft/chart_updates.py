# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Synthetic-only proposal extraction. No chart data, credentials or write tools reach the model."""
import json

import document_summary as document
import document_distill as distill
from validate_artifact import require

PATH = '/v1/chart-update-proposals'
PROMPT = (document.RESOURCES / 'chart-update-prompt.txt').read_text(encoding='utf-8')
SCHEMA = json.loads((document.RESOURCES / 'chart-update-schema.json').read_text(encoding='utf-8'))


def validate_request(request, notes, request_bytes):
    require(isinstance(request, dict) and request.get('workflow') == 'chart-update-proposals'
            and request.get('instructions') == PROMPT and request.get('output_schema') == SCHEMA,
            'Unexpected proposal contract')
    # Reuse complete-note disclosure, envelope, metadata and byte-budget checks.
    document.validate_request(dict(request, workflow='single-document-summary',
                                   instructions=document.PROMPT, output_schema=document.SCHEMA),
                              notes, request_bytes)
    require(len(json.dumps(request, ensure_ascii=False, separators=(',', ':')).encode('utf-8'))
            <= request_bytes, 'Proposal request exceeds budget')


def validate_output(output, source):
    require(isinstance(output, dict) and set(output) == {'proposals'}, 'Invalid proposal output')
    rows = output['proposals']
    require(isinstance(rows, list) and len(rows) <= 20, 'Invalid proposal count')
    seen = set()
    for row in rows:
        require(isinstance(row, dict) and set(row) == {'kind', 'evidence'}
                and row['kind'] in ('tickler', 'history'), 'Invalid proposal kind')
        text = row['evidence']
        require(isinstance(text, str) and text.strip() and len(text.encode('utf-16-le')) // 2 <= 2000
                and text in source and text not in seen, 'Invalid proposal evidence')
        seen.add(text)


def run(config, request, notes, complete):
    validate_request(request, notes, config['request_bytes'])
    source = request['sources'][0]['text']
    output = complete(distill.payload(config, PROMPT, {'sources': request['sources']}, SCHEMA))
    validate_output(output, source)
    return {'contract_version': 1, 'request_id': request['request_id'], 'status': 'completed', 'output': output}
