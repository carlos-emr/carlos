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
package io.github.carlos_emr.carlos.lab.ca.on;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Covers the Inbox Hub's acknowledgement bound on its way from the lab result layer to the query.
 *
 * <p>The bound is what keeps the toolbar's Acknowledged list to today's acknowledgements. The
 * lab results reach the query through two forwarding layers; one that dropped the bound would
 * leave the labs -- the largest part of most inboxes -- paging through the whole history.
 *
 * @since 2026-10-15
 */
@DisplayName("Lab results acknowledgement bound")
@Tag("unit")
@Tag("lab")
class CommonLabResultDataAcknowledgedSinceUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should hand the acknowledgement bound to the paged lab query")
    void shouldForwardAcknowledgedSince_toPagedLabQuery() throws Exception {
        registerResultLayerMocks();
        Hl7TextInfoDao labs = mock(Hl7TextInfoDao.class);
        Date startOfToday = Date.from(Instant.parse("2026-10-15T04:00:00Z"));
        // Hl7textResultsData resolves its DAO once, in a static initializer, so the query mock is
        // swapped into the field for this test and the field put back afterwards.
        Field query = Hl7textResultsData.class.getDeclaredField("hl7TxtInfoDao");
        query.setAccessible(true);
        Object original = query.get(null);
        query.set(null, labs);
        try {
            new CommonLabResultData().populateLabResultsData(null, "999998", null, "", "", "", "A",
                    true, 0, 100, false, null, null, null, startOfToday);
        } finally {
            query.set(null, original);
        }

        verify(labs).findLabAndDocsViaMagic(eq("999998"), isNull(), eq(""), eq(""), eq(""), eq("A"),
                eq(true), eq(0), eq(100), eq(false), isNull(), eq(true), eq(false), isNull(), isNull(),
                eq(startOfToday));
    }

    /**
     * CommonLabResultData and Hl7textResultsData resolve their DAOs in static initializers, and
     * the lab path constructs an MDSResultsData; register them all before either class loads.
     */
    private void registerResultLayerMocks() {
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(MeasurementsDeletedDao.class, mock(MeasurementsDeletedDao.class));
        registerMock(MeasurementDao.class, mock(MeasurementDao.class));
        registerMock(MeasurementsExtDao.class, mock(MeasurementsExtDao.class));
        registerMock(MeasurementMapDao.class, mock(MeasurementMapDao.class));
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(Hl7TextInfoDao.class, mock(Hl7TextInfoDao.class));
        registerMock(Hl7TextMessageDao.class, mock(Hl7TextMessageDao.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
        registerMock(LabPatientPhysicianInfoDao.class, mock(LabPatientPhysicianInfoDao.class));
    }
}
