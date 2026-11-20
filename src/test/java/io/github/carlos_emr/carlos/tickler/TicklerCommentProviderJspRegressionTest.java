/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.tickler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code TicklerComment.provider} is mapped {@code @NotFound(IGNORE)}, so a legacy comment whose
 * provider row is gone loads with a null provider. The tickler views render comments in a loop;
 * an unguarded {@code tc.getProvider().getLastName()} lets one orphaned comment abort the page.
 */
@DisplayName("Tickler comment provider rendering regressions")
@Tag("unit")
@Tag("tickler")
class TicklerCommentProviderJspRegressionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/webapp/WEB-INF/jsp/tickler/ticklerDemoMain.jsp",
            "src/main/webapp/WEB-INF/jsp/tickler/ticklerEdit.jsp"})
    @DisplayName("should guard a missing comment provider before rendering its name")
    void shouldGuardCommentProvider_forProviderlessLegacyComment(String jsp) throws IOException {
        String source = Files.readString(Path.of(jsp), StandardCharsets.UTF_8);

        assertThat(source).doesNotContain("tc.getProvider().");
        assertThat(source).contains("Provider commentProvider = tc.getProvider();");
        assertThat(source).containsPattern("commentProvider [!=]= null");
        // The name is never written raw: only inside SafeEncode or a <carlos:encode value='...'>.
        assertThat(source).doesNotContainPattern("(?<!value=')<%=\\s*commentProvider\\.get");
    }
}
