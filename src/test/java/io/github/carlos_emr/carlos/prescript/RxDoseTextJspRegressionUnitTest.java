/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A page that prints a floating-point dose directly shows a dose of 1 as "1.0" and ten million as
 * "1.0E7": {@code <%= rx.getTakeMin() %>} and {@code ${fav.takeMin}} both go through
 * {@code Float.toString}/{@code Double.toString}. ISMP Canada's <i>Do Not Use</i> list bans the
 * trailing zero, which is read as ten times the dose. Pages print the text
 * {@link io.github.carlos_emr.carlos.utility.DoseText} writes instead (#6185).
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("Dose text in JSP pages")
class RxDoseTextJspRegressionUnitTest {

    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Path EDIT_FAVORITES = WEBAPP.resolve("WEB-INF/jsp/rx/EditFavorites2.jsp");
    private static final Path COPY_FAVORITES = WEBAPP.resolve("WEB-INF/jsp/rx/CopyFavorites2.jsp");
    private static final Path PREVENTION_REVIEW = WEBAPP.resolve("WEB-INF/jsp/prevention/review.jsp");

    /** {@code <%= anything.getTakeMin() %>}: the float getter printed with nothing around it. */
    private static final Pattern RAW_SCRIPTLET_DOSE =
            Pattern.compile("<%=\\s*[\\w.()\\[\\]]*\\.getTakeM(?:in|ax)\\(\\)\\s*%>");

    /** {@code ${anything.takeMin}}: the float property printed with nothing around it. */
    private static final Pattern RAW_EL_DOSE =
            Pattern.compile("[$#]\\{\\s*[\\w.\\[\\]]*\\.takeM(?:in|ax)\\s*}");

    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

    /**
     * Matches that are not floats. {@code RxWriteScriptForm} holds the take fields as text, which
     * the page fills from {@code getTakeMinString()}/{@code getTakeMaxString()} before printing it.
     */
    private static final Set<String> TEXT_TYPED = Set.of(
            "WEB-INF/jsp/rx/WriteScript.jsp: <%= thisForm.getTakeMin() %>",
            "WEB-INF/jsp/rx/WriteScript.jsp: <%= thisForm.getTakeMax() %>");

    private static String page(Path jsp) throws IOException {
        // Commented-out markup is never rendered, so it is not a page that prints a dose.
        return JSP_COMMENT.matcher(Files.readString(jsp, StandardCharsets.UTF_8)).replaceAll("");
    }

    @Test
    @DisplayName("should print the favourite's dose as dose text in the favourites editor")
    void shouldPrintDoseText_inEditFavorites() throws IOException {
        String jsp = page(EDIT_FAVORITES);

        assertThat(jsp)
                .doesNotContain("<%= f.getTakeMin() %>")
                .doesNotContain("<%= f.getTakeMax() %>")
                .contains("value=\"<%= SafeEncode.forHtmlAttribute(f.getTakeMinString()) %>\"")
                .contains("value=\"<%= SafeEncode.forHtmlAttribute(f.getTakeMaxString()) %>\"");
    }

    @Test
    @DisplayName("should print the shared favourite's dose as dose text on the copy page")
    void shouldPrintDoseText_inCopyFavorites() throws IOException {
        String jsp = page(COPY_FAVORITES);

        assertThat(jsp)
                .doesNotContain("${fav.takeMin}")
                .doesNotContain("${fav.takeMax}")
                .contains("<carlos:encode value='<%= DoseText.of((float) copyFavorite.getTakeMin()) %>'/>")
                .contains("<carlos:encode value='<%= DoseText.of((float) copyFavorite.getTakeMax()) %>'/>");
    }

    @Test
    @DisplayName("should print the immunization dose as dose text on the submission review page")
    void shouldPrintDoseText_inPreventionReview() throws IOException {
        String jsp = page(PREVENTION_REVIEW);

        assertThat(jsp)
                .doesNotContain("getDoseQuantity().getValue().toString()")
                .contains("return DoseText.of(i.getDoseQuantity().getValue());");
    }

    @Test
    @DisplayName("should fill the legacy editor's text-typed take fields from the dose text")
    void shouldFillTextTypedTakeFields_fromDoseTextInWriteScript() throws IOException {
        // The exemption above holds only while the form is filled from the dose text.
        assertThat(page(WEBAPP.resolve("WEB-INF/jsp/rx/WriteScript.jsp")))
                .contains("thisForm.setTakeMin(rx.getTakeMinString());")
                .contains("thisForm.setTakeMax(rx.getTakeMaxString());");
    }

    @Test
    @DisplayName("should not print a floating-point dose directly in any page")
    void shouldNotPrintFloatDoseDirectly_inAnyPage() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            for (Path file : (Iterable<Path>) files.filter(RxDoseTextJspRegressionUnitTest::isPage)::iterator) {
                String content = page(file);
                for (Pattern pattern : List.of(RAW_SCRIPTLET_DOSE, RAW_EL_DOSE)) {
                    Matcher matcher = pattern.matcher(content);
                    while (matcher.find()) {
                        String found = WEBAPP.relativize(file).toString().replace('\\', '/') + ": " + matcher.group();
                        if (!TEXT_TYPED.contains(found)) {
                            offenders.add(found);
                        }
                    }
                }
            }
        }

        assertThat(offenders)
                .as("pages that print takeMin/takeMax as a raw float; print DoseText or getTakeMinString() instead")
                .isEmpty();
    }

    private static boolean isPage(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file)
                && (name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag"));
    }
}
