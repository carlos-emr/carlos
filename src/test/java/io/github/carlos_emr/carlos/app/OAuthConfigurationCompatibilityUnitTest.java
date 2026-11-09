/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Tag("oauth")
class OAuthConfigurationCompatibilityUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldRetainOutboundApplicationSchema_withCommentsAndEscapedSecrets() throws Exception {
        String xml = "<!-- before root --><app><type>oauth1</type><name>FAKE</name>"
                + "<consumerKey>key-é</consumerKey><consumerSecret><![CDATA[secret<&患者]]></consumerSecret>"
                + "<baseURL>https://example.invalid/api?a=1&amp;b=2</baseURL>"
                + "<requestTokenService>request</requestTokenService><accessTokenService>access</accessTokenService>"
                + "<authorizationServiceURI>authorize</authorizationServiceURI></app>";
        AppAuthConfig config = AppOAuth1Config.fromDocument(xml);
        assertThat(config).isInstanceOf(AppOAuth1Config.class);
        var outbound = (AppOAuth1Config) config;
        assertThat(outbound.getType()).isEqualTo("oauth1");
        assertThat(outbound.getName()).isEqualTo("FAKE");
        assertThat(outbound.getConsumerKey()).isEqualTo("key-é");
        assertThat(outbound.getConsumerSecret()).isEqualTo("secret<&患者");
        assertThat(outbound.getBaseURL()).isEqualTo("https://example.invalid/api?a=1&b=2");
        assertThat(outbound.getRequestTokenService()).isEqualTo("request");
        assertThat(outbound.getAccessTokenService()).isEqualTo("access");
        assertThat(outbound.getAuthorizationServiceURI()).isEqualTo("authorize");
        assertThat(AppOAuth1Config.fromDocument(XmlUtils.toDocument(xml)).getConsumerSecret())
                .isEqualTo(outbound.getConsumerSecret());
    }

    @Test
    void shouldRetainInboundVerifierSchema_forTextAndDocumentInputs() throws Exception {
        String xml = "<!-- ignored --><config><consumerKey> key </consumerKey>"
                + "<consumerSecret>secret&amp;é</consumerSecret><baseUrl> https://example.invalid/ </baseUrl>"
                + "<callbackURI>callback</callbackURI><applicationURI>application</applicationURI>"
                + "<scopes>read, write</scopes></config>";
        for (Object source : new Object[]{xml, XmlUtils.toDocument(xml)}) {
            var inbound = io.github.carlos_emr.carlos.login.AppOAuth1Config.fromDocument(source);
            assertThat(inbound).isNotNull();
            assertThat(inbound.getConsumerKey()).isEqualTo("key");
            assertThat(inbound.getConsumerSecret()).isEqualTo("secret&é");
            assertThat(inbound.getBaseUrl()).isEqualTo("https://example.invalid/");
            assertThat(inbound.getCallbackURI()).isEqualTo("callback");
            assertThat(inbound.getApplicationURI()).isEqualTo("application");
            assertThat(inbound.getScopes()).containsExactly("read", " write");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<config><consumerSecret>PRIVATE-SECRET</config>",
            "<config><consumerKey>a</consumerKey><consumerKey>b</consumerKey><consumerSecret>PRIVATE-SECRET</consumerSecret></config>",
            "<config><consumerSecret><nested>PRIVATE-SECRET</nested></consumerSecret></config>",
            "<!DOCTYPE config [<!ENTITY secret SYSTEM 'file:///PRIVATE-SECRET'>]><config><consumerSecret>&secret;</consumerSecret></config>",
            "<!DOCTYPE config [<!ENTITY secret 'PRIVATE-SECRET'>]><config><consumerSecret>&secret;</consumerSecret></config>",
            "<!DOCTYPE config SYSTEM 'https://example.invalid/PRIVATE-SECRET'><config/>"
    })
    void shouldRejectMalformedOrAmbiguousDocuments_withoutCredentialDiagnostics(String xml) {
        try (LogCapture logs = LogCapture.forLogger(io.github.carlos_emr.carlos.login.AppOAuth1Config.class)) {
            assertThat(io.github.carlos_emr.carlos.login.AppOAuth1Config.fromDocument(xml)).isNull();
            assertThatThrownBy(() -> AppOAuth1Config.fromDocument(xml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid OAuth configuration XML")
                    .hasNoCause();
            assertThat(logs.messages()).containsExactly("OAuth configuration XML could not be parsed");
            assertThat(logs.messages()).allSatisfy(message -> assertThat(message)
                    .doesNotContain("PRIVATE-SECRET", "consumerSecret", xml));
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }
    }

    @Test
    void shouldFailExplicitly_whenTheXmlParserCannotEnforceItsProtections() {
        try (var xml = org.mockito.Mockito.mockStatic(XmlUtils.class)) {
            xml.when(XmlUtils::createSecureDocumentBuilderFactory)
                    .thenThrow(new javax.xml.parsers.ParserConfigurationException("FAKE-unavailable"));
            assertThatThrownBy(() -> AppOAuth1Config.fromDocument("<app/>"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("OAuth XML parser protections are unavailable");
            assertThatThrownBy(() -> io.github.carlos_emr.carlos.login.AppOAuth1Config.fromDocument("<app/>"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("OAuth XML parser protections are unavailable");
        }
    }

    @Test
    void shouldPreserveEmptyAndMissingOptionalFields_withoutRenamingEitherPublicApi() throws Exception {
        var outbound = AppOAuth1Config.fromDocument("<app><baseURL/><consumerKey> padded </consumerKey></app>");
        assertThat(outbound.getBaseURL()).isEmpty();
        assertThat(outbound.getConsumerKey()).isEqualTo(" padded ");
        assertThat(outbound.getConsumerSecret()).isNull();
        var inbound = io.github.carlos_emr.carlos.login.AppOAuth1Config.fromDocument("<config><baseUrl/></config>");
        assertThat(inbound.getBaseUrl()).isEmpty();
        assertThat(inbound.getScopes()).isNull();
        assertThat(io.github.carlos_emr.carlos.login.AppOAuth1Config.fromDocument(null)).isNull();
        assertThat(io.github.carlos_emr.carlos.login.AppOAuth1Config.fromDocument("  ")).isNull();
        assertThat(inbound).isNotInstanceOf(AppAuthConfig.class);
    }

    @Test
    void shouldRoundTripOutboundTokens_withoutReadingCommentsAsTheRoot() throws Exception {
        String token = "FAKE-é<&患者";
        String secret = "FAKE-secret<&";
        String xml = AppOAuth1Config.getTokenXML(token, secret);
        assertThat(AppOAuth1Config.getKeySecret("<!-- prefix -->" + xml.replaceFirst("<\\?xml[^>]*\\?>", "")))
                .containsEntry("key", token).containsEntry("secret", secret);
        assertThatThrownBy(() -> AppOAuth1Config.getKeySecret("<token><key>a</key><key>b</key></token>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
