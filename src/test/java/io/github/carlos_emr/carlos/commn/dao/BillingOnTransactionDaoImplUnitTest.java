/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests pinning the strict-parse contracts on
 * {@link BillingOnTransactionDaoImpl#getUpdateCheader1TransTemplate}. These
 * three throw sites convert silent {@code -1} sentinel writes (which would
 * have produced orphan audit rows / FK violations under the legacy code)
 * into a typed {@link BillingValidationException} so the surrounding
 * transaction rolls back instead of persisting corruption.
 *
 * @since 2026-04-30
 */
@DisplayName("BillingOnTransactionDaoImpl strict-parse")
@Tag("unit")
@Tag("billing")
class BillingOnTransactionDaoImplUnitTest {

    private BillingOnTransactionDaoImpl dao;

    @BeforeEach
    void setUp() {
        dao = new BillingOnTransactionDaoImpl();
    }

    private BillingClaimHeaderDto wellFormedDto() {
        BillingClaimHeaderDto dto = new BillingClaimHeaderDto();
        dto.setId("42");
        dto.setDemographic_no("1234");
        dto.setBilling_date("2026-04-25");
        dto.setAdmission_date("2026-04-25");
        return dto;
    }

    @Test
    void shouldReturnTransactionTemplate_whenAllFieldsWellFormed() {
        BillingClaimHeaderDto dto = wellFormedDto();

        // No throw expected; happy path proves the negative tests below
        // really do isolate the strict-parse branches.
        assertThat(dao.getUpdateCheader1TransTemplate(dto, "999")).isNotNull();
    }

    @Test
    void shouldThrowBillingValidationException_whenBillingDateMalformed() {
        BillingClaimHeaderDto dto = wellFormedDto();
        dto.setBilling_date("not-a-date");

        // billing_date is required for OHIP submission — silent null would
        // produce a useless audit row.
        assertThatThrownBy(() -> dao.getUpdateCheader1TransTemplate(dto, "999"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("billing_date");
    }

    @Test
    void shouldThrowBillingValidationException_whenIdNonNumeric() {
        BillingClaimHeaderDto dto = wellFormedDto();
        dto.setId("not-a-number");

        // ch1 id is the FK back to BillingONCHeader1 — persisting -1 would
        // create an orphan audit row.
        assertThatThrownBy(() -> dao.getUpdateCheader1TransTemplate(dto, "999"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("ch1 id");
    }

    @Test
    void shouldThrowBillingValidationException_whenDemographicNoNonNumeric() {
        BillingClaimHeaderDto dto = wellFormedDto();
        dto.setDemographic_no("not-a-number");

        // demographic_no FK must point at a real patient — -1 sentinel
        // would mask a tampered form post.
        assertThatThrownBy(() -> dao.getUpdateCheader1TransTemplate(dto, "999"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("demographic_no");
    }

    @Test
    void shouldStoreNullAdmissionDate_whenMalformed_butNotThrow() {
        BillingClaimHeaderDto dto = wellFormedDto();
        dto.setAdmission_date("not-a-date");

        // admission_date is nullable in the source row — malformed input
        // logs an error but stores null on the audit row rather than
        // aborting transaction-row creation.
        var template = dao.getUpdateCheader1TransTemplate(dto, "999");
        assertThat(template).isNotNull();
        assertThat(template.getAdmissionDate()).isNull();
    }

    @Test
    void shouldStoreNullAdmissionDate_whenAbsent() {
        BillingClaimHeaderDto dto = wellFormedDto();
        dto.setAdmission_date(null);

        var template = dao.getUpdateCheader1TransTemplate(dto, "999");
        assertThat(template.getAdmissionDate()).isNull();
    }
}
