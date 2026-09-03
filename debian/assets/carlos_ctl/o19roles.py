# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""OSCAR 19 import — post-copy reconciliation of roles, privileges and the
rows CARLOS code requires (M8; plan §4.5 "Roles, privileges and
CARLOS-required rows").

Why this step exists. CARLOS's privilege check is exact-match and
deny-by-default: `hasPrivilege` reads `secUserRole` rows with
`activeyn = 1`, joins them to `secObjPrivilege` by role NAME, and knows no
parent fallback (`_admin.fax` does not inherit `_admin`). The table loop
copies the clinic's roles and assignments id-intact (`secRole`,
`secUserRole` are replace_seed) and MERGES the clinic's grants under
CARLOS's seeded matrix (CARLOS wins on a (role, object) collision, clinic
rows append). What the merge alone cannot give the clinic:

* the break-glass admin needs ACTIVE roles, a program membership and a
  facility link (case management and login fail closed without them);
* the webapp's first start creates program `OSCAR` with a membership for
  the seeded clinician — who no longer exists after the import (the
  `program_provider.provider_no` FK would then fail at startup);
* every active provider needs a `program_provider` row, or encounter
  notes and issues are unreadable (`CaseManagementManagerImpl` returns
  false with no membership);
* a clinic-custom role holds only what the clinic granted in O19; the
  CARLOS-era objects (`_fax`, `_email`, `_rx.editPharmacy`, ...) are
  backfilled from the stock role it resembles most;
* `secUserRole.activeyn` NULL grants nothing in CARLOS — rows of active
  accounts are activated (user decision) and every change is reported;
* data normalisation CARLOS ships only as post-baseline scripts
  (prevention type codes, the Rich Text Letter eForm) never reaches
  imported rows through Flyway, which ran on the empty deploy first.

Every write is idempotent (INSERT ... WHERE NOT EXISTS / INSERT IGNORE /
guarded UPDATE) and every sub-step is ledger-marked, so `--resume` re-runs
nothing that completed. Statement builders are pure (schema names in, SQL
out) for the unit tests; `run_roles` wires them to the ETL ledger.

Report hygiene: role and object names are not PHI and go to report.txt;
provider numbers and user names are PHI-correlating and go to the root-only
files written with `o19import.write_private`.
"""

import os
import re
from typing import Callable, Dict, List, Optional, Sequence, Set, Tuple

from . import o19map_schema
from .o19etl import _sql_str, appended_row_count_sql

# --- constants --------------------------------------------------------------

#: CARLOS seed tables snapshotted into o19_archive BEFORE the table loop —
#: the pristine target (P0-verified) is the seed, and the snapshot is what
#: the diff, the template choice and the role append read.
SNAPSHOT_TABLES = ("secObjPrivilege", "secObjectName", "secRole",
                   "access_type")

#: role names CARLOS code hard-codes (startup dereferences `doctor`;
#: `hasAdminRole` compares against `admin`)
GUARANTEED_ROLES = (("doctor", "doctor"), ("admin", "admin"))

#: roleUserGroup values that are not roles: patient-scoped `_all`, document
#: queues, bare provider numbers (per-provider overrides) and the seed's
#: `-1` pseudo-group
NON_ROLE_GROUP_RE = re.compile(r"^(-?\d+|_all|_queue\..*)$")

OSCAR_PROGRAM = "OSCAR"

RTL_TITLE_MARKER = "<title>Rich Text Letter</title>"
RTL_FORM_NAME = "Rich Text Letter"
#: the packaged scripts match `form_name = 'Rich Text Letter'` AND
#: `subject LIKE 'Rich Text Letter Generator%'`; a row whose subject a
#: clinic edited is out of their reach and is reported, not claimed fixed
RTL_SUBJECT_PREFIX = "Rich Text Letter Generator"
RTL_VERSION_MARKER = "RTL 2026.3.0"
#: public JSP routes the 2026-06-29 route fix rewrites; their presence in
#: form_html means that fix is still due whatever the version marker says
RTL_DEAD_ROUTES = ("../eform/attachEform.jsp",
                   "../eform/displayAttachedFiles.jsp")
#: packaged copies of database/mysql/updates scripts (debian/rules installs
#: them; the importer replays them because Flyway cannot see clinic rows)
DEFAULT_FIXUPS_DIR = "/usr/share/carlos-emr/schema/o19-fixups"
RTL_SEED_SCRIPT = "update-2012-07-12.sql"
RTL_ENABLE_SCRIPT = "update-2026-03-12-rtl-enable-direct.sql"
RTL_MODERNIZE_SCRIPT = "update-2026-03-22-rtl-2026.3.0-modernize.sql"
RTL_ROUTE_FIX_SCRIPT = "update-2026-06-29-rtl-attachment-route-fix.sql"
RTL_FIXUP_SCRIPTS = (RTL_ENABLE_SCRIPT, RTL_MODERNIZE_SCRIPT,
                     RTL_ROUTE_FIX_SCRIPT)

LEDGER_KEY = "roles"


def snapshot_table(table: str) -> str:
    return "carlos_seed_" + table


# --- pure statement builders ------------------------------------------------

def snapshot_statements(dst_schema: str, archive_schema: str) -> List[str]:
    """Copy the CARLOS seed of the role tables into o19_archive (DROP +
    CREATE ... AS SELECT, the archive idiom) before any clinic row lands."""
    out = []
    for table in SNAPSHOT_TABLES:
        snap = snapshot_table(table)
        out.append("DROP TABLE IF EXISTS `{0}`.`{1}`".format(archive_schema,
                                                             snap))
        out.append("CREATE TABLE `{0}`.`{1}` AS SELECT * FROM `{2}`.`{3}`"
                   .format(archive_schema, snap, dst_schema, table))
    return out


def startup_row_predicate(where: str, schema: str) -> str:
    """A STARTUP_CREATED_ROWS predicate bound to one schema (its subqueries
    carry a {schema} placeholder because the client runs without a default
    database)."""
    return where.replace("{schema}", "`{0}`".format(schema))


def startup_row_delete_statements(dst_schema: str) -> List[str]:
    """Remove the rows the webapp created on first start (the OSCAR
    program, the seeded clinician's membership, the default site) so the
    clinic's own rows — which reuse the same ids — copy without
    colliding. Manifest order: children before parents."""
    return ["DELETE FROM `{0}`.`{1}` WHERE {2}".format(
                dst_schema, table, startup_row_predicate(where, dst_schema))
            for table, where in o19map_schema.STARTUP_CREATED_ROWS]


def startup_row_count_sql(table: str, where: str, schema: str) -> str:
    return "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE {2}".format(
        schema, table, startup_row_predicate(where, schema))


def guaranteed_role_statements(dst_schema: str) -> List[str]:
    return ["INSERT INTO `{0}`.secRole (role_name, description) "
            "SELECT '{1}', '{2}' FROM DUAL WHERE NOT EXISTS "
            "(SELECT 1 FROM `{0}`.secRole WHERE role_name = '{1}')"
            .format(dst_schema, _sql_str(name), _sql_str(desc))
            for name, desc in GUARANTEED_ROLES]


def carlos_role_append_statement(dst_schema: str,
                                 archive_schema: str) -> str:
    """CARLOS-only roles (HRMAdmin, Site Manager, Partner Doctor) re-added
    by name with fresh ids, so their merged grants are not orphaned."""
    return ("INSERT INTO `{0}`.secRole (role_name, description) "
            "SELECT s.role_name, s.description FROM `{1}`.`{2}` s "
            "WHERE NOT EXISTS (SELECT 1 FROM `{0}`.secRole d "
            "WHERE d.role_name = s.role_name) ORDER BY s.role_no"
            .format(dst_schema, archive_schema, snapshot_table("secRole")))


def enabled_facility_count_sql(dst_schema: str) -> str:
    return ("SELECT COUNT(*) FROM `{0}`.Facility WHERE disabled = 0"
            .format(dst_schema))


def clinic_count_sql(dst_schema: str) -> str:
    return "SELECT COUNT(*) FROM `{0}`.clinic".format(dst_schema)


def provider_facility_statement(dst_schema: str) -> str:
    """Every active provider gets the first enabled facility unless it
    already belongs to one (login self-heals this only when it can)."""
    return ("INSERT INTO `{0}`.provider_facility (provider_no, facility_id) "
            "SELECT p.provider_no, (SELECT MIN(f.id) FROM `{0}`.Facility f "
            "WHERE f.disabled = 0) FROM `{0}`.provider p "
            "WHERE p.status = '1' AND NOT EXISTS (SELECT 1 FROM "
            "`{0}`.provider_facility pf WHERE pf.provider_no = p.provider_no)"
            .format(dst_schema))


def oscar_program_statement(dst_schema: str) -> str:
    """The program ContextStartupListener would create on first start
    (name OSCAR, type Service, active, maxAllowed 99999, first enabled
    facility). HAVING keeps the aggregate from inserting a NULL-facility
    row when the program already exists or no facility is enabled."""
    # every NOT NULL column without a default is supplied explicitly
    # (Program.java's field defaults), so the statement does not depend
    # on the executor's sql_mode
    return ("INSERT INTO `{0}`.program (facilityId, name, description, "
            "type, programStatus, maxAllowed, transgender, firstNation, "
            "alcohol, physicalHealth, mentalHealth, housing, exclusiveView, "
            "ageMin, ageMax) SELECT MIN(f.id), '{1}', '{1}', 'Service', "
            "'active', 99999, 0, 0, 0, 0, 0, 0, 'no', 0, 0 FROM "
            "`{0}`.Facility f WHERE f.disabled = 0 HAVING MIN(f.id) IS NOT "
            "NULL AND NOT EXISTS (SELECT 1 FROM `{0}`.program WHERE name = "
            "'{1}')".format(dst_schema, OSCAR_PROGRAM))


def membership_statements(dst_schema: str) -> List[str]:
    """program_provider rows for active providers that have NONE, in the
    OSCAR program, carrying the clinic's own role_no (program_provider.
    role_id references secRole.role_no and feeds the CAISI note access
    rules, so the clinic's default_role_access rows keep their meaning).

    First statement: providers with an active role get the role CARLOS
    would expect — `doctor` when they hold it, else their active role
    with the most privilege rows (the one that governs what they can do
    anyway), lowest role_no on a tie. Second: providers with no active
    role at all get the clinic role with the FEWEST privilege rows
    (least privilege until an administrator assigns a role); they are
    counted separately and listed in roles-details.txt."""
    grants = ("(SELECT COUNT(*) FROM `{0}`.secObjPrivilege g WHERE "
              "g.roleUserGroup = r.role_name)".format(dst_schema))
    active_role = ("(SELECT r.role_no FROM `{0}`.secUserRole ur JOIN "
                   "`{0}`.secRole r ON r.role_name = ur.role_name WHERE "
                   "ur.provider_no = pr.provider_no AND ur.activeyn = 1 "
                   "ORDER BY (r.role_name = 'doctor') DESC, {1} DESC, "
                   "r.role_no LIMIT 1)".format(dst_schema, grants))
    least_role = ("(SELECT r.role_no FROM `{0}`.secRole r ORDER BY {1} ASC, "
                  "r.role_no LIMIT 1)".format(dst_schema, grants))
    template = ("INSERT INTO `{0}`.program_provider (program_id, "
                "provider_no, role_id, team_id) SELECT p.id, pr.provider_no, "
                "{1}, NULL FROM `{0}`.provider pr JOIN `{0}`.program p "
                "ON p.name = '{2}' WHERE pr.status = '1' AND NOT EXISTS "
                "(SELECT 1 FROM `{0}`.program_provider pp WHERE "
                "pp.provider_no = pr.provider_no) AND {3} IS NOT NULL")
    return [
        template.format(dst_schema, active_role, OSCAR_PROGRAM, active_role),
        template.format(dst_schema, least_role, OSCAR_PROGRAM, least_role),
    ]


def fallback_membership_candidates_sql(dst_schema: str) -> str:
    """provider_no of active providers with no membership AND no active
    role — the ones the second membership statement gives the
    least-privileged role (listed privately, counted in the report)."""
    return ("SELECT pr.provider_no FROM `{0}`.provider pr WHERE pr.status = "
            "'1' AND NOT EXISTS (SELECT 1 FROM `{0}`.program_provider pp "
            "WHERE pp.provider_no = pr.provider_no) AND NOT EXISTS (SELECT 1 "
            "FROM `{0}`.secUserRole ur JOIN `{0}`.secRole r ON r.role_name = "
            "ur.role_name WHERE ur.provider_no = pr.provider_no AND "
            "ur.activeyn = 1) ORDER BY pr.provider_no".format(dst_schema))


def providers_without_membership_sql(dst_schema: str) -> str:
    return ("SELECT COUNT(*) FROM `{0}`.provider pr WHERE pr.status = '1' "
            "AND NOT EXISTS (SELECT 1 FROM `{0}`.program_provider pp "
            "WHERE pp.provider_no = pr.provider_no)".format(dst_schema))


def activeyn_candidates_sql(dst_schema: str) -> str:
    """provider_no of every NULL-activeyn role row whose account is live
    (provider status '1' with a security row) — the rows the import
    activates."""
    return ("SELECT DISTINCT ur.provider_no FROM `{0}`.secUserRole ur "
            "JOIN `{0}`.provider p ON p.provider_no = ur.provider_no "
            "JOIN `{0}`.security s ON s.provider_no = ur.provider_no "
            "WHERE ur.activeyn IS NULL AND p.status = '1' "
            "ORDER BY ur.provider_no".format(dst_schema))


def activeyn_update_statement(dst_schema: str) -> str:
    return ("UPDATE `{0}`.secUserRole ur JOIN `{0}`.provider p ON "
            "p.provider_no = ur.provider_no JOIN `{0}`.security s ON "
            "s.provider_no = ur.provider_no SET ur.activeyn = 1 "
            "WHERE ur.activeyn IS NULL AND p.status = '1'".format(dst_schema))


def activeyn_null_remaining_sql(dst_schema: str) -> str:
    return ("SELECT COUNT(*) FROM `{0}`.secUserRole WHERE activeyn IS NULL"
            .format(dst_schema))


# --- custom-role backfill (pure over fetched rows) -------------------------

def is_role_group(group: str) -> bool:
    """A roleUserGroup that names a role (not a provider number, `_all`
    or a document queue)."""
    return bool(group) and not NON_ROLE_GROUP_RE.match(group)


def carlos_era_objects(seed_rows: Sequence[Sequence[str]],
                       stage_rows: Sequence[Sequence[str]],
                       stage_objects: Sequence[str]) -> List[str]:
    """Objects the CARLOS seed grants that the clinic's O19 never knew:
    absent from its secObjectName AND from every one of its grants."""
    known = set(stage_objects) | {r[1] for r in stage_rows}
    return sorted({r[1] for r in seed_rows} - known)


def custom_roles(target_roles: Sequence[str],
                 stage_rows: Sequence[Sequence[str]],
                 stock_roles: Sequence[str]) -> List[str]:
    """Clinic roles that (a) exist in the target secRole, (b) hold at least
    one imported grant and (c) are not a CARLOS stock role name."""
    granted = {r[0] for r in stage_rows if is_role_group(r[0])}
    # secRole.role_name is UNIQUE under a case-insensitive collation and
    # Java's getRoleByName matches the same way: `Doctor` IS the stock role
    stock = {r.casefold() for r in stock_roles}
    return sorted(r for r in target_roles
                  if r in granted and r.casefold() not in stock)


def non_role_named_roles(target_roles: Sequence[str]) -> List[str]:
    """secRole rows whose name looks like a non-role group (all digits,
    `_all`, `_queue.*`): skipped by the backfill and reported, never
    silently ignored."""
    return sorted(r for r in target_roles if not is_role_group(r))


def role_pairs(rows: Sequence[Sequence[str]],
               role: str) -> Set[Tuple[str, str]]:
    return {(r[1], (r[2] or "").strip()) for r in rows if r[0] == role}


def jaccard(a: Set, b: Set) -> float:
    if not a and not b:
        return 0.0
    return len(a & b) / float(len(a | b))


def choose_template(custom: str, stage_rows: Sequence[Sequence[str]],
                    seed_rows: Sequence[Sequence[str]],
                    min_jaccard: float,
                    stock_roles: Optional[Sequence[str]] = None
                    ) -> Tuple[Optional[str], float]:
    """The stock role whose (object, privilege) set is closest to the
    custom role's imported grants; ties break alphabetically so the
    choice is deterministic. Below the floor: (None, best) — reported,
    not guessed. Candidates are the seed's groups that are stock ROLE
    names (given stock_roles): the seed's `-1`/provider pseudo-groups
    hold a handful of grants and would otherwise win small roles."""
    mine = role_pairs(stage_rows, custom)
    best_role, best = None, -1.0
    candidates = {r[0] for r in seed_rows if is_role_group(r[0])}
    if stock_roles is not None:
        candidates &= set(stock_roles)
    for role in sorted(candidates):
        score = jaccard(mine, role_pairs(seed_rows, role))
        if score > best:
            best_role, best = role, score
    if best_role is None or best < min_jaccard:
        return None, max(best, 0.0)
    return best_role, best


def backfill_statement(dst_schema: str, archive_schema: str, custom: str,
                       template: str, objects: Sequence[str]) -> str:
    """INSERT IGNORE the template's CARLOS-era grants onto the custom role
    (the prior CARLOS privilege-patch idiom); an imported row on the same
    (role, object) is never overwritten."""
    obj_list = ", ".join("'{0}'".format(_sql_str(o)) for o in objects)
    return ("INSERT IGNORE INTO `{0}`.secObjPrivilege (roleUserGroup, "
            "objectName, privilege, priority, provider_no) SELECT '{1}', "
            "objectName, privilege, priority, NULL FROM `{2}`.`{3}` WHERE "
            "roleUserGroup = '{4}' AND objectName IN ({5})".format(
                dst_schema, _sql_str(custom), archive_schema,
                snapshot_table("secObjPrivilege"), _sql_str(template),
                obj_list))


def parse_role_templates(values: Optional[Sequence[str]]) -> Dict[str, str]:
    """--role-template 'Custom Role=doctor' (repeatable) -> {custom: stock}.
    Malformed entries and two different templates for one role raise
    ValueError; an exact repeat is tolerated."""
    out: Dict[str, str] = {}
    for raw in values or ():
        if "=" not in raw:
            raise ValueError("--role-template expects CUSTOM=STOCK, got {0!r}"
                             .format(raw))
        custom, stock = (part.strip() for part in raw.split("=", 1))
        if not custom or not stock:
            raise ValueError("--role-template expects CUSTOM=STOCK, got {0!r}"
                             .format(raw))
        if custom in out and out[custom] != stock:
            raise ValueError("--role-template names {0!r} twice"
                             .format(custom))
        out[custom] = stock
    return out


def validate_role_templates(overrides: Dict[str, str],
                            customs: Sequence[str],
                            stock_roles: Sequence[str]) -> List[str]:
    """Problems with operator overrides (case-insensitive, like the
    role_name column's collation)."""
    problems = []
    custom_fold = {c.casefold() for c in customs}
    stock_fold = {r.casefold() for r in stock_roles}
    for custom, stock in sorted(overrides.items()):
        if custom.casefold() not in custom_fold:
            problems.append("--role-template {0!r}: not a clinic-custom role "
                            "with imported grants".format(custom))
        if stock.casefold() not in stock_fold:
            problems.append("--role-template {0!r}: {1!r} is not a CARLOS "
                            "stock role".format(custom, stock))
    return problems


def normalise_role_templates(overrides: Dict[str, str],
                             customs: Sequence[str],
                             stock_roles: Sequence[str]) -> Dict[str, str]:
    """Overrides re-keyed to the exact target/seed spellings (the INSERT
    must name the rows as the tables spell them). Validate first."""
    custom_by = {c.casefold(): c for c in customs}
    stock_by = {r.casefold(): r for r in stock_roles}
    return {custom_by[c.casefold()]: stock_by[t.casefold()]
            for c, t in overrides.items()
            if c.casefold() in custom_by and t.casefold() in stock_by}


def backfill_pending_count_sql(dst_schema: str, archive_schema: str,
                               custom: str, template: str,
                               objects: Sequence[str]) -> str:
    """How many rows backfill_statement WOULD add right now — taken before
    the write so the count survives a crash between write and ledger."""
    obj_list = ", ".join("'{0}'".format(_sql_str(o)) for o in objects)
    return ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE s.roleUserGroup = "
            "'{2}' AND s.objectName IN ({3}) AND NOT EXISTS (SELECT 1 FROM "
            "`{4}`.secObjPrivilege d WHERE d.roleUserGroup = '{5}' AND "
            "d.objectName = s.objectName)".format(
                archive_schema, snapshot_table("secObjPrivilege"),
                _sql_str(template), obj_list, dst_schema, _sql_str(custom)))


# --- privilege diff, property prune, prevention types, RTL -----------------

def privilege_diff_sql(src_schema: str, archive_schema: str) -> str:
    """Clinic grants the merge did not carry because CARLOS's seed holds
    the same (role, object) with a different privilege/priority."""
    return ("SELECT s.roleUserGroup, s.objectName, s.privilege, s.priority, "
            "d.privilege, d.priority FROM `{0}`.secObjPrivilege s JOIN "
            "`{1}`.`{2}` d ON d.roleUserGroup = s.roleUserGroup AND "
            "d.objectName = s.objectName WHERE NOT (s.privilege <=> "
            "d.privilege AND s.priority <=> d.priority) "
            "ORDER BY s.roleUserGroup, s.objectName".format(
                src_schema, archive_schema,
                snapshot_table("secObjPrivilege")))


def excluded_grants_count_sql(src_schema: str) -> Optional[str]:
    exclude = o19map_schema.TABLES.get("secObjPrivilege", {}).get(
        "merge_exclude")
    if not exclude:
        return None
    return ("SELECT COUNT(*) FROM `{0}`.secObjPrivilege s WHERE {1}"
            .format(src_schema, exclude))


def excluded_grants_sql(src_schema: str) -> Optional[str]:
    """The clinic grants the merge left behind (manifest MERGE_EXCLUDE),
    itemised for privilege-diff.txt."""
    exclude = o19map_schema.TABLES.get("secObjPrivilege", {}).get(
        "merge_exclude")
    if not exclude:
        return None
    return ("SELECT s.roleUserGroup, s.objectName, s.privilege FROM "
            "`{0}`.secObjPrivilege s WHERE {1} ORDER BY s.roleUserGroup, "
            "s.objectName".format(src_schema, exclude))


def stock_role_appends_sql(src_schema: str, archive_schema: str,
                           stock_roles: Sequence[str]) -> str:
    """Clinic grants on STOCK role names that the CARLOS seed does not hold
    at all: the merge appends them (clinic wins where CARLOS has no row),
    which widens a stock role beyond the seed — itemised for the review."""
    roles = ", ".join("'{0}'".format(_sql_str(r)) for r in stock_roles)
    exclude = o19map_schema.TABLES.get("secObjPrivilege", {}).get(
        "merge_exclude")
    sql = ("SELECT s.roleUserGroup, s.objectName, s.privilege, s.priority "
           "FROM `{0}`.secObjPrivilege s WHERE s.roleUserGroup IN ({1}) AND "
           "NOT EXISTS (SELECT 1 FROM `{2}`.`{3}` d WHERE d.roleUserGroup = "
           "s.roleUserGroup AND d.objectName = s.objectName)".format(
               src_schema, roles, archive_schema,
               snapshot_table("secObjPrivilege")))
    if exclude:
        sql += " AND NOT ({0})".format(exclude)
    return sql + " ORDER BY s.roleUserGroup, s.objectName"


def property_prune_statements(dst_schema: str, prefixes: Sequence[str]
                              ) -> List[Tuple[str, str, str]]:
    """(prefix, count-sql, delete-sql) per removed-module property prefix."""
    out = []
    for prefix in prefixes:
        like = "'{0}%'".format(_sql_str(prefix).replace("_", "\\_")
                               .replace("%", "\\%"))
        out.append((
            prefix,
            "SELECT COUNT(*) FROM `{0}`.property WHERE name LIKE {1}".format(
                dst_schema, like),
            "DELETE FROM `{0}`.property WHERE name LIKE {1}".format(
                dst_schema, like)))
    return out


def prevention_type_statements(dst_schema: str,
                               type_map: Dict[str, str]
                               ) -> List[Tuple[str, str, str, str]]:
    """(legacy, canonical, count-sql, update-sql) per mapping. The compare
    is BINARY: the column's collation is case-insensitive and legacy
    `dTaP` must not drag the valid pediatric code `DTaP` along into
    `Tdap`."""
    out = []
    for legacy in sorted(type_map):
        canonical = type_map[legacy]
        where = "BINARY prevention_type = '{0}'".format(_sql_str(legacy))
        out.append((
            legacy, canonical,
            "SELECT COUNT(*) FROM `{0}`.preventions WHERE {1}".format(
                dst_schema, where),
            "UPDATE `{0}`.preventions SET prevention_type = '{1}' WHERE {2}"
            .format(dst_schema, _sql_str(canonical), where)))
    return out


def unknown_prevention_types_sql(dst_schema: str,
                                 known: Sequence[str]) -> str:
    known_list = ", ".join("'{0}'".format(_sql_str(k)) for k in known)
    return ("SELECT prevention_type, COUNT(*) FROM `{0}`.preventions "
            "WHERE prevention_type NOT IN ({1}) GROUP BY prevention_type "
            "ORDER BY prevention_type".format(dst_schema, known_list))


def rtl_rows_sql(dst_schema: str) -> str:
    """Every eForm that IS the Rich Text Letter (by its HTML title; the
    2010 O19 seed named the row `letter`, the 2012 update `Rich Text
    Letter`): fid, form_name, status, subject, carries the 2026.3.0
    marker, still calls a dead attachment route."""
    dead = " OR ".join("form_html LIKE '%{0}%'".format(_sql_str(r))
                       for r in RTL_DEAD_ROUTES)
    return ("SELECT fid, form_name, status, subject, "
            "form_html LIKE '%{1}%', ({3}) FROM `{0}`.eform WHERE form_html "
            "LIKE '%{2}%' ORDER BY fid".format(
                dst_schema, RTL_VERSION_MARKER, RTL_TITLE_MARKER, dead))


def rtl_disable_statement(dst_schema: str, fid: str) -> str:
    return ("UPDATE `{0}`.eform SET status = 0 WHERE fid = {1}".format(
        dst_schema, int(fid)))


def is_rtl_canonical(row: Sequence[str]) -> bool:
    """A row the packaged scripts can address: named `Rich Text Letter`
    with a subject starting `Rich Text Letter Generator`."""
    return (row[1] == RTL_FORM_NAME
            and (row[3] or "").startswith(RTL_SUBJECT_PREFIX))


def fixup_scripts_needed(rows: Sequence[Sequence[str]]) -> List[str]:
    """The packaged scripts to run, in order, from the live rows: the v1
    seed when no canonical row exists, the three fixups when the canonical
    row lacks the 2026.3.0 marker, only the route fix when a marked row
    still calls a dead route (a crash between modernize and the route fix,
    or a form modernised before that fix existed). Empty when current."""
    canonical = [r for r in rows if is_rtl_canonical(r)]
    if not canonical:
        return [RTL_SEED_SCRIPT] + list(RTL_FIXUP_SCRIPTS)
    if any(str(r[4]) != "1" for r in canonical):
        return list(RTL_FIXUP_SCRIPTS)
    if any(len(r) > 5 and str(r[5]) == "1" for r in canonical):
        return [RTL_ROUTE_FIX_SCRIPT]
    return []


def rtl_plan(rows: Sequence[Sequence[str]]
             ) -> Tuple[List[str], List[str], List[str], List[str]]:
    """(fids to disable, scripts to run, fids to re-disable afterwards,
    notes) from rtl_rows_sql rows.

    Any RTL-titled row that is not the canonical `Rich Text Letter` (the
    legacy `letter`) is disabled — it carries the RptByExample.do sink and
    dead attach routes — never renamed or deleted. A canonical row that
    was disabled (status 0: O19's 2012 seed ships it so, and a clinic may
    have switched it off) is re-disabled after the scripts (the enable
    script switches every canonical row on): the import modernises what
    the clinic has but never makes a hidden form visible. A row named
    `Rich Text Letter` whose subject the clinic edited is out of the
    scripts' reach: reported."""
    disable, restore, notes = [], [], []
    scripts = fixup_scripts_needed(rows)
    for row in rows:
        fid, form_name, status = row[0], row[1], row[2]
        if is_rtl_canonical(row):
            if str(row[4]) == "1" and RTL_MODERNIZE_SCRIPT not in scripts:
                notes.append("fid {0}: Rich Text Letter already at 2026.3.0"
                             .format(fid))
            if str(status) == "0" and RTL_ENABLE_SCRIPT in scripts:
                restore.append(fid)
                notes.append("fid {0}: disabled in the clinic's O19 (the O19 "
                             "seed ships it disabled and this clinic never "
                             "enabled it, or switched it off) — modernised "
                             "but left disabled; enable it in Administration "
                             "> eForms if the clinic wants it".format(fid))
            if RTL_MODERNIZE_SCRIPT in scripts:
                notes.append("fid {0}: form_html replaced by the 2026.3.0 "
                             "build (clinic edits to the template are not "
                             "kept; the original row stays in the staging "
                             "schema until --cleanup)".format(fid))
        elif form_name == RTL_FORM_NAME:
            notes.append("fid {0}: named 'Rich Text Letter' but its subject "
                         "does not start with '{1}' — the packaged scripts "
                         "cannot address it; review by hand".format(
                             fid, RTL_SUBJECT_PREFIX))
        else:
            if str(status) != "0":
                disable.append(fid)
            notes.append("fid {0} ({1!r}): legacy Rich Text Letter form "
                         "disabled (superseded by the 2026.3.0 form)"
                         .format(fid, form_name))
    if not any(is_rtl_canonical(r) for r in rows):
        notes.append("no addressable 'Rich Text Letter' row: the v1 seed is "
                     "applied before the fixups")
    return disable, scripts, restore, notes


def rtl_current(rows: Sequence[Sequence[str]]) -> bool:
    """True when a canonical row exists, carries the marker and calls no
    dead route — what the fixups must leave behind."""
    return any(is_rtl_canonical(r) and str(r[4]) == "1"
               and not (len(r) > 5 and str(r[5]) == "1") for r in rows)


# --- verification -----------------------------------------------------------

def verify_role_checks(query: Callable, dst_schema: str,
                       admin_provider_no: Optional[str],
                       seed_floor: int
                       ) -> Tuple[List[str], List[str], List[str], List[str]]:
    """(ok_lines, problems, advisories, private_lines) for P7.

    Hard failures are the guarantees this step itself makes; advisories
    are clinic-data conditions the technical review must judge (names of
    the providers concerned go to the private file, never report.txt)."""
    ok, problems, advisories, private = [], [], [], []

    def n(sql):
        return int(query(sql)[0][0])

    # role_name is UNIQUE under a case-insensitive collation; `Doctor` is
    # the doctor role to the database and to Java's getRoleByName
    roles = {r[0].casefold() for r in query(
        "SELECT role_name FROM `{0}`.secRole".format(dst_schema))}
    for name, _ in GUARANTEED_ROLES:
        if name.casefold() in roles:
            ok.append("role '{0}' present".format(name))
        else:
            problems.append("role '{0}' missing from secRole".format(name))

    if admin_provider_no:
        pn = _sql_str(admin_provider_no)
        active = n("SELECT COUNT(*) FROM `{0}`.secUserRole WHERE provider_no "
                   "= '{1}' AND activeyn = 1".format(dst_schema, pn))
        if active:
            ok.append("break-glass admin holds {0} active role(s)"
                      .format(active))
        else:
            problems.append("break-glass admin has no active secUserRole row")
        admin_grant = n(
            "SELECT COUNT(*) FROM `{0}`.secObjPrivilege p JOIN "
            "`{0}`.secUserRole ur ON ur.role_name = p.roleUserGroup WHERE "
            "ur.provider_no = '{1}' AND ur.activeyn = 1 AND p.objectName = "
            "'_admin' AND (p.privilege LIKE '%x%' OR p.privilege LIKE "
            "'%w%')".format(dst_schema, pn))
        if admin_grant:
            ok.append("break-glass admin holds _admin")
        else:
            problems.append("break-glass admin has no _admin x/w grant "
                            "through an active role")

    if n(enabled_facility_count_sql(dst_schema)):
        ok.append("an enabled Facility exists")
    else:
        problems.append("no enabled Facility (login cannot set a facility)")
    if n(clinic_count_sql(dst_schema)):
        ok.append("clinic row present")
    else:
        problems.append("clinic table is empty (letterheads, requisitions "
                        "and consults dereference it)")
    if n("SELECT COUNT(*) FROM `{0}`.program WHERE name = '{1}'".format(
            dst_schema, OSCAR_PROGRAM)):
        ok.append("program '{0}' present".format(OSCAR_PROGRAM))
    else:
        problems.append("program '{0}' missing (startup would create it "
                        "for a provider that no longer exists)"
                        .format(OSCAR_PROGRAM))
    missing = n(providers_without_membership_sql(dst_schema))
    if missing:
        problems.append("{0} active provider(s) without program_provider "
                        "membership".format(missing))
    else:
        ok.append("every active provider has a program membership")
    unlinked = n("SELECT COUNT(*) FROM `{0}`.provider p WHERE p.status = "
                 "'1' AND NOT EXISTS (SELECT 1 FROM `{0}`.provider_facility "
                 "pf WHERE pf.provider_no = p.provider_no)".format(dst_schema))
    if unlinked:
        problems.append("{0} active provider(s) without a facility link"
                        .format(unlinked))
    else:
        ok.append("every active provider is linked to a facility")
    grants = n("SELECT COUNT(*) FROM `{0}`.secObjPrivilege".format(dst_schema))
    if grants >= seed_floor:
        ok.append("secObjPrivilege holds {0} grant(s) (seed floor {1})"
                  .format(grants, seed_floor))
    else:
        problems.append("secObjPrivilege holds {0} grant(s), below the "
                        "CARLOS seed floor {1}".format(grants, seed_floor))

    # advisories — clinic data the review must judge
    no_role = query(
        "SELECT p.provider_no FROM `{0}`.provider p JOIN `{0}`.security s "
        "ON s.provider_no = p.provider_no WHERE p.status = '1' AND NOT "
        "EXISTS (SELECT 1 FROM `{0}`.secUserRole ur WHERE ur.provider_no = "
        "p.provider_no AND ur.activeyn = 1) ORDER BY p.provider_no"
        .format(dst_schema))
    if no_role:
        advisories.append("{0} active account(s) hold no active role — they "
                          "can log in but reach nothing (see roles-details."
                          "txt)".format(len(no_role)))
        private.append("active accounts with no active role: "
                       + ", ".join(r[0] for r in no_role))
    no_grant = query(
        "SELECT DISTINCT ur.role_name FROM `{0}`.secUserRole ur WHERE "
        "ur.activeyn = 1 AND NOT EXISTS (SELECT 1 FROM `{0}`.secObjPrivilege "
        "p WHERE p.roleUserGroup = ur.role_name) ORDER BY ur.role_name"
        .format(dst_schema))
    if no_grant:
        advisories.append("role(s) assigned but granting nothing: "
                          + ", ".join(r[0] for r in no_grant))
    locked = query(
        "SELECT user_name FROM `{0}`.security WHERE b_ExpireSet = 1 AND "
        "(date_ExpireDate IS NULL OR date_ExpireDate < NOW()) ORDER BY "
        "user_name".format(dst_schema))
    if locked:
        advisories.append("{0} login(s) import expired (b_ExpireSet with a "
                          "past or missing expiry) — extend or clear before "
                          "go-live (see roles-details.txt)".format(
                              len(locked)))
        private.append("expired logins: " + ", ".join(r[0] for r in locked))
    jobs = n("SELECT COUNT(*) FROM `{0}`.OscarJobType".format(dst_schema))
    if not jobs:
        advisories.append("OscarJobType is empty: the job scheduler has no "
                          "job types (CARLOS ships none; configure in "
                          "Administration if the clinic used scheduled jobs)")
    rtl_rows = query(rtl_rows_sql(dst_schema))
    if rtl_current(rtl_rows):
        ok.append("Rich Text Letter at 2026.3.0 with live attachment routes")
    else:
        advisories.append("no Rich Text Letter eForm at 2026.3.0 with live "
                          "attachment routes — apply the packaged RTL "
                          "scripts by hand before go-live (see the roles: "
                          "Rich Text Letter report line)")
    return ok, problems, advisories, private


# --- driver -----------------------------------------------------------------

def run_roles(ctx, progress: Dict, save: Callable[[], None]) -> None:
    """The post-copy step, ledger-marked per sub-step under progress['roles'].
    ctx carries the ETL executors (query_etl with the bulk-copy prelude,
    query plain), the schema names, the report callback and the CLI
    options (role_templates, fixups_dir).

    Crash discipline: every write is idempotent, and everything the
    ledger records is either taken from the database AFTER the write
    (appended-row counts are the same anti-join row parity checks) or
    decided and persisted BEFORE the write (private lists, planned
    counts), so a resume after a crash between a write and its mark
    reports the same facts as an uninterrupted run."""
    from .util import die
    from . import o19import
    query = ctx["query_etl"]
    plain = ctx["query"]
    src, dst, arch = ctx["src_schema"], ctx["target_db"], ctx["archive_schema"]
    report = ctx["report"]
    state_dir = ctx["state_dir"]
    ledger = progress.setdefault(LEDGER_KEY, {})
    appended = ledger.setdefault("appended", {})
    details_path = os.path.join(state_dir, "roles-details.txt")

    def n(sql):
        return int(plain(sql)[0][0])

    def count(table):
        return n("SELECT COUNT(*) FROM `{0}`.`{1}`".format(dst, table))

    def mark(key, value=True):
        ledger[key] = value
        save()

    def record_appended(table):
        # the rows with no staging twin — exactly what row_parity will
        # measure, so a re-entered step records the same figure
        appended[table] = n(appended_row_count_sql(table, src, dst))

    def plan(key, compute):
        # decide once, persist, then act: the ledger keeps the decision
        # across a crash between the write and the step's mark
        stored = ledger.get(key + "_plan")
        if stored is None:
            stored = compute()
            mark(key + "_plan", stored)
        return stored

    def append_private(lines):
        existing = ""
        if os.path.isfile(details_path):
            with open(details_path, encoding="utf-8") as fh:
                existing = fh.read()
        o19import.write_private(details_path,
                                existing + "\n".join(lines) + "\n")

    # 0. --role-template is bound to the ledger like --admin-user: the
    #    mapping the import decided on is the one a resume continues with
    requested = dict(ctx.get("role_templates") or {})
    recorded = ledger.get("role_templates")
    if recorded is None:
        mark("role_templates", requested)
        recorded = requested
    elif requested and requested != recorded:
        die("roles: this import recorded --role-template {0}; the resume "
            "passes {1}. Resume with the recorded mapping (or without the "
            "flag) — a changed template would graft a second stock role's "
            "grants onto the custom role.".format(
                _fmt_templates(recorded), _fmt_templates(requested)))
    overrides = dict(recorded)

    # 1. hard-coded role names, CARLOS-only roles
    if not ledger.get("roles_appended"):
        before = count("secRole")
        for sql in guaranteed_role_statements(dst):
            query(sql)
        query(carlos_role_append_statement(dst, arch))
        added = count("secRole") - before
        record_appended("secRole")
        mark("roles_appended")
        if appended["secRole"]:
            names = [r[0] for r in plain(
                "SELECT role_name FROM `{0}`.secRole WHERE role_name NOT IN "
                "(SELECT role_name FROM `{1}`.secRole) ORDER BY role_name"
                .format(dst, src))]
            report("roles: {0} CARLOS role(s) added to the clinic's "
                   "catalogue ({1} this run): {2}".format(
                       appended["secRole"], added, ", ".join(names)))

    # 2. facility / clinic guarantees, facility links (run_etl pre-checks
    #    the same conditions against staging before the first write; this
    #    is the backstop)
    if not ledger.get("facility_links"):
        if not n(enabled_facility_count_sql(dst)):
            die("roles: the imported clinic has no enabled Facility row — "
                "CARLOS cannot log anyone in without one. Restore the "
                "pre-import snapshot, fix the source (enable a Facility) "
                "and re-run.")
        if not n(clinic_count_sql(dst)):
            die("roles: the imported clinic has no `clinic` row — "
                "letterheads, requisitions and consultations dereference "
                "it. Restore the pre-import snapshot, fix the source and "
                "re-run.")
        before = count("provider_facility")
        query(provider_facility_statement(dst))
        added = count("provider_facility") - before
        record_appended("provider_facility")
        mark("facility_links")
        if appended["provider_facility"]:
            report("roles: {0} active provider(s) linked to the first "
                   "enabled facility (they had no provider_facility row; "
                   "{1} this run)".format(appended["provider_facility"],
                                          added))

    # 3. activeyn normalisation (user decision: activate rows of live
    #    accounts, report every one). The list is decided and written
    #    to the private file BEFORE the UPDATE (file-first).
    if not ledger.get("activeyn"):
        candidates = plan("activeyn", lambda: [
            r[0] for r in plain(activeyn_candidates_sql(dst))])
        if candidates and not ledger.get("activeyn_listed"):
            append_private(["secUserRole.activeyn set to 1 for active "
                            "accounts: " + ", ".join(candidates)])
            mark("activeyn_listed")
        if candidates:
            query(activeyn_update_statement(dst))
        remaining = n(activeyn_null_remaining_sql(dst))
        mark("activeyn", {"activated_providers": len(candidates),
                          "null_rows_left": remaining})
        report("roles: secUserRole.activeyn was NULL for {0} active "
               "account(s) — set to 1 (CARLOS counts only activeyn = 1); "
               "{1} NULL row(s) of inactive accounts left as they were"
               .format(len(candidates), remaining))

    # 4. OSCAR program + memberships (after activeyn: role_id follows the
    #    provider's active role)
    if not ledger.get("program"):
        query(oscar_program_statement(dst))
        record_appended("program")
        missing, fallback = plan("program", lambda: [
            n(providers_without_membership_sql(dst)),
            [r[0] for r in plain(fallback_membership_candidates_sql(dst))]])
        if fallback and not ledger.get("program_listed"):
            append_private(["program membership with the least-privileged "
                            "clinic role (no active role held): "
                            + ", ".join(fallback)])
            mark("program_listed")
        for sql in membership_statements(dst):
            query(sql)
        record_appended("program_provider")
        mark("program")
        report("roles: program '{0}' {1}; {2} active provider(s) had no "
               "program membership — {3} membership row(s) now carry the "
               "provider's active role, {4} the least-privileged clinic "
               "role because the provider holds no active role (listed in "
               "roles-details.txt; assign a role in Administration)".format(
                   OSCAR_PROGRAM,
                   "created" if appended["program"] else "present",
                   missing, max(0, missing - len(fallback)), len(fallback)))

    # 5. custom-role backfill of CARLOS-era grants
    if not ledger.get("backfill"):
        seed_rows = plain("SELECT roleUserGroup, objectName, privilege, "
                          "priority FROM `{0}`.`{1}`".format(
                              arch, snapshot_table("secObjPrivilege")))
        stage_rows = plain("SELECT roleUserGroup, objectName, privilege, "
                           "priority FROM `{0}`.secObjPrivilege".format(src))
        stage_objects = [r[0] for r in plain(
            "SELECT objectName FROM `{0}`.secObjectName".format(src))]
        target_roles = [r[0] for r in plain(
            "SELECT role_name FROM `{0}`.secRole".format(dst))]
        stock = list(o19map_schema.STOCK_ROLE_NAMES)
        customs = custom_roles(target_roles, stage_rows, stock)
        era = carlos_era_objects(seed_rows, stage_rows, stage_objects)
        problems = validate_role_templates(overrides, customs, stock)
        if problems:
            die("roles: " + "; ".join(problems) + " — fix the flag and "
                "--resume (the completed sub-steps are kept)")
        overrides = normalise_role_templates(overrides, customs, stock)
        lines = ["CARLOS-era objects absent from the clinic's O19 ({0}): {1}"
                 .format(len(era), ", ".join(era) or "none")]
        odd = non_role_named_roles(target_roles)
        if odd:
            lines.append("secRole rows named like non-role groups, left "
                         "untouched by the backfill: " + ", ".join(odd))

        def decide():
            chosen, skipped = {}, {}
            for custom in customs:
                if custom in overrides:
                    chosen[custom] = [overrides[custom], -1.0]
                else:
                    template, score = choose_template(
                        custom, stage_rows, seed_rows,
                        o19map_schema.ROLE_TEMPLATE_MIN_JACCARD, stock)
                    if template is None:
                        skipped[custom] = score
                    else:
                        chosen[custom] = [template, score]
            pending = {}
            for custom, (template, _score) in chosen.items():
                pending[custom] = n(backfill_pending_count_sql(
                    dst, arch, custom, template, era)) if era else 0
            return {"templates": chosen, "skipped": skipped,
                    "pending": pending}

        decided = plan("backfill", decide)
        for custom in customs:
            if custom in decided["skipped"]:
                lines.append("custom role {0!r}: no stock role resembles it "
                             "(best similarity {1:.2f} < {2}) — grant "
                             "CARLOS-era objects by hand in Administration"
                             .format(custom, decided["skipped"][custom],
                                     o19map_schema.ROLE_TEMPLATE_MIN_JACCARD))
                continue
            template, score = decided["templates"][custom]
            how = ("operator --role-template" if score < 0 else
                   "closest stock role, similarity {0:.2f}".format(score))
            if era:
                query(backfill_statement(dst, arch, custom, template, era))
            added = decided["pending"][custom]
            if not era:
                why = " (the clinic's O19 already knew every seeded object)"
            elif not added:
                why = (" (the template holds none of them; only doctor/"
                       "admin-class roles do)")
            else:
                why = ""
            lines.append("custom role {0!r}: template {1!r} ({2}), {3} "
                         "CARLOS-era grant(s) added{4}".format(
                             custom, template, how, added, why))
        mark("backfill", {"templates": {c: t for c, (t, _s) in
                                        decided["templates"].items()},
                          "customs": customs})
        report("roles: {0} clinic-custom role(s)\n  ".format(len(customs))
               + "\n  ".join(lines))
    elif requested:
        report("roles: --role-template already applied by the run that "
               "performed the backfill (recorded: {0})".format(
                   _fmt_templates(recorded)))

    # 6. privilege diff (clinic grants CARLOS's seed overrode), stock-role
    #    appends and exclusions — all itemised for the technical review
    if not ledger.get("diff"):
        diff = plain(privilege_diff_sql(src, arch))
        appends = plain(stock_role_appends_sql(
            src, arch, o19map_schema.STOCK_ROLE_NAMES))
        excluded_sql = excluded_grants_sql(src)
        excluded = plain(excluded_sql) if excluded_sql else []
        text = ["clinic grants overridden by the CARLOS seed on the same "
                "(role, object): {0}".format(len(diff)),
                "role | object | clinic privilege/priority -> CARLOS"]
        for r in diff:
            text.append("{0} | {1} | {2}/{3} -> {4}/{5}".format(*r))
        text += ["", "clinic grants on stock roles the CARLOS seed does not "
                     "hold (appended as the clinic had them): {0}".format(
                         len(appends)),
                 "role | object | privilege/priority"]
        for r in appends:
            text.append("{0} | {1} | {2}/{3}".format(*r))
        text += ["", "clinic grants on objects no CARLOS code checks (not "
                     "carried): {0}".format(len(excluded)),
                 "role | object | privilege"]
        for r in excluded:
            text.append("{0} | {1} | {2}".format(*r))
        o19import.write_private(os.path.join(state_dir, "privilege-diff.txt"),
                                "\n".join(text) + "\n")
        mark("diff", {"overridden": len(diff), "appended": len(appends),
                      "excluded": len(excluded)})
        report("roles: {0} clinic grant(s) differ from the CARLOS seed on "
               "the same (role, object) — CARLOS's value stands; {1} clinic "
               "grant(s) on stock roles have no CARLOS seed row and were "
               "appended; {2} grant(s) on objects no CARLOS code checks not "
               "carried — all itemised in privilege-diff.txt for the "
               "technical review".format(len(diff), len(appends),
                                         len(excluded)))

    # 7. removed-module keys in the property table
    if not ledger.get("property_pruned"):
        from . import o19_preflight
        stmts = property_prune_statements(
            dst, o19_preflight.DROPPED_PROP_PREFIXES)
        pruned = plan("property_pruned", lambda: [
            [prefix, n(count_sql)] for prefix, count_sql, _d in stmts
            if n(count_sql)])
        for prefix, _c, delete_sql in stmts:
            if any(p == prefix for p, _n in pruned):
                query(delete_sql)
        mark("property_pruned", {"pruned": pruned})
        if pruned:
            report("roles: property rows of removed modules pruned: "
                   + ", ".join("{0} ({1})".format(p, c) for p, c in pruned))

    # 8. prevention type codes (binary compare — see
    #    prevention_type_statements)
    if not ledger.get("prevention_types"):
        stmts = prevention_type_statements(
            dst, o19map_schema.PREVENTION_TYPE_MAP)
        applied = plan("prevention_types", lambda: [
            [legacy, canonical, n(count_sql)]
            for legacy, canonical, count_sql, _u in stmts if n(count_sql)])
        due = {legacy for legacy, _c, _n in applied}
        for legacy, _canonical, _count_sql, update_sql in stmts:
            if legacy in due:
                query(update_sql)
        unknown = plain(unknown_prevention_types_sql(
            dst, o19map_schema.KNOWN_PREVENTION_TYPES))
        mark("prevention_types", {"applied": applied,
                                  "unknown": len(unknown)})
        report("roles: prevention types normalised: {0}{1}".format(
            ", ".join("{0} -> {1} ({2})".format(*a) for a in applied)
            or "none needed",
            ("\n  types PreventionItems.xml does not render (shown as "
             "unconfigured; review): " + ", ".join(
                 "{0} ({1})".format(r[0], r[1]) for r in unknown))
            if unknown else ""))

    # 9. Rich Text Letter
    if not ledger.get("rtl"):
        rows = plain(rtl_rows_sql(dst))
        disable, scripts, restore, notes = rtl_plan(rows)
        for fid in disable:
            query(rtl_disable_statement(dst, fid))
        outcome = "unchanged" if not scripts else ""
        if scripts:
            fixups_dir = ctx.get("fixups_dir") or DEFAULT_FIXUPS_DIR
            missing = [s for s in scripts
                       if not os.path.isfile(os.path.join(fixups_dir, s))]
            if missing:
                outcome = ("fixup scripts missing from {0}: {1} — apply the "
                           "Rich Text Letter 2026.3.0 SQL by hand before "
                           "go-live".format(fixups_dir, ", ".join(missing)))
            else:
                for script in scripts:
                    with open(os.path.join(fixups_dir, script),
                              encoding="utf-8") as fh:
                        # ETL executor (sql_mode='' — the 2012 seed omits
                        # NOT NULL columns), target as default database:
                        # the scripts use DELIMITER (a client directive)
                        # and unqualified table names
                        query(fh.read(), db=dst)
                for fid in restore:
                    query(rtl_disable_statement(dst, fid))
                after = plain(rtl_rows_sql(dst))
                if rtl_current(after) or (restore and any(
                        is_rtl_canonical(r) and str(r[4]) == "1"
                        for r in after)):
                    outcome = "modernised to 2026.3.0 via {0}".format(
                        ", ".join(scripts))
                else:
                    outcome = ("scripts ran ({0}) but no 'Rich Text Letter' "
                               "row carries the 2026.3.0 marker with live "
                               "attachment routes — the scripts match "
                               "form_name = 'Rich Text Letter' AND subject "
                               "LIKE '{1}%'; apply by hand before go-live"
                               .format(", ".join(scripts), RTL_SUBJECT_PREFIX))
        mark("rtl", {"disabled": disable, "restored_disabled": restore,
                     "scripts": scripts, "outcome": outcome})
        report("roles: Rich Text Letter — {0}{1}".format(
            outcome, ("\n  " + "\n  ".join(notes)) if notes else ""))


def _fmt_templates(mapping: Dict[str, str]) -> str:
    return (", ".join("{0}={1}".format(c, t) for c, t in sorted(
        mapping.items())) or "none")
