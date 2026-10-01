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
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Removing an eForm from a group must end on a GET of the group page, not on a forward of
 * the POST: the groups page gate refuses POST, so a successful removal used to land the
 * operator on "CARLOS Error: 405" (issue #4130). struts-eform.xml redirects to
 * {@link RemoveFromGroup2Action#getRedirectTarget()}.
 *
 * @since 2026-10-01
 */
@DisplayName("RemoveFromGroup2Action")
@Tag("integration")
@Tag("eform")
@Tag("delete")
class RemoveFromGroup2ActionTest extends CarlosWebTestBase {

    private RemoveFromGroup2Action action;

    @BeforeEach
    void setUp() {
        mockRequest.setMethod("POST");
        mockRequest.setParameter("fid", "7");
        action = new RemoveFromGroup2Action();
    }

    @Test
    @DisplayName("should remove the form from the group when the provider may write eForms")
    void shouldRemoveFormFromGroup_whenProviderHasEFormWritePrivilege() {
        allowPrivilege("_eform", "w");
        mockRequest.setParameter("groupName", "Intake");

        try (MockedStatic<EFormUtil> eformUtil = mockStatic(EFormUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            eformUtil.verify(() -> EFormUtil.remEFormFromGroup("Intake", "7"));
        }
    }

    @Test
    @DisplayName("should redirect to the group the form was removed from, URL-encoded")
    void shouldRedirectToEncodedGroup_whenGroupNameHasReservedCharacters() {
        mockRequest.setParameter("groupName", "Intake & ${x}?a=b");

        assertThat(action.getRedirectTarget())
                .isEqualTo("/eform/efmmanageformgroups?group_view=Intake+%26+%24%7Bx%7D%3Fa%3Db");
    }

    @Test
    @DisplayName("should redirect to the group list when no group name was posted")
    void shouldRedirectToGroupList_whenGroupNameIsMissing() {
        assertThat(action.getRedirectTarget()).isEqualTo("/eform/efmmanageformgroups");
    }
}
