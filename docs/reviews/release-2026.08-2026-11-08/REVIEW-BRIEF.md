# CARLOS EMR — release/2026.08 vs main review: shared reviewer brief

You are one of ~28 reviewers, each assigned one slice of the changes on `release/2026.08`
that are not yet on `main`. Your job is an intensive, methodical, adversarial review of YOUR
slice: find what is incorrect, incomplete, erroneous, omitted, breaking, or failing silently.

## The project (short)
CARLOS EMR is a Canadian (Ontario + BC) electronic medical record: Java 25, Spring 7, Struts 7.1
("*2Action" classes), Hibernate 7, Tomcat 11, MariaDB, JSPs under `src/main/webapp/WEB-INF/jsp`,
shipped as Debian packages (`debian/`). Patient data (PHI) must never be logged or exposed.
Project rules live in `CLAUDE.md` at the repo root — skim the sections relevant to your slice
(security checks, SafeEncode/`<carlos:encode>`, PathValidationUtils, CSRF, direct-response
actions must `return NONE`, GET/HEAD rejection for mutators, response-filter safety, typed DAO
paging order, PDF font policy, container-provided Jakarta APIs, HAPI HL7 rules, etc.).

## Where things are (READ-ONLY)
- Release head worktree (read files here): `/home/user/review/release-2026.08`
  (detached at `origin/release/2026.08` = 9004536d).
- Refs: `origin/main` (d40d2939, = tag 2026.08.0-beta7), `origin/release/2026.08` (9004536d).
  Merge base = e53e6cb5 (= tag 2026.08.0-beta6). main has ONE commit not on release (d40d2939,
  #5739 O19 billing filename audit + beta7 prep) — not your concern unless your slice touches it.
- Useful commands (run from the worktree; never change it):
  - `git show <sha>` / `git show --stat <sha>` — one commit (commit message ≈ PR description)
  - `git diff origin/main...origin/release/2026.08 -- <path>` — net change of a file on release
  - `git log --oneline origin/main..origin/release/2026.08 -- <path>` — which release commits
    touched a file (later commits may have fixed or re-broken earlier ones — check this before
    reporting anything)
  - `git show origin/main:<path>` — the file as it is on main
  - grep/rg across the worktree for callers, usages, string references (Spring XML `class=`,
    JSP `<%@ page import`, struts-*.xml, web.xml, reflection by name, JS fetch URLs, etc.)
- Your slice's commit list and file list are in
  `/tmp/claude-0/-home-user/e46be61b-8a52-5abb-b42c-abea0c97ed7a/scratchpad/slices/<SLICE>-commits.txt`
  and `<SLICE>-files.txt` (files of your commits that still differ from main at release head).

## Hard rules
- Read-only. Do NOT modify, stage, commit, checkout, stash, or reset anything in
  `/home/user/review/release-2026.08` or `/home/user/carlos`. Do NOT push. Do NOT create or
  comment on GitHub issues/PRs or post anything anywhere. Reading GitHub (e.g. a linked issue
  via the GitHub MCP `issue_read` tool, loaded with ToolSearch) is allowed if you need intent.
- No Maven builds (only JDK 21 is installed; the project needs 25). CI on the release head is
  green: build, unit `tests`, `jspc`, MariaDB DAO integration tests, CodeQL, Semgrep, SpotBugs,
  PMD. So do not report "does not compile". Focus on logic/behaviour that tests do not catch.
  You MAY run quick side-effect-free python3/node scripts or a repo test file, but only from a
  private copy under your own scratch dir
  (`/tmp/claude-0/-home-user/e46be61b-8a52-5abb-b42c-abea0c97ed7a/scratchpad/work-<SLICE>/`)
  if the tool would write files.
- Do not spawn subagents. Do the whole review yourself, in passes if needed.

## What to look for (the user's words: correct, complete, no errors, omissions, breaking changes, silent failures)
1. Correctness bugs: wrong logic, wrong SQL/HQL semantics (NULLs, joins dropping rows, LIKE,
   ordering, off-by-one, date/time zone/precision, Integer vs String ids), resource leaks,
   concurrency/races, transaction boundaries, wrong HTTP status, broken encoding.
2. Completeness / omissions: the commit says it fixes X — is X fixed everywhere (all call sites,
   all JSPs, all provinces, both add and edit paths, REST and web paths)? Leftover references to
   removed classes/libraries/files/routes (especially string references a compiler can't see).
3. Breaking changes: existing installs upgrading from earlier 2026.08 betas (config files, stored
   data, Debian maintainer scripts), external integrations (REST/SOAP clients, MCEDT/Teleplan,
   HL7, fax), URLs/routes, stored preferences, file formats/CSV columns, API response shapes.
4. Silent failures: exceptions swallowed or logged-and-ignored where the user/admin needs to
   know; fallbacks that quietly change behaviour; truncation without notice; "skip" treated as
   "pass"; validation that silently drops valid data; features that quietly stop working.
5. Security/privacy regressions: missing `hasPrivilege` checks, CSRF, XSS (missing SafeEncode),
   path traversal, XXE, PHI in logs/exception messages, unauthenticated paths.
6. Tests: do the new tests actually prove the fix (or are they vacuous / mock-only / asserting
   the bug)? Is risky behaviour untested? (Report test gaps only where the risk is real.)
7. Docs touched by your commits: claims that contradict the code.

Scope: report problems INTRODUCED or LEFT INCOMPLETE by your slice's commits (including where a
commit claims to fix something and doesn't fully). ALSO (user requirement): flag PRE-EXISTING
defects (already present on origin/main) that you find in the code your slice touches or its
immediate callers/consumers — correctness, security, privacy/PHI logging, silent failures, data
integrity, contract problems. Put them in a separate section `## Pre-existing issues`, numbered
`<SLICE>-P1`, `<SLICE>-P2`…, with the same per-finding fields plus
`- Pre-existing: yes (present on origin/main)`, verified with the same rigour (cite main and
release line numbers). Don't start an unbounded audit of unrelated code to find them.

## MANDATORY: contract changes (user requirement)
The user explicitly requires assurance that no data-format contract or code return/contract was
changed in a way that creates a breaking change or a silent failure. For your slice, enumerate
EVERY changed contract and check its consumers:
- Data formats: REST JSON/XML shapes (field names, order, types, null/absent, date/time formats,
  enums, wrappers, JAXB/Jackson annotations, package-info adapters), SOAP payloads, CSV/XLSX
  columns/headers/encodings, PDF layout used by downstream processes, HL7/CDS/MCEDT/Teleplan
  files, AJAX/JSON responses consumed by page JavaScript, values stored in the DB (escaping,
  encodings, date strings, encrypted/serialised values), file names/paths on disk, config and
  property keys (renamed/removed/default changed), request parameter names.
- Code contracts: public/protected method signatures, return types and return SEMANTICS
  (null ↔ empty collection/""/Optional, boolean meaning, sentinel values like -1/0, sorted vs
  unsorted, mutable vs unmodifiable, live view vs copy), thrown exceptions (checked/unchecked,
  throwing where it used to return false/null, or swallowing where it used to throw), HTTP status
  codes and response bodies (200→4xx/409/405/redirect, JSON error body vs HTML error page),
  Struts result names, request/session attribute names read by JSPs.
For each: who consumes it (grep Java, JSP, JS, Spring/Struts XML, scripts, external clients),
is every consumer updated, is the change documented (release notes/docs), and could a consumer
now misbehave silently (e.g. a caller that checks `== null` now never sees null; JS that only
handles 200 now ignores a 409; a client that parsed an XML date now gets date-time)?
Add a section `## Contract changes` to your report with one row per changed contract:
`| contract | old → new | consumers checked | verdict: safe / breaking / silent-risk |`, and
raise a finding for every breaking or silent-risk row.

## Already-known issues and security disclosure
- An earlier review (umbrella issue #5741, at release commit 4b302882 = #5710) already filed 56
  sub-issues, and many related issues exist. Read
  `/tmp/claude-0/-home-user/e46be61b-8a52-5abb-b42c-abea0c97ed7a/scratchpad/KNOWN-ISSUES.md`
  (full bodies of #5741's sub-issues: `known-5741-full.md` in the same folder). If your finding
  matches a known issue, do NOT re-report it as new: list it under a "Known issues re-confirmed"
  section with the number and whether it is still unfixed at release head (one line each).
  Spend your time on NEW problems, especially in commits after 4b302882 (#5722, #5691, #5798,
  #5694, #5518, #5705, #5512, #5689, #5513, #5821, #5800) and on what the earlier review missed.
- Security findings: the project maintainer has directed that ALL findings, including
  exploitable security ones, go in the public review ticket with full detail. Report them
  normally with `Category: security` (no private routing; ignore any earlier instruction to
  mark them `Disclosure: private`).

## Verify before you report
For every finding: (a) read the actual code at release head, with line numbers; (b) check callers
and related config; (c) check whether a LATER release commit already fixed it
(`git log -p origin/main..origin/release/2026.08 -- <file>`); (d) try hard to disprove it.
Mark Confirmed only if you traced it in code; otherwise Plausible + what would confirm it.
False positives cost the maintainers time — precision matters as much as recall.

## Severity calibration
- Critical: PHI exposure, exploitable security hole, data loss/corruption, app fails to start or a
  core workflow broken for most users, upgrade breaks existing installs.
- High: a common clinical/billing/admin workflow broken or producing wrong results; a silent
  failure hiding real errors on an important path; breaking change for existing installs or
  integrations without migration; meaningful security weakness.
- Medium: edge-case bugs, error-handling gaps, misleading messages, incomplete migration with
  limited reach, missing tests for genuinely risky changes.
- Low: small inconsistencies, doc inaccuracies, minor quality issues with a real effect.
Skip pure style nits.

## Output — write a file, then return a short summary
Write your full report to
`/tmp/claude-0/-home-user/e46be61b-8a52-5abb-b42c-abea0c97ed7a/scratchpad/findings/<SLICE>.md`
in exactly this shape:

```
# <SLICE>: <slice title>
## Coverage
<which commits/files you reviewed and how deeply; anything you could not cover>
## Findings
### <SLICE>-1: <short title>
- Severity: Critical|High|Medium|Low
- Category: bug|regression|breaking-change|silent-failure|omission|security|privacy|data-integrity|test-gap|docs|process
- Confidence: Confirmed|Plausible
- Commit: <short sha> (#PR)
- Location: `path:line` (release head)[, ...]
- Problem: <what is wrong>
- Evidence: <short code excerpt / command output, enough to verify>
- Impact: <who/what breaks; would anyone notice?>
- Correction: <concrete fix needed>
### <SLICE>-2: ...
## Checked and found OK
- <one line per significant thing you verified>
## Declined to judge
- <behaviour you set aside, with reason>
## Out of scope but notable
- <optional>
```
Then return to the coordinator ONLY: the counts by severity and one line per finding
(id, severity, title). Keep the return under 40 lines.
