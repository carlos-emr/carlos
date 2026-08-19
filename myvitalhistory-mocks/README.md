# MyVitalHistory Mocks

Visual mocks for the patient-held personal health record app proposed in
[#3474](https://github.com/carlos-emr/carlos/issues/3474). **"MyVitalHistory" is a working
name, not a decision.**

These are non-functional HTML mocks for discussion. Nothing here is built, and none of it
is wired to CARLOS.

Open `index.html` in a browser to view the welcome screen. Other screens can be opened
with `?screen=`, or from the switcher bar at the top:

- `index.html?screen=welcome`
- `index.html?screen=passphrase`
- `index.html?screen=recovery`
- `index.html?screen=unlock`
- `index.html?screen=library`
- `index.html?screen=empty`
- `index.html?screen=import`
- `index.html?screen=import-done`
- `index.html?screen=viewer`
- `index.html?screen=security`
- `index.html?screen=mobile`
- `index.html?screen=health`

## Included screens

1. **Welcome** — first run; establishes that the vault belongs to the patient
2. **Passphrase** — vault passphrase creation, with the no-reset warning
3. **Recovery** — the three key-recovery options, as an open question
4. **Unlock** — returning-user unlock, passphrase or biometric
5. **Library** — the home screen, with one document still locked
6. **Empty** — first-run empty state, explaining the CARLOS handoff
7. **Import** — the "Send this to MyVitalHistory" flow from a CARLOS email
8. **Import done** — confirmation, and the handover of custody
9. **Viewer** — reading a document, with a provenance panel
10. **Security** — encryption, recovery, backup, and auto-lock settings
11. **Mobile** — three phone screens (locked, records, reading) via the Capacitor shell
12. **Health data** — Apple Health / Health Connect, flagged as concept only

Each screen carries an amber caption explaining what it is showing and which open question
it relates to. The captions are mock scaffolding, not part of the product.

## What these mocks are arguing

**The vault is the patient's, and that has to be visible.** The clinic cannot read it and
neither can the vendor. This is stated on the welcome screen, restated on the security
screen, and it is the reason the passphrase screen carries a blunt warning instead of a
reassuring one.

**Cloud backup is safe because the contents are ciphertext.** The security screen says the
iCloud copy is unreadable by Apple. That is the design decision recorded on #3474, and it is
what makes automatic OS backup acceptable for PHI leaving the country.

**The CARLOS handoff is a custody transfer, not a download.** Screens 7 and 8 show a document
arriving locked under the *clinic's* passphrase (from the portal — #3207, #3449, #3450) and
being re-locked under the *patient's*. After that the clinic's passphrase is irrelevant. Making
that moment explicit is the point of the flow.

**Provenance is a feature.** A patient-held record is only useful if its origin is beyond
doubt, so the viewer keeps sender, provider, arrival date, and filing date visible rather
than burying them.

## Open questions these mocks deliberately do not answer

- **Key recovery** (screen 3) shows three options — printed sheet, trusted contact, clinic-held
  escrow — with the trade-off of each stated on the card. Whether the clinic-held option should
  exist at all is unresolved, and it is a liability question for the practice rather than a
  technical one.
- **Apple Health / Health Connect** (screen 12) is drawn as a concept and labelled as such. It
  is phone-only and cannot ingest documents.
- The **name**, the **backup destinations**, and whether there are **accounts at all** are all
  still open.

## Visual approach

Deliberately *not* the CARLOS/OSCAR visual language. CARLOS is clinician software used all day
by trained staff; this is consumer software used occasionally by patients, some of them elderly
and stressed. So: larger type, higher contrast, generous touch targets, plain wording, and calm
colour rather than dense information.

Wording is aimed at a patient, not a clinician — "scrambled so only you can open it" rather
than "AES-256 encrypted at rest". Every screen should be readable by someone who has never
heard the word "encryption".

Font Awesome is loaded from the CARLOS webapp assets, so the mocks must stay in a directory at
the repository root for the relative path to resolve.

Screenshots of each screen are in `screenshots/`.
