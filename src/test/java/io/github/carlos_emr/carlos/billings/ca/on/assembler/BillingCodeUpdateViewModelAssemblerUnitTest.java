package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeUpdateViewModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingCodeUpdateViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingCodeUpdateViewModelAssemblerUnitTest {

    @Test
    void shouldOnlySelectParametersWithCodePrefix() {
        BillingCodeUpdateViewModelAssembler assembler = new BillingCodeUpdateViewModelAssembler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("update", "Confirm");
        request.setParameter("decode_A001A", "on");
        request.setParameter("code_B002B", "on");

        BillingCodeUpdateViewModel model = assembler.assemble(request, null);

        assertThat(model.getMode()).isEqualTo(BillingCodeUpdateViewModel.Mode.CONFIRM_SELECTION);
        assertThat(model.getSelected0()).isEqualTo("B002B");
        assertThat(model.getSelected1()).isEmpty();
    }

    @Test
    void shouldReturnUpdateMode_whenUpdateParamPresent() {
        BillingCodeUpdateViewModelAssembler assembler = new BillingCodeUpdateViewModelAssembler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("update", "update A001A");

        BillingCodeUpdateViewModel model = assembler.assemble(request, null);

        assertThat(model.getMode()).isEqualTo(BillingCodeUpdateViewModel.Mode.UPDATE_DESCRIPTION);
    }
}
