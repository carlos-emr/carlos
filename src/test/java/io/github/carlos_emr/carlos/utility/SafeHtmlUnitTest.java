/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link SafeHtml}: administrator-authored help HTML keeps its formatting and loses
 * everything that can run script (#2773).
 *
 * <p>Assertions parse the cleaned output instead of comparing strings, so they pin what a browser would
 * build from it, and stay valid across jsoup releases that serialize the same tree slightly differently.</p>
 */
@Tag("unit")
@Tag("encoding")
@DisplayName("SafeHtml help-panel sanitizer")
class SafeHtmlUnitTest {

    private static Document parsed(String html) {
        return Jsoup.parseBodyFragment(SafeHtml.forFormattedText(html));
    }

    @Test
    void shouldReturnEmptyString_forNullAndEmptyInput() {
        assertThat(SafeHtml.forFormattedText(null)).isEmpty();
        assertThat(SafeHtml.forFormattedText("")).isEmpty();
    }

    @Test
    void shouldKeepFormattingMarkup_forTypicalEditorOutput() {
        String html = "<h2>Clinic help</h2><p>Call <strong>reception</strong> or <em>email</em>.</p>"
                + "<ul><li>one</li><li>two</li></ul><hr><p><del>old</del> <code>x</code></p>";

        assertThat(SafeHtml.forFormattedText(html)).isEqualTo(html);
    }

    @Test
    void shouldKeepTables_andTextWithEntities() {
        Document body = parsed("<table><tbody><tr><td>Tom &amp; Jerry &lt;3</td></tr></tbody></table>");

        assertThat(body.select("table td")).hasSize(1);
        assertThat(body.select("td").text()).isEqualTo("Tom & Jerry <3");
    }

    @Test
    void shouldDropScriptElements_withTheirContent() {
        String cleaned = SafeHtml.forFormattedText("<p>a</p><script>alert(document.cookie)</script><p>b</p>");

        assertThat(cleaned).isEqualTo("<p>a</p><p>b</p>").doesNotContain("alert");
    }

    @Test
    void shouldDropEventHandlerAttributes_butKeepTheElement() {
        Document body = parsed("<img src=\"https://img.example/i.png\" onerror=\"alert(1)\" alt=\"i\">"
                + "<p onclick=\"x()\" onmouseover=\"y()\">t</p>");

        assertThat(body.select("img[src]")).hasSize(1);
        assertThat(body.select("img").attr("onerror")).isEmpty();
        assertThat(body.select("p").text()).isEqualTo("t");
        assertThat(body.getAllElements().stream().flatMap(e -> e.attributes().asList().stream())
                .map(a -> a.getKey().toLowerCase())).noneMatch(key -> key.startsWith("on"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "  javascript:alert(1)",
            "jav&#x61;script:alert(1)",
            "jav&#x09;ascript:alert(1)",
            "jav&#10;ascript:alert(1)",
            "jav&Tab;ascript:alert(1)",
            "&#x01;javascript:alert(1)",
            "java&#13;&#10;script:alert(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "vbscript:msgbox(1)"
    })
    void shouldDropTheHref_ofLinksWithScriptSchemes(String href) {
        Document body = parsed("<a href=\"" + href + "\">x</a>");

        assertThat(body.select("a").text()).isEqualTo("x");
        assertThat(body.select("a").attr("href")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<iframe src=\"https://e.example\"></iframe>",
            "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>",
            "<form action=\"/x\"><input name=a></form>",
            "<svg onload=alert(1)><script>1</script></svg>",
            "<svg/onload=alert(1)>",
            "<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>",
            "<object data=\"https://e.example/x.swf\"></object>",
            "<embed src=\"https://e.example/x.swf\">",
            "<style>@import 'https://e.example/x.css';</style>",
            "<link rel=\"stylesheet\" href=\"https://e.example/x.css\">",
            "<base href=\"https://e.example/\">",
            "<meta http-equiv=\"refresh\" content=\"0;url=https://e.example\">",
            "<body onload=\"alert(1)\">",
            "<scr<script>ipt>alert(1)</scr</script>ipt>",
            "<img src=x onerror=alert(1)//",
            "<details open ontoggle=alert(1)>x</details>"
    })
    void shouldLeaveNothingExecutable_forKnownXssPayloads(String payload) {
        String cleaned = SafeHtml.forFormattedText("<p>safe</p>" + payload);
        Document body = Jsoup.parseBodyFragment(cleaned);

        assertThat(body.select("script, iframe, frame, form, input, svg, math, object, embed, style, link, base, meta"))
                .isEmpty();
        assertThat(body.getAllElements().stream().flatMap(e -> e.attributes().asList().stream())
                .map(a -> a.getKey().toLowerCase())).noneMatch(key -> key.startsWith("on") || key.equals("srcdoc"));
        assertThat(cleaned.toLowerCase()).doesNotContain("<script").doesNotContain("javascript:");
        assertThat(body.select("p").first().text()).isEqualTo("safe");
    }

    @Test
    void shouldDropTheSrc_ofImagesWithScriptSchemes() {
        Document body = parsed("<img src=\"javascript:alert(1)\"><img src=\"jav&#x09;ascript:alert(1)\">"
                + "<img src=\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\">");

        assertThat(body.select("img")).hasSize(3);
        assertThat(body.select("img[src]")).isEmpty();
    }

    @Test
    void shouldDropStyleClassAndId_fromEveryElement() {
        Document body = parsed("<p style=\"position:fixed\" class=\"c\" id=\"i\">t</p>");

        assertThat(body.select("p").first().attributes().size()).isZero();
    }

    @Test
    void shouldMarkLinksNoopenerNoreferrer_replacingAnyAuthorValue() {
        Document body = parsed("<a href=\"https://help.example/a?b=1&amp;c=2\" target=\"_blank\" rel=\"opener\">h</a>");
        var link = body.select("a").first();

        assertThat(link.attr("href")).isEqualTo("https://help.example/a?b=1&c=2");
        assertThat(link.attr("target")).isEqualTo("_blank");
        assertThat(link.attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void shouldKeepRelativeAndMailtoLinks_exactlyAsWritten() {
        Document body = parsed("<a href=\"/carlos/help/clinic.jsp?x=1\">rel</a><a href=\"mailto:front@desk.example\">m</a>");

        assertThat(body.select("a").eachAttr("href"))
                .containsExactly("/carlos/help/clinic.jsp?x=1", "mailto:front@desk.example");
    }

    @Test
    void shouldNeverEmitTheInternalBaseHost_forRelativeLinks() {
        assertThat(SafeHtml.forFormattedText("<a href=\"help/page.html\">r</a><img src=\"/img/a.png\">"))
                .doesNotContain("carlos.invalid");
    }
}
