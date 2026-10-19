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
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #4809: Administration ▸ Manage Clinic NBR Codes. The page lists codes by row id and Remove
 * Selected posts the selected option's value as that id. A code added on the page was given its typed
 * value as the option value, so removing it right away, before a reload, marked another row deleted.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("admin")
@DisplayName("Manage Clinic NBR Codes add and remove (#4809)")
class ClinicNbrManage2ActionUnitTest extends CarlosUnitTestBase {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/admin/clinicNbrManage.jsp");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/clinicNbrManage");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final ClinicNbrDao clinicNbrDao = mock(ClinicNbrDao.class);
    private final CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    private JsonNode post(String method, String nbr, String nbrDesc) throws Exception {
        request.setParameter("method", method);
        request.setParameter("nbr", nbr);
        if (nbrDesc != null) {
            request.setParameter("nbrDesc", nbrDesc);
        }
        assertThat(new ClinicNbrManage2Action(clinicNbrDao, methodSecurity).execute()).isEqualTo(ActionSupport.NONE);
        return MAPPER.readTree(response.getContentAsString());
    }

    @Test
    @DisplayName("should answer the new row's id with an added code")
    void shouldAnswerNewRowId_whenCodeIsAdded() throws Exception {
        // The typed value (4) is another row's id; the new row is 5.
        when(clinicNbrDao.addEntry("4", "FAKE-NBR new")).thenReturn(5);

        JsonNode json = post("add", "4", "FAKE-NBR new");

        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("id").asInt()).isEqualTo(5);
        assertThat(json.path("nbr").asText()).isEqualTo("4");
        assertThat(json.path("nbrDesc").asText()).isEqualTo("FAKE-NBR new");
    }

    @Test
    @DisplayName("should answer no id when the code could not be added")
    void shouldAnswerNoId_whenAddFails() throws Exception {
        when(clinicNbrDao.addEntry("4", "FAKE-NBR new")).thenReturn(0);

        JsonNode json = post("add", "4", "FAKE-NBR new");

        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.has("id")).isFalse();
    }

    @Test
    @DisplayName("should mark deleted the row whose id Remove Selected posts")
    void shouldRemoveRow_byPostedId() throws Exception {
        when(clinicNbrDao.removeEntry(5)).thenReturn(5);

        JsonNode json = post("remove", "5", null);

        assertThat(json.path("success").asBoolean()).isTrue();
        verify(clinicNbrDao).removeEntry(5);
    }

    @Test
    @DisplayName("should give a code added on the page its row id as the option value, as the listed codes have")
    void shouldGiveAddedOption_theRowIdAsValue() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8);
        assertThat(page).contains("<option value=\"<%=tempNbr.getId()%>\">");
        assertThat(page).contains("jQuery(\"<option>\").val(xml.id)");
        assertThat(page).contains("jQuery(\"#xml_p_nbr\").val(String(xml.id));");
        assertThat(page).doesNotContain("val(xml.nbr)").doesNotContain("<option value='\" + xml.nbr");
    }
}
