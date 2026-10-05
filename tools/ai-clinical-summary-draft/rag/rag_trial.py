#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Retrieval-only RAG trial on the FAKE NHS synthetic notes (NHSSYN###, default scope 001-010).

Research tool. It answers one question: does vector search (optionally fused with
keyword search) find the right parts of one patient's chart for whole-chart questions?

Safety limits, enforced in code:
- Only the committed synthetic corpus loaded through openrouter_agent.SyntheticNotes;
  every patient id must match ^NHSSYN\\d{3}$ before anything is indexed.
- Embeddings come only from the local Ollama at http://127.0.0.1:11434 (host guard).
  Only /api/embed, /api/pull, /api/tags and /api/ps are callable: no chat or generation.
- Search always filters to one patient first, then ranks exactly (no approximate index).

Subcommands: pull, index, evaluate, report (see rag/README.md).
"""
import argparse
from datetime import date
import hashlib
import json
import math
from pathlib import Path
import re
import sqlite3
import statistics
import struct
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

HERE = Path(__file__).resolve().parent
TOOL = HERE.parent
REPO = TOOL.parents[1]
OUT = REPO / 'target' / 'rag'
RESULTS = OUT / 'results'
PYLIB = OUT / 'pylib'
FACTS = TOOL / 'quality' / 'facts'
PROBES = HERE / 'probes.json'
if str(TOOL) not in sys.path:
    sys.path.insert(0, str(TOOL))

import chart_updates  # noqa: E402  (reused section helpers)

OLLAMA = 'http://127.0.0.1:11434'
OLLAMA_PATHS = frozenset({'/api/embed', '/api/pull', '/api/tags', '/api/ps'})
PATIENT_ID = re.compile(r'^NHSSYN[0-9]{3}$')  # ^NHSSYN\d{3}$ with ASCII digits only
TOP_K = 8
RRF_K = 60
CANDIDATE_DEPTH = 100
# 2026-10-05 scope: 001-003 carry the labelled facts; 004-010 are the other patients that
# per-patient filtering must exclude. The full 50-patient run was too long for the shared box.
DEFAULT_SCOPE = 'NHSSYN001-NHSSYN010'
BATCH = 16
MAX_CHARS = 1200
TARGET_CHARS = 900
TINY_CHARS = 60
QWEN_INSTRUCTION = ("Instruct: Given a clinician's question about one patient's chart, "
                    "retrieve the chart passages that answer it\nQuery: ")
# Model-card prefixes: nomic needs task prefixes, Qwen3 wants an instruction on queries only.
MODELS = {
    'nomic-embed-text': {'query': 'search_query: ', 'document': 'search_document: '},
    'bge-m3': {'query': '', 'document': ''},
    'qwen3-embedding:0.6b': {'query': QWEN_INSTRUCTION, 'document': ''},
}
MODES = ('vector', 'keyword', 'hybrid')
KINDS = ('fact', 'negation', 'family')
MISS_KINDS = ('wording mismatch', 'fact split across chunks', 'lost heading/context',
              'date/number fact', 'medication name', 'other')
DRUGS = re.compile(r'nimodipine|amlodipine|potassium chloride|kcl|ferrous|iron|tinzaparin|enoxaparin|'
                   r'paracetamol|ibuprofen|morphine|amoxiclav|omeprazole|codeine|cefuroxime', re.I)
STOPWORDS = frozenset("""a about after again all also am an and any anyone anything are as at be been before
being both but by can could did do does doing during each for from get given go going got had has have having
he her hers him his how i if in into is it its just me more most my no nor not of off on once only or other our
out over own patient patients same she should so some such than that the their them then there these they this
those through to too under until up very was we were what when where which while who whom why will with would
you your""".split())


class SafetyError(RuntimeError):
    """A hard trial limit was about to be broken; nothing was sent or indexed."""


# ---------------------------------------------------------------- safety guards

def guard_url(url):
    """Allow only plain HTTP to the loopback Ollama port and its embedding/model-list paths."""
    parts = urlsplit(url)
    host = parts.hostname
    try:
        port = parts.port
    except ValueError:
        port = None
    if (parts.scheme != 'http' or parts.username is not None or parts.password is not None
            or host not in ('127.0.0.1', '::1') or port != 11434
            or parts.path not in OLLAMA_PATHS or parts.query or parts.fragment):
        raise SafetyError('Refusing non-local or non-embedding endpoint: only loopback Ollama '
                          '/api/embed, /api/pull, /api/tags and /api/ps are allowed')
    return url


def assert_synthetic(notes):
    """Every note must belong to a committed FAKE NHS fixture id before indexing."""
    notes = list(notes)
    if not notes:
        raise SafetyError('No synthetic notes loaded')
    for patient, _date, _body in notes:
        if not isinstance(patient, str) or not PATIENT_ID.fullmatch(patient):
            raise SafetyError('Refusing to index a patient id that is not ^NHSSYN\\d{3}$')
    return notes


def load_notes():
    """The committed, checksum-verified synthetic corpus; nothing else is ever indexed."""
    from openrouter_agent import SyntheticNotes
    synthetic = SyntheticNotes()
    for patient, label in synthetic.labels.items():
        if PATIENT_ID.fullmatch(patient) and not label.startswith('FAKE-NHS'):
            raise SafetyError('Synthetic fixture label is not marked FAKE-NHS')
    return assert_synthetic(synthetic.notes)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None  # A redirect could leave loopback; urllib then raises HTTPError.


_OPENER = build_opener(ProxyHandler({}), _NoRedirect())


def ollama(path, payload=None, timeout=600, retries=6):
    """Call the local Ollama, retrying briefly (about 75 s in total) while it is busy.

    Kept short on purpose: every call runs inside a shared `heavy` build slot.
    """
    url = guard_url(OLLAMA + path)
    data = None if payload is None else json.dumps(payload).encode('utf-8')
    for attempt in range(retries):
        request = Request(url, data=data, headers={'Content-Type': 'application/json'},
                          method='GET' if data is None else 'POST')
        try:
            with _OPENER.open(request, timeout=timeout) as response:
                return json.loads(response.read().decode('utf-8'))
        except HTTPError as error:
            if error.code not in (429, 500, 502, 503) or attempt == retries - 1:
                raise RuntimeError(f'Ollama {path} failed with HTTP {error.code}') from None
        except (URLError, TimeoutError, ConnectionError):
            if attempt == retries - 1:
                raise RuntimeError(f'Ollama {path} unreachable on loopback') from None
        time.sleep(5 + 5 * attempt)
    raise RuntimeError('unreachable')


def embed(model, texts, keep_alive='5m'):
    if model not in MODELS:
        raise SafetyError('Unknown embedding model')
    reply = ollama('/api/embed', {'model': model, 'input': list(texts), 'keep_alive': keep_alive,
                                  'truncate': True})
    vectors = reply.get('embeddings')
    if not isinstance(vectors, list) or len(vectors) != len(texts):
        raise RuntimeError('Ollama returned the wrong number of embeddings')
    dims = {len(v) for v in vectors}
    if len(dims) != 1 or not all(math.isfinite(x) for v in vectors for x in v):
        raise RuntimeError('Ollama returned malformed embeddings')
    return vectors


def unload(model):
    ollama('/api/embed', {'model': model, 'input': [], 'keep_alive': 0})


def model_info(model):
    tags = ollama('/api/tags').get('models', [])
    names = {model, model + ':latest'}
    return next((m for m in tags if m.get('name') in names or m.get('model') in names), None)


def slug(model):
    return re.sub(r'[^a-z0-9]+', '-', model.lower()).strip('-')


# ---------------------------------------------------------------- chunking

GRID_HEADING = re.compile(r'[A-Z][A-Za-z /()&-]{1,39}')


def _standalone(segments, n):
    """Segment n is a whole line (not a sentence split from a longer line)."""
    return segments[n].endswith('\n') and (n == 1 or segments[n - 1].endswith('\n'))


def heading_of(segments, n):
    """Return the heading name if segment n opens a section, else None."""
    line = segments[n].strip()
    if not line:
        return None
    label, colon, _rest = line.partition(':')
    if chart_updates.passage_heading(line):
        name = label if colon else line
    elif re.match(r'^[A-Za-z][A-Za-z /()-]{0,60}:', line):  # the source_sections rule
        name = label
    elif (_standalone(segments, n) and GRID_HEADING.fullmatch(line)
          and (n == 1 or not segments[n - 1].strip())
          and n + 1 in segments and segments[n + 1].strip()):
        name = line  # grid-style form heading on its own line, e.g. "Presenting Complaint"
    else:
        return None
    name = re.sub(r'\s+', ' ', name.strip(' -=:')).strip()
    return name[:60] or None


def chunk_note(patient, note_id, note_date, body):
    """Split one note into section chunks with exact character offsets.

    chart_updates.source_segments gives lossless sentence/line segments and
    chart_updates.source_sections the coarse section partition; sections are then
    split at further clinical headings, tiny non-clinical scraps (signatures, form
    labels) are folded into the previous chunk, and long sections are cut at segment
    boundaries. Every chunk is prefixed with its date and heading for embedding.
    """
    segments = chart_updates.source_segments(body)
    offsets, position = {}, 0
    for n, text in segments.items():
        offsets[n] = position
        position += len(text)
    assert position == len(body)
    spans = []
    for section in chart_updates.source_sections(segments):
        start = section['start_id']
        for n in range(section['start_id'] + 1, section['end_id'] + 1):
            if heading_of(segments, n):
                spans.append((start, n - 1))
                start = n
        spans.append((start, section['end_id']))

    def size(start, end):
        return offsets[end] + len(segments[end]) - offsets[start]

    raw = []
    for start, end in spans:
        heading = heading_of(segments, start)
        first = segments[start].strip()
        content = ''.join(segments[n] for n in range(start, end + 1)).strip()
        inline = first.partition(':')[2].strip() if heading else first
        under = (inline + ''.join(segments[n] for n in range(start + 1, end + 1))).strip()
        # A "major" heading opens its own chunk: a recognised clinical heading, or a
        # standalone heading line with real content under it. Inline labels such as
        # "A: Airway patent", "Date: 21/12/25" or "GMC number: ..." are minor.
        major = heading is not None and (chart_updates.passage_heading(first)
                                         or (not inline and len(content) >= TINY_CHARS))
        raw.append({'start': start, 'end': end, 'heading': heading, 'major': major,
                    'empty': bool(heading) and not under, 'blank': not content})

    pieces = []
    for index, piece in enumerate(raw):
        following = raw[index + 1] if index + 1 < len(raw) else None
        if piece['blank'] and pieces:
            pieces[-1]['end'] = piece['end']
            continue
        if piece['empty'] and following and (not following['major'] or not pieces):
            # "ABCDE Assessment:" followed by "A: ...": the heading leads its content.
            following.update(start=piece['start'], heading=piece['heading'], major=True, empty=False)
            continue
        if piece['empty'] and pieces:
            pieces[-1]['end'] = piece['end']  # e.g. an empty "Today" template heading
            continue
        if pieces and not piece['major'] and size(pieces[-1]['start'], piece['end']) <= TARGET_CHARS:
            pieces[-1]['end'] = piece['end']
            continue
        if not piece['major'] and pieces and (not piece['heading'] or len(piece['heading']) < 4):
            piece['heading'] = pieces[-1]['heading']
        pieces.append(piece)

    chunks = []
    for piece in pieces:
        start, end, heading = piece['start'], piece['end'], piece['heading']
        heading = heading or 'Note text'
        group, size = [], 0
        for n in range(start, end + 1):
            if group and size + len(segments[n]) > MAX_CHARS and size >= TARGET_CHARS // 3:
                chunks.append((group[0], group[-1], heading))
                group, size = [], 0
            group.append(n)
            size += len(segments[n])
        chunks.append((group[0], group[-1], heading))

    result = []
    for start, end, heading in chunks:
        begin = offsets[start]
        finish = offsets[end] + len(segments[end])
        text = body[begin:finish]
        if not text.strip():
            continue
        result.append({'patient_id': patient, 'note_id': note_id, 'date': note_date,
                       'heading': heading, 'start': begin, 'end': finish, 'text': text,
                       'embed_text': f'Date: {note_date}. Section: {heading}.\n{text.strip()}'})
    return result


def chunk_corpus(notes):
    notes = assert_synthetic(notes)
    counters, chunks = {}, []
    for patient, note_date, body in notes:
        counters[patient] = counters.get(patient, 0) + 1
        chunks.extend(chunk_note(patient, f'{patient}-n{counters[patient]:03d}', note_date, body))
    return chunks


# ---------------------------------------------------------------- storage and search

def connect(path):
    """Open SQLite and try to load sqlite-vec from target/rag/pylib; report which backend."""
    db = sqlite3.connect(str(path))
    db.row_factory = sqlite3.Row
    backend = 'python-exact-cosine'
    if str(PYLIB) not in sys.path:
        sys.path.insert(0, str(PYLIB))
    try:
        import sqlite_vec
        db.enable_load_extension(True)
        sqlite_vec.load(db)
        db.enable_load_extension(False)
        backend = 'sqlite-vec ' + db.execute('select vec_version()').fetchone()[0]
    except Exception:  # noqa: BLE001 - any failure means the documented pure-Python fallback
        pass
    return db, backend


def pack(vector):
    return struct.pack(f'{len(vector)}f', *vector)


def unpack(blob):
    return struct.unpack(f'{len(blob) // 4}f', blob)


def create_schema(db, dims, use_vec):
    db.executescript("""
        CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS chunks(
            id INTEGER PRIMARY KEY, patient_id TEXT NOT NULL, note_id TEXT NOT NULL,
            date TEXT NOT NULL, heading TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL,
            text TEXT NOT NULL, embed_text TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS chunks_patient ON chunks(patient_id);
        CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
            patient_id UNINDEXED, embed_text, tokenize='porter unicode61');
        CREATE TABLE IF NOT EXISTS vectors(
            chunk_id INTEGER PRIMARY KEY REFERENCES chunks(id), patient_id TEXT NOT NULL,
            embedding BLOB NOT NULL);
        CREATE INDEX IF NOT EXISTS vectors_patient ON vectors(patient_id);
        CREATE TABLE IF NOT EXISTS runs(
            id INTEGER PRIMARY KEY, started TEXT NOT NULL, chunks INTEGER NOT NULL,
            embed_seconds REAL NOT NULL, first_batch_seconds REAL, steady_chunks INTEGER NOT NULL,
            steady_seconds REAL NOT NULL, wall_seconds REAL, loaded_bytes INTEGER, note TEXT);
    """)
    if use_vec:
        db.execute(f"""CREATE VIRTUAL TABLE IF NOT EXISTS vec_chunks USING vec0(
            chunk_id INTEGER PRIMARY KEY, patient_id TEXT PARTITION KEY,
            embedding FLOAT[{int(dims)}] distance_metric=cosine)""")


def insert_chunks(db, numbered):
    """Insert (corpus chunk number, chunk) pairs; numbers are stable across calls."""
    for number, chunk in numbered:
        db.execute('INSERT INTO chunks VALUES (?,?,?,?,?,?,?,?,?)',
                   (number, chunk['patient_id'], chunk['note_id'], chunk['date'], chunk['heading'],
                    chunk['start'], chunk['end'], chunk['text'], chunk['embed_text']))
        db.execute('INSERT INTO chunks_fts(rowid, patient_id, embed_text) VALUES (?,?,?)',
                   (number, chunk['patient_id'], chunk['embed_text']))


def insert_vector(db, chunk_id, patient, vector, use_vec):
    blob = pack(vector)
    db.execute('INSERT INTO vectors VALUES (?,?,?)', (chunk_id, patient, blob))
    if use_vec:
        db.execute('INSERT INTO vec_chunks(chunk_id, patient_id, embedding) VALUES (?,?,?)',
                   (chunk_id, patient, blob))


def has_vec_table(db):
    return db.execute("SELECT 1 FROM sqlite_master WHERE name='vec_chunks'").fetchone() is not None


def vector_search(db, patient, query_vector, limit, use_vec):
    """Exact cosine ranking within ONE patient's chunks."""
    if use_vec:
        # vec0 KNN is a brute-force (exact) scan of this patient's partition only.
        rows = db.execute('SELECT chunk_id, distance FROM vec_chunks WHERE embedding MATCH ? '
                          'AND k = ? AND patient_id = ? ORDER BY distance',
                          (pack(query_vector), int(limit), patient)).fetchall()
        return sorted(((row[0], row[1]) for row in rows), key=lambda item: (item[1], item[0]))
    norm_q = math.sqrt(sum(x * x for x in query_vector)) or 1.0
    scored = []
    for chunk_id, blob in db.execute('SELECT chunk_id, embedding FROM vectors WHERE patient_id = ?',
                                     (patient,)):
        vector = unpack(blob)
        norm = math.sqrt(sum(x * x for x in vector)) or 1.0
        cosine = sum(a * b for a, b in zip(query_vector, vector)) / (norm_q * norm)
        scored.append((chunk_id, 1.0 - cosine))
    scored.sort(key=lambda item: (item[1], item[0]))
    return scored[:limit]


def fts_query(question):
    words = [w for w in re.findall(r'[a-z0-9]+', question.lower()) if w not in STOPWORDS and len(w) > 1]
    return ' OR '.join(f'"{w}"' for w in dict.fromkeys(words))


def keyword_search(db, patient, question, limit):
    query = fts_query(question)
    if not query:
        return []
    rows = db.execute('SELECT rowid, bm25(chunks_fts) AS score FROM chunks_fts WHERE chunks_fts MATCH ? '
                      'AND patient_id = ? ORDER BY score, rowid LIMIT ?', (query, patient, int(limit)))
    return [(row[0], row[1]) for row in rows]


def rrf(rankings, k=RRF_K):
    """Reciprocal rank fusion: score = sum of 1/(k + rank), ranks starting at 1."""
    scores, best = {}, {}
    for ranking in rankings:
        for rank, chunk_id in enumerate(ranking, 1):
            scores[chunk_id] = scores.get(chunk_id, 0.0) + 1.0 / (k + rank)
            best[chunk_id] = min(best.get(chunk_id, rank), rank)
    return sorted(scores, key=lambda c: (-scores[c], best[c], c))


def search(db, patient, mode, question=None, query_vector=None, limit=TOP_K, use_vec=False):
    if not PATIENT_ID.fullmatch(patient):
        raise SafetyError('Search must be scoped to one synthetic patient')
    if mode == 'vector':
        ids = [c for c, _ in vector_search(db, patient, query_vector, limit, use_vec)]
    elif mode == 'keyword':
        ids = [c for c, _ in keyword_search(db, patient, question, limit)]
    elif mode == 'hybrid':
        ids = rrf([[c for c, _ in vector_search(db, patient, query_vector, CANDIDATE_DEPTH, use_vec)],
                   [c for c, _ in keyword_search(db, patient, question, CANDIDATE_DEPTH)]])[:limit]
    else:
        raise ValueError(mode)
    owners = {row[0]: row[1] for row in db.execute(
        f'SELECT id, patient_id FROM chunks WHERE id IN ({",".join("?" * len(ids))})', ids)} if ids else {}
    if any(owners.get(c) != patient for c in ids):
        raise SafetyError('Search returned another patient\'s chunk')
    return ids


# ---------------------------------------------------------------- probes and relevance

def compile_groups(groups):
    return [[re.compile(pattern, re.I) for pattern in group] for group in groups]


def group_matches(group, text):
    return any(pattern.search(text) for pattern in group)


def relevant(groups, text):
    """A chunk is relevant when ALL pattern groups match inside that one chunk."""
    return bool(groups) and all(group_matches(group, text) for group in groups)


def load_facts():
    facts = {}
    for path in sorted(FACTS.glob('nhssyn*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        facts[data['fixture']] = {fact['id']: fact for fact in data['facts']}
    return facts


def load_probes(path=PROBES, facts=None):
    facts = load_facts() if facts is None else facts
    probes = json.loads(Path(path).read_text(encoding='utf-8'))['probes']
    seen, covered = set(), set()
    for probe in probes:
        if probe['id'] in seen or probe['kind'] not in KINDS or not PATIENT_ID.fullmatch(probe['patient']):
            raise ValueError(f'Bad probe {probe["id"]}')
        seen.add(probe['id'])
        if probe['kind'] == 'fact':
            fact = facts[probe['patient']][probe['fact']]
            groups = [list(group) for group in fact['pattern_groups']]
            for index, replacement in probe.get('group_overrides', {}).items():
                groups[int(index)] = replacement
            probe['groups'] = groups
            covered.add((probe['patient'], probe['fact']))
        else:
            probe['groups'] = probe['pattern_groups']
            re.compile(probe['context_pattern'])
    expected = {(patient, fact_id) for patient, items in facts.items() for fact_id in items}
    if covered != expected:
        raise ValueError(f'Probes must cover every labelled fact exactly; missing {sorted(expected - covered)}')
    return probes


def prepare_probe(probe, chunks):
    """Resolve relevance against one patient's chunks (raw chart text, not the added prefix)."""
    groups = compile_groups(probe['groups'])
    dropped = [i for i, group in enumerate(groups) if not any(group_matches(group, c['text']) for c in chunks)]
    kept = [group for i, group in enumerate(groups) if i not in dropped]
    ids = [c['id'] for c in chunks if relevant(kept, c['text'])]
    return {'groups': kept, 'dropped_groups': [probe['groups'][i] for i in dropped],
            'relevant': ids, 'spans': bool(kept) and not ids, 'answerable': bool(kept)}


def is_hit(prepared, top, texts):
    if not prepared['answerable']:
        return False
    if not prepared['spans']:
        return any(c in prepared['relevant'] for c in top)
    return all(any(group_matches(group, texts[c]) for c in top) for group in prepared['groups'])


def content_words(text):
    return {w[:5] for w in re.findall(r'[a-z0-9]+', text.lower()) if w not in STOPWORDS and len(w) > 2}


def classify_miss(probe, prepared, top, by_id):
    """Assign one plain-words reason to a miss (first matching rule wins)."""
    groups = prepared['groups']
    texts = [by_id[c]['text'] for c in top]
    covered = [any(group_matches(group, t) for t in texts) for group in groups]
    if prepared['spans']:
        return 'fact split across chunks'
    if probe['kind'] in ('negation', 'family') and covered and covered[0]:
        return 'lost heading/context'
    drug_groups = [i for i, g in enumerate(groups) if any(DRUGS.search(p.pattern) for p in g)]
    number_groups = [i for i, g in enumerate(groups) if any(re.search(r'\d', p.pattern) for p in g)]
    if any(not covered[i] for i in drug_groups):
        return 'medication name'
    if number_groups:
        return 'date/number fact'
    target = by_id[prepared['relevant'][0]]['text'] if prepared['relevant'] else ''
    if not content_words(probe['question']) & content_words(target):
        return 'wording mismatch'
    return 'other'


def percentile(values, fraction):
    values = sorted(values)
    if not values:
        return None
    index = min(len(values) - 1, max(0, math.ceil(fraction * len(values)) - 1))
    return values[index]


# ---------------------------------------------------------------- commands

def db_path(model):
    return OUT / f'{slug(model)}.sqlite'


def cmd_pull(args):
    reply = ollama('/api/pull', {'model': args.model, 'stream': False}, timeout=3600, retries=3)
    print(json.dumps({'model': args.model, 'status': reply.get('status')}))


def parse_patients(spec):
    """'NHSSYN001-NHSSYN010' or a comma list; every id must be a synthetic fixture id."""
    patients = []
    for part in spec.split(','):
        first, _, last = part.strip().partition('-')
        last = last or first
        if not (PATIENT_ID.fullmatch(first) and PATIENT_ID.fullmatch(last)):
            raise SafetyError('Patient scope must use NHSSYN### ids')
        patients += [f'NHSSYN{n:03d}' for n in range(int(first[6:]), int(last[6:]) + 1)]
    return sorted(set(patients))


def has_table(db, name):
    return db.execute('SELECT 1 FROM sqlite_master WHERE name = ?', (name,)).fetchone() is not None


def index_stats(db, model, info=None):
    """Speed and size summary over every index call recorded in the runs table."""
    runs = [dict(row) for row in db.execute('SELECT * FROM runs ORDER BY id')] if has_table(db, 'runs') else []
    chunks = sum(r['chunks'] for r in runs)
    seconds = sum(r['embed_seconds'] for r in runs)
    steady_n = sum(r['steady_chunks'] for r in runs)
    steady_s = sum(r['steady_seconds'] for r in runs)
    first = [r['first_batch_seconds'] for r in runs if r['first_batch_seconds'] is not None]
    blob = db.execute('SELECT embedding FROM vectors LIMIT 1').fetchone()
    stats = {
        'model': model, 'slug': slug(model), 'dims': len(unpack(blob[0])) if blob else None,
        'patients': db.execute('SELECT COUNT(DISTINCT patient_id) FROM chunks').fetchone()[0],
        'chunks': db.execute('SELECT COUNT(*) FROM chunks').fetchone()[0],
        'vectors': db.execute('SELECT COUNT(*) FROM vectors').fetchone()[0],
        'index_calls': len(runs), 'chunks_embedded_timed': chunks, 'embed_seconds': round(seconds, 2),
        'chunks_per_second': round(chunks / seconds, 2) if seconds else None,
        'steady_chunks_per_second': round(steady_n / steady_s, 2) if steady_s else None,
        'first_batch_seconds_incl_model_load_median': round(statistics.median(first), 2) if first else None,
        'model_loaded_bytes': max((r['loaded_bytes'] or 0 for r in runs), default=None) or None,
        'batch_size': BATCH, 'run_notes': sorted({r['note'] for r in runs if r['note']}),
    }
    if info is not None:
        stats.update({'model_disk_bytes': info.get('size'),
                      'model_parameters': info.get('details', {}).get('parameter_size'),
                      'model_quantization': info.get('details', {}).get('quantization_level')})
        db.execute("INSERT OR REPLACE INTO meta VALUES ('model_info', ?)", (json.dumps(stats),))
    else:
        row = db.execute("SELECT value FROM meta WHERE key = 'model_info'").fetchone()
        if row:
            saved = json.loads(row[0])
            stats.update({k: saved.get(k) for k in ('model_disk_bytes', 'model_parameters', 'model_quantization')})
    return stats


def cmd_index(args):
    """Embed at most --max-chunks missing chunks for the patient scope, then stop.

    Incremental and resumable: chunks already embedded for this model are skipped, so
    the trial can run as several short `heavy` calls that free the build slot between them.
    """
    model = args.model
    if model not in MODELS:
        raise SafetyError('Only the three trial embedding models are allowed')
    patients = parse_patients(args.patients)
    notes = load_notes()
    known = {patient for patient, _, _ in notes}
    if not set(patients) <= known:
        raise SafetyError('Patient scope names a fixture that is not in the committed corpus')
    # Number chunks over the WHOLE corpus so ids never depend on the chosen scope.
    numbered = [(n, c) for n, c in enumerate(chunk_corpus(notes), 1) if c['patient_id'] in patients]
    info = model_info(model)
    if info is None:
        raise RuntimeError(f'{model} is not pulled; run the pull subcommand first')
    OUT.mkdir(parents=True, exist_ok=True)
    path = db_path(model)
    db, backend = connect(path)
    started = time.perf_counter()
    if has_table(db, 'chunks'):
        meta = dict(db.execute('SELECT key, value FROM meta'))
        if meta.get('model') != model or meta.get('document_prefix') != MODELS[model]['document']:
            raise SystemExit(f'{path} was built for another model or prefix; move it aside first')
        stored = dict(db.execute('SELECT id, embed_text FROM chunks'))
        wanted = dict((n, c['embed_text']) for n, c in numbered)
        if any(n in stored and stored[n] != text for n, text in wanted.items()):
            raise SystemExit(f'{path} was built by a different chunker; move it aside first')
        use_vec = backend.startswith('sqlite-vec') and has_vec_table(db)
        new_rows = [(n, c) for n, c in numbered if n not in stored]
    else:
        use_vec = backend.startswith('sqlite-vec')
        new_rows = numbered
    done = {row[0] for row in db.execute('SELECT chunk_id FROM vectors')} if has_table(db, 'vectors') else set()
    missing = [(n, c) for n, c in numbered if n not in done]
    todo = missing[:max(0, args.max_chunks)]
    print(f'{model}: scope {patients[0]}..{patients[-1]} ({len(patients)} patients, {len(numbered)} chunks); '
          f'{len(missing)} still to embed, embedding {len(todo)} now; backend {backend if use_vec else "python"}',
          flush=True)

    embed_seconds, first_batch, steady_n, steady_s, loaded = 0.0, None, 0, 0.0, None
    for offset in range(0, len(todo), BATCH):
        batch = todo[offset:offset + BATCH]
        tick = time.perf_counter()
        vectors = embed(model, [MODELS[model]['document'] + chunk['embed_text'] for _, chunk in batch])
        elapsed = time.perf_counter() - tick
        if offset == 0:
            first_batch = elapsed  # includes loading the model into Ollama
            ps = ollama('/api/ps').get('models', [])
            loaded = next((m.get('size') for m in ps if m.get('name', '').startswith(model)), None)
            if not has_table(db, 'chunks'):
                create_schema(db, len(vectors[0]), use_vec)
                db.executemany('INSERT INTO meta VALUES (?,?)',
                               [('model', model), ('document_prefix', MODELS[model]['document'])])
            create_schema(db, len(vectors[0]), use_vec)  # adds any table an older file lacks
            insert_chunks(db, new_rows)
        else:
            steady_n += len(batch)
            steady_s += elapsed
        embed_seconds += elapsed
        for (number, chunk), vector in zip(batch, vectors):
            insert_vector(db, number, chunk['patient_id'], vector, use_vec)
        db.commit()
    if todo:
        unload(model)
        db.execute('INSERT INTO runs(started, chunks, embed_seconds, first_batch_seconds, steady_chunks, '
                   'steady_seconds, wall_seconds, loaded_bytes, note) VALUES (?,?,?,?,?,?,?,?,?)',
                   (time.strftime('%Y-%m-%dT%H:%M:%S'), len(todo), embed_seconds, first_batch, steady_n,
                    steady_s, time.perf_counter() - started, loaded, None))
    elif not has_table(db, 'chunks'):
        raise SystemExit('Nothing to index')
    stats = index_stats(db, model, info)
    stats['remaining_in_scope'] = len(missing) - len(todo)
    stats['this_call'] = {'chunks': len(todo), 'embed_seconds': round(embed_seconds, 2),
                          'chunks_per_second': round(len(todo) / embed_seconds, 2) if embed_seconds else None}
    db.commit()
    db.close()
    stats['db_bytes'] = path.stat().st_size
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / f'{slug(model)}-index.json').write_text(json.dumps(stats, indent=2) + '\n')
    print(json.dumps(stats, indent=2))


def evaluate_db(db, model, probes, query_vectors, use_vec, embed_latency=None):
    """Score every probe in every mode. query_vectors maps probe id -> vector."""
    rows = [dict(row) for row in db.execute('SELECT * FROM chunks ORDER BY id')]
    by_id = {row['id']: row for row in rows}
    per_patient = {}
    for row in rows:
        per_patient.setdefault(row['patient_id'], []).append(row)
    texts = {row['id']: row['text'] for row in rows}
    prepared = {p['id']: prepare_probe(p, per_patient[p['patient']]) for p in probes}
    results = {mode: [] for mode in MODES}
    latency = {mode: [] for mode in MODES}
    for probe in probes:
        prep = prepared[probe['id']]
        for mode in MODES:
            tick = time.perf_counter()
            top = search(db, probe['patient'], mode, probe['question'], query_vectors[probe['id']],
                         TOP_K, use_vec)
            latency[mode].append(time.perf_counter() - tick)
            entry = {'probe': probe['id'], 'patient': probe['patient'], 'kind': probe['kind'],
                     'answerable': prep['answerable'], 'spans': prep['spans'],
                     'hit@8': is_hit(prep, top, texts), 'hit@3': is_hit(prep, top[:3], texts),
                     'top3': [f"{by_id[c]['date']} {by_id[c]['heading']}" for c in top[:3]]}
            if probe['kind'] != 'fact' and entry['hit@8'] and not prep['spans']:
                found = next(c for c in top if c in prep['relevant'])
                pattern = re.compile(probe['context_pattern'], re.I)
                entry['context_kept'] = bool(pattern.search(by_id[found]['embed_text']))
                entry['context_in_raw_text'] = bool(pattern.search(by_id[found]['text']))
            if prep['answerable'] and not entry['hit@8']:
                entry['miss_kind'] = classify_miss(probe, prep, top, by_id)
                entry['question'] = probe['question']
                if prep['relevant']:
                    expected = [f"{by_id[c]['date']} {by_id[c]['heading']}" for c in prep['relevant']]
                else:
                    expected = []
                    for group in prep['groups']:
                        first = next((r for r in per_patient[probe['patient']] if group_matches(group, r['text'])), None)
                        if first:
                            expected.append(f"{first['date']} {first['heading']} (part)")
                entry['expected'] = list(dict.fromkeys(expected))[:3]
            results[mode].append(entry)
    summary = {}
    for mode in MODES:
        entries = [e for e in results[mode] if e['answerable']]
        block = {}
        for label, subset in [('all', entries)] + [(k, [e for e in entries if e['kind'] == k]) for k in KINDS]:
            block[label] = {'n': len(subset),
                            'recall@8': round(sum(e['hit@8'] for e in subset) / len(subset), 3) if subset else None,
                            'recall@3': round(sum(e['hit@3'] for e in subset) / len(subset), 3) if subset else None}
        block['per_patient'] = {}
        for patient in sorted({e['patient'] for e in entries}):
            subset = [e for e in entries if e['patient'] == patient]
            block['per_patient'][patient] = {'n': len(subset), 'hit@8': sum(e['hit@8'] for e in subset),
                                             'hit@3': sum(e['hit@3'] for e in subset)}
        context = [e for e in results[mode] if 'context_kept' in e]
        block['context_kept'] = {'checked': len(context), 'kept': sum(e['context_kept'] for e in context),
                                 'in_raw_text': sum(e['context_in_raw_text'] for e in context)}
        block['misses_by_kind'] = {kind: sum(1 for e in entries if e.get('miss_kind') == kind) for kind in MISS_KINDS}
        block['search_ms_median'] = round(1000 * statistics.median(latency[mode]), 3)
        block['search_ms_p95'] = round(1000 * percentile(latency[mode], 0.95), 3)
        summary[mode] = block
    labels = {'probes': len(probes), 'by_kind': {k: sum(p['kind'] == k for p in probes) for k in KINDS},
              'spans_chunks': sorted(p for p, v in prepared.items() if v['spans']),
              'unanswerable': sorted(p for p, v in prepared.items() if not v['answerable']),
              'dropped_groups': {p: v['dropped_groups'] for p, v in prepared.items() if v['dropped_groups']},
              'relevant_chunk_counts': {p: len(v['relevant']) for p, v in prepared.items()}}
    return {'model': model, 'summary': summary, 'labels': labels, 'details': results,
            'query_embed_ms': embed_latency}


def isolation_check(db, query_vectors_generic, questions, use_vec):
    """Run generic queries for EVERY indexed patient in every mode; all hits must be that patient's."""
    patients = [row[0] for row in db.execute('SELECT DISTINCT patient_id FROM chunks ORDER BY patient_id')]
    checked, latencies = 0, {mode: [] for mode in MODES}
    for patient in patients:
        for question, vector in zip(questions, query_vectors_generic):
            for mode in MODES:
                tick = time.perf_counter()
                search(db, patient, mode, question, vector, TOP_K, use_vec)  # raises SafetyError on a leak
                latencies[mode].append(time.perf_counter() - tick)
                checked += 1
    return {'patients': len(patients), 'searches': checked, 'cross_patient_results': 0,
            'search_ms_median': {m: round(1000 * statistics.median(v), 3) for m, v in latencies.items()},
            'search_ms_p95': {m: round(1000 * percentile(v, 0.95), 3) for m, v in latencies.items()}}


GENERIC_QUESTIONS = ['What medicines is the patient on?', 'What was the main diagnosis?',
                     'Is there any family history of illness?']


def cmd_evaluate(args):
    model = args.model
    path = db_path(model)
    if not path.exists():
        raise SystemExit(f'Index {path} missing; run index first')
    db, backend = connect(path)
    use_vec = backend.startswith('sqlite-vec') and has_vec_table(db)
    stored = db.execute("SELECT value FROM meta WHERE key='model'").fetchone()[0]
    if stored != model:
        raise SystemExit('Index belongs to another model')
    patients = {row[0] for row in db.execute('SELECT DISTINCT patient_id FROM chunks')}
    if not all(PATIENT_ID.fullmatch(p) for p in patients):
        raise SafetyError('Index holds a non-synthetic patient id')
    probes = load_probes()
    unembedded = db.execute('SELECT COUNT(*) FROM chunks WHERE id NOT IN (SELECT chunk_id FROM vectors)').fetchone()[0]
    if unembedded or not {p['patient'] for p in probes} <= patients:
        raise SystemExit(f'Index incomplete ({unembedded} chunks without vectors, or probe patients missing); '
                         'run index again')
    vectors, embed_ms = {}, []
    prefix = MODELS[model]['query']
    embed(model, [prefix + 'warm up'])  # load once so per-query timing excludes model load
    for probe in probes:
        tick = time.perf_counter()
        vectors[probe['id']] = embed(model, [prefix + probe['question']])[0]
        embed_ms.append(1000 * (time.perf_counter() - tick))
    generic = embed(model, [prefix + q for q in GENERIC_QUESTIONS])
    unload(model)
    result = evaluate_db(db, model, probes, vectors, use_vec,
                         {'median': round(statistics.median(embed_ms), 1),
                          'p95': round(percentile(embed_ms, 0.95), 1)})
    result['backend'] = backend if use_vec else 'python-exact-cosine'
    result['isolation'] = isolation_check(db, generic, GENERIC_QUESTIONS, use_vec)
    result['index'] = index_stats(db, model)
    result['index']['db_bytes'] = path.stat().st_size
    result['evaluated_on'] = date.today().isoformat()
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / f'{slug(model)}-eval.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'model': model, 'backend': result['backend'], 'summary': {
        mode: {k: result['summary'][mode][k] for k in ('all', 'fact', 'negation', 'family', 'context_kept')}
        for mode in MODES}, 'isolation': result['isolation']}, indent=2))


def cmd_report(_args):
    rows, speed, misses = [], [], []
    labels = None
    for model in MODELS:
        path = RESULTS / f'{slug(model)}-eval.json'
        if not path.exists():
            continue
        result = json.loads(path.read_text())
        labels = result['labels']
        index = result['index'] or {}
        for mode in MODES:
            s = result['summary'][mode]
            ctx = s['context_kept']
            rows.append(f"| {model} | {mode} | {s['all']['recall@8']:.2f} | {s['all']['recall@3']:.2f} | "
                        f"{s['fact']['recall@8']:.2f} / {s['fact']['recall@3']:.2f} | "
                        f"{s['negation']['recall@8']:.2f} / {s['negation']['recall@3']:.2f} | "
                        f"{s['family']['recall@8']:.2f} / {s['family']['recall@3']:.2f} | "
                        f"{ctx['kept']}/{ctx['checked']} |")
            for entry in result['details'][mode]:
                if 'miss_kind' in entry:
                    misses.append((model, mode, entry))
        iso = result['isolation']
        speed.append(f"| {model} | {index.get('model_parameters')} {index.get('model_quantization')} | "
                     f"{(index.get('model_disk_bytes') or 0) / 1e6:.0f} MB | {index.get('dims')} | "
                     f"{index.get('chunks_embedded_timed')} in {index.get('embed_seconds')} s | "
                     f"{index.get('chunks_per_second')} | "
                     f"{index.get('first_batch_seconds_incl_model_load_median')} s | "
                     f"{result['query_embed_ms']['median']} / {result['query_embed_ms']['p95']} ms | "
                     f"{iso['search_ms_median']['vector']} / {iso['search_ms_median']['keyword']} / "
                     f"{iso['search_ms_median']['hybrid']} ms | {(index.get('db_bytes') or 0) / 1e6:.1f} MB |")
    out = ['# RAG retrieval trial: generated tables', '',
           '| Model | Mode | recall@8 | recall@3 | facts @8/@3 | negation @8/@3 | family @8/@3 | context kept |',
           '|---|---|---|---|---|---|---|---|', *rows, '',
           '| Model | Size | Disk | Dims | Chunks embedded (timed) | Chunks/s | First batch (load) | '
           'Query embed median/p95 | Search median vec/kw/hybrid | Index file |',
           '|---|---|---|---|---|---|---|---|---|---|', *speed, '']
    if labels:
        out += [f"Probes: {labels['probes']} ({labels['by_kind']}); spans chunks: {len(labels['spans_chunks'])} "
                f"{labels['spans_chunks']}; unanswerable: {labels['unanswerable']}; "
                f"groups dropped (match nowhere in chart): {labels['dropped_groups']}", '']
    out += ['## Misses', '', '| Model | Mode | Kind | Probe | Question | Expected | Top-3 retrieved |',
            '|---|---|---|---|---|---|---|']
    for model, mode, entry in misses:
        out.append(f"| {model} | {mode} | {entry['miss_kind']} | {entry['probe']} | {entry['question']} | "
                   f"{'; '.join(entry['expected'])} | {'; '.join(entry['top3'])} |")
    text = '\n'.join(out) + '\n'
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / 'report.md').write_text(text)
    print(text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    sub = parser.add_subparsers(dest='command', required=True)
    for name in ('pull', 'index', 'evaluate'):
        command = sub.add_parser(name)
        command.add_argument('--model', required=True, choices=sorted(MODELS))
        if name == 'index':
            command.add_argument('--patients', default=DEFAULT_SCOPE,
                                 help='NHSSYN### range or comma list (default %(default)s)')
            command.add_argument('--max-chunks', type=int, default=400,
                                 help='embed at most this many chunks per call, then stop (default %(default)s)')
    sub.add_parser('report')
    args = parser.parse_args(argv)
    {'pull': cmd_pull, 'index': cmd_index, 'evaluate': cmd_evaluate, 'report': cmd_report}[args.command](args)


if __name__ == '__main__':
    main()
