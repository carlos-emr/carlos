# Vaccine catalogue (National Vaccine Catalogue V2)

CARLOS keeps a local copy of the Canadian vaccine catalogue for the prevention screens: generic
vaccines, brands, DINs, holders and lot numbers with expiry dates. It is refreshed from the National
Vaccine Catalogue V2 bundle at `https://nvc-cnv.canada.ca/fhir/v2/Bundle/NVC`. The address is fixed;
no setting or user can point CARLOS elsewhere. The CVC V1 service it replaces is retired.

## Setting up the refresh

An administrator sets this up once. It then runs on its own.

1. **Administration > System Management > Job Type Management**: add a job type with any name and the
   JAVA Class Name `io.github.carlos_emr.carlos.commn.jobs.CanadianVaccineCatalogueJob`.
2. **Administration > System Management > Jobs Management**: add a job of that type, enabled.
   **Run As Provider** only names who ran the refresh in the audit log; use the administrator's own
   account.
3. Set the job's schedule: choose **minute 0**, **hour 3** and **weekday Sunday**, once a week. Choose
   the minute as well: the dialog defaults to every minute, which would refresh 60 times in that hour.

There is no "run now": the first refresh waits for the schedule.

Each refresh downloads and reads the whole bundle before touching the database. A bundle without
generics, brands, lots or any brand-to-generic link is refused. Otherwise the stored catalogue is
replaced in one transaction, so a failed download or a malformed bundle leaves the previous
catalogue in place. A failure is logged and the job runs again at its next time. A download is
abandoned past 64 MB or five minutes.

## What a refresh changes

- **New prevention types.** Every active NVC generic is added as a prevention type, unless a
  `CVCMapping` row folds it into an existing type in the loaded list. The type is named by the
  generic's SNOMED synonym without its bracketed abbreviation, for example "Influenza quadrivalent
  vaccine".
  - A name stays the same across refreshes, even if NVC later rewords or retires that vaccine, so
    records filed under it keep their type. A retired vaccine stays offered as a type, so its old
    records can still be edited. On a database that still holds the CVC V1 catalogue, the first
    refresh likewise keeps the V1 names of the generics it already offered.
  - If a new vaccine's name would repeat a kept one, its SNOMED code is added in parentheses.
  - Some names contain commas (20 in October 2026), so a `PREVENTION_CONFIG_SETS` list cannot
    name them.
  - The prevention list is read once, so new types appear after CARLOS restarts.
- **Recording.** Adding a prevention for a catalogue type offers its NVC brands, and choosing a brand
  offers its NVC lot numbers and expiry dates.
- **Legacy names elsewhere.** Immunizations recorded under catalogue type names are not counted by
  decision support (`prevention.drl`) or the child immunization report, which use the legacy type
  names (`Inf`, `Pneu-C`, …). CDS export cannot set their type.
- **Ontario ISPA.** NVC V2 has no ISPA flag. Each vaccine keeps the flag the previous catalogue
  recorded, and vaccines new to the catalogue start without it. No screen edits the flag yet.
- **Admin lot numbers.** Administration's "Add Lot Number" stores the prevention type in a 20-character
  column, so most catalogue type names do not fit there. Lot numbers for catalogue vaccines come from
  the catalogue itself.

## Catalogue search (opt-in)

The prevention page can search the catalogue by brand, generic or lot number ("Add by
Brand/Generic/Lot#") instead of offering the "Pick vaccine brand/generic" picker. To turn it on, set
`cvc.url` to any value in `carlos.properties` after the first refresh and a restart, so the
prevention list already holds the catalogue's types. The value is no longer used as an address.
While the search is on, restart CARLOS after each refresh too (for example, schedule a restart
after the Sunday refresh): a vaccine type the refresh added is offered in the search at once, but
opening it shows "Prevention not found" until the prevention list is read again.

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
- CDS export cannot set the type of any immunization, and CDS import files every immunization as
  `OtherA`, because CDS maps types through the loaded list;
- eForm prevention tags with a legacy type are skipped;
- the "Other" add menu is empty, saving custom stop-sign settings drops legacy entries, and a
  `CVCMapping` row can no longer fold a generic into a legacy type.

Removing the setting, and restarting, brings the full list back.
