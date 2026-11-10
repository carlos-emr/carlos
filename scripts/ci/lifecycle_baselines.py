# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Curated distinct main and historical package baselines for release 2026.08."""
import re
from pathlib import Path
from junit_evidence import EvidenceError

REQUIRED_TAGS = {'2026.08.0-beta6', '2026.08.0-beta5'}
PACKAGE_PREFIXES = ('carlos-emr_', 'carlos-emr-drugref_', 'carlos-ctl_')


def matches(pattern, value):
    return isinstance(value, str) and re.fullmatch(pattern, value) is not None


def candidate_cli(document):
    """Keep the shipped CLI independent of unchanged published predecessors."""
    pin = Path(__file__).resolve().parents[2] / 'debian/carlos-ctl.pin'
    tags = re.findall(r'^tag=(.+)$', pin.read_text(), re.M)
    row = document.get('candidateCli') if isinstance(document, dict) else None
    if (len(tags) != 1 or not matches(r'[0-9][0-9A-Za-z.+\-]*', tags[0])
            or not isinstance(row, dict)
            or set(row) != {'tag', 'version', 'name', 'bytes', 'sha256', 'url'}):
        raise EvidenceError('Candidate CLI requires its separate reviewed published pin')
    tag = tags[0]
    name = 'carlos-ctl_' + tag.replace('-', '.') + '_all.deb'
    if (row['tag'] != tag or row['version'] != tag.replace('-', '~') or row['name'] != name
            or not matches(r'[0-9a-f]{64}', row['sha256'])
            or type(row['bytes']) is not int or row['bytes'] <= 0
            or row['url'] != 'https://github.com/carlos-emr/carlos-ctl/releases/download/' + tag + '/' + name):
        raise EvidenceError('Candidate CLI differs from its shipped version or exact published identity')
    return row


def required_baselines(document):
    if not isinstance(document, dict):
        raise EvidenceError('Lifecycle baseline document must be an object')
    candidate_cli(document)
    additional = document.get('additionalSupportedBaselines')
    if not isinstance(additional, list):
        raise EvidenceError('Distinct historical lifecycle baseline is required')
    rows = [document, *additional]
    if any(not isinstance(row, dict) or not isinstance(row.get('tag'), str)
           or not isinstance(row.get('sourceCommit'), str) or not isinstance(row.get('baselineRoles'), list)
           for row in rows):
        raise EvidenceError('Lifecycle baseline identity and roles are incomplete')
    if (len(rows) != 2 or {row.get('tag') for row in rows} != REQUIRED_TAGS
            or len({row.get('sourceCommit') for row in rows}) != 2
            or 'currentMain' not in document.get('baselineRoles', [])
            or not any('supported-prior-release' in row.get('baselineRoles', []) for row in additional)):
        raise EvidenceError('Current main cannot stand in for the distinct historical upgrade')
    for row in rows:
        if (not matches(r'[0-9a-f]{40}', row.get('sourceCommit'))
                or not matches(r'[0-9a-f]{40}', row.get('tagObject'))
                or row.get('provinces') not in (['ON', 'BC'], ['BC', 'ON'])
                or not matches(r'[0-9][0-9A-Za-z.+:~\-]*', row.get('cliVersion'))):
            raise EvidenceError('Lifecycle baseline provenance, provinces or actual CLI version is incomplete')
        assets = row.get('assets', [])
        if (not isinstance(assets, list) or any(not isinstance(asset, dict)
                or not isinstance(asset.get('name'), str) for asset in assets)
                or len({asset.get('name') for asset in assets}) != len(assets)):
            raise EvidenceError('Lifecycle package asset inventory is ambiguous')
        for prefix in PACKAGE_PREFIXES:
            if sum(asset.get('name', '').startswith(prefix) and asset['name'].endswith('.deb') for asset in assets) != 1:
                raise EvidenceError('Lifecycle baseline lacks a required pinned package')
        for asset in assets:
            name = asset.get('name', '')
            expected = 'https://github.com/carlos-emr/carlos/releases/download/' + row['tag'] + '/' + name
            if (not matches(r'[A-Za-z0-9.+~_-]+', name)
                    or not matches(r'[0-9a-f]{64}', asset.get('sha256'))
                    or type(asset.get('bytes')) is not int or asset['bytes'] <= 0
                    or asset.get('url') != expected):
                raise EvidenceError('Lifecycle asset lacks its exact published identity')
    return rows


def select_baseline(document, tag=None):
    rows = required_baselines(document)
    tag = tag or document['tag']
    selected = [row for row in rows if row['tag'] == tag]
    if len(selected) != 1:
        raise EvidenceError('Requested lifecycle baseline is not in the reviewed pins')
    return selected[0]
