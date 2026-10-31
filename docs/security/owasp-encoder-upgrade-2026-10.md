# OWASP Encoder upgrade — October 2026

CARLOS upgrades `encoder` and `encoder-jakarta-jsp` together from 1.4.1 to
**1.6.0** for [GHSA-g8p6-7r8f-qrpv](https://github.com/OWASP/owasp-java-encoder/security/advisories/GHSA-g8p6-7r8f-qrpv)
([issue #5457](https://github.com/carlos-emr/carlos/issues/5457)). The moderate
advisory affects supported artifacts below 1.5.0. Interpolated fragments could
complete outer HTML/XML delimiters supplied in part by adjacent trusted text.
A call to an affected method alone does not establish an exploitable template.

## Version choice

1.6.0 is the latest published version as of October 31, 2026. It retains the
1.5.0 fixes, public APIs, Java 8 baseline, and Jakarta adapter identity. Its
JavaScript output refinement preserves interpreted values and recognized HTML
script-token protection, while changing emitted bytes. 1.5.1 is a conservative
alternative with the same advisory fix, but does not include that refinement.

[Upstream's 1.6.0 release record](https://github.com/OWASP/owasp-java-encoder/releases/tag/v1.6.0)
discloses a pre-release Daybreak review bypass. [Upstream PR #251](https://github.com/OWASP/owasp-java-encoder/pull/251)
records a favorable supplemental static review after publication. That later
review is not pre-release coverage. CARLOS's choice also depends on its own
parser, wrapper, tag, browser, build, and CI validation.

## Compatibility findings

- `SafeEncode` continues to coalesce null to empty and select the existing
  context. No application template or production wrapper behavior is replaced.
- JavaScript general/block modes escape punctuation, whitespace, and terminal
  script-name letters. For example, `script` becomes `scrip\x74`, while
  `http://google.com` becomes `http:\/\/google.com`. Encoded text is not a byte
  identity contract; interpreted string values must remain unchanged.
- The existing translated-message test used Commons Text's
  `unescapeEcmaScript`, which does not decode `\xNN`. It now evaluates the real
  JavaScript literal in Node's isolated VM and retains its quote/line-break
  checks. Existing null and OWASP delegation tests remain in place.
- The third-party AJAX login response had JavaScript-encoded a provider name
  before placing it in a Jackson JSON object. Hex escapes then survived JSON
  parsing as visible text. Jackson now serializes the original name, and the
  existing `textContent` consumer keeps markup inert. Response round-trip tests
  cover spaces, quotes, backslashes, Unicode, and markup-like names.
- Signature-stamp response URLs and the two annual-review checkbox arrays now
  use the null-safe `SafeEncode.forJson` wrapper. JSON parsers reject JavaScript
  `\xNN` escapes. Complete quoted JSON strings use the upstream JSON context,
  which also escapes HTML delimiters for the planner's JSON script element.
- CPP note metadata remains raw text until `viewNotes.jsp` HTML-encodes the
  complete note. Pre-encoding metadata as JavaScript would expose hex escapes
  in ordinary text such as treatment and family-history details.
- CDATA preserves parsed text but can expand 13× and split parser events.
  XML-comment hyphens become `~`, a documented lossy policy. On the starting
  release branch, these contexts have no production consumers outside wrapper,
  tag, and tag-library definitions.
- Source inspection found no direct `EncodedWriter` or low-level `Encoders`
  consumers in CARLOS. Its complete-value Writer facades therefore do not need
  the new explicit-flush migration for direct `EncodedWriter` use.
- Trusted surrounding fragments must still be valid in their parser context.
  Encoding cannot repair a delimiter already completed by trusted text or one
  formed entirely by trusted text around empty input.

## Validation

`EncoderBoundaryUnitTest` exercises nonempty partitions of script delimiters,
CDATA boundaries, and XML-comment hyphens through scalar, Writer, and CARLOS-tag
surfaces, using HTML/XML parsers rather than upstream-output parity alone.

A separate Playwright/Chromium 154 check passed 708 fixtures generated from the
actual CARLOS scalar, Writer, and tag classes, including mixed-case script
boundaries, quotes, line breaks, Unicode, URLs, and empty values. It verified
interpreted values, script completion, absence of injected elements, and zero
page errors. The same check fails with the original 1.4.1 core on its first
split closing-script fixture. This is parser-fixture validation, not a full
clinical-workflow browser run.

Five actual `Login2Action` JSON responses also passed through the third-party
JSP's submit handler in Playwright. Provider names retained spaces, quotes,
backslashes, Unicode, and markup-like text, with no injected elements or page
errors. The five JSON round-trip unit cases fail against the original producer.
Both actual annual-review JSP JSON builders also passed a Chromium check of
`JSON.parse` and checkbox restoration; their previous JavaScript-encoding
builders fail this check. The expanded focused set passes 184 tests without
failures or skips, including signature-status/upload JSON and CPP metadata.

Reproduce the permanent focused tests with JDK 25, Maven, and Node installed:

```sh
mvn -B -ntp -Dtest=EncoderBoundaryUnitTest,SafeEncodeUnitTest,CarlosEncodeTagUnitTest,Login2ActionForcedPasswordResetUnitTest test
```

The Maven lock goal changes only the versions and SHA-512 integrity values of
the two OWASP artifacts; dependency membership and scopes are unchanged. Keep
the CARLOS release version, SCM metadata, and published migrations unchanged.
