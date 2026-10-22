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
    @DisplayName("should list each current eForm with the editor its Edit action opens")
    void shouldListEditorKind_forEachCurrentEForm() throws Exception {
        EForm visual = persistForm("EDITOR_KIND_VISUAL", true,
                "<html><head><meta name='carlos-eform-editor' content='visual'><title>v</title></head>"
                        + "<body><div id='eform_container'><form id=\"inputForm\"></form></div></body></html>");
        EForm generator = persistForm("EDITOR_KIND_GENERATOR", true,
                "<html><body><div id=\"page1\" style=\"position:relative;\"><img id=\"BGImage1\" src=\"a.png\">"
                        + "</div></body></html>");
        EForm handWritten = persistForm("EDITOR_KIND_HTML", true,
                "<html><body><form><textarea name='letter'></textarea></form></body></html>");
        EForm deleted = persistForm("EDITOR_KIND_DELETED", false,
                "<html><head><meta name='carlos-eform-editor' content='visual'></head><body></body></html>");
        hibernateTemplate.flush();

        List<HashMap<String, ? extends Object>> listing =
                EFormUtil.listEFormsWithEditor(EFormUtil.NAME, EFormUtil.CURRENT);

        assertThat(editorKindOf(listing, visual)).isEqualTo(EFormEditorResolver.EditorKind.VISUAL);
        assertThat(editorKindOf(listing, generator)).isEqualTo(EFormEditorResolver.EditorKind.CONVERTIBLE);
        assertThat(editorKindOf(listing, handWritten)).isEqualTo(EFormEditorResolver.EditorKind.HTML);
        assertThat(listing).noneMatch(row -> deleted.getId().toString().equals(row.get("fid")));
    }

    @Test
    @DisplayName("should leave the plain listing without the editor key")
    void shouldOmitEditorKind_fromPlainListing() throws Exception {
        EForm visual = persistForm("EDITOR_KIND_PLAIN_LISTING", true,
                "<html><head><meta name='carlos-eform-editor' content='visual'></head><body></body></html>");
        hibernateTemplate.flush();

        List<HashMap<String, ? extends Object>> listing = EFormUtil.listEForms(EFormUtil.NAME, EFormUtil.CURRENT);

        assertThat(listing).filteredOn(row -> visual.getId().toString().equals(row.get("fid")))
                .singleElement()
                .satisfies(row -> assertThat(row).doesNotContainKey("editorKind"));
    }

    private EForm persistForm(String name, boolean current, String formHtml) throws Exception {
        EForm form = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(form);
        form.setFormName(name);
        form.setCurrent(current);
        form.setFormHtml(formHtml);
        eFormDao.persist(form);
        return form;
    }

    private static Object editorKindOf(List<HashMap<String, ? extends Object>> listing, EForm form) {
        return listing.stream()
                .filter(row -> form.getId().toString().equals(row.get("fid")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("eForm " + form.getFormName() + " is not listed"))
                .get("editorKind");
    }

    @Test
    @DisplayName("should return no such form message when loading unknown eForm")
    void shouldReturnNoSuchFormMessage_whenLoadingUnknownEForm() {
        HashMap<String, Object> loadedForm = EFormUtil.loadEForm(String.valueOf(Integer.MAX_VALUE));

        assertThat(loadedForm).doesNotContainKey("fid");
        assertThat(loadedForm).containsEntry("formName", "");
        assertThat(loadedForm).containsEntry("formHtml", "No Such Form in Database");
    }
}
