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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
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
        dto = dto.withId("42");
        dto = dto.withDemographicNo("1234");
        dto = dto.withBillingDate("2026-04-25");
        dto = dto.withAdmissionDate("2026-04-25");
        return dto;
    }

    @Test
    void shouldReturnTransactionTemplate_whenAllFieldsWellFormed() {
        BillingClaimHeaderDto dto = wellFormedDto();

        // Pin field-mapping invariants: a future refactor that silently
        // swaps two assignments would still pass the throw-site tests
        // below, so the happy-path test has to verify the actual write
        // shape rather than just non-null.
        var template = dao.getUpdateCheader1TransTemplate(dto, "999");
        assertThat(template).isNotNull();
        assertThat(template.getCh1Id()).isEqualTo(42);
        assertThat(template.getDemographicNo()).isEqualTo(1234);
        assertThat(template.getActionType()).isEqualTo("UH");
        assertThat(template.getBillingDate()).isNotNull();
        assertThat(template.getAdmissionDate()).isNotNull();
        assertThat(template.getUpdateProviderNo()).isEqualTo("999");
    }

    @Test
    void shouldThrowBillingValidationException_whenBillingDateMalformed() {
        BillingClaimHeaderDto dto = wellFormedDto().withBillingDate("not-a-date");

        // billing_date is required for OHIP submission — silent null would
        // produce a useless audit row.
        assertThatThrownBy(() -> dao.getUpdateCheader1TransTemplate(dto, "999"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("billing_date");
    }

    @Test
    void shouldThrowBillingValidationException_whenIdNonNumeric() {
        BillingClaimHeaderDto dto = wellFormedDto().withId("not-a-number");

        // ch1 id is the FK back to BillingONCHeader1 — persisting -1 would
        // create an orphan audit row.
        assertThatThrownBy(() -> dao.getUpdateCheader1TransTemplate(dto, "999"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("ch1 id");
    }

    @Test
    void shouldThrowBillingValidationException_whenDemographicNoNonNumeric() {
        BillingClaimHeaderDto dto = wellFormedDto().withDemographicNo("not-a-number");

        // demographic_no FK must point at a real patient — -1 sentinel
        // would mask a tampered form post.
        assertThatThrownBy(() -> dao.getUpdateCheader1TransTemplate(dto, "999"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("demographic_no");
    }

    @Test
    void shouldStoreNullAdmissionDate_whenMalformed_butNotThrow() {
        BillingClaimHeaderDto dto = wellFormedDto();
        dto = dto.withAdmissionDate("not-a-date");

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
        dto = dto.withAdmissionDate(null);

        var template = dao.getUpdateCheader1TransTemplate(dto, "999");
        assertThat(template.getAdmissionDate()).isNull();
    }

    @Test
    void shouldThrowBillingValidationException_whenEntityHeaderIdIsNull() {
        // S7: the entity-variant getTransTemplate must reject a transient
        // BillingONCHeader1 (id == null) so it can't NPE on the unboxing
        // assignment to a primitive int and persist a meaningless audit row.
        io.github.carlos_emr.carlos.commn.model.BillingONCHeader1 cheader1 =
                new io.github.carlos_emr.carlos.commn.model.BillingONCHeader1();
        // No id set — entity is transient.
        io.github.carlos_emr.carlos.commn.model.BillingONItem billItem =
                new io.github.carlos_emr.carlos.commn.model.BillingONItem();
        io.github.carlos_emr.carlos.commn.model.BillingONPayment billPayment =
                new io.github.carlos_emr.carlos.commn.model.BillingONPayment();

        assertThatThrownBy(() -> dao.getTransTemplate(cheader1, billItem, billPayment, "999", 1))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("transient header");
    }
}
