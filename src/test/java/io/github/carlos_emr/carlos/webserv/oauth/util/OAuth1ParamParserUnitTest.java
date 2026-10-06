package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Request;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("security")
class OAuth1ParamParserUnitTest {

    @ParameterizedTest
    @CsvSource({
            "application/x-www-form-urlencoded, true",
            "'Application/X-Www-Form-Urlencoded; charset=UTF-8', true",
            "application/x-www-form-urlencoded-json, false",
            "application/json, false",
            "'application/x-www-form-urlencoded invalid', false"
    })
    void shouldHonorExactFormMediaType_whenIncludingBodyParameters(
            String contentType, boolean includeBody) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth");
        request.addHeader("Authorization", "OAuth oauth_consumer_key=consumer,oauth_signature=signature,"
                + "oauth_signature_method=HMAC-SHA1,oauth_timestamp=1,oauth_nonce=nonce");
        request.setContentType(contentType);
        request.addParameter("body", "value");

        OAuth1Request parsed = new OAuth1ParamParser().parseFromRequest(request);

        assertThat(parsed.params.containsKey("body")).isEqualTo(includeBody);
    }
}
