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
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the consent type that outbound SMS is checked against.
 * <p>
 * The {@link UserProperty#SMS_COMMUNICATION} property names the consent type, mirroring how
 * {@code email_communication} configures email consent. An unset property, or one naming a missing or
 * inactive consent type, resolves to empty so callers block SMS instead of guessing a consent type.
 *
 * @since 2026-09-18
 */
@Service
public class SmsConsentTypeResolver {
    private final UserPropertyDAO userPropertyDao;
    private final ConsentTypeDao consentTypeDao;

    public SmsConsentTypeResolver(UserPropertyDAO userPropertyDao, ConsentTypeDao consentTypeDao) {
        this.userPropertyDao = userPropertyDao;
        this.consentTypeDao = consentTypeDao;
    }

    /**
     * @return the active consent type configured for SMS, or empty when SMS consent is not configured
     */
    public Optional<ConsentType> resolve() {
        UserProperty property = userPropertyDao.getProp(UserProperty.SMS_COMMUNICATION);
        if (property == null || property.getValue() == null || property.getValue().isBlank()) {
            return Optional.empty();
        }
        ConsentType consentType = consentTypeDao.findConsentType(property.getValue().trim());
        if (consentType == null || !consentType.isActive()) {
            return Optional.empty();
        }
        return Optional.of(consentType);
    }
}
