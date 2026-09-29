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
package io.github.carlos_emr.carlos.PMmodule.service;

import io.github.carlos_emr.carlos.PMmodule.dao.CriteriaDao;
import io.github.carlos_emr.carlos.PMmodule.dao.CriteriaSelectionOptionDao;
import io.github.carlos_emr.carlos.PMmodule.dao.CriteriaTypeDao;
import io.github.carlos_emr.carlos.PMmodule.dao.CriteriaTypeOptionDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyTemplateDao;
import io.github.carlos_emr.carlos.PMmodule.model.Criteria;
import io.github.carlos_emr.carlos.PMmodule.model.CriteriaType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("VacancyTemplateManager")
@Tag("unit")
class VacancyTemplateManagerUnitTest extends CarlosUnitTestBase {
    private Class<?> managerClass;
    private CriteriaDao criteriaDao;
    private CriteriaTypeDao criteriaTypeDao;
    private CriteriaTypeOptionDao criteriaTypeOptionDao;
    private CriteriaSelectionOptionDao criteriaSelectionOptionDao;


    @BeforeEach
    void setUp() throws java.io.IOException {
        createAndRegisterMock(VacancyTemplateDao.class);
        criteriaDao = createAndRegisterMock(CriteriaDao.class);
        criteriaTypeDao = createAndRegisterMock(CriteriaTypeDao.class);
        criteriaTypeOptionDao = createAndRegisterMock(CriteriaTypeOptionDao.class);
        criteriaSelectionOptionDao = createAndRegisterMock(CriteriaSelectionOptionDao.class);
        createAndRegisterMock(ProgramDao.class);
        createAndRegisterMock(VacancyDao.class);
        managerClass = isolatedManagerClass();
    }

    @Test
    void shouldUseRootLocale_whenBuildingCriteriaFieldKeys() throws ReflectiveOperationException {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(criteriaFieldKey("I Status"))
                    .isEqualTo("i_status");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void shouldPreserveFormContract_whenBuildingCriteriaFieldKeys() throws ReflectiveOperationException {
        String key = criteriaFieldKey("Referral Source");

        assertThat(key).isEqualTo("referral_source");
        assertThat(key + "Required").isEqualTo("referral_sourceRequired");
        assertThat("targetOf" + key).isEqualTo("targetOfreferral_source");
        assertThat(key + "Minimum").isEqualTo("referral_sourceMinimum");
        assertThat(key + "Maximum").isEqualTo("referral_sourceMaximum");
    }

    @Test
    void shouldPreserveNumberValue_whenRenderingCriteriaInput() throws ReflectiveOperationException {
        Criteria criteria = new Criteria();
        criteria.setId(17);
        criteria.setCriteriaValue("15");
        criteria.setCanBeAdhoc(2);

        CriteriaType criteriaType = new CriteriaType();
        criteriaType.setFieldName("Minimum Age");
        criteriaType.setFieldType("number");

        when(criteriaDao.getCriteriaByTemplateIdVacancyIdTypeId(99, null, 5))
                .thenReturn(criteria);
        when(criteriaTypeDao.find((Object) 5)).thenReturn(criteriaType);
        when(criteriaTypeOptionDao.getCriteriaTypeOptionByTypeId(5)).thenReturn(List.of());
        when(criteriaSelectionOptionDao.getCriteriaSelectedOptionsByCriteriaId(17))
                .thenReturn(List.of());

        String html = (String) managerClass.getMethod("renderAllSelectOptions", Integer.class, Integer.class,
                Integer.class).invoke(null, 99, null, 5);

        assertThat(html).contains("value=\"15\" name=\"minimum_ageNumber\"");
        assertThat(html).doesNotContain("value=\" 15\"");
    }
    private String criteriaFieldKey(String label) throws ReflectiveOperationException {
        return (String) managerClass.getMethod("criteriaFieldKey", String.class).invoke(null, label);
    }

    private static Class<?> isolatedManagerClass() throws java.io.IOException {
        // This legacy interface captures DAOs in static final fields. Load only its bytecode afresh:
        // dependencies still use the parent loader and the registered mocks, while other tests keep
        // their original interface and DAO bindings. No production field or global state is changed.
        return new ClassLoader(VacancyTemplateManagerUnitTest.class.getClassLoader()) {
            Class<?> defineManager() throws java.io.IOException {
                String name = "io.github.carlos_emr.carlos.PMmodule.service.VacancyTemplateManager";
                try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = java.util.Objects.requireNonNull(input, "manager bytecode").readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                }
            }
        }.defineManager();
    }
}
