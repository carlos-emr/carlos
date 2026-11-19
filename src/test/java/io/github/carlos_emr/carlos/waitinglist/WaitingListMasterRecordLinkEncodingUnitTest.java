/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.waitinglist;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.owasp.encoder.Encode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.waitinglist.bean.WLPatientWaitingListBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the waiting-list manager's Master Record link across the removal of the OWASP encoder
 * JSP taglib (#5477).
 *
 * <p>{@code DisplayWaitingList.jsp} was the last page calling that taglib: its patient-name link
 * built {@code DemographicEdit?demographic_no=...} with
 * {@code ${carlos:forJavaScript(e:forUriComponent(waitingListBean.demographicNo))}}. The page now
 * uses {@code carlos:forUriComponent}. Each expression is evaluated here with Tomcat's own EL
 * implementation (the one Jasper calls for template text at runtime), with every function bound
 * the way its tag library binds it: {@code e:forUriComponent} to
 * {@code org.owasp.encoder.Encode.forUriComponent(String)}, as the removed
 * {@code owasp.encoder.jakarta.advanced} TLD did, and the {@code carlos:} functions to whatever
 * {@code carlos-tag.tld} declares. The shipped expression is read from the JSP itself, so the
 * test follows the page rather than a copy of it.
 *
 * @since 2026-11-19
 */
@DisplayName("Waiting-list Master Record link encoding (#5477)")
@Tag("unit")
@Tag("waitinglist")
@Tag("security")
class WaitingListMasterRecordLinkEncodingUnitTest extends CarlosUnitTestBase {

    private static final Path DISPLAY_WAITING_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/waitinglist/DisplayWaitingList.jsp");
    private static final Path CARLOS_TLD = Path.of("src/main/webapp/WEB-INF/carlos-tag.tld");
    private static final Path DEPENDENCY_LOCK = Path.of("dependencies-lock.json");

    /** The link's EL before #5477, through the OWASP encoder taglib. */
    private static final String LEGACY_LINK_EXPRESSION =
            "${carlos:forJavaScript(e:forUriComponent(waitingListBean.demographicNo))}";

    /** The EL that fills {@code demographic_no=} in the patient-name link's onclick. */
    private static final Pattern LINK_EXPRESSION = Pattern.compile(
            "popupDemographicPage\\('<%= request\\.getContextPath\\(\\) %>/demographic/DemographicEdit"
                    + "\\?demographic_no=(\\$\\{[^}]*\\})'\\); return false;");

    private static final ExpressionFactory EXPRESSION_FACTORY = new ExpressionFactoryImpl();

    static Stream<Arguments> demographicNumbers() {
        return Stream.of(
                Arguments.of("1"),
                Arguments.of("123456"),
                Arguments.of(""),
                Arguments.of((Object) null),
                // Not real demographic numbers; they prove the two encoders agree on every
                // character class the URI-component and JavaScript layers treat specially.
                Arguments.of("12 34"),
                Arguments.of("1&demographic_no=2#x"),
                Arguments.of("O'Neil\"\\"),
                Arguments.of("</script><b>"),
                Arguments.of("é 中"));
    }

    @ParameterizedTest(name = "[{index}] demographicNo={0}")
    @MethodSource("demographicNumbers")
    @DisplayName("should render the same link as the OWASP taglib expression did")
    void shouldRenderTheLegacyLink_forEveryDemographicNumber(String demographicNo) throws Exception {
        String shipped = evaluate(shippedLinkExpression(), demographicNo);
        String legacy = evaluate(LEGACY_LINK_EXPRESSION, demographicNo);

        assertThat(shipped).isEqualTo(legacy);
    }

    @Test
    @DisplayName("should render a numeric demographic number unchanged")
    void shouldRenderNumericIdUnchanged_forExistingEntries() throws Exception {
        assertThat(evaluate(shippedLinkExpression(), "4711")).isEqualTo("4711");
    }

    @Test
    @DisplayName("should render a null demographic number as empty, never the word null")
    void shouldRenderEmpty_whenDemographicNumberIsNull() throws Exception {
        assertThat(evaluate(shippedLinkExpression(), null)).isEmpty();
        // Calling the OWASP method directly with null is the documented "null" bug the CARLOS
        // wrappers exist for. EL's String coercion turned null into "" before the old taglib
        // function ran, so the page never printed "null" here; the wrapper keeps that result
        // without depending on the coercion.
        assertThat(Encode.forUriComponent(null)).isEqualTo("null");
        assertThat(evaluate(LEGACY_LINK_EXPRESSION, null)).isEmpty();
    }

    @Test
    @DisplayName("should URI-component encode, then JavaScript encode, the demographic number")
    void shouldApplyBothEncodingLayers_inOrder() throws Exception {
        String raw = "1&x='y'";

        assertThat(evaluate(shippedLinkExpression(), raw))
                .isEqualTo(Encode.forJavaScript(Encode.forUriComponent(raw)));
    }

    @Nested
    @DisplayName("OWASP encoder taglib removal")
    class TaglibRemoval {

        @Test
        @DisplayName("should not declare or call the OWASP encoder taglib on the waiting-list page")
        void shouldNotUseOwaspTaglib_onDisplayWaitingList() throws IOException {
            String jsp = Files.readString(DISPLAY_WAITING_LIST_JSP, StandardCharsets.UTF_8);

            assertThat(jsp).doesNotContain("owasp.encoder.jakarta")
                    .doesNotContainPattern("\\be:for[A-Za-z]+\\s*\\(")
                    .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");
            assertThat(shippedLinkExpression())
                    .isEqualTo("${carlos:forJavaScript(carlos:forUriComponent(waitingListBean.demographicNo))}");
        }

        @Test
        @DisplayName("should lock the OWASP encoder core without its JSP taglib jar")
        void shouldLockEncoderWithoutJspTaglib_inDependencyLock() throws IOException {
            String lock = Files.readString(DEPENDENCY_LOCK, StandardCharsets.UTF_8);

            assertThat(lock).as("the OWASP core encoder must remain locked and its JSP taglib must stay removed (#5477)")
                    .contains("\"artifactId\" : \"encoder\",")
                    .doesNotContain("encoder-jakarta-jsp");
        }
    }

    private static String shippedLinkExpression() throws IOException {
        String jsp = Files.readString(DISPLAY_WAITING_LIST_JSP, StandardCharsets.UTF_8);
        Matcher matcher = LINK_EXPRESSION.matcher(jsp);
        assertThat(matcher.find()).as("Master Record link in %s", DISPLAY_WAITING_LIST_JSP).isTrue();
        String expression = matcher.group(1);
        assertThat(matcher.find()).as("exactly one Master Record link").isFalse();
        return expression;
    }

    /**
     * Evaluates {@code expression} as Jasper evaluates EL in template text: coerced to
     * {@link String}, with {@code waitingListBean} bound to a row whose demographic number is
     * {@code demographicNo}.
     */
    private static String evaluate(String expression, String demographicNo) throws Exception {
        StandardELContext context = new StandardELContext(EXPRESSION_FACTORY);
        for (String name : List.of("forJavaScript", "forUriComponent")) {
            context.getFunctionMapper().mapFunction("carlos", name, carlosFunction(name));
        }
        // owasp.encoder.jakarta.advanced declared:
        // <function-class>org.owasp.encoder.Encode</function-class>
        // <function-signature>java.lang.String forUriComponent(java.lang.String)</function-signature>
        context.getFunctionMapper().mapFunction("e", "forUriComponent",
                Encode.class.getMethod("forUriComponent", String.class));

        WLPatientWaitingListBean row =
                new WLPatientWaitingListBean(demographicNo, "7", "1", "FAKE-Patient", "", "", "2026-01-01");
        context.getVariableMapper().setVariable("waitingListBean",
                EXPRESSION_FACTORY.createValueExpression(row, WLPatientWaitingListBean.class));

        ValueExpression value = EXPRESSION_FACTORY.createValueExpression(context, expression, String.class);
        return (String) value.getValue(context);
    }

    /** Resolves a {@code carlos:} EL function to the method {@code carlos-tag.tld} binds it to. */
    private static Method carlosFunction(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document tld;
        try (InputStream in = Files.newInputStream(CARLOS_TLD)) {
            tld = builder.parse(in);
        }
        NodeList functions = tld.getElementsByTagName("function");
        for (int i = 0; i < functions.getLength(); i++) {
            Element function = (Element) functions.item(i);
            if (!name.equals(text(function, "name"))) {
                continue;
            }
            Class<?> functionClass = Class.forName(text(function, "function-class"));
            Matcher signature = Pattern.compile("\\S+\\s+(\\w+)\\s*\\(([^)]*)\\)")
                    .matcher(text(function, "function-signature"));
            assertThat(signature.matches()).as("signature of carlos:%s", name).isTrue();
            Class<?>[] parameters = Arrays.stream(signature.group(2).split(","))
                    .map(String::trim)
                    .filter(type -> !type.isEmpty())
                    .map(WaitingListMasterRecordLinkEncodingUnitTest::loadClass)
                    .toArray(Class<?>[]::new);
            return functionClass.getMethod(signature.group(1), parameters);
        }
        throw new AssertionError("carlos-tag.tld declares no function " + name);
    }

    private static String text(Element parent, String child) {
        return parent.getElementsByTagName(child).item(0).getTextContent().trim();
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("carlos-tag.tld names an unknown type " + name, e);
        }
    }
}
