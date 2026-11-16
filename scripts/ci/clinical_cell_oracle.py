# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Real MariaDB regression oracle for preservation fingerprints; creates no data."""
import json

from installed_package_state import cell_encoding
from junit_evidence import EvidenceError


def verify(query):
    cases = [
        ('FLOAT', '1.1', '1.2'),
        ('FLOAT', '1.00000011920928955078125', '1.0000002384185791015625'),
        ('FLOAT', '1e-40', '2e-40'),
        ('DOUBLE', '1.0000000000000002', '1.0000000000000004'),
        ('DOUBLE', '1e-310', '2e-310'),
        ('DECIMAL(65,30)', '12345678901234567890.000000000000000000000000000001',
                           '12345678901234567890.000000000000000000000000000002'),
        ('BINARY', "X'00ff'", "X'00fe'"),
        ('BINARY', 'NULL', "X''"),
    ]
    for column_type, before, after in cases:
        def encoded(value):
            rows = query('SELECT JSON_ARRAY(' + cell_encoding('value', column_type) + ') FROM '
                '(SELECT CAST(' + value + ' AS ' + column_type + ') AS value) AS fixture')
            if len(rows) != 1:
                raise EvidenceError('Clinical cell fingerprint oracle has incomplete results')
            return json.loads(rows[0])
        if encoded(before) == encoded(after):
            raise EvidenceError('Clinical cell fingerprint missed a stored value change: ' + column_type)
    # Simulate a latin1 -> utf8mb4 migration without creating persistent data.
    def text_cell(expression, kind):
        rows = query('SELECT JSON_ARRAY(' + cell_encoding('value', kind)
                     + ') FROM (SELECT ' + expression + ' AS value) AS fixture')
        if len(rows) != 1:
            raise EvidenceError('Character migration oracle has incomplete results')
        return json.loads(rows[0])
    latin1 = "CONVERT(X'636166e9' USING latin1)"
    utf8 = "CONVERT(X'636166c3a9' USING utf8mb4)"
    if text_cell(latin1, 'varchar(40)') != text_cell(utf8, 'text'):
        raise EvidenceError('Clinical text changed fingerprint during character-set migration')
    if text_cell(latin1, 'text') == text_cell("CONVERT(X'63616665' USING utf8mb4)", 'text'):
        raise EvidenceError('Clinical text fingerprint concealed changed characters')
    if text_cell("X'636166e9'", 'blob') == text_cell("X'636166c3a9'", 'varbinary(40)'):
        raise EvidenceError('Clinical binary fingerprint concealed changed bytes')
    return len(cases) + 3
