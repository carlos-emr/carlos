# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Load a frozen coverage seed plus independently editable source records."""
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import subprocess


def exclusion_blob(evidence, source):
    """Package annotations have no metrics, but their source bytes are still bound."""
    blob = evidence.get('exclusionSourceBlobs', {}).get(source)
    if blob is None:
        tree = evidence.get('sourceTree', '')
        blob = subprocess.check_output(['git', 'rev-parse', '--verify',
            tree + ':' + source.removeprefix('src/main/java/')], text=True).strip()
    if not isinstance(blob, str) or not re.fullmatch(r'[0-9a-f]{40}', blob):
        raise ValueError('Exclusion requires a source blob')
    return blob


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate coverage key: ' + key)
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


def source_path(value):
    if (not isinstance(value, str) or not value.startswith('src/main/java/')
            or PurePosixPath(value).as_posix() != value
            or '..' in PurePosixPath(value).parts or '\\' in value):
        raise ValueError('Coverage records require a canonical Java source path')
    return value


def validate_floor(row):
    for metric in ('lines', 'instructions', 'branches'):
        value = row.get(metric)
        if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 100:
            raise ValueError('Floors must be finite percentages for all three metrics')


def policy_directory(manifest, value):
    if (not isinstance(value, str) or not value or PurePosixPath(value).is_absolute()
            or '..' in PurePosixPath(value).parts or '\\' in value):
        raise ValueError('Coverage directories must be relative to the manifest')
    directory = manifest.parent / value
    if not directory.resolve().is_relative_to(manifest.parent.resolve()):
        raise ValueError('Coverage directory escapes the manifest directory')
    return directory


def read_evidence(directory, identity, base):
    if not isinstance(identity, str) or not re.fullmatch(r'[0-9a-f]{64}', identity):
        raise ValueError('Coverage evidence requires a SHA-256 identity')
    path = directory / (identity + '.json')
    if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != identity:
        raise ValueError('Coverage evidence digest mismatch: ' + identity)
    evidence = read_json(path)
    if evidence.get('schemaVersion') != 1 or evidence.get('base') != base:
        raise ValueError('Coverage evidence uses a different schema or reviewed base')
    if not isinstance(evidence.get('files'), dict) or not evidence['files']:
        raise ValueError('Coverage evidence requires source-bound floors')
    return evidence


def load_policy(path):
    """Keep v1 readable for importing existing PRs and standalone historical checks."""
    manifest = read_json(path)
    if manifest.get('schemaVersion') != 2:
        return manifest
    records = policy_directory(path, manifest.get('recordsDirectory'))
    evidence_directory = policy_directory(path, manifest.get('evidenceDirectory'))
    seed_id = manifest.get('seedEvidenceSha256')
    seed = read_evidence(evidence_directory, seed_id, manifest.get('base'))
    cache = {seed_id: seed}
    result = {'schemaVersion': 2, 'base': manifest['base'],
              'files': dict(seed['files']), 'exclusions': dict(seed.get('exclusions', {})),
              'zeroCoverageWaivers': dict(seed.get('zeroCoverageWaivers', {})),
              'sourceEvidence': {}, 'exclusionSourceBlobs': dict(manifest.get('seedExclusionSourceBlobs', {}))}
    if set(result['exclusionSourceBlobs']) != set(result['exclusions']):
        raise ValueError('Seed exclusions require exact source blob bindings')
    result['sourceEvidence'] = dict.fromkeys(set(result['files']) | set(result['exclusions']), seed)
    for record_path in sorted(records.rglob('*.json')):
        if record_path.is_symlink() or not record_path.resolve().is_relative_to(records.resolve()):
            raise ValueError('Coverage record escapes its directory')
        record = read_json(record_path)
        source = source_path(record.get('path'))
        if record_path.relative_to(records).as_posix() != source + '.json':
            raise ValueError('Coverage record filename must match its source path')
        if record.get('schemaVersion') != 1:
            raise ValueError('Unsupported source coverage record schema')
        kind = record.get('kind')
        if kind not in ('floor', 'exclusion', 'remove'):
            raise ValueError('Unknown source coverage record kind')
        for key in ('files', 'exclusions', 'zeroCoverageWaivers', 'sourceEvidence', 'exclusionSourceBlobs'):
            result[key].pop(source, None)
        if kind == 'remove':
            continue  # A still-changed source fails the checker's missing-floor check.
        identity = record.get('evidenceSha256')
        if identity not in cache:
            cache[identity] = read_evidence(evidence_directory, identity, manifest['base'])
        evidence = cache[identity]
        result['sourceEvidence'][source] = evidence
        if kind == 'exclusion':
            exclusion = record.get('exclusion')
            if exclusion != evidence.get('exclusions', {}).get(source) or not isinstance(exclusion, dict):
                raise ValueError('Exclusion is absent from its coverage evidence')
            result['exclusions'][source] = exclusion
            result['exclusionSourceBlobs'][source] = record.get('sourceBlob')
            continue
        floor = record.get('floor')
        measured = evidence['files'].get(source)
        if (not isinstance(floor, dict) or not isinstance(measured, dict)
                or floor.get('sourceBlob') != measured.get('sourceBlob')):
            raise ValueError('Floor is absent from its source-bound coverage evidence')
        validate_floor(floor)
        validate_floor(measured)
        for metric in ('lines', 'instructions', 'branches'):
            if floor[metric] < measured[metric]:
                raise ValueError('Floor cannot be lower than its measured evidence')
        if not set(measured.get('requiredRefusalLines', [])) <= set(floor.get('requiredRefusalLines', [])):
            raise ValueError('Floor cannot discard measured refusal lines')
        result['files'][source] = floor
        if 'zeroCoverageWaiver' in record:
            waiver = record['zeroCoverageWaiver']
            if waiver != evidence.get('zeroCoverageWaivers', {}).get(source):
                raise ValueError('Zero-coverage review is absent from its evidence')
            result['zeroCoverageWaivers'][source] = waiver
    return result


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def archive_evidence(directory, raw):
    identity = hashlib.sha256(raw).hexdigest()
    path = directory / (identity + '.json')
    directory.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.is_symlink() or path.read_bytes() != raw:
            raise ValueError('Existing coverage evidence has changed')
    else:
        path.write_bytes(raw)
    return identity


def migrate_policy(path):
    """Preserve the complete legacy document byte for byte; never remeasure it."""
    legacy = read_json(path)
    if legacy.get('schemaVersion') != 1 or not legacy.get('files'):
        raise ValueError('Migration requires a nonempty legacy coverage policy')
    identity = archive_evidence(path.parent / 'evidence', path.read_bytes())
    write_json(path, {'schemaVersion': 2, 'base': legacy['base'],
                     'seedEvidenceSha256': identity,
                     'recordsDirectory': 'floors', 'evidenceDirectory': 'evidence',
                     'seedExclusionSourceBlobs': {source: exclusion_blob(legacy, source)
                         for source in sorted(legacy.get('exclusions', {}))}})


def import_sources(path, incoming_path, sources):
    """Import only selected paths; retain existing higher floors and refusal checks."""
    manifest = read_json(path)
    if manifest.get('schemaVersion') != 2:
        raise ValueError('Migrate the coverage policy before importing source records')
    active = load_policy(path)
    incoming = read_json(incoming_path)
    if incoming.get('schemaVersion') != 1 or incoming.get('base') != manifest['base']:
        raise ValueError('Incoming evidence uses a different schema or reviewed base')
    identity = hashlib.sha256(incoming_path.read_bytes()).hexdigest()
    updates = []
    for source in sorted(set(sources)):
        source_path(source)
        record = {'schemaVersion': 1, 'path': source, 'evidenceSha256': identity}
        if source in incoming.get('files', {}):
            floor = dict(incoming['files'][source])
            validate_floor(floor)
            previous = active['files'].get(source, {})
            for metric in ('lines', 'instructions', 'branches'):
                floor[metric] = max(floor[metric], previous.get(metric, 0))
            refusals = sorted(set(floor.get('requiredRefusalLines', []))
                              | set(previous.get('requiredRefusalLines', [])))
            if refusals:
                floor['requiredRefusalLines'] = refusals
            record.update(kind='floor', floor=floor)
            waiver = incoming.get('zeroCoverageWaivers', {}).get(source)
            if waiver is not None:
                record['zeroCoverageWaiver'] = waiver
        elif source in incoming.get('exclusions', {}):
            record.update(kind='exclusion', exclusion=incoming['exclusions'][source],
                          sourceBlob=exclusion_blob(incoming, source))
        else:
            raise ValueError('Selected source needs a measured floor or documented exclusion: ' + source)
        updates.append((source, record))
    if not updates:
        raise ValueError('Select at least one source to import')
    archive_evidence(policy_directory(path, manifest['evidenceDirectory']), incoming_path.read_bytes())
    directory = policy_directory(path, manifest['recordsDirectory'])
    for source, record in updates:
        write_json(directory / (source + '.json'), record)
