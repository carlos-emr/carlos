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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC billing support popups hand a picked row back to the page that opened them, then focus that page
 * (issue #4990). A document has no {@code focus()}; the opener window does, so {@code opener.document.focus()}
 * threw a TypeError at the end of every pick.
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC billing support popups - focusing the opener after a pick")
class BcSupportPopupFocusRegressionTest {

    private static final Path SUPPORT = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/support");
    private static final Path WEBAPP = Path.of("src/main/webapp");

    @ParameterizedTest
    @ValueSource(strings = {"billingfeeitem.jsp", "icd9.jsp", "bodypart.jsp", "natureinjury.jsp"})
    @DisplayName("should focus the opener window, not its document, after a pick")
    void shouldFocusOpenerWindow_afterPick(String jsp) throws IOException {
        String page = Files.readString(SUPPORT.resolve(jsp), StandardCharsets.UTF_8);
        assertThat(page).doesNotContain("opener.document.focus(").contains("opener.focus();");
    }

    @Test
    @DisplayName("should not call focus() on a document in any page or script")
    void shouldNotFocusDocument_inAnyPageOrScript() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            offenders = files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().matches(".*\\.(jsp|jspf|js)$"))
                    .filter(file -> !file.toString().contains("/library/") && !file.toString().endsWith(".min.js"))
                    .filter(file -> {
                        try {
                            return Files.readString(file, StandardCharsets.UTF_8).contains("document.focus(");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
        assertThat(offenders).isEmpty();
    }
}
