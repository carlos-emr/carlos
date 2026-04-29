package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.IchppccodeDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingCodeSearchViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingCodeSearchViewModelAssemblerUnitTest {

    @Test
    void shouldBuildServiceDescriptionPatternsFromRawInputs() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        IchppccodeDao ichppccodeDao = mock(IchppccodeDao.class);
        when(billingServiceDao.search_service_code(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(List.of());
        BillingCodeSearchViewModelAssembler assembler =
                new BillingCodeSearchViewModelAssembler(billingServiceDao, ichppccodeDao);

        assembler.assembleService("A00", "B11", "C22");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> code1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> code2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> desc1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> desc2 = ArgumentCaptor.forClass(String.class);
        verify(billingServiceDao).search_service_code(
                code.capture(), code1.capture(), code2.capture(),
                desc.capture(), desc1.capture(), desc2.capture());

        assertThat(code.getValue()).isEqualTo("A00%");
        assertThat(desc.getValue()).isEqualTo("%A00%");
        assertThat(code1.getValue()).isEqualTo("B11%");
        assertThat(desc1.getValue()).isEqualTo("%B11%");
        assertThat(code2.getValue()).isEqualTo("C22%");
        assertThat(desc2.getValue()).isEqualTo("%C22%");
    }

    @Test
    void shouldBuildResearchDescriptionPatternsFromRawInputs() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        IchppccodeDao ichppccodeDao = mock(IchppccodeDao.class);
        when(ichppccodeDao.search_research_code(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(List.of());
        BillingCodeSearchViewModelAssembler assembler =
                new BillingCodeSearchViewModelAssembler(billingServiceDao, ichppccodeDao);

        assembler.assembleResearch("A00", "B11", "C22");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> code1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> code2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> desc1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> desc2 = ArgumentCaptor.forClass(String.class);
        verify(ichppccodeDao).search_research_code(
                code.capture(), code1.capture(), code2.capture(),
                desc.capture(), desc1.capture(), desc2.capture());

        assertThat(code.getValue()).isEqualTo("A00%");
        assertThat(desc.getValue()).isEqualTo("A00%");
        assertThat(code1.getValue()).isEqualTo("B11%");
        assertThat(desc1.getValue()).isEqualTo("B11%");
        assertThat(code2.getValue()).isEqualTo("C22%");
        assertThat(desc2.getValue()).isEqualTo("C22%");
    }
}
