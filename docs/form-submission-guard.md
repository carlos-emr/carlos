# Duplicate Submissions and Stale Edits

Issue #4225 found create forms that saved twice and an edit form that silently overwrote a newer
record. This page describes the two mechanisms that fix them and how to use them for another form.

## One save per rendered create form: `FormSubmissionGuard`

`io.github.carlos_emr.carlos.utility.FormSubmissionGuard` gives each rendering of a create form a
one-time token. It is held in the HTTP session and bound to a `Scope` (the form) and an optional
binding value, such as the patient a bill is for.

A classic form POST repeats itself in four ways: a double click, a double Enter, a second click
while a slow response is still out, and a reload of a result page rendered straight from the POST.
Each of these used to create a second appointment, Ontario bill, patient or consultation service.
With the guard, the first request to present a token claims it. A repeat of the same rendering
gets one of these outcomes:

| Verdict | Meaning | What the action does |
|---|---|---|
| `PROCEED` | The token was free and is now claimed | Saves, then closes the claim |
| `ALREADY_SAVED` | The first submission saved `result()` | Writes nothing; renders the first save's own result page from `result()` |
| `UNCONFIRMED` | The first submission may have saved but recorded no result | Writes nothing; HTTP 409 asking the user to check the record |
| `IN_PROGRESS` | The first submission is still saving after a 20 s wait | Writes nothing; HTTP 409 |
| `STALE` | Not a token this session issued for this scope and binding, or it was evicted | Writes nothing; HTTP 409 |
| `ABSENT` | No token was posted | Keeps the endpoint's previous behaviour |

The browser keeps whichever response arrived last, often the repeat's. That is why
`ALREADY_SAVED` answers with the same page the first save would have shown: the popup closes, the
day sheet refreshes, and the receipt or invoice prints. Repeats that cannot reproduce that page
use `FormSubmissionGuard.refuse()`. It sets HTTP 409 and `Cache-Control: no-store` and selects a
`global.formSubmission.*` message for `/WEB-INF/jsp/common/formSubmissionRefused.jsp`. Each
guarded action maps the `submissionRefused` result to that page.

A later, intentional create renders the form again and gets a new token. Each confirmation page
that also offers the form issues a new token, as Add Service does, so adding another record still
works. The guard identifies repeated submissions of one rendering. It does not deduplicate records
by content.

### When a token is freed for a retry

The claim frees the token only when the attempt provably wrote nothing:

- The save calls `claim.storageStarted()` first inside a `TransactionTemplate` around the write
  that creates the record. A rollback (`STATUS_ROLLED_BACK`) frees the token. Any other or unknown
  outcome keeps it spent, including a commit whose acknowledgement was lost. Add Appointment
  commits the appointment and its MC number together. The Ontario bill save marks a "nothing
  saved" result rollback-only. Add Demographic wraps only the patient row, because its later
  writes run in their own transactions. Add Service wraps its single insert.
- After the commit, the action calls `claim.saved(id)` so a repeat can render the first result. If
  a later step fails, the token stays spent and a repeat shows the first result instead of
  creating a second record. A repeat's page shows what was saved (the service name, the patient's
  name), never the repeat's own fields: those were not saved.

Claim the token before validation. Once the first save has stored a patient, the HIN check would
otherwise refuse the repeat as a duplicate of the patient it just created.

### Adding the guard to another create form

1. Add a `Scope` constant.
2. Render the token into the form as `FormSubmissionGuard.PARAMETER`. In a scriptlet page, encode
   the token for the HTML attribute: `<carlos:encode ... context="htmlAttribute"/>` or
   `SafeEncode.forHtmlAttribute(...)`. A page assembled by an action can set a request attribute
   instead (see `ViewBillingOnReview2Action`). A page that echoes every request parameter back
   (the Ontario bill review) must skip `carlosFormSubmission`.
3. In the action, call `attempt()` after the method and privilege checks. Handle the verdicts as
   in the table above, wrap the save in `try (Claim claim = attempt.claim())`, and map
   `submissionRefused` in the action's `struts-*.xml` entry.

`PreventionSubmissionGuard` (#4410) and `EFormSubmissionGuard` predate this class. They keep
form-specific state (the DHIR review, opener-session effects) and stay separate.

`EFormSubmissionGuard` frees a token by the same rule. When a submission fails after its storage
transaction started, `AddEForm2Action` keeps the token spent and answers HTTP 409 with its own
text, never the replay refusal `eform.submitUnavailable` (#4424). After a confirmed commit,
`eform.submitSavedStepIncomplete` says the eForm was saved and names the step that did not
complete: Add to Documents, the chart notes and other entries the template writes, the fax,
download or email preparation, or "a follow-on step" for work with no name of its own. When the
commit's outcome is unknown, `eform.submitOutcomeUnknown` says the save could not be confirmed.
The log records the step and `LogSafe.exceptionTrace`, never the exception's message.

### Browser checks that replay requests

`lib/mutation-families.js` and `get-reject-consult-config` replay a POST that the page already
sent once (the CSRF, authorization and WAF matrices). The replayed token is already spent, so the
families drop it (`carlosFormSubmission: null`) and replay the unguarded request. A check that
must prove the guard itself renders a new form instead. `double-submit-*` checks the rapid
activations and the reload, and `double-submit-intentional-adds` checks that a later add still
works.

## Stale Master Record edits: `DemographicEditVersion`

The Master Record edit form posts every field, and `DemographicUpdate2Action` used to write all of
them back. A form opened before another user's save put that user's changes back (a cleared phone
number). For a patient whose extension rows did not exist yet, it also inserted them a second time
and failed on `uk_demo_ext` with HTTP 500.

- `DemographicEdit2Action` renders `demographicVersion` into the form. This is a SHA-256 over the
  patient row, the `demographicExt` rows the form writes (`editedExtensionKeys()`) and the
  `demographiccust` row. A hash is used rather than `lastUpdateDate` because that column is stored
  to the second.
- `DemographicUpdate2Action` runs every write in one transaction after
  `DemographicDao.findForUpdate()` locks the patient row. A posted version that no longer matches
  is refused before anything is written: HTTP 409 with
  `/WEB-INF/jsp/demographic/demographicUpdateConflict.jsp`. That page lists each field where the
  refused entry differs from the current value and links to the current record. It also offers
  to save the same entries again deliberately, after a confirmation, bound to the current version.
- Extension rows are matched to the patient's existing rows by key, never by an id the form
  posted. An unversioned save therefore cannot hit `uk_demo_ext` or touch another patient's row.
- A post with no version (an older page or a hand-built request) is saved without the check.
  Replay tooling drops `demographicVersion` for that reason.

`concurrency-demographic-edit` and `concurrency-demographic-ext-insert` pin the refusal, and
`demographic-stale-edit-recovery` pins the conflict page and both recovery paths.
