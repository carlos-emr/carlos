# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Check the exact manifest headers consumed by the shipped carlos-ctl."""
import argparse
import json
from pathlib import Path

CONTRACTS = {
    'o19map_schema.json': ('o19map-schema', 3),
    'o19map_props.json': ('o19map-props', 1),
    'o19_preflight.json': ('o19-preflight', 1),
    'stock-eform-assets.json': ('o19-stock-eform-assets', 1),
}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate manifest key')
        result[key] = value
    return result


def verify_manifest(path, name):
    if name not in CONTRACTS:
        return {'file': name, 'problem': 'unregistered-manifest'}
    kind, version = CONTRACTS[name]
    path = Path(path)
    try:
        if not path.is_file() or path.stat().st_size > 32 * 1024 * 1024:
            raise ValueError('Manifest is missing or oversized')
        document = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_object)
        if (not isinstance(document, dict) or document.get('kind') != kind
                or type(document.get('format')) is not int or document['format'] != version):
            raise ValueError('Manifest header differs from the shipped contract')
    except (OSError, ValueError):
        return {'file': name, 'expectedKind': kind, 'expectedFormat': version}
    return None


def verify(directory):
    problems = []
    formats = {}
    for name, (_kind, version) in CONTRACTS.items():
        problem = verify_manifest(Path(directory) / name, name)
        if problem is not None:
            problems.append(problem)
        else:
            formats[name] = version
    return {'ready': not problems, 'formats': formats, 'problems': problems}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    result = verify(parser.parse_args().directory)
    print(json.dumps(result, sort_keys=True))
    raise SystemExit(0 if result['ready'] else 1)
