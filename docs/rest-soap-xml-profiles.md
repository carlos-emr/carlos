# XML compatibility profiles

The default remains the published WSDL contract. No header is required. Profiles
are selected explicitly per HTTP request with `X-Carlos-XML-Profile`; SOAP and
both application REST servers use the same selection. They do not change JSON,
authentication or authorization. Requests must continue to specify their media
type and authentication normally.

| Header value | XML behavior |
| --- | --- |
| `published` (or no header) | Literal character values, empty NUL sentinel, and birthday `xs:dateTime` with the calendar's zone offset. |
| `moxy-legacy` | Historical MOXy Calendar field-state formatting (including absent offsets and date-only birthdays) and the `AppointmentExtTo` sequence with `hPhoneExt` before `lastName`. Literal characters remain literal. |
| `moxy-date-only-birthday` | Retains the historical MOXy birthday representation, including a date-only value for a cleared date-only Calendar. Other properties retain the published contract. |
| `glassfish-numeric-characters` | Character scalars, arrays, list entries and map keys use decimal UTF-16 code units from 0 through 65535, as the historical Glassfish reader expects. Other properties retain the published contract. |
| `glassfish-legacy` | Numeric character handling plus the historical flat `DrugTransfer` sequence with `eTreatmentType` last, including nested prescription drugs and bulk responses. |

For example, numeric `<code>0</code>` is U+0000 under the numeric profile, while
literal `<code>0</code>` is U+0030 under the published and MOXy birthday profiles.
The server never infers a profile from field content. Numeric clients must send
the header for **reads and writes**, including each request in a paged export.
No deployment-wide switch or remembered session state changes another client.

The response identifies the selected profile and includes the profile header
in `Vary`. Clients must retain this header through their reverse proxy and verify
it when selecting an alternate format. Unknown, blank, duplicate or combined
profile selections fail explicitly (REST 400; SOAP client fault), rather than
falling back. XML binding errors also fail: an invalid character must not become
a null property or null map key. A profile grants no permission to fetch a
patient or field that the caller cannot otherwise access.

These profiles cover specific compatibility differences, rather than emulating an
entire JAXB provider. The numeric-only profile retains the published drug
sequence. Clients using an old strict drug schema select `glassfish-legacy`;
only its scalar `eTreatmentType` is buffered until the end of each drug. Other
properties stream normally, and output failures propagate. The Glassfish profiles retain
the corrected calendar/date-time behavior. The separate `moxy-legacy` profile buffers the scalar
appointment members between `lastName` and `hPhoneExt`; it retains MOXy Calendar field-state formatting.
The birthday-only profile continues to change only birthdays. No profile publishes a different WSDL
or changes the clinical precision carriers or either JSON surface.

Rollback requires client coordination: a server without these profiles cannot
honor the header. Keep the release candidate and its configuration together;
do not route profiled traffic to an older instance. Restore the client to the
published contract before removing profile support. The default contract has
no migration or database requirement.

Optional Date properties in SOAP transfer objects accept absent, empty and whitespace-only values as null. Malformed values produce a fault, and real date-time instants retain fractional seconds. All 45 current Date properties keep their published xs:dateTime schema type under both JAXB providers. This does not alter JSON date representation or unrelated REST XML date recovery.

The published literal profile rejects XML 1.0 control characters, isolated surrogates and U+FFFE/U+FFFF instead of emitting malformed XML or omitting a value. The empty NUL sentinel remains supported. Explicit numeric profiles retain all unsigned UTF-16 code units, including values that cannot be written literally. Real SOAP reader/writer tests verify a published-output fault and successful numeric round trips; this does not change either JSON mapper.

Appointment `editVersion` remains a JSON-only editing token. It is omitted from XML because the published beta6 schemas have no such element; both JSON surfaces continue to read and write the token.

The native binary matrix is reproducible with `scripts/ci/run-native-xml-binary-contracts.py`.
It verifies the independently pinned beta6 Debian package, runs the same native fixture producer
in separate JVMs with each binary's own libraries, validates all 56 candidate default writes,
and sends explicit legacy output through the unchanged old default and schema-validating readers.
Pre-existing old reader/context failures remain explicit per case. Results include input hashes
and do not replace the broader release wire/API or installed-client gates. CI retains these artifacts.
