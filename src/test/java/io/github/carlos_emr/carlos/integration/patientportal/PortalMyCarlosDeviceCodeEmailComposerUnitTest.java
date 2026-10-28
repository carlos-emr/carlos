/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The myCarlos device code email (clinic code v1, mycarlos#141): its words, language and archive. */
@Tag("unit")
class PortalMyCarlosDeviceCodeEmailComposerUnitTest {
    private static final List<Locale> LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"),
            Locale.of("pl"), Locale.of("pt", "BR"));

    @Test
    void shouldSayOnlyClinicCodeAndExpiry_inEnglish() {
        assertEquals("Your myCarlos code", PortalMyCarlosDeviceCodeEmailComposer.subject(Locale.ENGLISH));
        assertEquals("Your FAKE Clinic myCarlos code is 482 193 644. It expires in 10 minutes. Never enter it on a website.",
                PortalMyCarlosDeviceCodeEmailComposer.body(Locale.ENGLISH, "FAKE Clinic", "482 193 644", 10));
    }

    @Test
    void shouldFillEveryLanguage_withItsOwnText() {
        String english = PortalMyCarlosDeviceCodeEmailComposer.body(Locale.ENGLISH, "FAKE Clinic", "482 193 644", 7);
        for (Locale locale : LOCALES) {
            String body = PortalMyCarlosDeviceCodeEmailComposer.body(locale, "FAKE Clinic", "482 193 644", 7);
            assertTrue(body.contains("FAKE Clinic") && body.contains("482 193 644") && body.contains("7"), locale + ": " + body);
            assertFalse(body.contains("{") || body.contains("}"), locale + ": " + body);
            assertFalse(PortalMyCarlosDeviceCodeEmailComposer.subject(locale).isBlank());
            if (!locale.equals(Locale.ENGLISH)) assertNotEquals(english, body, locale.toString());
        }
        assertEquals("Votre code myCarlos de FAKE Clinic est 482 193 644. Il expire dans 7 minutes. "
                + "Ne l’entrez jamais sur un site Web.",
                PortalMyCarlosDeviceCodeEmailComposer.body(Locale.FRENCH, "FAKE Clinic", "482 193 644", 7));
    }

    @Test
    void shouldNeverSayZeroMinutes_forCodeAboutToExpire() {
        assertTrue(PortalMyCarlosDeviceCodeEmailComposer.body(Locale.ENGLISH, "FAKE Clinic", "482 193 644", 0)
                .contains("expires in 1 minutes"));
    }

    @Test
    void shouldRefuseCodeNotInGroupsOfThree_forBody() {
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceCodeEmailComposer.body(Locale.ENGLISH, "FAKE Clinic", "482193644", 10));
    }

    @Test
    void shouldPickPatientLanguage_fromSpokenThenOfficial() {
        assertEquals(Locale.FRENCH, PortalMyCarlosDeviceCodeEmailComposer.locale(null, "French"));
        assertEquals(Locale.of("es"), PortalMyCarlosDeviceCodeEmailComposer.locale("Spanish", "English"));
        assertEquals(Locale.of("pl"), PortalMyCarlosDeviceCodeEmailComposer.locale(" polish ", "French"));
        assertEquals(Locale.of("pt", "BR"), PortalMyCarlosDeviceCodeEmailComposer.locale("Portuguese", null));
        assertEquals(Locale.FRENCH, PortalMyCarlosDeviceCodeEmailComposer.locale("Klingon", "French"));
        assertEquals(Locale.ENGLISH, PortalMyCarlosDeviceCodeEmailComposer.locale("Klingon", "Other"));
        assertEquals(Locale.ENGLISH, PortalMyCarlosDeviceCodeEmailComposer.locale(null, null));
    }

    @Test
    void shouldKeepCodeOutOfArchive_andMarkEmailAsDeviceCode() {
        var email = PortalMyCarlosDeviceCodeEmailComposer.request(42, "fake.patient@example.invalid", 3, "-1",
                "Your myCarlos code", "Your FAKE Clinic myCarlos code is 482 193 644.", "482 193 644");
        assertEquals(TransactionType.MYCARLOS_CODE, email.getTransactionType());
        assertEquals(List.of("482 193 644"), email.getArchiveRedactions());
        assertArrayEquals(new String[] {"fake.patient@example.invalid"}, email.getRecipients());
        assertEquals(42, email.getDemographicNo());
        assertFalse(email.getIsEncrypted());
        assertTrue(email.getAttachments().isEmpty());
    }
}
