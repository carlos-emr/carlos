/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth1ExceptionMapper")
@Tag("unit")
@Tag("security")
class OAuth1ExceptionMapperUnitTest {

    private final OAuth1ExceptionMapper mapper = new OAuth1ExceptionMapper();

    @Test
    @DisplayName("should map a replay rejection to its carried 401 status")
    void shouldMapToCarriedStatus_whenNonceReplayed() {
        Response response = mapper.toResponse(new OAuth1Exception(401, "nonce_replayed"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getEntity()).isEqualTo("nonce_replayed");
        assertThat(response.getMediaType()).isEqualTo(MediaType.TEXT_PLAIN_TYPE);
    }

    @Test
    @DisplayName("should map a bad-request OAuth error to its carried 400 status")
    void shouldMapToCarriedStatus_whenParametersInvalid() {
        Response response = mapper.toResponse(new OAuth1Exception(400, "invalid_oauth_parameters"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).isEqualTo("invalid_oauth_parameters");
    }
}
