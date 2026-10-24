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
import io.github.carlos_emr.carlos.PMmodule.model.CriteriaTypeOption;
import io.github.carlos_emr.carlos.PMmodule.model.CriteriaSelectionOption;
import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.PMmodule.model.VacancyTemplate;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("VacancyTemplateManager")
@Tag("unit")
class VacancyTemplateManagerUnitTest extends CarlosUnitTestBase {

    private VacancyDao vacancyDAO;
    private VacancyTemplateDao templateDAO;
    private CriteriaDao criteriaDAO;
    private CriteriaTypeDao criteriaTypeDAO;
    private CriteriaTypeOptionDao criteriaTypeOptionDAO;
    private CriteriaSelectionOptionDao criteriaSelectionOptionDAO;

    @BeforeEach
    void setUp() {
        templateDAO = createAndRegisterMock(VacancyTemplateDao.class);
        criteriaDAO = createAndRegisterMock(CriteriaDao.class);
        criteriaTypeDAO = createAndRegisterMock(CriteriaTypeDao.class);
        criteriaTypeOptionDAO = createAndRegisterMock(CriteriaTypeOptionDao.class);
        criteriaSelectionOptionDAO = createAndRegisterMock(CriteriaSelectionOptionDao.class);
        createAndRegisterMock(ProgramDao.class);
        vacancyDAO = createAndRegisterMock(VacancyDao.class);
    }

    @Test
    void shouldUseCurrentDao_whenSpringContextChanges() {
        Criteria original = new Criteria();
        when(criteriaDAO.find((Object) 17)).thenReturn(original);
        assertThat(VacancyTemplateManager.getCriteriaByCriteriaId(17)).isSameAs(original);
        CriteriaDao replacement = createAndRegisterMock(CriteriaDao.class);
        Criteria current = new Criteria();
        when(replacement.find((Object) 17)).thenReturn(current);

        assertThat(VacancyTemplateManager.getCriteriaByCriteriaId(17)).isSameAs(current);
        org.mockito.Mockito.verify(replacement).find((Object) 17);
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

    @Test
    void shouldPreserveNumberValue_whenRenderingCriteriaInput() {
        Criteria criteria = new Criteria();
        criteria.setId(17);
        criteria.setCriteriaValue("15");
        criteria.setCanBeAdhoc(2);

        CriteriaType criteriaType = new CriteriaType();
        criteriaType.setFieldName("Minimum Age");
        criteriaType.setFieldType("number");

        when(criteriaDAO.getCriteriaByTemplateIdVacancyIdTypeId(99, null, 5))
                .thenReturn(criteria);
        when(criteriaTypeDAO.find((Object) 5)).thenReturn(criteriaType);
        when(criteriaTypeOptionDAO.getCriteriaTypeOptionByTypeId(5)).thenReturn(List.of());
        when(criteriaSelectionOptionDAO.getCriteriaSelectedOptionsByCriteriaId(17))
                .thenReturn(List.of());

        String html = VacancyTemplateManager.renderAllSelectOptions(99, null, 5);

        assertThat(html).contains("value=\"15\" name=\"minimum_ageNumber\"");
        assertThat(html).doesNotContain("value=\" 15\"");
    }
    @Test
    void shouldSaveExistingVacancyConfigurationInCurrentContext_whenContextIsReplacedAfterCreation() {
        VacancyTemplate template = VacancyTemplateManager.createVacancyTemplate("0");
        Criteria criteria = VacancyTemplateManager.createCriteria(null, "0");
        Vacancy vacancy = new Vacancy();
        CriteriaSelectionOption selection = new CriteriaSelectionOption();
        VacancyTemplateManager.saveVacancyTemplate(template);
        VacancyTemplateManager.saveCriteria(criteria);
        VacancyTemplateManager.saveVacancy(vacancy);
        VacancyTemplateManager.saveCriteriaSelectedOption(selection);
        verify(templateDAO).persist(template);
        verify(criteriaDAO).persist(criteria);
        verify(vacancyDAO).persist(vacancy);
        verify(criteriaSelectionOptionDAO).persist(selection);
        clearInvocations(templateDAO, criteriaDAO, vacancyDAO, criteriaSelectionOptionDAO);

        template.setId(11);
        criteria.setId(12);
        vacancy.setId(13);
        selection.setId(14);
        var currentTemplates = createAndRegisterMock(VacancyTemplateDao.class);
        var currentCriteria = createAndRegisterMock(CriteriaDao.class);
        var currentVacancies = createAndRegisterMock(VacancyDao.class);
        var currentSelections = createAndRegisterMock(CriteriaSelectionOptionDao.class);
        when(currentTemplates.getVacancyTemplate(11)).thenReturn(template);
        when(currentCriteria.find((Object) 12)).thenReturn(criteria);
        when(currentVacancies.find((Object) 13)).thenReturn(vacancy);

        assertThat(VacancyTemplateManager.createVacancyTemplate("11")).isSameAs(template);
        assertThat(VacancyTemplateManager.createCriteria(null, "12")).isSameAs(criteria);
        assertThat(VacancyTemplateManager.getVacancyById(13)).isSameAs(vacancy);
        VacancyTemplateManager.saveVacancyTemplate(template);
        VacancyTemplateManager.saveCriteria(criteria);
        VacancyTemplateManager.saveVacancy(vacancy);
        VacancyTemplateManager.saveCriteriaSelectedOption(selection);

        verify(currentTemplates).merge(template);
        verify(currentCriteria).merge(criteria);
        verify(currentVacancies).merge(vacancy);
        verify(currentSelections).merge(selection);
        verifyNoInteractions(templateDAO, criteriaDAO, vacancyDAO, criteriaSelectionOptionDAO);
    }

    @Test
    void shouldPreferVacancyOverrideToTemplate_whenResolvingSelectedCriteria() {
        Criteria inherited = new Criteria();
        Criteria refined = new Criteria();
        when(criteriaDAO.getCriteriaByTemplateIdVacancyIdTypeId(11, null, 5)).thenReturn(inherited);
        when(criteriaDAO.getCriteriaByTemplateIdVacancyIdTypeId(null, 13, 5)).thenReturn(refined);

        assertThat(VacancyTemplateManager.getSelectedCriteria(11, null, 5)).isSameAs(inherited);
        assertThat(VacancyTemplateManager.getSelectedCriteria(11, 13, 5)).isSameAs(refined);
        clearInvocations(criteriaDAO);
        assertThat(VacancyTemplateManager.getSelectedCriteria(null, null, null)).isNull();
        assertThat(VacancyTemplateManager.getSelectedCriteria(11, null, null)).isNull();
        verifyNoInteractions(criteriaDAO);
    }

    @Test
    void shouldUseCurrentVacancyCatalogue_whenContextChangesAfterNameLookup() {
        Vacancy previous = new Vacancy();
        previous.setId(41);
        when(vacancyDAO.getVacanciesByName("Available bed")).thenReturn(List.of(previous));
        assertThat(VacancyTemplateManager.getVacancyByName("Available bed")).isSameAs(previous);
        clearInvocations(vacancyDAO);
        Vacancy current = new Vacancy();
        current.setId(42);
        VacancyDao replacement = createAndRegisterMock(VacancyDao.class);
        when(replacement.getVacanciesByName("Available bed")).thenReturn(List.of(current));
        when(replacement.getVacanciesByWlProgramId(27)).thenReturn(List.of(current));
        when(replacement.getVacanciesByWlProgramIdAndStatus(27, "Active")).thenReturn(List.of(current));

        assertThat(VacancyTemplateManager.getVacancyByName("Available bed")).isSameAs(current);
        assertThat(VacancyTemplateManager.getVacancyByName("Missing bed")).isNull();
        assertThat(VacancyTemplateManager.getVacanciesByWlProgramId(27)).containsExactly(current);
        assertThat(VacancyTemplateManager.getVacanciesByWlProgramIdAndStatus(27, "Active")).containsExactly(current);
        verifyNoInteractions(vacancyDAO);
    }

    @Test
    void shouldRenderSavedSelectionWithEscapedLabel_whenCriteriaOptionsComeFromCurrentContext() {
        Criteria criteria = new Criteria();
        criteria.setId(17);
        criteria.setCanBeAdhoc(2);
        CriteriaType type = new CriteriaType();
        type.setId(5);
        type.setFieldName("Support Needs");
        type.setFieldType("select_multiple");
        CriteriaSelectionOption selected = new CriteriaSelectionOption();
        selected.setOptionValue("support");
        CriteriaTypeOption option = new CriteriaTypeOption();
        option.setOptionValue("support");
        option.setOptionLabel("A <script> & B");
        when(criteriaDAO.getCriteriaByTemplateIdVacancyIdTypeId(11, null, 5)).thenReturn(criteria);
        when(criteriaTypeDAO.find((Object) 5)).thenReturn(type);
        when(criteriaSelectionOptionDAO.getCriteriaSelectedOptionsByCriteriaId(17)).thenReturn(List.of(selected));
        when(criteriaTypeOptionDAO.getCriteriaTypeOptionByTypeId(5)).thenReturn(List.of(option));
        when(criteriaTypeOptionDAO.getByValueAndTypeId("support", 5)).thenReturn(option);

        String html = VacancyTemplateManager.renderAllSelectOptions(11, null, 5);

        assertThat(html).contains("targetOfsupport_needs", "<option selected", "value=\"support\"");
        assertThat(html).contains("A &lt;script&gt; &amp; B").doesNotContain("A <script> & B");
    }

}
