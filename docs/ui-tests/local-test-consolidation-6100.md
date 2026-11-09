# Local test consolidation for release/2026.08

PR #6100 consolidates the 32 requested issues. This is an implementation and
qualification ledger, not a declaration that all 32 acceptance criteria pass.
The existing release verification matrix remains authoritative. Historical
passes, a registered script, and a successful unit test do not establish an
installed workflow pass. Leave tickets open until their complete acceptance
has current evidence.

## Scope and disposition

| Issue | Implementation and remaining acceptance |
| --- | --- |
| #4102 | Actual develop SMS/consent locking implementations are absent from this branch. Keep branch-inapplicable races open; do not substitute unrelated locks or synthetic production tables. See `test-only-release-issues.md`. |
| #5718 | Corrected the all-sex operator and added matching/nonmatching multi-value and alias controls. Broader installed guideline acceptance remains required. |
| #5812 | Retain native MRP routing assertions. The composer now carries its draft through patient search, refuses an unselected assignee in both site modes, and offers an explicit blank choice for an unavailable preferred provider (#5824/#5825). Current installed retakes remain required. |
| #5806 | Retain actual signed callback/deny boundaries. Callback verification/storage now preserve internal percent escapes and invalid signatures map to 401 (#5823); real-parser signature controls cover header, form and query transports. All 262 selected OAuth/eForm/CSRF/report regression cases pass; current installed retakes remain required. |
| #5801 | Explicit empty eForm values clear saved markup and PDF defaults; missing parameters remain distinct. Existing nonblank-only value rows and chart-template substitutions are preserved. Installed visual editor, checkbox, radio, image and signature acceptance remains required. |
| #5709 | Retain lookup CRUD and consumer isolation. Provider add/edit forms omit inactive choices; edit explicitly preserves and labels its current retired value. The consumer test checks both that preservation and the new-provider boundary. Lookup add/remove/reorder now require POST and have direct/dispatcher GET/HEAD and authorized POST controls; current installed retakes remain required. |
| #5708 | Retain multi-recipient forwarding and independent ownership/restoration assertions; complete current installed retakes remain required. |
| #5730 | PDF suffix matching now handles upper/mixed case; action tests inspect stored page count and MIME. All native format/malformed-content retakes remain required. |
| #5803 | Qualified bounds are excluded from exact numeric graph points, with numeric and qualitative controls. Literal/history behavior (#5828) still requires an explicit clinical contract and installed retakes. |
| #5727 | Corrected request-scoped First Nations selected/original values and removed a missing focus handler; separate required First Nations round-trip and encoding profiles enable the module. Round-trip fixtures need two configured three-digit communities; installed retakes remain required. |
| #5726 | Retain original/corrected/out-of-order ingestion assertions. Current-version selection (#5819) and stale measurement replacement (#5820) remain product blockers. |
| #5794 | Only the error check provisions temporary fault routes; exact descriptor restoration is required before its supervisor exits. Packaged and development filter qualification remains required. |
| #6025 | Growth persistence tests compare all five submitted headers, with H2 and physical MariaDB executions. |
| #6093 | A real physical-table grouped history test covers independent MAX projections, historical ordering, the EctFormData caller and other-patient isolation. Required in generated and ON/BC Flyway native lanes. |
| #5946 | DAO tests cover the non-Ontario LIKE branch and Spire's one-row length ordering, with distinct provider fixtures. |
| #5700 | Keep all 40 workstreams and all local acceptance gates. No full-release readiness claim while any local gate or product blocker remains. |
| #4492 | Keep Tasks 17 and 35–48, profile/locale/time-zone, signal, application-finding and upgrade requirements; full current runtime qualification is outstanding. |
| #5177 | Keep H1–H46 and transferred acceptance, including full repetitions and independent residue checks. Helper passes do not replace installed executions. |
| #5487 | Keep current installed and development response-filter, authentication/reset and error-path lifecycle retakes. |
| #5852 | BC billing edit/delete checks use the actual link roles; dependency-table fixtures exercise anchor markup. BC installed retakes remain required. |
| #5881 | General suites/lifecycle clients run with the shipped error mapping; only the error check is supervised by the fault adapter. It cannot wrap arbitrary suite commands. |
| #5883 | The 20 retained scripts now write shared structured outcomes after their existing cleanup. A manifest regression checks reachability. BC PathNet has its own required profile; O19 has a required explicit imported-database/authentication lane. Neither is silently dropped. |
| #5884 | Every non-dry deployment run requires CARLOS_DISPOSABLE_HOST=true, including loopback. Pure standalone fixtures remain independent of deployment state. |
| #5914 | The shipping Debian build checks nested WAR jars for exactly the GlassFish JAXB factory, rejects extra/MOXy providers and malformed/ambiguous archives. |
| #5973 | Native DAO URLs use the packaged ROUND/old-alias conversion options through the compatibility driver. |
| #5977 | Source-contract tests pin both the DrugRef lock goal/path and absence of the lock-skip flag. The new PR package job executes the shipping source-build pipeline. |
| #6048 | Jasper compiles all eight dynamically dispatched JSP fragments in addition to the ordinary JSP execution; an exposed source-level incompatibility was repaired. |
| #6062 | Candidate coverage-floor regeneration, zero-coverage disposition and freshness enforcement remain outstanding. No waiver is implied. |
| #6069 | Java runs on main/release pushes; PR CI now builds both complete Debian packages and tests ON/BC installs. CI execution is still required. |
| #6070 | Node, packaging Python and O19 generator CI use strict outcome runners. Native ON/BC references are provisioned for required generator cases. The pinned CLI's separate upstream suite retains its own contract. |
| #6072 | Error-page detection recognizes colon-bearing HTTP status and the sanitizer heading/reference signature; failed body reads propagate. Controls cover legitimate reference prose. |
| #6076 | The current ON installed package passed the exact history-row patient assertion. Finding 141 is marked fixed and its manifest/known-outcome pin is retired; independent audit findings 206–208 remain fatal and do not become passing acceptance. |

## Qualification accounting

The merged-source H2 retake at `5c7977e30a8` completed 34,478 cases with no failures/errors and
143 explicitly routed skips. The OAuth callback changes also passed all
262 selected regression cases, including six independently signed transport and
refusal cases. The generated-schema MariaDB lane passed 3,128 cases without skips, and all
177 selected lookup-guard/mutator/render cases pass. Native MRP fixture cleanup
now reserves and removes owned provider billing-centre rows; the failing run is
retained and its three synthetic orphan rows were recovered without changing
unrelated rows. The strict Node inventory now has 7,729 passing cases in 547 files. Rebuilt
package retakes for the lookup guard and visible MRP guidance remain required. These source results do not substitute for rebuilding/reinstalling the
final candidate and rerunning its installed acceptance.

The generator's old 289 count included twelve duplicate executions of
`TestOverlayRulingsReachTheManifest`: unittest also discovered the `_base` loop
variable as a class alias. Removing that alias leaves 277 unique cases. The
strict runner rejects duplicate identities, skips and every non-PASS outcome.
The matrix floor now names those 277 unique cases; no unique test was removed.
The native socket's private parent stays mode 0700; its mysql-owned child is
traversable so the unprivileged CI runner can execute the real tests.

Error qualification has a 300-second browser bound plus compilation, counters
and two bounded service restarts. Its 1600-second manifest budget includes that
setup/restoration, which the old suite-wide wrapper placed outside the per-check
budget. The smoke ceiling therefore includes the additional 1300 seconds.
Failure or cancellation must still return nonzero after restoration.

## External exclusion

The user's external-systems exclusion covers Ministry staging
`ebs-packaged-staging` and the four SRFax `manual-fax-*` vendor gates already
identified in the matrix. Their absence never becomes PASS. Local checks with
`kind: external`, local transport/privacy checks, package lifecycle, backups,
restart/key persistence, crash recovery, load and production-view coverage stay
in scope. This local scope cannot establish full vendor-qualified release
readiness.
