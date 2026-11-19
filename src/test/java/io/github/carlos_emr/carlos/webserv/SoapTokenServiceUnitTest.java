/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.commn.dao.SecurityTokenDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.SecurityToken;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class SoapTokenServiceUnitTest extends CarlosUnitTestBase {
    private static final Instant NOW = Instant.parse("2026-11-09T04:00:00Z");
    private SecurityTokenDao tokens;
    private SecurityInfoManager privileges;
    private SoapTokenService service;
    private Security account;
    private MockedStatic<LogAction> audit;
    private final Map<String, SecurityToken> stored = new LinkedHashMap<>();

    @BeforeEach
    void setup() {
        tokens = mock(SecurityTokenDao.class);
        privileges = mock(SecurityInfoManager.class);
        service = serviceAt(NOW);
        account = new Security();
        account.setId(41);
        account.setUserName("FAKE-user");
        account.setPassword("FAKE-password-hash");
        account.setProviderNo("31");
        when(tokens.findSoapToken(anyString())).thenAnswer(call -> stored.get(call.<String>getArgument(0)));
        doAnswer(call -> {
            SecurityToken row = call.getArgument(0);
            stored.put(row.getToken(), row);
            return null;
        }).when(tokens).persist(any(SecurityToken.class));
        when(tokens.revokeSoapTokens(anyString())).thenAnswer(call -> {
            int before = stored.size();
            stored.values().removeIf(row -> row.getData().startsWith(call.<String>getArgument(0)));
            return before - stored.size();
        });
        audit = logActionMock;
    }

    private SoapTokenService serviceAt(Instant at) {
        return new SoapTokenService(tokens, privileges, Clock.fixed(at, ZoneOffset.UTC), new SecureRandom(), Duration.ofHours(1));
    }

    private LoggedInInfo actor(Security security) {
        var info = new LoggedInInfo();
        info.setLoggedInSecurity(security);
        return info;
    }

    @Test
    void shouldIssueIndependentOpaqueTokens_withoutStoringOrAuditingTheirPlaintext() {
        String first = service.issue(account);
        String second = service.issue(account);
        assertThat(first).matches("ct1\\.[A-Za-z0-9_-]{43}").isNotEqualTo(second);
        assertThat(stored).hasSize(2);
        assertThat(stored.values()).allSatisfy(row -> {
            assertThat(row.getToken()).matches("soap1:[0-9a-f]{64}").doesNotContain(first, second);
            assertThat(row.getData()).startsWith("soap1:41:").doesNotContain(first, second, account.getPassword(), account.getUserName());
            assertThat(row.getCreated()).isEqualTo(Date.from(NOW));
            assertThat(row.getExpiry()).isEqualTo(Date.from(NOW.plusSeconds(3600)));
        });
        audit.verify(() -> LogAction.addLogStrict(any(LoggedInInfo.class), eq("SoapToken.issue"), isNull(), isNull(),
                isNull(), eq("securityId=41")), times(2));
        assertThat(service.validate(account, first)).isTrue();
        assertThat(service.validate(account, second)).isTrue();
    }

    @Test
    void shouldValidateAcrossIndependentInstances_andExpireAtTheAbsoluteBoundary() {
        String token = service.issue(account);
        assertThat(serviceAt(NOW.plusSeconds(3599)).validate(account, token)).isTrue();
        assertThat(serviceAt(NOW.plusSeconds(3600)).validate(account, token)).isFalse();
        assertThat(serviceAt(NOW.plusSeconds(3601)).validate(account, token)).isFalse();
        assertThat(serviceAt(NOW.minusSeconds(30)).validate(account, token)).isTrue();
        assertThat(serviceAt(NOW.minusSeconds(61)).validate(account, token)).isFalse();
    }

    @Test
    void shouldRevokeOnCredentialOrAccountBindingChanges_evenWhenPasswordsAreShared() {
        String token = service.issue(account);
        account.setId(42);
        assertThat(service.validate(account, token)).isFalse();
        account.setId(41);
        account.setPassword("FAKE-new-hash");
        assertThat(service.validate(account, token)).isFalse();
        account.setPassword("FAKE-password-hash");
        account.setProviderNo("32");
        assertThat(service.validate(account, token)).isFalse();
        account.setProviderNo("31");
        account.setUserName("FAKE-renamed");
        assertThat(service.validate(account, token)).isFalse();
        account.setUserName("FAKE-user");
        account.setDateExpiredate(Date.from(NOW));
        assertThat(service.validate(account, token)).isFalse();
    }

    @Test
    void shouldRevokeOnlyTheAuthorizedAccount_withoutTouchingOtherPurposes() {
        String first = service.issue(account);
        account.setId(42);
        String second = service.issue(account);
        assertThatThrownBy(() -> service.revokeAll(actor(account), 41)).isInstanceOf(SecurityException.class);
        verify(tokens, never()).revokeSoapTokens(anyString());
        account.setId(41);
        assertThat(service.revokeAll(actor(account), 41)).isEqualTo(1);
        assertThat(service.validate(account, first)).isFalse();
        account.setId(42);
        assertThat(service.validate(account, second)).isTrue();
        when(privileges.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull())).thenReturn(true);
        assertThat(service.revokeAll(actor(account), 41)).isZero();
    }

    @Test
    void shouldFailExplicitly_onCorruptStorageAndDatabaseFailures() {
        String token = service.issue(account);
        stored.values().iterator().next().setData("soap1:41:broken");
        assertThatThrownBy(() -> service.validate(account, token)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid SOAP token storage");
        when(tokens.findSoapToken(anyString())).thenThrow(new IllegalStateException("FAKE-store-failure"));
        assertThatThrownBy(() -> service.validate(account, token)).hasMessage("FAKE-store-failure");
        assertThatThrownBy(() -> service.issue(account)).hasMessage("FAKE-store-failure");
    }

    @Test
    void shouldRefuseTokens_whenTheAccountOrCredentialIsAbsent() {
        for (String token : new String[]{null, "", "123456789", "ct1.short", "ct1." + "!".repeat(43)}) {
            assertThat(service.validate(account, token)).isFalse();
        }
        assertThat(service.validate(null, "ct1." + "A".repeat(43))).isFalse();
        assertThatThrownBy(() -> service.issue(null)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRejectUnknownTokens_andReportMissingStoredMetadataExplicitly() {
        assertThat(service.validate(account, "ct1." + "A".repeat(43))).isFalse();
        String token = service.issue(account);
        stored.values().iterator().next().setData(null);
        assertThatThrownBy(() -> service.validate(account, token)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid SOAP token storage");
    }

    @Test
    void shouldRejectInvalidLifetimeConfiguration_insteadOfIssuingUnboundedCredentials() {
        for (Duration lifetime : new Duration[]{Duration.ZERO, Duration.ofMinutes(-1), Duration.ofHours(25)}) {
            assertThatThrownBy(() -> new SoapTokenService(tokens, privileges, Clock.systemUTC(), new SecureRandom(), lifetime))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldPropagateInsertFailure_withoutReturningACredentialOrAuditingSuccess() {
        doThrow(new IllegalStateException("FAKE-insert-unavailable")).when(tokens).persist(any());
        assertThatThrownBy(() -> service.issue(account)).hasMessage("FAKE-insert-unavailable");
        assertThat(stored).isEmpty();
        audit.verifyNoInteractions();
    }

    @Test
    void shouldRefuseIssuance_whenAGenericTokenConsumerCouldReplayTheStoredDigest() {
        when(tokens.getByTokenAndExpiry(anyString(), any(Date.class))).thenReturn(new SecurityToken());
        assertThatThrownBy(() -> service.issue(account)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Token DAO does not isolate SOAP credential hashes");
        audit.verifyNoInteractions();
    }

    @Test
    void shouldRefuseRepeatedEntropyCollisions_withoutReturningAnUnpersistedCredential() {
        when(tokens.findSoapToken(anyString())).thenReturn(new SecurityToken());
        assertThatThrownBy(() -> service.issue(account)).isInstanceOf(IllegalStateException.class)
                .hasMessage("SOAP token entropy collision");
        verify(tokens, times(3)).findSoapToken(anyString());
        verify(tokens, never()).persist(any());
        audit.verifyNoInteractions();
    }
}
