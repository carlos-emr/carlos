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

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("service")
@DisplayName("SMS settings audit record")
class SmsConfigAuditRecorderUnitTest {
    private final OscarLogDao oscarLogDao = mock(OscarLogDao.class);
    private String originalKey;

    @BeforeEach
    void seedEncryptionKey() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
    }

    @AfterEach
    void restoreEncryptionKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @DisplayName("should record who saved, the switches in force and the changed setting names, with no secret")
    void shouldRecordSave_withoutSecrets() {
        SmsConfig saved = new SmsConfig();
        saved.setProviderType(SmsProviderType.STUB);
        saved.setEnabled(false);
        saved.setSchedulerEnabled(true);
        saved.setSenderNumber("+14165551212");
        saved.setWebhookSecret("webhook-value-123");

        new SmsConfigAuditRecorder(oscarLogDao).recordSaved(saved, "999998", List.of("enabled", "webhookSecret"));

        ArgumentCaptor<OscarLog> log = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(log.capture());
        assertThat(log.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(log.getValue().getAction()).isEqualTo("update");
        assertThat(log.getValue().getContent()).isEqualTo("sms_config");
        assertThat(log.getValue().getData())
                .isEqualTo("providerType=STUB enabled=false schedulerEnabled=true changed=enabled,webhookSecret")
                .doesNotContain("webhook-value-123")
                .doesNotContain("4165551212");
    }
}
