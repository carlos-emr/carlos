package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeUpdateViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("BillingCodeUpdateViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingCodeUpdateViewModelAssemblerUnitTest {

    @Test
    void shouldOnlySelectParametersWithCodePrefix() {
        BillingServiceDao dao = mock(BillingServiceDao.class);
        BillingCodeUpdateViewModelAssembler assembler = new BillingCodeUpdateViewModelAssembler(dao);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("update", "Confirm");
        request.setParameter("decode_A001A", "on");
        request.setParameter("code_B002B", "on");

        BillingCodeUpdateViewModel model = assembler.assemble(request, null);

        assertThat(model.getMode()).isEqualTo(BillingCodeUpdateViewModel.Mode.CONFIRM_SELECTION);
        assertThat(model.getSelected0()).isEqualTo("B002B");
        assertThat(model.getSelected1()).isEmpty();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldNotPersistNullDescription() {
        BillingServiceDao dao = mock(BillingServiceDao.class);
        BillingCodeUpdateViewModelAssembler assembler = new BillingCodeUpdateViewModelAssembler(dao);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("update", "update A001A");

        BillingCodeUpdateViewModel model = assembler.assemble(request, null);

        assertThat(model.getMode()).isEqualTo(BillingCodeUpdateViewModel.Mode.UPDATE_DESCRIPTION);
        verifyNoInteractions(dao);
    }
}
