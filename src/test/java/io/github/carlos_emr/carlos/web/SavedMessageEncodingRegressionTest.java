/*
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This file is part of CARLOS (https://github.com/carlos-emr/carlos).
 *
 * CARLOS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * CARLOS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CARLOS. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.carlos.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Regression coverage for JSP saved-message HTML encoding.
 *
 * @since 2026-05-30
 */
class SavedMessageEncodingRegressionTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath();
    private static final Path WEBAPP_ROOT = REPOSITORY_ROOT.resolve("src/main/webapp");

    @TestFactory
    Stream<DynamicTest> jspSavedMessageAlertsEncodeHtml() throws IOException {
        List<Path> jspAssets = List.of(
                WEBAPP_ROOT.resolve("WEB-INF/jsp/admin/sitesAdminDetail.jsp"),
                WEBAPP_ROOT.resolve("WEB-INF/jsp/form/pharmaForms/formBPMH.jsp"));

        return jspAssets.stream()
                .map(path -> DynamicTest.dynamicTest(REPOSITORY_ROOT.relativize(path).toString(), () -> {
                    String content = Files.readString(path);

                    assertTrue(
                            content.contains("${carlos:forHtml(savedMessage)}"),
                            "savedMessage should be encoded for HTML output");
                    assertFalse(
                            content.contains("${savedMessage}"),
                            "savedMessage must not be rendered without HTML encoding");
                }));
    }
}
