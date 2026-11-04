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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Billing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for issue #3950: the BC billing simulation / Teleplan report HTML built by
 * {@link ExtractBean} is emitted raw by {@code billingSim.jsp}, so claim-record values must be encoded
 * where the rows are assembled.
 */
@DisplayName("ExtractBean (BC) report HTML encoding")
@Tag("unit")
@Tag("billing")
@Tag("security")
class ExtractBeanHtmlUnitTest extends CarlosUnitTestBase {

    private static final String SCRIPT_NAME = "<script>alert('xss')</script>";
    private BillingDao billingDao;
    @TempDir private Path outputDirectory;

    @BeforeEach
    void registerExtractDaos() {
        // ExtractBean resolves these in field initializers; the HTML builders never touch them.
        createAndRegisterMock(LogTeleplanTxDao.class);
        billingDao = createAndRegisterMock(BillingDao.class);
        createAndRegisterMock(BillingmasterDAO.class);
    }

    @Test
    void shouldWriteReportOnce_afterCallerAssignsHtmlFilename() throws Exception {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("HOME_DIR")).thenReturn(outputDirectory.toString());
        when(billingDao.getMyMagicBillings()).thenReturn(List.of(new Billing()));
        try (MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            ExtractBean bean = spy(new ExtractBean());
            bean.seteFlag("1");
            bean.setVSFlag(1);
            bean.setProviderNo("91234");

            bean.dbQuery();

            verify(bean, never()).writeHtml(any());
            String html = bean.getHtmlCode();
            assertThat(html).contains("Billing Invoice for Billing No.91234");
            bean.setHtmlFilename("H191234_001.htm");
            bean.writeHtml(html);
            verify(bean, times(1)).writeHtml(html);
            assertThat(Files.readString(outputDirectory.resolve("H191234_001.htm")))
                    .isEqualTo(html + System.lineSeparator());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void shouldRejectMissingHtmlFilename_whenPublishingAnExport(String filename) {
        ExtractBean bean = new ExtractBean();
        bean.seteFlag("1");
        bean.setHtmlFilename(filename);
        assertThatThrownBy(() -> bean.writeHtml("<html/>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HTML filename must be set before writing a Teleplan report");
    }

    @Test
    void shouldKeepPreviewInMemory_withoutAnHtmlFilename() {
        ExtractBean bean = spy(new ExtractBean());
        bean.seteFlag("0");
        when(billingDao.getMyMagicBillings()).thenReturn(List.of(new Billing()));
        bean.dbQuery();
        verify(bean, never()).writeHtml(any());
        bean.writeHtml(bean.getHtmlCode());
    }

    @Test
    void shouldEncodeClaimFields_whenPatientNameContainsScript() {
        String html = new ExtractBean().htmlLine("42", "<i>INV</i>", SCRIPT_NAME, "<b>9876543210</b>",
                "20260612", "<u>00100</u>", "<s>23.00</s>", "<a>", "\"x\"", "&dx");

        assertThat(html)
                .contains(SafeEncode.forHtmlContent(SCRIPT_NAME))
                .contains(SafeEncode.forHtmlContent("<i>INV</i>"))
                .contains(SafeEncode.forHtmlContent("<b>9876543210</b>"))
                .contains(SafeEncode.forHtmlContent("<u>00100</u>"))
                .contains(SafeEncode.forHtmlContent("<s>23.00</s>"))
                .doesNotContain("<script>")
                .doesNotContain("<i>INV</i>")
                .doesNotContain("<b>9876543210</b>")
                .doesNotContain("<u>00100</u>")
                .doesNotContain("<s>23.00</s>")
                .doesNotContain("<a>");
    }

    @Test
    void shouldKeepAdjustmentLinkInsideJsString_whenBillingMasterNoAttemptsBreakout() {
        String html = new ExtractBean().htmlLine("1');alert(1);//", "INV", "Doe,Jane", "9876543210",
                "20260612", "00100", "23.00", "250", "", "");

        assertThat(html)
                .contains("openBrWindow('reprocessBill?billingmaster_no=")
                .doesNotContain("');alert(1)")
                .doesNotContain("1');");
    }

    @Test
    void shouldPreserveReportTableShape_forPlainClaimValues() {
        String html = new ExtractBean().htmlLine("42", "1001", "DOE,JANE", "9876543210", "20260612",
                "00100", "23.00", "250", "", "");

        assertThat(html)
                .startsWith("<tr>")
                .contains("openBrWindow('reprocessBill?billingmaster_no=0000042'")
                .contains(">1001</a>")
                .contains("<td class='bodytext'>DOE,JANE</td>")
                .contains("<td class='bodytext'>0000042</td>");
        assertThat(html.split("<td", -1)).hasSize(12);
    }

    @Test
    void shouldEncodeProviderNumber_whenBuildingReportHeader() {
        String header = new ExtractBean().htmlContentHeaderGen("<img src=x onerror=alert(1)>", "20260612", "");

        assertThat(header)
                .contains("Billing Invoice for Billing No." + SafeEncode.forHtmlContent("<img src=x onerror=alert(1)>"))
                .doesNotContain("<img");
    }
}
