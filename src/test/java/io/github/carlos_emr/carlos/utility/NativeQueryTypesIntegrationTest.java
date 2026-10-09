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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.PMmodule.dao.WaitlistDao;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriaBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriasBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.VacancyDisplayBO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.PrivateBillTransactionsDAO;
import io.github.carlos_emr.carlos.commn.dao.EFormReportToolDao;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Hibernate native SQL results flow through the affected legacy DAO consumers. */
@Tag("integration")
class NativeQueryTypesIntegrationTest extends CarlosTestBase {
    @Autowired private WaitlistDao waitlist;
    @Autowired private PrivateBillTransactionsDAO privatePayments;
    @Autowired private EFormReportToolDao reportTools;
    private static final Timestamp CREATED = Timestamp.valueOf("2026-03-04 12:34:56");

    @Test
    void shouldReadCount_whenReportTableContainsRows() {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery("CREATE TABLE IF NOT EXISTS ERT_native_types_test (id INT)").executeUpdate();
            session.createNativeQuery("DELETE FROM ERT_native_types_test").executeUpdate();
            session.createNativeQuery("INSERT INTO ERT_native_types_test VALUES (1), (2), (3)").executeUpdate();
            return null;
        });
        var tool = new EFormReportTool();
        tool.setTableName("ERT_native_types_test");
        assertThat(reportTools.getNumRecords(tool)).isEqualTo(3);
        reportTools.deleteAllData(tool);
        assertThat(reportTools.getNumRecords(tool)).isZero();
    }

    @Test
    void shouldReadCreationDate_whenPrivatePaymentExists() {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery("INSERT INTO billing_payment_type (id, payment_type) VALUES (41401, 'Owned Cash')").executeUpdate();
            session.createNativeQuery("INSERT INTO billing_private_transactions "
                    + "(billingmaster_no, amount_received, creation_date, payment_type_id) "
                    + "VALUES (41401, 12.50, :created, 41401)").setParameter("created", CREATED).executeUpdate();
            return null;
        });
        var rows = privatePayments.getPrivateBillTransactions("41401");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCreation_date()).isEqualTo(CREATED);
        assertThat(rows.getFirst().getAmount_received()).isEqualTo(12.50d);
        assertThat(rows.getFirst().getPayment_type_desc()).isEqualTo("Owned Cash");
    }

    @Test
    void shouldReadAllVacancyLists_whenNativeDateTimeReturned() {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery("INSERT INTO program (id, name) VALUES (41401, 'Native test')").executeUpdate();
            session.createNativeQuery("INSERT INTO vacancy_template (TEMPLATE_ID, NAME, WL_PROGRAM_ID) "
                    + "VALUES (41401, 'Native template', 41401)").executeUpdate();
            session.createNativeQuery("INSERT INTO vacancy (id, templateId, wlProgramId, vacancyName, status, dateCreated) "
                    + "VALUES (41401, 41401, 41401, 'Native vacancy', 'active', :created)")
                    .setParameter("created", CREATED).executeUpdate();
            return null;
        });
        var lists = List.of(waitlist.listDisplayVacanciesForWaitListProgram(41401),
                waitlist.listDisplayVacanciesForAllWaitListPrograms(), waitlist.getDisplayVacanciesForAgencyProgram(41401),
                List.of(waitlist.getDisplayVacancy(41401)), waitlist.listNoOfVacanciesForWaitListProgram(),
                waitlist.listVacanciesForWaitListProgram());
        for (List<VacancyDisplayBO> rows : lists) {
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getCreated()).isEqualTo(CREATED);
        }
    }

    @Test
    void shouldReadMatchingEforms_whenNativeDateReturned() {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery("INSERT INTO eform_data (fdid, demographic_no, form_date, status, showLatestFormOnly, patient_independent) "
                    + "VALUES (41401, 41401, DATE '2026-03-04', TRUE, FALSE, FALSE), (41402, 41401, DATE '2026-03-05', TRUE, FALSE, FALSE)").executeUpdate();
            return null;
        });
        var criteria = new CriteriasBO();
        criteria.crits = new CriteriaBO[0];
        var rows = waitlist.searchForMatchingEforms(criteria);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(io.github.carlos_emr.carlos.commn.model.EFormData::getId)
                .containsExactlyInAnyOrder(41401, 41402);
        assertThat(rows).extracting(io.github.carlos_emr.carlos.commn.model.EFormData::getFormDate)
                .containsExactlyInAnyOrder(java.sql.Date.valueOf("2026-03-04"), java.sql.Date.valueOf("2026-03-05"));
    }
    @Test
    void shouldReadWcbCorrection_whenNativeDatesAndDurationCodeReturned() throws Exception {
        var patient = new io.github.carlos_emr.carlos.commn.model.Demographic();
        io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator.generateTestDataForModelClass(patient);
        var master = new io.github.carlos_emr.carlos.entities.Billingmaster();
        io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator.generateTestDataForModelClass(master);
        var form = hibernateTemplate.execute(session -> {
            session.persist(patient);
            session.flush();
            master.setDemographicNo(patient.getDemographicNo());
            master.setBillingNo(41403);
            master.setBillingstatus("O");
            master.setServiceDate("20260304");
            session.persist(master);
            var wcb = new io.github.carlos_emr.carlos.billing.CA.BC.model.Wcb();
            wcb.setBillingNo(41403);
            wcb.setDemographicNo(patient.getDemographicNo());
            wcb.setDuration(9);
            wcb.setDoi(java.sql.Date.valueOf("2026-03-01"));
            wcb.setServiceDate(java.sql.Date.valueOf("2026-03-04"));
            wcb.setWorkDate(java.sql.Date.valueOf("2026-03-05"));
            wcb.setEstimateDate(java.sql.Date.valueOf("2026-03-06"));
            wcb.setCapability("Y");
            session.persist(wcb);
            session.flush();
            // Use the same real persistence context to read this transaction's owned fixtures.
            var dao = new io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO();
            org.springframework.test.util.ReflectionTestUtils.setField(dao, "entityManager", session);
            return new io.github.carlos_emr.carlos.billings.ca.bc.administration.TeleplanCorrectionFormWCB(
                    dao.select_user_bill_report_wcb(master.getBillingmasterNo()));
        });
        assertThat(form.getW_doi()).isEqualTo("2026-03-01");
        assertThat(form.getW_servicedate()).isEqualTo("2026-03-04");
        assertThat(form.getW_workdate()).isEqualTo("2026-03-05");
        assertThat(form.getW_estimatedate()).isEqualTo("2026-03-06");
        assertThat(form.getW_duration()).isEqualTo("9");
        assertThat(form.getW_capability()).isEqualTo("Y");
        assertThat(form.getStatus()).isEqualTo("O");
    }

}
