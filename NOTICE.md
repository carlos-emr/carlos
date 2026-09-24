# CARLOS EMR - Project Notice

## Project Identity

CARLOS (Clinical Assisting Recording Ledger Open Source) is an independent
open-source electronic medical records system. This project is developed and
maintained by the CARLOS community.

## Project Heritage

This codebase has evolved through multiple open-source projects:
- **CARLOS** (2026-present) - Current independent project
- **OpenO EMR** - Intermediate fork
- **OSCAR McMaster** - Original project (2001-2020)

## 2025-2026 Fork Transition

As part of establishing CARLOS as an independent project, the codebase underwent
namespace reorganizations through multiple forks:
- `org.oscarehr.*` → `ca.openosp.openo.*` (OpenO EMR fork)
- `ca.openosp.openo.*` → `io.github.carlos_emr.carlos.*` (CARLOS fork)

These changes touched most source files but represent structural reorganizations
rather than functional modifications of individual files.

## Copyright & Attribution

This software contains code from multiple contributors over 20+ years:

- Department of Family Medicine, McMaster University (2001-2020)
- Centre for Research on Inner City Health, St. Michael's Hospital, Toronto (2005-2012)
- OSCARservice, OpenSoft System (2006-2016)
- Peter Hutten-Czapski (2007-2026+)
- Indivica Inc. (2008-2012)
- PeaceWorks Technology Solutions (2011-2012)
- University of Victoria, Department of Computer Science (2013-2015)
- KAI Innovations Inc. (2014-2015)
- The Pharmacists Clinic, University of British Columbia (2015-2019)
- Magenta Health (2024+)
- OpenOSP and the OpenO EMR contributors (2025-2026)
- CARLOS Contributors (2026+)
- And many other contributors

Dates are mostly taken from the copyright notices and may not reflect contribution spans.
All original copyright notices are preserved in source files as required by
the GNU General Public License.

## Data Attribution

- Email PDF passphrase generation uses an English wordlist derived in part from
  EFF's Long Wordlist for dice-generated passphrases, published by the
  Electronic Frontier Foundation.
  Source: https://www.eff.org/files/2016/07/18/eff_large_wordlist.txt
  License: Creative Commons Attribution 3.0 United States (CC BY 3.0 US),
  https://creativecommons.org/licenses/by/3.0/us/
  Local changes include filtering for lowercase ASCII words, removing
  patient-unfriendly terms, and reducing the list to 4096 entries.

## Trademark Notice

"OSCAR" is an official mark of McMaster University. Any references to
OSCAR in this codebase are for historical and descriptive purposes only and
do not imply endorsement by or affiliation with McMaster University.

## No Affiliation Disclaimer

CARLOS has no organizational affiliation with:
- McMaster University or the Department of Family Medicine
- OpenOSP organization
- Any other organization referenced in historical copyright notices

Listing OpenOSP under Copyright & Attribution above records that CARLOS
inherits code from the OpenO EMR fork and preserves its contributors'
attribution, as the GPL requires. It does not imply any endorsement by,
sponsorship from, or relationship with that organization.

The presence of copyright notices from these organizations reflects the
open-source heritage of the code and compliance with GPL requirements to
preserve attribution, not any current organizational relationship.

## In-Application Notices

Two authenticated pages present a user-facing summary of this file and must be
kept in step with it when contributors or lineage change:

- **About** — `src/main/webapp/WEB-INF/jsp/encounter/About.jsp`, route
  `/encounter/ViewAbout` (eChart, prescription, fax cover page, messenger)
- **Licence** — `src/main/webapp/WEB-INF/jsp/encounter/License.jsp`, route
  `/encounter/ViewLicense`

Those pages are summaries for clinicians, not a replacement for this file or for
the per-file copyright headers, which remain the authoritative attribution
record under the GPL.
