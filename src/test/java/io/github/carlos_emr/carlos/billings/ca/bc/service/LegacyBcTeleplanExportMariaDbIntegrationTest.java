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
            jdbc.update("DELETE FROM wcb WHERE billing_no IN (?,?)",BILL,BILL+10);
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
    void shouldRollbackLogsAndStatuses_whenOutputCannotBeCreated() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM log_teleplantx",Integer.class);
        Files.writeString(directory.resolve("HK81234.001"),"owned collision fixture");
        assertThatThrownBy(() -> service.generate(selection(),"BC60")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM billing WHERE billing_no=?",String.class,BILL)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE creator='BC60'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM log_teleplantx",Integer.class)).isEqualTo(before);
        assertThat(directory.resolve("HK81234.001")).hasContent("owned collision fixture");
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
        jdbc.update("UPDATE billingmaster SET billing_code='19950',billingstatus='W' WHERE billingmaster_no=?",MASTER);
        jdbc.update("INSERT INTO billingservice (service_code,description,value,billingservice_date,region) VALUES ('19950','FAKE-Wcb-6097','12.34','2026-01-01','BC')");
        jdbc.update("INSERT INTO wcb (billing_no,demographic_no,provider_no,bill_amount,w_feeitem,w_icd9,w_wcbno,w_lname,w_fname,w_pracno,w_payeeno,formNeeded,status,formCreated,formEdited,w_dob,w_servicedate,w_doi) "
                + "VALUES (?,985560,'0','12.34','19950','250','123','FAKE','Worker','91234','81234',0,'O',NOW(),NOW(),'1980-01-02','2026-11-01','2026-10-31')",BILL);
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
    void shouldKeepWcbClaimsPending_whenTheFormAndClaimAmountsDisagree() {
        makeWcbSource(); jdbc.update("UPDATE wcb SET bill_amount='11.00' WHERE billing_no=?",BILL);
        assertThatThrownBy(() -> service.generate(selection(),"BC60")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT billingstatus FROM billingmaster WHERE billingmaster_no=?",String.class,MASTER)).isEqualTo("W");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billactivity WHERE creator='BC60'",Integer.class)).isZero();
        assertThat(directory.resolve("HK81234.001")).doesNotExist();
    }

}
