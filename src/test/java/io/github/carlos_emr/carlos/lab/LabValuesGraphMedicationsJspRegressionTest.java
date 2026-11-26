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
package io.github.carlos_emr.carlos.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * #6325 review: the lab graph page lists the patient's prescriptions, which needs the prescription read
 * right on that patient. The view gate decides ({@code ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED}); the page
 * must keep the prescription query, the picker and the drugs on the graph image inside that decision.
 * A source-pattern guard, because the repository has no JSP render test for this page.
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
@DisplayName("Lab graph page leaves medications out without the prescription right")
class LabValuesGraphMedicationsJspRegressionTest {

    private static final String JSP = "src/main/webapp/WEB-INF/jsp/lab/CA/ON/labValuesGraph.jsp";

    private static String page() throws IOException {
        Path root = Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        return Files.readString(root.resolve(JSP), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should read the gate's decision from the request attribute the gate sets")
    void shouldReadTheGatesDecision_fromTheRequestAttribute() throws IOException {
        String page = page();

        assertThat(page).contains("ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED");
        assertThat(page).contains("boolean medicationsAllowed");
    }

    @Test
    @DisplayName("should run the prescription query and draw the picker only inside the medicationsAllowed block")
    void shouldKeepTheQueryAndPicker_insideTheDecision() throws IOException {
        String page = page();
        int blockStart = page.indexOf("<% if (medicationsAllowed) { %>");
        int query = page.indexOf("getUniquePrescriptionsByPatient");
        int picker = page.indexOf("name=\"drug\"");
        int outline = page.indexOf("getFullOutLine()");
        int formEnd = page.indexOf("</form>", picker);
        int blockEnd = page.indexOf("<% } %>", formEnd);

        assertThat(blockStart).as("the gate's decision opens a block").isNotNegative();
        assertThat(query).as("the query").isGreaterThan(blockStart);
        assertThat(picker).as("the picker").isGreaterThan(blockStart);
        assertThat(outline).as("the prescription outline").isGreaterThan(blockStart);
        assertThat(formEnd).as("the picker form ends inside the block").isLessThan(blockEnd);
        assertThat(page.indexOf("getUniquePrescriptionsByPatient", query + 1)).as("no second, unguarded query")
                .isNegative();
        assertThat(page.indexOf("getFullOutLine()", outline + 1)).as("no second, unguarded outline").isNegative();
    }

    @Test
    @DisplayName("should drop drug parameters from the graph image address without the right")
    void shouldDropDrugsFromTheGraphImage_withoutTheRight() throws IOException {
        String page = page();

        assertThat(page).contains("medicationsAllowed && request.getParameterValues(\"drug\") != null");
    }
}
