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

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link EFormUtil} behavior used by the eForm edit JSP.
 *
 * @since 2026-04-22
 */
@DisplayName("EFormUtil Integration Tests")
@Tag("integration")
@Tag("eform")
@Transactional
class EFormUtilIntegrationTest extends CarlosTestBase {

    @Autowired
    private EFormDao eFormDao;

    @Test
    @DisplayName("should return fid as String when loading persisted eForm")
    void shouldReturnFidAsString_whenLoadingPersistedEForm() throws Exception {
        EForm persistedForm = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(persistedForm);
        persistedForm.setFormName("LOAD_EFORM_FID_TYPE");
        eFormDao.persist(persistedForm);
        hibernateTemplate.flush();

        HashMap<String, Object> loadedForm = EFormUtil.loadEForm(persistedForm.getId().toString());

        assertThat(loadedForm).containsEntry("fid", persistedForm.getId().toString());
    }

    @Test
    @DisplayName("should return no such form message when loading unknown eForm")
    void shouldReturnNoSuchFormMessage_whenLoadingUnknownEForm() {
        HashMap<String, Object> loadedForm = EFormUtil.loadEForm(String.valueOf(Integer.MAX_VALUE));

        assertThat(loadedForm).doesNotContainKey("fid");
        assertThat(loadedForm).containsEntry("formName", "");
        assertThat(loadedForm).containsEntry("formHtml", "No Such Form in Database");
    }

    @Test
    @DisplayName("should list all current eForms for admins")
    void shouldListAllCurrentEForms_whenProviderIsAdmin() throws Exception {
        EForm ownForm = persistCurrentForm("ADMIN_VISIBLE_OWN", "doc1");
        EForm otherForm = persistCurrentForm("ADMIN_VISIBLE_OTHER", "doc2");
        EForm sharedForm = persistCurrentForm("ADMIN_VISIBLE_SHARED", null);

        List<HashMap<String, ? extends Object>> forms =
                EFormUtil.listEFormsForProvider(EFormUtil.NAME, EFormUtil.CURRENT, "doc1", true);

        assertThat(formNames(forms))
                .contains("ADMIN_VISIBLE_OWN", "ADMIN_VISIBLE_OTHER", "ADMIN_VISIBLE_SHARED");
        assertCreator(forms, ownForm.getId().toString(), "doc1");
        assertCreator(forms, otherForm.getId().toString(), "doc2");
        assertCreator(forms, sharedForm.getId().toString(), null);
    }

    @Test
    @DisplayName("should list only owned and shared current eForms for non-admin providers")
    void shouldListOwnedAndSharedCurrentEForms_whenProviderIsNotAdmin() throws Exception {
        EForm ownForm = persistCurrentForm("PROVIDER_VISIBLE_OWN", "doc1");
        EForm sharedForm = persistCurrentForm("PROVIDER_VISIBLE_SHARED", null);
        persistCurrentForm("PROVIDER_HIDDEN_OTHER", "doc2");

        List<HashMap<String, ? extends Object>> forms =
                EFormUtil.listEFormsForProvider(EFormUtil.NAME, EFormUtil.CURRENT, "doc1", false);

        assertThat(formNames(forms))
                .contains("PROVIDER_VISIBLE_OWN", "PROVIDER_VISIBLE_SHARED")
                .doesNotContain("PROVIDER_HIDDEN_OTHER");
        assertCreator(forms, ownForm.getId().toString(), "doc1");
        assertCreator(forms, sharedForm.getId().toString(), null);
    }

    @Test
    @DisplayName("should list only shared current eForms when provider number is null")
    void shouldListOnlySharedCurrentEForms_whenProviderNumberIsNull() throws Exception {
        persistCurrentForm("NULL_PROVIDER_SHARED", null);
        persistCurrentForm("NULL_PROVIDER_OWNED", "doc1");

        List<HashMap<String, ? extends Object>> forms =
                EFormUtil.listEFormsForProvider(EFormUtil.NAME, EFormUtil.CURRENT, null, false);

        assertThat(formNames(forms))
                .contains("NULL_PROVIDER_SHARED")
                .doesNotContain("NULL_PROVIDER_OWNED");
        assertThat(forms)
                .allSatisfy(form -> assertThat(form).containsKey(EFormUtil.FORM_CREATOR_KEY));
    }

    private EForm persistCurrentForm(String formName, String creator) throws Exception {
        EForm eForm = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(eForm);
        eForm.setFormName(formName);
        eForm.setCurrent(true);
        eForm.setCreator(creator);
        eFormDao.persist(eForm);
        hibernateTemplate.flush();
        return eForm;
    }

    private List<String> formNames(List<HashMap<String, ? extends Object>> forms) {
        return forms.stream()
                .map(form -> (String) form.get("formName"))
                .toList();
    }

    private void assertCreator(List<HashMap<String, ? extends Object>> forms, String fid, String expectedCreator) {
        assertThat(forms)
                .filteredOn(form -> fid.equals(form.get("fid")))
                .singleElement()
                .satisfies(form -> assertThat(form.get(EFormUtil.FORM_CREATOR_KEY)).isEqualTo(expectedCreator));
    }
}
