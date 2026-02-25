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
package io.github.carlos_emr.carlos.decisionSupport.model.impl.drools;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.decisionSupport.model.DSCondition;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess;
import io.github.carlos_emr.carlos.decisionSupport.model.DSParameter;
import io.github.carlos_emr.carlos.decisionSupport.model.conditionValue.DSValue;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the DRL generation methods in {@link DSGuidelineDrools}.
 *
 * <p>These tests verify the programmatic DRL text generation pipeline without
 * requiring a database, Spring context, or Drools compilation. Each test
 * constructs model objects ({@link DSCondition}, {@link DSParameter},
 * {@link DSConsequence}) and verifies the generated DRL strings.</p>
 *
 * @see DSGuidelineDrools
 * @since 2026-02-18
 */
@Tag("unit")
@Tag("drools")
@DisplayName("DSGuidelineDrools")
class DSGuidelineDroolsUnitTest {

    private DSGuidelineDrools guideline;

    @BeforeEach
    void setUp() {
        guideline = new DSGuidelineDrools();
        guideline.setTitle("TestGuideline");
        guideline.setParsed(true);
        guideline.setConditions(new ArrayList<>());
        guideline.setConsequences(new ArrayList<>());
        guideline.setParameters(new ArrayList<>());
    }

    @Nested
    @DisplayName("getRuleBaseFactoryKey")
    class GetRuleBaseFactoryKey {

        @Test
        @DisplayName("should use ID when entity has persisted ID")
        void shouldUseId_whenEntityHasPersistedId() {
            guideline.setId(42);

            String key = guideline.getRuleBaseFactoryKey();

            assertThat(key).isEqualTo("DSGuidelineDrools:42");
        }

        @Test
        @DisplayName("should use title when entity has no ID")
        void shouldUseTitle_whenEntityHasNoId() {
            guideline.setId(null);
            guideline.setTitle("Diabetes Screening");

            String key = guideline.getRuleBaseFactoryKey();

            assertThat(key).isEqualTo("DSGuidelineDrools:Diabetes Screening");
        }
    }

    @Nested
    @DisplayName("getDroolsCondition")
    class GetDroolsCondition {

        @Test
        @DisplayName("should generate DRL expression with accessMethod and listOperator")
        void shouldGenerateDrlExpression_withAccessMethodAndListOperator() {
            DSCondition condition = new DSCondition();
            condition.setConditionType(DSDemographicAccess.Module.dxcodes);
            condition.setListOperator(DSCondition.ListOperator.any);
            condition.setValues(DSValue.createDSValues("icd9:250,icd9:401"));

            String result = guideline.getDroolsCondition(condition);

            assertThat(result).contains("a.hasDxCodesAny(");
            // DSValueString.toString() wraps string values in single quotes
            assertThat(result).contains("icd9:'250',icd9:'401'");
        }

        @Test
        @DisplayName("should capitalize listOperator in method name")
        void shouldCapitalizeListOperator_inMethodName() {
            DSCondition condition = new DSCondition();
            condition.setConditionType(DSDemographicAccess.Module.age);
            condition.setListOperator(DSCondition.ListOperator.all);
            condition.setValues(DSValue.createDSValues(">18 y"));

            String result = guideline.getDroolsCondition(condition);

            assertThat(result).contains("a.isAgeAll(");
        }

        @Test
        @DisplayName("should append Hashtable label when condition has param")
        void shouldAppendHashtableLabel_whenConditionHasParam() {
            DSCondition condition = new DSCondition();
            condition.setConditionType(DSDemographicAccess.Module.billedFor);
            condition.setListOperator(DSCondition.ListOperator.any);
            condition.setValues(DSValue.createDSValues("13050"));
            Hashtable<String, String> params = new Hashtable<>();
            params.put("payer", "MSP");
            condition.setParam(params);
            condition.setLabel("param0");

            String result = guideline.getDroolsCondition(condition);

            assertThat(result).contains("param0");
            assertThat(result).contains("a.billedForAny(\"'13050'\",param0)");
        }

        @Test
        @DisplayName("should not append param when condition has no param")
        void shouldNotAppendParam_whenConditionHasNoParam() {
            DSCondition condition = new DSCondition();
            condition.setConditionType(DSDemographicAccess.Module.sex);
            condition.setListOperator(DSCondition.ListOperator.any);
            condition.setValues(DSValue.createDSValues("F"));

            String result = guideline.getDroolsCondition(condition);

            assertThat(result).contains("a.isSexAny(\"'F'\")");
            assertThat(result).doesNotContain("param");
        }

        @Test
        @DisplayName("should handle notany listOperator")
        void shouldHandleNotanyListOperator() {
            DSCondition condition = new DSCondition();
            condition.setConditionType(DSDemographicAccess.Module.drugs);
            condition.setListOperator(DSCondition.ListOperator.notany);
            condition.setValues(DSValue.createDSValues("atc:C09AA"));

            String result = guideline.getDroolsCondition(condition);

            assertThat(result).contains("a.hasRxCodesNotany(");
        }
    }

    @Nested
    @DisplayName("getDroolsParameter")
    class GetDroolsParameter {

        @Test
        @DisplayName("should generate fact binding with alias and FQCN")
        void shouldGenerateFactBinding_withAliasAndFqcn() {
            DSParameter param = new DSParameter();
            param.setStrAlias("myList");
            param.setStrClass("java.util.ArrayList");

            String result = guideline.getDroolsParameter(param);

            assertThat(result).contains("myList : java.util.ArrayList()");
        }

        @Test
        @DisplayName("should indent with 8 spaces")
        void shouldIndentWith8Spaces() {
            DSParameter param = new DSParameter();
            param.setStrAlias("helper");
            param.setStrClass("com.example.Helper");

            String result = guideline.getDroolsParameter(param);

            assertThat(result).startsWith("        ");
        }
    }

    @Nested
    @DisplayName("getDroolsConsequences")
    class GetDroolsConsequences {

        @Test
        @DisplayName("should always include setPassedGuideline marker")
        void shouldAlwaysIncludeSetPassedGuidelineMarker() {
            List<DSConsequence> consequences = new ArrayList<>();

            String result = guideline.getDroolsConsequences(consequences);

            assertThat(result).contains("a.setPassedGuideline(true);");
        }

        @Test
        @DisplayName("should append java-type consequence text")
        void shouldAppendJavaTypeConsequenceText() {
            DSConsequence javaConsequence = new DSConsequence();
            javaConsequence.setConsequenceType(DSConsequence.ConsequenceType.java);
            javaConsequence.setText("someObject.doSomething();");
            List<DSConsequence> consequences = new ArrayList<>();
            consequences.add(javaConsequence);

            String result = guideline.getDroolsConsequences(consequences);

            assertThat(result).contains("a.setPassedGuideline(true);");
            assertThat(result).contains("someObject.doSomething();");
        }

        @Test
        @DisplayName("should skip warning-type consequences")
        void shouldSkipWarningTypeConsequences() {
            DSConsequence warningConsequence = new DSConsequence();
            warningConsequence.setConsequenceType(DSConsequence.ConsequenceType.warning);
            warningConsequence.setText("Check patient blood pressure");
            List<DSConsequence> consequences = new ArrayList<>();
            consequences.add(warningConsequence);

            String result = guideline.getDroolsConsequences(consequences);

            assertThat(result).isEqualTo("a.setPassedGuideline(true);");
            assertThat(result).doesNotContain("Check patient blood pressure");
        }

        @Test
        @DisplayName("should include only java-type from mixed consequences")
        void shouldIncludeOnlyJavaType_fromMixedConsequences() {
            DSConsequence warningConsequence = new DSConsequence();
            warningConsequence.setConsequenceType(DSConsequence.ConsequenceType.warning);
            warningConsequence.setText("Warning text");

            DSConsequence javaConsequence = new DSConsequence();
            javaConsequence.setConsequenceType(DSConsequence.ConsequenceType.java);
            javaConsequence.setText("a.setFlag(true);");

            List<DSConsequence> consequences = new ArrayList<>();
            consequences.add(warningConsequence);
            consequences.add(javaConsequence);

            String result = guideline.getDroolsConsequences(consequences);

            assertThat(result).contains("a.setPassedGuideline(true);");
            assertThat(result).contains("a.setFlag(true);");
            assertThat(result).doesNotContain("Warning text");
        }
    }

    @Nested
    @DisplayName("afterSave")
    class AfterSave {

        @Test
        @DisplayName("should remove entry from RuleBaseFactory cache")
        void shouldRemoveEntry_fromRuleBaseFactoryCache() throws Exception {
            guideline.setId(99);
            String key = guideline.getRuleBaseFactoryKey();

            // Pre-populate cache with a real KieBase compiled from minimal DRL
            String minimalDrl = "package test;\nrule \"dummy\" when then end\n";
            org.kie.api.KieBase dummyKieBase =
                    io.github.carlos_emr.carlos.drools.DroolsHelper.createKieBaseFromDrl(minimalDrl);
            RuleBaseFactory.putRuleBase(key, dummyKieBase);
            assertThat(RuleBaseFactory.getRuleBase(key)).isNotNull();

            guideline.afterSave();

            assertThat(RuleBaseFactory.getRuleBase(key)).isNull();
        }
    }
}
