/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.eform.data;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * Pins the CDN-jQuery local alias.
 *
 * <p>Much of the shared-eForm corpus loads jQuery from {@code code.jquery.com} or
 * {@code ajax.googleapis.com}. The PDF render browser cannot reach any off-origin host by design,
 * so those forms would render with their scripts dead. Rather than open egress — which would mean
 * loosening the proxy bypass, the CSP and the network gate, and would still leave a
 * top-level-navigation exfiltration path CSP cannot close — the reference is rewritten to the
 * locally deployed bundle.</p>
 *
 * <p>The rewrite runs at the string level in {@code setContextPath}, so it applies to the
 * interactive viewer as well as the render path. The jsoup DOM pass is deliberately render-only.</p>
 */
@DisplayName("EForm CDN jQuery local alias")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormCdnJqueryAliasUnitTest {

    private static final String LOCAL_BUNDLE = "/carlos/eform/displayImage?imagefile=jquery-1.12.0.min.js";

    private static String normalized(String bodyHtml) {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>" + bodyHtml + "</body></html>");
        // No enableRenderNormalization(): the alias must work on the plain viewer path too.
        eform.setContextPath("/carlos");
        return eform.getFormHtml();
    }

    @Test
    @DisplayName("should alias a code.jquery.com reference to the locally deployed bundle")
    void shouldAliasCodeJqueryReference_toLocalBundle() {
        String html = normalized("<script src=\"https://code.jquery.com/jquery-2.2.1.min.js\"></script>");

        assertThat(html).contains(LOCAL_BUNDLE);
        assertThat(html).doesNotContain("code.jquery.com");
    }

    @Test
    @DisplayName("should alias a googleapis reference to the locally deployed bundle")
    void shouldAliasGoogleapisReference_toLocalBundle() {
        String html = normalized(
                "<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js\"></script>");

        assertThat(html).contains(LOCAL_BUNDLE);
        assertThat(html).doesNotContain("ajax.googleapis.com");
    }

    @Test
    @DisplayName("should alias single-quoted and plain-http spellings the corpus also uses")
    void shouldAliasAlternateSpellings_forTheSameAsset() {
        assertThat(normalized("<script src='https://code.jquery.com/jquery-2.2.1.min.js'></script>"))
                .contains(LOCAL_BUNDLE)
                .doesNotContain("code.jquery.com");
        assertThat(normalized("<script src=\"http://code.jquery.com/jquery-2.2.1.min.js\"></script>"))
                .contains(LOCAL_BUNDLE)
                .doesNotContain("code.jquery.com");
    }

    @Test
    @DisplayName("should still alias the legacy relative jQuery spellings in both quoting styles")
    void shouldAliasLegacyRelativeSpellings_toLocalBundle() {
        assertThat(normalized("<script src=\"jquery-1.12.0.min.js\"></script>")).contains(LOCAL_BUNDLE);
        assertThat(normalized("<script src=\"/eform/jquery-1.12.0.min.js\"></script>")).contains(LOCAL_BUNDLE);
        // Single-quoted is not hypothetical: real OSCAR Galaxy packages ship
        // src='jquery-1.12.0.min.js', which previously slipped past the alias and 404'd.
        assertThat(normalized("<script type='text/javascript' src='jquery-1.12.0.min.js'></script>"))
                .contains(LOCAL_BUNDLE);
        assertThat(normalized("<script src='/eform/jquery-1.12.0.min.js'></script>")).contains(LOCAL_BUNDLE);
    }

    @Test
    @DisplayName("should drop the CDN subresource-integrity pin when aliasing to the local bundle")
    void shouldDropSubresourceIntegrity_whenAliasingToLocalBundle() {
        // Corpus forms pin their CDN jQuery with SRI. That hash describes the CDN's bytes, so once
        // src points at our own bundle the browser finds no valid digest and refuses to execute the
        // script at all — leaving the form worse off than before the alias. Observed against real
        // OSCAR Galaxy packages: "Failed to find a valid digest in the 'integrity' attribute".
        String html = normalized(
                "<script type=\"text/javascript\" src=\"https://code.jquery.com/jquery-2.2.1.min.js\""
                + " integrity=\"sha256-gvQgAFzTH6trSrAWoH1iPo9Xc96QxSZ3feW6kem+O00=\""
                + " crossorigin=\"anonymous\"></script>");

        assertThat(html)
                .contains(LOCAL_BUNDLE)
                .doesNotContain("integrity")
                .doesNotContain("crossorigin");
    }

    /** A local legacy alias must shed its old digest without a CDN script triggering cleanup. */
    @Test
    @DisplayName("should drop stale integrity pins from legacy-only library aliases in both quote styles")
    void shouldDropSubresourceIntegrity_whenAliasingLegacyLibraryScripts() {
        for (String prefix : java.util.List.of("/carlos", "..", "")) {
            for (String build : java.util.List.of("jquery-2.2.4.min.js", "jquery-1.4.2.js")) {
                for (String quote : java.util.List.of("\"", "'")) {
                    String html = normalized("<script src=" + quote + prefix + "/library/jquery/" + build
                            + quote + " integrity=" + quote + "sha256-old" + quote
                            + " crossorigin=" + quote + "anonymous" + quote + "></script>");

                    assertThat(html).as(prefix + "/" + build + " with " + quote)
                            .contains("src=" + quote + LOCAL_BUNDLE + quote)
                            .doesNotContain("integrity", "crossorigin");
                }
            }
        }
    }

    /** The older eForm-relative spellings replace the bytes in exactly the same way. */
    @Test
    @DisplayName("should drop stale integrity pins from legacy eForm-relative aliases")
    void shouldDropSubresourceIntegrity_whenAliasingLegacyRelativeScripts() {
        for (String reference : java.util.List.of("jquery-1.12.0.min.js", "/eform/jquery-1.12.0.min.js")) {
            assertThat(normalized("<script src='" + reference
                    + "' integrity='sha256-old' crossorigin='anonymous'></script>"))
                    .contains("src='" + LOCAL_BUNDLE + "'")
                    .doesNotContain("integrity", "crossorigin");
        }
    }

    @Test
    @DisplayName("should preserve SRI-like text inside other attributes when removing real pins")
    void shouldPreserveQuotedAttributeText_whenRemovingSubresourceIntegrity() {
        for (String source : java.util.List.of("../library/jquery/jquery-2.2.4.min.js",
                "https://code.jquery.com/jquery-2.2.4.min.js")) {
            for (String config : java.util.List.of(
                    "data-config=\"keep integrity='sha256-config' crossorigin='config' value\"",
                    "data-config='keep integrity=\"sha256-config\" crossorigin=\"config\" value'")) {
                String attributes = config + " data-integrity='keep' data-crossorigin='keep'";
                assertThat(normalized("<script " + attributes + " integrity='sha256-old' src='"
                        + source + "' crossorigin=\"anonymous\"></script>"))
                        .contains("<script " + attributes + " src='" + LOCAL_BUNDLE + "'></script>");
            }
        }
    }

    @Test
    @DisplayName("should read the whole opening tag when a quoted value contains a greater-than sign")
    void shouldDropSubresourceIntegrity_whenQuotedAttributeContainsTagDelimiter() {
        for (String attributes : java.util.List.of("data-config=\"a > b\"", "data-config='a > b'")) {
            for (boolean beforeSrc : java.util.List.of(true, false)) {
                String prefix = beforeSrc ? attributes + " " : "";
                String suffix = beforeSrc ? "" : " " + attributes;
                assertThat(normalized("<script " + prefix + "src='../library/jquery/jquery-2.2.4.min.js'"
                        + suffix + " integrity='sha256-old' crossorigin='anonymous'></script>"))
                        .contains("<script " + prefix + "src='" + LOCAL_BUNDLE + "'" + suffix + "></script>");
            }
        }
    }

    @Test
    @DisplayName("should remove unquoted, valueless, duplicate and mixed-case integrity attributes")
    void shouldDropSubresourceIntegrity_forAllAttributeValueStyles() {
        for (String pins : java.util.List.of(
                " INTEGRITY = sha256-old\tcRoSsOrIgIn=anonymous",
                " integrity crossorigin",
                " integrity='sha256-old' integrity=sha256-other crossorigin='anonymous' crossorigin")) {
            assertThat(normalized("<script defer src='../library/jquery/jquery-2.2.4.min.js'"
                    + pins + " data-note='keep'></script>"))
                    .contains("<script defer src='" + LOCAL_BUNDLE + "' data-note='keep'></script>");
        }
    }

    @Test
    @DisplayName("should preserve Unicode lookalikes of ASCII integrity attribute names")
    void shouldKeepUnicodeAttributeNames_whenRemovingAsciiIntegrityAttributes() {
        String attributes = "\u0131ntegrity='keep-dotless' \u0130ntegrity='keep-dotted'"
                + " cro\u017f\u017forigin='keep-long-s'";

        assertThat(normalized("<script " + attributes + " src='../library/jquery/jquery-2.2.4.min.js'"
                + " INTEGRITY='sha256-old' CROSSORIGIN='anonymous'></script>"))
                .contains("<script " + attributes + " src='" + LOCAL_BUNDLE + "'></script>");
    }

    @Test
    @DisplayName("should preserve large quoted attribute values without backtracking through their contents")
    void shouldPreserveLargeAttributeValues_whenRemovingSubresourceIntegrity() {
        String attributes = "data-config=\"" + " integrity='keep' > ".repeat(5000) + "\"";

        assertTimeout(Duration.ofSeconds(5), () ->
                assertThat(normalized("<script " + attributes + " src='../library/jquery/jquery-2.2.4.min.js'"
                        + " integrity='sha256-old' crossorigin='anonymous'></script>"))
                        .contains("<script " + attributes + " src='" + LOCAL_BUNDLE + "'></script>"));
    }

    @Test
    @DisplayName("should preserve quoted configuration and JavaScript around document.write aliases")
    void shouldPreserveDocumentWriteMarkup_whenRemovingRealIntegrityAttributes() {
        String before = "<script>window.jQuery || document.write('<script data-config=\"a > b integrity=value\""
                + " src=\"../library/jquery/jquery-2.2.4.min.js\" integrity=\"sha256-old\""
                + " crossorigin=\"anonymous\">< \\/script>')</script>";
        String expected = before.replace("../library/jquery/jquery-2.2.4.min.js", LOCAL_BUNDLE)
                .replace(" integrity=\"sha256-old\"", "").replace(" crossorigin=\"anonymous\"", "");

        assertThat(normalized(before)).isEqualTo("<html><body>" + expected + "</body></html>");
    }

    /** Attribute lookalikes must not cause another script's integrity guarantee to be removed. */
    @Test
    @DisplayName("should preserve integrity when the alias URL occurs outside the actual src value")
    void shouldKeepSubresourceIntegrity_whenAliasUrlAppearsOutsideSrc() {
        for (String attribute : java.util.List.of(
                "data-src=\"" + LOCAL_BUNDLE + "\"",
                "data-note=' src=\"" + LOCAL_BUNDLE + "\"'",
                "data-note=\" src='" + LOCAL_BUNDLE + "'\"")) {
            String unrelated = "<script src='https://cdn.example.org/analytics.js' " + attribute
                    + " integrity='sha256-analytics' crossorigin='anonymous'></script>";
            assertThat(normalized("<script src='https://code.jquery.com/jquery-2.2.4.min.js'"
                    + " integrity='sha256-old'></script>" + unrelated))
                    .contains(unrelated)
                    .doesNotContain("sha256-old");
        }
    }

    /** HTML whitespace and quote style do not change which script is receiving the local bytes. */
    @Test
    @DisplayName("should remove pins from aliased src attributes after HTML whitespace")
    void shouldDropSubresourceIntegrity_whenSrcFollowsHtmlWhitespace() {
        for (String whitespace : java.util.List.of(" ", "\t", "\n", "\f", "\r")) {
            for (String quote : java.util.List.of("\"", "'")) {
                assertThat(normalized("<script" + whitespace + "src=" + quote
                        + "https://code.jquery.com/jquery-2.2.4.min.js" + quote
                        + " integrity=" + quote + "sha256-old" + quote
                        + " crossorigin=" + quote + "anonymous" + quote + "></script>"
                        + "<script" + whitespace + "SRC" + whitespace + "=" + whitespace + quote
                        + LOCAL_BUNDLE + quote + " integrity=" + quote + "sha256-old" + quote
                        + " crossorigin=" + quote + "anonymous" + quote + "></script>"))
                        .contains(LOCAL_BUNDLE)
                        .doesNotContain("integrity", "crossorigin");
            }
        }
    }

    /** Inspecting one opening tag must preserve the surrounding JavaScript fallback string. */
    @Test
    @DisplayName("should remove a legacy-only document.write fallback's stale integrity pin")
    void shouldDropSubresourceIntegrity_whenAliasingLegacyDocumentWriteFallback() {
        String html = normalized("<script>window.jQuery || document.write('<script"
                + " src=\"../library/jquery/jquery-2.2.4.min.js\" integrity=\"sha256-old\""
                + " crossorigin=\"anonymous\">< \\/script>')</script>");

        assertThat(html).contains("document.write('<script src=\"" + LOCAL_BUNDLE + "\"")
                .doesNotContain("integrity", "crossorigin");
    }

    @Test
    @DisplayName("should keep subresource integrity on resources it did not alias")
    void shouldKeepSubresourceIntegrity_onUntouchedResources() {
        String html = normalized(
                "<script src='https://code.jquery.com/jquery-2.2.4.min.js'></script>"
                + "<script src=\"https://cdn.example.org/analytics.js\" integrity=\"sha256-abc\"></script>");

        assertThat(html).contains("integrity=\"sha256-abc\"");
    }

    @Test
    @DisplayName("should leave an unrecognised third-party script untouched so it fails visibly")
    void shouldLeaveUnknownThirdPartyScript_untouched() {
        // The alias is an exact-URL allowlist on purpose. Matching by host or prefix would silently
        // redirect scripts nobody has vetted; leaving them alone makes the render gate report them.
        String html = normalized(
                "<script src=\"https://cdn.example.org/analytics.js\"></script>"
                + "<script src=\"https://code.jquery.com/jquery-9.9.9.min.js\"></script>");

        assertThat(html)
                .contains("https://cdn.example.org/analytics.js")
                .contains("https://code.jquery.com/jquery-9.9.9.min.js");
    }

    @Test
    @DisplayName("should alias a superseded webapp jQuery build to the deployed one")
    void shouldAliasSupersededLibraryJquery_toDeployedBuild() {
        // 28 of 199 shared-corpus packages pin /library/jquery/jquery-3.6.4.min.js. That is CARLOS's
        // own library path, not a third-party host, and the build is simply no longer shipped - so it
        // 404s and takes the form's scripts down with it.
        String html = normalized(
                "<script src=\"/library/jquery/jquery-3.6.4.min.js\"></script>");

        assertThat(html).contains("/library/jquery/jquery-3.7.1.min.js");
        assertThat(html).doesNotContain("3.6.4");
    }

    @Test
    @DisplayName("should alias the CDN jQuery builds current OSCAR Galaxy forms load")
    void shouldAliasGalaxyCdnBuilds_toLocalBundle() {
        // Each URL is the exact spelling of a published 2020-2026 Galaxy package, whose only jQuery
        // it is (or whose fallback is another CDN): Toronto Cardiac Care Referral and NeuPath
        // Referral (code.jquery.com 2.2.4 with SRI), NeuPath Referral (3.6.4 with SRI), Rich Text
        // Letter 2020 V2 (1.12.4) and the Simple Rich Text Letter (cdnjs 3.3.1). Without the alias
        // the CSP blocks them and the form runs with jQuery undefined.
        for (String url : java.util.List.of(
                "https://code.jquery.com/jquery-2.2.4.min.js",
                "https://code.jquery.com/jquery-3.6.4.min.js",
                "https://code.jquery.com/jquery-1.12.4.min.js",
                "https://cdnjs.cloudflare.com/ajax/libs/jquery/3.3.1/jquery.min.js")) {
            assertThat(normalized("<script src=\"" + url + "\"></script>"))
                    .as(url)
                    .contains(LOCAL_BUNDLE)
                    .doesNotContain(url);
        }
    }

    @Test
    @DisplayName("should alias a CDN fallback written by document.write, with its integrity pin removed")
    void shouldAliasDocumentWriteFallback_withoutIntegrityPin() {
        // NeuPath Referral 2026, verbatim shape: an SRI-pinned CDN load, then a document.write
        // fallback to another SRI-pinned CDN build inside a single-quoted JavaScript string.
        String html = normalized(
                "<script src=\"https://code.jquery.com/jquery-3.6.4.min.js\""
                + " integrity=\"sha384-UG8ao2jwOWB7/oDdObZc6ItJmwUkR/PfMyt9Qs5AwX7PsnYn1CRKCTWyncPTWvaS\""
                + " crossorigin=\"anonymous\"></script>"
                + "<script> window.jQuery || document.write('<script src=\"https://code.jquery.com/jquery-2.2.4.min.js\""
                + "   integrity=\"sha256-BbhdlvQf/xTY9gja0Dq3HiwQF8LaCRTXxZKRutelT44=\"   crossorigin=\"anonymous\">"
                + "< \\/script>') </script>");

        assertThat(html)
                .doesNotContain("code.jquery.com")
                .doesNotContain("integrity")
                .doesNotContain("crossorigin")
                .contains("document.write('<script src=\"" + LOCAL_BUNDLE + "\"");
    }

    @Test
    @DisplayName("should alias a pre-3.x webapp jQuery build to the bundle that carries the compat shim")
    void shouldAliasLegacyLibraryJquery_toCompatBundle() {
        // FIB-4 loads ${oscar_javascript_path}jquery/jquery-2.2.4.min.js as its only jQuery; the
        // Simple Rich Text Letter loads ${oscar_javascript_path}jquery/jquery-1.4.2.js. Neither build
        // ships, so both 404 and the form's scripts fail. A pre-3.x build gets the bundle with the
        // compat shim (the APIs 3.x removed), like every other pre-3.x alias, not bare 3.7.1.
        for (String legacy : java.util.List.of("jquery-2.2.4.min.js", "jquery-1.4.2.js")) {
            String html = normalized(
                    "<script src='${oscar_javascript_path}jquery/" + legacy + "'> </script>"
                    + "<script src=\"../library/jquery/" + legacy + "\"></script>");

            assertThat(html).as(legacy).doesNotContain(legacy.replace("jquery-", "/jquery-"))
                    .contains("src='" + LOCAL_BUNDLE + "'")
                    .contains("src=\"" + LOCAL_BUNDLE + "\"");
        }
    }

    @Test
    @DisplayName("should alias a pre-3.x webapp jQuery build on a root-context deployment")
    void shouldAliasLegacyLibraryJquery_onRootContext() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body><script src=\"${oscar_javascript_path}jquery/jquery-2.2.4.min.js\""
                + " integrity='sha256-old' crossorigin='anonymous'>"
                + "</script></body></html>");
        eform.setContextPath("");

        assertThat(eform.getFormHtml())
                .contains("src=\"/eform/displayImage?imagefile=jquery-1.12.0.min.js\"")
                .doesNotContain("2.2.4", "integrity", "crossorigin");
    }

    @Test
    @DisplayName("should leave an unrecognised jQuery build to fail visibly")
    void shouldLeaveUnrecognisedLibraryJquery_untouched() {
        // Exact-version matching: silently upgrading an unknown build would hide a real breakage.
        String html = normalized(
                "<script src=\"/library/jquery/jquery-2.0.0.min.js\"></script>");

        assertThat(html).contains("/library/jquery/jquery-2.0.0.min.js");
    }
}
