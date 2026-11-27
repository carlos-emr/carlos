#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Generate the JPA entities of the 2004 rehabilitation-study questionnaire forms.

The eighteen questionnaire tables (issue #5693) share one header -- ID, demographic_no,
provider_no, formCreated, formEdited, studyID -- followed by hundreds of answer columns.
Each entity maps every answer column of its unchanged table from the Flyway baseline,
together with a static column table the Properties mapper iterates without reflection.
Tables whose answer columns are the same by design (SHARED_ANSWERS) map them once, in a
generated @MappedSuperclass their entities extend.

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

# Mapped superclass -> (tables, title). The caregiver SF-36 is the same survey asked of a
# caregiver, so its table repeats every formSF36 answer column; one superclass maps them, and
# the generator refuses the group if the tables ever differ.
SHARED_ANSWERS = {
    "SF36Answers": (("formSF36", "formSF36Caregiver"), "SF-36 Health Survey"),
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
# the legacy form bound them as strings and the server converts nonblank values.
TEXT_TYPE = re.compile(r"^(?:varchar|char)\(\d+\)$|^int\(\d+\)$|^double$")
COLUMN_LINE = re.compile(r"^\s*`([^`]+)`\s+(\S+)(.*?),?\s*$")


def parse_tables(sql: str) -> dict[str, list[tuple[str, str, str]]]:
    """Returns each listed table's (column, type, remainder) definitions in physical order."""
    tables = {}
    for table in TABLES:
        match = re.search(r"CREATE TABLE `" + re.escape(table) + r"` \((.*?)\n\)", sql, re.DOTALL)
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


HEADER_NOTE = [
    " * <p>Generated by {@code scripts/forms/generate_rehab_study_entities.py} from the Flyway",
    " * baseline; regenerate rather than edit. {@code RehabStudyEntitySchemaContractUnitTest}",
    " * fails the build when this mapping and the baseline table disagree.</p>",
    " *",
    f" * @since {SINCE}",
    " */",
]


def wrapped(text: str) -> list[str]:
    return [" * " + line for line in textwrap.wrap(text, 92)]


def opening(imports: list[str]) -> list[str]:
    return [
        "/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */",
        f"package {PACKAGE};",
        "",
        *[f"import {name};" for name in sorted(imports)],
        "",
        "/**",
    ]


def answer_imports(body) -> list[str]:
    kinds = sorted({kind for _, _, kind, _, _ in body})
    imports = [f"{PACKAGE}.RehabStudyColumn.{kind}" for kind in kinds] + ["jakarta.persistence.Column"]
    if "Day" in kinds:
        imports += ["jakarta.persistence.Temporal", "jakarta.persistence.TemporalType", "java.util.Date"]
    return imports


def answer_members(owner: str, body) -> list[str]:
    """The column table and the mapped answer fields of the class that declares them."""
    lines = [f"    private static final List<RehabStudyColumn<{owner}>> COLUMNS = List.of("]
    entries = []
    for name, physical, kind, _, field in body:
        is_numeric = physical.startswith("int(") or physical == "double"
        constructor = "Text.forNumericColumn" if is_numeric else f"new {kind}<>"
        reader = f'            {constructor}("{name}", form -> form.{field},'
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
    return lines


def columns_accessor(entity: str, expression: str) -> list[str]:
    return [
        "",
        "    /** The answer columns in physical order; the shared header is mapped by the superclass. */",
        f"    public static List<RehabStudyColumn<{entity}>> columns() {{",
        f"        return {expression};",
        "    }",
        "}",
        "",
    ]


def render(table: str, columns: list[tuple[str, str, str]]) -> str:
    """An entity that maps its own table's answer columns."""
    entity, title = TABLES[table]
    body = body_columns(table, columns)
    imports = answer_imports(body) + ["jakarta.persistence.Entity", "jakarta.persistence.Table", "java.util.List"]
    lines = opening(imports) + wrapped(
        f"Every column of the unchanged {{@code {table}}} table: the {title} questionnaire"
        " of the 2004 rehabilitation study.") + [" *"] + HEADER_NOTE + [
        "@Entity",
        f'@Table(name = "{table}")',
        f"public class {entity} extends RehabStudyAssessment {{",
        "    private static final long serialVersionUID = 1L;",
        "",
    ]
    return "\n".join(lines + answer_members(entity, body) + columns_accessor(entity, "COLUMNS"))


def render_shared(superclass: str, tables: tuple[str, ...], title: str, body) -> str:
    """The mapped superclass of the answer columns that several tables repeat."""
    named = " and ".join(f"{{@code {table}}}" for table in tables)
    imports = answer_imports(body) + ["jakarta.persistence.MappedSuperclass", "java.util.List"]
    lines = opening(imports) + wrapped(
        f"Every answer column of the {named} tables, which have the same columns: the {title}"
        " questionnaire of the 2004 rehabilitation study. Each table's entity extends this class,"
        " so the answers are mapped once.") + [" *"] + HEADER_NOTE + [
        "@MappedSuperclass",
        f"public abstract class {superclass} extends RehabStudyAssessment {{",
        "    private static final long serialVersionUID = 1L;",
        "",
    ]
    lines += answer_members(superclass, body)
    lines += [
        "",
        "    /** The answer columns in physical order, for the entities that extend this class. */",
        f"    static List<RehabStudyColumn<{superclass}>> answerColumns() {{",
        "        return COLUMNS;",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def render_extending(table: str, superclass: str) -> str:
    """An entity whose answer columns are those of a shared mapped superclass."""
    entity, title = TABLES[table]
    imports = ["jakarta.persistence.Entity", "jakarta.persistence.Table", "java.util.List"]
    lines = opening(imports) + wrapped(
        f"The unchanged {{@code {table}}} table: the {title} questionnaire of the 2004"
        f" rehabilitation study. Its answer columns are those of {{@link {superclass}}}.") + [" *"] + HEADER_NOTE + [
        "@Entity",
        f'@Table(name = "{table}")',
        f"public class {entity} extends {superclass} {{",
        "    private static final long serialVersionUID = 1L;",
        "",
        f"    private static final List<RehabStudyColumn<{entity}>> COLUMNS = RehabStudyColumn.narrow(answerColumns());",
    ]
    return "\n".join(lines + columns_accessor(entity, "COLUMNS"))


def sources(tables: dict[str, list[tuple[str, str, str]]]) -> dict[str, str]:
    """Every generated source, keyed by file name."""
    shared_by_table = {}
    generated = {}
    for superclass, (members, title) in SHARED_ANSWERS.items():
        bodies = [body_columns(table, tables[table]) for table in members]
        if any(body != bodies[0] for body in bodies[1:]):
            raise SystemExit(f"{superclass}: the answer columns of {', '.join(members)} differ")
        generated[f"{superclass}.java"] = render_shared(superclass, members, title, bodies[0])
        shared_by_table.update({table: superclass for table in members})
    for table, columns in tables.items():
        entity = TABLES[table][0]
        if table in shared_by_table:
            generated[f"{entity}.java"] = render_extending(table, shared_by_table[table])
        else:
            generated[f"{entity}.java"] = render(table, columns)
    return generated


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="report drift without writing")
    arguments = parser.parse_args()
    tables = parse_tables(BASELINE.read_text(encoding="utf-8"))
    drift = []
    for name, source in sources(tables).items():
        path = TARGET / name
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
