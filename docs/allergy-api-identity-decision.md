# Allergy identity on the 2026.08 APIs

The published REST `AllergyTo1` and SOAP `AllergyTransfer` contracts remain
unchanged for #4679. Adding a field to either shared DTO would change the
release API shape; SOAP clients generated from the published WSDL and strict
REST readers are supported defaults. No evidence in this repository identifies
all deployed consumers or proves that they tolerate an additional field.
The compatibility profiles documented in [XML profiles](rest-soap-xml-profiles.md)
change only their named birthday/character behaviors. They do not imply support
for additional allergy fields.

REST exposes the stored DrugRef identifier, type code and regional identifier;
SOAP exposes the type code and regional identifier. Neither exposes stored ATC.
A type-8 class allergy therefore cannot be resolved reliably from these old
responses alone. Do not infer an ATC class from description text or reinterpret
a DrugRef identifier as an ATC code. Historical shortcut identifiers 44452 and
44159 identify unrelated products in the packaged DrugRef and must not be used
as class identities.

The existing CDS export uses `AllergyAtcClass.classCodeOf`: explicit type-8 ATC
class codes and the exact recognized historical shortcut records resolve to
class codes. Stored product ATC and resolved class ATC are different facts.
The authoritative allergy row retains its raw ATC, DrugRef identifier and type.
This decision does not erase, rewrite or fabricate those values.

The issue expressly permits a documented decision to retain the release API
contracts. An integration requiring ATC identity needs a separately versioned
read contract with explicit raw ATC, raw DrugRef identifier, type and resolved
class fields, the same patient/consent guards, and its own reader/schema tests.
The current release APIs cannot promise that capability. This disposition is
an explicit contract limitation, rather than a claim that missing metadata was
added or that unidentified clients were qualified.
