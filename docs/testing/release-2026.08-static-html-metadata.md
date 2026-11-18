# Decorated static HTML metadata (#5936)

On the release implementation, real Tomcat DefaultServlet requests through the
production response-default and privacy filters reproduce three failures:
HEAD advertises the undecorated file length, decorated GET retains the file's
validators, and a conditional GET returns 304 after the print statement changes.
Four controls pass before repair: an empty statement, excluded HTML, AJAX, and
non-HTML retain the file bytes, length, and conditional caching.

The privacy filter now omits bare-file ETag, Last-Modified, and Accept-Ranges
metadata when it decorates a successful representation. HEAD omits the bare-file
length. For GET/HEAD resources whose MIME type is HTML and whose statement will
be appended, downstream cache validation does not see obsolete file ETags or
modification dates. Other request data, including Range/If-Range, is retained.
Wildcard existence conditions still return 304, with no bare-file metadata.
The configured statement is read once for each filter invocation.

All nine real-HTTP metadata cases pass after repair, including every servlet
header accessor, unrelated-header preservation, and wildcard GET/HEAD conditions.
All 608 related Java 25 filter cases pass with no failures, errors, or skips.
The existing changed-line floors rise from 95.12/95.26/90.54 percent to
96.30/96.66/91.27 percent for lines/instructions/branches, using fresh source-bound
JaCoCo evidence.

Pre-repair HTTP report SHA-256:
`723e1a9ae3176eeb5a348051efad564ec3eadfc80a506078f90ed9bed9f37303`.
Post-repair nine-case HTTP report SHA-256:
`6c8db5e3b01839d61bac67dc0bfe03c75489a0c90d8624257ef37eb4a2d8974a`.
Related 608-case report SHA-256:
`6cb9b86bf98dd80ff25936f8bca0db6a594377fdd361ba6264994a9ccc69f09d`.

`privacy-static-html-metadata` is an active manual check for the host serving the
packaged install. It reads only the installed GPL page and its file date and
checks decorated GET, HEAD, conditional file-date GET, and wildcard behavior.
It honors `CARLOS_INSTALLED_WEBAPP_DIR` and changes no clinical data or settings.
Native installed qualification is pending the next package; `b4048148` lacks this
fix. The separate partial-response repair in #6226 remains an integration control.

All 8,111 complete Node cases pass on Node 22.23.5 with zero failures, skips,
cancellations, or TODOs, and all 570 test-source fingerprints verified. The case
inventory is generated from that actual execution. Node report SHA-256:
`a622abac48d99d12fa96845233e30296f8984a8437f73aa06268ed47661b5a01`.
