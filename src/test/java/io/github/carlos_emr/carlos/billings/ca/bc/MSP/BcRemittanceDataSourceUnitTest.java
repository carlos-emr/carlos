/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.RemittanceRow;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The projection adapter must satisfy the actual, unchanged Jasper field contract. */
@Tag("unit")
@Tag("billing")
class BcRemittanceDataSourceUnitTest {
    private static RemittanceRow sampleRow() {
        return new RemittanceRow("00100", "FAKE", "Provider", "91234", "S00", "20261015",
                "20261016", "00001", "FAKE-5546,BC", 985546, "000123M", null, "EX2", "EX3", "EX4", "EX5", "EX6", "0000042");
    }

    @Test
    void shouldSupplyEveryTemplateField_withItsDeclaredTypeAndNulls() throws Exception {
        RemittanceRow row = sampleRow();
        Map<String, Object> expected = new HashMap<>();
        expected.put("billing_code", "00100");
        expected.put("first_name", "FAKE");
        expected.put("last_name", "Provider");
        expected.put("t_practitionerno", "91234");
        expected.put("t_s00type", "S00");
        expected.put("t_servicedate", "20261015");
        expected.put("t_payment", "20261016");
        expected.put("t_datacenter", "00001");
        expected.put("demographic_name", "FAKE-5546,BC");
        expected.put("demographic_no", 985546);
        expected.put("t_paidamt", "000123M");
        expected.put("t_exp1", null);
        expected.put("t_exp2", "EX2");
        expected.put("t_exp3", "EX3");
        expected.put("t_exp4", "EX4");
        expected.put("t_exp5", "EX5");
        expected.put("t_exp6", "EX6");
        expected.put("t_dataseq", "0000042");
        BcRemittanceDataSource source = new BcRemittanceDataSource(java.util.List.of(row));
        assertThat(source.next()).isTrue();
        var fields = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(Path.of(
                "src/main/resources/oscar/oscarBilling/ca/bc/reports/pdf_rep_msprem.jrxml").toFile())
                .getElementsByTagName("field");
        assertThat(fields.getLength()).isEqualTo(18);
        for (int i = 0; i < fields.getLength(); i++) {
            var attributes = fields.item(i).getAttributes();
            JRDesignField field = new JRDesignField();
            field.setName(attributes.getNamedItem("name").getNodeValue());
            field.setValueClass(Class.forName(attributes.getNamedItem("class").getNodeValue()));
            Object value = source.getFieldValue(field);
            assertThat(expected).containsKey(field.getName());
            assertThat(value).as(field.getName()).isEqualTo(expected.get(field.getName()));
            if (value != null) assertThat(value).isInstanceOf(field.getValueClass());
        }
        assertThat(source.next()).isFalse();
    }

    @Test
    void shouldRejectUnknownFields_insteadOfSilentlyRenderingMissingData() throws Exception {
        BcRemittanceDataSource source = new BcRemittanceDataSource(java.util.List.of(sampleRow()));
        JRDesignField field = new JRDesignField();
        field.setName("unknown");
        assertThatThrownBy(() -> source.getFieldValue(field)).isInstanceOf(JRException.class);
        assertThat(source.next()).isTrue();
        assertThatThrownBy(() -> source.getFieldValue(field)).isInstanceOf(JRException.class);
        assertThat(source.next()).isFalse();
    }
}
