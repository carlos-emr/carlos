# Retained Patient Intake role login (#4562)

The retired Patient Intake interface has no Struts result. Accounts retaining that
role previously returned the unmapped `patientIntake` result after ordinary login,
password-reset completion, or MFA completion. The role now follows the same
configured destination as every other authenticated account. Its assigned
privileges, credentials, session setup, and audit policy are unchanged.

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

Installed browser qualification is pending. Finding 188 remains open until that
qualification succeeds; focused tests alone do not establish a live reproduction.
