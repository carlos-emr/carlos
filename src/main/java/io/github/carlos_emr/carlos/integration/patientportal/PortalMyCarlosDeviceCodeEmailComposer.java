/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.email.core.EmailData;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The email that carries a myCarlos device code (clinic code v1, mycarlos#141). It names only the
 * clinic and the code: no health information, no patient name. The text comes from the CARLOS
 * resource bundles in the patient's language when CARLOS has it, else English.
 */
final class PortalMyCarlosDeviceCodeEmailComposer {
    static final String SUBJECT_KEY = "mycarlos.code.email.subject";
    static final String BODY_KEY = "mycarlos.code.email.body";
    private static final Locale PORTUGUESE_BRAZIL = Locale.of("pt", "BR");

    private PortalMyCarlosDeviceCodeEmailComposer() {
    }

    /**
     * The patient's language among the five CARLOS has text in: the chart's spoken language first,
     * then its official language (English or French), else English.
     */
    static Locale locale(String spokenLanguage, String officialLanguage) {
        Locale spoken = known(spokenLanguage);
        if (spoken != null) return spoken;
        Locale official = known(officialLanguage);
        return official == null ? Locale.ENGLISH : official;
    }

    private static Locale known(String language) {
        if (language == null) return null;
        // Chart languages are words such as "French" or "Portuguese"; compared without case.
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "english", "eng", "en" -> Locale.ENGLISH;
            case "french", "français", "francais", "fre", "fra", "fr" -> Locale.FRENCH;
            case "spanish", "español", "espanol", "spa", "es" -> Locale.of("es");
            case "polish", "polski", "pol", "pl" -> Locale.of("pl");
            case "portuguese", "português", "portugues", "por", "pt", "pt-br", "pt_br" -> PORTUGUESE_BRAZIL;
            default -> null;
        };
    }

    static String subject(Locale locale) {
        return bundle(locale).getString(SUBJECT_KEY);
    }

    /**
     * @param displayCode the 9-digit code as "ddd ddd ddd"
     * @param minutes whole minutes until the code expires, at least 1
     */
    static String body(Locale locale, String clinicName, String displayCode, long minutes) {
        if (!displayCode.matches("[0-9]{3} [0-9]{3} [0-9]{3}")) {
            throw new IllegalArgumentException("device code must be shown as ddd ddd ddd");
        }
        MessageFormat format = new MessageFormat(bundle(locale).getString(BODY_KEY), locale);
        return format.format(new Object[] {clinicName, displayCode, Math.max(1, minutes)});
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("oscarResources", locale);
    }

    /**
     * The outbox request for one code email, sent as the system provider. The body is cleared once
     * sent ({@link TransactionType#MYCARLOS_CODE}) and the code is redacted from the archived copy.
     */
    // The empty password fields mark the email as unencrypted; they are not credentials.
    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "Empty strings mark the email unencrypted; they are not authentication credentials")
    static EmailData request(int demographicNo, String recipient, int senderConfigId, String providerNo,
            String subject, String body, String displayCode) {
        EmailData email = new EmailData();
        email.setSenderConfigId(senderConfigId);
        email.setRecipients(new String[] {recipient});
        email.setSubject(subject);
        email.setBody(body);
        email.setEncryptedMessage("");
        email.setPassword("");
        email.setPasswordClue("");
        email.setIsEncrypted(false);
        email.setIsAttachmentEncrypted(false);
        email.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        email.setInternalComment("");
        email.setTransactionType(TransactionType.MYCARLOS_CODE);
        email.setDemographicNo(demographicNo);
        email.setProviderNo(providerNo);
        email.setAdditionalParams("");
        email.setAttachments(Collections.emptyList());
        // The outbound archive is a permanent patient document; it keeps the email without the code.
        email.setArchiveRedactions(List.of(displayCode, displayCode.replace(" ", "")));
        return email;
    }
}
