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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the encoding contract of the form/eForm preference checkbox builders:
 * form names come from admin-editable DB rows and render into both an HTML
 * attribute (value) and HTML body content (label).
 */
@DisplayName("UserPreference2Action form checkbox HTML")
@Tag("unit")
class UserPreference2ActionFormHtmlUnitTest extends CarlosUnitTestBase {

    private static final String HOSTILE_NAME = "\"><script>alert(1)</script>";

    @Test
    void shouldEncodeFormName_whenRenderingEncounterFormCheckboxes() {
        EncounterFormDao encounterFormDao = createAndRegisterMock(EncounterFormDao.class);
        EncounterForm form = new EncounterForm();
        form.setFormName(HOSTILE_NAME);
        when(encounterFormDao.findAll()).thenReturn(new ArrayList<>(List.of(form)));

        String html = UserPreference2Action.getEncounterFormHTML(Map.of(), "encounterFormName");

        assertThat(html)
                .contains("value=\"" + SafeEncode.forHtmlAttribute(HOSTILE_NAME) + "\"")
                .contains(SafeEncode.forHtmlContent(HOSTILE_NAME))
                .doesNotContain(HOSTILE_NAME);
    }

    @Test
    void shouldEncodeFormName_whenRenderingEformCheckboxes() {
        EFormDao eFormDao = createAndRegisterMock(EFormDao.class);
        EForm eform = new EForm();
        eform.setFormName(HOSTILE_NAME);
        ReflectionTestUtils.setField(eform, "id", 7);
        when(eFormDao.findAll(true)).thenReturn(new ArrayList<>(List.of(eform)));

        String html = UserPreference2Action.getEformHTML(Map.of(), "eformName");

        assertThat(html)
                .contains("value=\"7\"")
                .contains(SafeEncode.forHtmlContent(HOSTILE_NAME))
                .doesNotContain(HOSTILE_NAME);
    }
}
