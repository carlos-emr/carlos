/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.OtnEconsult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The OTN eConsult import stores the caller's description HTML-encoded in document.docdesc, a
 * utf8mb4 VARCHAR(255) that the packaged database cut at 255 characters without a word, possibly
 * in the middle of an entity ("&am"). An unmodified API client keeps working: its description is
 * shortened before encoding to what fits, never leaving part of an entity or a surrogate pair.
 */
@Tag("unit")
@DisplayName("OtnEconsultConverter description")
class OtnEconsultConverterUnitTest {

    private static final int MAX = Document.DESCRIPTION_MAX_LENGTH;

    private static Document convert(String description) throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        OtnEconsult econsult = new OtnEconsult();
        econsult.setDocDescription(description);
        econsult.setFileName("econsult.pdf");
        econsult.setContentType("application/pdf");
        econsult.setContents(new byte[] {1});
        econsult.setImportDate(new Date());
        return new OtnEconsultConverter().getAsDomainObject(loggedInInfo, econsult);
    }

    @Test
    void shouldStoreWholeEntities_whenTheEncodedDescriptionIsLongerThanTheColumn() throws Exception {
        // "x&" encodes to six characters: 42 pairs (252) fit, and the 43rd "x" makes 253 before an
        // "&amp;" that would no longer fit.
        String stored = convert("x&".repeat(100)).getDocdesc();

        assertThat(stored).isEqualTo("x&amp;".repeat(42) + "x");
        assertThat(stored.codePointCount(0, stored.length())).isLessThanOrEqualTo(MAX);
    }

    @Test
    void shouldShortenByCodePoints_whenTheDescriptionHasEmoji() throws Exception {
        String emoji = "😀";

        String stored = convert(emoji.repeat(300)).getDocdesc();

        assertThat(stored).isEqualTo(emoji.repeat(MAX));
    }

    @Test
    void shouldStoreTheEncodedDescriptionUnchanged_whenItFits() throws Exception {
        assertThat(convert("Cardiology reply & plan").getDocdesc()).isEqualTo("Cardiology reply &amp; plan");
        assertThat(convert(null).getDocdesc()).isEqualTo("OTN eConsult Consultation");
    }
}
