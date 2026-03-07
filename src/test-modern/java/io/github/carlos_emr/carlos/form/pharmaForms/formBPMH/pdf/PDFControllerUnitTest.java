/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences,
 * University of British Columbia. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 PDFControllerTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;

/**
 * Unit tests for {@link PDFController}.
 *
 * <p>Tests PDF template loading, output path management, and getter method
 * invocation for BPMH (Best Possible Medication History) form PDF generation.
 * Migrated from legacy JUnit 4 PDFControllerTest.
 *
 * @since 2014-11-01 (original)
 */
@Tag("unit")
@Tag("form")
@DisplayName("PDFController unit tests")
class PDFControllerUnitTest {

    private PDFController pdfController;
    private BpmhForm2Bean data;
    private Demographic demographic;

    @BeforeEach
    void setUp() {
        ClassLoader loader = PDFController.class.getClassLoader();
        URL url = loader.getResource("oscar/form/prop/bpmh_template_marked.pdf");

        pdfController = new PDFController(url.getPath());
        pdfController.setOutputPath("/var/lib/OscarDocument");

        demographic = new Demographic();
        demographic.setDemographicNo(12345);
        demographic.setFirstName("Dennis");
        demographic.setLastName("Warren");
        demographic.setHin("9374636728674");
        demographic.setEffDate(new Date());

        BpmhDrug bpmhDrug1 = new BpmhDrug();
        bpmhDrug1.setGenericName("GENERIC DRUG");
        bpmhDrug1.setWhy("This is a description.");
        bpmhDrug1.setWhat("chicken butt");

        BpmhDrug bpmhDrug2 = new BpmhDrug();
        bpmhDrug2.setGenericName("DRUG NAME");
        bpmhDrug2.setWhy("take this drug daily");

        List<BpmhDrug> bpmhDrugList = new ArrayList<>();
        bpmhDrugList.add(bpmhDrug1);
        bpmhDrugList.add(bpmhDrug2);

        data = new BpmhForm2Bean();
        data.setDemographicNo("2345");
        data.setFamilyDrName("Dr. Who");
        data.setDemographic(demographic);
        data.setDrugs(bpmhDrugList);
    }

    @Test
    @DisplayName("should return configured output path")
    void shouldReturnOutputPath_whenSet() {
        assertThat(pdfController.getOutputPath()).isEqualTo("/var/lib/OscarDocument");
    }

    @Test
    @DisplayName("should return PDF template filename from input path")
    void shouldReturnTemplateFilename_fromInputPath() {
        assertThat(pdfController.getFilePath().getName()).isEqualTo("bpmh_template_marked.pdf");
    }

    @Test
    @DisplayName("should invoke getter methods on demographic object via reflection")
    void shouldInvokeGetterMethods_onDemographicObject() {
        pdfController.setDataObject(demographic);
        assertThat(pdfController.invokeValue("firstName")).isEqualTo("Dennis");
        assertThat(pdfController.invokeValue("hin")).isEqualTo("9374636728674");
    }

    @Test
    @DisplayName("should return null for missing getter methods")
    void shouldReturnNull_forMissingGetterMethods() {
        Map<String, Method> getterMethods = PDFController.getGetterMethods(data);
        assertThat(PDFController.invokeValue("democratic.fakemethod", getterMethods, data)).isNull();
        assertThat(PDFController.invokeValue("fakemethod", getterMethods, data)).isNull();
    }
}
