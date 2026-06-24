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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Frm2Action redirect construction")
@Tag("unit")
@Tag("security")
class Frm2ActionRedirectUnitTest {

    @Test
    @DisplayName("should encode form_link before redirecting to forwardname")
    void shouldEncodeFormLink_whenBuildingForwardNameRedirect() {
        String redirect = Frm2Action.forwardNameRedirectUrl(
                "/carlos",
                "formannual.jsp&demographic_no=999",
                "save?demographic_no=123&formId=456");

        assertThat(redirect).isEqualTo(
                "/carlos/form/forwardname?form_link=formannual.jsp%26demographic_no%3D999"
                        + "&demographic_no=123&formId=456");
    }

    @Test
    @DisplayName("should reject non-save forward actions")
    void shouldRejectForwardAction_whenNonSave() {
        assertThatThrownBy(() -> Frm2Action.forwardNameRedirectUrl(
                "/carlos",
                "formannual.jsp",
                "https://evil.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject unsafe forwardname redirect shape")
    void shouldRejectRedirect_whenForwardnameShapeUnsafe() {
        assertThatThrownBy(() -> Frm2Action.forwardNameRedirectUrl(
                "//evil.example",
                "formannual.jsp",
                "save?demographic_no=123"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Frm2Action.forwardNameRedirectUrl(
                "/carlos",
                "formannual.jsp",
                "save?next=%0d%0aLocation:%20https://evil.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
