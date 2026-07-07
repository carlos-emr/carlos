# OSCAR19 apconfig compatibility gaps blocked by removed or unproven CARLOS backends

## Purpose

Track the shared `apconfig.xml` behaviors that still cannot be claimed as OSCAR19-equivalent after the July 7, 2026 compatibility pass.

This issue intentionally aggregates all flagged items into one ticket.

## Comparator Reference Used

- OSCAR baseline: `src/test/resources/oscar/eform/oscar19-apconfig.xml`
- CARLOS runtime file: `src/main/resources/oscar/eform/apconfig.xml`
- Review rule: shared `ap-name` values are behavior contracts, not just name matches

## Flagged Inventory

### Missing OSCAR19 tags with no proven CARLOS replacement

- `onEDB`
- `onGTPAL`

### Shared tags with broadened CARLOS query semantics

- `RABIES_immunization_date`
- `bcg_immunization_date`
- `chol_ecol_o_immunization_date`
- `cholera_immunization_date`
- `dpt_polio_immunization_date`
- `dptp_hib_immunization_date`
- `dt_ipv_immunization_date`
- `dtap_hbv_ipv_hib_immunization_date`
- `dtap_hib_immunization_date`
- `dtap_immunization_date`
- `dtap_ipv_hb_immunization_date`
- `dtap_ipv_hib_hb_immunization_date`
- `dtap_ipv_hib_immunization_date`
- `dtap_ipv_immunization_date`
- `flu_immunization_date`
- `hepa_immunization_date`
- `hepab_immunization_date`
- `hepb_immunization_date`
- `hib_immunization_date`
- `hpv_vaccine_9_immunization_date`
- `hpv_vaccine_immunization_date`
- `ipv_immunization_date`
- `je_immunization_date`
- `measles_immunization_date`
- `men_p_ac_immunization_date`
- `men_p_acwy_immunization_date`
- `menb_b_immunization_date`
- `menc_c_immunization_date`
- `mmr_immunization_date`
- `mmrv_immunization_date`
- `mr_immunization_date`
- `mumps_immunization_date`
- `opv_immunization_date`
- `pediacel_immunization_date`
- `pneu_c_immunization_date`
- `pneumovax_immunization_date`
- `prevnar_13_immunization_date`
- `prevnar_immunization_date`
- `rabies_immunization_date`
- `rot_immunization_date`
- `rsv_immunization_date`
- `rubella_immunization_date`
- `shingles_immunization_date`
- `t_immunization_date`
- `tbe_immunization_date`
- `td_immunization_date`
- `td_ipv_immunization_date`
- `tdap_ipv_immunization_date`
- `tdp_immunization_date`
- `typh-o_immunization_date`
- `typhoid_immunization_date`
- `varicella_immunization_date`
- `yf_immunization_date`

## Evidence

### Missing-tag cluster

#### `onEDB`

- OSCAR19 status: tag exists in the comparator
- CARLOS status: tag is absent from `src/main/resources/oscar/eform/apconfig.xml`
- Proof gap: the OSCAR-era backend path depended on `formONAREnhancedRecord`, which is not present in CARLOS
- Recommended next action: confirm whether CARLOS has a replacement data source for this concept; if not, decide whether to reintroduce support or explicitly retire the OSCAR contract

#### `onGTPAL`

- OSCAR19 status: tag exists in the comparator
- CARLOS status: tag is absent from `src/main/resources/oscar/eform/apconfig.xml`
- Proof gap: the OSCAR-era backend path depended on `formONAREnhancedRecord`, which is not present in CARLOS
- Recommended next action: confirm whether CARLOS has a replacement data source for this concept; if not, decide whether to reintroduce support or explicitly retire the OSCAR contract

### Immunization cluster

Shared pattern:

- OSCAR19 generally uses exact `prevention_type` matching for these tags
- CARLOS broadens the same shared tags with `LIKE`, alternate aliases, or other expanded predicates
- That may be intentional migration support, but it is still a semantic change on an OSCAR19 tag name
- Until product or domain owners confirm that the broader match is the intended replacement contract, these tags remain unproven for OSCAR19-equivalence

Representative examples:

- `flu_immunization_date`
  - OSCAR19: exact `p.prevention_type = 'Flu'`
  - CARLOS: exact `Flu` plus an added influenza-style `LIKE` branch
- `dtap_immunization_date`
  - OSCAR19: exact `DTaP` and exact `dTap` entries
  - CARLOS: broadened branches using additional `LIKE` patterns
- `varicella_immunization_date`
  - OSCAR19: exact `Varicella`
  - CARLOS: exact `Varicella`, exact `VZ`, plus an added `LIKE` branch

Recommended next action for the full cluster:

- Decide whether shared OSCAR19 immunization tags must stay exact-match compatible, or whether the broader CARLOS behavior is the new approved contract
- If exact compatibility is required, restore the OSCAR19 predicates on the shared tag names and move broader matching to CARLOS-only tag names
- If broader matching is approved, record that approval explicitly so these tags stop being treated as unresolved compatibility drift

## Open Questions

- Should the 53 broadened immunization tags be restored to exact OSCAR19 semantics now, or formally accepted as an intentional CARLOS compatibility extension?
- Is there a supported CARLOS replacement backend for `onEDB` and `onGTPAL`, or should those behaviors be declared retired?
- Do we want a follow-up cleanup for duplicate shared tag definitions even where behavior is currently equivalent?
