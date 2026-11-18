# Retained Patient Intake role login (#4562)

The retired Patient Intake interface has no Struts result. Accounts retaining that
role previously returned the unmapped `patientIntake` result after ordinary login,
password-reset completion, or MFA completion. The role now follows the same
configured destination as every other authenticated account. Its assigned
privileges, credentials, session setup, and audit policy are unchanged.

The original missing-result failure was confirmed in source and was not reproduced
on a live pre-fix installation. The following pre-repair regressions execute the
action directly; the installed browser result below validates only the repaired path.

Three regressions fail against release `c7f0a450eec4aab390d014698876ac21dd388e15`;
80 existing controls pass. After repair, all 85 focused Java 25 cases pass with
zero failures, errors, or skips. The added redirect-failure controls retain exactly
one sign-in audit for ordinary and legacy roles. Measured release lines,
instructions, and branches remain at the retained 100% floors.

`login-legacy-intake` exercises an owned throwaway login carrying the legacy
assignment alongside its copied privileges. It requires the supported schedule
after ordinary login, forced password reset, and a new login with the replacement
password. Cleanup removes the owned assignments, credentials, preferences, and
audit rows; shared accounts and role definitions are untouched.

The strict installed `login-legacy-intake` workflow passes on the Ubuntu 26.04
package built from `b4048148a18a26efbd2bc4173336d693f5270672`, using pinned Chromium
154.0.8025.0. Ordinary login, forced reset, and replacement-password login reach
the supported schedule, with database assertions and owned-fixture cleanup. The
installed Login2Action source is byte-identical to this fix. Finding 188 is fixed.

The exact 27-case report SHA-256 is `f47dffdc0325b5c56c218f9923cb6811fd1a7d9a3e8b9dd51246eaf34647f84f`.
This workflow passed in 15.450 seconds. That report also retains an unrelated
report-order navigation timing failure, so it is not evidence that all 27 cases
passed. The full combined qualification and subsequent timing-check retake are
tracked separately.

After CodeRabbit review, all new fixture queries use separate bound parameters,
and finding 188 retains the original source-only/live-reproduction distinction.
All 79 manifest and throwaway-login fixture controls pass. The revised installed
workflow passes again in 23.683 seconds with strict release mode, residue audit,
pinned Chromium, unchanged properties/restart count, and a clean server-log audit.
This test-only retake uses the same `b4048148` application package. Its JUnit
SHA-256 is `a8e675055cd213790c766f287b9341ea1f86c3bbb9b45e86f48684f677edf320`.
