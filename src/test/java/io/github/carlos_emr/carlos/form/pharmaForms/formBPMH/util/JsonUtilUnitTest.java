/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * Migrated from legacy JUnit 4 JsonUtilTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;

/**
 * Unit tests for {@link JsonUtil}.
 *
 * <p>Tests JSON serialization/deserialization of BPMH drug objects and demographics.
 * Migrated from legacy JUnit 4 JsonUtilTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("JsonUtil unit tests")
class JsonUtilUnitTest {

    private static Demographic demographic;
    private static List<BpmhDrug> bpmhDrugList;

    private static final String jsonListString = "[{\"atc\":\"\",\"code\":\"\",\"codingSystem\":\"\",\"comment\":\"\",\"comments\":\"\",\"customInstructions\":\"\",\"customName\":\"\",\"customNote\":\"\",\"demographicId\":\"\",\"dosage\":\"\",\"drugForm\":\"\",\"durUnit\":\"\",\"duration\":\"\",\"freqCode\":\"\",\"genericName\":\"GENERIC DRUG\",\"how\":\"this is how one\",\"id\":\"\",\"instruction\":\"\",\"method\":\"\",\"outsideProviderName\":\"\",\"outsideProviderOhip\":\"\",\"position\":\"\",\"prn\":\"\",\"quantity\":\"\",\"repeat\":\"\",\"route\":\"\",\"special\":\"\",\"special_instruction\":\"\",\"takeMax\":\"\",\"takeMin\":\"\",\"unit\":\"\",\"what\":\"GENERIC DRUG \",\"why\":\"This is a description.\"},{\"atc\":\"\",\"code\":\"\",\"codingSystem\":\"\",\"comment\":\"\",\"comments\":\"\",\"customInstructions\":\"\",\"customName\":\"\",\"customNote\":\"\",\"demographicId\":\"\",\"dosage\":\"\",\"drugForm\":\"\",\"durUnit\":\"\",\"duration\":\"\",\"freqCode\":\"\",\"genericName\":\"DRUG NAME\",\"how\":\"this is how two\",\"id\":\"\",\"instruction\":\"\",\"method\":\"\",\"outsideProviderName\":\"\",\"outsideProviderOhip\":\"\",\"position\":\"\",\"prn\":\"\",\"quantity\":\"\",\"repeat\":\"\",\"route\":\"\",\"special\":\"\",\"special_instruction\":\"\",\"takeMax\":\"\",\"takeMin\":\"\",\"unit\":\"\",\"what\":\"DRUG NAME \",\"why\":\"take this drug daily\"}]";
    private static final String jsonString = "{\"atc\":\"\",\"code\":\"\",\"codingSystem\":\"\",\"comment\":\"\",\"comments\":\"\",\"customInstructions\":\"\",\"customName\":\"\",\"customNote\":\"\",\"demographicId\":\"\",\"dosage\":\"\",\"drugForm\":\"\",\"durUnit\":\"\",\"duration\":\"\",\"freqCode\":\"\",\"genericName\":\"GENERIC DRUG\",\"how\":\"this is how one\",\"id\":\"\",\"instruction\":\"\",\"method\":\"\",\"outsideProviderName\":\"\",\"outsideProviderOhip\":\"\",\"position\":\"\",\"prn\":\"\",\"quantity\":\"\",\"repeat\":\"\",\"route\":\"\",\"special\":\"\",\"special_instruction\":\"\",\"takeMax\":\"\",\"takeMin\":\"\",\"unit\":\"\",\"what\":\"GENERIC DRUG \",\"why\":\"This is a description.\"}";
    private static final String[] ignoreMethods = new String[]{"handler", "hibernateLazyInitializer", "hours", "minutes", "seconds"};

    @BeforeAll
    static void setUp() {
        demographic = new Demographic();
        demographic.setDemographicNo(12345);
        demographic.setFirstName("Dennis");
        demographic.setLastName("Warren");
        demographic.setHin("9374636728674");
        demographic.setEffDate(new java.sql.Date(new Date().getTime()));
        demographic.setFamilyDoctor("<rd>Who, Doctor</rd><rdohip>973637</rdohip>");
        demographic.setFamilyPhysician("<rd>Who, Doctor</rd><rdohip>973637</rdohip>");

        BpmhDrug bpmhDrug1 = new BpmhDrug();
        bpmhDrug1.setGenericName("GENERIC DRUG");
        bpmhDrug1.setWhy("This is a description.");
        bpmhDrug1.setHow("this is how one");

        BpmhDrug bpmhDrug2 = new BpmhDrug();
        bpmhDrug2.setGenericName("DRUG NAME");
        bpmhDrug2.setWhy("take this drug daily");
        bpmhDrug2.setHow("this is how two");

        bpmhDrugList = new ArrayList<>();
        bpmhDrugList.add(bpmhDrug1);
        bpmhDrugList.add(bpmhDrug2);
    }

    @Test
    @DisplayName("should convert POJO to JSON string")
    void shouldConvertPojoToJson() {
        assertThat(JsonUtil.pojoToJson(demographic, ignoreMethods)).isNotNull();
    }

    @Test
    @DisplayName("should convert POJO list to JSON string")
    void shouldConvertPojoListToJson() {
        assertThat(JsonUtil.pojoCollectionToJson(bpmhDrugList, ignoreMethods)).isNotNull();
    }

    @Test
    @DisplayName("should deserialize JSON list to BpmhDrug list")
    @SuppressWarnings("unchecked")
    void shouldDeserializeJsonList_toBpmhDrugList() {
        List<BpmhDrug> drugList = (List<BpmhDrug>) JsonUtil.jsonToPojoList(jsonListString, BpmhDrug.class);

        assertThat(drugList.get(0).getGenericName()).isEqualTo("GENERIC DRUG");
        assertThat(drugList.get(1).getWhy()).isEqualTo("take this drug daily");
        assertThat(drugList.get(0).getHow()).isEqualTo("this is how one");
    }

    @Test
    @DisplayName("should deserialize JSON string to BpmhDrug")
    void shouldDeserializeJsonString_toBpmhDrug() {
        BpmhDrug drug = (BpmhDrug) JsonUtil.jsonToPojo(jsonString, BpmhDrug.class);

        assertThat(drug.getGenericName()).isEqualTo("GENERIC DRUG");
        assertThat(drug.getWhy()).isEqualTo("This is a description.");
        assertThat(drug.getHow()).isEqualTo("this is how one");
    }
}
