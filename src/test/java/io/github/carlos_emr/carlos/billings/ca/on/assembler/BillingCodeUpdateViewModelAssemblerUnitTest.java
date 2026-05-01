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
