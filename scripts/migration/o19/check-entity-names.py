#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Audit O19 -> CARLOS column names against the ENTITY MODELS, not the DDL.

`generate_manifests.py` matches columns by name, case-folded, and refuses
to emit a manifest while any table has both an unmatched O19 column and an
unwritten CARLOS column -- the signature of a rename it cannot see. That
catches the case where a rename leaves a hole on each side.

It cannot catch a rename where BOTH sides happen to have a column of the
matching name (a swap), and it says nothing about intent. The entity
models can: O19 and CARLOS both annotate the same JPA field with
`@Column(name = ...)`, so a field whose column name differs between the
two trees is a rename stated by the code itself.

This is an audit tool, not part of the build: it needs an OSCAR 19
checkout, which CI does not have, and the generator's own refusal is what
gates a manifest. Run it when curating renames, or when a schema diff
looks surprising.

    python3 scripts/migration/o19/check-entity-names.py \\
        --oscar-src /path/to/oscar [--carlos-src src/main/java]

Exit status: 0 clean, 1 mismatches found, 2 bad arguments.
"""

import argparse
import os
import re
import sys

TABLE_RE = re.compile(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"')
COLUMN_RE = re.compile(r'@Column\s*\(([^)]*)\)', re.S)
NAME_RE = re.compile(r'name\s*=\s*"([^"]+)"')
#: A field declaration immediately after the annotation. The access
#: modifier is OPTIONAL: JPA is perfectly happy with a package-private
#: field, and the composite-id classes here use them
#: (CompositeFormBCAR2020TextId and friends declare `Integer formId;`
#: with no modifier at all). Requiring one made those fall through to
#: GETTER_RE, which then matched the first getter in the window -- a
#: DIFFERENT property -- so three mappings per class collapsed into one
#: wrong one, silently, in a tool whose whole job is to notice a name
#: that does not match.
#: The lookahead keeps a STATEMENT from reading as a declaration.
#: `return providerNo;` inside an annotated getter has exactly the shape
#: `<type> <name>;`, and the search is not anchored -- so on a
#: getter-annotated entity FIELD_RE reached past the getter and named a
#: field belonging to a DIFFERENT property (FormRourke2009's
#: @Column(name = "provider_no") came out as `p2_fallsOkConcerns`).
FIELD_RE = re.compile(
    r'(?:(?:private|protected|public|static|final|transient|volatile)\s+)*'
    r'(?!(?:return|throw|new|case|else|do|try|assert|break|continue|yield)'
    r'\b)'
    r'(?:[A-Za-z_$][\w<>,\[\]\.]*(?:\s*<[^;=]*>)?)\s+(\w+)\s*[;=]')
#: JPA reads annotations from FIELDS or from GETTERS, per entity, and an
#: entity that annotates its getters has no annotated field for FIELD_RE
#: to find. Dropping those would compare one side of a rename against
#: nothing and call it clean, which is the failure this tool exists to
#: prevent.
GETTER_RE = re.compile(
    r'(?:public|protected)\s+[\w<>,\[\]\.\s]+?\s+'
    r'(?:get|is)([A-Z]\w*)\s*\(\s*\)')

#: Hibernate quotes reserved words in @Column(name = "`value`"); the DDL
#: column is unquoted, and the manifest is built from the DDL.
QUOTES = '`"[]'


def parse_entity(path):
    """(class, table, {java field: db column}) for one .java file, or None
    when it declares no mapped columns."""
    # An audit that cannot read a file must not call the result clean:
    # every fail-open path here turns "I did not look" into "nothing is
    # wrong", which is worse than having no tool at all.
    with open(path, encoding="utf-8", errors="replace") as fh:
        src = fh.read()
    columns = {}
    for match in COLUMN_RE.finditer(src):
        # the field declaration follows the annotation; 400 chars is well
        # past any javadoc or further annotations between them
        window = src[match.end():match.end() + 400]
        field = FIELD_RE.search(window)
        getter = GETTER_RE.search(window)
        # WHICHEVER COMES FIRST is the member this annotation is on. The
        # window runs 400 characters past the annotation and so reaches
        # into the next member; preferring the field unconditionally let
        # a later declaration outrank the getter sitting right here.
        if field and (getter is None or field.start() < getter.start()):
            prop = field.group(1)
        else:
            if not getter:
                continue
            # getFoo() -> foo, the property name JPA derives through
            # java.beans.Introspector.decapitalize -- which leaves a name
            # whose first TWO characters are upper case alone, so
            # getURL() is the property `URL`, not `uRL`
            prop = getter.group(1)
            if not (len(prop) > 1 and prop[1].isupper()):
                prop = prop[0].lower() + prop[1:]
        name = NAME_RE.search(match.group(1))
        # `@Column` with no name= maps to the field or property name
        # under JPA's default strategy. Skipping those would compare one
        # side of a rename against nothing and report it clean, so
        # resolve the implicit name rather than dropping the member.
        columns[prop] = (name.group(1).strip(QUOTES) if name else prop)
    if not columns:
        return None
    table = TABLE_RE.search(src)
    cls = os.path.basename(path)[:-len(".java")]
    return cls, (table.group(1) if table else cls), columns


ENTITY_RE = re.compile(r'@Entity\b')


def scan(root):
    """({class: (table, {field: column})}, {unmapped class names}) for
    every entity under `root`.

    Keyed by CLASS because the packages differ between the two trees
    (org.oscarehr.* vs io.github.carlos_emr.carlos.*) while the class
    names are what survived the migration.

    The second element is the entities this tool CANNOT compare: an
    entity that maps every field implicitly has no `@Column` for the
    parser to read, so it is dropped from both sides. Returned rather
    than discarded because a clean verdict over a silently narrowed
    population is the failure this tool exists to prevent -- see the
    coverage line `main` prints."""
    def unreadable(exc):
        # os.walk swallows a directory it cannot open, so a permission
        # error deep in the tree would silently shrink the audit
        raise SystemExit(
            "cannot walk {0}: {1} -- refusing to report a clean audit "
            "over a tree it could not read".format(
                getattr(exc, "filename", root), exc))

    out = {}
    unmapped = set()
    for dirpath, _dirs, files in os.walk(root, onerror=unreadable):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            try:
                parsed = parse_entity(path)
                if not parsed:
                    with open(path, encoding="utf-8",
                              errors="replace") as fh:
                        if ENTITY_RE.search(fh.read()):
                            unmapped.add(name[:-len(".java")])
            except OSError as exc:
                raise SystemExit(
                    "cannot read {0}: {1} -- refusing to report a clean "
                    "audit over sources it could not open".format(path, exc))
            if parsed:
                out.setdefault(parsed[0], parsed[1:])
    return out, unmapped


def mismatches(o19, carlos):
    """Every place the two trees give one entity field two different
    database names, table names included."""
    found = []
    for cls in sorted(set(o19) & set(carlos)):
        (o19_table, o19_cols) = o19[cls]
        (carlos_table, carlos_cols) = carlos[cls]
        if o19_table.lower() != carlos_table.lower():
            found.append(("table", cls, o19_table, carlos_table))
        for field in sorted(set(o19_cols) & set(carlos_cols)):
            if o19_cols[field].lower() != carlos_cols[field].lower():
                found.append(("column", "{0}.{1}".format(cls, field),
                              o19_cols[field], carlos_cols[field]))
    return found


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--oscar-src", required=True,
                    help="path to an oscaremr/oscar (OSCAR 19) checkout")
    ap.add_argument("--carlos-src", default="src/main/java",
                    help="CARLOS java root (default: src/main/java)")
    args = ap.parse_args(argv)
    for path in (args.oscar_src, args.carlos_src):
        if not os.path.isdir(path):
            return ap.error("not a directory: {0}".format(path))

    o19_java = os.path.join(args.oscar_src, "src", "main", "java")
    if not os.path.isdir(o19_java):
        return ap.error(
            "{0} has no src/main/java -- pass the root of an OSCAR 19 "
            "checkout, not a subdirectory".format(args.oscar_src))
    o19, o19_unmapped = scan(o19_java)
    carlos, carlos_unmapped = scan(args.carlos_src)
    # zero entities on either side means the scan found nothing to compare,
    # which is a broken invocation rather than a clean result
    for label, found in (("O19", o19), ("CARLOS", carlos)):
        if not found:
            return ap.error(
                "no JPA-mapped entities found on the {0} side -- the audit "
                "would report clean without comparing anything".format(label))
    shared = set(o19) & set(carlos)
    print("mapped entities: O19 {0}, CARLOS {1}, shared {2}".format(
        len(o19), len(carlos), len(shared)))
    # What the verdict below does NOT cover, stated rather than implied.
    # An @Entity that maps every field implicitly carries no @Column for
    # the parser to read, so it is invisible to the comparison -- and a
    # clean line over a silently narrowed population is exactly the
    # false comfort this tool exists to remove.
    blind = (o19_unmapped | carlos_unmapped) - shared
    fields = sum(len(set(o19[c][1]) & set(carlos[c][1])) for c in shared)
    print("compared {0} field pair(s) across the shared entities; NOT "
          "compared: {1} entity/entities that declare no @Column (every "
          "field mapped implicitly), and @JoinColumn and .hbm.xml "
          "mappings, which this tool does not read".format(
              fields, len(blind)))
    found = mismatches(o19, carlos)
    if not found:
        print("no name mismatches: every shared entity field maps to the "
              "same database column on both sides")
        return 0
    print("\n{0} name mismatch(es) -- each is a rename the DDL comparison "
          "cannot see:".format(len(found)))
    for kind, where, a, b in found:
        print("  {0:<7} {1:<44} O19={2:<26} CARLOS={3}".format(
            kind, where, a, b))
    return 1


if __name__ == "__main__":
    sys.exit(main())
