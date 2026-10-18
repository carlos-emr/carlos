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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prescribing-card input and correction handling in {@link RxWriteScript2Action}:
 * <ul>
 *   <li>a non-numeric inline Min/Max or repeats is a 400 the card can explain, not an HTTP 500,
 *       and the staged card keeps its value (#5003);</li>
 *   <li>a full written or start date that is not a real day is refused before any card changes,
 *       instead of rolling 2026-02-31 over to 2026-03-03 (#5003);</li>
 *   <li>saving a card again after Save And Print archives the row its first save wrote, so the
 *       corrected medication is the only active one (#5005).</li>
 * </ul>
 *
 * @since 2026-10-18
 */
@DisplayName("RxWriteScript2Action card input and correction")
@Tag("unit")
@Tag("prescript")
class RxWriteScript2ActionCardCorrectionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
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
    private MockHttpServletResponse response;
    private RxSessionBean bean;
    private RxPrescriptionData.Prescription card;
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
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo("999998");
        card = new RxPrescriptionData.Prescription(0, "999998", DEMOGRAPHIC_NO);
        card.setRandomId(1);
        card.setBrandName("SYNTHETIC DRUG");
        card.setSpecial("1 tab PO OD");
        card.setQuantity("30");
        card.setTakeMin(1);
        card.setTakeMax(1);
        card.setRepeat(2);
        bean.getStashList().add(card);
        bean.setStashIndex(0);
        RxSessionBeanResolver.register(request.getSession(), bean);

        action = new RxWriteScript2Action(mockSignatureStampService);
    }

    @AfterEach
    void tearDown() throws Exception {
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

    @Nested
    @DisplayName("inline Min/Max and repeats (#5003)")
    class InlineProperty {

        @ParameterizedTest
        @ValueSource(strings = {"minimum_1", "maximum_1"})
        @DisplayName("should refuse a non-numeric dose with 400 and keep the staged dose")
        void shouldRefuseDose_whenValueIsNotANumber(String field) throws Exception {
            for (String value : List.of("one", "1 tab", "-1", "1e3", "NaN", "Infinity", "1/2")) {
                response = new MockHttpServletResponse();
                servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
                action = new RxWriteScript2Action(mockSignatureStampService);
                request.setParameter("elementId", field);
                request.setParameter("propertyValue", value);

                assertThat(action.updateProperty()).as(value).isEqualTo(RxWriteScript2Action.NONE);

                assertThat(response.getStatus()).as(value).isEqualTo(400);
                assertThat(response.getContentAsString()).as(value).contains("INVALID_RX_DOSE");
                assertThat(card.getTakeMin()).isEqualTo(1f);
                assertThat(card.getTakeMax()).isEqualTo(1f);
            }
        }

        @Test
        @DisplayName("should store a fractional dose")
        void shouldStoreDose_whenValueIsFractional() throws Exception {
            request.setParameter("elementId", "maximum_1");
            request.setParameter("propertyValue", " 1.5 ");

            assertThat(action.updateProperty()).isNull();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(card.getTakeMax()).isEqualTo(1.5f);
        }

        @Test
        @DisplayName("should refuse repeats that are not a whole number")
        void shouldRefuseRepeats_whenValueIsNotWholeNumber() throws Exception {
            request.setParameter("elementId", "repeats_1");
            request.setParameter("propertyValue", "two");

            assertThat(action.updateProperty()).isEqualTo(RxWriteScript2Action.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("INVALID_RX_REPEATS");
            assertThat(card.getRepeat()).isEqualTo(2);
        }

        @Test
        @DisplayName("should read only plain non-negative decimals as doses")
        void shouldParseDose_forPlainDecimals() {
            assertThat(RxWriteScript2Action.parseDoseAmount("1")).isEqualTo(1f);
            assertThat(RxWriteScript2Action.parseDoseAmount("0.5")).isEqualTo(0.5f);
            assertThat(RxWriteScript2Action.parseDoseAmount(".25")).isEqualTo(0.25f);
            assertThat(RxWriteScript2Action.parseDoseAmount("0.33333334")).isEqualTo(0.33333334f);
            for (String value : new String[]{null, "", "abc", "-1", "+1", "1e3", "NaN", "Infinity", "1,5", "0x10"}) {
                assertThat(RxWriteScript2Action.parseDoseAmount(value)).as(String.valueOf(value)).isNull();
            }
        }
    }

    @Nested
    @DisplayName("written and start dates (#5003)")
    class CalendarDates {

        @ParameterizedTest
        @ValueSource(strings = {"writtenDate_1", "rxDate_1"})
        @DisplayName("should refuse a full date the calendar does not have before changing the card")
        void shouldRefuseSave_whenDateIsNotOnCalendar(String field) throws Exception {
            request.setParameter("drugName_1", "CHANGED NAME");
            request.setParameter(field, "2026-02-31");
            request.setParameter("draftRevision_1", card.getDraftRevision());
            action = spy(action);

            assertThat(action.updateSaveAllDrugs()).isEqualTo(RxWriteScript2Action.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("INVALID_RX_DATE");
            assertThat(card.getBrandName()).isEqualTo("SYNTHETIC DRUG");
            verify(action, never()).persistStash(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-02-28", "2028-02-29", "2026-02", "2026", ""})
        @DisplayName("should save a real or partial written date")
        void shouldSave_whenWrittenDateIsRealOrPartial(String value) throws Exception {
            request.setParameter("drugName_1", "SYNTHETIC DRUG");
            request.setParameter("writtenDate_1", value);
            request.setParameter("draftRevision_1", card.getDraftRevision());
            action = spy(action);
            doReturn("9001").when(action).persistStash(mockLoggedInInfo, bean);

            assertThat(action.updateSaveAllDrugs()).isEqualTo("refresh");

            verify(action).persistStash(mockLoggedInInfo, bean);
        }

        @Test
        @DisplayName("should judge only full dates against the calendar")
        void shouldCheckCalendar_forFullDatesOnly() {
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial("2026-02-31")).isFalse();
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial("2026/04/31")).isFalse();
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial("2026-13-01")).isFalse();
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial(" 2028-02-29 ")).isTrue();
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial(null)).isTrue();
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial("2026-02")).isTrue();
            assertThat(RxWriteScript2Action.isCalendarDateOrPartial("unknown")).isTrue();
        }
    }

    @Nested
    @DisplayName("saving an acknowledged card again (#5005)")
    class CorrectedSave {

        private final Deque<Integer> nextDrugIds = new ArrayDeque<>(List.of(501, 502, 503));

        @BeforeEach
        void persistAssignsIds() {
            doAnswer(invocation -> {
                Drug saved = invocation.getArgument(0);
                saved.setId(nextDrugIds.removeFirst());
                return null;
            }).when(mockDrugDao).persist(any(Drug.class));
        }

        private String save() {
            try (MockedConstruction<RxPrescriptionData> data = mockConstruction(RxPrescriptionData.class,
                    (mock, context) -> when(mock.saveScript(any(), any())).thenReturn("77"))) {
                return action.persistStash(mockLoggedInInfo, bean);
            }
        }

        private Drug row(int id, boolean archived) {
            Drug drug = new Drug();
            drug.setId(id);
            drug.setDemographicId(DEMOGRAPHIC_NO);
            drug.setArchived(archived);
            return drug;
        }

        @Test
        @DisplayName("should archive the first save's row when the corrected card is saved again")
        void shouldArchiveEarlierRow_whenCardIsSavedAgain() {
            assertThat(save()).isEqualTo("77");
            assertThat(card.getDrugId()).isEqualTo(501);
            verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), anyString());

            when(mockDrugDao.find(501)).thenReturn(row(501, false));
            when(mockRxManager.archiveDrug(mockLoggedInInfo, 501, DEMOGRAPHIC_NO, Drug.REPRESCRIBED)).thenReturn(true);
            card.setSpecial("1 tab PO BID x 14 days");

            assertThat(save()).isEqualTo("77");

            assertThat(card.getDrugId()).isEqualTo(502);
            verify(mockRxManager).archiveDrug(mockLoggedInInfo, 501, DEMOGRAPHIC_NO, Drug.REPRESCRIBED);
            verify(mockRxManager, never()).archiveDrug(any(), eq(502), anyInt(), anyString());
            logActionMock.verify(() -> LogAction.addLog(eq("999998"), eq(LogConst.REPRESCRIBE), eq(LogConst.CON_MEDICATION),
                    eq("drugid=501"), any(), eq(String.valueOf(DEMOGRAPHIC_NO)), any()));
        }

        @Test
        @DisplayName("should leave a separately staged new prescription and its earlier rows alone")
        void shouldNotArchive_whenNewCardWasNeverSaved() {
            RxPrescriptionData.Prescription another = new RxPrescriptionData.Prescription(0, "999998", DEMOGRAPHIC_NO);
            another.setRandomId(2);
            another.setBrandName("SECOND SYNTHETIC DRUG");
            another.setSpecial("1 tab PO OD");
            bean.getStashList().add(another);

            assertThat(save()).isEqualTo("77");

            assertThat(card.getDrugId()).isEqualTo(501);
            assertThat(another.getDrugId()).isEqualTo(502);
            verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), anyString());
        }

        @Test
        @DisplayName("should keep the earlier row's own reason when it was already archived")
        void shouldNotRearchive_whenEarlierRowAlreadyArchived() {
            save();
            when(mockDrugDao.find(501)).thenReturn(row(501, true));

            assertThat(save()).isEqualTo("77");

            verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), anyString());
        }

        @Test
        @DisplayName("should report an incomplete save when the earlier row cannot be archived")
        void shouldReportIncompleteSave_whenEarlierRowCannotBeArchived() {
            save();
            when(mockDrugDao.find(501)).thenReturn(row(501, false));
            when(mockRxManager.archiveDrug(any(), eq(501), anyInt(), anyString())).thenReturn(false);

            assertThatThrownBy(this::save).isInstanceOf(RxWriteScript2Action.PrescriptionSaveException.class);
        }
    }
}
