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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.apache.struts2.ServletActionContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayPrevention2Action;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.NavBarDisplayDAO;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.summary.PreventionsSummary;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryItemTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1;

/**
 * The eChart's Preventions box and the REST preventions summary look their patient up once per
 * request, through {@link PreventionPageData}, however many prevention types they go through,
 * and still show the same items. The type list is set on the {@link PreventionDisplayConfig}
 * singleton for the test's duration, and {@code PreventionData}'s DAOs, which it resolves when
 * the class loads, are swapped through reflection.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("eChart Preventions box and REST summary lookups, once per request")
class PreventionBoxAndSummaryLookupsUnitTest extends CarlosUnitTestBase {

    private static final String PATIENT = "7";
    private static final int PATIENT_ID = 7;
    private static final String DENIED = "missing required sec object (_demographic)";
    private static final List<String> TYPES =
            List.of("Td", "Flu", "HepB", "PAP", "PSA", "FAKE-Generic-A", "FAKE-Generic-B");

    private final DemographicManager demographicManager = mock(DemographicManager.class);
    private final PreventionDao preventionDao = mock(PreventionDao.class);
    private final PreventionExtDao preventionExtDao = mock(PreventionExtDao.class);
    private final PartialDateDao partialDateDao = mock(PartialDateDao.class);
    private final PreventionManager preventionManager = mock(PreventionManager.class);
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final PreventionDS preventionDS = mock(PreventionDS.class);
    private final LoggedInInfo user = new LoggedInInfo();
    private final Demographic patient = new Demographic();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final Date fluDate = new GregorianCalendar(2024, 9, 1).getTime();
    private final Date pendingDate = new GregorianCalendar(2022, 9, 1).getTime();
    private final Date refusedDate = new GregorianCalendar(2020, 9, 1).getTime();

    private Object savedPreventionDao;
    private Object savedPreventionExtDao;
    private Object savedPartialDateDao;
    private ArrayList<HashMap<String, String>> savedTypeList;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() throws Exception {
        // In case this test is the first to load PreventionData or PreventionDisplayConfig.
        registerMock(PreventionDao.class, preventionDao);
        registerMock(PreventionExtDao.class, preventionExtDao);
        registerMock(PartialDateDao.class, partialDateDao);
        registerMock(CVCImmunizationDao.class, mock(CVCImmunizationDao.class));
        registerMock(CanadianVaccineCatalogueManager.class, mock(CanadianVaccineCatalogueManager.class));
        registerMock(CVCMappingDao.class, mock(CVCMappingDao.class));
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DemographicCustDao.class, mock(DemographicCustDao.class));
        registerMock(PreventionManager.class, preventionManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PreventionDS.class, preventionDS);
        savedPreventionDao = swapPreventionDataField("preventionDao", preventionDao);
        savedPreventionExtDao = swapPreventionDataField("preventionExtDao", preventionExtDao);
        savedPartialDateDao = swapPreventionDataField("partialDateDao", partialDateDao);
        savedTypeList = PreventionDisplayConfig.setPreventionsForTesting(typeList());
        // The box's action reads the request when it is created.
        request.setContextPath("/carlos");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);

        // A FAKE woman aged 46 on whatever day the test runs, so the age rules below (PAP is
        // 21-69) give the same answer every year. Her 46th birthday was yesterday: a day's margin.
        LocalDate born = LocalDate.now().minusYears(46).minusDays(1);
        patient.setYearOfBirth(String.valueOf(born.getYear()));
        patient.setMonthOfBirth(String.format("%02d", born.getMonthValue()));
        patient.setDateOfBirth(String.format("%02d", born.getDayOfMonth()));
        patient.setSex("F");
        when(demographicManager.getDemographic(user, PATIENT)).thenReturn(patient);
        when(demographicManager.getDemographic(user, Integer.valueOf(PATIENT_ID))).thenReturn(patient);
        when(securityInfoManager.hasPrivilege(user, "_prevention", "r", (String) null)).thenReturn(true);
        when(partialDateDao.getDatePartial(anyString(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> call.getArgument(0));
        doAnswer(call -> {
            Prevention prevention = call.getArgument(0);
            prevention.addWarning("Td", "FAKE Td booster overdue");
            return prevention;
        }).when(preventionDS).getMessages(any(Prevention.class));

        io.github.carlos_emr.carlos.commn.model.Prevention flu = prevention(11, "Flu", fluDate);
        io.github.carlos_emr.carlos.commn.model.Prevention refused = prevention(12, "HepB", refusedDate);
        when(refused.isRefused()).thenReturn(true);
        io.github.carlos_emr.carlos.commn.model.Prevention pending = prevention(13, "FAKE-Generic-B", pendingDate);
        when(preventionDao.findByTypeAndDemoNo("Flu", PATIENT_ID)).thenReturn(List.of(flu));
        when(preventionDao.findByTypeAndDemoNo("HepB", PATIENT_ID)).thenReturn(List.of(refused));
        when(preventionDao.findByTypeAndDemoNo("FAKE-Generic-B", PATIENT_ID)).thenReturn(List.of(pending));
        PreventionExt pendingResult = mock(PreventionExt.class);
        when(pendingResult.getkeyval()).thenReturn("result");
        when(pendingResult.getVal()).thenReturn("pending");
        when(preventionExtDao.findByPreventionId(13)).thenReturn(List.of(pendingResult));
        when(preventionManager.getPreventionsByDemographicNo(user, PATIENT_ID)).thenReturn(List.of(flu, refused));
    }

    @AfterEach
    void restore() throws Exception {
        try {
            if (servletActionContext != null) {
                servletActionContext.close();
            }
        } finally {
            // Put back whatever setUp swapped, even if it stopped part-way.
            swapPreventionDataField("preventionDao", savedPreventionDao);
            swapPreventionDataField("preventionExtDao", savedPreventionExtDao);
            swapPreventionDataField("partialDateDao", savedPartialDateDao);
            PreventionDisplayConfig.setPreventionsForTesting(savedTypeList);
        }
    }

    @Test
    @DisplayName("should list the box's items as before, looking the patient up once for every type")
    void shouldListBoxItems_withOnePatientLookupForAllTypes() {
        NavBarDisplayDAO box = new NavBarDisplayDAO();

        assertThat(boxAction().getInfo(sessionBean(), request, box)).isTrue();

        // Warnings first, then the rest by date as the box sorts them: undated first, then newest.
        assertThat(items(box)).extracting(NavBarDisplayDAO.Item::getTitle, NavBarDisplayDAO.Item::getColour,
                NavBarDisplayDAO.Item::getDate).containsExactly(
                tuple("\u26A0 Td", "#FF0000", null),
                tuple("\u25CB PAP", "#999999", null),
                tuple("\u2713 Flu", "#009900", fluDate),
                tuple("\u23F3 FAKE-Generic-B", "#FF00FF", pendingDate),
                tuple("\u2717 HepB", "#FF6600", refusedDate));
        // One checked lookup for the loop, and the one the decision-support input already made.
        verify(demographicManager).getDemographic(user, PATIENT);
        verify(demographicManager).getDemographic(user, Integer.valueOf(PATIENT_ID));
        verifyNoMoreInteractions(demographicManager);
        TYPES.forEach(type -> verify(preventionDao).findByTypeAndDemoNo(type, PATIENT_ID));
    }

    @Test
    @DisplayName("should refuse the box, reading no prevention types, when the user may not read this patient")
    void shouldRefuseBox_whenUserMayNotReadThisPatient() {
        denyThisPatient();
        NavBarDisplayDAO box = new NavBarDisplayDAO();

        assertThatThrownBy(() -> boxAction().getInfo(sessionBean(), request, box)).hasMessage(DENIED);

        assertThat(box.numItems()).isZero();
        verify(preventionDao, never()).findByTypeAndDemoNo(anyString(), anyInt());
    }

    @Test
    @DisplayName("should refuse the box before reading any type when the user lacks patient read in general")
    void shouldRefuseBoxBeforeReadingTypes_whenGeneralPatientReadIsMissing() {
        // A user with a grant for this patient but no _demographic read in general: the String
        // lookup makes the general check and refuses, while the decision-support input's Integer
        // lookup checks this patient only and passes. Without such a grant, that lookup refuses too.
        when(demographicManager.getDemographic(user, PATIENT)).thenThrow(new RuntimeException(DENIED));
        NavBarDisplayDAO box = new NavBarDisplayDAO();

        assertThatThrownBy(() -> boxAction().getInfo(sessionBean(), request, box)).hasMessage(DENIED);

        assertThat(box.numItems()).isZero();
        verify(preventionDao, never()).findByTypeAndDemoNo(anyString(), anyInt());
    }

    @Test
    @DisplayName("should leave the box out, looking nothing up, when the user may not read preventions")
    void shouldLeaveBoxOut_whenUserMayNotReadPreventions() {
        when(securityInfoManager.hasPrivilege(user, "_prevention", "r", (String) null)).thenReturn(false);
        NavBarDisplayDAO box = new NavBarDisplayDAO();

        assertThat(boxAction().getInfo(sessionBean(), request, box)).isTrue();

        assertThat(box.numItems()).isZero();
        verifyNoInteractions(demographicManager, preventionDao);
    }

    @Test
    @DisplayName("should list the summary's items as before, looking the patient up once for every type")
    void shouldListSummaryItems_withOnePatientLookupForAllTypes() {
        SummaryTo1 summary = summary().getSummary(user, PATIENT_ID, SummaryTo1.PREVENTIONS);

        // Warnings, then shown types with nothing recorded, then the recorded preventions.
        assertThat(summary.getSummaryItem()).extracting(SummaryItemTo1::getId, SummaryItemTo1::getDisplayName,
                SummaryItemTo1::getIndicatorClass, SummaryItemTo1::getWarning).containsExactly(
                tuple(0, "Td", "highlight", "FAKE Td booster overdue"),
                tuple(0, "PAP", null, null),
                tuple(0, "FAKE-Generic-B", null, null),
                tuple(11, "Flu", null, null),
                tuple(12, "HepB", "refused", null));
        verify(demographicManager).getDemographic(user, PATIENT);
        verify(demographicManager).getDemographic(user, Integer.valueOf(PATIENT_ID));
        verifyNoMoreInteractions(demographicManager);
        TYPES.forEach(type -> verify(preventionDao).findByTypeAndDemoNo(type, PATIENT_ID));
    }

    @Test
    @DisplayName("should refuse the summary, reading no prevention types, when the user may not read this patient")
    void shouldRefuseSummary_whenUserMayNotReadThisPatient() {
        denyThisPatient();

        assertThatThrownBy(() -> summary().getSummary(user, PATIENT_ID, SummaryTo1.PREVENTIONS))
                .hasMessage(DENIED);
        verify(preventionDao, never()).findByTypeAndDemoNo(anyString(), anyInt());
    }

    @Test
    @DisplayName("should refuse the summary before reading any type when the user lacks patient read in general")
    void shouldRefuseSummaryBeforeReadingTypes_whenGeneralPatientReadIsMissing() {
        when(demographicManager.getDemographic(user, PATIENT)).thenThrow(new RuntimeException(DENIED));

        assertThatThrownBy(() -> summary().getSummary(user, PATIENT_ID, SummaryTo1.PREVENTIONS))
                .hasMessage(DENIED);
        verify(preventionDao, never()).findByTypeAndDemoNo(anyString(), anyInt());
    }

    /** Both lookups refuse, as DemographicManager does when the user may not read this patient. */
    private void denyThisPatient() {
        when(demographicManager.getDemographic(user, PATIENT)).thenThrow(new RuntimeException(DENIED));
        when(demographicManager.getDemographic(user, Integer.valueOf(PATIENT_ID))).thenThrow(new RuntimeException(DENIED));
    }

    private EctDisplayPrevention2Action boxAction() {
        return new EctDisplayPrevention2Action() {
            @Override
            public String getText(String key) {
                return key;
            }
        };
    }

    private PreventionsSummary summary() {
        PreventionsSummary summary = new PreventionsSummary();
        ReflectionTestUtils.setField(summary, "preventionManager", preventionManager);
        return summary;
    }

    private EctSessionBean sessionBean() {
        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = PATIENT;
        return bean;
    }

    private static List<NavBarDisplayDAO.Item> items(NavBarDisplayDAO box) {
        List<NavBarDisplayDAO.Item> items = new ArrayList<>();
        for (int i = 0; i < box.numItems(); i++) {
            items.add(box.getItem(i));
        }
        return items;
    }

    /** Types like PreventionItems.xml's and the vaccine catalogue's generic ones. */
    private static ArrayList<HashMap<String, String>> typeList() {
        ArrayList<HashMap<String, String>> types = new ArrayList<>();
        types.add(type("Td", Map.of("minAge", "18")));
        types.add(type("Flu", Map.of("minAge", "6")));
        types.add(type("HepB", Map.of("minAge", "18")));
        types.add(type("PAP", Map.of("minAge", "21", "maxAge", "69", "sex", "F")));
        types.add(type("PSA", Map.of("sex", "M")));
        types.add(type("FAKE-Generic-A", Map.of("showIfMinRecordNum", "1")));
        types.add(type("FAKE-Generic-B", Map.of("showIfMinRecordNum", "1")));
        return types;
    }

    private static HashMap<String, String> type(String name, Map<String, String> rules) {
        HashMap<String, String> type = new HashMap<>(rules);
        type.put("name", name);
        type.put("desc", name + " (FAKE description)");
        return type;
    }

    private static io.github.carlos_emr.carlos.commn.model.Prevention prevention(int id, String type, Date date) {
        io.github.carlos_emr.carlos.commn.model.Prevention prevention =
                mock(io.github.carlos_emr.carlos.commn.model.Prevention.class);
        when(prevention.getId()).thenReturn(id);
        when(prevention.getPreventionType()).thenReturn(type);
        when(prevention.getPreventionDate()).thenReturn(date);
        return prevention;
    }

    private static Object swapPreventionDataField(String name, Object value) throws Exception {
        Field field = PreventionData.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
