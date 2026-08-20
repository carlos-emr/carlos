# MyVitalHistory Mocks

Visual mocks for the patient-held personal health record app proposed in
[#3474](https://github.com/carlos-emr/carlos/issues/3474). **"MyVitalHistory" is a working
name, not a decision.**

These are non-functional HTML mocks for discussion. Nothing here is built, and none of it
is wired to CARLOS.

Open `index.html` in a browser to view the welcome screen. Other screens can be opened
with `?screen=`, or from the switcher bar at the top:

`welcome` · `passphrase` · `recovery` · `unlock` · `library` · `grid` · `folder` · `menu` ·
`select` · `move` · `trash` · `empty` · `import` · `import-done` · `viewer` · `security` ·
`mobile` · `health`

## Included screens

**Getting in**

1. **Welcome** — first run; establishes that the vault belongs to the patient
2. **Passphrase** — vault passphrase creation, with the no-reset warning
3. **Recovery** — the key-recovery options, as an open question
4. **Unlock** — returning-user unlock, passphrase or biometric

**The file system**

5. **Library** — the home screen: folders, sortable columns, list view
6. **Grid** — the same records as thumbnails
7. **Folder** — inside a folder, with breadcrumbs
8. **Menu** — what you can do with a single document
9. **Select** — multi-select with a bulk action bar
10. **Move** — the folder picker
11. **Trash** — deleted items, with a 30-day countdown
12. **Empty** — first-run empty state, explaining the CARLOS handoff

**Getting documents in and reading them**

13. **Import** — "Send this to MyVitalHistory", after the patient unlocked it in their email
14. **Import done** — confirmation, and the handover of custody
15. **Viewer** — reading a document, with a provenance panel

**Everything else**

16. **Security** — encryption, recovery, backup, and auto-lock settings
17. **Mobile** — three phone screens via the Capacitor shell
18. **Health data** — Apple Health / Health Connect, flagged as concept only

Each screen carries an amber caption explaining what it is showing and which open question
it relates to. The captions are mock scaffolding, not part of the product.

## The model: a filing cabinet, not an inbox

The organising idea is a **file system the patient runs themselves** — the familiarity of
Google Drive, narrowed to medical documents. Folders the patient names, list and grid views,
sorting, multi-select, move, rename, and a trash that holds things for 30 days.

Two consequences of taking that seriously:

**Nothing files itself.** Documents arrive at the top level and stay there until the patient
puts them somewhere. Automatic sorting is tempting and wrong here: a patient's mental model of
their own care ("everything for the cardiology referral") rarely matches the categories a
clinical system would assign, and being second-guessed by software about your own records is
exactly the feeling this app exists to avoid. The "By kind" entries in the sidebar are filters
over what is already there, not folders competing with the patient's own.

**Deleting is survivable.** A medical record deleted by accident is not recoverable from
anywhere else, so trash keeps items for 30 days and every row states its own countdown.

## What these mocks are arguing

**The vault is the patient's, and that has to be visible.** The clinic cannot read it and
neither can the vendor. This is stated on the welcome screen, restated on the security
screen, and it is the reason the passphrase screen carries a blunt warning instead of a
reassuring one.

**Cloud backup is safe because the contents are ciphertext.** The security screen says the
iCloud copy is unreadable by Apple. That is the design decision recorded on #3474, and it is
what makes automatic OS backup acceptable for PHI leaving the country.

**The CARLOS handoff is a custody transfer, not a download.** The patient unlocks the emailed
document *in their email*, using the passphrase the CARLOS portal gives them (#3207, #3449,
#3450), and only then presses "Send this to MyVitalHistory". So the document arrives readable and
is locked again under the patient's own passphrase.

**MyVitalHistory never handles the clinic's passphrase.** That boundary stays with CARLOS and the
portal, which keeps the app out of the clinic's key material entirely. It also means nothing in
the vault is ever in a locked-and-unopenable state — once the vault is open, everything in it is
readable.

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
- **"Send a copy"** appears in the document menu but has no screen behind it. Sharing a record
  with a new doctor or a family member is genuinely useful and probably necessary, but it is the
  one action that takes a document back *out* of the vault, so it needs its own design rather
  than a menu entry.
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
