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
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

/**
 * End-to-end persistence integration tests for eForm {@code <select>} prefill, dependent
 * {@code oscarDB="e$..."} lookups, and saved-value restoration against the real H2 database
 * and bundled {@code apconfig.xml} database tags. Synthetic FAKE data only.
 *
 * @since 2026-11-27
 */
@DisplayName("eForm select database-tag prefill integration tests")
@Tag("integration")
@Tag("dao")
@Tag("eform")
@Transactional
class EFormSelectApPrefillIntegrationTest extends CarlosTestBase {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    private DemographicDao demographicDao;

    @Autowired
    private EFormDao eFormDao;

    @Autowired
    private EFormDataDao eFormDataDao;

    @Autowired
    private EFormValueDao eFormValueDao;

    @Test
    @DisplayName("should select exact option and clear later template default from real patient_alias")
    void shouldSelectExactOptionAndClearLaterDefault_whenPrefilledFromRealPatientAlias() throws Exception {
        Demographic patient = persistPatient("Yes");
        String templateHtml = "<form>"
                + "<select name=\"Choice\" oscarDB=patient_alias>"
                + "<option value=\"Yes, with help\">Yes, with help</option>"
                + "<option value=\"Yes\">Yes</option>"
                + "<option value=\"No\" selected>No</option>"
                + "</select>"
                + "</form>";
        EForm template = persistTemplate("FAKE_SELECT_PREFIX", templateHtml);

        io.github.carlos_emr.carlos.eform.data.EForm rendered =
                new io.github.carlos_emr.carlos.eform.data.EForm(
                        template.getId().toString(), patient.getDemographicNo().toString(), "999998");
        rendered.setDatabaseAPs();

        Document page = Jsoup.parse(rendered.getFormHtml());
        assertThat(page.select("select[name=Choice] option[selected]").eachAttr("value"))
                .containsExactly("Yes");
    }

    @Test
    @DisplayName("should match entity-encoded option and bind decoded value to dependent e$count query")
    void shouldMatchEntityEncodedOptionAndBindDecodedValue_whenDependentCountRunsAgainstDatabase() throws Exception {
        Demographic patient = persistPatient("Smith & Sons");
        String templateHtml = "<form>"
                + "<select name=\"c\" oscarDB=patient_alias>"
                + "<option value=\"O\">o</option>"
                + "<option value=\"Smith &amp; Sons\">s</option>"
                + "<option value=\"N\" selected>n</option>"
                + "</select>"
                + "<input name=\"p\" oscarDB=\"e$count#c\" var$value=\"{c}\">"
                + "</form>";
        EForm template = persistTemplate("FAKE_SELECT_ENTITY_DEP", templateHtml);
        persistSavedValue(template.getId(), patient.getDemographicNo(), "c", "Smith & Sons");
        persistSavedValue(template.getId(), patient.getDemographicNo(), "c", "Smith &amp; Sons");
        persistSavedValue(template.getId(), patient.getDemographicNo(), "c", "Smith &amp; Sons");

        Document page = renderWithH2CompatibleCountAp(template.getId(), patient.getDemographicNo());
        assertThat(page.select("select[name=c] option[selected]").eachAttr("value"))
                .containsExactly("Smith & Sons");
        // Matches only the 1 saved row carrying decoded "Smith & Sons", not the 2 raw-entity rows.
        assertThat(page.selectFirst("input[name=p]").attr("value")).isEqualTo("1");
    }

    @Test
    @DisplayName("should ignore inert template form and disabled select when binding dependent e$count query")
    void shouldIgnoreTemplateFormAndDisabledSelect_whenDependentCountRunsAgainstDatabase() throws Exception {
        Demographic patient = persistPatient("Unused");
        String templateHtml = "<template><form><select name=\"c\"><option selected>X</option></select></form></template>"
                + "<form>"
                + "<select name=\"c\"><option>R</option></select>"
                + "<select name=\"c\" disabled><option selected>X</option></select>"
                + "<input oscarDB=\"e$count#c\" var$value=\"{c}\">"
                + "</form>";
        EForm template = persistTemplate("FAKE_SELECT_TPL_DISABLED", templateHtml);
        persistSavedValue(template.getId(), patient.getDemographicNo(), "c", "R");
        persistSavedValue(template.getId(), patient.getDemographicNo(), "c", "X");
        persistSavedValue(template.getId(), patient.getDemographicNo(), "c", "X");

        Document page = renderWithH2CompatibleCountAp(template.getId(), patient.getDemographicNo());
        // Matches only the 1 saved row for live enabled "R", not the 2 "X" rows.
        assertThat(page.selectFirst("input").attr("value")).isEqualTo("1");
    }

    /**
     * Runs {@code setDatabaseAPs()} against real H2 tables using the bundled {@code apconfig.xml} definitions,
     * adapting only {@code _eform_values_count}'s MySQL {@code status=1} predicate to H2's {@code BOOLEAN}
     * {@code status=true}.
     */
    private Document renderWithH2CompatibleCountAp(Integer fid, Integer demographicNo) {
        EFormLoader.getInstance();
        DatabaseAP bundledCount = EFormLoader.getAP("_eform_values_count");
        DatabaseAP h2Count = new DatabaseAP(
                bundledCount.getApName(),
                bundledCount.getApSQL().replace("status=1", "status=true"),
                bundledCount.getApOutput());
        try (var loader = mockStatic(EFormLoader.class, CALLS_REAL_METHODS)) {
            loader.when(() -> EFormLoader.getAP("_eform_values_count")).thenReturn(h2Count);
            io.github.carlos_emr.carlos.eform.data.EForm rendered =
                    new io.github.carlos_emr.carlos.eform.data.EForm(
                            fid.toString(), demographicNo.toString(), "999998");
            rendered.setDatabaseAPs();
            return Jsoup.parse(rendered.getFormHtml());
        }
    }

    @Test
    @DisplayName("should preserve no-break space option selection across setValues save and fdid reload")
    void shouldPreserveNoBreakSpaceSelection_whenSavedAndReloadedByFdid() throws Exception {
        Demographic patient = persistPatient("None");
        String templateHtml = "<html><body><form>"
                + "<select name=\"Status\"><option>Not done</option><option>Not&nbsp;done</option></select>"
                + "</form></body></html>";
        EForm template = persistTemplate("FAKE_SELECT_NBSP_RESTORE", templateHtml);

        io.github.carlos_emr.carlos.eform.data.EForm form =
                new io.github.carlos_emr.carlos.eform.data.EForm(
                        template.getId().toString(), patient.getDemographicNo().toString(), "999998");
        form.setValues(new ArrayList<>(List.of("Status")), new ArrayList<>(List.of("Not\u00a0done")));

        EFormData saved = new EFormData();
        saved.setFormId(template.getId());
        saved.setFormName(template.getFormName());
        saved.setSubject("FAKE NBSP subject");
        saved.setDemographicId(patient.getDemographicNo());
        saved.setCurrent(true);
        saved.setFormDate(new Date());
        saved.setFormTime(new Date());
        saved.setProviderNo("999998");
        saved.setFormData(form.getFormHtml());
        eFormDataDao.persist(saved);
        entityManager.flush();

        io.github.carlos_emr.carlos.eform.data.EForm reopened =
                new io.github.carlos_emr.carlos.eform.data.EForm(saved.getId().toString());
        List<Element> options = Jsoup.parse(reopened.getFormHtml()).select("select[name=Status] option");

        assertThat(options).hasSize(2);
        assertThat(options.get(0).hasAttr("selected")).isFalse();
        assertThat(options.get(1).hasAttr("selected")).isTrue();
    }

    private Demographic persistPatient(String alias) {
        Demographic patient = new Demographic();
        patient.setLastName("FAKE_SELECT_LAST");
        patient.setFirstName("FAKE_SELECT_FIRST");
        patient.setSex("F");
        patient.setYearOfBirth("1985");
        patient.setMonthOfBirth("04");
        patient.setDateOfBirth("12");
        patient.setPatientStatus("AC");
        patient.setAlias(alias);
        demographicDao.save(patient);
        entityManager.flush();
        return patient;
    }

    private EForm persistTemplate(String name, String html) throws Exception {
        EForm template = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(template);
        template.setFormName(name);
        template.setCurrent(true);
        template.setPatientIndependent(false);
        template.setShowLatestFormOnly(false);
        template.setFormHtml(html);
        eFormDao.persist(template);
        entityManager.flush();
        return template;
    }

    private void persistSavedValue(Integer fid, Integer demographicNo, String varName, String varValue) {
        EFormData data = new EFormData();
        data.setFormId(fid);
        data.setFormName("FAKE_SAVED");
        data.setSubject("FAKE");
        data.setDemographicId(demographicNo);
        data.setCurrent(true);
        data.setFormDate(new Date());
        data.setFormTime(new Date());
        data.setProviderNo("999998");
        data.setFormData("<form></form>");
        eFormDataDao.persist(data);
        entityManager.flush();

        EFormValue value = new EFormValue();
        value.setFormDataId(data.getId());
        value.setFormId(fid);
        value.setDemographicId(demographicNo);
        value.setVarName(varName);
        value.setVarValue(varValue);
        eFormValueDao.persist(value);
        entityManager.flush();
    }
}
