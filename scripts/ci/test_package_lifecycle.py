# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import json
from pathlib import Path
import tempfile
import subprocess
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import release_verification as gate
from junit_evidence import EvidenceError
from package_lifecycle import CELL_ASSERTIONS, validate_config, validate_inventory


class PackageLifecycleTest(unittest.TestCase):
    def test_failed_result_retains_diagnosis_without_reading_unreconciled_private_files(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('lifecycle_inputs', Path(__file__).with_name('run-package-lifecycle.py'))
        controller = importlib.util.module_from_spec(spec); spec.loader.exec_module(controller)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            private = root / 'private'; private.mkdir()
            (private / 'unfinished.log').symlink_to('/unreadable-root-owned-log')
            diagnosis = root / 'lifecycle-failure.json'
            diagnosis.write_text('{"failureType":"ProcessGroupFailure","hostRequiresReconciliation":true}')
            inputs = controller.retained_inputs(root, root / 'result.json', False)
            self.assertEqual(inputs, [{'path': diagnosis.name, 'sha256': gate.digest(diagnosis)}])
            self.assertTrue((private / 'unfinished.log').is_symlink())
            with self.assertRaises(EvidenceError):
                controller.retained_inputs(root, root / 'result.json', True)

    def test_failed_container_is_stopped_and_retained_and_uncertain_shutdown_cannot_pass(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('lifecycle_stop', Path(__file__).with_name('run-package-lifecycle.py'))
        controller = importlib.util.module_from_spec(spec); spec.loader.exec_module(controller)
        for code, state, expected in [(0, 'false', True), (0, 'true', False), (1, 'false', False)]:
            with self.subTest(code=code, state=state), patch.object(controller.subprocess, 'run',
                    side_effect=[SimpleNamespace(returncode=code), SimpleNamespace(returncode=0, stdout=state)]) as run:
                self.assertIs(controller.stop_failed_cell('owned-fixture'), expected)
                self.assertEqual(run.call_args_list[0].args[0][-4:], ['stop', '--time', '30', 'owned-fixture'])
                self.assertFalse(any('rm' in call.args[0] for call in run.call_args_list))
        for failure in (PermissionError('synthetic'), subprocess.TimeoutExpired(['docker'], 45)):
            with patch.object(controller.subprocess, 'run', side_effect=failure):
                self.assertFalse(controller.stop_failed_cell('owned-fixture'))

    def test_local_daemon_selection_preserves_registry_config_and_runtime_proxy_defaults(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('lifecycle_host', Path(__file__).with_name('run-package-lifecycle.py'))
        controller = importlib.util.module_from_spec(spec); spec.loader.exec_module(controller)
        with patch.dict('os.environ', {'DOCKER_HOST': 'tcp://FAKE:2375', 'DOCKER_CONTEXT': 'FAKE',
                'DOCKER_TLS_VERIFY': '1', 'DOCKER_CERT_PATH': '/FAKE', 'DOCKER_CONFIG': '/FAKE/registry-config',
                'HTTPS_PROXY': 'http://FAKE-outer-proxy:8080'}, clear=True):
            self.assertEqual(controller.docker_environment(), {'DOCKER_CONFIG': '/FAKE/registry-config',
                'HTTPS_PROXY': 'http://FAKE-outer-proxy:8080'})

    def fixture(self, root):
        source = {'commit': 'a' * 40, 'tree': 'b' * 40, 'clean': True}
        document = {'schemaVersion': 1, 'source': source, 'profile': 'ON', 'warSha256': 'c' * 64, 'cells': []}
        for role in ('clean', 'main', 'historical'):
            cell = {'role': role, 'profile': 'ON', 'source': source, 'warSha256': 'c' * 64,
                'failureType': None, 'assertions': dict.fromkeys(CELL_ASSERTIONS, True),
                'dependencyContract': {'floor': '1.3.1', 'installedCli': '1.3.0', 'olderCliRefused': True,
                    'compatibleCliResolved': True, 'statePreserved': True},
                'initialDeployment': {'scope': 'installed-package-application', 'packageSha256': 'd' * 64,
                    'contentSha256': 'e' * 64},
                'candidateDeployment': {'scope': 'installed-package-application', 'warSha256': 'c' * 64,
                    'contentSha256': 'f' * 64}}
            if role != 'historical': cell['dependencyContract']['installedCli'] = '1.3.1'
            if role in ('main', 'historical'):
                pins = gate.read_json(gate.ROOT / 'docs/testing/release-2026.08-lifecycle-baselines.json')
                baseline = pins if role == 'main' else pins['additionalSupportedBaselines'][0]
                cell['initialDeployment']['packageSha256'] = next(a['sha256'] for a in baseline['assets'] if a['name'].startswith('carlos-emr_'))
            path = root / (role + '.json'); gate.write_json(path, cell)
            document['cells'].append({'role': role, 'path': path.name, 'sha256': gate.digest(path)})
        (root / 'lifecycle.json').write_text(json.dumps(document))
        return source, document

    def validate(self, root, source, document):
        (root / 'lifecycle.json').write_text(json.dumps(document))
        return validate_inventory(document, source, 'ON', 'c' * 64, root, gate.read_json, gate.digest)

    def test_main_cannot_replace_the_distinct_historical_or_clean_cell(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source, document = self.fixture(root)
            self.assertEqual(self.validate(root, source, document)['scope'], 'orchestrated-package-lifecycle')
            for roles in (document['cells'][:2], [document['cells'][0], document['cells'][1], document['cells'][1]]):
                broken = copy.deepcopy(document); broken['cells'] = roles
                with self.assertRaises(EvidenceError): self.validate(root, source, broken)

    def test_green_cell_without_same_source_province_war_or_cleanup_cannot_qualify(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source, document = self.fixture(root)
            row = document['cells'][1]; path = root / row['path']; original = gate.read_json(path)
            for name, value in [('source', {}), ('profile', 'BC'), ('warSha256', '0' * 64),
                    ('failureType', 'TimeoutExpired'), ('candidateDeployment', {}), ('initialDeployment', {}),
                    ('dependencyContract', {})]:
                broken = dict(original); broken[name] = value; path.write_text(json.dumps(broken))
                row['sha256'] = gate.digest(path)
                with self.assertRaises(EvidenceError): self.validate(root, source, document)
            for value in (False, 1, None):
                broken = copy.deepcopy(original); broken['assertions']['clinical-cleanup-preserved'] = value
                path.write_text(json.dumps(broken)); row['sha256'] = gate.digest(path)
                with self.assertRaises(EvidenceError): self.validate(root, source, document)

    def test_modified_or_linked_cell_is_rejected_even_when_summary_stays_green(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source, document = self.fixture(root)
            row = document['cells'][0]; path = root / row['path']
            path.write_text(path.read_text() + '\n')
            with self.assertRaises(EvidenceError): self.validate(root, source, document)
            path.unlink(); path.symlink_to(root / 'main.json'); row['sha256'] = gate.digest(root / 'main.json')
            with self.assertRaises(EvidenceError): self.validate(root, source, document)

    def test_mutable_runtime_or_missing_drugref_cli_and_baselines_are_refused(self):
        packages = {name: {'path': '/FAKE/' + name + '.deb', 'sha256': 'd' * 64}
                    for name in ('emr', 'drugref', 'ctl')}
        config = {'runtimeImage': 'sha256:' + 'a' * 64, 'caFile': '/FAKE/ca.pem',
                  'browserDirectory': '/FAKE/browser', 'packages': {key: packages for key in
                    ('target', '2026.08.0-beta6', '2026.08.0-beta5')}}
        validate_config(config)
        for name, value in [('runtimeImage', 'ubuntu:26.04'), ('caFile', 'relative'), ('packages', {'target': packages})]:
            broken = copy.deepcopy(config); broken[name] = value
            with self.assertRaises(EvidenceError): validate_config(broken)
        for name in ('drugref', 'ctl'):
            broken = copy.deepcopy(config); broken['packages']['2026.08.0-beta5'].pop(name)
            with self.assertRaises(EvidenceError): validate_config(broken)

    def test_host_collection_and_final_verification_require_lifecycle_byte_binding(self):
        from test_verify_release_evidence import ReleaseEvidenceTest
        helper = ReleaseEvidenceTest('test_clean_source_bound_control_is_ready')
        helper.setUp(); self.addCleanup(helper.doCleanups)
        producer = helper.root / 'lifecycle.py'
        producer.write_text('''import sys,json,pathlib
sys.path.insert(0,sys.argv[1])
import release_verification as gate
from package_lifecycle import CELL_ASSERTIONS
directory=pathlib.Path(sys.argv[2]); artifact=pathlib.Path(sys.argv[3]); source=gate.source_identity(pathlib.Path.cwd())
document={'schemaVersion':1,'source':source,'profile':'ON','warSha256':gate.digest(artifact),'cells':[]}
for role in ['clean','main','historical']:
 cell={'role':role,'profile':'ON','source':source,'warSha256':gate.digest(artifact),'failureType':None,
  'assertions':dict.fromkeys(CELL_ASSERTIONS,True),
  'dependencyContract':{'floor':'1.3.1','installedCli':'1.3.0','olderCliRefused':True,'compatibleCliResolved':True,'statePreserved':True},
  'initialDeployment':{'scope':'installed-package-application','packageSha256':'d'*64,'contentSha256':'e'*64},
  'candidateDeployment':{'scope':'installed-package-application','warSha256':gate.digest(artifact),'contentSha256':'f'*64}}
 if role!='historical':cell['dependencyContract']['installedCli']='1.3.1'
 if role in ['main','historical']:
  pins=gate.read_json(gate.ROOT/'docs/testing/release-2026.08-lifecycle-baselines.json')
  baseline=pins if role=='main' else pins['additionalSupportedBaselines'][0]
  cell['initialDeployment']['packageSha256']=next(a['sha256'] for a in baseline['assets'] if a['name'].startswith('carlos-emr_'))
 path=directory/(role+'.json');gate.write_json(path,cell)
 document['cells'].append({'role':role,'path':path.name,'sha256':gate.digest(path)})
gate.write_json(directory/'lifecycle.json',document)
gate.write_json(directory/'lifecycle-result.json',{'schemaVersion':1,'assertions':{'complete-lifecycle-core':True},'inputs':[
 {'path':p.name,'sha256':gate.digest(p)} for p in directory.glob('*.json')]})
''')
        check = {'id': 'lifecycle-core', 'kind': 'command', 'profiles': ['ON'], 'packaged': True,
            'deploymentScope': 'orchestrated-package-lifecycle',
            'command': [__import__('sys').executable, str(producer), str(Path(__file__).parent), '{run}', '{artifact}'],
            'resultContract': {'file': 'lifecycle-result.json', 'assertions': ['complete-lifecycle-core']}}
        helper.plan['checks'].append(check)
        helper.plan['workstreams'][0]['requiredChecks'].append(check['id'])
        helper.save_plan(); helper.commit(); helper.seed()
        receipt = gate.run_check(helper.plan_path, check['id'], helper.evidence, 'ON', 1,
                                  artifact=helper.artifact, root=helper.root)
        self.assertEqual(receipt['outcome'], 'PASS', receipt)
        self.assertTrue(helper.verify()['releaseReady'])
        receipt_path = helper.evidence / receipt['runId'] / 'receipt.json'
        receipt['deployment']['warSha256'] = '0' * 64; receipt_path.write_text(json.dumps(receipt))
        with self.assertRaisesRegex(EvidenceError, 'installed-byte binding'): helper.verify()

