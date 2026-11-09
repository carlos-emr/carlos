/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Live generator transaction and cross-entry-point lock contracts. @since 2026-11-09 */
@Tag("integration") @Tag("dao") @Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named="CARLOS_TEST_MARIADB_DAO", matches="true")
@EnabledIfEnvironmentVariable(named="CARLOS_TEST_MARIADB_SCHEMA", matches="flyway")
@EnabledIfEnvironmentVariable(named="CARLOS_TEST_MARIADB_PROVINCE", matches="bc")
@ContextConfiguration(inheritLocations=false, locations="classpath:test-bc-live-export-context.xml")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class LegacyBcTeleplanExportMariaDbIntegrationTest extends CarlosTestBase {
    @Autowired LegacyBcTeleplanExportService service;
    @Autowired TeleplanExportCoordinator coordinator;
    @Autowired DataSource dataSource;
    @TempDir Path directory;
    JdbcTemplate jdbc;
    String previousHome;
    String previousCenter;
    int wcbId;
    private static final int BILL=9556001;
    private static final int MASTER=9556002;

    @BeforeEach
    void seedCommittedSource() {
        jdbc=new JdbcTemplate(dataSource);
        CarlosProperties properties=CarlosProperties.getInstance();
        previousHome=properties.getProperty("HOME_DIR"); previousCenter=properties.getProperty("dataCenterId");
        properties.setProperty("HOME_DIR",directory.toString()); properties.setProperty("dataCenterId","00001");
        jdbc.update("INSERT INTO provider (provider_no,first_name,last_name,ohip_no,billing_no,provider_type,status,comments,lastUpdateDate) "
                + "VALUES ('BC60','FAKE','Export','91234','81234','doctor','1','',NOW())");
        jdbc.update("INSERT INTO billing (billing_no,provider_no,provider_ohip_no,demographic_no,demographic_name,billingtype,status,billing_date,visitdate) "
                + "VALUES (?,'BC60','91234',985560,'FAKE,Export','MSP','O','2026-11-01','2026-11-01')",BILL);
        jdbc.update("INSERT INTO billingmaster (billingmaster_no,billing_no,billingstatus,service_date,bill_amount,billing_unit,payee_no,practitioner_no,datacenter,billing_code,correspondence_code) "
                + "VALUES (?,?,'O','20261101','12.34','1','81234','91234','00001','00100','0')",MASTER,BILL);
    }

    @AfterEach
    void removeCommittedSource() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM billactivity WHERE creator='BC60'");
            jdbc.update("DELETE FROM wcb WHERE demographic_no=985560");
            jdbc.update("DELETE FROM billingservice WHERE description='FAKE-Wcb-6097'");
            jdbc.update("DELETE FROM billingmaster WHERE billingmaster_no IN (?,?)",MASTER,MASTER+10);
            jdbc.update("DELETE FROM billing WHERE billing_no IN (?,?)",BILL,BILL+10);
            jdbc.update("DELETE FROM provider WHERE provider_no IN ('BC60','BC61','BC62')");
        }
        var properties=CarlosProperties.getInstance();
        if(previousHome==null) properties.remove("HOME_DIR"); else properties.setProperty("HOME_DIR",previousHome);
        if(previousCenter==null) properties.remove("dataCenterId"); else properties.setProperty("dataCenterId",previousCenter);
    }

    private LegacyBcTeleplanExportService.Selection selection() {
        return new LegacyBcTeleplanExportService.Selection("91234,BC60","K","V03","");
    }

    @Test
    void shouldCommitCompleteFilesLogsAndStatuses_onceOnRetry() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM log_teleplantx",Integer.class);
        assertThat(service.generate(selection(),"BC60")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM billing WHERE billing_no=?",String.class,BILL)).isEqualTo("B");
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("B");
        assertThat(jdbc.queryForObject("SELECT creator FROM billactivity WHERE providerohipno='91234' AND creator='BC60'",String.class)).isEqualTo("BC60");
        assertThat(directory.resolve("HK81234.001")).isRegularFile();
        assertThat(Files.readString(directory.resolve("HK81234.001"))).contains("VS1","9556002");
        assertThat(directory.resolve("HK91234_001.htm")).isRegularFile();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM log_teleplantx",Integer.class)).isGreaterThan(before);
        assertThat(service.generate(selection(),"BC60")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE providerohipno='91234' AND creator='BC60'",Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRollbackLogsAndStatuses_whenAllBatchNamesAreOccupied() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM log_teleplantx",Integer.class);
        for (int batch=1; batch<=999; batch++) {
            Files.writeString(directory.resolve(String.format(java.util.Locale.ROOT,"HK81234.%03d",batch)),"owned collision fixture");
        }
        assertThatThrownBy(() -> service.generate(selection(),"BC60")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM billing WHERE billing_no=?",String.class,BILL)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE creator='BC60'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM log_teleplantx",Integer.class)).isEqualTo(before);
        assertThat(directory.resolve("HK81234.001")).hasContent("owned collision fixture");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"claim","html","both"})
    void shouldSkipPriorYearArtifacts_withoutOverwritingOrRequiringCurrentYearActivity(String occupied) throws Exception {
        if (!"html".equals(occupied)) Files.writeString(directory.resolve("HK81234.001"),"prior-year claim");
        if (!"claim".equals(occupied)) Files.writeString(directory.resolve("HK91234_001.htm"),"prior-year report");
        assertThat(service.generate(selection(),"BC60")).isEqualTo(1);
        assertThat(directory.resolve("HK81234.002")).isRegularFile();
        assertThat(directory.resolve("HK91234_002.htm")).isRegularFile();
        if (!"html".equals(occupied)) assertThat(directory.resolve("HK81234.001")).hasContent("prior-year claim");
        if (!"claim".equals(occupied)) assertThat(directory.resolve("HK91234_001.htm")).hasContent("prior-year report");
        assertThat(jdbc.queryForObject("SELECT batchcount FROM billactivity WHERE creator='BC60'",Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldReleaseDatabaseLock_afterGenerationFails() throws Exception {
        assertThatThrownBy(() -> coordinator.generate(() -> { throw new IllegalStateException("synthetic"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.generate(() -> 42)).isEqualTo(42);
    }

    @Test
    void shouldRefuseConcurrentGeneration_fromAnotherEntryPoint() throws Exception {
        coordinator.generate(() -> {
            assertThatThrownBy(() -> coordinator.generate(() -> 1)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Another Teleplan export");
            return null;
        });
    }
    @Test
    void shouldSeparateBillingGroups_whenAllProvidersAreGenerated() throws Exception {
        jdbc.update("INSERT INTO provider (provider_no,first_name,last_name,ohip_no,billing_no,provider_type,status,comments,lastUpdateDate) "
                + "VALUES ('BC61','FAKE','Second','91235','81235','doctor','1','',NOW()),('BC62','FAKE','Unrelated','bad','bad','doctor','1','',NOW())");
        jdbc.update("INSERT INTO billing (billing_no,provider_no,provider_ohip_no,demographic_no,demographic_name,billingtype,status,billing_date,visitdate) "
                + "VALUES (?,'BC61','91235',985561,'FAKE,Second','MSP','O','2026-11-01','2026-11-01')",BILL+10);
        jdbc.update("INSERT INTO billingmaster (billingmaster_no,billing_no,billingstatus,service_date,bill_amount,billing_unit,payee_no,practitioner_no,datacenter,billing_code,correspondence_code) "
                + "VALUES (?,?,'O','20261101','12.34','1','81235','91235','00001','00100','0')",MASTER+10,BILL+10);
        assertThat(service.generate(new LegacyBcTeleplanExportService.Selection("all","K","V03",""),"BC60")).isEqualTo(2);
        assertThat(Files.readString(directory.resolve("HK81234.001"))).startsWith("VS1").contains(String.valueOf(MASTER)).doesNotContain(String.valueOf(MASTER+10));
        assertThat(Files.readString(directory.resolve("HK81235.001"))).startsWith("VS1").contains(String.valueOf(MASTER+10)).doesNotContain(String.valueOf(MASTER));
    }

    private void makeWcbSource() {
        jdbc.update("UPDATE billing SET billingtype='WCB',status='W' WHERE billing_no=?",BILL);
        jdbc.update("INSERT INTO billingservice (service_code,description,value,billingservice_date,region) VALUES ('19950','FAKE-Wcb-6097','12.34','2026-01-01','BC')");
        var request=new org.springframework.mock.web.MockHttpServletRequest("POST","/billing/CA/BC/formwcb");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        request.getSession().setAttribute("user","BC60"); request.setParameter("saveAndClose","true");
        var login=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        io.github.carlos_emr.carlos.utility.LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),login);
        var previous=org.apache.struts2.ActionContext.getContext();
        org.apache.struts2.ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
        try {
            var action=new io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.WCBAction22Action();
            var security=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
            org.mockito.Mockito.when(security.hasPrivilege(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("_billing"),
                    org.mockito.ArgumentMatchers.eq("w"),org.mockito.ArgumentMatchers.isNull())).thenReturn(true);
            org.springframework.test.util.ReflectionTestUtils.setField(action,"securityInfoManager",security);
            // A browser submits empty text controls too; leave none of the form's String
            // fields null merely because this integration fixture binds without a browser.
            for (var setter : action.getClass().getMethods()) {
                if (setter.getName().startsWith("setW_") && java.util.Arrays.equals(setter.getParameterTypes(),new Class<?>[]{String.class})) {
                    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> setter.invoke(action,""));
                }
            }
            action.setDemographic_no("985560"); action.setW_duration("0"); action.setW_feeitem("19950");
            action.setW_icd9("250"); action.setW_wcbno("123"); action.setW_lname("FAKE"); action.setW_fname("Worker");
            action.setW_pracno("91234"); action.setW_payeeno("81234");
            action.setW_dob("1980-01-02"); action.setW_servicedate("2026-11-01"); action.setW_doi("2026-10-31");
            assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(action::execute)).isEqualTo("saveAndClose");
            wcbId=((Number)request.getAttribute("WCBFormId")).intValue();
        } finally {
            if(previous==null) org.apache.struts2.ActionContext.clear(); else previous.bind();
        }
        // BillingSaveBilling2Action stores the link on the claim, never on the clinical form.
        jdbc.update("UPDATE billingmaster SET billing_code='19950',billingstatus='W',wcb_id=? WHERE billingmaster_no=?",wcbId,MASTER);
        assertThat(jdbc.queryForObject("SELECT billing_no FROM wcb WHERE id=?",Integer.class,wcbId)).isZero();
        assertThat(jdbc.queryForObject("SELECT bill_amount FROM wcb WHERE id=?",String.class,wcbId)).isNull();
    }

    @Test
    void shouldUseTheActualClaimIdAndAmount_forWcbExport() throws Exception {
        makeWcbSource();
        assertThat(service.generate(selection(),"BC60")).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("HK81234.001"))).contains(String.valueOf(MASTER),"0001234");
        assertThat(jdbc.queryForObject("SELECT total FROM billactivity WHERE creator='BC60'",String.class)).isEqualTo("12.34");
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("B");
    }

    @Test
    void shouldReadTheSameProductionWcbLink_inBothExportEngines() throws Exception {
        makeWcbSource();
        // The modern engine hydrates primitive entity fields which the legacy scalar fixture omits.
        jdbc.update("UPDATE billing SET appointment_no=0,clinic_no=0 WHERE billing_no=?",BILL);
        jdbc.update("UPDATE billingmaster SET demographic_no=985560,appointment_no=0,paymentMethod=0,dx_code1='250' WHERE billingmaster_no=?",MASTER);
        jdbc.update("UPDATE wcb SET w_duration=0 WHERE id=?",wcbId);
        var writer=new io.github.carlos_emr.carlos.billings.ca.bc.MSP.TeleplanFileWriter();
        writer.setBillingmasterDAO(io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO.class));
        var patients=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.DemographicManager.class);
        var patient=new io.github.carlos_emr.carlos.commn.model.Demographic();
        patient.setFirstName("FAKE"); patient.setLastName("Worker"); patient.setSex("M"); patient.setHin("1234567890");
        org.mockito.Mockito.when(patients.getDemographic(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(patient);
        writer.setDemographicManager(patients);
        var provider=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.providers.data.ProviderData.class);
        org.mockito.Mockito.when(provider.getOhip_no()).thenReturn("91234");
        var submission=writer.getSubmission(null,true,new io.github.carlos_emr.carlos.providers.data.ProviderData[]{provider},"00001");
        assertThat(submission.getNumClaims()).isEqualTo(1);
        assertThat(submission.getMspFile()).contains(String.valueOf(MASTER),"0001234");
        assertThat(submission.getBigTotal()).isEqualByComparingTo("12.34");
        assertThat(service.generate(selection(),"BC60")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT billing_no FROM wcb WHERE id=?",Integer.class,wcbId)).isZero();
    }

    @Test
    void shouldKeepWcbClaimsPending_whenTheFormAndClaimFeeCodesDisagree() {
        makeWcbSource(); jdbc.update("UPDATE wcb SET w_feeitem='19951' WHERE id=?",wcbId);
        assertThatThrownBy(() -> service.generate(selection(),"BC60")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("W");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE creator='BC60'",Integer.class)).isZero();
        assertThat(directory.resolve("HK81234.001")).doesNotExist();
    }

    @Test
    void shouldNotTreatAnOlderFilenameAsCommitEvidence_whenNewExportRollsBack() {
        jdbc.update("INSERT INTO billactivity (monthCode,batchcount,groupno,providerohipno,ohipfilename,htmlfilename,creator,updatedatetime,status) "
                + "VALUES ('K',1,'81234','91234','HK81234.001','HK91234_001.htm','BC60',DATE_SUB(NOW(),INTERVAL 1 YEAR),'D')");
        jdbc.execute("CREATE TRIGGER FAKE_6097_export_failure BEFORE UPDATE ON billingmaster FOR EACH ROW "
                + "BEGIN IF NEW.billingmaster_no=" + MASTER + " AND NEW.billingstatus='B' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic export rollback'; END IF; END");
        try {
            assertThatThrownBy(() -> service.generate(selection(),"BC60")).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM billing WHERE billing_no=?",String.class,BILL)).isEqualTo("O");
            assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("O");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE creator='BC60'",Integer.class)).isEqualTo(1);
            assertThat(directory.resolve("HK81234.001")).doesNotExist();
            assertThat(directory.resolve("HK91234_001.htm")).doesNotExist();
        } finally { jdbc.execute("DROP TRIGGER FAKE_6097_export_failure"); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings={"bad", "12.345", "100000.00", "-1.00", "1e3"})
    void shouldRefuseUnrepresentableAmounts_withoutPublishingOrMarkingBilled(String amount) {
        jdbc.update("UPDATE billingmaster SET bill_amount=? WHERE billingmaster_no=?", amount, MASTER);
        assertThatThrownBy(() -> service.generate(selection(),"BC60")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE creator='BC60'",Integer.class)).isZero();
        assertThat(directory.resolve("HK81234.001")).doesNotExist();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void shouldEncodeDecimalDollarsAsExactCents_andKeepTheActivityTotalConsistent(boolean wcb) throws Exception {
        if (wcb) {
            makeWcbSource();
            jdbc.update("UPDATE wcb SET bill_amount='12.3' WHERE id=?",wcbId);
        }
        jdbc.update("UPDATE billingmaster SET bill_amount='12.3' WHERE billingmaster_no=?",MASTER);
        assertThat(service.generate(selection(),"BC60")).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("HK81234.001"))).contains("0001230");
        assertThat(jdbc.queryForObject("SELECT total FROM billactivity WHERE creator='BC60'",String.class)).isEqualTo("12.30");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"bad", "12.345", "100000.00"})
    void shouldListInvalidClaimsAndKeepSimulating_withoutMutatingBilling(String amount) {
        jdbc.update("UPDATE billingmaster SET bill_amount=? WHERE billingmaster_no=?",amount,MASTER);
        var extract = new io.github.carlos_emr.carlos.billings.ca.bc.MSP.ExtractBean();
        extract.seteFlag("0"); extract.setProviderNo("91234");
        extract.dbQuery();
        assertThat(extract.getHtmlCode()).contains("Claim cannot be exported",String.valueOf(MASTER));
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("O");
        assertThat(extract.getMasterIdsToMark()).isEmpty();
    }

    @Test
    void shouldListWcbLinkProblemsInSimulation_withoutAbortingThePreview() {
        makeWcbSource();
        jdbc.update("UPDATE billingmaster SET wcb_id=0 WHERE billingmaster_no=?",MASTER);
        var extract = new io.github.carlos_emr.carlos.billings.ca.bc.MSP.ExtractBean();
        extract.seteFlag("0"); extract.setProviderNo("91234");
        extract.dbQuery();
        assertThat(extract.getHtmlCode()).contains("WCB invoice " + BILL,"cannot be exported");
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("W");
    }

    @Test
    void shouldExportAnAdditionalWcbFee_fromTheClaimWithoutDuplicatingFormNotes() throws Exception {
        makeWcbSource();
        jdbc.update("UPDATE wcb SET w_extrafeeitem='19951',formNeeded=1 WHERE id=?",wcbId);
        jdbc.update("UPDATE billingmaster SET billing_code='19951',bill_amount='7.25' WHERE billingmaster_no=?",MASTER);
        assertThat(service.generate(selection(),"BC60")).isEqualTo(1);
        String file=Files.readString(directory.resolve("HK81234.001"));
        assertThat(file).contains("19951","0000725").doesNotContain("N01");
        assertThat(jdbc.queryForObject("SELECT total FROM billactivity WHERE creator='BC60'",String.class)).isEqualTo("7.25");
    }

}
