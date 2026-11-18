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
The configured statement is read at most once for each filter invocation.

All twelve real-HTTP metadata cases pass after repair, including every servlet
header accessor, unrelated-header preservation, and wildcard GET/HEAD conditions.
All 611 related Java 25 filter cases pass with no failures, errors, or skips.
The existing changed-line floors rise from 95.12/95.26/90.54 percent to
97.53/97.83/94.44 percent for lines/instructions/branches, using fresh source-bound
JaCoCo evidence.

Pre-repair HTTP report SHA-256:
`723e1a9ae3176eeb5a348051efad564ec3eadfc80a506078f90ed9bed9f37303`.
Post-repair twelve-case HTTP report SHA-256:
`5fc626e4dc3f1bb8027a9704382d3cdb5ae09d4b3869c4e3a9d9ffcd3f5a2d08`.
Related 611-case report SHA-256:
`a2795d68ec9637c6bb91b1dc6103d91267d82726eea5487c30932d1ec06f17c2`.

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

The three additional protocol controls preserve POST preconditions, empty 204 HEAD
responses, and complete body metadata/caching when the notice was already printed.
The complete Node execution above precedes these Java-only controls; the combined
qualification exercises their final source together.

The unmodified installed b4048148 application also reproduces the defect through
its full nginx/Tomcat/filter chain: the new strict browser check fails because the
decorated GPL page retains its bare-file ETag. The original manifest is restored
byte exactly afterward; the temporary entrypoint is removed, properties and restart
count remain unchanged, and service health passes. Pre-fix installed JUnit SHA-256:
`6603d689fb5429ad7616d9a57595e4dded014c5b126d71be118978075440a185`.

The first combined installed run (`08fb174856d298ec58467b5397884cef92706fd2`)
passes decorated GET but exposes a second HEAD interaction: the authenticated
logout filter creates a script-only body when DefaultServlet supplied metadata
without opening a body channel. The outer capture then advertises those 1,749
bytes instead of the 23,784-byte GET. The failed default-profile report is retained
with SHA-256 `92661a6910cb83f5cad98a9c30792bdb0ed89f076da98127c15e236e0ae68c46`;
its other 27 workflows pass, and properties and health are restored.

A new real-Tomcat regression reproduces that interaction with the production
capture, logout and privacy filters: HEAD advertises 1,749 bytes versus a 292-byte
GET. Its thirteen-case before report has one regression failure and twelve
passing controls; SHA-256
`550730c1459f873cf5541090390ac078a63badcfc7101d0da6da8e435e1bf58f`.
The logout filter now leaves a HEAD response without a body channel empty and
removes its unrepresentative length. Writer and stream controls preserve the
existing full-body paths.

All 666 related filter cases now pass without failures, errors or skips,
including thirteen metadata HTTP cases and three additional HEAD channel
controls. Report SHA-256:
`7d2103745ab866a5a97fcc7eb5a70e8746d2c88bb5b956997d7502a359ce1b27`.
Privacy floors remain 97.53/97.83/94.44; logout floors rise from
90.48/92.52/75.00 to 91.49/93.25/78.26 percent. These are source-bound fresh
measurements. Positive installed verification of this follow-up and its next
combined DEB are pending; the failed combined package is not labelled complete.

The Ubuntu 26.04 installed component retake passes the static HTML workflow,
including HEAD, alongside both MCEDT mailbox variants. Three native cases pass
with no residue; JUnit SHA-256 `bb659a9c83b1bf1a285fab5b8aec8c4b5a5d9eb2f53460ed652538e07245d8b6`.
All 10,755 original application files, properties and test inputs are restored.
The scoped audit explains exactly three owned invalid-delete refusals and four
recognized events; its raw exit 1 is retained. This six-class temporary overlay
does not replace the pending combined-DEB qualification.

The three static-analysis audit markers are documented at their exact sites:
the untrusted header selects only HTTP wildcard existence behavior, and fixed
ASCII field-name comparisons implement the case-insensitive HTTP protocol.
A fresh 666-case rerun passes (JUnit SHA-256
`69aa00afcc52a3f41a96761e6e20563d1c28f4d208ff81fe31ab6bdad6b87039`);
source-bound floors are retained. Evidence SHA-256
`0cf9583c95483bb17bdcf59120b9844de3b6a5475b58632bfe17e7f7b0cc8cb6`.

## Static cache review follow-up

Four of seventeen real HTTP cases fail on the previous public implementation:
both two-mebibyte pages commit bare-file validators, and both cacheable request
variants omit X-Requested-With from Vary. Before JUnit SHA-256:
`d11d88a1e97abe8b1e7f73fa7b7448194466690bb41bb4aaede674836d976b2c`.

Static metadata now passes through a status-aware wrapper before any body write
can commit. It withholds the bare validators for decorated full responses and
restores them for exact partial responses or a non-HTML final type. It retains
existing Vary tokens and wildcard values, adds X-Requested-With to ordinary and
AJAX variants, and reapplies the variant after resets and downstream Vary writes.
The current full-filter HTTP fixture includes authentication, capture and logout
wrappers for the large captured page. All 631 selected Java cases pass with zero
failures/errors/skips. Changed-source coverage is 100% lines, 99.510284%
instructions and 99.462366% branches, retaining both parents' floors.

- Current JUnit SHA-256: `2acbf64d741c9e89933dcccc89446a2c2055bd8f43a75bcf7796ef5dad1984e8`.
- Source-bound coverage SHA-256: `2ce8f0a367a668686ccf64d6b2c211a95ffd03dc22ee15c38b843cc419b94664`.

The native check now adds same-URL browser AJAX/ordinary fetches and an owned
two-mebibyte static HTML file which is removed during cleanup. Existing installed
proofs above precede this follow-up. The rebuilt-DEB and browser retake for these
latest changes is pending the next combined qualification.
