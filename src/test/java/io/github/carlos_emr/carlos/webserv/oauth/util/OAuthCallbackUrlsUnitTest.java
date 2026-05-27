/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OAuth callback URL validation")
@Tag("unit")
@Tag("security")
class OAuthCallbackUrlsUnitTest {

    @Test
    @DisplayName("should normalize http callback URLs")
    void shouldNormalizeHttpCallbackUrls() {
        String result = OAuthCallbackUrls.normalizeHttpCallback("HTTPS://App.Example:443/callback/../done?x=1");

        assertThat(result).isEqualTo("https://app.example/done?x=1");
    }

    @Test
    @DisplayName("should reject non-http callback schemes")
    void shouldRejectNonHttpCallbackSchemes() {
        assertThatThrownBy(() -> OAuthCallbackUrls.normalizeHttpCallback("javascript:alert(1)"))
                .isInstanceOfSatisfying(OAuth1Exception.class, e -> {
                    assertThat(e.getHttpCode()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("invalid_callback_scheme");
                });
    }

    @Test
    @DisplayName("should reject malformed callback URLs")
    void shouldRejectMalformedCallbackUrls() {
        assertThatThrownBy(() -> OAuthCallbackUrls.normalizeHttpCallback("https://exa mple/callback"))
                .isInstanceOfSatisfying(OAuth1Exception.class, e -> {
                    assertThat(e.getHttpCode()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("invalid_callback");
                });
    }

    @Test
    @DisplayName("should reject callbacks without a host")
    void shouldRejectCallbacksWithoutHost() {
        assertThatThrownBy(() -> OAuthCallbackUrls.normalizeHttpCallback("https:/callback"))
                .isInstanceOfSatisfying(OAuth1Exception.class, e -> {
                    assertThat(e.getHttpCode()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("invalid_callback");
                });
    }
}
