/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.RemittanceRow;
import java.nio.file.Path;
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
                "20261016", "00001", "FAKE-5546,BC", 985546, "000123M", null, "AB", "", "", "", "", "0000042");
    }

    @Test
    void shouldSupplyEveryTemplateField_withItsDeclaredTypeAndNulls() throws Exception {
        RemittanceRow row = sampleRow();
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
            if ("t_exp1".equals(field.getName())) assertThat(value).isNull();
            else assertThat(value).isInstanceOf(field.getValueClass());
            if ("demographic_no".equals(field.getName())) assertThat(value).isEqualTo(985546);
            if ("t_paidamt".equals(field.getName())) assertThat(value).isEqualTo("000123M");
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
