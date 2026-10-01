/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eForm group mutators redirect (never forward) to the groups page, issue #4130.
 *
 * @since 2026-10-01
 */
@DisplayName("EFormGroupRedirect")
@Tag("unit")
@Tag("eform")
class EFormGroupRedirectUnitTest {

    @Test
    @DisplayName("should reopen the named group")
    void shouldReturnGroupView_forPlainGroupName() {
        assertThat(EFormGroupRedirect.toGroup("Intake")).isEqualTo("/eform/efmmanageformgroups?group_view=Intake");
    }

    @Test
    @DisplayName("should URL-encode the group so it cannot change the path, add parameters or inject an expression")
    void shouldEncodeGroupName_withReservedCharacters() {
        assertThat(EFormGroupRedirect.toGroup("a&b=c/../${x}"))
                .isEqualTo("/eform/efmmanageformgroups?group_view=a%26b%3Dc%2F..%2F%24%7Bx%7D");
    }

    @Test
    @DisplayName("should land on the group list when there is no group")
    void shouldReturnGroupList_forNullOrEmptyName() {
        assertThat(EFormGroupRedirect.toGroup(null)).isEqualTo("/eform/efmmanageformgroups");
        assertThat(EFormGroupRedirect.toGroup("")).isEqualTo("/eform/efmmanageformgroups");
    }
}
