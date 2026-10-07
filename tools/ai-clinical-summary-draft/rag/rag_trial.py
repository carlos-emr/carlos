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
LABELS = HERE / 'labels.json'
LABEL_SETS = ('direct', 'regex')
PLAIN_WORDS = HERE / 'plain_words.json'
# Round 3: 'off' = rounds 1-2; 'query' adds chart words to a question that uses a plain phrase;
# 'both' also adds the plain words to every chunk that uses a chart word (needs its own index).
PLAIN_WORD_MODES = ('off', 'query', 'both')
HELDOUT = HERE / 'heldout.json'
QUESTION_SETS = ('probes', 'heldout')  # heldout: new wording for some probes, same labels


def heldout_probes(probes, path=HELDOUT):
    """The probes that have a held-out question, each with that question instead of its own."""
    questions = json.loads(Path(path).read_text(encoding='utf-8'))['questions']
    known = {p['id'] for p in probes}
    unknown = sorted(set(questions) - known)
    if unknown:
        raise ValueError(f'held-out questions name unknown probes: {unknown}')
    return [dict(p, question=questions[p['id']]) for p in probes if p['id'] in questions]
if str(TOOL) not in sys.path:
    sys.path.insert(0, str(TOOL))

import chart_updates  # noqa: E402  (reused section helpers)

OLLAMA = 'http://127.0.0.1:11434'
OLLAMA_PATHS = frozenset({'/api/embed', '/api/pull', '/api/tags', '/api/ps'})
PATIENT_ID = re.compile(r'^NHSSYN[0-9]{3}$')  # ^NHSSYN\d{3}$ with ASCII digits only
TOP_K = 8
RECALL_KS = (3, 8, 12, 16, 24)  # vector search is also scored at these depths
FORM_FIELD_PROBES = ('NHSSYN002:neg-complications', 'NHSSYN002:anaesthesia')  # round 1's cutter misses
RRF_K = 60
CANDIDATE_DEPTH = 100
# 2026-10-05 scope: 001-003 carry the labelled facts; 004-010 are the other patients that
# per-patient filtering must exclude. The full 50-patient run was too long for the shared box.
DEFAULT_SCOPE = 'NHSSYN001-NHSSYN010'
BATCH = 16
MAX_CHARS = 1200
TARGET_CHARS = 900
TINY_CHARS = 60
# A block of short form fields: 'none' = round 1's cutter (left as it is), 'label' = round 2's default
# (headed with every field label, embedded as "Label: value" lines), 'split' = one chunk per field.
FORM_FIELD_MODES = ('none', 'label', 'split')
MAX_HEADING = 120
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
DRUGS = re.compile(r'\b(?:nimodipine|amlodipine|potassium chloride|kcl|ferrous|iron|tinzaparin|enoxaparin|'
                   r'paracetamol|ibuprofen|morphine|amoxiclav|omeprazole|codeine|cefuroxime)\b', re.I)
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


def grid_label(segments, n):
    """The label if segment n is a form-field label on its own line ("Complications", value below)."""
    line = segments[n].strip()
    if (_standalone(segments, n) and GRID_HEADING.fullmatch(line)
            and (n == 1 or not segments[n - 1].strip()) and n + 1 in segments and segments[n + 1].strip()):
        return re.sub(r'\s+', ' ', line)
    return None


def form_fields_in(segments, start, end):
    """Labels of the form fields inside segments start..end, in order."""
    return [label for n in range(start, end + 1) if (label := grid_label(segments, n))]


def field_lines(segments, start, end):
    """Render a block of form fields as "Label: value" lines for embedding only.

    Short form fields ("Anaesthesia Type" / "General anaesthesia") are folded into one
    chunk, which round 1 headed with the first field alone, so "Complications / None"
    was embedded as part of "Tissue Removed". Writing each field as "Label: value" keeps
    every label next to its own value. The stored chunk text is not changed.
    """
    lines, pending = [], None
    for n in range(start, end + 1):
        text = segments[n].strip()
        if not text:
            continue
        label = grid_label(segments, n)
        if label:
            if pending:
                lines.append(pending)
            pending = label
        elif pending:
            lines.append(f'{pending}: {text}')
            pending = None
        else:
            lines.append(text)
    if pending:
        lines.append(pending)
    return '\n'.join(lines)


def chunk_note(patient, note_id, note_date, body, form_fields='label'):
    """Split one note into section chunks with exact character offsets.

    chart_updates.source_segments gives lossless sentence/line segments and
    chart_updates.source_sections the coarse section partition; sections are then
    split at further clinical headings, tiny non-clinical scraps (signatures, form
    labels) are folded into the previous chunk, and long sections are cut at segment
    boundaries. Every chunk is prefixed with its date and heading for embedding. A chunk
    made of several short form fields is, in round 2, either headed with all their labels
    and embedded as "Label: value" lines (form_fields='label') or cut into one chunk per
    field (form_fields='split').
    """
    if form_fields not in FORM_FIELD_MODES:
        raise ValueError(f'Unknown form_fields mode {form_fields}')
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

    from_split = set()
    if form_fields == 'split':
        split = []
        for start, end, heading in chunks:
            labels = [n for n in range(start, end + 1) if grid_label(segments, n)]
            if len(labels) < 2:
                split.append((start, end, heading))
                continue
            if labels[0] > start:  # text before the first field keeps the chunk's heading
                split.append((start, labels[0] - 1, heading))
            for first, nxt in zip(labels, labels[1:] + [end + 1]):
                split.append((first, nxt - 1, grid_label(segments, first)))
                from_split.add(first)
        chunks = split

    result = []
    for start, end, heading in chunks:
        begin = offsets[start]
        finish = offsets[end] + len(segments[end])
        text = body[begin:finish]
        if not text.strip():
            continue
        content = text.strip()
        fields = form_fields_in(segments, start, end) if form_fields != 'none' else []
        if len(fields) >= 2:
            # A heading that is not itself one of the fields (say "Test Results" over "FBC", "U&E") leads.
            names = fields if heading in fields else [heading, *fields]
            heading = ' / '.join(names)[:MAX_HEADING]
        if len(fields) >= 2 or start in from_split:
            content = field_lines(segments, start, end)
        result.append({'patient_id': patient, 'note_id': note_id, 'date': note_date,
                       'heading': heading, 'start': begin, 'end': finish, 'text': text,
                       'embed_text': f'Date: {note_date}. Section: {heading}.\n{content}'})
    return result


def chunk_corpus(notes, form_fields='label', plain_words=None):
    """plain_words (from load_plain_words) adds a 'Plain words:' line to the EMBEDDED text only."""
    notes = assert_synthetic(notes)
    counters, chunks = {}, []
    for patient, note_date, body in notes:
        counters[patient] = counters.get(patient, 0) + 1
        chunks.extend(chunk_note(patient, f'{patient}-n{counters[patient]:03d}', note_date, body, form_fields))
    if plain_words:
        for chunk in chunks:
            extra = plain_words_for_chunk(chunk['text'], plain_words)
            if extra:
                chunk['embed_text'] += f"\nPlain words: {', '.join(extra)}."
    return chunks


# ---------------------------------------------------------------- round 3: plain words

def term_pattern(term, plain=False):
    """Whole words or phrases, any run of whitespace. A chart abbreviation (two capitals, or a
    digit, such as PTX, HR or SpO2) must match its case, so 'hr' in 'three' or 'bp' never counts;
    plain phrases and full clinical words ignore case."""
    body = r'\s+'.join(re.escape(word) for word in term.split())
    exact = not plain and bool(re.search(r'[A-Z].*[A-Z]|\d', term))
    return re.compile(r'(?<![A-Za-z0-9])' + body + r'(?![A-Za-z0-9])', 0 if exact else re.I)


def load_plain_words(path=PLAIN_WORDS):
    entries = []
    for entry in json.loads(Path(path).read_text(encoding='utf-8'))['entries']:
        plain = [p.strip() for p in entry['plain'] if p.strip()]
        chart = [c.strip() for c in entry['chart'] if c.strip()]
        if not plain or not chart:
            raise ValueError(f'plain-words entry needs plain and chart words: {entry!r}')
        entries.append({'plain': plain, 'chart': chart,
                        'plain_patterns': [term_pattern(p, plain=True) for p in plain],
                        'chart_patterns': [term_pattern(c) for c in chart]})
    return entries


def plain_words_digest(path=PLAIN_WORDS):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()[:16]


def expand_question(question, entries):
    """Adds the chart's words for any plain phrase in the question, e.g. '... (pneumothorax; PTX)'."""
    extra = []
    for entry in entries:
        if any(pattern.search(question) for pattern in entry['plain_patterns']):
            extra.extend(term for term in entry['chart'] if term not in extra)
    return f"{question} ({'; '.join(extra)})" if extra else question


def plain_words_for_chunk(text, entries, per_entry=2):
    """The first plain phrases of every entry whose chart words appear in the chunk text."""
    extra = []
    for entry in entries:
        if any(pattern.search(text) for pattern in entry['chart_patterns']):
            extra.extend(p for p in entry['plain'][:per_entry] if p not in extra)
    return extra


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

def phrase_pattern(text):
    """An exact chart phrase as a pattern: any case, any run of whitespace, ASCII word edges."""
    text = text.strip()
    body = r'\s+'.join(re.escape(word) for word in text.split())
    head = r'(?<![A-Za-z0-9])' if text[:1].isascii() and text[:1].isalnum() else ''
    tail = r'(?![A-Za-z0-9])' if text[-1:].isascii() and text[-1:].isalnum() else ''
    return re.compile(head + body + tail, re.I)


def compile_groups(groups):
    """Each item becomes (date or None, pattern, source words). Regex labels are strings; direct labels are dicts."""
    compiled = []
    for group in groups:
        items = []
        for item in group:
            if isinstance(item, dict):
                items.append((item.get('date'), phrase_pattern(item['text']), item['text']))
            else:
                items.append((None, re.compile(item, re.I), item))
        compiled.append(items)
    return compiled


def group_matches(group, chunk):
    """chunk is a chunk row (dict with text and date); a dated phrase only matches that day's notes."""
    return any((date is None or date == chunk['date']) and pattern.search(chunk['text'])
               for date, pattern, _source in group)


def relevant(groups, chunk):
    """A chunk is relevant when ALL pattern groups match inside that one chunk."""
    return bool(groups) and all(group_matches(group, chunk) for group in groups)


def load_labels(path=LABELS):
    """Direct labels: probe id -> evidence groups of {'date'?, 'text'} phrases copied from the chart."""
    labels = json.loads(Path(path).read_text(encoding='utf-8'))['labels']
    result = {}
    for probe_id, label in labels.items():
        groups = []
        if not isinstance(label.get('evidence'), list) or not all(isinstance(g, list) for g in label['evidence']):
            raise ValueError(f'{probe_id}: evidence must be a list of lists of phrases')
        for group in label['evidence']:
            items = []
            for item in group:
                item = item if isinstance(item, dict) else {'text': item}
                if set(item) - {'text', 'date'} or not str(item.get('text', '')).strip():
                    raise ValueError(f'{probe_id}: a label phrase needs non-blank text and at most a date')
                if 'date' in item and not re.fullmatch(r'\d{4}-\d{2}-\d{2}', str(item['date'])):
                    raise ValueError(f'{probe_id}: a label date must be YYYY-MM-DD')
                items.append(item)
            if not items:
                raise ValueError(f'{probe_id}: an evidence group is empty')
            groups.append(items)
        if not groups:
            raise ValueError(f'{probe_id}: no evidence')
        result[probe_id] = groups
    return result


def load_facts():
    facts = {}
    for path in sorted(FACTS.glob('nhssyn*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        facts[data['fixture']] = {fact['id']: fact for fact in data['facts']}
    return facts


def load_probes(path=PROBES, facts=None, label_set='direct'):
    """label_set 'direct' (round 2): relevance from labels.json; 'regex': round 1's fact pattern groups."""
    if label_set not in LABEL_SETS:
        raise ValueError(f'Unknown label set {label_set}')
    facts = load_facts() if facts is None else facts
    direct = load_labels() if label_set == 'direct' else {}
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
    if label_set == 'direct':
        if set(direct) != seen:
            raise ValueError(f'labels.json must label every probe exactly; differs on {sorted(set(direct) ^ seen)}')
        for probe in probes:
            probe['groups'] = direct[probe['id']]
    return probes


def prepare_probe(probe, chunks):
    """Resolve relevance against one patient's chunks (raw chart text, not the added prefix)."""
    groups = compile_groups(probe['groups'])
    dropped = [i for i, group in enumerate(groups) if not any(group_matches(group, c) for c in chunks)]
    kept = [group for i, group in enumerate(groups) if i not in dropped]
    ids = [c['id'] for c in chunks if relevant(kept, c)]
    return {'groups': kept, 'dropped_groups': [probe['groups'][i] for i in dropped],
            'relevant': ids, 'spans': bool(kept) and not ids, 'answerable': bool(kept)}


def is_hit(prepared, top, by_id):
    if not prepared['answerable']:
        return False
    if not prepared['spans']:
        return any(c in prepared['relevant'] for c in top)
    return all(any(group_matches(group, by_id[c]) for c in top) for group in prepared['groups'])


def content_words(text):
    return {w[:5] for w in re.findall(r'[a-z0-9]+', text.lower()) if w not in STOPWORDS and len(w) > 2}


def classify_miss(probe, prepared, top, by_id):
    """Assign one plain-words reason to a miss (first matching rule wins)."""
    groups = prepared['groups']
    covered = [any(group_matches(group, by_id[c]) for c in top) for group in groups]
    if prepared['spans']:
        return 'fact split across chunks'
    if probe['kind'] in ('negation', 'family') and covered and covered[0]:
        return 'lost heading/context'
    # Judged on the label's own words: a direct phrase's pattern carries digits in its word-edge guard.
    drug_groups = [i for i, g in enumerate(groups) if any(DRUGS.search(source) for _, _, source in g)]
    number_groups = [i for i, g in enumerate(groups) if any(re.search(r'\d', source) for _, _, source in g)]
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

RESERVED_DIRS = frozenset({'r1', 'results', 'pylib', 'logs'})  # r1 is the '' folder's result tag


def index_dir(name):
    """'' is round 1's index folder (target/rag); a plain name such as 'r2' is a subfolder of it."""
    if name and (not re.fullmatch(r'[a-z0-9-]{1,20}', name) or name in RESERVED_DIRS):
        raise SafetyError('Index folder must be a short plain name such as r2 (not r1, results, pylib or logs)')
    return OUT / name if name else OUT


def db_path(model, folder=''):
    return index_dir(folder) / f'{slug(model)}.sqlite'


def reusable_vectors(model, folder, digest=None):
    """embed_text -> stored vector from another index of the SAME model and prefix (round 1, say)."""
    path = db_path(model, folder)
    if not path.exists():
        raise SystemExit(f'No index to reuse at {path}')
    db, _backend = connect(path)
    try:
        if not (has_table(db, 'meta') and has_table(db, 'chunks') and has_table(db, 'vectors')):
            raise SystemExit(f'{path} is not a trial index; cannot reuse its vectors')
        meta = dict(db.execute('SELECT key, value FROM meta'))
        if meta.get('model') != model or meta.get('document_prefix') != MODELS[model]['document']:
            raise SystemExit(f'{path} was built for another model or prefix; cannot reuse its vectors')
        if digest and meta.get('model_digest') not in (None, digest):
            raise SystemExit(f'{path} was built with another build of {model}; cannot reuse its vectors')
        return dict(db.execute('SELECT c.embed_text, v.embedding FROM chunks c JOIN vectors v ON v.chunk_id = c.id'))
    finally:
        db.close()


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
    plain = load_plain_words() if getattr(args, 'plain_words', 'off') == 'both' else None
    numbered = [(n, c) for n, c in enumerate(chunk_corpus(notes, args.form_fields, plain), 1)
                if c['patient_id'] in patients]
    info = model_info(model)
    if info is None:
        raise RuntimeError(f'{model} is not pulled; run the pull subcommand first')
    path = db_path(model, args.index_dir)
    path.parent.mkdir(parents=True, exist_ok=True)
    reuse = reusable_vectors(model, args.reuse_from, info.get('digest')) if args.reuse_from is not None else {}
    db, backend = connect(path)
    started = time.perf_counter()
    if has_table(db, 'chunks'):
        meta = dict(db.execute('SELECT key, value FROM meta'))
        if meta.get('model') != model or meta.get('document_prefix') != MODELS[model]['document']:
            raise SystemExit(f'{path} was built for another model or prefix; move it aside first')
        if info.get('digest') and meta.get('model_digest') not in (None, info['digest']):
            raise SystemExit(f'{path} was built with another build of {model}; move it aside first')
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
    # Chunks whose embedded text is unchanged keep the other index's vector: same model, same input.
    copied = [(n, c) for n, c in missing if c['embed_text'] in reuse]
    if copied:
        if not has_table(db, 'chunks'):
            create_schema(db, len(unpack(reuse[copied[0][1]['embed_text']])), use_vec)
            db.executemany('INSERT INTO meta VALUES (?,?)',
                           [('model', model), ('document_prefix', MODELS[model]['document'])])
            if info.get('digest'):
                db.execute("INSERT OR REPLACE INTO meta VALUES ('model_digest', ?)", (info['digest'],))
        create_schema(db, len(unpack(reuse[copied[0][1]['embed_text']])), use_vec)
        insert_chunks(db, new_rows)
        new_rows = []
        for number, chunk in copied:
            insert_vector(db, number, chunk['patient_id'], unpack(reuse[chunk['embed_text']]), use_vec)
        db.commit()
        missing = [(n, c) for n, c in missing if c['embed_text'] not in reuse]
    todo = missing[:max(0, args.max_chunks)]
    print(f'{model}: scope {patients[0]}..{patients[-1]} ({len(patients)} patients, {len(numbered)} chunks); '
          f'{len(copied)} vectors reused unchanged; {len(missing)} still to embed, embedding {len(todo)} now; '
          f'backend {backend if use_vec else "python"}', flush=True)

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
                if info.get('digest'):
                    db.execute("INSERT OR REPLACE INTO meta VALUES ('model_digest', ?)", (info['digest'],))
            create_schema(db, len(vectors[0]), use_vec)  # adds any table an older file lacks
            insert_chunks(db, new_rows)
            new_rows = []
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
    # Which plain-words list (if any) is in the embedded text, so evaluate can refuse a mismatch.
    db.execute("INSERT OR REPLACE INTO meta VALUES ('plain_words', ?)",
               (plain_words_digest() if plain else 'off',))
    stats = index_stats(db, model, info)
    stats['remaining_in_scope'] = len(missing) - len(todo)
    stats['this_call'] = {'chunks': len(todo), 'embed_seconds': round(embed_seconds, 2),
                          'chunks_per_second': round(len(todo) / embed_seconds, 2) if embed_seconds else None}
    db.commit()
    db.close()
    stats['db_bytes'] = path.stat().st_size
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / f"{slug(model)}-{args.index_dir or 'r1'}-index.json").write_text(json.dumps(stats, indent=2) + '\n')
    print(json.dumps(stats, indent=2))


def evaluate_db(db, model, probes, query_vectors, use_vec, embed_latency=None):
    """Score every probe in every mode. query_vectors maps probe id -> vector."""
    rows = [dict(row) for row in db.execute('SELECT * FROM chunks ORDER BY id')]
    by_id = {row['id']: row for row in rows}
    per_patient = {}
    for row in rows:
        per_patient.setdefault(row['patient_id'], []).append(row)
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
                     'hit@8': is_hit(prep, top, by_id), 'hit@3': is_hit(prep, top[:3], by_id),
                     'top3': [f"{by_id[c]['date']} {by_id[c]['heading']}" for c in top[:3]]}
            if probe['kind'] != 'fact' and entry['hit@8'] and not prep['spans']:
                found = next(c for c in top if c in prep['relevant'])
                pattern = re.compile(probe['context_pattern'], re.I)
                entry['context_kept'] = bool(pattern.search(by_id[found]['embed_text']))
                entry['context_in_raw_text'] = bool(pattern.search(by_id[found]['text']))
            if mode == 'vector' and prep['answerable']:
                # How deep the vector ranking must go before the probe counts as found (None = never).
                ranked = search(db, probe['patient'], 'vector', query_vector=query_vectors[probe['id']],
                                limit=len(per_patient[probe['patient']]), use_vec=use_vec)
                entry['rank_needed'] = next((k for k in range(1, len(ranked) + 1)
                                             if is_hit(prep, ranked[:k], by_id)), None)
            if prep['answerable'] and not entry['hit@8']:
                entry['miss_kind'] = classify_miss(probe, prep, top, by_id)
                entry['question'] = probe.get('asked', probe['question'])
                if probe['question'] != entry['question']:
                    entry['expanded_question'] = probe['question']
                if prep['relevant']:
                    expected = [f"{by_id[c]['date']} {by_id[c]['heading']}" for c in prep['relevant']]
                else:
                    expected = []
                    for group in prep['groups']:
                        first = next((r for r in per_patient[probe['patient']] if group_matches(group, r)), None)
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
        if mode == 'vector':
            block['recall_at'] = {str(k): round(sum(1 for e in entries if e['rank_needed'] and e['rank_needed'] <= k)
                                                / len(entries), 3) for k in RECALL_KS}
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


def result_tag(args):
    suffix = {'off': '', 'query': '-pwq', 'both': '-pwb'}[getattr(args, 'plain_words', 'off')]
    held = '-ho' if getattr(args, 'questions', 'probes') == 'heldout' else ''
    return f"{args.index_dir or 'r1'}-{args.labels}{suffix}{held}"


def cmd_evaluate(args):
    model = args.model
    path = db_path(model, args.index_dir)
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
    probes = load_probes(label_set=args.labels)
    if getattr(args, 'questions', 'probes') == 'heldout':
        probes = heldout_probes(probes)
    built_with = dict(db.execute('SELECT key, value FROM meta')).get('plain_words', 'off')
    mode = getattr(args, 'plain_words', 'off')
    if mode == 'both' and built_with != plain_words_digest():
        raise SystemExit('Index was not built with the current plain-words list; run index --plain-words both')
    if mode != 'both' and built_with != 'off':
        raise SystemExit('Index has plain words in its chunks; evaluate it with --plain-words both')
    generic_questions = GENERIC_QUESTIONS
    if mode != 'off':
        plain = load_plain_words()
        probes = [dict(p, asked=p['question'], question=expand_question(p['question'], plain)) for p in probes]
        generic_questions = [expand_question(q, plain) for q in GENERIC_QUESTIONS]
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
    generic = embed(model, [prefix + q for q in generic_questions])
    unload(model)
    result = evaluate_db(db, model, probes, vectors, use_vec,
                         {'median': round(statistics.median(embed_ms), 1),
                          'p95': round(percentile(embed_ms, 0.95), 1)})
    result['backend'] = backend if use_vec else 'python-exact-cosine'
    result['isolation'] = isolation_check(db, generic, generic_questions, use_vec)
    result['plain_words'] = {'mode': mode,
                             'expanded_questions': sum(p.get('asked', p['question']) != p['question'] for p in probes)}
    result['index'] = index_stats(db, model)
    result['index']['db_bytes'] = path.stat().st_size
    result['evaluated_on'] = date.today().isoformat()
    result['tag'] = result_tag(args)
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / f'{slug(model)}-{result_tag(args)}-eval.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'model': model, 'backend': result['backend'], 'summary': {
        mode: {k: result['summary'][mode][k] for k in ('all', 'fact', 'negation', 'family', 'context_kept')}
        for mode in MODES}, 'isolation': result['isolation']}, indent=2))


def cmd_report(args):
    if not re.fullmatch(r'[a-z0-9-]{1,40}', args.tag):
        raise SafetyError('Report tag must be a short plain name such as r2-direct')
    rows, speed, misses, depth = [], [], [], []
    labels = None
    for model in MODELS:
        path = RESULTS / f'{slug(model)}-{args.tag}-eval.json'
        if not path.exists():
            continue
        result = json.loads(path.read_text())
        labels = result['labels']
        index = result['index'] or {}
        recall_at = result['summary']['vector'].get('recall_at')
        if recall_at:
            needed = {e['probe']: e.get('rank_needed') for e in result['details']['vector']}
            depth.append(f"| {model} | " + ' | '.join(f'{recall_at[str(k)]:.2f}' for k in RECALL_KS) + ' | '
                         + ' | '.join(str(needed.get(p)) for p in FORM_FIELD_PROBES) + ' |')
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
    out = [f'# RAG retrieval trial: generated tables ({args.tag})', '',
           '| Model | Mode | recall@8 | recall@3 | facts @8/@3 | negation @8/@3 | family @8/@3 | context kept |',
           '|---|---|---|---|---|---|---|---|', *rows, '',
           '| Model | Size | Disk | Dims | Chunks embedded (timed) | Chunks/s | First batch (load) | '
           'Query embed median/p95 | Search median vec/kw/hybrid | Index file |',
           '|---|---|---|---|---|---|---|---|---|---|', *speed, '']
    if depth:
        out += ['Vector search scored deeper (recall at k), and how deep it must go for the two form-field facts:', '',
                '| Model | ' + ' | '.join(f'@{k}' for k in RECALL_KS) + ' | '
                + ' | '.join(f'{p} rank' for p in FORM_FIELD_PROBES) + ' |',
                '|---|' + '---|' * (len(RECALL_KS) + len(FORM_FIELD_PROBES)), *depth, '']
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
    (RESULTS / f'report-{args.tag}.md').write_text(text)
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
            command.add_argument('--form-fields', default='label', choices=FORM_FIELD_MODES,
                                 help='a block of short form fields: relabel it, or split it per field '
                                      '(default %(default)s)')
            command.add_argument('--reuse-from', metavar='FOLDER',
                                 help="copy vectors for unchanged chunks from that index folder ('' = round 1)")
        if name in ('index', 'evaluate'):
            command.add_argument('--index-dir', default='', metavar='FOLDER',
                                 help="index subfolder of target/rag, e.g. r2 ('' = round 1, the default)")
            command.add_argument('--plain-words', default='off', choices=PLAIN_WORD_MODES,
                                 help='round 3 plain-word list (plain_words.json): add chart words to questions '
                                      '(query), and also plain words to chunks (both; index too) '
                                      '(default %(default)s)')
        if name == 'evaluate':
            command.add_argument('--questions', default='probes', choices=QUESTION_SETS,
                                 help='probes.json questions, or the held-out wording in heldout.json for '
                                      'some of them (same labels) (default %(default)s)')
            command.add_argument('--labels', default='direct', choices=LABEL_SETS,
                                 help='relevance labels: direct (labels.json) or regex (round 1) (default %(default)s)')
    report = sub.add_parser('report')
    report.add_argument('--tag', default='r2-direct', help='which results to tabulate, e.g. r1-regex')
    args = parser.parse_args(argv)
    {'pull': cmd_pull, 'index': cmd_index, 'evaluate': cmd_evaluate, 'report': cmd_report}[args.command](args)


if __name__ == '__main__':
    main()
