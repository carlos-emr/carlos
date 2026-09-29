/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
class EFormRenderCapacityResponseUnitTest {
    @Test
    void savedFormContinuationsCarryNoClinicalSaveAndHaveExplicitBusyHeaders() {
        for (var operation : new EFormRenderApprovalService.Operation[] {
                EFormRenderApprovalService.Operation.DOWNLOAD, EFormRenderApprovalService.Operation.EDOC}) {
            var request = new MockHttpServletRequest(); request.setContextPath("/carlos");
            var response = new MockHttpServletResponse();
            var fields = Map.of("fdid", "42", "demographicNo", "123", "renderApproval", "one-use", "autoClose", "true");
            assertThat(EFormRenderCapacityResponse.offer(request, response, operation, fields)).isEqualTo("renderBusy");
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Retry-After")).isEqualTo("2");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(request.getAttribute("renderCapacityAction")).isEqualTo(operation == EFormRenderApprovalService.Operation.DOWNLOAD
                    ? "/carlos/eform/downloadEFormPdf" : "/carlos/eform/saveEFormAsEDoc");
            assertThat(request.getAttribute("renderCapacityFields")).isEqualTo(fields);
        }
    }
    @Test
    void neverOffersFaxQueueOrClinicalFormReplay() {
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> EFormRenderCapacityResponse.offer(request, response, EFormRenderApprovalService.Operation.FAX,
                Map.of("method", "queue", "transactionType", "EFORM", "transactionId", "42", "demographicNo", "123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EFormRenderCapacityResponse.offer(request, response, EFormRenderApprovalService.Operation.DOWNLOAD,
                Map.of("fdid", "42", "demographicNo", "123", "clinicalFormField", "must not replay")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("renderCapacityFields")).isNull();
    }
    @Test
    void requiresRealPositiveSavedFormAndPatientIds() {
        for (String invalid : new String[] {"", "0", "-1", "2147483648", "1&method=queue"}) {
            assertThatThrownBy(() -> EFormRenderCapacityResponse.offer(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    EFormRenderApprovalService.Operation.DOWNLOAD, Map.of("fdid", invalid, "demographicNo", "123")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
