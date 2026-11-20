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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.ConsentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class SmsConsentTypeResolverUnitTest {
    private static final String SMS_CONSENT_TYPE = "sms_communication_consent";

    @Mock
    private UserPropertyDAO userPropertyDao;

    @Mock
    private ConsentTypeDao consentTypeDao;

    @Test
    @DisplayName("resolve returns empty when the sms_communication property is missing")
    void shouldReturnEmpty_whenPropertyIsMissing() {
        when(userPropertyDao.getProp(UserProperty.SMS_COMMUNICATION)).thenReturn(null);

        assertThat(resolver().resolve()).isEmpty();
        verifyNoInteractions(consentTypeDao);
    }

    @Test
    @DisplayName("resolve returns empty when the sms_communication property value is blank")
    void shouldReturnEmpty_whenPropertyValueIsBlank() {
        when(userPropertyDao.getProp(UserProperty.SMS_COMMUNICATION)).thenReturn(property("   "));

        assertThat(resolver().resolve()).isEmpty();
        verifyNoInteractions(consentTypeDao);
    }

    @Test
    @DisplayName("resolve returns empty when the configured consent type does not exist")
    void shouldReturnEmpty_whenConsentTypeIsNotFound() {
        when(userPropertyDao.getProp(UserProperty.SMS_COMMUNICATION)).thenReturn(property(SMS_CONSENT_TYPE));
        when(consentTypeDao.findConsentType(SMS_CONSENT_TYPE)).thenReturn(null);

        assertThat(resolver().resolve()).isEmpty();
    }

    @Test
    @DisplayName("resolve returns empty when the configured consent type is inactive")
    void shouldReturnEmpty_whenConsentTypeIsInactive() {
        when(userPropertyDao.getProp(UserProperty.SMS_COMMUNICATION)).thenReturn(property(SMS_CONSENT_TYPE));
        when(consentTypeDao.findConsentType(SMS_CONSENT_TYPE)).thenReturn(consentType(false));

        assertThat(resolver().resolve()).isEmpty();
    }

    @Test
    @DisplayName("resolve returns the active consent type named by the trimmed property value")
    void shouldReturnConsentType_whenPropertyNamesActiveType() {
        ConsentType active = consentType(true);
        when(userPropertyDao.getProp(UserProperty.SMS_COMMUNICATION))
                .thenReturn(property("  " + SMS_CONSENT_TYPE + " "));
        when(consentTypeDao.findConsentType(SMS_CONSENT_TYPE)).thenReturn(active);

        assertThat(resolver().resolve()).containsSame(active);
    }

    private SmsConsentTypeResolver resolver() {
        return new SmsConsentTypeResolver(userPropertyDao, consentTypeDao);
    }

    private static UserProperty property(String value) {
        UserProperty property = new UserProperty();
        property.setName(UserProperty.SMS_COMMUNICATION);
        property.setValue(value);
        return property;
    }

    private static ConsentType consentType(boolean active) {
        ConsentType consentType = new ConsentType();
        consentType.setId(7);
        consentType.setType(SMS_CONSENT_TYPE);
        consentType.setActive(active);
        return consentType;
    }
}
