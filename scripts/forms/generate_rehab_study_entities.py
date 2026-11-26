#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Generate the JPA entities of the 2004 rehabilitation-study questionnaire forms.

The eighteen questionnaire tables (issue #5693) share one header -- ID, demographic_no,
provider_no, formCreated, formEdited, studyID -- followed by hundreds of answer columns.
Each entity maps every answer column of its unchanged table from the Flyway baseline,
together with a static column table the Properties mapper iterates without reflection.

    python3 scripts/forms/generate_rehab_study_entities.py           # rewrite the sources
    python3 scripts/forms/generate_rehab_study_entities.py --check   # fail on any drift

The generated classes are never edited by hand. RehabStudyEntitySchemaContractUnitTest
re-reads the same baseline in the build and fails when an entity and its table disagree;
scripts/ci/test_rehab_study_entity_generator.py fails when a committed entity is not this
script's current output.
"""

from __future__ import annotations

import argparse
import keyword
import re
import sys
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "database/mysql/migration/common/V1__baseline_schema.sql"
PACKAGE = "io.github.carlos_emr.carlos.form.model.rehab"
TARGET = ROOT / "src/main/java" / PACKAGE.replace(".", "/")
SINCE = "2026-11-26"

# Table -> (entity class, questionnaire title used in the class Javadoc).
TABLES = {
    "form2MinWalk": ("Form2MinWalk", "Two-Minute Walk and balance"),
    "formCESD": ("FormCESD", "CES-D depression scale"),
    "formCaregiver": ("FormCaregiver", "Caregiver"),
    "formCostQuestionnaire": ("FormCostQuestionnaire", "Cost Questionnaire"),
    "formFalls": ("FormFalls", "Falls"),
    "formGripStrength": ("FormGripStrength", "Grip Strength"),
    "formHomeFalls": ("FormHomeFalls", "Home Falls"),
    "formIntakeInfo": ("FormIntakeInfo", "Intake Information"),
    "formInternetAccess": ("FormInternetAccess", "Internet Access"),
    "formLateLifeFDIDisability": ("FormLateLifeFDIDisability", "Late-Life FDI Disability"),
    "formLateLifeFDIFunction": ("FormLateLifeFDIFunction", "Late-Life FDI Function"),
    "formSF36": ("FormSF36", "SF-36 Health Survey"),
    "formSF36Caregiver": ("FormSF36Caregiver", "SF-36 Health Survey (caregiver)"),
    "formSatisfactionScale": ("FormSatisfactionScale", "Satisfaction Scale"),
    "formSelfAdministered": ("FormSelfAdministered", "Self-Administered"),
    "formSelfEfficacy": ("FormSelfEfficacy", "Self-Efficacy"),
    "formSelfManagement": ("FormSelfManagement", "Self-Management"),
    "formTreatmentPref": ("FormTreatmentPref", "Treatment Preference"),
}

# The shared header mapped by RehabStudyAssessment, with the physical definition it requires.
HEADER = {
    "ID": "int(10) NOT NULL AUTO_INCREMENT",
    "demographic_no": "int(10) NOT NULL",
    "provider_no": "int(10) DEFAULT NULL",
    "formCreated": "date DEFAULT NULL",
    "formEdited": "timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()",
    "studyID": "varchar(20) NOT NULL DEFAULT 'N/A'",
}
# formCaregiver gives demographic_no a zero default; the mapping is the same.
HEADER_ALTERNATIVES = {"demographic_no": {"int(10) NOT NULL DEFAULT 0"}}

# Physical type -> (column kind, Java type). Integer and floating-point answers stay text:
# the legacy form bound them as strings and the packaged server's empty sql_mode converts them.
TEXT_TYPE = re.compile(r"^(?:varchar|char)\(\d+\)$|^int\(\d+\)$|^double$")
COLUMN_LINE = re.compile(r"^\s*`([^`]+)`\s+(\S+)(.*?),?\s*$")


def parse_tables(sql: str) -> dict[str, list[tuple[str, str, str]]]:
    """Returns each listed table's (column, type, remainder) definitions in physical order."""
    tables = {}
    for table in TABLES:
        match = re.search(r"CREATE TABLE `" + re.escape(table) + r"` \((.*?)\n\)", sql, re.S)
        if match is None:
            raise SystemExit(f"{table}: CREATE TABLE not found in {BASELINE.relative_to(ROOT)}")
        columns = []
        for line in match.group(1).splitlines():
            if not line.strip().startswith("`"):
                continue
            column = COLUMN_LINE.match(line)
            if column is None:
                raise SystemExit(f"{table}: unreadable column definition {line.strip()!r}")
            name, physical, rest = column.groups()
            columns.append((name, physical.lower(), rest.strip()))
        tables[table] = columns
    return tables


def field_name(column: str) -> str:
    """Lower-camel Java field for a column; the column name itself stays in @Column."""
    candidate = column[0].lower() + column[1:]
    if not re.fullmatch(r"[a-z][A-Za-z0-9]*", candidate) or keyword.iskeyword(candidate):
        raise SystemExit(f"column {column!r} needs an explicit Java field name")
    return candidate


def body_columns(table: str, columns: list[tuple[str, str, str]]):
    header = [c for c in columns if c[0] in HEADER]
    names = [c[0] for c in header]
    if sorted(names) != sorted(HEADER):
        raise SystemExit(f"{table}: header columns {names} differ from {sorted(HEADER)}")
    for name, physical, rest in header:
        definition = f"{physical} {rest}".strip()
        if definition.lower() != HEADER[name].lower() and definition not in HEADER_ALTERNATIVES.get(name, ()):
            raise SystemExit(f"{table}.{name}: header definition {definition!r} is not mapped")
    body = []
    seen = set()
    for name, physical, rest in columns:
        if name in HEADER:
            continue
        if rest.upper() != "DEFAULT NULL":
            raise SystemExit(f"{table}.{name}: only nullable answer columns are mapped ({rest!r})")
        if physical == "tinyint(1)":
            kind, java = "Checkbox", "Byte"
        elif physical == "date":
            kind, java = "Day", "Date"
        elif TEXT_TYPE.match(physical):
            kind, java = "Text", "String"
        else:
            raise SystemExit(f"{table}.{name}: unmapped physical type {physical!r}")
        field = field_name(name)
        if field.lower() in seen:
            raise SystemExit(f"{table}.{name}: duplicate field {field}")
        seen.add(field.lower())
        body.append((name, physical, kind, java, field))
    return body


def render(table: str, columns: list[tuple[str, str, str]]) -> str:
    entity, title = TABLES[table]
    body = body_columns(table, columns)
    kinds = sorted({kind for _, _, kind, _, _ in body})
    uses_date = any(kind == "Day" for _, _, kind, _, _ in body)
    imports = [f"{PACKAGE}.RehabStudyColumn.{kind}" for kind in kinds]
    imports += ["jakarta.persistence.Column", "jakarta.persistence.Entity", "jakarta.persistence.Table"]
    if uses_date:
        imports += ["jakarta.persistence.Temporal", "jakarta.persistence.TemporalType", "java.util.Date"]
    imports.append("java.util.List")
    lines = [
        "/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */",
        f"package {PACKAGE};",
        "",
        *[f"import {name};" for name in sorted(imports)],
        "",
        "/**",
        *[" * " + line for line in textwrap.wrap(
            f"Every column of the unchanged {{@code {table}}} table: the {title} questionnaire"
            " of the 2004 rehabilitation study.", 92)],
        " *",
        " * <p>Generated by {@code scripts/forms/generate_rehab_study_entities.py} from the Flyway",
        " * baseline; regenerate rather than edit. {@code RehabStudyEntitySchemaContractUnitTest}",
        " * fails the build when this mapping and the baseline table disagree.</p>",
        " *",
        f" * @since {SINCE}",
        " */",
        "@Entity",
        f'@Table(name = "{table}")',
        f"public class {entity} extends RehabStudyAssessment {{",
        "    private static final long serialVersionUID = 1L;",
        "",
        f"    private static final List<RehabStudyColumn<{entity}>> COLUMNS = List.of(",
    ]
    entries = []
    for name, _, kind, _, field in body:
        reader = f'            new {kind}<>("{name}", form -> form.{field},'
        writer = f"(form, value) -> form.{field} = value)"
        single = f"{reader} {writer}"
        entries.append(single if len(single) <= 120 else f"{reader}\n                    {writer}")
    lines.append(",\n".join(entries) + ");")
    lines.append("")
    for name, physical, kind, java, field in body:
        if kind == "Day":
            lines.append("    @Temporal(TemporalType.DATE)")
        lines.append(f'    @Column(name = "{name}", columnDefinition = "{physical}")')
        lines.append(f"    private {java} {field};")
    lines += [
        "",
        "    /** The answer columns in physical order; the shared header is mapped by the superclass. */",
        f"    public static List<RehabStudyColumn<{entity}>> columns() {{",
        "        return COLUMNS;",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="report drift without writing")
    arguments = parser.parse_args()
    tables = parse_tables(BASELINE.read_text(encoding="utf-8"))
    drift = []
    for table, columns in tables.items():
        path = TARGET / f"{TABLES[table][0]}.java"
        source = render(table, columns)
        if arguments.check:
            if not path.exists() or path.read_text(encoding="utf-8") != source:
                drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
    if drift:
        print("Regenerate with scripts/forms/generate_rehab_study_entities.py:", *drift, sep="\n  ")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
