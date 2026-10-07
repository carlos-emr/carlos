# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Offline tests for the retrieval-only RAG trial. No Ollama, no network.

Run from tools/ai-clinical-summary-draft:
    python3 -m unittest discover -s rag/tests -t .
"""
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
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

    def test_short_form_fields_keep_their_own_labels_for_embedding(self):
        note = ("Surgeon\nDr. Test Person\n\nAnaesthesia Type\nGeneral anaesthesia\n\n"
                "Procedure\nTotal left knee replacement with a long description of the steps taken.\n\n"
                "Tissue Removed\nDamaged cartilage\n\nEstimated Blood Loss\nMinimal\n\nComplications\nNone\n\n"
                "Plan\n1. Transfer to recovery.\n")
        chunks = rag.chunk_note('NHSSYN999', 'NHSSYN999-n002', '2026-01-05', note)
        block = next(c for c in chunks if 'Complications' in c['text'])
        self.assertIn('Complications', block['heading'])
        self.assertIn('Tissue Removed', block['heading'])
        self.assertIn('\nComplications: None', block['embed_text'])
        self.assertIn('\nEstimated Blood Loss: Minimal', block['embed_text'])
        self.assertIn('Complications\nNone', block['text'])  # stored text is still the exact chart slice
        anaesthesia = next(c for c in chunks if 'Anaesthesia Type' in c['text'])
        self.assertIn('Anaesthesia Type: General anaesthesia', anaesthesia['embed_text'])
        for chunk in chunks:
            self.assertEqual(note[chunk['start']:chunk['end']], chunk['text'])

    def test_split_mode_gives_each_short_form_field_its_own_chunk(self):
        note = ("Surgeon\nDr. Test Person\n\nAnaesthesia Type\nGeneral anaesthesia\n\n"
                "Tissue Removed\nDamaged cartilage\n\nEstimated Blood Loss\nMinimal\n\nComplications\nNone\n\n"
                "Plan\n1. Transfer to recovery.\n")
        chunks = rag.chunk_note('NHSSYN999', 'NHSSYN999-n003', '2026-01-05', note, form_fields='split')
        complications = next(c for c in chunks if 'Complications' in c['text'])
        self.assertEqual('Complications', complications['heading'])
        self.assertEqual('Date: 2026-01-05. Section: Complications.\nComplications: None', complications['embed_text'])
        self.assertNotIn('Minimal', complications['text'])
        for chunk in chunks:
            self.assertEqual(note[chunk['start']:chunk['end']], chunk['text'])
        with self.assertRaises(ValueError):
            rag.chunk_note('NHSSYN999', 'NHSSYN999-n003', '2026-01-05', note, form_fields='guess')

    def test_none_mode_is_round_ones_cutter(self):
        note = ("Surgeon\nDr. Test Person\n\nAnaesthesia Type\nGeneral anaesthesia\n\n"
                "Tissue Removed\nDamaged cartilage\n\nComplications\nNone\n")
        chunks = rag.chunk_note('NHSSYN999', 'NHSSYN999-n004', '2026-01-05', note, form_fields='none')
        block = next(c for c in chunks if 'Complications' in c['text'])
        self.assertNotIn('Complications', block['heading'])
        self.assertNotIn('Complications: None', block['embed_text'])

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


def row(text, date='2026-01-01', number=1):
    return {'id': number, 'text': text, 'date': date}


class RelevanceTest(unittest.TestCase):
    def chunks(self, *texts):
        return [row(text, number=n) for n, text in enumerate(texts, 1)]

    def test_all_groups_must_match_in_one_chunk_case_insensitively(self):
        groups = rag.compile_groups([['nimodipine'], ['60 ?mg']])
        self.assertTrue(rag.relevant(groups, row('Start NIMODIPINE 60mg every 4 hours')))
        self.assertFalse(rag.relevant(groups, row('Continue nimodipine 30 mg')))
        self.assertFalse(rag.relevant([], row('anything')))

    def test_alternatives_inside_a_group(self):
        groups = rag.compile_groups([['potassium|k\\+'], ['3\\.3']])
        self.assertTrue(rag.relevant(groups, row('Mild hypokalemia (K+ 3.3 mmol/L)')))

    def test_direct_phrases_ignore_case_and_whitespace_but_keep_word_edges(self):
        groups = rag.compile_groups([[{'text': 'Complications None'}]])
        self.assertTrue(rag.relevant(groups, row('Estimated Blood Loss\nMinimal\n\ncomplications\nNONE\n')))
        self.assertFalse(rag.relevant(groups, row('anaesthetic complications: None reported')))
        rr = rag.compile_groups([[{'text': 'RR 1'}]])
        self.assertTrue(rag.relevant(rr, row('HR 2\nBP 124/78\nRR 1\n')))
        self.assertFalse(rag.relevant(rr, row('RR 14 br/min')))
        # Only ASCII letters and digits count as word characters: the chart's mis-encoded degree sign follows 40.
        self.assertTrue(rag.relevant(rag.compile_groups([[{'text': 'Reached 40'}]]),
                                     row('Reached 40\u00c2\u00b0 flexion')))

    def test_dated_phrase_matches_only_that_days_note(self):
        groups = rag.compile_groups([[{'date': '2026-01-05', 'text': 'BP 124/78'}]])
        self.assertTrue(rag.relevant(groups, row('BP 124/78', '2026-01-05')))
        self.assertFalse(rag.relevant(groups, row('BP 124/78 mmHg', '2025-12-20')))

    def test_fact_split_across_chunks_needs_top_k_to_cover_every_group(self):
        probe = {'groups': [['nimodipine'], ['amlodipine']]}
        chunks = self.chunks('nimodipine 30 mg taper', 'amlodipine 5 mg OD', 'unrelated')
        prepared = rag.prepare_probe(probe, chunks)
        self.assertTrue(prepared['spans'])
        by_id = {c['id']: c for c in chunks}
        self.assertTrue(rag.is_hit(prepared, [1, 2], by_id))
        self.assertFalse(rag.is_hit(prepared, [1, 3], by_id))

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
        for label_set in rag.LABEL_SETS:
            for probe in rag.load_probes(label_set=label_set):
                if probe['kind'] != 'fact':
                    continue
                undated = [[dict(item, date=None) if isinstance(item, dict) else item for item in group]
                           for group in probe['groups']]
                self.assertFalse(rag.relevant(rag.compile_groups(undated), row(probe['question'])),
                                 f'{label_set} {probe["id"]}')
        for probe in rag.load_probes():
            if probe['kind'] == 'fact':
                continue
            # Negation/family probes name the condition on purpose, but must not give
            # away the negation or the family-history context they are testing.
            self.assertIsNone(re.search(probe['context_pattern'], probe['question'], re.I), probe['id'])

    def test_direct_labels_name_a_real_chart_passage_for_every_probe(self):
        chunks = [dict(c, id=n) for n, c in enumerate(rag.chunk_corpus(rag.load_notes()), 1)]
        per_patient = {}
        for chunk in chunks:
            per_patient.setdefault(chunk['patient_id'], []).append(chunk)
        probes = rag.load_probes(label_set='direct')
        self.assertEqual(set(rag.load_labels()), {p['id'] for p in probes})
        for probe in probes:
            prepared = rag.prepare_probe(probe, per_patient[probe['patient']])
            self.assertTrue(prepared['answerable'], probe['id'])
            self.assertEqual([], prepared['dropped_groups'], probe['id'])
        complications = next(p for p in probes if p['id'] == 'NHSSYN002:neg-complications')
        relevant = rag.prepare_probe(complications, per_patient['NHSSYN002'])['relevant']
        self.assertEqual(1, len(relevant))
        self.assertIn('Complications', next(c for c in chunks if c['id'] == relevant[0])['heading'])

    def test_every_label_phrase_matches_a_chunk_of_its_patient(self):
        chunks = rag.chunk_corpus(rag.load_notes())
        per_patient = {}
        for chunk in chunks:
            per_patient.setdefault(chunk['patient_id'], []).append(chunk)
        for probe_id, groups in rag.load_labels().items():
            patient = probe_id.split(':')[0]
            for group in groups:
                for item in group:
                    compiled = rag.compile_groups([[item]])[0]
                    self.assertTrue(any(rag.group_matches(compiled, c) for c in per_patient[patient]),
                                    f'{probe_id}: {item} matches no chunk')

    def test_label_file_is_checked(self):
        bad = [[[{'text': '  '}]], [[{'text': 'x', 'date': '5 Jan'}]], [[{'text': 'x', 'day': '2026-01-05'}]],
               ['Allergies: NKA'], [[]], []]
        with tempfile.TemporaryDirectory() as tmp:
            for evidence in bad:
                path = Path(tmp) / 'labels.json'
                path.write_text(json.dumps({'labels': {'NHSSYN001:x': {'evidence': evidence}}}))
                with self.assertRaises(ValueError, msg=repr(evidence)):
                    rag.load_labels(path)

    def test_drug_names_need_whole_words(self):
        self.assertIsNone(rag.DRUGS.search('No known drug or environmental allergies'))
        self.assertIsNotNone(rag.DRUGS.search('Ferrous sulfate (iron) 200 mg'))

    def test_round_one_regex_labels_still_load_for_comparison(self):
        probes = {p['id']: p for p in rag.load_probes(label_set='regex')}
        self.assertTrue(all(isinstance(item, str) for group in probes['NHSSYN001:taper']['groups'] for item in group))
        with self.assertRaises(ValueError):
            rag.load_probes(label_set='guess')


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

        args = type('Args', (), {'model': 'nomic-embed-text', 'patients': 'NHSSYN001', 'max_chunks': 40,
                                 'index_dir': '', 'reuse_from': None, 'form_fields': 'label'})
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

            # A second index folder reusing those vectors embeds only chunks whose text changed: none here.
            reuse = type('Args', (), {'model': 'nomic-embed-text', 'patients': 'NHSSYN001', 'max_chunks': 40,
                                      'index_dir': 'r2', 'reuse_from': '', 'form_fields': 'label'})
            rag.cmd_index(reuse)
            self.assertEqual(embedded, sum(calls))
            db, _backend = rag.connect(Path(tmp) / 'r2' / 'nomic-embed-text.sqlite')
            self.assertEqual(97, db.execute('SELECT COUNT(*) FROM vectors').fetchone()[0])
            self.assertEqual(97, db.execute('SELECT COUNT(*) FROM chunks').fetchone()[0])
            db.close()
            with self.assertRaises(rag.SafetyError):
                rag.index_dir('../elsewhere')


class ReuseIndexTest(unittest.TestCase):
    def run_index(self, tmp, model, folder, reuse_from, form_fields, calls, digest='d1'):
        def fake_embed(model_name, texts, keep_alive='5m'):
            calls.extend(texts)
            return [fake_vector(t) for t in texts]

        def fake_ollama(path, payload=None, **_kwargs):
            return {'models': [{'name': model + ':latest', 'size': 1}]}

        args = type('Args', (), {'model': model, 'patients': 'NHSSYN001', 'max_chunks': 400,
                                 'index_dir': folder, 'reuse_from': reuse_from, 'form_fields': form_fields})
        with mock.patch.object(rag, 'OUT', Path(tmp)), mock.patch.object(rag, 'RESULTS', Path(tmp) / 'results'), \
                mock.patch.object(rag, 'embed', fake_embed), mock.patch.object(rag, 'unload'), \
                mock.patch.object(rag, 'ollama', fake_ollama), \
                mock.patch.object(rag, 'model_info', return_value={'size': 1, 'details': {}, 'digest': digest}), \
                mock.patch('builtins.print'):
            rag.cmd_index(args)

    def test_one_call_reuses_unchanged_chunks_and_embeds_changed_ones(self):
        with tempfile.TemporaryDirectory() as tmp:
            first, second = [], []
            self.run_index(tmp, 'nomic-embed-text', '', None, 'none', first)
            self.run_index(tmp, 'nomic-embed-text', 'r2', '', 'label', second)
            changed = [c for c in rag.chunk_corpus(rag.load_notes(), 'label') if c['patient_id'] == 'NHSSYN001']
            round_one = {c['embed_text'] for c in rag.chunk_corpus(rag.load_notes(), 'none')}
            fresh = [c['embed_text'] for c in changed if c['embed_text'] not in round_one]
            self.assertTrue(fresh)
            self.assertEqual(sorted(rag.MODELS['nomic-embed-text']['document'] + t for t in fresh), sorted(second))
            old, _ = rag.connect(Path(tmp) / 'nomic-embed-text.sqlite')
            new, _ = rag.connect(Path(tmp) / 'r2' / 'nomic-embed-text.sqlite')
            source = dict(old.execute('SELECT c.embed_text, v.embedding FROM chunks c JOIN vectors v ON v.chunk_id = c.id'))
            copied = dict(new.execute('SELECT c.embed_text, v.embedding FROM chunks c JOIN vectors v ON v.chunk_id = c.id'))
            self.assertEqual(len(changed), new.execute('SELECT COUNT(*) FROM chunks').fetchone()[0])
            self.assertEqual(len(changed), new.execute('SELECT COUNT(*) FROM vectors').fetchone()[0])
            for text, blob in copied.items():
                if text in source:
                    self.assertEqual(source[text], blob)  # the very same vector, not re-embedded
            self.assertEqual('d1', dict(new.execute('SELECT key, value FROM meta'))['model_digest'])
            old.close()
            new.close()

    def test_reuse_is_refused_across_models_and_model_builds(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.run_index(tmp, 'nomic-embed-text', '', None, 'none', [])
            # The same file presented as another model's index: its stored model must not match.
            shutil.copy(Path(tmp) / 'nomic-embed-text.sqlite', Path(tmp) / 'bge-m3.sqlite')
            with self.assertRaisesRegex(SystemExit, 'another model or prefix'):
                self.run_index(tmp, 'bge-m3', 'r2', '', 'label', [])
            with self.assertRaisesRegex(SystemExit, 'another build'):
                self.run_index(tmp, 'nomic-embed-text', 'r3', '', 'label', [], digest='d2')
            with self.assertRaisesRegex(SystemExit, 'another build'):
                self.run_index(tmp, 'nomic-embed-text', '', None, 'none', [], digest='d2')  # resuming

    def test_folder_names_and_report_tags_are_checked(self):
        for bad in ('r1', 'results', 'pylib', '../x', 'R2'):
            with self.assertRaises(rag.SafetyError, msg=bad):
                rag.index_dir(bad)
        with self.assertRaises(rag.SafetyError):
            rag.cmd_report(type('Args', (), {'tag': '../x'}))


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
        recall_at = result['summary']['vector']['recall_at']
        self.assertEqual([str(k) for k in rag.RECALL_KS], list(recall_at))
        self.assertEqual(sorted(recall_at.values()), list(recall_at.values()))  # deeper never finds less
        self.assertTrue(all('rank_needed' in e for e in result['details']['vector']))



class PlainWordsTest(unittest.TestCase):
    """Round 3: the small hand-written list of plain words and chart words (plain_words.json)."""

    def setUp(self):
        self.entries = rag.load_plain_words()

    def test_question_with_a_plain_phrase_gets_the_charts_words(self):
        asked = 'Was there a collapsed lung on the X-ray?'
        self.assertEqual(rag.expand_question(asked, self.entries),
                         asked + ' (pneumothorax; PTX; CXR; chest radiograph)')

    def test_question_without_a_plain_phrase_is_unchanged(self):
        self.assertEqual(rag.expand_question('What was the main diagnosis?', self.entries),
                         'What was the main diagnosis?')

    def test_plain_phrases_need_whole_words(self):
        # "pulse" names the heart rate; "impulse" must not.
        self.assertEqual(rag.expand_question('Any impulse control problems?', self.entries),
                         'Any impulse control problems?')

    def test_chart_abbreviations_match_case_and_word_edges(self):
        self.assertEqual(rag.plain_words_for_chunk('three hours; bp fine; the hr team', self.entries), [])
        self.assertIn('blood pressure', rag.plain_words_for_chunk('BP 124/78, HR 88', self.entries))
        self.assertIn('oxygen level', rag.plain_words_for_chunk('SpO2 97% on air', self.entries))

    def test_chunk_plain_words_change_only_the_embedded_text(self):
        notes = [('NHSSYN001', '2026-01-05', 'Investigations\nCXR: no PTX.\n\nPlan\nHome tomorrow.')]
        plain = rag.chunk_corpus(notes, plain_words=self.entries)
        bare = rag.chunk_corpus(notes)
        self.assertEqual([c['text'] for c in plain], [c['text'] for c in bare])
        with_words = [c for c in plain if 'Plain words:' in c['embed_text']]
        self.assertEqual(len(with_words), 1)
        self.assertIn('collapsed lung', with_words[0]['embed_text'])
        self.assertIn('chest film', with_words[0]['embed_text'])

    def test_every_entry_has_plain_and_chart_words(self):
        self.assertGreaterEqual(len(self.entries), 20)
        for entry in self.entries:
            self.assertTrue(entry['plain'] and entry['chart'])

    def test_result_tags_keep_each_mode_apart(self):
        args = mock.Mock(index_dir='r2', labels='direct', plain_words='off')
        self.assertEqual(rag.result_tag(args), 'r2-direct')
        args.plain_words = 'query'
        self.assertEqual(rag.result_tag(args), 'r2-direct-pwq')
        args.index_dir, args.plain_words = 'r4', 'both'
        self.assertEqual(rag.result_tag(args), 'r4-direct-pwb')


if __name__ == '__main__':
    unittest.main()
