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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("billing")
@DisplayName("Billing error-page schedule navigation")
class BillingErrorPageNavigationUnitTest {

    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorPagesWithScheduleNavigation")
    @DisplayName("should navigate the top-level window from framed error pages")
    void shouldNavigateTopLevelWindow_whenScheduleLinkClicked(Path jspPath) throws Exception {
        String jsp = readJsp(jspPath);

        assertThat(Jsoup.parse(jsp).select("a[target=_top]"))
                .anyMatch(link -> link.attr("href").endsWith("/provider/providercontrol"));
    }

    @Test
    @DisplayName("should not embed JSP syntax in scriptlet comments")
    void shouldNotEmbedJspSyntax_whenDocumentingErrorSources() throws Exception {
        String jsp = readJsp(JSP_ROOT.resolve(
                "billing/CA/ON/billingValidationError.jsp"));

        assertThat(jsp).doesNotContain("<%@ page errorPage=");
    }

    private static Stream<Path> errorPagesWithScheduleNavigation() {
        return Stream.of(
                JSP_ROOT.resolve("billing/CA/ON/billingFileWriteError.jsp"),
                JSP_ROOT.resolve("billing/CA/ON/billingValidationError.jsp"));
    }

    private static String readJsp(Path jspPath) throws Exception {
        File validatedJsp = PathValidationUtils.validateExistingPath(
                jspPath.toFile(), JSP_ROOT.toFile());
        return Files.readString(validatedJsp.toPath());
    }
}
