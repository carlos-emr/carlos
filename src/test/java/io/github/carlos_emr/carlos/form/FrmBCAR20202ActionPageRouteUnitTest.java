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
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.form.gate.FormViewRoutes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * After a save, BC-AR 2020 redirected to {@code /form/formBCAR2020pg<N>.jsp}, which no longer exists: the pages
 * live under WEB-INF and are served at extensionless routes (issue #4919). The save now goes to the same route
 * the form gate resolves the legacy page address to.
 *
 * @since 2026-10-18
 */
@DisplayName("BC-AR 2020 page routes")
@Tag("unit")
@Tag("form")
class FrmBCAR20202ActionPageRouteUnitTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void shouldRouteANumberedPage_toItsExtensionlessView(int pageNo) {
        assertThat(FrmBCAR20202Action.pageRoute(pageNo))
                .isEqualTo("/form/formBCAR2020pg" + pageNo)
                .isEqualTo(FormViewRoutes.resolveActionPath("/form/formBCAR2020pg" + pageNo + ".jsp"));
    }

    @ParameterizedTest
    @ValueSource(ints = {6})
    void shouldRouteAttachments_toItsExtensionlessView(int pageNo) {
        assertThat(FrmBCAR20202Action.pageRoute(pageNo))
                .isEqualTo("/form/formBCAR2020Attachments")
                .isEqualTo(FormViewRoutes.resolveActionPath("/form/formBCAR2020Attachments.jsp"));
    }
}
