package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class CarlosSmsMessageBodyAccessAuthorizerUnitTest {
    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    @Test
    @DisplayName("assertCanReadFullBody allows body access when message and demographic read are granted")
    void shouldAllowRead_whenMessageAndDemographicReadGranted() {
        SmsTransaction transaction = outboundTransaction();
        CarlosSmsMessageBodyAccessAuthorizer authorizer =
                new CarlosSmsMessageBodyAccessAuthorizer(securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, 123))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, 123))
                .thenReturn(true);

        assertThatCode(() -> authorizer.assertCanReadFullBody(transaction, loggedInInfo))
                .doesNotThrowAnyException();

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, 123);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, 123);
    }

    @Test
    @DisplayName("assertCanReadFullBody denies before demographic check when message read is missing")
    void shouldDenyRead_whenMessageReadIsMissing() {
        SmsTransaction transaction = outboundTransaction();
        CarlosSmsMessageBodyAccessAuthorizer authorizer =
                new CarlosSmsMessageBodyAccessAuthorizer(securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, 123))
                .thenReturn(false);

        assertThatThrownBy(() -> authorizer.assertCanReadFullBody(transaction, loggedInInfo))
                .isInstanceOfSatisfying(AccessDeniedException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getPermission()).isEqualTo("_msgSMS");
                    org.assertj.core.api.Assertions.assertThat(exception.getAction()).isEqualTo(SecurityInfoManager.READ);
                    org.assertj.core.api.Assertions.assertThat(exception.getSubject()).isEqualTo("123");
                });

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, 123);
        verify(securityInfoManager, never())
                .hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, 123);
    }

    @Test
    @DisplayName("assertCanReadFullBody denies when demographic read is missing")
    void shouldDenyRead_whenDemographicReadIsMissing() {
        SmsTransaction transaction = outboundTransaction();
        CarlosSmsMessageBodyAccessAuthorizer authorizer =
                new CarlosSmsMessageBodyAccessAuthorizer(securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, 123))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, 123))
                .thenReturn(false);

        assertThatThrownBy(() -> authorizer.assertCanReadFullBody(transaction, loggedInInfo))
                .isInstanceOfSatisfying(AccessDeniedException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getPermission()).isEqualTo("_demographic");
                    org.assertj.core.api.Assertions.assertThat(exception.getAction()).isEqualTo(SecurityInfoManager.READ);
                    org.assertj.core.api.Assertions.assertThat(exception.getSubject()).isEqualTo("123");
                });
    }

    @Test
    @DisplayName("assertCanReadFullBody requires only message read when no demographic is linked")
    void shouldRequireOnlyMessageRead_whenTransactionHasNoDemographic() {
        SmsTransaction transaction = SmsTransaction.deliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                null,
                null,
                null,
                null,
                null
        ));
        CarlosSmsMessageBodyAccessAuthorizer authorizer =
                new CarlosSmsMessageBodyAccessAuthorizer(securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, (String) null))
                .thenReturn(true);

        assertThatCode(() -> authorizer.assertCanReadFullBody(transaction, loggedInInfo))
                .doesNotThrowAnyException();

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, (String) null);
        verify(securityInfoManager, never())
                .hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, (String) null);
    }

    @Test
    @DisplayName("assertCanReadFullBody denies without logged-in context")
    void shouldDenyRead_whenLoggedInInfoIsMissing() {
        SmsTransaction transaction = outboundTransaction();
        CarlosSmsMessageBodyAccessAuthorizer authorizer =
                new CarlosSmsMessageBodyAccessAuthorizer(securityInfoManager);

        assertThatThrownBy(() -> authorizer.assertCanReadFullBody(transaction, null))
                .isInstanceOfSatisfying(AccessDeniedException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getPermission()).isEqualTo("_msgSMS");
                    org.assertj.core.api.Assertions.assertThat(exception.getAction()).isEqualTo(SecurityInfoManager.READ);
                    org.assertj.core.api.Assertions.assertThat(exception.getSubject()).isEqualTo("123");
                });

        verifyNoInteractions(securityInfoManager);
    }

    private SmsTransaction outboundTransaction() {
        return SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
    }
}
