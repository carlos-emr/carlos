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
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit coverage for demographic 2Action constructor injection wiring.
 *
 * @since 2026-06-01
 */
@DisplayName("Demographic 2Action constructor injection")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class Demographic2ActionConstructorInjectionUnitTest extends CarlosWebTestBase {

    @ParameterizedTest(name = "{0}")
    @MethodSource("constructorInjectedActions")
    @DisplayName("should instantiate actions with constructor dependencies")
    void shouldInstantiate_withConstructorDependencies(Class<?> actionClass) throws Exception {
        Constructor<?> constructor = onlyPublicConstructor(actionClass);
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(Demographic2ActionConstructorInjectionUnitTest::testDependency)
                .toArray();

        Object action = constructor.newInstance(dependencies);

        assertThat(action).isInstanceOf(actionClass);
    }

    private static Constructor<?> onlyPublicConstructor(Class<?> actionClass) {
        Constructor<?>[] constructors = actionClass.getConstructors();
        assertThat(constructors)
                .as("%s should expose exactly one constructor-injection entry point", actionClass.getName())
                .hasSize(1);
        return constructors[0];
    }

    private static Object testDependency(Class<?> dependencyType) {
        if (dependencyType == String.class) {
            return "";
        }
        if (dependencyType == Integer.class || dependencyType == int.class) {
            return 0;
        }
        if (dependencyType == Long.class || dependencyType == long.class) {
            return 0L;
        }
        if (dependencyType == Boolean.class || dependencyType == boolean.class) {
            return false;
        }
        return mock(dependencyType);
    }

    private static Stream<Class<?>> constructorInjectedActions() {
        return Stream.of(
                io.github.carlos_emr.carlos.demographic.PrintClientLabLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.PrintDemoAddressLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.PrintDemoChartLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.PrintDemoLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewAddDemoToPatientSet2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewAddNewDemographicSwipe2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewContact2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewContactSearch2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicAddARecordHtm2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicAudit2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicCohort2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicEditDemographicJs2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicLabelPrintSetting2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicPrintDemographic2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDemographicSearch2ReportResults2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDisplayFirstNationsModule2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewDisplayHealthCareTeam2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewEnrollmentHistory2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewManageFirstNationsModule2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewManageHealthCareTeam2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewPrintAddressLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewPrintClientLabLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewPrintDemoChartLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewPrintDemoLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewPrintEnvelope2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewProContact2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewProContactSearch2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewProfessionalSpecialistSearch2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewSearch2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewZdemographicFullTitleSearch2Action.class,
                io.github.carlos_emr.carlos.demographic.gate.ViewZdemographicSwipe2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.AddDemographicRelationship2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DeleteDemographicRelationship2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicAdd2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicAddRecord2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicApptHistory2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEdit2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicExportAction42Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicLinkMsg2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicMergeRecord2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicPdfLabel2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicSearch2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.DemographicUpdate2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.ImportDemographicDataAction42Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.ImportLogDownload2Action.class,
                io.github.carlos_emr.carlos.demographic.pageUtil.RourkeExport2Action.class
        );
    }
}
