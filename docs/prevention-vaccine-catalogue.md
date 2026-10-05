# Vaccine catalogue (National Vaccine Catalogue V2)

CARLOS keeps a local copy of the Canadian vaccine catalogue for the prevention screens: generic
vaccines, brands, DINs, holders and lot numbers with expiry dates. It is refreshed from the National
Vaccine Catalogue V2 bundle at `https://nvc-cnv.canada.ca/fhir/v2/Bundle/NVC`. The address is fixed;
no setting or user can point CARLOS elsewhere. The CVC V1 service it replaces is retired.

## Setting up the refresh

An administrator sets this up once. It then runs on its own.

1. **Administration > System Management > Job Type Management**: add a job type with any name and the
   JAVA Class Name `io.github.carlos_emr.carlos.commn.jobs.CanadianVaccineCatalogueJob`.
2. **Administration > System Management > Jobs Management**: add a job of that type, enabled. Pick any
   provider as **Run As Provider**: it only names who ran the refresh in the audit log.
3. Set the job's schedule. Weekly, Sunday at 3 am, is suggested; the catalogue changes slowly.

There is no "run now": the first refresh waits for the schedule.

Each refresh downloads and reads the whole bundle before touching the database. It then replaces
the stored catalogue in one transaction, so a failed download or a malformed bundle leaves the
previous catalogue in place. A failure is logged and the job runs again at its next time.

## What it changes

- Once a catalogue has been loaded, the prevention page offers **Add by Brand/Generic/Lot#** and
  searches the catalogue. A non-empty `cvc.url` from a CVC V1 setup also turns this search on; the
  value is no longer used as an address.
- Every active NVC generic becomes a prevention type, named by its SNOMED synonym without the
  bracketed abbreviation, for example "Influenza quadrivalent vaccine". The exception is a generic that
  a `CVCMapping` row folds into an existing type in the loaded list. The prevention list is read
  once, so new types appear after CARLOS restarts.
- Adding a prevention for a generic offers its NVC brands, and choosing a brand offers its NVC lot
  numbers and expiry dates.
- NVC V2 has no Ontario ISPA flag. Each vaccine keeps the flag the previous catalogue recorded, and
  vaccines new to the catalogue start without it. No screen edits the flag yet.

## The prevention list without legacy immunizations (opt-in)

`oscar/prevention/NVCpreventionItems.xml` is `PreventionItems.xml` without its 57 items under the
**Immunizations** heading. It is for clinics that take every immunization type from the catalogue.
A unit test keeps it identical to `PreventionItems.xml` otherwise. CARLOS uses it only when
`carlos.properties` sets:

```properties
PREVENTION_ITEMS=classpath:oscar/prevention/NVCpreventionItems.xml
```

Do not set this for a live clinic until its legacy immunization types have been migrated. With
this list:

- existing records of the 57 legacy types (`Inf`, `Pneu-C`, `MMR`, `DTaP-IPV`, `H1N1` and the rest)
  no longer show on the prevention page, in its print-out or in the eChart Preventions box. They
  stay in the database, and the newer preventions summary and flowsheets still list them, but they
  cannot be edited or deleted, because saving checks the type against the loaded list. Their
  decision-support warnings still appear, with no row to act on;
- decision support (`prevention.drl`) and the child immunization report key on the legacy names, so
  they do not count immunizations recorded under catalogue names and "due" warnings keep appearing;
- CDS export cannot set the type of any immunization, and CDS import files every immunization as
  `OtherA`, because CDS maps types through the loaded list;
- eForm prevention tags with a legacy type are skipped;
- the "Other" add menu is empty, saving custom stop-sign settings drops legacy entries, and a
  `CVCMapping` row can no longer fold a generic into a legacy type.

Removing the setting, and restarting, brings the full list back.
