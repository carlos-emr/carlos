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
from . import o19etl
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
#: (case-insensitive, like the column's collation and the SQL twin below)
NON_ROLE_GROUP_RE = re.compile(r"^(-?\d+|_all|_queue\..*)$", re.IGNORECASE)
#: the same pattern for MariaDB REGEXP (single-quoted SQL literal body)
NON_ROLE_GROUP_SQL_RE = "^(-?[0-9]+|_all|_queue\\\\..*)$"

#: the seed's system pseudo-provider (`-1 system`, status '1', no login):
#: never a clinician, so it gets no membership, facility link or review
#: entry and is not counted as an active account anywhere in this step
SYSTEM_PROVIDER = "-1"

#: a clinic-custom role is mapped onto the `admin` template automatically
#: only when it is unmistakably an administrator role; below this
#: similarity the operator names the template (--role-template) instead
ADMIN_TEMPLATE_MIN_JACCARD = 0.5

OSCAR_PROGRAM = "OSCAR"

RTL_TITLE_MARKER = "<title>Rich Text Letter</title>"
#: the report-by-example injection sink the 2010 form carried; a clinic
#: copy that does not call it (nor a dead attach route) is a clinic form
RTL_SINK_MARKER = "RptByExample.do"
RTL_FORM_NAME = "Rich Text Letter"
#: the 2010 O19 seed's name for the same form
RTL_LEGACY_FORM_NAME = "letter"
#: the packaged scripts match `form_name = 'Rich Text Letter'` AND
#: `subject LIKE 'Rich Text Letter Generator%'`; a row whose subject a
#: clinic edited is out of their reach and is reported, not claimed fixed
RTL_SUBJECT_PREFIX = "Rich Text Letter Generator"
#: the scripts' WHERE, verbatim (test_scripts_match_the_rows_the_planner_
#: calls_canonical pins it against the packaged files)
RTL_CANONICAL_PREDICATE = ("form_name = 'Rich Text Letter' AND subject LIKE "
                           "'Rich Text Letter Generator%'")
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
    """Archive-schema name for the pre-import snapshot of a role table."""
    return "carlos_seed_" + table


# --- pure statement builders ------------------------------------------------

def snapshot_statements(dst_schema: str, archive_schema: str) -> List[str]:
    """Copy the CARLOS seed of the role tables into o19_archive before any
    clinic row lands.

    Built beside the previous snapshot and swapped in, never dropped
    first: this is taken BEFORE the merges, so once they have run the
    target no longer holds only the seed and the snapshot cannot be
    recreated. A DROP whose CREATE then failed would take the privilege
    baseline the roles step diffs against with it.
    """
    out = []
    for table in SNAPSHOT_TABLES:
        out.extend(o19etl.rebuild_statements(
            archive_schema, snapshot_table(table),
            lambda scratch, t=table: [
                "CREATE TABLE `{0}`.{1} AS SELECT * FROM `{2}`.`{3}`"
                .format(archive_schema, o19etl.ident(scratch),
                        dst_schema, t)]))
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
    """Counts the rows the webapp's own startup listener writes into
    `table` -- the ones P0 tolerates on a stock deploy and the copy
    removes before the clinic's rows land."""
    return "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE {2}".format(
        schema, table, startup_row_predicate(where, schema))


def guaranteed_role_statements(dst_schema: str) -> List[str]:
    """Insert-if-absent for the roles CARLOS requires regardless of what
    the clinic had. Written as `WHERE NOT EXISTS` so a resume replays
    them without duplicating a role."""
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
    """Counts enabled facilities. Zero of them is a refusal: CARLOS scopes
    a provider's whole session by facility, so an import that left none
    enabled gives every carried login an empty EMR."""
    return ("SELECT COUNT(*) FROM `{0}`.Facility WHERE disabled = 0"
            .format(dst_schema))


def clinic_count_sql(dst_schema: str) -> str:
    """Counts `clinic` rows -- the companion presence check to the facility
    one."""
    return "SELECT COUNT(*) FROM `{0}`.clinic".format(dst_schema)


def provider_facility_statement(dst_schema: str) -> str:
    """Every active provider gets the first enabled facility unless it
    already belongs to one (login self-heals this only when it can)."""
    return ("INSERT INTO `{0}`.provider_facility (provider_no, facility_id) "
            "SELECT p.provider_no, (SELECT MIN(f.id) FROM `{0}`.Facility f "
            "WHERE f.disabled = 0) FROM `{0}`.provider p "
            "WHERE p.status = '1' AND p.provider_no <> '{1}' AND NOT EXISTS "
            "(SELECT 1 FROM `{0}`.provider_facility pf WHERE pf.provider_no "
            "= p.provider_no)".format(dst_schema, SYSTEM_PROVIDER))


def oscar_program_statement(dst_schema: str) -> str:
    """The program ContextStartupListener would create on first start
    (name OSCAR, type Service, active, maxAllowed 99999, first enabled
    facility). HAVING keeps the aggregate from inserting a NULL-facility
    row when the program already exists or no facility is enabled."""
    # Two rules, not one. Every NOT NULL column without a default is
    # supplied explicitly (Program.java's field defaults) so the
    # statement does not depend on the executor's sql_mode -- and so is
    # every column `Program.java` maps as a JAVA PRIMITIVE, whatever the
    # schema's nullability, because Hibernate cannot hydrate NULL into
    # one.
    #
    # The second rule was missing, and it is not cosmetic: measured by
    # deploying the application against a migrated database, the row
    # this statement wrote made CARLOS FAIL TO START --
    #
    #   SEVERE ... Exception sending context initialized event to
    #   listener instance of class ContextStartupListener
    #   java.lang.NullPointerException: Cannot invoke
    #   "java.lang.Boolean.booleanValue()" because "<parameter2>" is null
    #     at ...PMmodule.model.Program$HibernateAccessOptimizer...
    #
    # -- so the whole webapp was undeployable (HTTP 404 on every route)
    # on a clinic whose import had otherwise passed every gate. The five
    # columns below are `private boolean` fields in Program.java over
    # columns the CARLOS schema leaves nullable; `userDefined` defaults
    # to true there, the rest to false.
    #
    # BOXED `Boolean` fields belong to the same rule, and finding that
    # out cost a second measurement: `enableEncounterTime` and
    # `enableEncounterTransportationTime` are `Boolean` (not `boolean`)
    # in Program.java, so the primitive cross missed them -- and the
    # application unboxes them anyway. On a migrated clinic the clinical
    # NOTES pane of EVERY chart answered HTTP 500 with
    #
    #   NullPointerException: Cannot invoke "java.lang.Boolean.
    #   booleanValue()" because the return value of
    #   "Program.getEnableEncounterTime()" is null
    #
    # Both carry `= false` in Program.java, which is what this writes.
    # Re-derive the set by crossing Program.java's `boolean` AND
    # `Boolean` fields with the nullable columns of `program` -- which is
    # exactly what the contract test does.
    return ("INSERT INTO `{0}`.program (facilityId, name, description, "
            "type, programStatus, maxAllowed, transgender, firstNation, "
            "alcohol, physicalHealth, mentalHealth, housing, exclusiveView, "
            "ageMin, ageMax, holdingTank, allowBatchAdmission, "
            "allowBatchDischarge, hic, userDefined, enableEncounterTime, "
            "enableEncounterTransportationTime) SELECT MIN(f.id), "
            "'{1}', '{1}', 'Service', 'active', 99999, 0, 0, 0, 0, 0, 0, "
            "'no', 0, 0, 0, 0, 0, 0, 1, 0, 0 FROM "
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
    # a secRole row named like a non-role group (digits, _all, _queue.*)
    # is never a membership role — same pattern as NON_ROLE_GROUP_RE
    least_role = ("(SELECT r.role_no FROM `{0}`.secRole r WHERE r.role_name "
                  "NOT REGEXP '{2}' ORDER BY {1} ASC, r.role_no LIMIT 1)"
                  .format(dst_schema, grants, NON_ROLE_GROUP_SQL_RE))
    # the programs this provider's OWN work is in, beside the OSCAR
    # program. A membership only in OSCAR is not enough to reach a chart:
    # `CaseManagementManagerImpl.isClientInProgramDomain` compares the
    # provider's programs against the CLIENT'S ADMISSIONS, so a patient
    # admitted to any other program answers "not in your program domain"
    # and the encounter never opens. Measured on a migrated clinic whose
    # OSCAR 19 `program_provider` was empty: every chart was unreachable
    # for every provider, including the break-glass administrator.
    #
    # This only ever fires for a provider who has NO membership at all --
    # a clinic that never configured program access, where OSCAR 19
    # denied them just as much -- so it cannot loosen an access
    # configuration the clinic actually made.
    own_programs = (
        "p.id IN (SELECT a.program_id FROM `{0}`.admission a WHERE "
        "a.provider_no = pr.provider_no AND a.program_id IS NOT NULL) "
        "OR p.id IN (SELECT ap.program_id FROM `{0}`.appointment ap WHERE "
        "ap.provider_no = pr.provider_no AND ap.program_id IS NOT NULL)")
    template = ("INSERT INTO `{0}`.program_provider (program_id, "
                "provider_no, role_id, team_id) SELECT DISTINCT p.id, "
                "pr.provider_no, {1}, NULL FROM `{0}`.provider pr JOIN "
                "`{0}`.program p ON (p.name = '{2}' OR {5}) WHERE "
                "pr.status = '1' AND pr.provider_no "
                "<> '{4}' AND NOT EXISTS (SELECT 1 FROM "
                "`{0}`.program_provider "
                "pp WHERE pp.provider_no = pr.provider_no) AND {3} IS NOT "
                "NULL")
    return [
        template.format(dst_schema, active_role, OSCAR_PROGRAM, active_role,
                        SYSTEM_PROVIDER,
                        own_programs.format(dst_schema)),
        template.format(dst_schema, least_role, OSCAR_PROGRAM, least_role,
                        SYSTEM_PROVIDER,
                        own_programs.format(dst_schema)),
    ]


def admin_membership_statement(dst_schema: str,
                               admin_provider_no: str) -> str:
    """Put the break-glass administrator in every program the clinic's
    patients are actually admitted to.

    The account exists so an operator can verify a migration before the
    clinic sees it, and `docs/o19-import-deb.md` tells them to smoke-test
    the migrated charts with it -- which the program-domain check makes
    impossible while the admin's only membership is the OSCAR program the
    roles step invents. Scoped to programs that HOLD an admission rather
    than to every row of the `program` table, so the account gains
    nothing it does not need: 33 of the stock programs are discharge
    destinations no patient is ever admitted to.

    Its role_id is the one its own OSCAR-program membership already
    carries, so this grants reach, never privilege. Idempotent: the
    NOT EXISTS skips a program it is already in, and the whole statement
    is a no-op once it has run."""
    pn = _sql_str(admin_provider_no)
    role = ("(SELECT pp.role_id FROM `{0}`.program_provider pp WHERE "
            "pp.provider_no = '{1}' ORDER BY pp.id LIMIT 1)"
            .format(dst_schema, pn))
    return ("INSERT INTO `{0}`.program_provider (program_id, provider_no, "
            "role_id, team_id) SELECT DISTINCT a.program_id, '{1}', {2}, "
            "NULL FROM `{0}`.admission a JOIN `{0}`.program p ON p.id = "
            "a.program_id WHERE a.program_id IS NOT NULL AND {2} IS NOT "
            "NULL AND NOT EXISTS (SELECT 1 FROM `{0}`.program_provider q "
            "WHERE q.provider_no = '{1}' AND q.program_id = a.program_id)"
            .format(dst_schema, pn, role))


def admin_unreachable_programs_sql(dst_schema: str,
                                   admin_provider_no: str) -> str:
    """COUNT of programs holding an admission that the break-glass
    administrator is still not a member of — the residual the report
    names, and zero after `admin_membership_statement` has run."""
    pn = _sql_str(admin_provider_no)
    return ("SELECT COUNT(DISTINCT a.program_id) FROM `{0}`.admission a "
            "WHERE a.program_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "
            "`{0}`.program_provider q WHERE q.provider_no = '{1}' AND "
            "q.program_id = a.program_id)".format(dst_schema, pn))


def fallback_membership_candidates_sql(dst_schema: str) -> str:
    """provider_no of active providers with no membership AND no active
    role — the ones the second membership statement gives the
    least-privileged role (listed privately, counted in the report)."""
    return ("SELECT pr.provider_no FROM `{0}`.provider pr WHERE pr.status = "
            "'1' AND pr.provider_no <> '{1}' AND NOT EXISTS (SELECT 1 FROM "
            "`{0}`.program_provider pp WHERE pp.provider_no = pr.provider_no) "
            "AND NOT EXISTS (SELECT 1 FROM `{0}`.secUserRole ur JOIN "
            "`{0}`.secRole r ON r.role_name = ur.role_name WHERE "
            "ur.provider_no = pr.provider_no AND ur.activeyn = 1) ORDER BY "
            "pr.provider_no".format(dst_schema, SYSTEM_PROVIDER))


def providers_without_membership_sql(dst_schema: str) -> str:
    """Counts active providers with no `program_provider` membership.

    Such a provider can sign in and reach nothing, so the step
    synthesises memberships and this is the residual it reports. The
    system pseudo-provider is excluded: it is not an account."""
    return ("SELECT COUNT(*) FROM `{0}`.provider pr WHERE pr.status = '1' "
            "AND pr.provider_no <> '{1}' AND NOT EXISTS (SELECT 1 FROM "
            "`{0}`.program_provider pp WHERE pp.provider_no = "
            "pr.provider_no)".format(dst_schema, SYSTEM_PROVIDER))


#: the one role the import never activates on its own: CARLOS treats a
#: NULL admin assignment as inactive on purpose (SecUserRoleDaoImpl —
#: "an inactive admin assignment (activeyn = 0 or legacy NULL) must not
#: grant admin access"), so a dormant admin row stays dormant and is
#: reported for an explicit decision
ADMIN_ROLE = "admin"


def _live_account_predicate(dst_schema: str, alias: str = "ur") -> str:
    """The role row belongs to a live account: provider status '1' with
    at least one login row. EXISTS, not a join — a provider with two
    logins (OSCAR allows it) must count once."""
    return ("EXISTS (SELECT 1 FROM `{0}`.provider p WHERE p.provider_no = "
            "{1}.provider_no AND p.status = '1' AND p.provider_no <> '{2}') "
            "AND EXISTS (SELECT 1 FROM `{0}`.security s WHERE s.provider_no "
            "= {1}.provider_no)".format(dst_schema, alias, SYSTEM_PROVIDER))


def activeyn_candidates_sql(dst_schema: str) -> str:
    """(provider_no, role_name) of every NULL-activeyn role row whose
    account is live (provider status '1' with a security row) — the rows
    the import activates — except `admin` assignments."""
    return ("SELECT ur.provider_no, ur.role_name FROM `{0}`.secUserRole ur "
            "WHERE ur.activeyn IS NULL AND {1} AND LOWER(ur.role_name) <> "
            "'{2}' ORDER BY ur.provider_no, ur.role_name".format(
                dst_schema, _live_account_predicate(dst_schema),
                ADMIN_ROLE))


def activeyn_admin_left_sql(dst_schema: str) -> str:
    """provider_no of live accounts whose NULL-activeyn row is an `admin`
    assignment — left as it is, listed for the review."""
    return ("SELECT DISTINCT ur.provider_no FROM `{0}`.secUserRole ur "
            "WHERE ur.activeyn IS NULL AND {1} AND LOWER(ur.role_name) = "
            "'{2}' ORDER BY ur.provider_no".format(
                dst_schema, _live_account_predicate(dst_schema),
                ADMIN_ROLE))


def activeyn_update_statement(dst_schema: str) -> str:
    """Activates the legacy NULL `activeyn` assignments of live accounts.

    OSCAR 19 left the column NULL where CARLOS reads NULL as inactive,
    so untouched rows would silently grant nothing. `admin` is
    deliberately excluded -- a dormant admin assignment stays dormant
    and is reported for an explicit decision rather than activated by a
    migration."""
    return ("UPDATE `{0}`.secUserRole ur SET ur.activeyn = 1 WHERE "
            "ur.activeyn IS NULL AND {1} AND LOWER(ur.role_name) <> '{2}'"
            .format(dst_schema, _live_account_predicate(dst_schema),
                    ADMIN_ROLE))


def dangling_role_assignments_sql(dst_schema: str,
                                  role_names: Sequence[str]) -> str:
    """Active assignments to the CARLOS-only roles the step re-added:
    they granted nothing in O19 (no secRole row) and now carry the CARLOS
    seed's grants — listed for the review."""
    names = ", ".join("'{0}'".format(_sql_str(r)) for r in role_names)
    # step 3 activates the NULL rows of live accounts after this list is
    # taken, so those count as active here too (else the very rows the
    # import switches on would be the ones missing from the review)
    return ("SELECT ur.provider_no, ur.role_name FROM `{0}`.secUserRole ur "
            "WHERE ur.role_name IN ({1}) AND (ur.activeyn = 1 OR "
            "(ur.activeyn IS NULL AND {2})) ORDER BY ur.provider_no, "
            "ur.role_name".format(dst_schema, names,
                                  _live_account_predicate(dst_schema)))


def activeyn_null_remaining_sql(dst_schema: str) -> str:
    """NULL-activeyn rows of accounts that are NOT live (inactive
    provider, no login, or the system pseudo-provider) — the complement
    of what the step activates or lists as a dormant admin row, so the
    three figures in the report never count one row twice."""
    return ("SELECT COUNT(*) FROM `{0}`.secUserRole ur WHERE ur.activeyn IS "
            "NULL AND NOT ({1})".format(dst_schema,
                                        _live_account_predicate(dst_schema)))


# --- custom-role backfill (pure over fetched rows) -------------------------

def is_role_group(group: str) -> bool:
    """A roleUserGroup that names a role (not a provider number, `_all`
    or a document queue)."""
    return bool(group) and not NON_ROLE_GROUP_RE.match(group)


def carlos_era_objects(seed_rows: Sequence[Sequence[str]],
                       stage_rows: Sequence[Sequence[str]],
                       stage_objects: Sequence[str]) -> List[str]:
    """Objects the CARLOS seed grants that the clinic's O19 never knew:
    absent from its secObjectName AND from every one of its grants
    (compared like the column's collation: `_masterlink` knows
    `_masterLink`).

    _fold, not .lower(): the column's collation is PAD SPACE, so a
    clinic's `_hrm ` already covers `_hrm`. Missing that promotes an
    object the clinic knew into the CARLOS-era set, and the backfill then
    grants it to every custom role — `_hrm`, `_masterLink`, `_report`
    and the `_admin.*` family are all in that seed."""
    known = {_fold(o) for o in stage_objects} | {_fold(r[1])
                                                 for r in stage_rows}
    return sorted({r[1] for r in seed_rows if _fold(r[1]) not in known})


def custom_roles(target_roles: Sequence[str],
                 stage_rows: Sequence[Sequence[str]],
                 stock_roles: Sequence[str]) -> List[str]:
    """Clinic roles that (a) exist in the target secRole, (b) hold at least
    one imported grant and (c) are not a CARLOS stock role name."""
    # secRole.role_name is UNIQUE under a case-insensitive collation and
    # Java's getRoleByName matches the same way: `Doctor` IS the stock
    # role, and a grant row spelled `triage nurse` belongs to `Triage
    # Nurse`
    granted = {_fold(r[0]) for r in stage_rows if is_role_group(r[0])}
    stock = {_fold(r) for r in stock_roles}
    return sorted(r for r in target_roles
                  if _fold(r) in granted and _fold(r) not in stock)


def non_role_named_roles(target_roles: Sequence[str]) -> List[str]:
    """secRole rows whose name looks like a non-role group (all digits,
    `_all`, `_queue.*`): skipped by the backfill and reported, never
    silently ignored."""
    return sorted(r for r in target_roles if not is_role_group(r))


def _fold(name: str) -> str:
    """Compare like the role_name column: case-insensitive, TRAILING
    spaces ignored (PAD SPACE collation); a leading space is significant
    there and stays significant here."""
    return (name or "").rstrip(" ").lower()


def role_pairs(rows: Sequence[Sequence[str]],
               role: str) -> Set[Tuple[str, str]]:
    """{(object, privilege)} granted to one role.

    Role name AND object name are folded the way the database's own
    pad-space collation folds them, so "Nurse " and "nurse" are one role
    here as they are there, and `_masterlink` is `_masterLink` -- the
    spelling the O19 baseline's secObjectName carries against the one the
    CARLOS seed grants. Comparing object names raw (while
    carlos_era_objects, the backfill INSERT's `objectName IN (...)` and
    the merge join all fold them) dropped such a pair from the
    intersection without dropping it from the union, so choose_template's
    Jaccard score fell -- and fell unevenly across candidates, because a
    difference on `_masterLink` costs only the templates that hold it. On
    a small custom role that is enough to sink the best score under
    ROLE_TEMPLATE_MIN_JACCARD and leave the role for hand work. The
    privilege is stripped for the same collation reason."""
    return {(_fold(r[1]), (r[2] or "").strip()) for r in rows
            if _fold(r[0]) == _fold(role)}


def jaccard(a: Set, b: Set) -> float:
    """Intersection over union: how alike two grant sets are.

    Union rather than the smaller side on purpose -- scoring by the
    smaller side makes a tiny role "match" every large one it is a
    subset of, which is how a custom role would inherit the wrong
    template. Two empty sets resemble nothing (0.0), not everything."""
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
        stock_fold = {_fold(s) for s in stock_roles}
        candidates = {c for c in candidates if _fold(c) in stock_fold}
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
    ValueError; an exact or case-only repeat is tolerated."""
    out: Dict[str, str] = {}
    for raw in values or ():
        if "=" not in raw:
            raise ValueError("--role-template expects CUSTOM=STOCK, got {0!r}"
                             .format(raw))
        custom, stock = (part.strip() for part in raw.split("=", 1))
        if not custom or not stock:
            raise ValueError("--role-template expects CUSTOM=STOCK, got {0!r}"
                             .format(raw))
        # role names are unique case-insensitively (the column's
        # collation), so `Triage Nurse=nurse` and `triage nurse=doctor`
        # conflict; an exact or case-only repeat of the same mapping is
        # tolerated and the first spelling kept
        twin = next((k for k in out if k.lower() == custom.lower()),
                    None)
        if twin is not None:
            if out[twin].lower() != stock.lower():
                raise ValueError("--role-template names {0!r} twice"
                                 .format(custom))
            continue
        out[custom] = stock
    return out


def same_role_templates(a: Dict[str, str], b: Dict[str, str]) -> bool:
    """Equality up to case, like the role_name column."""
    def fold(m):
        return {c.lower(): t.lower() for c, t in m.items()}
    return fold(a) == fold(b)


def validate_role_templates(overrides: Dict[str, str],
                            customs: Sequence[str],
                            stock_roles: Sequence[str]) -> List[str]:
    """Problems with operator overrides (case-insensitive, like the
    role_name column's collation)."""
    problems = []
    custom_fold = {_fold(c) for c in customs}
    stock_fold = {_fold(r) for r in stock_roles}
    for custom, stock in sorted(overrides.items()):
        if _fold(custom) not in custom_fold:
            problems.append("--role-template {0!r}: not a clinic-custom role "
                            "with imported grants".format(custom))
        if _fold(stock) not in stock_fold:
            problems.append("--role-template {0!r}: {1!r} is not a CARLOS "
                            "stock role".format(custom, stock))
    return problems


def normalise_role_templates(overrides: Dict[str, str],
                             customs: Sequence[str],
                             stock_roles: Sequence[str]) -> Dict[str, str]:
    """Overrides re-keyed to the exact target/seed spellings (the INSERT
    must name the rows as the tables spell them). Validate first."""
    custom_by = {_fold(c): c for c in customs}
    stock_by = {_fold(r): r for r in stock_roles}
    return {custom_by[_fold(c)]: stock_by[_fold(t)]
            for c, t in overrides.items()
            if _fold(c) in custom_by and _fold(t) in stock_by}


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
    """Counts the clinic grants the privilege merge deliberately leaves
    behind (the manifest's `merge_exclude`), or None when the manifest
    excludes nothing."""
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


def restored_seed_grants_sql(src_schema: str, archive_schema: str) -> str:
    """CARLOS seed grants on roles the clinic HAS (a secRole row) whose
    (role, object) the clinic's own matrix does not hold at all — the
    clinic had removed or never held them, the merge keeps the seed's
    row, so after import the role can do more than it could in O19."""
    return ("SELECT d.roleUserGroup, d.objectName, d.privilege, d.priority "
            "FROM `{1}`.`{2}` d WHERE d.roleUserGroup IN (SELECT role_name "
            "FROM `{0}`.secRole) AND NOT EXISTS (SELECT 1 FROM "
            "`{0}`.secObjPrivilege s WHERE s.roleUserGroup = d.roleUserGroup "
            "AND s.objectName = d.objectName) ORDER BY d.roleUserGroup, "
            "d.objectName".format(src_schema, archive_schema,
                                  snapshot_table("secObjPrivilege")))


def appended_role_seed_grants_sql(src_schema: str, dst_schema: str,
                                  archive_schema: str) -> str:
    """CARLOS seed grants on roles the clinic did NOT have.

    The companion to restored_seed_grants_sql, which restricts itself to
    roles the clinic HAS -- so neither it nor any other diff query could
    ever name these. carlos_role_append_statement re-adds every CARLOS
    seed role missing from the clinic's catalogue, and the seed's grants
    for those role names are already sitting in the merged
    secObjPrivilege (the merge is a union, and CARLOS resolves a grant
    from secUserRole.role_name straight to secObjPrivilege with no secRole
    lookup). So the moment the role row exists, every one of these becomes
    live on any assignment the clinic still held -- an addition of access
    the technical review has to see itemised, not just counted."""
    return ("SELECT d.roleUserGroup, d.objectName, d.privilege, d.priority "
            "FROM `{1}`.`{2}` d WHERE d.roleUserGroup IN (SELECT role_name "
            "FROM `{3}`.secRole) AND d.roleUserGroup NOT IN (SELECT "
            "role_name FROM `{0}`.secRole) AND NOT EXISTS (SELECT 1 FROM "
            "`{0}`.secObjPrivilege s WHERE s.roleUserGroup = d.roleUserGroup "
            "AND s.objectName = d.objectName) ORDER BY d.roleUserGroup, "
            "d.objectName".format(src_schema, archive_schema,
                                  snapshot_table("secObjPrivilege"),
                                  dst_schema))


def backfilled_custom_grants_sql(src_schema: str, dst_schema: str,
                                 customs: Sequence[str]) -> str:
    """Grants the target holds on the clinic's custom roles that the
    clinic's own matrix never held -- i.e. the rows backfill_statement
    inserted, measured rather than re-derived from the plan.

    The report gives a per-role template name and a COUNT; the (object,
    privilege) rows themselves appeared in no artifact, and the `doctor`
    template's CARLOS-era grants include PHI-transmission objects
    (`_fax x`, `_email x`, `_rx.editPharmacy x`). A custom role holds no
    CARLOS-era grant of its own by construction, so dst-minus-src on these
    roles is exactly what the backfill added."""
    if not customs:
        # no custom roles -> nothing was backfilled; a `roleUserGroup IN
        # ()` would be a syntax error, so answer with an empty result set
        return ("SELECT roleUserGroup, objectName, privilege, priority FROM "
                "`{0}`.secObjPrivilege WHERE 1 = 0".format(dst_schema))
    roles = ", ".join("'{0}'".format(_sql_str(r)) for r in customs)
    return ("SELECT d.roleUserGroup, d.objectName, d.privilege, d.priority "
            "FROM `{1}`.secObjPrivilege d WHERE d.roleUserGroup IN ({2}) AND "
            "NOT EXISTS (SELECT 1 FROM `{0}`.secObjPrivilege s WHERE "
            "s.roleUserGroup = d.roleUserGroup AND s.objectName = "
            "d.objectName) ORDER BY d.roleUserGroup, d.objectName".format(
                src_schema, dst_schema, roles))


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


def property_prune_statements(dst_schema: str, prefixes: Sequence[str],
                              keys: Sequence[str] = ()
                              ) -> List[Tuple[str, str, str]]:
    """(name, count-sql, delete-sql) per removed-module property prefix,
    then per removed-module property KEY.

    Both halves matter: a key the overlay classifies by name rather than
    by prefix is dropped from oscar.properties, and without the exact
    match its `property` table row survives the import and CARLOS reads
    it back — the same drift the prefix list was derived to close, one
    level down."""
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
    for key in keys:
        # exact, so a key that is a prefix of a live one cannot take it
        literal = "'{0}'".format(_sql_str(key))
        out.append((
            key,
            "SELECT COUNT(*) FROM `{0}`.property WHERE name = {1}".format(
                dst_schema, literal),
            "DELETE FROM `{0}`.property WHERE name = {1}".format(
                dst_schema, literal)))
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
    """Prevention types in the target that CARLOS has no configuration
    for, with their row counts.

    Compared under BINARY, like the rewrite itself: a code differing
    from a known one only by case does not render either, so it belongs
    on this list. NULL is reported under its own label rather than
    dropped by the comparison."""
    known_list = ", ".join("'{0}'".format(_sql_str(k)) for k in known)
    # BINARY like the rewrite: a code differing from a known one only by
    # case is not rendered either, so it must be listed for review
    # MIN() keeps the statement valid under ONLY_FULL_GROUP_BY (this read
    # runs through the plain client, under the host's own sql_mode)
    # a NULL type renders as unconfigured too: listed under its own label
    return ("SELECT IFNULL(MIN(prevention_type), '<NULL>'), COUNT(*) FROM "
            "`{0}`.preventions WHERE prevention_type IS NULL OR BINARY "
            "prevention_type NOT IN ({1}) GROUP BY BINARY prevention_type "
            "ORDER BY 1".format(dst_schema, known_list))


def rtl_rows_sql(dst_schema: str) -> str:
    """Every eForm that IS the Rich Text Letter (by its HTML title; the
    2010 O19 seed named the row `letter`, the 2012 update `Rich Text
    Letter`): fid, form_name, status, subject, carries the 2026.3.0
    marker, still calls a dead attachment route."""
    dead = " OR ".join("form_html LIKE '%{0}%'".format(_sql_str(r))
                       for r in RTL_DEAD_ROUTES)
    # the 7th column is the scripts' own WHERE, evaluated by the database
    # under the column collation (case-insensitive, trailing spaces
    # ignored) so the planner and the scripts agree on "canonical"
    # 8th column: calls the RptByExample.do sink (what makes a legacy
    # copy unsafe, as opposed to merely RTL-derived)
    return ("SELECT fid, form_name, status, subject, "
            "form_html LIKE '%{1}%', ({3}), ({4}), form_html LIKE '%{5}%' "
            "FROM `{0}`.eform WHERE form_html LIKE '%{2}%' OR ({4}) ORDER BY "
            "fid".format(dst_schema, RTL_VERSION_MARKER, RTL_TITLE_MARKER,
                         dead, RTL_CANONICAL_PREDICATE, RTL_SINK_MARKER))


def rtl_disable_statement(dst_schema: str, fid: str) -> str:
    """Disables one eForm by fid. `fid` is coerced through `int`, so no
    caller can steer this statement with a value read from the
    database."""
    return ("UPDATE `{0}`.eform SET status = 0 WHERE fid = {1}".format(
        dst_schema, int(fid)))


def is_rtl_canonical(row: Sequence[str]) -> bool:
    """A row the packaged scripts can address: named `Rich Text Letter`
    with a subject starting `Rich Text Letter Generator`. The database's
    verdict (7th column of rtl_rows_sql, its own collation) wins; the
    Python test is the fallback for shorter rows."""
    if len(row) > 6:
        return str(row[6]) == "1"
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

    An RTL-titled row that is not the canonical `Rich Text Letter` is
    disabled when it is the legacy `letter` row or still calls the
    RptByExample.do sink or a dead attach route — never renamed or
    deleted; a clinic copy that carries neither is a clinic form and is
    only reported (names go to the private file). A canonical row that
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
                # do not assert WHY it is off: retiring an eForm is a soft
                # delete (status 0), so this is equally the O19 seed's
                # shipped-disabled row and a template the clinic replaced
                # and retired. Either way the enable script matches on name
                # and subject with no status term, so it must be switched
                # back off after the scripts run
                notes.append("fid {0}: disabled in the clinic's O19 (the O19 "
                             "seed ships it disabled, or the clinic retired "
                             "it) — the packaged scripts match on name and "
                             "subject whatever the status, so it is "
                             "modernised but left disabled; enable it in "
                             "Administration > eForms if the clinic wants "
                             "it".format(fid))
            if RTL_MODERNIZE_SCRIPT in scripts:
                notes.append("fid {0}: form_html replaced by the 2026.3.0 "
                             "build (clinic edits to the template are not "
                             "kept; the original row stays in the staging "
                             "schema until --cleanup)".format(fid))
        elif _fold(form_name) == _fold(RTL_FORM_NAME):
            notes.append("fid {0}: named 'Rich Text Letter' but its subject "
                         "does not start with '{1}' — the packaged scripts "
                         "cannot address it; review by hand".format(
                             fid, RTL_SUBJECT_PREFIX))
        elif (_fold(form_name) == RTL_LEGACY_FORM_NAME
              or (len(row) > 5 and str(row[5]) == "1")
              or (len(row) > 7 and str(row[7]) == "1")):
            if str(status) != "0":
                disable.append(fid)
            notes.append("fid {0}: legacy Rich Text Letter form disabled "
                         "(RptByExample.do sink or dead attach route; "
                         "superseded by the 2026.3.0 form)".format(fid))
        else:
            notes.append("fid {0}: Rich Text Letter-derived clinic form "
                         "(no sink, no dead route) left as it is — review"
                         .format(fid))
    if not any(is_rtl_canonical(r) for r in rows):
        notes.append("no addressable 'Rich Text Letter' row: the v1 seed is "
                     "applied before the fixups — the clinic gets a new, "
                     "ENABLED Rich Text Letter eForm")
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
    roles = {_fold(r[0]) for r in query(
        "SELECT role_name FROM `{0}`.secRole".format(dst_schema))}
    for name, _ in GUARANTEED_ROLES:
        if _fold(name) in roles:
            ok.append("role '{0}' present".format(name))
        else:
            problems.append("role '{0}' missing from secRole".format(name))

    # every active assignment's grants must be findable by the app, which
    # matches role names EXACTLY while the database matches them
    # case-insensitively; step 1b aligns the spellings, and a non-zero
    # count here means a provider can log in and open nothing
    drift = n(role_spelling_drift_sql(dst_schema))
    if drift:
        problems.append(
            "{0} active role assignment(s) name a role whose privilege "
            "rows carry a different spelling — CARLOS matches role names "
            "exactly, so those grants are inert".format(drift))
    else:
        ok.append("role-name spellings agree across secRole, secUserRole "
                  "and secObjPrivilege")

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

    # every chart reads these on every open; a row naming a table CARLOS
    # removed makes the notes pane answer HTTP 500 (EctFormData)
    broken = query(encounter_forms_missing_tables_sql(dst_schema))
    if broken:
        problems.append(
            "{0} encounterForm row(s) name a form table this schema does "
            "not have — every chart's notes pane would fail to load"
            .format(len(broken)))
        private.extend("encounterForm names a missing table: {0} ({1})"
                       .format(r[0], r[1]) for r in broken)
    else:
        ok.append("every encounterForm row names a table that exists")

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
                 "'1' AND p.provider_no <> '{1}' AND NOT EXISTS (SELECT 1 "
                 "FROM `{0}`.provider_facility pf WHERE pf.provider_no = "
                 "p.provider_no)".format(dst_schema, SYSTEM_PROVIDER))
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
        "SELECT p.provider_no FROM `{0}`.provider p WHERE p.status = '1' AND "
        "p.provider_no <> '{1}' AND EXISTS (SELECT 1 FROM `{0}`.security s "
        "WHERE s.provider_no = p.provider_no) AND NOT EXISTS (SELECT 1 FROM "
        "`{0}`.secUserRole ur WHERE ur.provider_no = p.provider_no AND "
        "ur.activeyn = 1) ORDER BY p.provider_no".format(
            dst_schema, SYSTEM_PROVIDER))
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
    canonical = [r for r in rtl_rows if is_rtl_canonical(r)]
    # count ENABLED rows only. Retiring an eForm is a SOFT delete
    # (EFormUtil.delEForm sets status 0) and CARLOS's own same-name guard
    # counts live rows only, so a clinic that replaced its Rich Text
    # Letter template legitimately holds a retired row beside the live
    # one. Counting retired rows here failed the whole import on that
    # normal clinic — and unclearably, because the predicate is
    # status-blind: disabling "the duplicate" left the count where it was
    enabled = [r for r in canonical if str(r[2]) != "0"]
    retired = [r for r in canonical if str(r[2]) == "0"]
    if len(enabled) > 1:
        # the v1 seed ran twice (it is a bare INSERT): row parity tolerates
        # one synthesised eform row, never two live identical forms
        problems.append("{0} ENABLED Rich Text Letter rows addressable by "
                        "the packaged scripts (fid {1}) — the v1 seed was "
                        "applied more than once; disable all but one in "
                        "Administration > eForms and --resume".format(
                            len(enabled),
                            ", ".join(str(r[0]) for r in enabled)))
    elif not enabled and canonical:
        advisories.append("every Rich Text Letter row the packaged scripts "
                          "address is retired (disabled) in this clinic — "
                          "left retired; enable one in Administration > "
                          "eForms if the clinic wants the form")
    elif rtl_current(enabled):
        # `enabled`, not every row: a clinic that retired an already
        # modernised template and kept an OLD one live would otherwise be
        # told "at 2026.3.0" on the strength of the row nobody can use
        ok.append("Rich Text Letter at 2026.3.0 with live attachment routes")
    else:
        advisories.append("no Rich Text Letter eForm at 2026.3.0 with live "
                          "attachment routes — apply the packaged RTL "
                          "scripts by hand before go-live (see the roles: "
                          "Rich Text Letter report line)")
    if retired and enabled:
        # not a problem: the clinic retired it on purpose and the step put
        # it back to status 0. Say so, because the packaged scripts have no
        # status term and rewrote its form_html and subject on the way past
        advisories.append("{0} retired (disabled) Rich Text Letter row(s) "
                          "beside the live one (fid {1}) — left retired; "
                          "the packaged scripts address rows by name and "
                          "subject whatever the status, so their form_html "
                          "and subject now carry the 2026.3.0 build too "
                          "(the originals stay in the staging schema until "
                          "--cleanup)".format(
                              len(retired),
                              ", ".join(str(r[0]) for r in retired)))
    return ok, problems, advisories, private


# --- driver -----------------------------------------------------------------

def role_spelling_drift_sql(dst_schema: str) -> str:
    """Active role assignments whose grants CARLOS would not find.

    CARLOS resolves a grant with `Properties.containsKey` over the
    provider's role names (OscarRoleObjectPrivilege.checkPrivilege), i.e.
    EXACT Java string equality — while the database compares role names
    under utf8mb4_general_ci, which folds case and trailing blanks. The
    privilege merge therefore keeps the CARLOS seed's `nurse` rows and
    drops the clinic's `Nurse` ones, and secUserRole (replace_seed) still
    says `Nurse`: every grant on that role becomes inert and the provider
    can log in but open nothing."""
    # DISTINCT ur.id: one role can carry many differently-spelled
    # privilege rows, and the figure this returns is reported as the
    # number of affected ASSIGNMENTS, not of grants
    return ("SELECT COUNT(DISTINCT ur.id) FROM `{0}`.secUserRole ur JOIN "
            "`{0}`.secObjPrivilege p ON p.roleUserGroup = ur.role_name "
            "WHERE ur.activeyn = 1 AND BINARY p.roleUserGroup <> "
            "BINARY ur.role_name".format(dst_schema))


def role_spelling_statements(dst_schema: str) -> List[str]:
    """Canonicalise every role-name spelling onto the one secRole holds.

    Safe and idempotent: secRole.role_name is a case-insensitive UNIQUE
    key and secObjPrivilege's PRIMARY KEY (roleUserGroup, objectName) is
    case-insensitive too, so at most one spelling of a given role can
    exist in either table and the update cannot collide. Re-running it
    matches nothing."""
    return [
        "UPDATE `{0}`.secObjPrivilege p JOIN `{0}`.secRole r ON "
        "r.role_name = p.roleUserGroup SET p.roleUserGroup = r.role_name "
        "WHERE BINARY p.roleUserGroup <> BINARY r.role_name"
        .format(dst_schema),
        "UPDATE `{0}`.secUserRole ur JOIN `{0}`.secRole r ON "
        "r.role_name = ur.role_name SET ur.role_name = r.role_name "
        "WHERE BINARY ur.role_name <> BINARY r.role_name"
        .format(dst_schema),
    ]


def comma_named_roles_sql(dst_schema: str) -> str:
    """Role names carrying a comma. CARLOS splits a provider's role list
    on ',' before the exact match, so such a role can never match and
    grants nothing — reported, never rewritten (renaming a role is the
    clinic's decision)."""
    return ("SELECT role_name FROM `{0}`.secRole WHERE role_name LIKE "
            "'%,%' ORDER BY role_name".format(dst_schema))


class RolesRun(object):
    """The handles every step of the roles post-step shares.

    The six helpers carried here are CLOSURES, not free functions:
    ``mark`` and ``plan`` write to the ledger and call ``save()``, and
    ``append_private`` appends to this run's roles-details.txt. Carrying
    them on the run object is what lets each step below keep the body it
    had inside run_roles, unchanged, while becoming separately testable.
    """

    def __init__(self, ctx, query, plain, report, state_dir, ledger,
                 appended, n, count, mark, record_appended, plan,
                 append_private, admin_provider_no=None):
        #: the break-glass account this import created, or None on a run
        #: that did not create one. The program step needs it: the
        #: account has to reach the charts the operator is told to smoke
        #: test.
        self.admin_provider_no = admin_provider_no
        self.query = query
        self.plain = plain
        self.src = ctx["src_schema"]
        self.dst = ctx["target_db"]
        self.arch = ctx["archive_schema"]
        self.report = report
        self.state_dir = state_dir
        self.ledger = ledger
        self.appended = appended
        self.n = n
        self.count = count
        self.mark = mark
        self.record_appended = record_appended
        self.plan = plan
        self.append_private = append_private


def roles_bind_role_templates(
        run: 'RolesRun', ctx) -> Tuple[Dict[str, str], Dict[str, str]]:
    """Bind --role-template to the ledger, and return the mapping to use.

    Returns ``(requested, overrides)``: what this invocation asked for,
    and what the backfill will actually apply. The flag stays changeable
    on a resume until the backfill has DECIDED on it -- after that a
    changed template would graft a second stock role's grants onto a
    custom role, so it is refused."""
    from .util import die
    report = run.report
    ledger = run.ledger
    # 0. --role-template: the mapping is bound to the ledger once the
    #    backfill has DECIDED on it (validated, planned); until then a
    #    resume may add or change the flag, and the change is reported
    requested = dict(ctx.get("role_templates") or {})
    recorded = ledger.get("role_templates")
    bound = "backfill_plan" in ledger or bool(ledger.get("backfill"))
    if requested and recorded is not None \
            and not same_role_templates(requested, recorded):
        if bound:
            die("roles: this import decided the custom-role templates with "
                "--role-template {0}; the resume passes {1}. Resume with the "
                "recorded mapping (or without the flag) — a changed "
                "template would graft a second stock role's grants onto "
                "the custom role.".format(_fmt_templates(recorded),
                                          _fmt_templates(requested)))
        report("roles: --role-template changed from {0} to {1} before the "
               "backfill decided — the new mapping is used".format(
                   _fmt_templates(recorded), _fmt_templates(requested)))
    overrides = dict(requested or recorded or {})
    return requested, overrides


def roles_append_carlos_roles(run: 'RolesRun') -> None:
    """Add the roles CARLOS requires to the clinic's catalogue.

    An O19 assignment to a role that had no secRole row there granted
    nothing; it carries the CARLOS seed's grants now, so every such live
    assignment is itemised in roles-details.txt."""
    query = run.query
    plain = run.plain
    src = run.src
    dst = run.dst
    arch = run.arch
    report = run.report
    ledger = run.ledger
    appended = run.appended
    count = run.count
    mark = run.mark
    record_appended = run.record_appended
    plan = run.plan
    append_private = run.append_private
    # 1. hard-coded role names, CARLOS-only roles
    if not ledger.get("roles_appended"):
        # persisted BEFORE the write: on a resume the appends are
        # idempotent no-ops, so the count read afterwards is the
        # post-write one and the delta stays this run's real figure
        before = plan("secRole_before", lambda: count("secRole"))
        for sql in guaranteed_role_statements(dst):
            query(sql)
        query(carlos_role_append_statement(dst, arch))
        added = count("secRole") - before
        record_appended("secRole")
        if appended["secRole"]:
            names = [r[0] for r in plain(
                "SELECT role_name FROM `{0}`.secRole WHERE role_name NOT IN "
                "(SELECT role_name FROM `{1}`.secRole) ORDER BY role_name"
                .format(dst, src))]
            # an O19 assignment to such a role granted nothing there (no
            # secRole row) and carries the CARLOS seed's grants now
            dangling = plain(dangling_role_assignments_sql(dst, names))
            if dangling and not ledger.get("roles_appended_listed"):
                append_private(["active assignments to CARLOS-only roles "
                                "(granted nothing in O19, seed grants now): "
                                + ", ".join("{0}={1}".format(r[0], r[1])
                                            for r in dangling)])
                mark("roles_appended_listed")
            report("roles: {0} CARLOS role(s) added to the clinic's "
                   "catalogue ({1} this run): {2}{3}".format(
                       appended["secRole"], added, ", ".join(names),
                       ("; {0} active assignment(s) to them now carry the "
                        "CARLOS seed's grants (see roles-details.txt)"
                        .format(len(dangling))) if dangling else ""))
        mark("roles_appended")


def roles_align_role_spelling(run: 'RolesRun') -> None:
    """One spelling per role, everywhere.

    CARLOS matches role names exactly and the database matches them
    case-insensitively; the privilege merge opens the gap. Aligning adds
    and removes no grant. Comma-bearing names can never match at all
    (CARLOS splits a provider's role list on ","), so they are named for
    the operator rather than renamed under them."""
    query = run.query
    plain = run.plain
    dst = run.dst
    report = run.report
    ledger = run.ledger
    n = run.n
    mark = run.mark
    plan = run.plan
    append_private = run.append_private
    # 1b. one spelling per role, everywhere (see role_spelling_drift_sql:
    #     the app matches role names exactly, the database matches them
    #     case-insensitively, and the privilege merge creates the gap)
    if not ledger.get("role_spelling"):
        # PLANNED, not just measured: the UPDATEs below are what make
        # `role_spelling_drift_sql` answer 0, so a crash after they
        # commit and before the report line loses the only record that
        # N assignments carried an unresolvable spelling. This step's
        # own mark is five statements further down.
        drift = plan("role_spelling_drift",
                     lambda: n(role_spelling_drift_sql(dst)))
        for sql in role_spelling_statements(dst):
            query(sql)
        if drift:
            report("roles: {0} active assignment(s) named a role whose "
                   "privilege rows carried a different spelling (CARLOS "
                   "matches role names exactly, the database does not) — "
                   "all spellings aligned to the clinic's secRole "
                   "catalogue; no grant was added or removed"
                   .format(drift))
        commas = [r[0] for r in plain(comma_named_roles_sql(dst)) if r]
        if commas and not ledger.get("role_comma_listed"):
            append_private(["role names carrying a comma (CARLOS splits a "
                            "provider's role list on ',', so these grant "
                            "nothing): " + ", ".join(commas)])
            mark("role_comma_listed")
        if commas:
            report("roles: {0} role name(s) contain a comma and can never "
                   "match in CARLOS (named in roles-details.txt) — rename "
                   "them in Administration > Roles after go-live"
                   .format(len(commas)))
        mark("role_spelling")


def roles_facility_links(run: 'RolesRun') -> None:
    """Facility/clinic guarantees and provider_facility links.

    The two refusals here are backstops for run_etl pre-checks that
    already passed against the STAGED dump, so if one fires the source
    was fine and the copy lost the row: an import defect, not a clinic
    condition -- which is why they do not say "re-export"."""
    from .util import die
    query = run.query
    dst = run.dst
    report = run.report
    state_dir = run.state_dir
    ledger = run.ledger
    appended = run.appended
    n = run.n
    count = run.count
    mark = run.mark
    plan = run.plan
    record_appended = run.record_appended
    # 2. facility / clinic guarantees, facility links (run_etl pre-checks
    #    the same conditions against staging before the first write; this
    #    is the backstop)
    if not ledger.get("facility_links"):
        # These are backstops for pre-checks that already ran against the
        # STAGED dump before the first write. If one fires, the source
        # was fine and the copy lost the row: an import defect, not a
        # clinic condition — so "fix the source and re-export" would
        # send the operator round a loop that cannot change the outcome.
        if not n(enabled_facility_count_sql(dst)):
            die("roles: the copy did not carry an enabled Facility row "
                "into {0}, although the staged dump had one (checked "
                "before the first write). CARLOS cannot log anyone in "
                "without it. This is an import defect, not a clinic "
                "condition: re-exporting the same source will not change "
                "it. Roll back and send {1}/report.txt.".format(
                    dst, state_dir))
        if not n(clinic_count_sql(dst)):
            die("roles: the copy did not carry a `clinic` row into {0}, "
                "although the staged dump had one (checked before the "
                "first write); letterheads, requisitions and "
                "consultations dereference it. This is an import defect, "
                "not a clinic condition. Roll back and send "
                "{1}/report.txt.".format(dst, state_dir))
        before = plan("provider_facility_before",
                      lambda: count("provider_facility"))
        query(provider_facility_statement(dst))
        added = count("provider_facility") - before
        record_appended("provider_facility")
        if appended["provider_facility"]:
            report("roles: {0} active provider(s) linked to the first "
                   "enabled facility (they had no provider_facility row; "
                   "{1} this run)".format(appended["provider_facility"],
                                          added))
        mark("facility_links")


def roles_normalise_activeyn(run: 'RolesRun') -> None:
    """Activate secUserRole rows of live accounts, and report every one.

    Admin assignments are the deliberate exception: CARLOS treats a NULL
    admin row as inactive on purpose. The lists are decided and written
    to the private file BEFORE the UPDATE, so a crash in between cannot
    lose the record of what changed."""
    query = run.query
    plain = run.plain
    dst = run.dst
    report = run.report
    ledger = run.ledger
    n = run.n
    mark = run.mark
    plan = run.plan
    append_private = run.append_private
    # 3. activeyn normalisation (user decision: activate rows of live
    #    accounts, report every one) — except admin assignments, which
    #    CARLOS deliberately treats as inactive when NULL. The lists are
    #    decided and written to the private file BEFORE the UPDATE.
    if not ledger.get("activeyn"):
        decided = plan("activeyn", lambda: {
            "activated": [[r[0], r[1]] for r in
                          plain(activeyn_candidates_sql(dst))],
            "admin_left": [r[0] for r in plain(activeyn_admin_left_sql(dst))],
        })
        candidates = decided["activated"]
        admin_left = decided["admin_left"]
        if (candidates or admin_left) and not ledger.get("activeyn_listed"):
            lines = []
            if candidates:
                lines.append("secUserRole.activeyn set to 1 for active "
                             "accounts (provider=role): " + ", ".join(
                                 "{0}={1}".format(p, r)
                                 for p, r in candidates))
            if admin_left:
                lines.append("admin assignments with activeyn NULL left "
                             "inactive (activate in Administration if "
                             "intended): " + ", ".join(admin_left))
            append_private(lines)
            mark("activeyn_listed")
        if candidates:
            query(activeyn_update_statement(dst))
        remaining = n(activeyn_null_remaining_sql(dst))
        by_role: Dict[str, int] = {}
        for _p, role in candidates:
            by_role[role] = by_role.get(role, 0) + 1
        report("roles: secUserRole.activeyn was NULL for {0} assignment(s) "
               "of active accounts — set to 1 (CARLOS counts only "
               "activeyn = 1){1}; {2} NULL admin assignment(s) of active "
               "accounts left inactive (CARLOS treats a NULL admin row as "
               "inactive on purpose; listed in roles-details.txt); {3} "
               "NULL row(s) of inactive accounts left as they were".format(
                   len(candidates),
                   (": " + ", ".join("{0} x{1}".format(r, c) for r, c in
                                     sorted(by_role.items())))
                   if by_role else "",
                   len(admin_left), remaining))
        mark("activeyn", {"activated_assignments": len(candidates),
                          "admin_left_inactive": len(admin_left),
                          "null_rows_left": remaining})


def roles_program_memberships(run: 'RolesRun') -> None:
    """The OSCAR program and its memberships.

    Runs after activeyn normalisation on purpose: role_id follows the
    provider's ACTIVE role, so the memberships would be built from
    pre-normalisation state otherwise."""
    query = run.query
    plain = run.plain
    dst = run.dst
    report = run.report
    ledger = run.ledger
    appended = run.appended
    n = run.n
    count = run.count
    mark = run.mark
    record_appended = run.record_appended
    plan = run.plan
    append_private = run.append_private
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
                            "clinic role (no active role with a secRole "
                            "row): " + ", ".join(fallback)])
            mark("program_listed")
        with_role, least = membership_statements(dst)
        # Two writes, so two persisted readings rather than one: the
        # counts either side of a write are what the report's "this run
        # created N" lines are, and on a resume both INSERTs match
        # nothing. `missing` already comes from the ledger, so without
        # these the line reads "5 providers had none — this run created
        # 0 and 0", which is the opposite of what happened. Each reading
        # is taken from the database immediately AFTER its write and
        # persisted there, which is the rule the rest of this module
        # follows.
        before = plan("program_before", lambda: count("program_provider"))
        query(with_role)
        after_role = plan("program_after_role",
                          lambda: count("program_provider"))
        query(least)
        added_role = after_role - before
        added_least = count("program_provider") - after_role
        admin_line = ""
        if run.admin_provider_no:
            # after the two general statements: the admin's role_id is
            # read from the membership they create for it
            unreachable = plan(
                "program_admin", lambda: n(admin_unreachable_programs_sql(
                    dst, run.admin_provider_no)))
            query(admin_membership_statement(dst, run.admin_provider_no))
            left = n(admin_unreachable_programs_sql(
                dst, run.admin_provider_no))
            admin_line = ("; the break-glass administrator was given "
                          "membership of {0} program(s) holding admissions "
                          "so it can open a migrated chart{1}"
                          .format(unreachable - left,
                                  (" ({0} still unreachable)".format(left))
                                  if left else ""))
        record_appended("program_provider")
        still = n(providers_without_membership_sql(dst))
        report("roles: program '{0}' {1}; {2} active provider(s) had no "
               "program membership — this run created {3} membership "
               "row(s) carrying the provider's active role and {4} with "
               "the least-privileged clinic role (providers holding no "
               "active role with a secRole row: {5}, listed in "
               "roles-details.txt; assign a role in Administration){6}"
               .format(OSCAR_PROGRAM,
                       "created" if appended["program"] else "present",
                       missing, added_role, added_least, len(fallback),
                       ("; {0} still without membership — P7 will fail"
                        .format(still)) if still else "")
               + admin_line)
        mark("program")


def roles_backfill_custom_grants(run: 'RolesRun', overrides: Dict[str, str],
                                 requested: Dict[str, str]) -> None:
    """Backfill CARLOS-era grants onto the clinic's custom roles.

    A role the clinic invented has no CARLOS-era objects in its grants,
    because those objects did not exist in OSCAR 19. Each custom role is
    matched to the stock role it most resembles (Jaccard over the shared
    grant set, or an operator's --role-template) and given that role's
    CARLOS-era grants. The decision is planned and persisted before the
    first write."""
    from .util import die
    query = run.query
    plain = run.plain
    src = run.src
    dst = run.dst
    arch = run.arch
    report = run.report
    ledger = run.ledger
    n = run.n
    mark = run.mark
    plan = run.plan
    append_private = run.append_private
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
                "--resume (the completed sub-steps are kept; the mapping "
                "is not recorded until it validates)")
        if ledger.get("role_templates") is None \
                or not same_role_templates(overrides,
                                           ledger["role_templates"]):
            mark("role_templates", overrides)
        overrides = normalise_role_templates(overrides, customs, stock)
        lines = ["CARLOS-era objects absent from the clinic's O19 ({0}): {1}"
                 .format(len(era), ", ".join(era) or "none")]
        odd = non_role_named_roles(target_roles)
        if odd:
            # digit-named rows are most plausibly provider numbers: the
            # names go to the private file, the count to the report
            if not ledger.get("backfill_odd_listed"):
                append_private(["secRole rows named like non-role groups, "
                                "left untouched by the backfill: "
                                + ", ".join(odd)])
                mark("backfill_odd_listed")
            lines.append("{0} secRole row(s) named like non-role groups "
                         "(digits, _all, _queue.*) left untouched by the "
                         "backfill (see roles-details.txt)".format(len(odd)))

        def decide():
            chosen, skipped, admin_held = {}, {}, {}
            for custom in customs:
                if custom in overrides:
                    chosen[custom] = [overrides[custom], -1.0]
                else:
                    template, score = choose_template(
                        custom, stage_rows, seed_rows,
                        o19map_schema.ROLE_TEMPLATE_MIN_JACCARD, stock)
                    if template is None:
                        skipped[custom] = score
                    elif (template.lower() == ADMIN_ROLE
                          and score < ADMIN_TEMPLATE_MIN_JACCARD):
                        # administrator sub-grants are not handed out on
                        # a weak resemblance: the operator names it
                        admin_held[custom] = score
                    else:
                        chosen[custom] = [template, score]
            pending = {}
            for custom, (template, _score) in chosen.items():
                pending[custom] = n(backfill_pending_count_sql(
                    dst, arch, custom, template, era)) if era else 0
            return {"templates": chosen, "skipped": skipped,
                    "admin_held": admin_held, "pending": pending}

        decided = plan("backfill", decide)
        for custom in customs:
            if custom in decided.get("admin_held", {}):
                lines.append("custom role {0!r}: closest stock role is "
                             "'admin' (similarity {1:.2f} < {2}) — not "
                             "applied automatically; pass --role-template "
                             "'{0}=admin' to grant the administrator "
                             "objects, or grant them by hand"
                             .format(custom, decided["admin_held"][custom],
                                     ADMIN_TEMPLATE_MIN_JACCARD))
                continue
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
            if template.lower() == ADMIN_ROLE:
                why += " — admin-class template: review the grants"
            lines.append("custom role {0!r}: template {1!r} ({2}), {3} "
                         "CARLOS-era grant(s) added{4}".format(
                             custom, template, how, added, why))
        report("roles: {0} clinic-custom role(s)\n  ".format(len(customs))
               + "\n  ".join(lines))
        mark("backfill", {"templates": {c: t for c, (t, _s) in
                                        decided["templates"].items()},
                          "customs": customs})
    elif requested:
        report("roles: --role-template already applied by the run that "
               "performed the backfill (recorded: {0})".format(
                   _fmt_templates(ledger.get("role_templates") or {})))


def roles_privilege_diff(run: 'RolesRun') -> None:
    """Itemise every privilege the merge resolved, for technical review.

    Clinic grants the CARLOS seed overrode, seed grants restored on the
    clinic's own roles, seed grants on the CARLOS roles this import
    re-added, the custom-role backfill's grants, stock role appends and
    exclusions -- written to privilege-diff.txt rather than the report,
    because the list is long and root-only.

    The two seed-driven lists (re-added roles, backfill) are additions of
    ACCESS and were previously reported only as counts: the reviewer had
    to reconstruct them by diffing the o19_archive snapshot by hand."""
    from . import o19import
    plain = run.plain
    src = run.src
    dst = run.dst
    arch = run.arch
    report = run.report
    state_dir = run.state_dir
    ledger = run.ledger
    mark = run.mark
    # 6. privilege diff (clinic grants CARLOS's seed overrode), restored
    #    seed grants, seed grants on re-added CARLOS roles, backfilled
    #    custom-role grants, stock-role appends and exclusions — all
    #    itemised for the technical review
    if not ledger.get("diff"):
        diff = plain(privilege_diff_sql(src, arch))
        restored = plain(restored_seed_grants_sql(src, arch))
        readded = plain(appended_role_seed_grants_sql(src, dst, arch))
        # the backfill sub-step runs before this one and records which
        # roles it treated as custom; nothing was backfilled without it
        customs = list((ledger.get("backfill") or {}).get("customs") or [])
        backfilled = plain(backfilled_custom_grants_sql(src, dst, customs))
        appends = plain(stock_role_appends_sql(
            src, arch, o19map_schema.STOCK_ROLE_NAMES))
        excluded_sql = excluded_grants_sql(src)
        excluded = plain(excluded_sql) if excluded_sql else []
        text = ["clinic grants overridden by the CARLOS seed on the same "
                "(role, object): {0}".format(len(diff)),
                "role | object | clinic privilege/priority -> CARLOS"]
        for r in diff:
            text.append("{0} | {1} | {2}/{3} -> {4}/{5}".format(*r))
        text += ["", "CARLOS seed grants on roles the clinic has but whose "
                     "(role, object) the clinic's matrix did not hold — the "
                     "role can do more than in O19: {0}".format(
                         len(restored)),
                 "role | object | privilege/priority"]
        for r in restored:
            text.append("{0} | {1} | {2}/{3}".format(*r))
        text += ["", "CARLOS seed grants on roles this import RE-ADDED to the "
                     "clinic's catalogue — these granted nothing in O19 (no "
                     "secRole row) and are live now on every assignment the "
                     "clinic kept: {0}".format(len(readded)),
                 "role | object | privilege/priority"]
        for r in readded:
            text.append("{0} | {1} | {2}/{3}".format(*r))
        text += ["", "CARLOS-era grants the backfill added to the clinic's "
                     "custom roles from their templates — access no O19 role "
                     "held: {0}".format(len(backfilled)),
                 "role | object | privilege/priority"]
        for r in backfilled:
            text.append("{0} | {1} | {2}/{3}".format(*r))
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
        admin_appends = [r for r in appends
                         if r[1].lower().startswith("_admin")]
        report("roles: {0} clinic grant(s) differ from the CARLOS seed on "
               "the same (role, object) — CARLOS's value stands; {1} seed "
               "grant(s) on the clinic's roles have no clinic row (the "
               "role can do more than in O19); {5} seed grant(s) on the "
               "CARLOS roles this import re-added are live now; {6} "
               "CARLOS-era grant(s) were backfilled onto clinic-custom "
               "roles; {2} clinic grant(s) on stock roles have no CARLOS "
               "seed row and were appended{3}; {4} grant(s) on objects no "
               "CARLOS code checks not carried — all itemised in "
               "privilege-diff.txt for the technical review".format(
                   len(diff), len(restored), len(appends),
                   (" (administration objects: " + ", ".join(
                       "{0}/{1}".format(r[0], r[1]) for r in admin_appends)
                    + ")") if admin_appends else "",
                   len(excluded), len(readded), len(backfilled)))
        mark("diff", {"overridden": len(diff), "restored": len(restored),
                      "appended": len(appends), "excluded": len(excluded),
                      "readded_roles": len(readded),
                      "backfilled": len(backfilled)})


def roles_prune_property_keys(run: 'RolesRun') -> None:
    """Remove property keys belonging to modules CARLOS no longer has."""
    query = run.query
    dst = run.dst
    report = run.report
    ledger = run.ledger
    n = run.n
    mark = run.mark
    plan = run.plan
    # 7. removed-module keys in the property table
    if not ledger.get("property_pruned"):
        from . import o19_preflight
        stmts = property_prune_statements(
            dst, o19_preflight.DROPPED_PROP_PREFIXES,
            o19_preflight.DROPPED_PROP_KEYS)

        def plan_prune():
            out = []
            for prefix, count_sql, _d in stmts:
                c = n(count_sql)
                if c:
                    out.append([prefix, c])
            return out

        pruned = plan("property_pruned", plan_prune)
        for prefix, _c, delete_sql in stmts:
            if any(p == prefix for p, _n in pruned):
                query(delete_sql)
        if pruned:
            report("roles: property rows of removed modules pruned: "
                   + ", ".join("{0} ({1})".format(p, c) for p, c in pruned))
        mark("property_pruned", {"pruned": pruned})


#: encounterForm rows whose `form_table` names a table the TARGET does
#: not have. CARLOS's encounter reads every one of them on every chart
#: (`EctFormData.getGroupedPatientFormsFromAllTables`), and a missing
#: table is not skipped: the SQLException becomes a PersistenceException
#: and `CaseManagementView` answers HTTP 500, so the clinical NOTES pane
#: of every chart fails. Measured on a migrated clinic, whose OSCAR 19
#: `encounterForm` listed seven forms CARLOS removed (`formAdf`,
#: `formAR`, `formONAR`, ...).
#:
#: The comparison is BINARY on purpose: MariaDB table names are
#: case-sensitive on Linux, so a row naming `formadf` when the table is
#: `formAdf` is a broken row and must be treated as one.
#: One definition, shared with `row_parity`: a check that tolerated a
#: different set from the one the prune removes would either hide a row
#: the prune missed or fail on a row it removed.
def _encounter_form_missing(dst_schema: str) -> str:
    return o19etl.pruned_encounter_form_predicate(dst_schema, alias="e")


def encounter_forms_missing_tables_sql(dst_schema: str) -> str:
    """The form_table/form_name pairs pointing at a table CARLOS does not
    have — what the prune removes, and what P7 requires to be empty."""
    return ("SELECT e.form_table, e.form_name FROM `{0}`.encounterForm e "
            "WHERE {1} ORDER BY e.form_table"
            .format(dst_schema, _encounter_form_missing(dst_schema)))


def encounter_form_prune_statements(dst_schema: str, archive_schema: str
                                    ) -> Tuple[str, str]:
    """(archive-sql, delete-sql) for those rows.

    Archived before deletion, not just deleted: the row is the clinic's
    own menu entry and the form's DATA is preserved as
    `import_archived_<form table>`, so the pointer to it is kept too and
    nothing about the removed form is orphaned.

    The WHOLE row is archived, not the pair the report happens to print.
    `encounterForm` is (form_name, form_value, form_table, hidden) with
    `form_value` -- the URL the menu entry points at -- as its PRIMARY
    KEY, so archiving only (form_table, form_name) would keep a record
    that the entry existed while losing what it did, and requirement B
    is that nothing the import removes is left unrecoverable. Keying the
    NOT EXISTS guard on `form_value` for the same reason: two entries
    can share a table and a name and differ only in the URL, and a guard
    on the pair would archive the first and silently drop the second.

    Both statements are idempotent -- the INSERT is guarded by NOT
    EXISTS on the archive and the DELETE's own predicate is false once
    the rows are gone.

    The guard is form_value equality and NOTHING ELSE, deliberately.
    Rows an older carlos-ctl archived hold NULL there, and matching them
    on their (form_table, form_name) pair instead -- which an earlier
    version of this function did -- suppresses EVERY live entry sharing
    that pair, not the one row the legacy row stood for. The DELETE that
    follows is not similarly narrowed, so those entries left with no
    archive row at all: requirement B inverted, and worse than the
    duplicate the pair-match was added to avoid.

    `encounter_form_backfill_statement` is what makes the narrow guard
    safe: it gives a legacy row its form_value back wherever that is
    UNAMBIGUOUS. Where it is not, the legacy row keeps its NULL, every
    live entry archives in full, and the archive carries one harmless
    duplicate. Duplicates are recoverable; a deleted row with no archive
    row is not, so the ambiguity resolves toward the archive every
    time."""
    missing = _encounter_form_missing(dst_schema)
    archive = ("INSERT INTO `{1}`.encounterForm__pruned (form_table, "
               "form_name, form_value, hidden) SELECT e.form_table, "
               "e.form_name, e.form_value, e.hidden FROM "
               "`{0}`.encounterForm e WHERE {2} AND NOT EXISTS (SELECT 1 "
               "FROM `{1}`.encounterForm__pruned a WHERE a.form_value = "
               "e.form_value)"
               .format(dst_schema, archive_schema, missing))
    delete = ("DELETE FROM `{0}`.`encounterForm` WHERE {1}"
              .format(dst_schema,
                      o19etl.pruned_encounter_form_predicate(dst_schema,
                                                             alias="")))
    return archive, delete


def encounter_form_archive_ddl(archive_schema: str) -> str:
    """The archive table the prune writes into.

    One column per column of `encounterForm`, widened rather than copied
    exactly (the source is varchar(30)/varchar(255)/varchar(50)/int(5)):
    an archive that truncated a clinic's own value would defeat its own
    purpose, and a vendor fork with a wider column is not a reason to
    lose the row."""
    return ("CREATE TABLE IF NOT EXISTS `{0}`.encounterForm__pruned ("
            "form_table VARCHAR(255), form_name VARCHAR(255), "
            "form_value VARCHAR(255), hidden INT) "
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4".format(archive_schema))


#: Columns `encounter_form_prune_statements` writes, in the order the
#: DDL declares them.
ENCOUNTER_FORM_ARCHIVE_COLUMNS = ("form_table", "form_name",
                                  "form_value", "hidden")


def encounter_form_archive_upgrades(plain, archive_schema: str) -> List[str]:
    """`ALTER TABLE` statements bringing an EXISTING archive table up to
    the current column set, or [] when there is nothing to add.

    `CREATE TABLE IF NOT EXISTS` is a no-op against a table that already
    exists, columns and all -- so a workspace whose archive table was
    created by an earlier carlos-ctl (which wrote only form_table and
    form_name) would meet ERROR 1054 on the INSERT and the resumed import
    would stop, mid-roles, with no way forward but a restore. Adding the
    missing columns is safe in both directions: the rows already archived
    keep their values and gain NULLs, which is honest -- that run did not
    record them."""
    # the READ channel: `query` is the driver's write path and every SQL
    # it issues has to be idempotent (test_roles_driver pins that), while
    # this is an introspection whose answer decides whether a write is
    # needed at all
    rows = plain(
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE "
        "TABLE_SCHEMA = '{0}' AND TABLE_NAME = 'encounterForm__pruned'"
        .format(_sql_str(archive_schema)))
    if not rows:
        return []          # no table yet: the CREATE builds it in full
    have = {str(r[0]).lower() for r in rows}
    types = {"form_table": "VARCHAR(255)", "form_name": "VARCHAR(255)",
             "form_value": "VARCHAR(255)", "hidden": "INT"}
    return ["ALTER TABLE `{0}`.encounterForm__pruned ADD COLUMN `{1}` {2}"
            .format(archive_schema, col, types[col])
            for col in ENCOUNTER_FORM_ARCHIVE_COLUMNS
            if col.lower() not in have]


def encounter_form_backfill_statement(dst_schema: str,
                                      archive_schema: str) -> str:
    """Give a legacy archive row its `form_value` back, where exactly one
    live menu entry carries its (form_table, form_name) pair.

    Rows written before `form_value` was a column hold NULL in it after
    `encounter_form_archive_upgrades` widens the table, and the archive
    guard matches on form_value alone. Restoring the value where it is
    unambiguous is what keeps a resumed run from archiving those rows a
    second time -- without the pair-matching that would suppress, and
    then silently delete, sibling entries sharing the pair.

    BOTH missing columns are restored, not just the one the guard reads.
    Filling `form_value` alone makes the live row look archived -- the
    guard skips it, the DELETE removes it -- while the archive row still
    holds NULL in `hidden`, so the clinic's visibility flag is gone with
    no row carrying it. Requirement B is about the whole row, and the
    guard is not the only thing the archive is for.

    Deliberately does nothing when the pair is ambiguous (two live
    entries, same table and name, different URLs). There is no way to
    tell which one the legacy row recorded, and guessing would put a
    wrong URL in the clinic's archive. Both subqueries sit under the
    same `COUNT(*) = 1` condition, so the two values always come from
    the SAME live row rather than from two independent MINs. Idempotent:
    once a row has a form_value the WHERE no longer selects it."""
    one_row = ("(SELECT COUNT(*) FROM `{0}`.encounterForm e WHERE "
               "e.form_table = a.form_table AND e.form_name = "
               "a.form_name) = 1".format(dst_schema))
    pick = ("(SELECT MIN(e.{{0}}) FROM `{0}`.encounterForm e WHERE "
            "e.form_table = a.form_table AND e.form_name = a.form_name)"
            .format(dst_schema))
    return ("UPDATE `{0}`.encounterForm__pruned a SET a.form_value = {1}, "
            "a.hidden = {2} WHERE a.form_value IS NULL AND {3}"
            .format(archive_schema, pick.format("form_value"),
                    pick.format("hidden"), one_row))


def roles_prune_encounter_forms(run: 'RolesRun') -> None:
    """Remove encounter-form menu entries pointing at removed forms.

    Without this the notes pane of EVERY chart answers HTTP 500 after a
    migration -- the single most visible thing a clinic would meet on
    the morning after cutover."""
    query = run.query
    plain = run.plain
    dst = run.dst
    arch = run.arch
    report = run.report
    ledger = run.ledger
    mark = run.mark
    plan = run.plan
    if ledger.get("encounter_forms_pruned"):
        return
    pruned = plan("encounter_forms", lambda: [
        [r[0], r[1]] for r in plain(encounter_forms_missing_tables_sql(dst))])
    if pruned:
        query(encounter_form_archive_ddl(arch))
        for upgrade in encounter_form_archive_upgrades(plain, arch):
            query(upgrade)
        # after the widening and BEFORE the guard reads form_value
        query(encounter_form_backfill_statement(dst, arch))
        archive, delete = encounter_form_prune_statements(dst, arch)
        query(archive)
        query(delete)
        report("roles: {0} encounter-form entr(ies) named a form table "
               "CARLOS removed and would have made every chart's notes "
               "pane fail; pruned and kept at {1}.encounterForm__pruned: "
               "{2}".format(len(pruned), arch,
                            ", ".join(sorted(t for t, _n in pruned))))
    mark("encounter_forms_pruned", {"pruned": pruned})


def appointments_outside_program_zero_sql(dst_schema: str) -> str:
    """COUNT of migrated appointments carrying a program id other than 0.

    Not a defect and not repaired -- reported. CARLOS's day view pins the
    program to 0 (`appointmentprovideradminday.jsp`: "Disable schedule
    view associated with the program"), and so did OSCAR 19's, so an
    appointment booked under any other program was already invisible on
    the day schedule BEFORE the migration and stays invisible after it.
    It is still in the patient's appointment history, and it is still in
    the database. An operator comparing the two systems' day views needs
    to know the count rather than discover it at go-live."""
    return ("SELECT COUNT(*) FROM `{0}`.appointment WHERE program_id IS "
            "NOT NULL AND program_id <> 0".format(dst_schema))


def roles_report_appointment_programs(run: 'RolesRun') -> None:
    """Report appointments the day schedule will not show."""
    dst = run.dst
    report = run.report
    ledger = run.ledger
    n = run.n
    mark = run.mark
    plan = run.plan
    if ledger.get("appointment_programs"):
        return
    outside = plan("appointment_programs_count",
                   lambda: n(appointments_outside_program_zero_sql(dst)))
    if outside:
        report("roles: {0} migrated appointment(s) carry a program id other "
               "than 0. CARLOS's day schedule shows program 0 only, as "
               "OSCAR 19's did, so these appear in the patient's "
               "appointment history but not on the day view — in CARLOS "
               "exactly as they did not in OSCAR 19. Nothing was changed."
               .format(outside))
    mark("appointment_programs", {"outside": outside})


def roles_prevention_types(run: 'RolesRun') -> None:
    """Reconcile prevention type codes against the CARLOS set."""
    query = run.query
    plain = run.plain
    dst = run.dst
    report = run.report
    ledger = run.ledger
    n = run.n
    mark = run.mark
    plan = run.plan
    # 8. prevention type codes (binary compare — see
    #    prevention_type_statements)
    if not ledger.get("prevention_types"):
        stmts = prevention_type_statements(
            dst, o19map_schema.PREVENTION_TYPE_MAP)

        def plan_prevention():
            out = []
            for legacy, canonical, count_sql, _u in stmts:
                c = n(count_sql)
                if c:
                    out.append([legacy, canonical, c])
            return out

        applied = plan("prevention_types", plan_prevention)
        due = {legacy for legacy, _c, _n in applied}
        for legacy, _canonical, _count_sql, update_sql in stmts:
            if legacy in due:
                query(update_sql)
        unknown = plain(unknown_prevention_types_sql(
            dst, o19map_schema.KNOWN_PREVENTION_TYPES))
        report("roles: prevention types normalised: {0}{1}".format(
            ", ".join("{0} -> {1} ({2})".format(*a) for a in applied)
            or "none needed",
            ("\n  types PreventionItems.xml does not render (shown as "
             "unconfigured; review): " + ", ".join(
                 "{0} ({1})".format(r[0], r[1]) for r in unknown))
            if unknown else ""))
        mark("prevention_types", {"applied": applied,
                                  "unknown": len(unknown)})


def roles_rich_text_letter(run: 'RolesRun', ctx) -> None:
    """Modernise the Rich Text Letter eform.

    The plan -- which rows to disable, which scripts to run, which
    canonical rows to re-disable -- is persisted BEFORE the first write:
    the enable script flips every canonical row on, so a resumed run
    could not otherwise tell a clinic-disabled form from one the script
    enabled."""
    from .util import die
    query = run.query
    plain = run.plain
    dst = run.dst
    report = run.report
    ledger = run.ledger
    mark = run.mark
    record_appended = run.record_appended
    plan = run.plan
    # 9. Rich Text Letter — the plan (which rows to disable, which scripts
    #    to run, which canonical rows to re-disable) is persisted BEFORE
    #    the first write: the enable script flips every canonical row on,
    #    so a resume could not otherwise tell a clinic-disabled form apart
    if not ledger.get("rtl"):
        rows = plain(rtl_rows_sql(dst))
        decided = plan("rtl", lambda: list(rtl_plan(rows)))
        disable, scripts, restore, notes = (
            list(decided[0]), list(decided[1]), list(decided[2]),
            list(decided[3]))
        # the v1 seed is a bare INSERT: on a resume after it committed
        # (crash before this mark) a canonical row now exists and the
        # persisted plan must not seed a second one — the seed decision is
        # re-derived from the live rows, the rest of the plan is kept
        if (RTL_SEED_SCRIPT in scripts
                and any(is_rtl_canonical(r) for r in rows)):
            scripts.remove(RTL_SEED_SCRIPT)
        fixups_dir = ctx.get("fixups_dir") or DEFAULT_FIXUPS_DIR
        missing = [s for s in scripts
                   if not os.path.isfile(os.path.join(fixups_dir, s))]
        if missing:
            # a broken package install, not a clinic condition: fail
            # closed, resumable once the scripts are back
            die("roles: Rich Text Letter fixup script(s) missing from {0}: "
                "{1} — reinstall carlos-emr (they ship under "
                "schema/o19-fixups/) and --resume".format(
                    fixups_dir, ", ".join(missing)))
        for fid in disable:
            query(rtl_disable_statement(dst, fid))
        if scripts:
            for script in scripts:
                with open(os.path.join(fixups_dir, script),
                          encoding="utf-8") as fh:
                    # ETL executor (sql_mode='' — the 2012 seed omits
                    # NOT NULL columns), target as default database: the
                    # scripts use DELIMITER (a client directive) and
                    # unqualified table names
                    query(fh.read(), db=dst)
            for fid in restore:
                query(rtl_disable_statement(dst, fid))
            after = plain(rtl_rows_sql(dst))
            if rtl_current(after):
                outcome = "modernised to 2026.3.0 via {0}".format(
                    ", ".join(scripts))
            else:
                outcome = ("scripts ran ({0}) but no 'Rich Text Letter' row "
                           "carries the 2026.3.0 marker with live "
                           "attachment routes — the scripts match {1}; "
                           "apply by hand before go-live".format(
                               ", ".join(scripts), RTL_CANONICAL_PREDICATE))
        elif disable:
            outcome = ("current; {0} legacy row(s) disabled".format(
                len(disable)))
        else:
            outcome = "unchanged"
        # the v1 seed adds an eform row with a fresh fid: row parity
        # tolerates exactly the rows recorded here (0 on every other path)
        record_appended("eform")
        report("roles: Rich Text Letter — {0}{1}".format(
            outcome, ("\n  " + "\n  ".join(notes)) if notes else ""))
        mark("rtl", {"disabled": disable, "restored_disabled": restore,
                     "scripts": scripts, "outcome": outcome})


def run_roles(ctx, progress: Dict, save: Callable[[], None]) -> None:
    """The post-copy step, ledger-marked per sub-step under progress['roles'].
    ctx carries the ETL executors (query_etl with the bulk-copy prelude,
    query plain), the schema names, the report callback and the CLI
    options (role_templates, fixups_dir).

    Crash discipline: every write is idempotent, and everything the
    ledger records is either taken from the database AFTER the write
    (appended-row counts are the same anti-join row parity checks) or
    decided and persisted BEFORE the write (private lists, planned
    counts, the RTL plan), so a resume after a crash between a write and
    its mark reports the same facts as an uninterrupted run. Report lines
    are emitted before the mark: a crash in between repeats a line on
    resume rather than losing it."""
    from . import o19import
    query = ctx["query_etl"]
    plain = ctx["query"]
    src = ctx["src_schema"]
    dst = ctx["target_db"]
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
        o19import.append_private(details_path, "\n".join(lines) + "\n")
    run = RolesRun(ctx, query, plain, report, state_dir, ledger, appended,
                   n, count, mark, record_appended, plan, append_private,
                   progress.get("admin_provider_no"))
    requested, overrides = roles_bind_role_templates(run, ctx)
    roles_append_carlos_roles(run)
    roles_align_role_spelling(run)
    roles_facility_links(run)
    # activeyn before program: a membership's role_id follows the
    # provider's ACTIVE role, so it must be normalised first
    roles_normalise_activeyn(run)
    roles_program_memberships(run)
    roles_backfill_custom_grants(run, overrides, requested)
    roles_privilege_diff(run)
    roles_prune_property_keys(run)
    roles_prune_encounter_forms(run)
    roles_report_appointment_programs(run)
    roles_prevention_types(run)
    roles_rich_text_letter(run, ctx)


def _fmt_templates(mapping: Dict[str, str]) -> str:
    """The role-template bindings as `class=template` pairs for the report,
    or the word `none`."""
    return (", ".join("{0}={1}".format(c, t) for c, t in sorted(
        mapping.items())) or "none")
