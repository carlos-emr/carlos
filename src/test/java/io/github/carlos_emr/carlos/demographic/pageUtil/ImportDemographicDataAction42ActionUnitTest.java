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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportDemographicDataAction42Action}.
 *
 * @since 2026-05-03
 */
@DisplayName("ImportDemographicDataAction42Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class ImportDemographicDataAction42ActionUnitTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";

    @Mock
    private EctProgramManager mockEctProgramManager;

    private ImportDemographicDataAction42Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(EctProgramManager.class, mockEctProgramManager);
        replaceSpringUtilsBean(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class, mockSecurityInfoManager);

        getMockSession().getServletContext().setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        when(mockEctProgramManager.getProgramBeans(TEST_PROVIDER, null))
                .thenReturn(List.of(new LabelValueBean("Default Program", "0")));
        when(mockEctProgramManager.getDefaultProgramId(TEST_PROVIDER)).thenReturn(0);

        action = new ImportDemographicDataAction42Action();
    }

    @Test
    @DisplayName("should return success when no upload is present")
    void shouldReturnSuccess_whenNoUploadIsPresent() throws Exception {
        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should return success when uploaded filename is blank")
    void shouldReturnSuccess_whenUploadedFilenameIsBlank() throws Exception {
        action.setImportFile(new File("dummy-upload"));
        action.setImportFileFileName(" ");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should return logout when user session attribute is missing")
    void shouldReturnLogout_whenUserSessionAttributeIsMissing() throws Exception {
        setSessionAttribute("user", null);

        String result = executeAction(action);

        assertThat(result).isEqualTo("logout");
    }
}
