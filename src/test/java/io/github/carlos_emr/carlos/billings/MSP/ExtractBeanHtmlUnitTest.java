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
package io.github.carlos_emr.carlos.billings.MSP;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import java.sql.ResultSet;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Issue #3950: the legacy BC extract ({@code billings.MSP.ExtractBean}) builds the same report HTML
 * as the current BC bean. Drives {@link ExtractBean#dbQuery()} in dry-run mode over a mocked JDBC
 * extract and checks every record-derived value in the header, both row shapes and the footer is
 * encoded.
 */
@DisplayName("ExtractBean (legacy MSP copy) report HTML encoding")
@Tag("unit")
@Tag("billing")
@Tag("security")
class ExtractBeanHtmlUnitTest extends CarlosUnitTestBase {

    private static final String SCRIPT_NAME = "<script>alert(1)</script>";

    @BeforeEach
    void registerExtractDaos() {
        createAndRegisterMock(LogTeleplanTxDao.class);
        createAndRegisterMock(BillingDao.class);
        createAndRegisterMock(BillingmasterDAO.class);
    }

    private static ResultSet rows(int count, Map<String, String> values) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Boolean[] more = new Boolean[count];
        java.util.Arrays.fill(more, Boolean.TRUE);
        when(rs.next()).thenReturn(count > 0, append(more, Boolean.FALSE));
        // Fixed-width claim columns the test does not care about read as zero-filled.
        when(rs.getString(anyString())).thenAnswer(call -> values.getOrDefault(call.getArgument(0, String.class), "0"));
        return rs;
    }

    private static Boolean[] append(Boolean[] head, Boolean last) {
        Boolean[] out = java.util.Arrays.copyOfRange(head, 1, head.length + 1);
        out[out.length - 1] = last;
        return out;
    }

    @Test
    void shouldEncodeRecordValues_whenBuildingDryRunReport() throws Exception {
        ResultSet billing = rows(1, Map.of("billing_no", "1001", "demographic_name", SCRIPT_NAME));
        ResultSet claims = rows(2, Map.of(
                "billingmaster_no", "42", "phn", "<b>98</b>", "billing_code", "<u>01</u>",
                "bill_amount", "23.00", "service_date", "20260612", "dx_code1", "<i>"));

        try (MockedConstruction<dbExtract> extracts = mockConstruction(dbExtract.class, (extract, context) -> {
            when(extract.executeQuery(anyString(), any(Object[].class))).thenReturn(billing);
            when(extract.executeQuery2(anyString(), any(Object[].class))).thenReturn(claims);
        })) {
            ExtractBean bean = new ExtractBean();
            bean.seteFlag("0");
            bean.setProviderNo("<img src=x>");
            bean.dbQuery();

            String html = bean.getHtmlCode();
            assertThat(extracts.constructed()).hasSize(1);
            assertThat(html)
                    .contains("Billing Invoice for Billing No." + SafeEncode.forHtmlContent("<img src=x>"))
                    .contains(SafeEncode.forHtmlContent(SCRIPT_NAME))
                    .contains(SafeEncode.forHtmlContent("<b>98</b>"))
                    .contains(SafeEncode.forHtmlContent("<u>01</u>"))
                    .contains("adjustBill.jsp?billingmaster_no=0000042")
                    .doesNotContain("<script>")
                    .doesNotContain("<img")
                    .doesNotContain("<b>")
                    .doesNotContain("<u>")
                    .doesNotContain("<i>");
            // Second claim on the same invoice uses the continuation row: blank patient columns.
            assertThat(html).contains("<tr><td class='bodytext'></td><td class='bodytext'></td>");
            assertThat(html).endsWith("</table></body></html>");
        }
    }
}
