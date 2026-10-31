/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.io.StringReader;
import java.io.StringWriter;

import io.github.carlos_emr.carlos.utility.tld.CarlosEncodeTag;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockPageContext;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parser-boundary regressions for GHSA-g8p6-7r8f-qrpv through CARLOS's public surfaces.
 * Parity with OWASP alone cannot catch a regression in the upstream dependency.
 */
@Tag("unit")
@Tag("encoding")
@DisplayName("Encoded fragments cannot complete trusted HTML/XML delimiters")
class EncoderBoundaryUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"javaScript", "javaScriptBlock"})
    void shouldKeepScriptTokensInsideString_acrossEveryNonemptyFragment(String context) throws Exception {
        for (String surface : new String[]{"scalar", "writer", "tag"}) {
            for (String token : new String[]{"</script>", "</ScRiPt>", "<!--", "-->"}) {
                for (int start = 0; start < token.length(); start++) {
                    for (int end = start + 1; end <= token.length(); end++) {
                        String fragment = token.substring(start, end);
                        String encoded = encode(context, surface, fragment);
                        String source = "window.value='" + token.substring(0, start) + encoded
                                + token.substring(end) + "';window.completed=true;";
                        var html = Jsoup.parse("<script>" + source + "</script><p id=after>after</p>");

                        assertThat(html.select("script")).as("%s/%s: %s [%s,%s)",
                                context, surface, token, start, end).hasSize(1);
                        assertThat(html.selectFirst("script").data()).isEqualTo(source);
                        assertThat(html.select("#after")).hasSize(1);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"scalar", "writer", "tag"})
    void shouldPreserveCdataText_acrossEveryDelimiterPartition(String surface) throws Exception {
        String token = "]]>";
        for (int start = 0; start < token.length(); start++) {
            for (int end = start + 1; end <= token.length(); end++) {
                String xml = "<root><![CDATA[" + token.substring(0, start)
                        + encode("cdata", surface, token.substring(start, end))
                        + token.substring(end) + "<injected/>]]></root>";
                Document parsed = parseXml(xml);
                assertThat(parsed.getDocumentElement().getTextContent()).isEqualTo("]]>" + "<injected/>");
                assertThat(parsed.getElementsByTagName("injected").getLength()).isZero();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"scalar", "writer", "tag"})
    void shouldKeepXmlCommentClosedOnlyByTemplate_acrossTrustedHyphens(String surface) throws Exception {
        String encoded = encode("xmlComment", surface, "-payload-");
        Document parsed = parseXml("<root><!---" + encoded + "-><injected/>--></root>");

        assertThat(parsed.getElementsByTagName("injected").getLength()).isZero();
        assertThat(parsed.getDocumentElement().getFirstChild().getNodeValue())
                .isEqualTo("-~payload~-><injected/>");
    }

    private static Document parseXml(String xml) throws Exception {
        return XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    private static String encode(String context, String surface, String value) throws Exception {
        if ("tag".equals(surface)) {
            MockPageContext page = new MockPageContext();
            page.getResponse().setCharacterEncoding("UTF-8");
            CarlosEncodeTag tag = new CarlosEncodeTag();
            tag.setPageContext(page);
            tag.setContext(context);
            tag.setValue(value);
            tag.doStartTag();
            return page.getContentAsString();
        }
        if ("writer".equals(surface)) {
            StringWriter writer = new StringWriter();
            switch (context) {
                case "javaScript" -> SafeEncode.forJavaScript(writer, value);
                case "javaScriptBlock" -> SafeEncode.forJavaScriptBlock(writer, value);
                case "cdata" -> SafeEncode.forCDATA(writer, value);
                case "xmlComment" -> SafeEncode.forXmlComment(writer, value);
                default -> throw new IllegalArgumentException(context);
            }
            return writer.toString();
        }
        return switch (context) {
            case "javaScript" -> SafeEncode.forJavaScript(value);
            case "javaScriptBlock" -> SafeEncode.forJavaScriptBlock(value);
            case "cdata" -> SafeEncode.forCDATA(value);
            case "xmlComment" -> SafeEncode.forXmlComment(value);
            default -> throw new IllegalArgumentException(context);
        };
    }
}
