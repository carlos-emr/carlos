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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.support.IntegrationTestSeedService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

/**
 * Populates a legacy MDS report from the real DAO queries against H2 (issue #4567).
 *
 * <p>Production's MDS detail tables ({@code mdsOBX}, {@code mdsZMN}, {@code mdsNTE}, ...) have no
 * primary key: every row of one report shares its {@code segmentID}, which the JPA entities map as
 * {@code @Id}. The H2 schema generated from those entities makes {@code segmentID} a primary key, so
 * a report with more than one result could not be stored. Before the test transaction the key is
 * dropped from those tables to give them the production shape, and it is restored afterwards. That
 * shape is what exposes queries returning entities: Hibernate resolves every row with the same id to
 * the first row's instance, so a report would show its first result once per row.</p>
 *
 * <p>The report is synthetic: two report groups, three results over two OBR segments, one plain note,
 * one message-code note and a lab routing row.</p>
 *
 * @since 2026-10-14
 */
@DisplayName("MDSSegmentData integration (H2)")
@Tag("integration")
@Tag("lab")
class MDSSegmentDataIntegrationTest extends CarlosTestBase {

    private static final int SEGMENT = 9045671;
    private static final String PROVIDER = "904567";
    private static final String ACKNOWLEDGER = "904568";

    /** Tables that share segmentID across rows in production; mdsMSH keeps its real primary key. */
    private static final List<String> MULTI_ROW_TABLES = List.of(
            "mdsZFR", "mdsPV1", "mdsZLB", "mdsZRG", "mdsZMN", "mdsOBR", "mdsOBX", "mdsNTE", "mdsZMC", "mdsPID");

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(applicationContext.getBean("dataSource", DataSource.class));
    }

    @BeforeTransaction
    void dropSyntheticPrimaryKeys() {
        JdbcTemplate jdbc = jdbc();
        for (String table : MULTI_ROW_TABLES) {
            jdbc.execute("ALTER TABLE " + table + " DROP PRIMARY KEY");
        }
    }

    @AfterTransaction
    void restoreSyntheticPrimaryKeys() {
        JdbcTemplate jdbc = jdbc();
        for (String table : MULTI_ROW_TABLES) {
            // The test transaction rolled back; the delete only guards the key against a leaked row.
            jdbc.update("DELETE FROM " + table + " WHERE segmentID = ?", SEGMENT);
            jdbc.execute("ALTER TABLE " + table + " ADD PRIMARY KEY (segmentID)");
        }
    }

    @BeforeEach
    void seedReport() {
        IntegrationTestSeedService.ensureProviderExists(em, PROVIDER);
        IntegrationTestSeedService.ensureProviderExists(em, ACKNOWLEDGER);
        sql("INSERT INTO mdsMSH (segmentID, sendingApp, dateTime, type, messageConID, processingID, versionID,"
                + " acceptAckType, appAckType, demographic_no)"
                + " VALUES (" + SEGMENT + ", 'FAKE-LAB', TIMESTAMP '2026-01-02 08:30:00', 'ORU^R01', 'C1234-A5678',"
                + " 'P', '2.3', 'AL', 'NE', 0)");
        // Two form rows: the report is only Final when every form is (min of the statuses).
        sql("INSERT INTO mdsZFR (segmentID, reportForm, reportFormStatus) VALUES (" + SEGMENT + ", '1', '1')");
        sql("INSERT INTO mdsZFR (segmentID, reportForm, reportFormStatus) VALUES (" + SEGMENT + ", '2', '0')");
        sql("INSERT INTO mdsPV1 (segmentID, patientClass, refDoctor, conDoctor, admDoctor)"
                + " VALUES (" + SEGMENT + ", 'O', '000000^FakeRef^Alex^^^Dr', '', NULL)");
        sql("INSERT INTO mdsZLB (segmentID, labID) VALUES (" + SEGMENT + ", 'FK')");

        // Report groups: CHEM with no heading, HEME with two heading rows.
        sql("INSERT INTO mdsZRG (segmentID, reportSequence, reportGroupID, reportGroupDesc, reportGroupHeading)"
                + " VALUES (" + SEGMENT + ", '1', 'CHEM', 'FAKE CHEMISTRY', '')");
        sql("INSERT INTO mdsZRG (segmentID, reportSequence, reportGroupID, reportGroupDesc, reportGroupHeading)"
                + " VALUES (" + SEGMENT + ", '2', 'HEME', 'FAKE HEMATOLOGY', 'FAKE HEADING 1')");
        sql("INSERT INTO mdsZRG (segmentID, reportSequence, reportGroupID, reportGroupDesc, reportGroupHeading)"
                + " VALUES (" + SEGMENT + ", '3', 'HEME', 'FAKE HEMATOLOGY', 'FAKE HEADING 2')");

        // Mnemonics: the test names, units and ranges, and which group each result code belongs to.
        mnemonic("FKGLU", "Fake Glucose", "mmol/L", "3.5-5.0", "CHEM");
        mnemonic("FKNA", "Fake Sodium", "mmol/L", "135-145", "CHEM");
        mnemonic("FKHGB", "Fake Hemoglobin", "g/L", "120-160", "HEME");

        sql("INSERT INTO mdsOBR (segmentID, obrID, observationDateTime) VALUES (" + SEGMENT + ", 1, '20260102083000')");
        sql("INSERT INTO mdsOBR (segmentID, obrID, observationDateTime) VALUES (" + SEGMENT + ", 2, '20260102091500')");

        result(1, "XFKGLU^Glucose", "FKGLU", "7.7", "HI", "F", 1);
        result(2, "XFKNA^Sodium", "FKNA", "140", "", "F", 1);
        result(3, "XFKHGB^Hemoglobin", "FKHGB", "131", null, "C", 2);

        // An "M" note carries its text after a three-character prefix; an "MC" note names a ZMC set.
        sql("INSERT INTO mdsNTE (segmentID, sourceOfComment, comment, associatedOBX)"
                + " VALUES (" + SEGMENT + ", 'M', 'XXXFasting sample', 2)");
        sql("INSERT INTO mdsNTE (segmentID, sourceOfComment, comment, associatedOBX)"
                + " VALUES (" + SEGMENT + ", 'MC', 'X7', 3)");
        sql("INSERT INTO mdsZMC (segmentID, setID, messageCodeDesc) VALUES (" + SEGMENT + ", '6', 'Wrong code')");
        sql("INSERT INTO mdsZMC (segmentID, setID, messageCodeDesc) VALUES (" + SEGMENT + ", '7', 'Specimen haemolysed')");

        // MariaDB fills providerLabRouting.timestamp (NOT NULL DEFAULT current_timestamp); H2 does not.
        sql("INSERT INTO providerLabRouting (provider_no, lab_no, lab_type, status, timestamp)"
                + " VALUES ('" + PROVIDER + "', " + SEGMENT + ", 'MDS', 'N', TIMESTAMP '2026-01-02 09:00:00')");
        sql("INSERT INTO providerLabRouting (provider_no, lab_no, lab_type, status, comment, timestamp)"
                + " VALUES ('" + ACKNOWLEDGER + "', " + SEGMENT + ", 'MDS', 'A', 'Reviewed',"
                + " TIMESTAMP '2026-01-03 14:05:00')");
    }

    private void mnemonic(String code, String name, String units, String range, String group) {
        sql("INSERT INTO mdsZMN (segmentID, resultMnemonic, reportName, units, referenceRange, resultCode, reportGroup)"
                + " VALUES (" + SEGMENT + ", '" + code + "', '" + name + "', '" + units + "', '" + range + "', '"
                + code + "', '" + group + "')");
    }

    private void result(int obx, String iden, String subId, String value, String flags, String status, int obr) {
        sql("INSERT INTO mdsOBX (segmentID, obxID, valueType, observationIden, observationSubID, observationValue,"
                + " abnormalFlags, observationResultStatus, producersID, associatedOBR)"
                + " VALUES (" + SEGMENT + ", " + obx + ", 'NM', '" + iden + "', '" + subId + "', '" + value + "', "
                + (flags == null ? "NULL" : "'" + flags + "'") + ", '" + status + "', 'FK^Fake Lab', " + obr + ")");
    }

    private void sql(String statement) {
        em.createNativeQuery(statement).executeUpdate();
    }

    private static MDSSegmentData populate() {
        MDSSegmentData data = new MDSSegmentData();
        data.populateMDSSegmentData(String.valueOf(SEGMENT));
        return data;
    }

    @Test
    @DisplayName("should fill the report header from the message and form segments")
    void shouldFillReportHeader_whenSegmentHasMessageAndFormRows() {
        MDSSegmentData data = populate();

        assertThat(data.reportDate).isEqualTo("02-Jan-2026");
        assertThat(data.reportStatus).isEqualTo("Partial");
        assertThat(data.clientNo).isEqualTo("C1234");
        assertThat(data.accessionNo).isEqualTo("A5678");
    }

    @Test
    @DisplayName("should name the requesting, report-to and cc clients from the visit segment")
    void shouldFillProviders_whenVisitSegmentPresent() {
        MDSSegmentData data = populate();

        assertThat(data.providers).isNotNull();
        assertThat(data.providers.referringDoctor).isEqualTo("Dr FakeRef");
        assertThat(data.providers.consultingDoctor).isEmpty();
        assertThat(data.providers.admittingDoctor).isEmpty();
    }

    @Test
    @DisplayName("should list the report groups in sequence with their headings")
    void shouldListReportGroups_inReportSequence() {
        MDSSegmentData data = populate();

        assertThat(data.headersArray).extracting(h -> h.reportFlag)
                .containsExactly("FAKE CHEMISTRY", "FAKE HEMATOLOGY");
        assertThat(data.headersArray).extracting(h -> h.reportSequence).containsExactly("CHEM", "HEME");
        assertThat(data.headersArray.get(0).reportHeading).isNull();
        assertThat(data.headersArray.get(1).reportHeading).containsExactly("FAKE HEADING 1", "FAKE HEADING 2");
    }

    @Test
    @DisplayName("should group each report group's results under its OBR with the observation time")
    void shouldGroupResults_byObservationRequest() {
        MDSSegmentData data = populate();

        GroupedReports chemistry = data.headersArray.get(0).groupedReportsArray.get(0);
        GroupedReports hematology = data.headersArray.get(1).groupedReportsArray.get(0);
        assertThat(data.headersArray.get(0).groupedReportsArray).hasSize(1);
        assertThat(chemistry.associatedOBR).isEqualTo("1");
        assertThat(chemistry.timeStamp).isEqualTo("02-Jan-26 08:30");
        assertThat(chemistry.codes).containsExactlyInAnyOrder("FKGLU", "FKNA");
        assertThat(data.headersArray.get(1).groupedReportsArray).hasSize(1);
        assertThat(hematology.associatedOBR).isEqualTo("2");
        assertThat(hematology.timeStamp).isEqualTo("02-Jan-26 09:15");
    }

    @Test
    @DisplayName("should show every result row with its own value, flag, range and units")
    void shouldShowEveryResultRow_withItsOwnValues() {
        MDSSegmentData data = populate();

        List<Results> chemistry = data.headersArray.get(0).groupedReportsArray.get(0).resultsArray;
        assertThat(chemistry).extracting(r -> r.name).containsExactly("Fake Glucose", "Fake Sodium");
        assertThat(chemistry).extracting(r -> r.observationValue).containsExactly("7.7", "140");
        assertThat(chemistry).extracting(r -> r.abnormalFlags).containsExactly("HI", "");
        assertThat(chemistry).extracting(r -> r.referenceRange).containsExactly("3.5-5.0", "135-145");
        assertThat(chemistry).extracting(r -> r.units).containsExactly("mmol/L", "mmol/L");
        assertThat(chemistry).extracting(r -> r.resultStatus).containsExactly("Final", "Final");
        assertThat(chemistry).extracting(r -> r.labID).containsExactly("FK", "FK");

        Results hemoglobin = data.headersArray.get(1).groupedReportsArray.get(0).resultsArray.get(0);
        assertThat(hemoglobin.name).isEqualTo("Fake Hemoglobin");
        assertThat(hemoglobin.observationValue).isEqualTo("131");
        assertThat(hemoglobin.abnormalFlags).isEmpty();
        assertThat(hemoglobin.resultStatus).isEqualTo("Corrected");
    }

    @Test
    @DisplayName("should attach each note to the result it belongs to")
    void shouldAttachNotes_toTheirResults() {
        MDSSegmentData data = populate();

        List<Results> chemistry = data.headersArray.get(0).groupedReportsArray.get(0).resultsArray;
        Results hemoglobin = data.headersArray.get(1).groupedReportsArray.get(0).resultsArray.get(0);
        assertThat(chemistry.get(0).notes).isNull();
        assertThat(chemistry.get(1).notes).containsExactly("Fasting sample");
        assertThat(hemoglobin.notes).containsExactly("Specimen haemolysed");
    }

    @Test
    @DisplayName("should list the routing status of each provider the lab is routed to")
    void shouldListRoutingStatus_forEachRoutedProvider() {
        MDSSegmentData data = populate();

        assertThat(data.statusArray).hasSize(2);
        assertThat(data.statusArray).filteredOn(s -> PROVIDER.equals(s.getProviderNo())).singleElement()
                .satisfies(status -> assertThat(status.getStatus()).isEqualTo("Not Acknowledged"));
        assertThat(data.statusArray).filteredOn(s -> ACKNOWLEDGER.equals(s.getProviderNo())).singleElement()
                .satisfies(status -> {
                    assertThat(status.getStatus()).isEqualTo("Acknowledged");
                    assertThat(status.getComment()).isEqualTo("Reviewed");
                    // The time of the acknowledgement, not midnight of its date.
                    assertThat(status.getTimestamp()).isEqualTo("03-Jan-26 14:05");
                });
        assertThat(data.getAcknowledgedStatus(PROVIDER)).isFalse();
        assertThat(data.getAcknowledgedStatus(ACKNOWLEDGER)).isTrue();
    }
}
