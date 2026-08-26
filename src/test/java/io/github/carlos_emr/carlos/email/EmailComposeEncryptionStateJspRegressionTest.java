/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the encryption UI is synchronized before send-result branches can return.
 *
 * @since 2026-08-25
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email compose encryption-state rendering")
class EmailComposeEncryptionStateJspRegressionTest {

    private static final Path EMAIL_COMPOSE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp");

    @Test
    @DisplayName("should apply encryption state before failed-send initialization returns")
    void shouldApplyEncryptionState_beforeSendResultBranchReturns() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int domReady = jsp.indexOf("document.addEventListener(\"DOMContentLoaded\"");
        int applyState = jsp.indexOf("applyEncryptionState();", domReady);
        int sendResultBranch = jsp.indexOf("// A successful send is terminal", domReady);

        assertThat(domReady).isGreaterThanOrEqualTo(0);
        assertThat(applyState).isGreaterThan(domReady);
        assertThat(applyState).isLessThan(sendResultBranch);
    }
}
