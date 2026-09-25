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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.drools.DroolsHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Executes the shared diabetes rules used by the Queens and standard flowsheets. */
@Tag("unit")
@Tag("drools")
class TuningForkReminderUnitTest {
    private static KieBase rules;

    @BeforeAll
    static void compileRules() throws Exception {
        rules = DroolsHelper.loadFromUrl(TuningForkReminderUnitTest.class.getResource(
                "/oscar/encounter/oscarMeasurements/flowsheets/diab.drl"));
    }

    @ParameterizedTest
    @CsvSource({"-1,true,false", "0,false,false", "9,false,false", "10,false,true",
            "11,false,true", "12,true,false", "13,true,false"})
    void shouldMatchReminderBoundary_whenTuningForkIsOnTheFlowsheet(int months, boolean warning, boolean recommendation) {
        MeasurementInfo info = mock(MeasurementInfo.class);
        when(info.getMeasurementData("NRTF")).thenReturn(new ArrayList<>());
        when(info.getLastDateRecordedInMonths("NRTF")).thenReturn(months);

        fire(info);

        verify(info, warning ? org.mockito.Mockito.times(1) : never()).addWarning(eq("NRTF"), anyString());
        verify(info, recommendation ? org.mockito.Mockito.times(1) : never()).addRecommendation(eq("NRTF"), anyString());
    }

    @Test
    void shouldAvoidTuningForkWarnings_whenAnotherFlowsheetUsesTheSharedRules() {
        MeasurementInfo info = mock(MeasurementInfo.class);
        when(info.getMeasurementData("NRTF")).thenReturn(null);
        when(info.getLastDateRecordedInMonths("NRTF")).thenReturn(-1);

        fire(info);

        verify(info, never()).addWarning(eq("NRTF"), anyString());
        verify(info, never()).addRecommendation(eq("NRTF"), anyString());
    }

    private static void fire(MeasurementInfo info) {
        KieSession session = rules.newKieSession();
        try {
            session.insert(info);
            session.fireAllRules(match -> match.getRule().getName().startsWith("NRTF "));
        } finally {
            session.dispose();
        }
    }
}
