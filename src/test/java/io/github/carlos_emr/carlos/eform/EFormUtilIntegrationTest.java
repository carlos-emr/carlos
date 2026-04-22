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
        assertThat(loadedForm.get("fid")).isInstanceOf(String.class);
    }
}
