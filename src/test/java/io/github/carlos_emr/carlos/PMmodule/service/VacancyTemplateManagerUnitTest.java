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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VacancyTemplateManager")
@Tag("unit")
class VacancyTemplateManagerUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void setUp() {
        createAndRegisterMock(VacancyTemplateDao.class);
        createAndRegisterMock(CriteriaDao.class);
        createAndRegisterMock(CriteriaTypeDao.class);
        createAndRegisterMock(CriteriaTypeOptionDao.class);
        createAndRegisterMock(CriteriaSelectionOptionDao.class);
        createAndRegisterMock(ProgramDao.class);
        createAndRegisterMock(VacancyDao.class);
    }

    @Test
    void shouldUseRootLocale_whenBuildingCriteriaFieldKeys() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(VacancyTemplateManager.criteriaFieldKey("I Status"))
                    .isEqualTo("i_status");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void shouldPreserveFormContract_whenBuildingCriteriaFieldKeys() {
        String key = VacancyTemplateManager.criteriaFieldKey("Referral Source");

        assertThat(key).isEqualTo("referral_source");
        assertThat(key + "Required").isEqualTo("referral_sourceRequired");
        assertThat("targetOf" + key).isEqualTo("targetOfreferral_source");
        assertThat(key + "Minimum").isEqualTo("referral_sourceMinimum");
        assertThat(key + "Maximum").isEqualTo("referral_sourceMaximum");
    }
}
