/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormReportToolDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EFormReportToolManagerImpl}.
 *
 * <p>Verifies report population batches eform values by form-data ID instead of
 * loading values in a loop.</p>
 *
 * @since 2026-04-17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EForm Report Tool Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
public class EFormReportToolManagerUnitTest extends CarlosUnitTestBase {

    @Mock
    private EFormReportToolDao mockEFormReportToolDao;

    @Mock
    private EFormDao mockEFormDao;

    @Mock
    private EFormValueDao mockEFormValueDao;

    @Mock
    private EFormDataDao mockEFormDataDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private EFormReportToolManagerImpl manager;

    @BeforeEach
    void setUp() {
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_admin.eformreporttool", "r", null)).thenReturn(true);

        manager = new EFormReportToolManagerImpl();
        injectDependency(manager, "eformReportToolDao", mockEFormReportToolDao);
        injectDependency(manager, "eformDao", mockEFormDao);
        injectDependency(manager, "eformValueDao", mockEFormValueDao);
        injectDependency(manager, "eformDataDao", mockEFormDataDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should batch load eform values when populating report table")
    void shouldBatchLoadEformValues_whenPopulatingReportTable() {
        // Given
        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setId(77);
        reportTool.setEformId(500);
        reportTool.setTableName("eft_report");

        Date formDate = new Date();
        Date formTime = new Date();
        Object[] rowOne = new Object[]{10, 1001, formDate, formTime, "999998"};
        Object[] rowTwo = new Object[]{11, 1002, formDate, formTime, "999998"};
        Object[] rowThree = new Object[]{12, 1003, formDate, formTime, "999999"};
        EFormValue valueOne = createValue(10, "bp");
        EFormValue valueThree = createValue(12, "weight");

        // Production calls dao.find(Integer) which resolves to find(Object); stub must match that
        // overload, not the find(int) overload picked by a bare int literal.
        when(mockEFormReportToolDao.find((Object) Integer.valueOf(77))).thenReturn(reportTool);
        when(mockEFormDataDao.findMetaFieldsByFormId(500)).thenReturn(List.of(rowOne, rowTwo, rowThree));
        when(mockEFormValueDao.findByFormDataIdList(List.of(10, 11, 12))).thenReturn(List.of(valueOne, valueThree));

        // When
        manager.populateReportTable(mockLoggedInInfo, 77);

        // Then
        verify(mockEFormValueDao).findByFormDataIdList(List.of(10, 11, 12));
        verify(mockEFormValueDao, never()).findByFormDataId(anyInt());
        verify(mockEFormReportToolDao).populateReportTableItem(eq(reportTool), eq(List.of(valueOne)), eq(10), eq(1001), any(Date.class), eq("999998"));
        verify(mockEFormReportToolDao).populateReportTableItem(eq(reportTool), eq(List.of(valueThree)), eq(12), eq(1003), any(Date.class), eq("999999"));
        verify(mockEFormReportToolDao, never()).populateReportTableItem(eq(reportTool), anyList(), eq(11), eq(1002), any(Date.class), eq("999998"));
    }

    @Test
    @DisplayName("should chunk form data ID batches when populating large report tables")
    void shouldChunkFormDataIdBatches_whenPopulatingLargeReportTables() {
        // Given
        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setId(78);
        reportTool.setEformId(501);
        reportTool.setTableName("eft_large_report");

        List<Object[]> fdidRows = new ArrayList<>();
        List<Integer> firstBatch = new ArrayList<>();
        List<Integer> secondBatch = new ArrayList<>();
        Set<Integer> populatedFdids = new LinkedHashSet<>();
        Date formDate = new Date();

        for (int fdid = 1; fdid <= 501; fdid++) {
            fdidRows.add(new Object[]{fdid, 2000 + fdid, formDate, formDate, "999998"});
            if (fdid <= 500) {
                firstBatch.add(fdid);
            } else {
                secondBatch.add(fdid);
            }
            if (fdid == 1 || fdid == 501) {
                populatedFdids.add(fdid);
            }
        }

        when(mockEFormReportToolDao.find((Object) Integer.valueOf(78))).thenReturn(reportTool);
        when(mockEFormDataDao.findMetaFieldsByFormId(501)).thenReturn(fdidRows);
        when(mockEFormValueDao.findByFormDataIdList(firstBatch)).thenReturn(List.of(createValue(1, "bp")));
        when(mockEFormValueDao.findByFormDataIdList(secondBatch)).thenReturn(List.of(createValue(501, "weight")));

        // When
        manager.populateReportTable(mockLoggedInInfo, 78);

        // Then
        verify(mockEFormValueDao).findByFormDataIdList(firstBatch);
        verify(mockEFormValueDao).findByFormDataIdList(secondBatch);
        verify(mockEFormValueDao, never()).findByFormDataId(anyInt());
        verify(mockEFormReportToolDao, times(populatedFdids.size())).populateReportTableItem(eq(reportTool), anyList(), anyInt(), anyInt(), any(Date.class), eq("999998"));
    }

    /**
     * Creates an eform value fixture for a specific form-data row. Sets a unique id so
     * AbstractModel.equals — which dereferences getId() without a null guard — doesn't NPE
     * when Mockito runs eq() comparisons during verify().
     *
     * @param formDataId Integer the form-data identifier the value belongs to
     * @param varName String the eform variable name
     * @return EFormValue the populated test value
     */
    private EFormValue createValue(Integer formDataId, String varName) {
        EFormValue value = new EFormValue();
        value.setFormDataId(formDataId);
        value.setVarName(varName);
        value.setVarValue("value");
        try {
            java.lang.reflect.Field idField = EFormValue.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(value, formDataId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to set id on EFormValue test fixture", e);
        }
        return value;
    }
}
