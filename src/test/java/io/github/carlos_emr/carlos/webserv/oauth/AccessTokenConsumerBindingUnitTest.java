/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.webserv.oauth;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1ParamParser;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
class AccessTokenConsumerBindingUnitTest {
    @Test
    void shouldRefuseExchange_whenRequestTokenBelongsToAnotherClient() {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        OAuth1Request oauth = new OAuth1Request();
        oauth.consumerKey = "other-client";
        oauth.token = "fixture-request-token";
        when(parser.parseFromRequest(request)).thenReturn(oauth);
        when(provider.getClient("other-client")).thenReturn(new Client("other-client", "other-secret", "Other", ""));
        when(provider.getRequestToken(oauth.token)).thenReturn(new RequestToken(
                new Client("original-client", "original-secret", "Original", ""), oauth.token, "token-secret"));
        AccessTokenResource endpoint = new AccessTokenResource(provider, parser, verifier);
        ReflectionTestUtils.setField(endpoint, "req", request);
        try (Response response = endpoint.exchange()) {
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getEntity()).isEqualTo("invalid_request_token");
        }
        verifyNoInteractions(verifier);
    }
}
