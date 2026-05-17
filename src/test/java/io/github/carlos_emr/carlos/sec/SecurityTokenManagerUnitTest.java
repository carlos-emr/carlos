/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.sec;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for configured token-manager loading.
 */
@Tag("unit")
@Tag("security")
@Isolated
@DisplayName("SecurityTokenManager")
class SecurityTokenManagerUnitTest {
    private String originalTokenManager;

    @BeforeEach
    void setUp() {
        originalTokenManager = CarlosProperties.getInstance().getProperty("sec.token.manager");
        SecurityTokenManager.instance = null;
    }

    @AfterEach
    void tearDown() {
        if (originalTokenManager == null) {
            CarlosProperties.getInstance().remove("sec.token.manager");
        } else {
            CarlosProperties.getInstance().setProperty("sec.token.manager", originalTokenManager);
        }
        SecurityTokenManager.instance = null;
    }

    @Test
    @DisplayName("should return null when token manager is not configured")
    void shouldReturnNull_whenTokenManagerIsNotConfigured() {
        CarlosProperties.getInstance().remove("sec.token.manager");

        assertThat(SecurityTokenManager.getInstance()).isNull();
    }

    @Test
    @DisplayName("should fail fast when configured manager is outside allowed package")
    void shouldFailFast_whenConfiguredManagerIsOutsideAllowedPackage() {
        CarlosProperties.getInstance().setProperty("sec.token.manager", "java.lang.String");

        assertThatThrownBy(SecurityTokenManager::getInstance)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the allowed package");
    }

    @Test
    @DisplayName("should fail fast when configured manager cannot load")
    void shouldFailFast_whenConfiguredManagerCannotLoad() {
        CarlosProperties.getInstance().setProperty("sec.token.manager",
                "io.github.carlos_emr.carlos.sec.DoesNotExistTokenManager");

        assertThatThrownBy(SecurityTokenManager::getInstance)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to load configured token manager");
    }
}
