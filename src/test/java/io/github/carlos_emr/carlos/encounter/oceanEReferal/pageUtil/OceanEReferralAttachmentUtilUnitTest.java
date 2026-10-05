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
package io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDataDaoImpl;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link OceanEReferralAttachmentUtil}: removing a document from the pending Ocean
 * eReferral queue is scoped to the consultation's patient (issue #3867).
 *
 * @since 2026-09-24
 */
@DisplayName("OceanEReferralAttachmentUtil")
@Tag("unit")
@Tag("security")
class OceanEReferralAttachmentUtilUnitTest extends CarlosUnitTestBase {

    private EReferAttachmentDataDaoImpl dataDao;
    private EReferAttachmentDaoImpl attachmentDao;
    private Object originalDataDao;
    private Object originalAttachmentDao;

    @BeforeEach
    void setUp() {
        dataDao = mock(EReferAttachmentDataDaoImpl.class);
        attachmentDao = mock(EReferAttachmentDaoImpl.class);
        registerMock(EReferAttachmentDataDaoImpl.class, dataDao);
        registerMock(EReferAttachmentDaoImpl.class, attachmentDao);
        // The DAOs are static fields resolved once at class load; swap them for this test only.
        originalDataDao = ReflectionTestUtils.getField(OceanEReferralAttachmentUtil.class, "eReferAttachmentDataDao");
        originalAttachmentDao = ReflectionTestUtils.getField(OceanEReferralAttachmentUtil.class, "eReferAttachmentDao");
        ReflectionTestUtils.setField(OceanEReferralAttachmentUtil.class, "eReferAttachmentDataDao", dataDao);
        ReflectionTestUtils.setField(OceanEReferralAttachmentUtil.class, "eReferAttachmentDao", attachmentDao);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(OceanEReferralAttachmentUtil.class, "eReferAttachmentDataDao", originalDataDao);
        ReflectionTestUtils.setField(OceanEReferralAttachmentUtil.class, "eReferAttachmentDao", originalAttachmentDao);
    }

    @Test
    @DisplayName("should look up only the consultation patient's Ocean queue row and leave another patient's alone")
    void shouldScopeQueueLookupToPatient_whenDetaching() {
        when(dataDao.getRecentByDocumentId(eq(555), eq("D"), eq(123), any(Date.class))).thenReturn(null);

        OceanEReferralAttachmentUtil.detachOceanEReferralConsult("555", 123, "D");

        verify(dataDao).getRecentByDocumentId(eq(555), eq("D"), eq(123), any(Date.class));
        verify(dataDao, never()).remove((Object) any());
        verify(attachmentDao, never()).remove((Object) any());
    }
}
