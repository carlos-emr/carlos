/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailData")
class EmailDataUnitTest {

    @Test
    @DisplayName("should apply safe defaults when nullable fields are set to null")
    void shouldApplySafeDefaults_whenNullableFieldsAreNull() {
        EmailData emailData = new EmailData();

        emailData.setSenderConfigId((String) null);
        emailData.setSender(null);
        emailData.setRecipients(null);
        emailData.setSubject(null);
        emailData.setBody(null);
        emailData.setEncryptedMessage(null);
        emailData.setPassword(null);
        emailData.setPasswordClue(null);
        emailData.setIsEncrypted((String) null);
        emailData.setIsAttachmentEncrypted((String) null);
        emailData.setChartDisplayOption((String) null);
        emailData.setInternalComment(null);
        emailData.setTransactionType((String) null);
        emailData.setDemographicNo((String) null);
        emailData.setProviderNo(null);
        emailData.setAdditionalParams(null);
        emailData.setAttachments(null);

        assertThat(emailData.getSenderConfigId()).isNull();
        assertThat(emailData.getSender()).isEmpty();
        assertThat(emailData.getRecipients()).isEmpty();
        assertThat(emailData.getSubject()).isEmpty();
        assertThat(emailData.getBody()).isEmpty();
        assertThat(emailData.getEncryptedMessage()).isEmpty();
        assertThat(emailData.getPassword()).isEmpty();
        assertThat(emailData.getPasswordClue()).isEmpty();
        assertThat(emailData.getIsEncrypted()).isFalse();
        assertThat(emailData.getIsAttachmentEncrypted()).isFalse();
        assertThat(emailData.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITH_FULL_NOTE);
        assertThat(emailData.getInternalComment()).isEmpty();
        assertThat(emailData.getTransactionType()).isEqualTo(TransactionType.DIRECT);
        assertThat(emailData.getDemographicNo()).isEqualTo(-1);
        assertThat(emailData.getProviderNo()).isEqualTo("-1");
        assertThat(emailData.getAdditionalParams()).isEmpty();
        assertThat(emailData.getAttachments()).isEmpty();
    }

    @Test
    @DisplayName("should parse request string fields with constrained values")
    void shouldParseRequestStringFields_withConstrainedValues() {
        EmailData emailData = new EmailData();

        emailData.setSenderConfigId("42");
        emailData.setIsEncrypted("true");
        emailData.setIsAttachmentEncrypted("true");
        emailData.setChartDisplayOption("DoNotAddAsNote");
        emailData.setTransactionType("consultation");
        emailData.setDemographicNo("123");
        emailData.setAdditionalParams("custom=params");

        assertThat(emailData.getSenderConfigId()).isEqualTo(42);
        assertThat(emailData.getIsEncrypted()).isTrue();
        assertThat(emailData.getIsAttachmentEncrypted()).isTrue();
        assertThat(emailData.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITHOUT_NOTE);
        assertThat(emailData.getTransactionType()).isEqualTo(TransactionType.CONSULTATION);
        assertThat(emailData.getDemographicNo()).isEqualTo(123);
        assertThat(emailData.getAdditionalParams()).isEqualTo("custom=params");

        emailData.setSenderConfigId("not-a-number");
        emailData.setIsEncrypted("TRUE");
        emailData.setIsAttachmentEncrypted("yes");
        emailData.setChartDisplayOption("DoNotAddAsNotf");
        emailData.setTransactionType("ticklef");

        assertThat(emailData.getSenderConfigId()).isNull();
        assertThat(emailData.getIsEncrypted()).isFalse();
        assertThat(emailData.getIsAttachmentEncrypted()).isFalse();
        assertThat(emailData.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITH_FULL_NOTE);
        assertThat(emailData.getTransactionType()).isEqualTo(TransactionType.DIRECT);
    }

    @Test
    @DisplayName("should defensively copy recipient and attachment collections")
    void shouldDefensivelyCopyRecipientsAndAttachments() {
        EmailData emailData = new EmailData();
        String[] recipients = {"patient@example.com"};
        List<EmailAttachment> attachments = new ArrayList<>();
        attachments.add(new EmailAttachment("lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 10));

        emailData.setRecipients(recipients);
        emailData.setAttachments(attachments);
        recipients[0] = "attacker@example.com";
        attachments.clear();

        String[] returnedRecipients = emailData.getRecipients();
        returnedRecipients[0] = "changed@example.com";

        assertThat(emailData.getRecipients()).containsExactly("patient@example.com");
        assertThat(emailData.getAttachments())
                .extracting(EmailAttachment::getFileName)
                .containsExactly("lab.pdf");
        List<EmailAttachment> returnedAttachments = emailData.getAttachments();
        EmailAttachment extraAttachment = new EmailAttachment("extra.pdf", "/tmp/extra.pdf", DocumentType.DOC, 11);

        assertThatThrownBy(() -> returnedAttachments.add(extraAttachment))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should preserve directly assigned enum and scalar values")
    void shouldPreserveDirectlyAssignedEnumAndScalarValues() {
        EmailData emailData = new EmailData();

        emailData.setSenderConfigId(7);
        emailData.setSender("clinic@example.com");
        emailData.setSubject("Subject");
        emailData.setBody("Body");
        emailData.setEncryptedMessage("Encrypted");
        emailData.setPassword("secret");
        emailData.setPasswordClue("Delivery note");
        emailData.setIsEncrypted(true);
        emailData.setIsAttachmentEncrypted(true);
        emailData.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        emailData.setInternalComment("Internal");
        emailData.setTransactionType(TransactionType.EFORM);
        emailData.setDemographicNo(456);
        emailData.setProviderNo("999998");

        assertThat(emailData.getSenderConfigId()).isEqualTo(7);
        assertThat(emailData.getSender()).isEqualTo("clinic@example.com");
        assertThat(emailData.getSubject()).isEqualTo("Subject");
        assertThat(emailData.getBody()).isEqualTo("Body");
        assertThat(emailData.getEncryptedMessage()).isEqualTo("Encrypted");
        assertThat(emailData.getPassword()).isEqualTo("secret");
        assertThat(emailData.getPasswordClue()).isEqualTo("Delivery note");
        assertThat(emailData.getIsEncrypted()).isTrue();
        assertThat(emailData.getIsAttachmentEncrypted()).isTrue();
        assertThat(emailData.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITHOUT_NOTE);
        assertThat(emailData.getInternalComment()).isEqualTo("Internal");
        assertThat(emailData.getTransactionType()).isEqualTo(TransactionType.EFORM);
        assertThat(emailData.getDemographicNo()).isEqualTo(456);
        assertThat(emailData.getProviderNo()).isEqualTo("999998");
    }
}
