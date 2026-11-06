/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.billing.web;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("billing")
class BillingFormServiceCodeInputUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldReadAllGroupsAndBoundaryRows_whenCodesArePadded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("group1_service0", " A007A ");
        request.setParameter("group2", "Middle group");
        request.setParameter("group2_service10", "\tK005A\n");
        request.setParameter("group2_service10_order", "invalid");
        request.setParameter("group3_service19", " A001A ");

        var inputs = BillingFormServiceCodeInput.read(request);

        assertThat(inputs).extracting(BillingFormServiceCodeInput::parameterName)
                .containsExactly("group1_service0", "group2_service10", "group3_service19");
        assertThat(inputs).extracting(BillingFormServiceCodeInput::serviceCode)
                .containsExactly("A007A", "K005A", "A001A");
        assertThat(inputs.get(0).groupName()).isEmpty();
        assertThat(inputs.get(0).order()).isNull();
        assertThat(inputs.get(1).order()).isEqualTo("invalid");
        var row = inputs.get(1).toRow("42", "Family Medicine", 7);
        assertThat(row.getServiceCode()).isEqualTo("K005A");
        assertThat(row.getServiceType()).isEqualTo("42");
        assertThat(row.getServiceTypeName()).isEqualTo("Family Medicine");
        assertThat(row.getServiceGroup()).isEqualTo("Group2");
        assertThat(row.getServiceGroupName()).isEqualTo("Middle group");
        assertThat(row.getServiceOrder()).isEqualTo(7);
        assertThat(row.getStatus()).isEqualTo("A");
    }

    @Test
    void shouldOmitBlankRows_whenTheirOrdersAreInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("group1_service0", " \t\r\n ");
        request.setParameter("group1_service0_order", "invalid");
        request.setParameter("group2_service10", "");
        request.setParameter("group2_service10_order", "invalid");
        request.setParameter("group3_service19_order", "invalid");

        assertThat(BillingFormServiceCodeInput.read(request)).isEmpty();
    }
}
