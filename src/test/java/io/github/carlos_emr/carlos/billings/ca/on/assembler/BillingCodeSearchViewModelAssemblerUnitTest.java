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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.IchppccodeDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingCodeSearchViewModelAssembler} search-result shaping. */
@DisplayName("BillingCodeSearchViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingCodeSearchViewModelAssemblerUnitTest {

    @Test
    void shouldBuildServiceDescriptionPatterns_fromRawInputs() {
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
    void shouldBuildResearchDescriptionPatterns_fromRawInputs() {
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

    @ParameterizedTest
    @ValueSource(strings = {
        "document.forms[1].elements['servicecode0'].value",
        "document.forms[1].elements['servicecode11'].value",
        "document.forms[0].elements['serviceCode0'].value",
        "document.forms[0].elements['serviceCode11'].value",
        "document.serviceform.xml_other1.value"
    })
    void shouldPreserveSupportedAttachmentTarget(String target) {
        BillingServiceDao dao = mock(BillingServiceDao.class);
        when(dao.search_service_code(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(List.of());
        var assembler = new BillingCodeSearchViewModelAssembler(dao, mock(IchppccodeDao.class));
        var model = assembler.assembleService("A00", "", "", target);
        assertThat(model.getNameFSafe()).isEqualTo(target);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "document.forms[1].elements['servicecode0'].value;alert(1)",
        "document.forms[1].elements['servicecode0\\'];alert(1)//'].value",
        "document.forms[1].elements['servicecode0'].innerHTML",
        "document.forms[2].elements['servicecode0'].value",
        "document.forms[1].elements['demographic_no'].value",
        "document.forms[1].elements['servicecode0'].value\n",
        "document.forms[alert(1)].elements['servicecode0'].value",
        "<script>alert(1)</script>"
    })
    void shouldRejectExecutableOrUnsupportedAttachmentTarget(String target) {
        BillingServiceDao dao = mock(BillingServiceDao.class);
        when(dao.search_service_code(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(List.of());
        var assembler = new BillingCodeSearchViewModelAssembler(dao, mock(IchppccodeDao.class));
        var model = assembler.assembleService("A00", "", "", target);
        assertThat(model.getNameFSafe()).isEmpty();
    }
}
