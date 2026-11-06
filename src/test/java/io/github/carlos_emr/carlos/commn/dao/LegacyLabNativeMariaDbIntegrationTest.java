// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

/** Physical MariaDB priority hints, counts, nullable columns and export metadata. */
@Tag("integration")
@Tag("flyway-schema")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class LegacyLabNativeMariaDbIntegrationTest extends CarlosTestBase {
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    @Autowired private Hl7TextInfoDao labs;
    @Autowired private InboxCategoryDao inbox;
    @Autowired private DocumentTransferDao transfers;
    @Autowired private WorkFlowDao workflows;

    @Test
    void shouldBoundAndOrderLabStatuses_andApplyLowPriorityUpdate() {
        int first = 1900554801;
        int second = first + 1;
        for (int id : new int[]{first, second}) {
            Hl7TextInfo info = new Hl7TextInfo();
            info.setLabNumber(id);
            info.setReportStatus("F");
            info.setAccessionNumber("synthetic-5548-" + id);
            info.setResultStatus(id == first ? null : "");
            em.persist(info);
        }
        em.flush();
        assertThat(labs.findRecentLabStatuses(-1)).isEmpty();
        assertThat(labs.findRecentLabStatuses(0)).isEmpty();
        assertThat(labs.findRecentLabStatuses(1)).containsExactly(new Hl7TextInfoDao.LabStatus(second, ""));
        assertThat(labs.findRecentLabStatuses(2)).containsExactly(
                new Hl7TextInfoDao.LabStatus(second, ""), new Hl7TextInfoDao.LabStatus(first, ""));
        labs.updateResultStatusLowPriority(first, "A");
        assertThat(labs.findRecentLabStatuses(2).get(1).resultStatus()).isEqualTo("A");
    }

    @Test
    void shouldPreserveDistinctAccessionAndRoutingStatus_whenCountingUnmatchedLabs() throws Exception {
        String provider = "554801";
        for (int index = 0; index < 3; index++) {
            int id = 1900554810 + index;
            Hl7TextInfo info = new Hl7TextInfo();
            info.setLabNumber(id);
            info.setReportStatus("F");
            info.setAccessionNumber(index == 2 ? "synthetic-other" : "synthetic-same");
            info.setResultStatus("A");
            em.persist(info);
            em.createNativeQuery("INSERT INTO providerLabRouting (provider_no, lab_no, lab_type, status) VALUES (?1,?2,'HL7',?3)")
                    .setParameter(1, provider).setParameter(2, id).setParameter(3, index == 2 ? "A" : "N").executeUpdate();
        }
        var filter = new InboxCategoryDao.Filter("", "", "", false, true, provider, "N", "all", null, null);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isEqualTo(1);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.ABNORMAL, null).total()).isEqualTo(2);
        var all = new InboxCategoryDao.Filter("", "", "", false, true, provider, "", "all", null, null);
        assertThat(inbox.count(all, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isEqualTo(2);
    }

    @Test
    void shouldSelectLatestFormWithinThePatientAndWorkflow_only() {
        for (int[] row : new int[][]{{1900554801,554801,7},{1900554802,554801,7},
                {1900554803,554802,7},{1900554804,554801,8}}) {
            em.createNativeQuery("INSERT INTO formRhImmuneGlobulin (ID,demographic_no,workflowId,formEdited)"
                    + " VALUES (?1,?2,?3,'2026-11-05 12:00:00')")
                    .setParameter(1,row[0]).setParameter(2,row[1]).setParameter(3,row[2]).executeUpdate();
        }
        assertThat(workflows.findLatestRhFormId(554801,7)).isEqualTo(1900554802);
        assertThat(workflows.findLatestRhFormId(554801,9)).isNull();
    }

    @Test
    void shouldKeepPhysicalColumnNamesAndTrimNulls_whenMappingTransferRows() throws Exception {
        // Bound synthetic expression values avoid mutating any fixture demographic.
        var definition = new DocumentTransferDao.Definition(
                "(SELECT 5548 AS demographic_no, CAST(NULL AS CHAR) AS nullable_value, '  synthetic  ' AS text_value) synthetic",
                "synthetic.demographic_no", "", "", "synthetic.demographic_no", List.of("synthetic.nullable_value", "synthetic.text_value"));
        var rows = transfers.read(definition, 5548);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().item()).isEqualTo("5548");
        assertThat(rows.getFirst().fields()).containsExactly("", "synthetic");
        assertThat(rows.getFirst().columns()).extracting(DocumentTransferDao.Column::qualifiedName)
                .allSatisfy(name -> assertThat(name).contains("."));
        assertThat(transfers.read(definition, 5549)).isEmpty();
    }
}
