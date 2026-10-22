/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility.tld;

import java.io.StringWriter;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;

import io.github.carlos_emr.carlos.test.mocks.CapturingPageContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.owasp.encoder.Encode;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CarlosEncodeTag}.
 *
 * <p>Uses a stub {@link PageContext} whose {@link JspWriter} captures output
 * into a {@link StringWriter}. The tag's delegation to {@link io.github.carlos_emr.carlos.utility.SafeEncode}
 * is verified by comparing captured output against direct
 * {@link org.owasp.encoder.Encode} calls.
 *
 * @since 2026-04-18
 */
@Tag("unit")
@Tag("encoding")
@DisplayName("CarlosEncodeTag")
class CarlosEncodeTagUnitTest {

    private CarlosEncodeTag tag;
    private StringWriter captured;

    @BeforeEach
    void setUp() {
        tag = new CarlosEncodeTag();
        captured = new StringWriter();
        CapturingPageContext ctx = CapturingPageContext.writingTo(captured);
        tag.setPageContext(ctx);
    }

    @Nested
    @DisplayName("Null value handling")
    class NullValue {

        @Test
        @DisplayName("should write empty when value is null and no context is set")
        void shouldWriteEmpty_whenValueIsNullAndNoContext() throws JspException {
            tag.setValue(null);
            tag.doStartTag();
            assertThat(captured.toString()).isEmpty();
        }

        @Test
        @DisplayName("should write empty when value is null for every supported context")
        void shouldWriteEmpty_whenValueIsNull_forEveryContext() throws JspException {
            String[] contexts = {
                    "html", "htmlContent", "htmlAttribute", "htmlUnquotedAttribute", "forHtml",
                    "javaScript", "js", "javaScriptAttribute", "javaScriptBlock", "javaScriptSource",
                    "uri", "uriComponent",
                    "cssString", "css", "cssUrl",
                    "xml", "xmlAttribute", "xmlContent", "xmlComment", "cdata", "java"
            };
            for (String ctx : contexts) {
                tag = new CarlosEncodeTag();
                StringWriter sw = new StringWriter();
                tag.setPageContext(CapturingPageContext.writingTo(sw));
                tag.setContext(ctx);
                tag.setValue(null);
                tag.doStartTag();
                assertThat(sw.toString()).as("context=%s", ctx).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Default context (no context attribute)")
    class DefaultContext {

        @Test
        @DisplayName("should default to HTML content encoding")
        void shouldDefaultToHtmlContent_whenContextNotSet() throws JspException {
            tag.setValue("<b>bold</b>");
            tag.doStartTag();
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlContent("<b>bold</b>"));
        }

        @Test
        @DisplayName("should default to HTML content when context is empty string")
        void shouldDefaultToHtmlContent_whenContextIsEmpty() throws JspException {
            tag.setValue("<b>bold</b>");
            tag.setContext("");
            tag.doStartTag();
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlContent("<b>bold</b>"));
        }
    }

    @Nested
    @DisplayName("Context dispatch — each context maps to the matching Encode method")
    class ContextDispatch {

        @Test
        void shouldDispatchToForHtmlContent_whenContextIsHtml() throws JspException {
            run("html", "O'Brien & <b>");
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlContent("O'Brien & <b>"));
        }

        @Test
        void shouldDispatchToForHtmlContentWithBreakMarkers_whenContextIsHtmlWithBreakMarkers() throws JspException {
            run("htmlWithBreakMarkers", "5.2<br />Repeat <b>");
            assertThat(captured.toString()).isEqualTo("5.2<br/>Repeat &lt;b&gt;");
        }

        @Test
        void shouldRenderEmpty_whenHtmlWithBreakMarkersValueIsNull() throws JspException {
            run("htmlWithBreakMarkers", null);
            assertThat(captured.toString()).isEmpty();
        }

        @Test
        void shouldDispatchToForHtmlAttribute_whenContextIsHtmlAttribute() throws JspException {
            run("htmlAttribute", "\" onerror=alert(1)");
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlAttribute("\" onerror=alert(1)"));
        }

        @Test
        void shouldDispatchToForHtmlUnquotedAttribute_whenContextIsSet() throws JspException {
            run("htmlUnquotedAttribute", "value with spaces");
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlUnquotedAttribute("value with spaces"));
        }

        @Test
        void shouldDispatchToForJavaScript_whenContextIsJavaScript() throws JspException {
            run("javaScript", "alert('xss')");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScript("alert('xss')"));
        }

        @Test
        void shouldDispatchToForJavaScript_whenContextIsJsShorthand() throws JspException {
            run("js", "alert('xss')");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScript("alert('xss')"));
        }

        @Test
        void shouldDispatchToForJavaScriptAttribute_whenContextIsSet() throws JspException {
            run("javaScriptAttribute", "value");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScriptAttribute("value"));
        }

        @Test
        @DisplayName("should keep stored drug names inside a JavaScript href argument")
        void shouldEncodeDrugNameQuotesAndEntities_forJavaScriptAttribute() throws JspException {
            // WriteScript's ShowDrugInfo link uses this context inside a double-quoted href
            // and a single-quoted JS argument. HTML entities must not reopen either quote.
            run("javaScriptAttribute", "O'Brien');alert(1);//\" onmouseover=\"alert(2) &quot;");

            assertThat(captured.toString())
                    .doesNotContain("'", "\"", "&")
                    .contains("\\x27", "\\x22", "\\x26");
        }

        @Test
        void shouldDispatchToForJavaScriptBlock_whenContextIsSet() throws JspException {
            run("javaScriptBlock", "value");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScriptBlock("value"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"en", "es", "fr", "pl", "pt-BR"})
        @DisplayName("should preserve quoted contact validation messages through the real JSP tag")
        void shouldEncodeContactValidationMessages_forJavaScriptBlock(String language) throws JspException {
            ResourceBundle messages = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));
            for (String key : new String[]{"demographic.contactForm.msgLastNameRequired",
                    "demographic.contactForm.msgFirstNameRequired"}) {
                String message = messages.getString(key);
                assertThat(message).as("translated quote regression fixture %s/%s", language, key).contains("\"");
                captured.getBuffer().setLength(0);
                run("javaScriptBlock", message);
                String encoded = captured.toString();
                // The actual tag output must stay inside the JSP's double-quoted literal.
                assertThat(encoded).doesNotContain("\r", "\n", "</script").contains("\\\"");
                assertThat(encoded.replace("\\\"", "")).doesNotContain("\"");
                assertThat(StringEscapeUtils.unescapeEcmaScript(encoded)).isEqualTo(message);
            }
        }

        @Test
        void shouldDispatchToForUri_whenContextIsUri() throws JspException {
            run("uri", "hello world?x=1&y=2");
            assertThat(captured.toString()).isEqualTo(Encode.forUri("hello world?x=1&y=2"));
        }

        @Test
        void shouldDispatchToForUriComponent_whenContextIsUriComponent() throws JspException {
            run("uriComponent", "hello world?x=1&y=2");
            assertThat(captured.toString()).isEqualTo(Encode.forUriComponent("hello world?x=1&y=2"));
        }

        @Test
        void shouldDispatchToForCssString_whenContextIsCss() throws JspException {
            run("css", "attack\"ed");
            assertThat(captured.toString()).isEqualTo(Encode.forCssString("attack\"ed"));
        }

        @Test
        void shouldDispatchToForCssString_whenContextIsCssString() throws JspException {
            run("cssString", "attack\"ed");
            assertThat(captured.toString()).isEqualTo(Encode.forCssString("attack\"ed"));
        }

        @Test
        void shouldDispatchToForCssUrl_whenContextIsCssUrl() throws JspException {
            run("cssUrl", "/path/to/image.png");
            assertThat(captured.toString()).isEqualTo(Encode.forCssUrl("/path/to/image.png"));
        }

        @Test
        void shouldDispatchToForXml_whenContextIsXml() throws JspException {
            run("xml", "<x>ok</x>");
            assertThat(captured.toString()).isEqualTo(Encode.forXml("<x>ok</x>"));
        }

        @Test
        void shouldDispatchToForXmlAttribute_whenContextIsSet() throws JspException {
            run("xmlAttribute", "attr\"value");
            assertThat(captured.toString()).isEqualTo(Encode.forXmlAttribute("attr\"value"));
        }

        @Test
        void shouldDispatchToForCDATA_whenContextIsCdata() throws JspException {
            run("cdata", "section data]]>");
            assertThat(captured.toString()).isEqualTo(Encode.forCDATA("section data]]>"));
        }

        @Test
        void shouldBeCaseInsensitive_forContextNames() throws JspException {
            // HTML / Html / hTmL all route to forHtmlContent
            for (String variant : new String[] {"HTML", "Html", "hTmL", "HtMl"}) {
                CarlosEncodeTag t = new CarlosEncodeTag();
                StringWriter sw = new StringWriter();
                t.setPageContext(CapturingPageContext.writingTo(sw));
                t.setContext(variant);
                t.setValue("<x>");
                t.doStartTag();
                assertThat(sw.toString()).as("context=%s", variant)
                        .isEqualTo(Encode.forHtmlContent("<x>"));
            }
        }

        private void run(String context, String value) throws JspException {
            tag.setContext(context);
            tag.setValue(value);
            tag.doStartTag();
        }
    }

    @Nested
    @DisplayName("Invalid context")
    class InvalidContext {

        @Test
        @DisplayName("should throw JspException when context is unknown")
        void shouldThrowJspException_whenContextIsUnknown() {
            tag.setContext("bogusContext");
            tag.setValue("value");
            assertThatThrownBy(() -> tag.doStartTag())
                    .isInstanceOf(JspException.class)
                    .hasMessageContaining("bogusContext");
        }
    }

    @Nested
    @DisplayName("State lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should clear attributes on release")
        void shouldClearAttributes_onRelease() {
            tag.setValue("foo");
            tag.setContext("html");
            tag.release();
            // After release, re-invoking with no setters should produce empty (null value -> empty)
            CarlosEncodeTag sameTag = tag;
            StringWriter sw = new StringWriter();
            sameTag.setPageContext(CapturingPageContext.writingTo(sw));
            try {
                sameTag.doStartTag();
            } catch (JspException e) {
                throw new AssertionError(e);
            }
            assertThat(sw.toString()).isEmpty();
        }
    }
}
