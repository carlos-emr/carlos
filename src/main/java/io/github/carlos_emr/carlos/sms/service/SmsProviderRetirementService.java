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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDao;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRetirementDao;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Locale;
import java.util.Optional;

/**
 * Serializes selection changes and queue ownership across servers. The existing STUB limiter key is
 * atomically materialized and locked before config, retirement preferences, or message rows. Merely
 * locking the missing config key allows compatible gap locks and is not a first-save mutex. This does
 * not acquire a permit, and no external send or nested limiter transaction runs while this lock is held.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SmsProviderRetirementService {
    private final SmsProviderRateLimitDao rateLimitDao;
    private final SmsConfigDao configDao;
    private final SmsProviderRetirementDao retirementDao;

    public SmsProviderRetirementService(SmsProviderRateLimitDao rateLimitDao, SmsConfigDao configDao,
                                        SmsProviderRetirementDao retirementDao) {
        this.rateLimitDao = rateLimitDao;
        this.configDao = configDao;
        this.retirementDao = retirementDao;
    }

    public Optional<SmsConfig> lockSelection() {
        rateLimitDao.ensureExists(SmsProviderType.STUB, new Date());
        if (rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.STUB).isEmpty()) {
            throw new IllegalStateException("SMS provider selection could not be locked; sending is blocked.");
        }
        return configDao.findCurrentForUpdate();
    }

    public void recordSelection(SmsProviderType previous, SmsProviderType selected, boolean firstSave) {
        retirementDao.recordSelection(previous, selected, firstSave);
    }

    public long retiredThrough(SmsProviderType provider) {
        return retirementDao.retiredThrough(provider);
    }

    public boolean retired(SmsTransaction row) {
        return row.getId() != null && row.getId() <= retiredThrough(row.getProviderType());
    }

    /** A forced administrator STUB system test is permitted independently of the clinic's selection. */
    public boolean admissionAllowed(Optional<SmsConfig> config, SmsProviderType provider, SmsMessagePurpose purpose) {
        retirementDao.retiredThrough(provider); // Corrupt retirement state also refuses new admissions.
        if (purpose == SmsMessagePurpose.SYSTEM_TEST && provider == SmsProviderType.STUB) {
            return true;
        }
        SmsProviderType selected = config.map(SmsConfig::getProviderType).orElseGet(() -> {
            String value = CarlosProperties.getInstance().getProperty("sms.provider.default");
            if (value == null || value.isBlank()) {
                return SmsProviderType.STUB;
            }
            try {
                return SmsProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid sms.provider.default; configure a supported SMS provider.");
            }
        });
        return selected == provider && config.map(SmsConfig::isEnabled).orElse(true);
    }

    static SmsProviderSendResultDto retirementFailure(SmsTransaction row) {
        boolean systemTest = row.getMessagePurpose() == SmsMessagePurpose.SYSTEM_TEST;
        return SmsProviderSendResultDto.failed(systemTest ? "QUEUE_SYSTEM_TEST_NOT_ACTIVE" : "QUEUE_PROVIDER_NOT_ACTIVE",
                systemTest ? "This queued system test was not sent. Use Send test again in Administration > SMS."
                        : "The SMS provider for this queued text was retired. Review it before sending it again.");
    }
}
