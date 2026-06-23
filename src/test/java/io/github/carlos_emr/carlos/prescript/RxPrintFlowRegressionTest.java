/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rx print flow regression Tests")
@Tag("unit")
@Tag("fast")
@Tag("prescript")
class RxPrintFlowRegressionTest {

    private static final Path STRUTS_PRESCRIPTION_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-prescription.xml");
    private static final Path PRINT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/rx/Print.jsp");
    private static final Path CHOOSE_DRUG_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/rx/ChooseDrug.jsp");
    private static final Path SIDE_LINKS_EDIT_FAVORITES_2_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/rx/SideLinksEditFavorites2.jsp");

    @Test
    void shouldRoutePrintSearchAgainThroughRxSearchPatient() throws Exception {
        String printJsp = Files.readString(PRINT_JSP, StandardCharsets.UTF_8);
        String struts = Files.readString(STRUTS_PRESCRIPTION_XML, StandardCharsets.UTF_8);

        assertThat(printJsp)
                .contains("${pageContext.request.contextPath}/rx/searchPatient")
                .contains("type=\"text\" name=\"surname\"")
                .contains("${pageContext.request.contextPath}/rx/ViewPrint")
                .doesNotContain("SearchPatient.jsp")
                .doesNotContain("type=\"checkbox\" name=\"surname\"")
                .doesNotContain("${pageContext.request.contextPath}/rx/choosePatient\" method=\"post\"");

        assertThat(struts)
                .contains("<action name=\"rx/searchPatient\" class=\"io.github.carlos_emr.carlos.prescript.pageUtil.RxSearchPatient2Action\">")
                .contains("<result name=\"success\">/WEB-INF/jsp/rx/Print.jsp</result>");
    }

    @Test
    void shouldKeepIdBasedDrugInfoLookupOnBnPath() throws Exception {
        String chooseDrugJsp = Files.readString(CHOOSE_DRUG_JSP, StandardCharsets.UTF_8);
        Matcher showDrugInfo = Pattern.compile("function ShowDrugInfo\\(drug\\) \\{(.*?)\\n\\s*\\}",
                Pattern.DOTALL).matcher(chooseDrugJsp);

        assertThat(showDrugInfo.find()).isTrue();
        assertThat(showDrugInfo.group(1))
                .contains("ShowDrugInfoBN(drug);")
                .doesNotContain("drugInfo?GN=");
    }

    @Test
    void shouldEncodeFavoriteNames_inSideLinksEditFavorites2Jsp() throws Exception {
        String sideLinksEditFavorites2Jsp = Files.readString(SIDE_LINKS_EDIT_FAVORITES_2_JSP, StandardCharsets.UTF_8);

        assertThat(sideLinksEditFavorites2Jsp)
                .contains("title=\"<carlos:encode")
                .contains("context=\"htmlAttribute\"")
                .contains("context=\"html\"")
                .contains("substring(0, 10) + \"...\"")
                .doesNotContain("title=\"<%= favorites[j].getFavoriteName() %>\"")
                .doesNotContain("<%= favorites[j].getFavoriteName().substring(0, 10) + \"...\" %>")
                .doesNotContain("<%= favorites[j].getFavoriteName() %>");
    }
}
