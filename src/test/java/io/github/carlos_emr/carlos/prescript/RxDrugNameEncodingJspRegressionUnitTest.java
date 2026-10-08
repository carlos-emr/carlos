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
package io.github.carlos_emr.carlos.prescript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Rx views to "store the drug name raw, encode it once at output" (#3952).
 *
 * <p>The staging card ({@code prescribe.jsp}) escaped quotes by hand and re-decoded the name from
 * ISO-8859-1 as UTF-8 before encoding it again for output, so a name such as {@code CHILDREN'S ...}
 * showed, saved and printed as {@code CHILDREN\'S ...} and accented names lost their accents. The
 * views that show the saved name printed it raw. These rows keep both halves from coming back.
 *
 * @since 2026-10-08
 */
@DisplayName("Rx drug name output encoding JSP regressions")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxDrugNameEncodingJspRegressionUnitTest {

    private static final Path PRESCRIBE_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/prescribe.jsp");
    private static final Path PREVIEW_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/Preview2.jsp");

    @Test
    @DisplayName("should hand the staging card the raw name, without hand escaping or charset re-decoding")
    void shouldKeepDrugNameRaw_beforeOutputEncoding() throws IOException {
        String jsp = Files.readString(PRESCRIBE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .doesNotContain("drugName=drugName.replace(")
                .doesNotContain("getBytes(\"ISO-8859-1\")")
                .doesNotContain("decodeURIComponent(encodeURIComponent('<carlos:encode value='<%= drugName %>'")
                .contains(".value='<carlos:encode value='<%= drugName %>' context=\"javaScriptBlock\"/>';")
                .contains("value=\"<carlos:encode value='<%= drugName %>' context=\"htmlAttribute\"/>\"")
                .contains("addFav('<%=rand%>','<carlos:encode value='<%= drugName %>' context=\"javaScriptAttribute\"/>')");
    }

    @Test
    @DisplayName("should encode the drug id the card writes into its script block")
    void shouldEncodeGcnCode_inScriptBlock() throws IOException {
        String jsp = Files.readString(PRESCRIBE_JSP, StandardCharsets.UTF_8);

        // createNewRx no longer Java-escapes the drug id it stores, so the page encodes it here.
        assertThat(jsp)
                .doesNotContain("var gcn_val=\"<%=gcnCode%>\";")
                .contains("var gcn_val=\"<carlos:encode value='<%= gcnCode %>' context=\"javaScriptBlock\"/>\";");
    }

    @Test
    @DisplayName("should render the print preview outline through the encoding helper")
    void shouldRenderPreviewOutline_throughEncodingHelper() throws IOException {
        String jsp = Files.readString(PREVIEW_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("RxPrescriptionData.fullOutLineToHtml(rawOutLine)")
                .doesNotContain("rx.getFullOutLine().replaceAll(\";\", \"<br />\")");
    }

    static Stream<Arguments> savedNameSinks() {
        return Stream.of(
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= StringUtils.trimToEmpty(drug.getDrugName()) %>",
                        "<%= SafeEncode.forHtmlContent(StringUtils.trimToEmpty(drug.getDrugName())) %>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getBrandName()%>", "<%= SafeEncode.forHtmlContent(drug.getBrandName())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getFullOutLine()%>", "<%= SafeEncode.forHtmlContent(drug.getFullOutLine())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getAuditString()%>", "<%= SafeEncode.forHtmlContent(drug.getAuditString())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getGcnSeqNo()%>", "<%= SafeEncode.forHtmlContent(drug.getGcnSeqNo())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getGenericName()%>", "<%= SafeEncode.forHtmlContent(drug.getGenericName())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp",
                        "<%=drug.getRxDisplay()%>", "<carlos:encode value='<%= drug.getRxDisplay() %>'/>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/prescribe.jsp",
                        "value=\"<%=comment%>\"", "value=\"<carlos:encode value='<%= comment %>' context=\"htmlAttribute\"/>\""),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/ListDrugs.jsp",
                        "<%=RxPrescriptionData.getFullOutLine(prescriptDrug.getSpecial()).replaceAll(\";\", \" \")%>",
                        "<carlos:encode value='<%= RxPrescriptionData.getFullOutLine(prescriptDrug.getSpecial()).replaceAll(\";\", \" \") %>'/>"));
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("savedNameSinks")
    @DisplayName("each view of a saved drug name should render it through the null-safe encoder")
    void shouldEncodeSavedDrugName_atSink(String file, String rawForm, String encodedForm) throws IOException {
        String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);

        assertThat(source).contains(encodedForm).doesNotContain(rawForm);
    }
}
