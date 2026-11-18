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
    private final io.github.carlos_emr.carlos.daos.security.SecObjectNameDao securityObjects =
            mock(io.github.carlos_emr.carlos.daos.security.SecObjectNameDao.class);
    private final UserPropertyDAO properties = mock(UserPropertyDAO.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private ProviderProperty2Action action;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        registerMock(QueueDao.class, queues);
        registerMock(io.github.carlos_emr.carlos.daos.security.SecObjectNameDao.class, securityObjects);
        registerMock(UserPropertyDAO.class, properties);
        registerMock(io.github.carlos_emr.carlos.commn.service.ProviderDocumentQueueService.class,
                new io.github.carlos_emr.carlos.commn.service.ProviderDocumentQueueService(queues, securityObjects, properties));
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
        when(properties.lockProviderPreferences("owner")).thenReturn(true);
        when(queues.addNewQueueAndGetId("Other")).thenReturn(99);
        when(queues.addNewQueueAndGetId("Mine")).thenAnswer(call -> {
            assertThat(queues.addNewQueueAndGetId("Other")).isEqualTo(99);
            return 42;
        });

        assertThat(action.saveDefaultDocQueue()).isEqualTo("genDefaultDocQueue");

        verify(properties).lockProviderPreferences("owner");
        verify(properties).saveProp(argThat(prop -> "42".equals(prop.getValue())
                && "owner".equals(prop.getProviderNo()) && UserProperty.DOC_DEFAULT_QUEUE.equals(prop.getName())));
        verify(queues, never()).getLastId();
        assertThat(request.getAttribute("status")).isEqualTo("success");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavesThePreviousDefaultUntouchedWhenCreationFails(boolean hasPreviousDefault) {
        request.setParameter("chooseMode", "new");
        when(properties.lockProviderPreferences("owner")).thenReturn(true);
        UserProperty previous = new UserProperty();
        previous.setValue("7");
        if (hasPreviousDefault) {
            lenient().when(properties.getAllProperties(UserProperty.DOC_DEFAULT_QUEUE, java.util.List.of("owner")))
                    .thenReturn(java.util.List.of(previous));
        }
        when(queues.addNewQueueAndGetId("Mine")).thenThrow(new PersistenceException("insert failed"));

        assertThat(action.saveDefaultDocQueue()).isEqualTo("genDefaultDocQueue");

        assertThat(previous.getValue()).isEqualTo("7");
        assertNotSaved();
    }

    @Test
    void doesNotSaveADefaultWithoutAnAssignedId() {
        request.setParameter("chooseMode", "new");
        when(properties.lockProviderPreferences("owner")).thenReturn(true);
        when(queues.addNewQueueAndGetId("Mine")).thenReturn(null);

        action.saveDefaultDocQueue();

        assertNotSaved();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesSelectionOfAnExistingQueue(boolean hasPreviousDefault) {
        request.setParameter("chooseMode", "existing");
        when(properties.lockProviderPreferences("owner")).thenReturn(true);
        UserProperty choice = new UserProperty();
        choice.setValue("7");
        action.setExistingDefaultDocQueueProperty(choice);
        if (hasPreviousDefault) {
            UserProperty previous = new UserProperty();
            previous.setProviderNo("owner");
            previous.setName(UserProperty.DOC_DEFAULT_QUEUE);
            previous.setValue("3");
            when(properties.getAllProperties(UserProperty.DOC_DEFAULT_QUEUE, java.util.List.of("owner")))
                    .thenReturn(java.util.List.of(previous));
        }

        action.saveDefaultDocQueue();

        var writes = inOrder(properties);
        writes.verify(properties).lockProviderPreferences("owner");
        writes.verify(properties).getAllProperties(UserProperty.DOC_DEFAULT_QUEUE, java.util.List.of("owner"));
        writes.verify(properties).saveProp(argThat(prop -> "7".equals(prop.getValue())));
        assertThat(request.getAttribute("status")).isEqualTo("success");
        verifyNoInteractions(queues);
    }

    @Test
    void reportsMissingModeAsNotSaved() {
        action.saveDefaultDocQueue();
        assertNotSaved();
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "existing", "unknown"})
    void refusesMissingChoicesOrAnUnknownMode(String mode) {
        request.setParameter("chooseMode", mode);
        action.setNewDefaultDocQueueProperty(null);
        action.setExistingDefaultDocQueueProperty(null);
        action.saveDefaultDocQueue();
        assertNotSaved();
        verifyNoInteractions(queues, securityObjects);
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
        verify(properties, never()).saveProp(any(UserProperty.class));
        verify(queues, never()).getLastId();
        assertThat(request.getAttribute("status")).isEqualTo("error");
        assertThat(request.getAttribute("providermsgSuccess")).isEqualTo("provider.setDefaultDocumentQueue.msgNotSaved");
    }
    @Test
    void createsTheGrantableSecurityObject_withThePersistedQueueId() {
        request.setParameter("chooseMode", "new");
        when(properties.lockProviderPreferences("owner")).thenReturn(true);
        when(queues.addNewQueueAndGetId("Mine")).thenReturn(42);
        assertThat(action.saveDefaultDocQueue()).isEqualTo("genDefaultDocQueue");
        verify(properties).lockProviderPreferences("owner");
        verify(properties).saveProp(argThat(prop -> "42".equals(prop.getValue())));
        verify(securityObjects).saveOrUpdate(argThat(object -> "_queue.42".equals(object.getObjectname())
                && "Mine".equals(object.getDescription()) && object.getOrgapplicable() == 0));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", " ", "\t", "\r\n"})
    void refusesBlankNewQueueNames_beforeCreatingRowsOrChangingTheDefault(String blank) {
        request.setParameter("chooseMode", "new");
        action.getNewDefaultDocQueueProperty().setValue(blank);
        lenient().when(queues.addNewQueueAndGetId(blank)).thenReturn(42);
        action.saveDefaultDocQueue();
        assertNotSaved();
        verify(queues, never()).addNewQueueAndGetId(anyString());
        verify(properties, never()).saveProp(any(UserProperty.class));
        verifyNoInteractions(securityObjects);
    }

}
