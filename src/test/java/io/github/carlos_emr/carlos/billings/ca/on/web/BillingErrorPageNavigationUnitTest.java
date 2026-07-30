/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private static final Pattern TOP_LEVEL_SCHEDULE_LINK = Pattern.compile(
            "<a\\b(?=[^>]*href=\"[^\"]*/provider/providercontrol\")"
                    + "(?=[^>]*target=\"_top\")[^>]*>",
            Pattern.DOTALL);

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorPagesWithScheduleNavigation")
    @DisplayName("should navigate the top-level window from framed error pages")
    void shouldNavigateTopLevelWindow_whenScheduleLinkClicked(String jspPath) throws Exception {
        String jsp = Files.readString(JSP_ROOT.resolve(jspPath));

        assertThat(TOP_LEVEL_SCHEDULE_LINK.matcher(jsp).find()).isTrue();
    }

    @Test
    @DisplayName("should not embed JSP syntax in scriptlet comments")
    void shouldNotEmbedJspSyntax_whenDocumentingErrorSources() throws Exception {
        String jsp = Files.readString(JSP_ROOT.resolve(
                "billing/CA/ON/billingValidationError.jsp"));

        assertThat(jsp).doesNotContain("<%@ page errorPage=");
    }

    private static Stream<String> errorPagesWithScheduleNavigation() {
        return Stream.of(
                "billing/CA/ON/billingFileWriteError.jsp",
                "billing/CA/ON/billingValidationError.jsp",
                "error/errorpage.jsp");
    }
}
