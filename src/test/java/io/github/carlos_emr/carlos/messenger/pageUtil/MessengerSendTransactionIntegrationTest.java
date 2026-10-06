/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.MessengerGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real message, delivery and patient-link DAO writes test rollback and uncertain completion. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MessengerSendTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private MessageTblDao messages;
    @Autowired private MessageListDao deliveries;
    @Autowired private MsgDemoMapDao links;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entities;

    @ParameterizedTest
    @ValueSource(strings = {"message", "delivery", "link", "afterCommit", "rollbackOnly"})
    void shouldRetryOnlyConfirmedRollback_withoutDuplicatingAnyMessageRows(String failure) throws Exception {
        String marker = "owned-send-" + UUID.randomUUID();
        List<Integer> ownedIds = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(true);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter(MessengerSubmissionGuard.PARAMETER,
                MessengerSubmissionGuard.issue(request.getSession(), "999998"));
        MsgSessionBean draft = new MsgSessionBean();
        draft.setProviderNo("999998");
        draft.setUserName("Owned sender");
        draft.setAttachment("owned attachment");
        draft.setSubject(marker);
        draft.setMessage("Owned body");
        request.getSession().setAttribute("msgSessionBean", draft);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), eq("_msg"), eq("w"), any())).thenReturn(true);
        var productionDemographics = new MessengerDemographicManagerImpl();
        ReflectionTestUtils.setField(productionDemographics, "securityInfoManager", security);
        ReflectionTestUtils.setField(productionDemographics, "msgDemoMapDao", links);
        MessengerDemographicManager demographics = mock(MessengerDemographicManager.class);
        when(demographics.attachDemographicToMessage(eq(login), anyInt(), anyInt())).thenAnswer(call -> {
            Long id = productionDemographics.attachDemographicToMessage(login, (int) call.getArgument(1), (int) call.getArgument(2));
            entities.flush();
            if (fail.get() && "link".equals(failure)) throw new IllegalStateException("Injected link failure");
            return id;
        });
        MessageTblDao messageWrites = mock(MessageTblDao.class);
        doAnswer(call -> {
            MessageTbl row = call.getArgument(0);
            messages.persist(row);
            entities.flush();
            ownedIds.add(row.getId());
            if (fail.get() && "message".equals(failure)) throw new IllegalStateException("Injected message failure");
            if (fail.get() && "afterCommit".equals(failure)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { throw new IllegalStateException("Injected acknowledgement failure"); }
                });
            }
            return null;
        }).when(messageWrites).persist(any());
        MessageListDao deliveryWrites = mock(MessageListDao.class);
        doAnswer(call -> {
            deliveries.persist(call.getArgument(0));
            entities.flush();
            if (fail.get() && "delivery".equals(failure)) throw new IllegalStateException("Injected delivery failure");
            return null;
        }).when(deliveryWrites).persist(any());
        MessengerGroupManager groups = mock(MessengerGroupManager.class);
        MsgProviderData recipient = new MsgProviderData();
        recipient.getId().setContactId("999997");
        recipient.setLastName("Owned recipient");
        when(groups.getMemberData(eq(login), any(ContactIdentifier.class))).thenReturn(recipient);
        OscarCommLocationsDao locations = mock(OscarCommLocationsDao.class);
        when(locations.findByCurrent1(1)).thenReturn(null);
        try (var spring = mockStatic(SpringUtils.class);
             var servlet = mockStatic(ServletActionContext.class);
             var loggedIn = mockStatic(LoggedInInfo.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(MessengerDemographicManager.class)).thenReturn(demographics);
            spring.when(() -> SpringUtils.getBean(MessageTblDao.class)).thenReturn(messageWrites);
            spring.when(() -> SpringUtils.getBean(MessageListDao.class)).thenReturn(deliveryWrites);
            spring.when(() -> SpringUtils.getBean(MessengerGroupManager.class)).thenReturn(groups);
            spring.when(() -> SpringUtils.getBean(OscarCommLocationsDao.class)).thenReturn(locations);
            // A rollback-only completion may return normally from TransactionTemplate.
            // Exercise that outcome independently of exceptions from the write callbacks.
            PlatformTransactionManager completion = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return transactions.getTransaction(definition);
                }
                @Override public void commit(TransactionStatus status) {
                    if (fail.get() && "rollbackOnly".equals(failure)) status.setRollbackOnly();
                    transactions.commit(status);
                }
                @Override public void rollback(TransactionStatus status) { transactions.rollback(status); }
            };
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(completion);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);

            String result = send(marker);
            assertThat(draft.getAttachment()).isEqualTo("owned attachment");
            assertThat(draft.getMessage()).isEqualTo("Owned body");
            if ("afterCommit".equals(failure)) {
                assertThat(result).isEqualTo("none");
                assertThat(response.getStatus()).isEqualTo(409);
                assertRows(ownedIds, 1);
            } else {
                assertThat(result).isEqualTo("error");
                assertThat(response.getStatus()).isEqualTo(500);
                assertRows(ownedIds, 0);
                fail.set(false);
                response.reset();
                assertThat(send(marker)).isEqualTo("success");
                assertThat(draft.getAttachment()).isNull();
                assertRows(ownedIds, 1);
            }
            fail.set(false);
            response.reset();
            assertThat(send(marker)).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(409);
            assertRows(ownedIds, 1);
        } finally {
            if (!ownedIds.isEmpty()) new TransactionTemplate(transactions).executeWithoutResult(status -> {
                entities.createQuery("delete from MessageList where message in (:ids)")
                        .setParameter("ids", ownedIds.stream().map(Integer::longValue).toList()).executeUpdate();
                entities.createQuery("delete from MsgDemoMap where messageID in (:ids)").setParameter("ids", ownedIds).executeUpdate();
                entities.createQuery("delete from MessageTbl where id in (:ids)").setParameter("ids", ownedIds).executeUpdate();
            });
        }
    }

    private String send(String subject) throws Exception {
        MsgCreateMessage2Action action = new MsgCreateMessage2Action();
        action.setSubject(subject);
        action.setMessage("Owned body");
        action.setProvider(new String[]{"999997-0"});
        action.setDemographic_no("893100");
        return action.execute();
    }

    private void assertRows(List<Integer> ids, long expected) {
        assertThat(ids).isNotEmpty();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(entities.createQuery("select count(m) from MessageTbl m where m.id in (:ids)", Long.class)
                    .setParameter("ids", ids).getSingleResult()).isEqualTo(expected);
            assertThat(entities.createQuery("select count(d) from MessageList d where d.message in (:ids)", Long.class)
                    .setParameter("ids", ids.stream().map(Integer::longValue).toList()).getSingleResult()).isEqualTo(expected);
            assertThat(entities.createQuery("select count(l) from MsgDemoMap l where l.messageID in (:ids)", Long.class)
                    .setParameter("ids", ids).getSingleResult()).isEqualTo(expected);
        });
    }
}
