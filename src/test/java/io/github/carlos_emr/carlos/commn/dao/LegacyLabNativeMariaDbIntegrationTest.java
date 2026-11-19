/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
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
@Tag("dao")
@Tag("flyway-schema")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class LegacyLabNativeMariaDbIntegrationTest extends CarlosTestBase {
    private static final InboxCategoryDao.DateSearchType OBSERVATION = InboxCategoryDao.DateSearchType.SERVICE_OBSERVATION;
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
            info.setResultStatus(null);
            em.persist(info);
        }
        em.flush();
        // The entity setter trims a single space to NULL; write the physical VARCHAR(1) value
        // directly so this assertion exercises the DAO's trim contract rather than the setter.
        em.createNativeQuery("UPDATE hl7TextInfo SET result_status=' ' WHERE lab_no=?1")
                .setParameter(1, second).executeUpdate();
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
        var filter = new InboxCategoryDao.Filter("", "", "", false, true, provider, "N", "all", null, null, OBSERVATION);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isEqualTo(2);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.ABNORMAL, null).total()).isEqualTo(2);
        var all = new InboxCategoryDao.Filter("", "", "", false, true, provider, "", "all", null, null, OBSERVATION);
        assertThat(inbox.count(all, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isEqualTo(3);
    }

    @Test
    void shouldCountOnlyTheSelectedPatientLabs_andKeepZeroAndMissingPatientsUnmatched() throws Exception {
        String provider = "577901";
        int patient = 1900577901;
        em.createNativeQuery("INSERT INTO demographic (demographic_no,last_name,first_name,hin,sex) VALUES (?1,'FAKE-Inbox','Native',NULL,'F')")
                .setParameter(1, patient).executeUpdate();
        for (int index = 0; index < 4; index++) {
            int lab = 1900577910 + index;
            em.createNativeQuery("INSERT INTO hl7TextInfo (lab_no,accessionNum,result_status) VALUES (?1,?2,?3)")
                    .setParameter(1, lab).setParameter(2, index < 2 ? "FAKE-shared-accession" : "FAKE-" + lab)
                    .setParameter(3, index == 1 ? "N" : "A").executeUpdate();
            em.createNativeQuery("INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (?1,?2,'HL7','N')")
                    .setParameter(1, provider).setParameter(2, lab).executeUpdate();
            if (index != 3) em.createNativeQuery("INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (?1,?2,'HL7',NOW())")
                    .setParameter(1, index == 2 ? 0 : patient).setParameter(2, lab).executeUpdate();
        }
        var filter = new InboxCategoryDao.Filter("FAKE-Inbox", "Native", "", true, true, provider, "N", "all", null, null, OBSERVATION);
        var result = inbox.count(filter, InboxCategoryDao.Operation.LAB_PATIENT, null);
        assertThat(result.total()).isEqualTo(2);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.NORMAL, null).total()).isEqualTo(1);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.ABNORMAL, null).total()).isEqualTo(1);
        assertThat(result.patients()).containsExactly(new InboxCategoryDao.PatientCount(patient, "Native", "FAKE-Inbox", 2, 0, 0));
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.LAB_DEMOGRAPHIC, Integer.toString(patient)).total()).isEqualTo(2);
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.LAB_DEMOGRAPHIC, Integer.toString(patient + 1)).total()).isZero();
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isEqualTo(2);
    }

    @Test
    void shouldCountDistinctPatientDocuments_andSeparateUnmatchedAndAcknowledgedRouting() throws Exception {
        String provider = "577902";
        int patient = 1900577920;
        em.createNativeQuery("INSERT INTO demographic (demographic_no,last_name,first_name,hin,sex) VALUES (?1,'FAKE-Document','Native',NULL,'F')")
                .setParameter(1, patient).executeUpdate();
        for (int index = 0; index < 3; index++) {
            int document = 1900577921 + index;
            em.createNativeQuery("INSERT INTO document (document_no,docdesc,docfilename,doccreator,responsible,status,contenttype,restrictToProgram,abnormal)"
                    + " VALUES (?1,'FAKE-inbox','fake.pdf',?2,?2,'A','application/pdf',0,0)")
                    .setParameter(1, document).setParameter(2, provider).executeUpdate();
            em.createNativeQuery("INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',?1,?2,'A')")
                    .setParameter(1, index == 1 ? -1 : patient).setParameter(2, document).executeUpdate();
            em.createNativeQuery("INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (?1,?2,'DOC',?3)")
                    .setParameter(1, provider).setParameter(2, document).setParameter(3, index == 2 ? "A" : "N").executeUpdate();
        }
        // A second module link must not count the same routed document twice.
        em.createNativeQuery("INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('other',?1,1900577921,'A')")
                .setParameter(1, patient).executeUpdate();
        var filter = new InboxCategoryDao.Filter("FAKE-Document", "Native", "", true, true, provider, "N", "all", null, null, OBSERVATION);
        var result = inbox.count(filter, InboxCategoryDao.Operation.DOCUMENT_PATIENT, null);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.patients()).containsExactly(new InboxCategoryDao.PatientCount(patient, "Native", "FAKE-Document", 0, 1, 0));
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.DOCUMENT_UNMATCHED, null).total()).isEqualTo(1);
        em.createNativeQuery("DELETE FROM ctl_document WHERE document_no=1900577921").executeUpdate();
        assertThat(inbox.count(filter, InboxCategoryDao.Operation.DOCUMENT_PATIENT, null).total()).isZero();
    }

    @Test
    void shouldCountDistinctHrmReports_byPatientProviderAndSignoffIndependentlyOfViewed() throws Exception {
        String provider = "577903";
        int patient = 1900577930;
        em.createNativeQuery("INSERT INTO demographic (demographic_no,last_name,first_name,hin,sex) VALUES (?1,'FAKE-Hrm','Native',NULL,'F')")
                .setParameter(1, patient).executeUpdate();
        for (int index = 0; index < 4; index++) {
            int report = 1900577931 + index;
            em.createNativeQuery("INSERT INTO HRMDocument (id,reportFile,timeReceived) VALUES (?1,'FAKE.xml','2026-11-07 12:00:00')")
                    .setParameter(1, report).executeUpdate();
            em.createNativeQuery("INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (?1,?2,?3,?4)")
                    .setParameter(1, provider).setParameter(2, Integer.toString(report))
                    .setParameter(3, index % 2).setParameter(4, index % 2 == 0 ? 1 : 0).executeUpdate();
            if (index < 2) em.createNativeQuery("INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId) VALUES (?1,?2)")
                    .setParameter(1, Integer.toString(patient)).setParameter(2, Integer.toString(report)).executeUpdate();
        }
        // Duplicate provider associations must not inflate DISTINCT report counts.
        em.createNativeQuery("INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (?1,'1900577931',0,0)")
                .setParameter(1, provider).executeUpdate();
        for (String status : new String[]{"N", "A", ""}) {
            var filter = new InboxCategoryDao.Filter("FAKE-Hrm", "Native", "", true, true, provider, status, "all", null, null, OBSERVATION);
            int expected = status.isEmpty() ? 2 : 1;
            assertThat(inbox.count(filter, InboxCategoryDao.Operation.HRM_PATIENT, null).total()).as("patient status %s", status).isEqualTo(expected);
            assertThat(inbox.count(filter, InboxCategoryDao.Operation.HRM_UNMATCHED, null).total()).as("unmatched status %s", status).isEqualTo(expected);
        }
        var foreign = new InboxCategoryDao.Filter("", "", "", false, true, "577904", "", "all", null, null, OBSERVATION);
        assertThat(inbox.count(foreign, InboxCategoryDao.Operation.HRM_PATIENT, null).total()).isZero();
        assertThat(inbox.count(foreign, InboxCategoryDao.Operation.HRM_UNMATCHED, null).total()).isZero();
    }

    @Test
    void shouldCompareTheFiltersDateColumns_forLabAndDocumentBoundsOnPhysicalMariaDb() throws Exception {
        // Issue #5772: the date mode arrives in the filter. Each fixture item falls inside the window
        // by exactly one of its two dates, so each mode counts a different, known set.
        String provider = "577940";
        int patient = 1900577940;
        em.createNativeQuery("INSERT INTO demographic (demographic_no,last_name,first_name,hin,sex) VALUES (?1,'FAKE-Dates','Native',NULL,'F')")
                .setParameter(1, patient).executeUpdate();
        // Labs: 41 observed in January but received in March; 42 observed in December but received
        // in January; 43 is unmatched, observed in January and received in March.
        Object[][] labFixtures = {{1900577941, "2026-01-10", "2026-03-05 10:00:00", true},
                {1900577942, "2025-12-01", "2026-01-10 09:00:00", true},
                {1900577943, "2026-01-10", "2026-03-05 10:00:00", false}};
        for (Object[] lab : labFixtures) {
            em.createNativeQuery("INSERT INTO hl7TextInfo (lab_no,accessionNum,result_status,obr_date,report_status) VALUES (?1,?2,'A',?3,'F')")
                    .setParameter(1, lab[0]).setParameter(2, "FAKE-" + lab[0]).setParameter(3, lab[1]).executeUpdate();
            em.createNativeQuery("INSERT INTO hl7TextMessage (lab_id,fileUploadCheck_id,message,type,serviceName,created)"
                    + " VALUES (?1,0,'FAKE','FAKE','FAKE',?2)")
                    .setParameter(1, lab[0]).setParameter(2, lab[2]).executeUpdate();
            em.createNativeQuery("INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (?1,?2,'HL7','N')")
                    .setParameter(1, provider).setParameter(2, lab[0]).executeUpdate();
            if ((Boolean) lab[3]) em.createNativeQuery("INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (?1,?2,'HL7',NOW())")
                    .setParameter(1, patient).setParameter(2, lab[0]).executeUpdate();
        }
        // Documents: 44 observed in January, created in March; 45 observed in December, created in
        // January; 46 is unmatched, observed in March and created in January.
        Object[][] documentFixtures = {{1900577944, "2026-01-10", "2026-03-05 10:00:00", patient},
                {1900577945, "2025-12-01", "2026-01-10 09:00:00", patient},
                {1900577946, "2026-03-05", "2026-01-10 08:00:00", -1}};
        for (Object[] document : documentFixtures) {
            em.createNativeQuery("INSERT INTO document (document_no,docdesc,docfilename,doccreator,responsible,status,contenttype,"
                    + "restrictToProgram,abnormal,observationdate,contentdatetime) VALUES (?1,'FAKE-dates','fake.pdf',?2,?2,'A',"
                    + "'application/pdf',0,0,?3,?4)")
                    .setParameter(1, document[0]).setParameter(2, provider).setParameter(3, document[1])
                    .setParameter(4, document[2]).executeUpdate();
            em.createNativeQuery("INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',?1,?2,'A')")
                    .setParameter(1, document[3]).setParameter(2, document[0]).executeUpdate();
            em.createNativeQuery("INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (?1,?2,'DOC','N')")
                    .setParameter(1, provider).setParameter(2, document[0]).executeUpdate();
        }
        for (var mode : InboxCategoryDao.DateSearchType.values()) {
            boolean received = mode == InboxCategoryDao.DateSearchType.RECEIVED_CREATED;
            var filter = new InboxCategoryDao.Filter("", "", "", false, true, provider, "N", "all",
                    "2026-01-01", "2026-01-31", mode);
            var patientLabs = inbox.count(filter, InboxCategoryDao.Operation.LAB_PATIENT, null);
            assertThat(patientLabs.total()).as("%s patient labs", mode).isEqualTo(1);
            assertThat(inbox.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total())
                    .as("%s unmatched labs", mode).isEqualTo(received ? 0 : 1);
            assertThat(inbox.count(filter, InboxCategoryDao.Operation.ABNORMAL, null).total())
                    .as("%s abnormal labs", mode).isEqualTo(received ? 1 : 2);
            var patientDocuments = inbox.count(filter, InboxCategoryDao.Operation.DOCUMENT_PATIENT, null);
            assertThat(patientDocuments.total()).as("%s patient documents", mode).isEqualTo(1);
            assertThat(inbox.count(filter, InboxCategoryDao.Operation.DOCUMENT_UNMATCHED, null).total())
                    .as("%s unmatched documents", mode).isEqualTo(received ? 1 : 0);
            assertThat(patientLabs.patients()).containsExactly(
                    new InboxCategoryDao.PatientCount(patient, "Native", "FAKE-Dates", 1, 0, 0));
            assertThat(patientDocuments.patients()).containsExactly(
                    new InboxCategoryDao.PatientCount(patient, "Native", "FAKE-Dates", 0, 1, 0));
        }
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
        int patient = 1900569502;
        em.createNativeQuery("INSERT INTO demographic (demographic_no,last_name,first_name,hin,sex,date_joined)"
                + " VALUES (?1,'  Fixture  ','  Synthetic  ',NULL,'F','2026-11-05')")
                .setParameter(1, patient).executeUpdate();
        var definition = new DocumentTransferDao.Definition(
                "demographic", "demographic.demographic_no", "", "", "demographic.demographic_no",
                List.of("demographic.hin", "demographic.first_name", "demographic.date_joined"));
        var rows = transfers.read(definition, patient);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().item()).isEqualTo(Integer.toString(patient));
        assertThat(rows.getFirst().fields()).containsExactly("", "Synthetic", "2026-11-05");
        assertThat(rows.getFirst().columns()).contains(
                new DocumentTransferDao.Column("demographic.demographic_no", Integer.toString(patient)),
                new DocumentTransferDao.Column("demographic.last_name", "Fixture"),
                new DocumentTransferDao.Column("demographic.first_name", "Synthetic"),
                new DocumentTransferDao.Column("demographic.hin", ""),
                new DocumentTransferDao.Column("demographic.date_joined", "2026-11-05"));
        assertThat(rows.getFirst().columns()).extracting(DocumentTransferDao.Column::qualifiedName)
                .allSatisfy(name -> assertThat(name).startsWith("demographic.").doesNotContain(".fld"));
        assertThat(transfers.read(definition, patient + 1)).isEmpty();
    }
}
