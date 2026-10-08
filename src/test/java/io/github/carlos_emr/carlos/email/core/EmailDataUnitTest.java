/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for merged email content and consent-audit validation in {@link EmailData}.
 *
 * @since 2026-07-06
 */
@Tag("unit")
@Tag("fast")
@DisplayName("EmailData")
class EmailDataUnitTest {

    @Test
    @DisplayName("should prefer the encrypted-message channel when encryption is on")
    void shouldReturnEncryptedMessage_whenEncryptionOn() {
        assertThat(EmailData.mergeMessage(true, "cleartext body", "secret pdf content"))
                .isEqualTo("secret pdf content");
    }

    @Test
    @DisplayName("should fall back to the body channel when encrypted-message is empty and encryption is on")
    void shouldFallBackToBody_whenEncryptedMessageEmptyAndEncryptionOn() {
        assertThat(EmailData.mergeMessage(true, "cleartext body", "")).isEqualTo("cleartext body");
    }

    @Test
    @DisplayName("should prefer the body channel when encryption is off")
    void shouldReturnBody_whenEncryptionOff() {
        assertThat(EmailData.mergeMessage(false, "cleartext body", "secret pdf content"))
                .isEqualTo("cleartext body");
    }

    @Test
    @DisplayName("should not move encrypted-message content into an encryption-off draft")
    void shouldNotReturnEncryptedMessage_whenBodyEmptyAndEncryptionOff() {
        assertThat(EmailData.mergeMessage(false, null, "secret pdf content")).isEmpty();
    }

    @Test
    @DisplayName("should force encryption on when protected content is the only available message")
    void shouldForceEncryptionOn_whenEncryptedMessageIsOnlyContent() {
        boolean encrypted = EmailData.resolveMergedMessageEncryption(false, null, "secret pdf content");

        assertThat(encrypted).isTrue();
        assertThat(EmailData.mergeMessage(encrypted, null, "secret pdf content"))
                .isEqualTo("secret pdf content");
    }

    @Test
    @DisplayName("should preserve encryption off when the cleartext body is populated")
    void shouldPreserveEncryptionOff_whenBodyPopulated() {
        assertThat(EmailData.resolveMergedMessageEncryption(
                false, "cleartext body", "stale encrypted content")).isFalse();
    }

    @Test
    @DisplayName("should return an empty string when both channels are null")
    void shouldReturnEmpty_whenBothChannelsNull() {
        assertThat(EmailData.mergeMessage(true, null, null)).isEmpty();
        assertThat(EmailData.mergeMessage(false, null, null)).isEmpty();
    }

    @Test
    @DisplayName("should reject consent override reasons that cannot be persisted in full")
    void shouldRejectConsentOverrideReason_whenLongerThanColumnLimit() {
        EmailData emailData = new EmailData();

        assertThatThrownBy(() -> emailData.setConsentOverrideReason("a".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consent override reason must not exceed 255 characters");
    }
    @Test
    @DisplayName("should allow adding attachments to default list")
    void shouldAllowAddingAttachments_whenUsingDefaultList() {
        EmailData emailData = new EmailData();

        emailData.getAttachments().add(new EmailAttachment());

        assertThat(emailData.getAttachments()).hasSize(1);
    }

    @Test
    @DisplayName("should allow adding attachments after setting null list")
    void shouldAllowAddingAttachments_whenAttachmentsAreSetToNull() {
        EmailData emailData = new EmailData();
        emailData.setAttachments(null);

        emailData.getAttachments().add(new EmailAttachment());

        assertThat(emailData.getAttachments()).hasSize(1);
    }
    @Test
    @DisplayName("should send the body exactly as today when there is no footer")
    void shouldReturnBodyUnchanged_whenFooterEmptyOrNull() {
        EmailData emailData = new EmailData();
        emailData.setBody("Hello\n");

        assertThat(emailData.getFooter()).isEmpty();
        assertThat(emailData.getTransmittedBody()).isEqualTo("Hello\n");
        emailData.setFooter(null);
        assertThat(emailData.getFooter()).isEmpty();
        assertThat(emailData.getTransmittedBody()).isEqualTo("Hello\n");
    }

    @Test
    @DisplayName("should add no trailing blank lines for a footer of only whitespace")
    void shouldReturnBodyUnchanged_whenFooterWhitespaceOnly() {
        EmailData emailData = new EmailData();
        emailData.setBody("Hello\n");
        emailData.setFooter(" \r\n\t\u2003 ");

        assertThat(emailData.getTransmittedBody()).isEqualTo("Hello\n");
        // what the log keeps is what was sent: no footer
        assertThat(emailData.getSentFooter()).isEmpty();
    }

    @Test
    @DisplayName("should put the footer's plain text after exactly one blank line")
    void shouldAppendTrimmedFooter_afterOneBlankLine() {
        EmailData emailData = new EmailData();
        emailData.setBody("Hello\n\n\n");
        emailData.setFooter("<b>Riverside Clinic</b><br>Not monitored for urgent issues.  <br><br>");

        assertThat(emailData.getTransmittedBody())
                .isEqualTo("Hello\n\nRiverside Clinic\nNot monitored for urgent issues.");
        assertThat(emailData.getBody()).isEqualTo("Hello\n\n\n");
        // The log keeps the formatted footer, without the editor's trailing line breaks.
        assertThat(emailData.getSentFooter()).isEqualTo("<b>Riverside Clinic</b><br>Not monitored for urgent issues.");
    }

    @Test
    @DisplayName("should send and log a footer without anything outside the footer's allow-list")
    void shouldCleanFooter_whenFooterCarriesScriptOrUnsafeLink() {
        EmailData emailData = new EmailData();
        emailData.setBody("Hello");
        emailData.setFooter("<script>alert(1)</script><a href=\"javascript:alert(1)\" onclick=\"x()\">Clinic</a>"
                + "<img src=\"https://tracker.example/p.gif\"><a href=\"https://clinic.example\">Website</a>");

        assertThat(emailData.getSentFooter())
                .isEqualTo("<a>Clinic</a><a href=\"https://clinic.example\">Website</a>")
                .doesNotContain("script", "javascript", "onclick", "img");
        assertThat(emailData.getTransmittedBody()).isEqualTo("Hello\n\nClinicWebsite <https://clinic.example>");
        assertThat(emailData.getTransmittedHtml()).doesNotContain("<script", "javascript:", "tracker.example");
    }

    @Test
    @DisplayName("should send plain text only, with no logo, when there is no footer")
    void shouldReturnNoHtmlAndNoLogo_whenFooterEmpty() {
        EmailData emailData = new EmailData();
        emailData.setBody("Hello");
        emailData.setFooterLogo(new EmailInlineImage("clinic-logo-1@carlos-emr", "image/png", new byte[] {1}));
        emailData.setFooter("<br> &nbsp; <b></b>");

        assertThat(emailData.getSentFooter()).isEmpty();
        assertThat(emailData.getTransmittedHtml()).isNull();
        assertThat(emailData.getFooterLogo()).isNull();
    }

    @Test
    @DisplayName("should build the formatted version from the escaped message, the logo and the cleaned footer")
    void shouldBuildHtmlVersion_whenFooterAndLogoSet() {
        EmailData emailData = new EmailData();
        emailData.setBody("Results are <ready>\nCall us");
        EmailInlineImage logo = new EmailInlineImage("clinic-logo-ab12@carlos-emr", "image/png", new byte[] {1, 2});
        emailData.setFooterLogo(logo);
        emailData.setFooter("<i>Riverside Clinic</i>");

        String html = emailData.getTransmittedHtml();

        assertThat(html).contains("Results are &lt;ready&gt;<br>Call us")
                .contains("<img src=\"cid:clinic-logo-ab12@carlos-emr\"")
                .contains("<i>Riverside Clinic</i>");
        assertThat(html.indexOf("cid:")).isLessThan(html.indexOf("Riverside Clinic"));
        assertThat(emailData.getFooterLogo()).isSameAs(logo);
    }

    @Test
    @DisplayName("should put the footer after the encrypted-message notice and never in the PDF content")
    void shouldAppendFooterAfterNotice_whenMessageEncrypted() {
        EmailData emailData = new EmailData();
        emailData.setIsEncrypted(true);
        emailData.setBody("You have a secure message.");
        emailData.setEncryptedMessage("Confidential result");
        emailData.setFooter("Riverside Clinic");

        assertThat(emailData.getTransmittedBody()).isEqualTo("You have a secure message.\n\nRiverside Clinic");
        assertThat(emailData.getTransmittedHtml()).contains("You have a secure message.").doesNotContain("Confidential result");
        assertThat(emailData.getEncryptedMessage()).isEqualTo("Confidential result");
        assertThat(emailData.getBody()).isEqualTo("You have a secure message.");
    }

    @Test
    void shouldDefaultToWithoutNote_whenChartOptionIsAbsentOrNull() {
        EmailData data = new EmailData();
        assertThat(data.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITHOUT_NOTE);
        data.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
        data.setChartDisplayOption((ChartDisplayOption) null);
        assertThat(data.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITHOUT_NOTE);
        data.setChartDisplayOption("addFullNote");
        assertThat(data.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITH_FULL_NOTE);
        data.setChartDisplayOption((String) null);
        assertThat(data.getChartDisplayOption()).isEqualTo(ChartDisplayOption.WITHOUT_NOTE);
    }
}
