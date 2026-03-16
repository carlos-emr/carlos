/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 PdfCoverPageCreatorTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.fax.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;

/**
 * Unit tests for {@link PdfCoverPageCreator}.
 *
 * <p>Tests PDF cover page generation for fax transmissions.
 * Migrated from legacy JUnit 4 PdfCoverPageCreatorTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("PdfCoverPageCreator unit tests")
class PdfCoverPageCreatorUnitTest {

    @Test
    @DisplayName("should create non-empty PDF cover page byte array")
    void shouldCreateNonEmptyPdfCoverPage() {
        FaxRecipient recipient = new FaxRecipient();
        recipient.setName("Test Recipient Clinic");
        recipient.setFax("778-998-0876");

        FaxAccount account = new FaxAccount();
        account.setName("The MOA Name");
        account.setPhone("604-555-1212");
        account.setFax("604-234-2345");
        account.setSubText("Primary Care Network (PCN) ");
        account.setFaxNumberOwner("MOA Name Here");
        account.setLetterheadName("Dr. So Andso");

        String coverNote = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "Integer laoreet nisi ante, vel dictum lacus egestas nec.";

        PdfCoverPageCreator creator = new PdfCoverPageCreator(coverNote, 5, recipient, account);
        byte[] result = creator.createCoverPage();

        assertThat(result).isNotNull().isNotEmpty();
    }
}
