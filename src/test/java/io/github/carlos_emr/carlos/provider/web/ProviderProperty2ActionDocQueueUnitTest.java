/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.PersistenceException;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProviderProperty2ActionDocQueueUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final QueueDao queues = mock(QueueDao.class);
    private final UserPropertyDAO properties = mock(UserPropertyDAO.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private ProviderProperty2Action action;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        registerMock(QueueDao.class, queues);
        registerMock(UserPropertyDAO.class, properties);
        registerMock(ProviderManager2.class, mock(ProviderManager2.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("owner");
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        action = new ProviderProperty2Action();
        UserProperty newQueue = new UserProperty();
        newQueue.setValue("Mine");
        action.setNewDefaultDocQueueProperty(newQueue);
    }

    @AfterEach
    void tearDown() {
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "NEW"})
    void savesTheCreatedIdWhenAnotherRequestCreatesAHigherQueue(String mode) {
        request.setParameter("chooseMode", mode);
        when(queues.addNewQueueAndGetId("Other")).thenReturn(99);
        when(queues.addNewQueueAndGetId("Mine")).thenAnswer(call -> {
            assertThat(queues.addNewQueueAndGetId("Other")).isEqualTo(99);
            return 42;
        });

        assertThat(action.saveDefaultDocQueue()).isEqualTo("genDefaultDocQueue");

        verify(properties).saveProp(argThat(prop -> "42".equals(prop.getValue())
                && "owner".equals(prop.getProviderNo()) && UserProperty.DOC_DEFAULT_QUEUE.equals(prop.getName())));
        verify(queues, never()).getLastId();
        assertThat(request.getAttribute("status")).isEqualTo("success");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavesThePreviousDefaultUntouchedWhenCreationFails(boolean hasPreviousDefault) {
        request.setParameter("chooseMode", "new");
        UserProperty previous = new UserProperty();
        previous.setValue("7");
        if (hasPreviousDefault) {
            lenient().when(properties.getProp("owner", UserProperty.DOC_DEFAULT_QUEUE)).thenReturn(previous);
        }
        when(queues.addNewQueueAndGetId("Mine")).thenThrow(new PersistenceException("insert failed"));

        assertThat(action.saveDefaultDocQueue()).isEqualTo("genDefaultDocQueue");

        assertThat(previous.getValue()).isEqualTo("7");
        assertNotSaved();
    }

    @Test
    void doesNotSaveADefaultWithoutAnAssignedId() {
        request.setParameter("chooseMode", "new");
        when(queues.addNewQueueAndGetId("Mine")).thenReturn(null);

        action.saveDefaultDocQueue();

        assertNotSaved();
    }

    @Test
    void preservesSelectionOfAnExistingQueue() {
        request.setParameter("chooseMode", "existing");
        UserProperty choice = new UserProperty();
        choice.setValue("7");
        action.setExistingDefaultDocQueueProperty(choice);

        action.saveDefaultDocQueue();

        verify(properties).saveProp(argThat(prop -> "7".equals(prop.getValue())));
        verifyNoInteractions(queues);
    }

    @Test
    void reportsMissingModeAsNotSaved() {
        action.saveDefaultDocQueue();
        assertNotSaved();
    }

    @Test
    void opensQueueChoicesWithTheIntegerIdsReturnedByTheDao() {
        java.util.Hashtable<String, Object> queue = new java.util.Hashtable<>();
        queue.put("id", 42);
        queue.put("queue", "Mine");
        when(queues.getQueues()).thenReturn(java.util.List.of(queue));

        assertThat(action.viewDefaultDocQueue()).isEqualTo("genDefaultDocQueue");

        @SuppressWarnings("unchecked")
        java.util.Collection<io.github.carlos_emr.carlos.util.LabelValueBean> choices =
                (java.util.Collection<io.github.carlos_emr.carlos.util.LabelValueBean>) request.getAttribute("viewChoices");
        assertThat(choices).extracting(io.github.carlos_emr.carlos.util.LabelValueBean::getValue).contains("42");
        verify(properties, never()).saveProp(any());
    }

    private void assertNotSaved() {
        verifyNoInteractions(properties);
        verify(queues, never()).getLastId();
        assertThat(request.getAttribute("status")).isEqualTo("error");
        assertThat(request.getAttribute("providermsgSuccess")).isEqualTo("provider.setDefaultDocumentQueue.msgNotSaved");
    }
}
