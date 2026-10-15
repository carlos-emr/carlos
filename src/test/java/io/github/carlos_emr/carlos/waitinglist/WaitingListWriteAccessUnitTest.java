/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.waitinglist;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.demographic.pageUtil.DemographicUpdate2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.pageUtil.WLAdd2WaitingList2Action;
import io.github.carlos_emr.carlos.waitinglist.util.WaitingListAccess;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifies fresh list/group checks before either stage of the waiting-list write workflow. */
@Tag("unit")
class WaitingListWriteAccessUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ProviderPreferenceDao preferences;
    private WaitingListNameDao lists;
    private DemographicDao demographics;
    private LoggedInInfo login;
    private CarlosProperties properties;
    private ProviderPreference preference;
    private MockedStatic<CarlosProperties> propertyContext;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedStatic<WLWaitingListUtil> writes;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        response = new MockHttpServletResponse();
        preferences = mock(ProviderPreferenceDao.class);
        lists = mock(WaitingListNameDao.class);
        demographics = mock(DemographicDao.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        properties = mock(CarlosProperties.class);
        when(login.getLoggedInProviderNo()).thenReturn("owner");
        when(security.hasPrivilege(login, "_demographic", "w", null)).thenReturn(true);
        when(properties.getBooleanProperty("DEMOGRAPHIC_WAITING_LIST", "true")).thenReturn(true);
        registerMock(ProviderPreferenceDao.class, preferences);
        registerMock(WaitingListNameDao.class, lists);
        registerMock(DemographicDao.class, demographics);
        registerMock(SecurityInfoManager.class, security);
        preference = new ProviderPreference();
        preference.setMyGroupNo("groupA");
        when(preferences.find("owner")).thenReturn(preference);
        WaitingListName list = new WaitingListName();
        list.setId(12);
        when(lists.findCurrentByGroup("groupA")).thenReturn(List.of(list));
        when(lists.findCurrentByGroup("groupB")).thenReturn(List.of());
        propertyContext = mockStatic(CarlosProperties.class);
        propertyContext.when(CarlosProperties::getInstance).thenReturn(properties);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        writes = mockStatic(WLWaitingListUtil.class);
    }

    @AfterEach
    void tearDown() {
        if (writes != null) writes.close();
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
        if (propertyContext != null) propertyContext.close();
    }

    private String submit(String stage) throws Exception {
        request.setParameter("list_id", "12");
        request.setParameter("listId", "12");
        request.setParameter("demographicNo", "34");
        request.setParameter("waitingListNote", "owned note");
        request.setParameter("onListSince", "2026-03-04");
        return "demographic".equals(stage) ? new DemographicUpdate2Action().execute()
                : new WLAdd2WaitingList2Action().execute();
    }

    private void assertRejected(String stage) throws Exception {
        assertThat(submit(stage)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(demographics);
        writes.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"demographic", "confirmation"})
    void rejectsAListRemovedFromTheCurrentGroupBeforeWriting(String stage) throws Exception {
        when(lists.findCurrentByGroup("groupA")).thenReturn(List.of());
        assertRejected(stage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"demographic", "confirmation"})
    void readsTheNewProviderGroupRatherThanTrustingTheRenderedSelection(String stage) throws Exception {
        assertThat(WaitingListAccess.isAvailable(login, "12")).isTrue();
        preference.setMyGroupNo("groupB");
        assertRejected(stage);
        verify(lists).findCurrentByGroup("groupB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"demographic", "confirmation"})
    void rejectsDisabledWaitingListsBeforeWriting(String stage) throws Exception {
        when(properties.getBooleanProperty("DEMOGRAPHIC_WAITING_LIST", "true")).thenReturn(false);
        assertRejected(stage);
        verifyNoInteractions(preferences, lists);
    }

    @ParameterizedTest
    @ValueSource(strings = {"demographic", "confirmation"})
    void rejectsMissingProviderPreferencesBeforeWriting(String stage) throws Exception {
        when(preferences.find("owner")).thenReturn(null);
        assertRejected(stage);
        verifyNoInteractions(lists);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingProviderGroup(String group) {
        preference.setMyGroupNo(group);
        assertThat(WaitingListAccess.isAvailable(login, "12")).isFalse();
        verifyNoInteractions(lists);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "not-an-id", "2147483648"})
    void rejectsInvalidListIdsWithoutReadingGroupData(String id) {
        assertThat(WaitingListAccess.isAvailable(login, id)).isFalse();
        verifyNoInteractions(preferences, lists);
    }

    @Test
    void rejectsUnauthenticatedAvailabilityChecks() {
        assertThat(WaitingListAccess.isAvailable(null, "12")).isFalse();
        when(login.getLoggedInProviderNo()).thenReturn(null);
        assertThat(WaitingListAccess.isAvailable(login, "12")).isFalse();
        verifyNoInteractions(preferences, lists);
    }

    @Test
    void validConfirmationAddsTheOwnedEntry() throws Exception {
        assertThat(submit("confirmation")).isEqualTo("none");
        writes.verify(() -> WLWaitingListUtil.add2WaitingList("12", "owned note", "34", "2026-03-04"));
        assertThat(response.getRedirectedUrl()).isEqualTo("/demographic/DemographicEdit?demographic_no=34");
    }

    @Test
    void confirmationRechecksAvailabilityEvenAfterAnEarlierSuccessfulCheck() throws Exception {
        assertThat(WaitingListAccess.isAvailable(login, "12")).isTrue();
        when(lists.findCurrentByGroup("groupA")).thenReturn(List.of());
        assertRejected("confirmation");
        verify(lists, times(2)).findCurrentByGroup("groupA");
    }
}
