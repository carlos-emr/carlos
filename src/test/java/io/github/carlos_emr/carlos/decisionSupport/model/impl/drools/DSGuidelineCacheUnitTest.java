/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.decisionSupport.model.impl.drools;

import io.github.carlos_emr.carlos.decisionSupport.model.DSCondition;
import io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess;
import io.github.carlos_emr.carlos.decisionSupport.model.conditionValue.DSValue;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("drools")
class DSGuidelineCacheUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldEvaluateEachConditionIndependently_whenPreviewsHaveTheSameTitle(boolean reverseOrder) throws Exception {
        var diagnosis = preview(DSDemographicAccess.Module.dxcodes, "icd9:2700");
        var sex = preview(DSDemographicAccess.Module.sex, "F");
        if (reverseOrder) {
            assertNotNull(evaluate(sex));
            assertNull(evaluate(diagnosis));
        } else {
            assertNull(evaluate(diagnosis));
            assertNotNull(evaluate(sex));
        }
        // Repeated evaluations must retain each preview's own result after cache warm-up.
        assertNull(evaluate(diagnosis));
        assertNotNull(evaluate(sex));
        assertNull(evaluate(preview(DSDemographicAccess.Module.sex, "M")));
    }

    private static DSGuidelineDrools preview(DSDemographicAccess.Module type, String value) {
        var condition = new DSCondition();
        condition.setConditionType(type);
        condition.setListOperator(DSCondition.ListOperator.any);
        condition.setValues(DSValue.createDSValues(value));
        var guideline = new DSGuidelineDrools();
        guideline.setTitle("Shared \"guideline\"\npreview");
        guideline.setParsed(true);
        guideline.setConditions(List.of(condition));
        guideline.setConsequences(List.of());
        guideline.setParameters(List.of());
        return guideline;
    }

    private static Object evaluate(DSGuidelineDrools guideline) throws Exception {
        var access = mock(DSDemographicAccess.class);
        var passed = new AtomicBoolean();
        when(access.isSexAny("'F'")).thenReturn(true);
        when(access.isPassedGuideline()).thenAnswer(call -> passed.get());
        doAnswer(call -> { passed.set(call.getArgument(0)); return null; })
                .when(access).setPassedGuideline(anyBoolean());
        var method = DSGuidelineDrools.class.getDeclaredMethod("executeRules", DSDemographicAccess.class);
        method.setAccessible(true);
        return method.invoke(guideline, access);
    }
}
