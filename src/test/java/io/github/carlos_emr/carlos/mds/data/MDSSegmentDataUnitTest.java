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
package io.github.carlos_emr.carlos.mds.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.MdsMSHDao;
import io.github.carlos_emr.carlos.commn.dao.MdsNTEDao;
import io.github.carlos_emr.carlos.commn.dao.MdsOBRDao;
import io.github.carlos_emr.carlos.commn.dao.MdsOBXDao;
import io.github.carlos_emr.carlos.commn.dao.MdsPV1Dao;
import io.github.carlos_emr.carlos.commn.dao.MdsZMCDao;
import io.github.carlos_emr.carlos.commn.dao.MdsZMNDao;
import io.github.carlos_emr.carlos.commn.dao.MdsZRGDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Failure handling and legacy-value tolerance of {@link MDSSegmentData} (issue #4567).
 *
 * <p>The DAO queries themselves are exercised against H2 by {@code MDSSegmentDataIntegrationTest};
 * these tests feed the rows a legacy OSCAR import can hold (missing carets, missing hyphen, null
 * columns) and a failing query, and check what the report page is given to render.</p>
 *
 * @since 2026-10-14
 */
@DisplayName("MDSSegmentData")
@Tag("unit")
@Tag("lab")
class MDSSegmentDataUnitTest extends CarlosUnitTestBase {

    private static final int SEGMENT = 17;

    private MdsMSHDao mshDao;
    private MdsZMNDao zmnDao;
    private MdsZRGDao zrgDao;
    private MdsOBRDao obrDao;
    private MdsOBXDao obxDao;

    @BeforeEach
    void setUpDaos() {
        mshDao = createAndRegisterMock(MdsMSHDao.class);
        createAndRegisterMock(MdsPV1Dao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        zmnDao = createAndRegisterMock(MdsZMNDao.class);
        createAndRegisterMock(MdsNTEDao.class);
        createAndRegisterMock(MdsZMCDao.class);
        zrgDao = createAndRegisterMock(MdsZRGDao.class);
        obrDao = createAndRegisterMock(MdsOBRDao.class);
        obxDao = createAndRegisterMock(MdsOBXDao.class);
    }

    private static MDSSegmentData populate() {
        MDSSegmentData data = new MDSSegmentData();
        data.populateMDSSegmentData(String.valueOf(SEGMENT));
        return data;
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    @Test
    @DisplayName("should flag the load as failed and still give the page providers when a query fails")
    void shouldFlagLoadFailure_whenAReportQueryFails() {
        when(mshDao.findMdsSementDataById(SEGMENT)).thenThrow(new IllegalStateException("query failed"));

        MDSSegmentData data = populate();

        assertThat(data.isLoadFailed()).isTrue();
        assertThat(data.providers).isNotNull();
        assertThat(data.providers.referringDoctor).isEmpty();
        assertThat(data.providers.consultingDoctor).isEmpty();
        assertThat(data.providers.admittingDoctor).isEmpty();
    }

    @Test
    @DisplayName("should not flag a failure when the segment simply has no rows")
    void shouldNotFlagLoadFailure_whenSegmentHasNoRows() {
        MDSSegmentData data = populate();

        assertThat(data.isLoadFailed()).isFalse();
        assertThat(data.providers).isNotNull();
        assertThat(data.headersArray).isEmpty();
        assertThat(data.statusArray).isEmpty();
    }

    @Test
    @DisplayName("should keep the whole control id as the client number when it has no accession part")
    void shouldLeaveAccessionEmpty_whenControlIdHasNoHyphen() {
        when(mshDao.findMdsSementDataById(SEGMENT)).thenReturn(
                rows(new Object[] {Timestamp.valueOf("2026-01-02 08:30:00"), "C1234", null}));

        MDSSegmentData data = populate();

        assertThat(data.isLoadFailed()).isFalse();
        assertThat(data.reportDate).isEqualTo("02-Jan-2026");
        assertThat(data.clientNo).isEqualTo("C1234");
        assertThat(data.accessionNo).isEmpty();
        // No form status at all is not a final report.
        assertThat(data.reportStatus).isEqualTo("Partial");
    }

    @Test
    @DisplayName("should read a result whose identifier and producer carry no caret")
    void shouldReadResult_whenIdentifiersHaveNoCaret() {
        when(zmnDao.findMnemonicsBySegmentId(SEGMENT)).thenReturn(
                rows(new Object[] {"FKGLU", "Fake Glucose", "mmol/L", "3.5-5.0"}));
        when(zrgDao.findById(SEGMENT)).thenReturn(rows(new Object[] {"FAKE CHEMISTRY", "CHEM", 1L, "", "1"}));
        when(zmnDao.findResultCodes(SEGMENT, "CHEM")).thenReturn(List.of("FKGLU"));
        when(obrDao.findByIdAndResultCodes(SEGMENT, List.of("FKGLU"))).thenReturn(
                rows(new Object[] {1, "20260102083000"}));
        when(obxDao.findByIdObrAndCodes(SEGMENT, 1, List.of("FKGLU"))).thenReturn(
                rows(new Object[] {1, "XFKGLU", "7.7", "HI", "F", "FK"}));

        MDSSegmentData data = populate();

        assertThat(data.isLoadFailed()).isFalse();
        Results glucose = data.headersArray.get(0).groupedReportsArray.get(0).resultsArray.get(0);
        assertThat(glucose.name).isEqualTo("Fake Glucose");
        assertThat(glucose.units).isEqualTo("mmol/L");
        assertThat(glucose.observationValue).isEqualTo("7.7");
        assertThat(glucose.labID).isEqualTo("FK");
    }

    @Test
    @DisplayName("should list no results under a report group that files no result codes")
    void shouldListNoResults_forReportGroupWithoutResultCodes() {
        when(zrgDao.findById(SEGMENT)).thenReturn(rows(
                new Object[] {"FAKE CHEMISTRY", "CHEM", 1L, "", "1"},
                new Object[] {"FAKE COMMENTS", "NOTES", 1L, "", "2"}));
        when(zmnDao.findResultCodes(SEGMENT, "CHEM")).thenReturn(List.of("FKGLU"));
        when(zmnDao.findResultCodes(SEGMENT, "NOTES")).thenReturn(List.of());
        // An empty code list is no filter at all to the OBR and OBX queries: every row of the report.
        when(obrDao.findByIdAndResultCodes(any(), anyList())).thenReturn(rows(new Object[] {1, "20260102083000"}));
        when(obxDao.findByIdObrAndCodes(any(), any(), anyList())).thenReturn(
                rows(new Object[] {1, "XFKGLU^Glucose", "7.7", "HI", "F", "FK^Fake"}));

        MDSSegmentData data = populate();

        assertThat(data.headersArray.get(0).groupedReportsArray).hasSize(1);
        assertThat(data.headersArray.get(1).groupedReportsArray).isEmpty();
    }

    @Test
    @DisplayName("should give the page empty text, never null, for result columns a legacy row left null")
    void shouldBlankMissingValues_whenLegacyResultColumnsAreNull() {
        when(zmnDao.findMnemonicsBySegmentId(SEGMENT)).thenReturn(
                rows(new Object[] {"FKGLU", null, null, null}));
        when(zrgDao.findById(SEGMENT)).thenReturn(rows(new Object[] {null, "CHEM", 1L, null, "1"}));
        when(zmnDao.findResultCodes(SEGMENT, "CHEM")).thenReturn(List.of("FKGLU"));
        when(obrDao.findByIdAndResultCodes(any(), anyList())).thenReturn(rows(new Object[] {1, null}));
        when(obxDao.findByIdObrAndCodes(any(), any(), anyList())).thenReturn(
                rows(new Object[] {1, "XFKGLU^Glucose", null, null, null, null}));

        MDSSegmentData data = populate();

        assertThat(data.isLoadFailed()).isFalse();
        assertThat(data.headersArray.get(0).reportFlag).isEmpty();
        GroupedReports group = data.headersArray.get(0).groupedReportsArray.get(0);
        assertThat(group.timeStamp).isEmpty();
        Results result = group.resultsArray.get(0);
        assertThat(result.name).isEmpty();
        assertThat(result.units).isEmpty();
        assertThat(result.referenceRange).isEmpty();
        assertThat(result.observationValue).isEmpty();
        assertThat(result.abnormalFlags).isEmpty();
        assertThat(result.resultStatus).isEmpty();
        assertThat(result.labID).isEmpty();
    }

    @Test
    @DisplayName("should read an empty provider field from the visit segment as no provider")
    void shouldReturnEmptyName_whenProviderFieldIsNull() {
        assertThat(ProviderData.beautifyProviderName(null)).isEmpty();
    }

    @Test
    @DisplayName("should describe a routing row with a blank status as not acknowledged")
    void shouldDescribeBlankStatus_asNotAcknowledged() {
        MDSSegmentData data = new MDSSegmentData();

        assertThat(data.descriptiveStatus(null)).isEqualTo("Not Acknowledged");
        assertThat(data.descriptiveStatus("")).isEqualTo("Not Acknowledged");
        assertThat(data.descriptiveStatus("A")).isEqualTo("Acknowledged");
    }
}
