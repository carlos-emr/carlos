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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingShortcutPg2ViewModel.PostSaveAction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the hospital-billing Confirmation window goes after a save (#5055).
 *
 * <p>The Confirmation page posts its buttons as {@code button} and echoes page 1's
 * {@code submit=Next} back as a hidden field, as the browser sends it.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("billing")
@DisplayName("Hospital billing Confirmation: after a save")
class BillingShortcutPg2ServicePostSaveActionUnitTest {

    private static MockHttpServletRequest confirmation(String button) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/billing/CA/ON/BillingShortcutPg2Save");
        request.addParameter("submit", "Next");
        request.addParameter("addition", "Confirm");
        if (button != null) {
            request.addParameter("button", button);
        }
        return request;
    }

    @Test
    void shouldCloseTheWindow_whenSaveIsPressed() {
        assertThat(BillingShortcutPg2Service.postSaveAction(confirmation("Save"))).isEqualTo(PostSaveAction.CLOSE_WINDOW);
    }

    @Test
    void shouldReopenPageOne_whenSaveAndBackIsPressed() {
        assertThat(BillingShortcutPg2Service.postSaveAction(confirmation("Save and Back")))
                .isEqualTo(PostSaveAction.REDIRECT_TO_PG1);
    }

    @Test
    void shouldStillDecideFromSubmit_whenThePostNamesNoButton() {
        MockHttpServletRequest legacySave = new MockHttpServletRequest("POST", "/billing/CA/ON/BillingShortcutPg2Save");
        legacySave.addParameter("submit", "Save");

        assertThat(BillingShortcutPg2Service.postSaveAction(legacySave)).isEqualTo(PostSaveAction.CLOSE_WINDOW);
        assertThat(BillingShortcutPg2Service.postSaveAction(confirmation(null))).isEqualTo(PostSaveAction.REDIRECT_TO_PG1);
    }
}
