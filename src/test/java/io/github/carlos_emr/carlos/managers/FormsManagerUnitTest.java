/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao.EFormSortOrder;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormGroupDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FormsManagerImpl} medical forms management logic.
 *
 * <p>Tests EForm retrieval, group name lookups, encounter form listing,
 * security enforcement, and form-by-demographic queries.</p>
 *
 * @since 2026-03-31
 * @see FormsManagerImpl
 * @see FormsManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FormsManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("forms")
class FormsManagerUnitTest extends CarlosUnitTestBase {

    @Mock private EFormDao mockEFormDao;
    @Mock private EFormGroupDao mockEFormGroupDao;
    @Mock private EFormDataDao mockEFormDataDao;
    @Mock private EncounterFormDao mockEncounterFormDao;
    @Mock private DocumentManager mockDocumentManager;
    @Mock private SecurityInfoManager mockSecurityInfoManager;

    private FormsManagerImpl manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(EFormDao.class, mockEFormDao);
        registerMock(EFormGroupDao.class, mockEFormGroupDao);
        registerMock(EFormDataDao.class, mockEFormDataDao);
        registerMock(EncounterFormDao.class, mockEncounterFormDao);
        registerMock(DocumentManager.class, mockDocumentManager);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        manager = new FormsManagerImpl();
        injectDependency(manager, "eformDao", mockEFormDao);
        injectDependency(manager, "eFormGroupDao", mockEFormGroupDao);
        injectDependency(manager, "eFormDataDao", mockEFormDataDao);
        injectDependency(manager, "encounterFormDao", mockEncounterFormDao);
        injectDependency(manager, "documentManager", mockDocumentManager);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    private void grantFormReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
    }

    private void denyFormReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);
    }

    // -----------------------------------------------------------------------
    // findByStatus
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should return active eForms sorted by specified order")
        void shouldReturnActiveForms_sortedByOrder() {
            EForm form1 = new EForm();
            form1.setId(1);
            form1.setFormName("Blood Work");
            EForm form2 = new EForm();
            form2.setId(2);
            form2.setFormName("Mental Health Assessment");
            when(mockEFormDao.findByStatus(true, EFormSortOrder.NAME)).thenReturn(List.of(form1, form2));

            List<EForm> result = manager.findByStatus(loggedInInfo, true, EFormSortOrder.NAME);

            assertThat(result).hasSize(2);
            verify(mockEFormDao).findByStatus(true, EFormSortOrder.NAME);
        }

        @Test
        @DisplayName("should return empty list when no forms match status")
        void shouldReturnEmptyList_whenNoFormsMatch() {
            when(mockEFormDao.findByStatus(false, null)).thenReturn(Collections.emptyList());

            List<EForm> result = manager.findByStatus(loggedInInfo, false, null);

            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getEfromInGroupByGroupName
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getEfromInGroupByGroupName")
    class GetEformInGroupByGroupName {

        @Test
        @DisplayName("should return eForms in specified group")
        void shouldReturnForms_inGroup() {
            EForm form = new EForm();
            form.setId(1);
            when(mockEFormDao.getEfromInGroupByGroupName("Pediatrics")).thenReturn(List.of(form));

            List<EForm> result = manager.getEfromInGroupByGroupName(loggedInInfo, "Pediatrics");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list for unknown group")
        void shouldReturnEmptyList_forUnknownGroup() {
            when(mockEFormDao.getEfromInGroupByGroupName("NonExistent")).thenReturn(Collections.emptyList());

            List<EForm> result = manager.getEfromInGroupByGroupName(loggedInInfo, "NonExistent");

            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getGroupNames
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getGroupNames")
    class GetGroupNames {

        @Test
        @DisplayName("should return all group names from DAO")
        void shouldReturnGroupNames() {
            when(mockEFormGroupDao.getGroupNames()).thenReturn(List.of("Pediatrics", "Mental Health", "Labs"));

            List<String> result = manager.getGroupNames();

            assertThat(result).containsExactly("Pediatrics", "Mental Health", "Labs");
        }
    }

    // -----------------------------------------------------------------------
    // findByDemographicId
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByDemographicId")
    class FindByDemographicId {

        @Test
        @DisplayName("should return form data for demographic")
        void shouldReturnFormData_forDemographic() {
            EFormData data = new EFormData();
            data.setId(1);
            when(mockEFormDataDao.findByDemographicId(100)).thenReturn(List.of(data));

            List<EFormData> result = manager.findByDemographicId(loggedInInfo, 100);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no form data for demographic")
        void shouldReturnEmptyList_whenNoFormData() {
            when(mockEFormDataDao.findByDemographicId(999)).thenReturn(Collections.emptyList());

            List<EFormData> result = manager.findByDemographicId(loggedInInfo, 999);

            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getAllEncounterForms
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getAllEncounterForms")
    class GetAllEncounterForms {

        @Test
        @DisplayName("should return all encounter forms sorted by name")
        void shouldReturnAllForms_sortedByName() {
            EncounterForm form1 = new EncounterForm();
            form1.setFormName("Zebra Form");
            EncounterForm form2 = new EncounterForm();
            form2.setFormName("Annual Form");
            when(mockEncounterFormDao.findAll()).thenReturn(new ArrayList<>(List.of(form1, form2)));

            List<EncounterForm> result = manager.getAllEncounterForms();

            assertThat(result).hasSize(2);
            // sorted by FORM_NAME_COMPARATOR
            assertThat(result.get(0).getFormName()).isEqualTo("Annual Form");
            assertThat(result.get(1).getFormName()).isEqualTo("Zebra Form");
        }

        @Test
        @DisplayName("should return empty list when no forms exist")
        void shouldReturnEmptyList_whenNoForms() {
            when(mockEncounterFormDao.findAll()).thenReturn(new ArrayList<>());

            List<EncounterForm> result = manager.getAllEncounterForms();

            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getSelectedEncounterForms
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getSelectedEncounterForms")
    class GetSelectedEncounterForms {

        @Test
        @DisplayName("should return only non-hidden forms sorted by name")
        void shouldReturnNonHiddenForms_sortedByName() {
            EncounterForm form = new EncounterForm();
            form.setFormName("Rourke 2020");
            when(mockEncounterFormDao.findAllNotHidden()).thenReturn(new ArrayList<>(List.of(form)));

            List<EncounterForm> result = manager.getSelectedEncounterForms();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFormName()).isEqualTo("Rourke 2020");
        }
    }

    // -----------------------------------------------------------------------
    // getEncounterFormsbyDemographicNumber - security
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getEncounterFormsbyDemographicNumber")
    class GetEncounterFormsByDemographicNumber {

        @Test
        @DisplayName("should throw RuntimeException when form read privilege denied")
        void shouldThrowException_whenFormReadDenied() {
            denyFormReadPrivilege();

            assertThatThrownBy(() -> manager.getEncounterFormsbyDemographicNumber(loggedInInfo, 100, false, false))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("_form");
        }
    }

    // -----------------------------------------------------------------------
    // saveFormDataAsEDoc - security
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("saveFormDataAsEDoc security")
    class SaveFormDataAsEDocSecurity {

        @Test
        @DisplayName("should throw RuntimeException when edoc write privilege denied")
        void shouldThrowException_whenEdocWriteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.saveFormDataAsEDoc(loggedInInfo, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("_eform");
        }
    }

    // -----------------------------------------------------------------------
    // renderForm(LoggedInInfo, FormTransportContainer) - security
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("renderForm security")
    class RenderFormSecurity {

        @Test
        @DisplayName("should throw RuntimeException when form read privilege denied for render")
        void shouldThrowException_whenRenderDenied() {
            denyFormReadPrivilege();

            assertThatThrownBy(() -> manager.renderForm(loggedInInfo, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("_form");
        }
    }

    // -----------------------------------------------------------------------
    // getFormById - security
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getFormById")
    class GetFormById {

        @Test
        @DisplayName("should throw RuntimeException when form read privilege denied")
        void shouldThrowException_whenReadDenied() {
            denyFormReadPrivilege();

            assertThatThrownBy(() -> manager.getFormById(loggedInInfo, 1, 100))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("_form");
        }
    }
}
