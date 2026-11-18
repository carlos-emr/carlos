# Penicillin and Sulfa allergy shortcuts

The shortcuts save ATC class allergens: `PENICILLINS` under `J01C`, and
`SULFONAMIDES` under `J01E`, type 8, without a DIN or a DrugRef row identifier.
This follows [phc007's clinical direction on #4576](https://github.com/carlos-emr/carlos/issues/4576#issuecomment-6178044520).
Rows saved by the old shortcuts are recognized by their exact old identity;
checks do not rewrite clinical records. An amendment saves the new class identity.

`J01E` includes trimethoprim. Its alert is retained deliberately, as
[phc007 requested on #4680](https://github.com/carlos-emr/carlos/issues/4680#issuecomment-6193749072).
The [WHO ATC index](https://atcddd.fhi.no/atc_ddd_index/?code=J01E&showdescription=no)
records this class and its trimethoprim subgroup.

## Combination and topical products

An ATC prefix match produces a warning. DrugRef's AHFS and ATC class-name
warnings are also retained. A product outside the shortcut's ATC prefix needs
an additional comparison: the exact DIN's active ingredients are checked against
the shortcut's ingredient names. This avoids depending on AHFS availability or
warning on every product in a broad combination/topical ATC branch.

The ingredient approach was approved for this PR. The names in
`AllergyAtcClass.Shortcut.coversIngredient` cover systemic penicillins from
[J01CA](https://atcddd.fhi.no/atc_ddd_index/?code=J01CA&showdescription=no),
[J01CE](https://atcddd.fhi.no/atc_ddd_index/?code=J01CE&showdescription=no), and
[J01CF](https://atcddd.fhi.no/atc_ddd_index/?code=J01CF&showdescription=no),
and antibacterial sulfonamides from
[J01EB](https://atcddd.fhi.no/atc_ddd_index/?code=J01EB&showdescription=no),
[J01EC](https://atcddd.fhi.no/atc_ddd_index/?code=J01EC&showdescription=no), and
[J01ED](https://atcddd.fhi.no/atc_ddd_index/?code=J01ED&showdescription=no).
These references were checked against the 2026 index on October 15, 2026.
Catalogue synonyms include penicillin G/V, methicillin, sulfamethazine and
sulfisoxazole. Sulfacetamide, silver sulfadiazine and sulfasalazine retain the
topical/intestinal warning coverage identified in #4680. Trimethoprim is explicit.

The comparison uses complete ingredient names, with a word boundary before a
salt/form suffix. It does not infer an allergy from a brand, a free-text allergy
description, a `sulfa` substring, or an entire topical ATC branch. Non-antibiotic
sulfonamides such as hydrochlorothiazide, celecoxib and acetazolamide are negative
regression controls. Future catalogue terminology or ingredient additions need
review of this explicit vocabulary.

Missing DINs, missing products, failed lookups, and incomplete ingredient lists
leave the out-of-class shortcut **not checked**. They cannot produce an ingredient
all-clear. Confirmed ATC/DrugRef warnings are preserved if the supplementary lookup
fails. The AJAX check carries the DIN, and the full prescription page bypasses its
ATC-only cache when checking exact-product ingredients. That page renders confirmed
warnings, each unchecked allergen, and an explicit failure notice separately.

## Regression coverage

`RxShortcutIngredientAllergyUnitTest` simulates a reference without AHFS and checks
both new class rows and old shortcut rows. It covers combination/topical matches,
same-branch negative controls, non-antibiotic sulfonamides, trimethoprim, and
missing/failed ingredient responses. `allergy-class-shortcut-alert` remains the
packaged-install workflow for shortcut storage and real product warnings.
