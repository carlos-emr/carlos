# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Offline tests for the retrieval-only RAG trial. No Ollama, no network.

Run from tools/ai-clinical-summary-draft:
    python3 -m unittest discover -s rag/tests -t .
"""
import hashlib
import math
from pathlib import Path
import re
import sys
import tempfile
import unittest
from unittest import mock
from urllib.request import ProxyHandler

TOOL = Path(__file__).resolve().parents[2]
for path in (TOOL, TOOL / 'rag'):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

import rag_trial as rag  # noqa: E402


def fake_vector(text, dims=32):
    """Deterministic bag-of-words hash embedding; stands in for Ollama in tests."""
    vector = [0.0] * dims
    for word in re.findall(r'[a-z]+', text.lower()):
        digest = hashlib.sha256(word.encode()).digest()
        vector[digest[0] % dims] += 1.0 if digest[1] % 2 else -1.0
    norm = math.sqrt(sum(x * x for x in vector)) or 1.0
    return [x / norm for x in vector]


NOTE = (
    "Clerking Doctor\nDr. Test Person (SpR)\n\n"
    "Presenting Complaint\nSudden severe chest pain worse on deep breathing.\n\n"
    "Past Medical History\n- Asthma\n\n"
    "Allergies\nNil\n\n"
    "Family History\n- Mother has type 2 diabetes\n- Father has HTN\n\n"
    "Plan\n" + ''.join(f"- Step {n}: continue monitoring observations closely and review.\n" for n in range(40))
)


class ChunkerTest(unittest.TestCase):
    def setUp(self):
        self.chunks = rag.chunk_note('NHSSYN999', 'NHSSYN999-n001', '2026-01-05', NOTE)

    def test_chunks_keep_date_heading_and_exact_offsets(self):
        family = next(c for c in self.chunks if 'Mother has type 2 diabetes' in c['text'])
        self.assertEqual('Family History', family['heading'])
        self.assertTrue(family['embed_text'].startswith('Date: 2026-01-05. Section: Family History.\n'))
        self.assertNotIn('Asthma', family['text'])  # one section per chunk
        for chunk in self.chunks:
            self.assertEqual(NOTE[chunk['start']:chunk['end']], chunk['text'])
            self.assertIn('Date: 2026-01-05.', chunk['embed_text'])

    def test_tiny_clinical_section_stays_its_own_chunk(self):
        allergies = next(c for c in self.chunks if c['text'].startswith('Allergies'))
        self.assertEqual('Allergies\nNil', allergies['text'].strip())
        self.assertEqual('Allergies', allergies['heading'])

    def test_long_section_is_split_and_every_piece_keeps_heading(self):
        plan = [c for c in self.chunks if c['heading'] == 'Plan']
        self.assertGreater(len(plan), 1)
        for chunk in plan:
            self.assertLessEqual(len(chunk['text']), rag.MAX_CHARS)
            self.assertIn('Section: Plan.', chunk['embed_text'])
        self.assertNotIn('Plan', plan[-1]['text'].split('\n')[0])  # heading travels only via the prefix

    def test_every_character_is_covered_once(self):
        covered = sorted((c['start'], c['end']) for c in self.chunks)
        for (_, end), (start, _) in zip(covered, covered[1:]):
            self.assertLessEqual(end, start)
        text = ''.join(NOTE[s:e] for s, e in covered)
        self.assertEqual(re.sub(r'\s', '', NOTE), re.sub(r'\s', '', text))

    def test_real_synthetic_corpus_chunks_losslessly(self):
        notes = rag.load_notes()
        self.assertEqual(50, len({patient for patient, _, _ in notes}))
        chunks = rag.chunk_corpus(notes)
        bodies, counts = {}, {}
        for patient, _date, body in notes:
            counts[patient] = counts.get(patient, 0) + 1
            bodies[f'{patient}-n{counts[patient]:03d}'] = body
        for chunk in chunks:
            self.assertRegex(chunk['patient_id'], r'^NHSSYN[0-9]{3}$')
            self.assertEqual(bodies[chunk['note_id']][chunk['start']:chunk['end']], chunk['text'])
            self.assertLessEqual(len(chunk['text']), rag.MAX_CHARS)


class RelevanceTest(unittest.TestCase):
    def chunks(self, *texts):
        return [{'id': n, 'text': text} for n, text in enumerate(texts, 1)]

    def test_all_groups_must_match_in_one_chunk_case_insensitively(self):
        groups = rag.compile_groups([['nimodipine'], ['60 ?mg']])
        self.assertTrue(rag.relevant(groups, 'Start NIMODIPINE 60mg every 4 hours'))
        self.assertFalse(rag.relevant(groups, 'Continue nimodipine 30 mg'))
        self.assertFalse(rag.relevant([], 'anything'))

    def test_alternatives_inside_a_group(self):
        groups = rag.compile_groups([['potassium|k\\+'], ['3\\.3']])
        self.assertTrue(rag.relevant(groups, 'Mild hypokalemia (K+ 3.3 mmol/L)'))

    def test_fact_split_across_chunks_needs_top_k_to_cover_every_group(self):
        probe = {'groups': [['nimodipine'], ['amlodipine']]}
        chunks = self.chunks('nimodipine 30 mg taper', 'amlodipine 5 mg OD', 'unrelated')
        prepared = rag.prepare_probe(probe, chunks)
        self.assertTrue(prepared['spans'])
        texts = {c['id']: c['text'] for c in chunks}
        self.assertTrue(rag.is_hit(prepared, [1, 2], texts))
        self.assertFalse(rag.is_hit(prepared, [1, 3], texts))

    def test_group_that_matches_nowhere_in_the_chart_is_dropped_and_reported(self):
        probe = {'groups': [['conflict'], ['nimodipine']]}
        prepared = rag.prepare_probe(probe, self.chunks('nimodipine 60 mg', 'other'))
        self.assertEqual([['conflict']], prepared['dropped_groups'])
        self.assertEqual([1], prepared['relevant'])
        self.assertFalse(rag.prepare_probe({'groups': [['absent']]}, self.chunks('x'))['answerable'])

    def test_single_chunk_hit(self):
        prepared = rag.prepare_probe({'groups': [['allerg'], ['nil']]}, self.chunks('Allergies\nNil', 'Plan'))
        self.assertFalse(prepared['spans'])
        self.assertTrue(rag.is_hit(prepared, [2, 1], {}))
        self.assertFalse(rag.is_hit(prepared, [2], {}))


class ProbeFileTest(unittest.TestCase):
    def test_probes_cover_every_labelled_fact_and_minimum_probe_counts(self):
        probes = rag.load_probes()  # raises if any labelled fact lacks exactly one probe
        kinds = [p['kind'] for p in probes]
        self.assertGreaterEqual(kinds.count('negation'), 6)
        self.assertGreaterEqual(kinds.count('family'), 6)
        self.assertEqual(sum(len(v) for v in rag.load_facts().values()), kinds.count('fact'))

    def test_questions_do_not_simply_copy_the_answer(self):
        for probe in rag.load_probes():
            if probe['kind'] == 'fact':
                groups = rag.compile_groups(probe['groups'])
                self.assertFalse(rag.relevant(groups, probe['question']), probe['id'])
            else:
                # Negation/family probes name the condition on purpose, but must not give
                # away the negation or the family-history context they are testing.
                self.assertIsNone(re.search(probe['context_pattern'], probe['question'], re.I), probe['id'])


class RrfTest(unittest.TestCase):
    def test_fusion_order(self):
        # 1: 1/61+1/62, 3: 1/63+1/61, 2: 1/62, 4: 1/63
        self.assertEqual([1, 3, 2, 4], rag.rrf([[1, 2, 3], [3, 1, 4]], k=60))

    def test_ties_break_by_best_rank_then_id(self):
        self.assertEqual([5, 7, 6, 8], rag.rrf([[5, 6], [7, 8]]))
        self.assertEqual([], rag.rrf([[], []]))

    def test_item_in_both_lists_beats_single_list_leader(self):
        self.assertEqual(9, rag.rrf([[1, 9], [2, 9]])[0])


class PatientIsolationTest(unittest.TestCase):
    TEXTS = ['Family History\nMother has type 2 diabetes', 'Allergies\nNil',
             'Plan\nStart nimodipine 60 mg every 4 hours', 'Investigations\nCT head: no haemorrhage']

    def build(self, tmp, use_vec):
        db, backend = rag.connect(Path(tmp) / 'test.sqlite')
        use_vec = use_vec and backend.startswith('sqlite-vec')
        chunks = []
        for patient in ('NHSSYN998', 'NHSSYN999'):
            for text in self.TEXTS:
                chunks.append({'patient_id': patient, 'note_id': patient + '-n001', 'date': '2026-01-05',
                               'heading': text.split('\n')[0], 'start': 0, 'end': len(text), 'text': text,
                               'embed_text': 'Date: 2026-01-05. Section: x.\n' + text})
        rag.create_schema(db, 32, use_vec)
        rag.insert_chunks(db, enumerate(chunks, 1))
        for number, chunk in enumerate(chunks, 1):
            rag.insert_vector(db, number, chunk['patient_id'], fake_vector(chunk['embed_text']), use_vec)
        db.commit()
        return db, use_vec

    def check(self, use_vec):
        with tempfile.TemporaryDirectory() as tmp:
            db, use_vec = self.build(tmp, use_vec)
            owner = dict(db.execute('SELECT id, patient_id FROM chunks'))
            for patient in ('NHSSYN998', 'NHSSYN999'):
                for question in ('Does the patient have diabetes?', 'nimodipine dose', 'allergies'):
                    vector = fake_vector(question)
                    for mode in rag.MODES:
                        ids = rag.search(db, patient, mode, question, vector, 8, use_vec)
                        self.assertTrue(ids or mode == 'keyword')
                        self.assertEqual({patient}, {owner[i] for i in ids} or {patient}, (mode, question))
                # A query vector identical to the OTHER patient's chunk still only returns this patient.
                other = 'NHSSYN999' if patient == 'NHSSYN998' else 'NHSSYN998'
                blob = db.execute('SELECT embedding FROM vectors WHERE patient_id = ? LIMIT 1', (other,)).fetchone()[0]
                ids = rag.search(db, patient, 'vector', None, list(rag.unpack(blob)), 8, use_vec)
                self.assertEqual(4, len(ids))
                self.assertEqual({patient}, {owner[i] for i in ids})
            with self.assertRaises(rag.SafetyError):
                rag.search(db, 'demographic-1', 'keyword', 'x', None, 8, use_vec)
            db.close()

    def test_python_exact_cosine_never_returns_another_patient(self):
        self.check(use_vec=False)

    def test_sqlite_vec_partition_never_returns_another_patient(self):
        _db, backend = rag.connect(':memory:')
        if not backend.startswith('sqlite-vec'):
            self.skipTest('sqlite-vec not installed in target/rag/pylib')
        self.check(use_vec=True)

    def test_sqlite_vec_and_python_rank_identically(self):
        _db, backend = rag.connect(':memory:')
        if not backend.startswith('sqlite-vec'):
            self.skipTest('sqlite-vec not installed in target/rag/pylib')
        with tempfile.TemporaryDirectory() as tmp:
            db, _ = self.build(tmp, True)
            vector = fake_vector('diabetes in the mother')
            fast = rag.vector_search(db, 'NHSSYN998', vector, 8, True)
            slow = rag.vector_search(db, 'NHSSYN998', vector, 8, False)
            self.assertEqual([c for c, _ in slow], [c for c, _ in fast])
            for (_, a), (_, b) in zip(fast, slow):
                self.assertAlmostEqual(a, b, places=5)
            db.close()


class HostGuardTest(unittest.TestCase):
    def test_accepts_only_loopback_ollama_embedding_paths(self):
        for path in ('/api/embed', '/api/pull', '/api/tags', '/api/ps'):
            self.assertEqual('http://127.0.0.1:11434' + path, rag.guard_url('http://127.0.0.1:11434' + path))
        self.assertTrue(rag.guard_url('http://[::1]:11434/api/embed'))

    def test_rejects_hosted_or_non_loopback_hosts_and_generation_paths(self):
        for url in ('https://openrouter.ai/api/v1/embeddings', 'http://openrouter.ai:11434/api/embed',
                    'http://10.0.0.5:11434/api/embed', 'http://192.168.1.2:11434/api/embed',
                    'http://localhost:11434/api/embed', 'http://127.0.0.1.nip.io:11434/api/embed',
                    'http://user@127.0.0.1:11434/api/embed', 'https://127.0.0.1:11434/api/embed',
                    'http://127.0.0.1:8080/api/embed', 'http://127.0.0.1/api/embed',
                    'http://127.0.0.1:11434/api/generate', 'http://127.0.0.1:11434/api/chat',
                    'http://127.0.0.1:11434/v1/embeddings', 'http://127.0.0.1:11434/api/embed?x=1',
                    'ftp://127.0.0.1:11434/api/embed', 'http://0.0.0.0:11434/api/embed'):
            with self.assertRaises(rag.SafetyError, msg=url):
                rag.guard_url(url)

    def test_guard_runs_before_any_network_call(self):
        with mock.patch.object(rag, 'OLLAMA', 'https://api.example.com'), \
                mock.patch.object(rag._OPENER, 'open', side_effect=AssertionError('network used')):
            with self.assertRaises(rag.SafetyError):
                rag.ollama('/api/embed', {'model': 'bge-m3', 'input': ['x']})
        with mock.patch.object(rag._OPENER, 'open', side_effect=AssertionError('network used')):
            with self.assertRaises(rag.SafetyError):
                rag.ollama('/api/generate', {'model': 'x'})
            with self.assertRaises(rag.SafetyError):
                rag.embed('some-hosted-model', ['x'])

    def test_no_proxy_or_redirect_can_leave_loopback(self):
        # ProxyHandler({}) replaces the environment-proxy default and adds no proxy routes.
        self.assertFalse([h for h in rag._OPENER.handlers if isinstance(h, ProxyHandler)])
        self.assertTrue([h for h in rag._OPENER.handlers if isinstance(h, rag._NoRedirect)])
        self.assertIsNone(rag._NoRedirect().redirect_request(None, None, 302, 'Found', {}, 'http://x'))


class SyntheticIdTest(unittest.TestCase):
    def test_accepts_nhssyn_ids(self):
        notes = [('NHSSYN001', '2026-01-01', 'x'), ('NHSSYN050', '2026-01-02', 'y')]
        self.assertEqual(notes, rag.assert_synthetic(notes))

    def test_rejects_anything_else_before_indexing(self):
        for bad in ('NHSSYN01', 'NHSSYN0001', 'nhssyn001', 'demographic-3001', 'NHSSYN001\n',
                    ' NHSSYN001', 'NHSSYN١٢٣', 'TEST', None):
            with self.assertRaises(rag.SafetyError, msg=repr(bad)):
                rag.assert_synthetic([('NHSSYN001', '2026-01-01', 'x'), (bad, '2026-01-01', 'y')])
            with self.assertRaises(rag.SafetyError):
                rag.chunk_corpus([(bad, '2026-01-01', 'Plan\nx')])
        with self.assertRaises(rag.SafetyError):
            rag.assert_synthetic([])


class IncrementalIndexTest(unittest.TestCase):
    def test_patient_scope_parser_accepts_only_synthetic_ranges(self):
        self.assertEqual(['NHSSYN001', 'NHSSYN002', 'NHSSYN003'], rag.parse_patients('NHSSYN001-NHSSYN003'))
        self.assertEqual(['NHSSYN004', 'NHSSYN009'], rag.parse_patients('NHSSYN009,NHSSYN004'))
        for bad in ('demographic-1', 'NHSSYN001-3', 'NHSSYN1-NHSSYN3', ''):
            with self.assertRaises(rag.SafetyError, msg=bad):
                rag.parse_patients(bad)

    def test_index_resumes_in_short_calls_and_skips_embedded_chunks(self):
        calls = []

        def fake_embed(model, texts, keep_alive='5m'):
            calls.append(len(texts))
            self.assertLessEqual(len(texts), rag.BATCH)
            return [fake_vector(t) for t in texts]

        def fake_ollama(path, payload=None, **_kwargs):
            self.assertEqual('/api/ps', path)
            return {'models': [{'name': 'nomic-embed-text:latest', 'size': 1}]}

        args = type('Args', (), {'model': 'nomic-embed-text', 'patients': 'NHSSYN001', 'max_chunks': 40})
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(rag, 'OUT', Path(tmp)), mock.patch.object(rag, 'RESULTS', Path(tmp) / 'r'), \
                mock.patch.object(rag, 'embed', fake_embed), mock.patch.object(rag, 'unload'), \
                mock.patch.object(rag, 'ollama', fake_ollama), \
                mock.patch.object(rag, 'model_info', return_value={'size': 1, 'details': {}}), \
                mock.patch('builtins.print'):
            for _ in range(3):
                rag.cmd_index(args)
            db, _backend = rag.connect(Path(tmp) / 'nomic-embed-text.sqlite')
            total = db.execute('SELECT COUNT(*) FROM chunks').fetchone()[0]
            self.assertEqual(97, total)  # every NHSSYN001 chunk, inserted once
            self.assertEqual(total, db.execute('SELECT COUNT(*) FROM vectors').fetchone()[0])
            self.assertEqual([40, 40, 17], [r[0] for r in db.execute('SELECT chunks FROM runs ORDER BY id')])
            self.assertEqual({'NHSSYN001'}, {r[0] for r in db.execute('SELECT patient_id FROM chunks')})
            db.close()
            embedded = sum(calls)
            rag.cmd_index(args)  # nothing left: no embedding call, no new run row
            self.assertEqual(embedded, sum(calls))
            self.assertEqual(97, embedded)


class EvaluateSmokeTest(unittest.TestCase):
    def test_evaluate_db_scores_and_classifies_without_ollama(self):
        notes = [n for n in rag.load_notes() if n[0] == 'NHSSYN001']
        chunks = rag.chunk_corpus(notes)
        probes = [p for p in rag.load_probes() if p['patient'] == 'NHSSYN001']
        with tempfile.TemporaryDirectory() as tmp:
            db, _backend = rag.connect(Path(tmp) / 'smoke.sqlite')
            rag.create_schema(db, 32, False)
            rag.insert_chunks(db, enumerate(chunks, 1))
            for number, chunk in enumerate(chunks, 1):
                rag.insert_vector(db, number, chunk['patient_id'], fake_vector(chunk['embed_text']), False)
            db.commit()
            vectors = {p['id']: fake_vector(p['question']) for p in probes}
            result = rag.evaluate_db(db, 'test', probes, vectors, False)
            db.close()
        for mode in rag.MODES:
            summary = result['summary'][mode]
            self.assertEqual(len(probes), summary['all']['n'])
            misses = sum(summary['misses_by_kind'].values())
            hits = summary['per_patient']['NHSSYN001']['hit@8']
            self.assertEqual(len(probes), misses + hits)
        self.assertIn('NHSSYN001:discharge-conflict', result['labels']['spans_chunks'])


if __name__ == '__main__':
    unittest.main()
