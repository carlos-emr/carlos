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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxInteractionData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Staging a catalogue drug keeps the name the prescriber picked exactly as it was chosen (#3952).
 *
 * <p>{@code createNewRx} used to run the picked name through {@code Encode.forJava} before storing
 * it, so {@code CHILDREN'S ...} was staged, saved and printed as {@code CHILDREN\'S ...}. The name
 * is clinical text: it is stored raw and every view encodes it for its own output context.
 *
 * @since 2026-10-08
 */
@DisplayName("RxWriteScript2Action createNewRx drug name storage")
@Tag("unit")
@Tag("prescript")
class RxWriteScript2ActionCreateNewRxUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final String DRUG_ID = "17210";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<RxPatientData> patientDataMock;
    private MockedStatic<RxInteractionData> interactionDataMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private UserPropertyDAO mockUserPropertyDAO;
    @Mock
    private PartialDateDao mockPartialDateDao;
    @Mock
    private DemographicManager mockDemographicManager;
    @Mock
    private RxManager mockRxManager;
    @Mock
    private DrugDao mockDrugDao;
    @Mock
    private PrescriptionSignatureStampService mockSignatureStampService;
    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private RxSessionBean bean;
    private RxWriteScript2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(UserPropertyDAO.class, mockUserPropertyDAO);
        registerMock(PartialDateDao.class, mockPartialDateDao);
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(RxManager.class, mockRxManager);
        registerMock(DrugDao.class, mockDrugDao);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        // Staging a card starts the interaction and allergy preloads on worker threads; stub their
        // sources so no worker reaches the DrugRef service (construction mocks are thread-local).
        registerMock(AllergyDao.class, mock(AllergyDao.class));
        RxPatientData.Patient patient = mock(RxPatientData.Patient.class);
        when(patient.getActiveAllergies()).thenReturn(new Allergy[0]);
        patientDataMock = mockStatic(RxPatientData.class);
        patientDataMock.when(() -> RxPatientData.getPatient(any(LoggedInInfo.class), anyInt())).thenReturn(patient);
        interactionDataMock = mockStatic(RxInteractionData.class);
        interactionDataMock.when(RxInteractionData::getInstance).thenReturn(mock(RxInteractionData.class));

        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo("999998");
        RxSessionBeanResolver.register(request.getSession(), bean);

        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("randomId", "4242");
        request.setParameter("drugId", DRUG_ID);

        action = new RxWriteScript2Action(mockSignatureStampService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (interactionDataMock != null) {
            interactionDataMock.close();
        }
        if (patientDataMock != null) {
            patientDataMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "CHILDREN'S BENADRYL ALLERGY LIQUID",
            "TYLENOL \"EXTRA STRENGTH\" 500MG",
            "ACÉTAMINOPHÈNE O'NEIL \"FORTE\" \\ 325MG"})
    @DisplayName("should stage the picked name exactly as chosen when it holds quotes, accents or backslashes")
    void shouldStagePickedNameRaw_whenNameHoldsQuotes(String pickedName) throws Exception {
        request.setParameter("text", pickedName);

        RxPrescriptionData.Prescription staged = stage(monograph("CHILDREN'S BENADRYL ALLERGY LIQUID"));

        assertThat(staged.getDrugPrescribed()).isEqualTo(pickedName);
        assertThat(staged.getBrandName()).isEqualTo("CHILDREN'S BENADRYL ALLERGY LIQUID");
        assertThat(request.getAttribute("rxStageError")).isNull();
    }

    @Test
    @DisplayName("should fall back to the raw picked name as brand name when DrugRef has no product name")
    void shouldUseRawPickedName_forBrandNameWithoutProduct() throws Exception {
        request.setParameter("text", "CHILDREN'S \"JUNIOR\" SYRUP");

        RxPrescriptionData.Prescription staged = stage(monograph(null));

        assertThat(staged.getBrandName()).isEqualTo("CHILDREN'S \"JUNIOR\" SYRUP");
        assertThat(staged.getDrugPrescribed()).isEqualTo("CHILDREN'S \"JUNIOR\" SYRUP");
        assertThat(staged.getBrandName()).doesNotContain("\\");
    }

    private RxPrescriptionData.Prescription stage(RxDrugData.DrugMonograph monograph) throws Exception {
        try (MockedConstruction<RxDrugData> drugData = mockConstruction(RxDrugData.class,
                (mock, context) -> when(mock.getDrug2(anyString())).thenReturn(monograph))) {
            assertThat(action.createNewRx()).isEqualTo("newRx");
            verify(drugData.constructed().get(0)).getDrug2(DRUG_ID);
        }
        assertThat(bean.getStashSize()).as("the card is staged").isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<RxPrescriptionData.Prescription> rendered =
                (List<RxPrescriptionData.Prescription>) request.getAttribute("listRxDrugs");
        assertThat(rendered).as("the staged card is rendered").containsExactly(bean.getStashItem(0));
        return bean.getStashItem(0);
    }

    /** The shape the live DrugRef returns for a single-ingredient product, without needing the service. */
    private static RxDrugData.DrugMonograph monograph(String product) {
        Hashtable<String, Object> hash = new Hashtable<>();
        hash.put("name", "DIPHENHYDRAMINE HYDROCHLORIDE");
        hash.put("atc", "R06AA02");
        if (product != null) {
            hash.put("product", product);
        }
        hash.put("regional_identifier", "02017849");
        hash.put("drugForm", "LIQUID");
        hash.put("drugId", DRUG_ID);
        Vector<String> route = new Vector<>();
        route.add("ORAL");
        hash.put("drugRoute", route);
        Vector<Hashtable<String, Object>> components = new Vector<>();
        Hashtable<String, Object> component = new Hashtable<>();
        component.put("name", "DIPHENHYDRAMINE HYDROCHLORIDE");
        component.put("unit", "MG");
        component.put("strength", "12.5");
        components.add(component);
        hash.put("components", components);
        return new RxDrugData().new DrugMonograph(hash);
    }
}
