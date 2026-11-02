/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Tag("report")
class BcRemittanceExportDaoUnitTest {
    private Connection database() throws Exception {
        var connection = DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL");
        try (var st = connection.createStatement()) {
            st.execute("CREATE TABLE teleplanS21(s21_id int, status varchar, t_payment varchar, t_payeeno varchar, t_payeename varchar, t_amtbilled varchar, t_amtpaid varchar, t_cheque varchar)");
            st.execute("INSERT INTO teleplanS21 VALUES(1,'A','20261001','001','Synthetic Clinic','123450','12345J','12345}')");
            st.execute("INSERT INTO teleplanS21 VALUES(2,'A','20261002','002','Other Clinic','100','100','100')");
            st.execute("CREATE TABLE teleplanS22(s22_id int, s21_id int, t_s22type varchar, t_payment varchar, t_payeeno varchar, t_practitionerno varchar, t_practitionername varchar, t_amtbilled varchar, t_amtpaid varchar)");
            st.execute("INSERT INTO teleplanS22 VALUES(1,1,'A','20261001','001','003','Synthetic Doctor','123450','12345J'),(2,1,'D','20261001','001','004','Deleted','100','100')");
            st.execute("CREATE TABLE teleplanS23(s23_id int, s21_id int, t_s23type varchar, t_aji varchar, t_ajc varchar, t_calcmethod varchar, t_rpercent varchar, t_opercent varchar, t_gamount varchar, t_ramount varchar, t_oamount varchar, t_balancefwd varchar, t_adjmade varchar, t_adjoutstanding varchar)");
            st.execute("INSERT INTO teleplanS23 VALUES(1,1,'S23','003','A','1','100','20','100','200','300','400','500','600')");
            st.execute("CREATE TABLE teleplan_adj_codes(adj_code varchar, adj_desc varchar)");
            st.execute("INSERT INTO teleplan_adj_codes VALUES('A','Synthetic adjustment')");
            st.execute("CREATE TABLE teleplanS25(s25_id int, s21_id int, t_practitionerno varchar, t_linecode varchar, t_message varchar)");
            st.execute("INSERT INTO teleplanS25 VALUES(1,1,'003','001','=synthetic,Élodie'),(2,2,'004','001','Excluded')");
            st.execute("CREATE TABLE billing(billing_no int, demographic_name varchar, demographic_no int)");
            st.execute("INSERT INTO billing VALUES(10,'Synthetic Élodie',1)");
            st.execute("CREATE TABLE billingmaster(billingmaster_no int, billing_no int, service_date varchar, billing_code varchar)");
            st.execute("INSERT INTO billingmaster VALUES(20,10,'20261001','00100')");
            st.execute("CREATE TABLE provider(ohip_no varchar, first_name varchar, last_name varchar)");
            st.execute("INSERT INTO provider VALUES('003','Synthetic','Doctor')");
            st.execute("CREATE TABLE teleplanS00(s21_id int,t_officeno int,t_practitionerno varchar,t_payeeno varchar,t_s00type varchar,t_payment varchar,t_datacenter varchar,t_paidamt varchar,t_exp1 varchar,t_dataseq varchar)");
            st.execute("INSERT INTO teleplanS00 VALUES(1,20,'003','001','S00','20261002','001','12345J','AA','0001')");
        }
        return connection;
    }

    @Test
    void shouldReadAllSummarySections_andBindRemittanceId() throws Exception {
        try (var db = database()) {
            var output = new ByteArrayOutputStream();
            new BcRemittanceExportDao(db).writeSummary(output, "1");
            var rows = CSVFormat.DEFAULT.parse(new StringReader(output.toString(java.nio.charset.StandardCharsets.UTF_8))).getRecords();
            assertThat(rows).hasSize(5);
            assertThat(rows).allSatisfy(r -> assertThat(r.size()).isEqualTo(22));
            assertThat(rows.get(1)).contains("Remittance", "01-Oct-26", "1234.50", "-1234.51", "-1234.50");
            assertThat(rows.get(2)).contains("Practitioner", "Synthetic Doctor");
            assertThat(rows.get(3)).contains("Adjustment", "Synthetic adjustment", "1.00", "2.00", "3.00", "4.00", "5.00", "6.00");
            assertThat(rows.get(4)).contains("Message", "\t=synthetic,Élodie");
            assertThat(output.toString()).doesNotContain("Deleted", "Excluded", "Other Clinic");
            assertThatThrownBy(() -> new BcRemittanceExportDao(db).writeSummary(new ByteArrayOutputStream(), "1 OR 1=1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void shouldReadBilledRows_withBoundPayeeAndStableLocale() throws Exception {
        Locale old = Locale.getDefault();
        try (var db = database()) {
            String expected = null;
            for (var locale : List.of(Locale.ENGLISH, Locale.US, Locale.CANADA)) {
                Locale.setDefault(locale);
                var output = new ByteArrayOutputStream();
                new BcRemittanceExportDao(db).writeBilled(output, "001", "1");
                if (expected == null) expected = output.toString();
                assertThat(output.toString()).isEqualTo(expected).contains("-1234.51", "Synthetic Élodie(1)", "02-Oct-26");
            }
            var output = new ByteArrayOutputStream();
            new BcRemittanceExportDao(db).writeBilled(output, "001' OR '1'='1", "1");
            assertThat(CSVFormat.DEFAULT.parse(new StringReader(output.toString())).getRecords()).hasSize(1);
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    void shouldDecodeTeleplanOverpunch_withoutLoggingOrRoundingInput() {
        assertThat(TeleplanAmount.decode("12345J")).isEqualTo("-1234.51");
        assertThat(TeleplanAmount.decode("12345}")).isEqualTo("-1234.50");
        assertThatThrownBy(() -> TeleplanAmount.decode("invalid")).isInstanceOf(NumberFormatException.class);
    }
}
