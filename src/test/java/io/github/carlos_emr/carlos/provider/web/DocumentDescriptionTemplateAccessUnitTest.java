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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The document description template rules shared by the page gate, the page and the endpoint
 * (issue #5057).
 */
@Tag("unit")
@Tag("security")
@DisplayName("DocumentDescriptionTemplateAccess")
class DocumentDescriptionTemplateAccessUnitTest {

    private SecurityInfoManager security;
    private LoggedInInfo user;

    @BeforeEach
    void setUp() {
        security = mock(SecurityInfoManager.class);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("100101");
    }

    private void grant(String object, String right) {
        when(security.hasPrivilege(eq(user), eq(object), eq(right), isNull())).thenReturn(true);
    }

    @ParameterizedTest(name = "{0} {1} -> read templates {2}")
    @CsvSource({
            "_pref,w,true",
            "_pref,r,false",
            "_edoc,r,true",
            "_admin,r,true",
            "_admin.document,r,true",
            "_demographic,r,false",
    })
    @DisplayName("should let each page's own right read templates, and nothing else")
    void shouldGrantTemplateReads_forEachPageRight(String object, String right, boolean expected) {
        grant(object, right);

        assertThat(DocumentDescriptionTemplateAccess.canReadTemplates(security, user)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should refuse everything to a missing session without asking for privileges")
    void shouldRefuseEverything_withoutSession() {
        assertThat(DocumentDescriptionTemplateAccess.canReadTemplates(security, null)).isFalse();
        assertThatThrownBy(() -> DocumentDescriptionTemplateAccess.requireWrite(security, null, "100101"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> DocumentDescriptionTemplateAccess.requireWrite(security, null, null))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(security);
    }

    @Test
    @DisplayName("should keep a provider's own entries to that provider, even for an administrator")
    void shouldKeepOwnEntries_toTheirOwner() {
        grant("_admin", "r");
        grant("_admin", "w");
        grant("_pref", "w");

        assertThatCode(() -> DocumentDescriptionTemplateAccess.requireRead(security, user, "100101"))
                .doesNotThrowAnyException();
        assertThatCode(() -> DocumentDescriptionTemplateAccess.requireWrite(security, user, "100101"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DocumentDescriptionTemplateAccess.requireRead(security, user, "200202"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> DocumentDescriptionTemplateAccess.requireWrite(security, user, "200202"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should let any template reader read the clinic-wide list but only its writers change it")
    void shouldSeparateClinicReads_fromClinicWrites() {
        grant("_edoc", "r");
        grant("_admin.document", "r");

        assertThatCode(() -> DocumentDescriptionTemplateAccess.requireRead(security, user, null))
                .doesNotThrowAnyException();
        assertThat(DocumentDescriptionTemplateAccess.canReadClinicList(security, user)).isTrue();
        assertThat(DocumentDescriptionTemplateAccess.canEditClinicList(security, user)).isFalse();
        assertThatThrownBy(() -> DocumentDescriptionTemplateAccess.requireWrite(security, user, null))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin | _admin.document)");
    }
}
