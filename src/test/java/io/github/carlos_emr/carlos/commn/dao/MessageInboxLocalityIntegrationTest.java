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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.messenger.pageUtil.MsgAttachmentAccess;
import io.github.carlos_emr.carlos.messenger.pageUtil.MsgDisplayMessagesBean;
import io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage;
import io.github.carlos_emr.carlos.messenger.util.LocalMessageRecipients;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps paginated inboxes and unread indicators within the same clinic boundary as mutations.
 *
 * @since 2026-10-25
 */
@Tag("integration")
@Tag("security")
@Tag("messenger")
class MessageInboxLocalityIntegrationTest extends CarlosTestBase {
    private static final String PROVIDER = "994451";
    private static final int FIRST_LOCAL = 994451;
    private static final int SECOND_LOCAL = 994452;
    private static final int REMOTE = 994453;
    private static final int UNKNOWN = 994454;

    @Autowired
    private MessageListDao recipients;
    @Autowired
    private OscarCommLocationsDao locations;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    private int sequence;

    @BeforeEach
    void setUp() {
        location(FIRST_LOCAL, 1);
        location(SECOND_LOCAL, 1);
        location(REMOTE, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "count", "status", "unread", "attached-count"})
    void shouldExcludeRemoteRecipients_whenReadingInboxOrUnreadIndicators(String query) {
        MessageList legacy = recipient(PROVIDER, 0, 0, MessageList.STATUS_NEW);
        MessageList first = recipient(PROVIDER, FIRST_LOCAL, 0, MessageList.STATUS_NEW);
        MessageList second = recipient(PROVIDER, SECOND_LOCAL, -1, MessageList.STATUS_NEW);
        MessageList nullableLegacy = recipient(PROVIDER, FIRST_LOCAL, 0, MessageList.STATUS_NEW);
        entityManager.createNativeQuery("UPDATE messagelisttbl SET destinationFacilityId=NULL WHERE id=:id")
                .setParameter("id", nullableLegacy.getId()).executeUpdate();
        recipient(PROVIDER, REMOTE, 0, MessageList.STATUS_NEW);
        recipient(PROVIDER, UNKNOWN, 0, MessageList.STATUS_NEW);
        recipient(PROVIDER, FIRST_LOCAL, 7, MessageList.STATUS_NEW);
        recipient(PROVIDER, 0, 7, MessageList.STATUS_NEW);
        recipient("994455", FIRST_LOCAL, 0, MessageList.STATUS_NEW);
        recipient(PROVIDER, FIRST_LOCAL, 0, MessageList.STATUS_READ);
        entityManager.flush();
        entityManager.clear();

        List<Integer> expected = List.of(legacy.getId(), first.getId(), second.getId(), nullableLegacy.getId());
        switch (query) {
            case "search" -> assertThat(recipients.search(PROVIDER, MessageList.STATUS_NEW, 0, 20))
                    .extracting(MessageList::getId).containsExactlyInAnyOrderElementsOf(expected);
            case "count" -> assertThat(recipients.searchAndReturnTotal(PROVIDER, MessageList.STATUS_NEW)).isEqualTo(4);
            case "status" -> assertThat(recipients.findByProviderAndStatus(PROVIDER, MessageList.STATUS_NEW))
                    .extracting(MessageList::getId).containsExactlyInAnyOrderElementsOf(expected);
            case "unread" -> assertThat(recipients.findUnreadByProvider(PROVIDER))
                    .extracting(MessageList::getId).containsExactlyInAnyOrderElementsOf(expected);
            case "attached-count" -> assertThat(recipients.findUnreadByProviderAndAttachedCount(PROVIDER)).isEqualTo(4);
            default -> throw new IllegalArgumentException("Unexpected query");
        }
    }

    @Test
    void shouldFilterBeforePagination_whenRemoteRowsAreNewerThanLocalMessages() {
        MessageList oldest = recipient(PROVIDER, 0, 0, MessageList.STATUS_NEW);
        MessageList newest = recipient(PROVIDER, FIRST_LOCAL, 0, MessageList.STATUS_NEW);
        recipient(PROVIDER, REMOTE, 0, MessageList.STATUS_NEW);
        recipient(PROVIDER, FIRST_LOCAL, 7, MessageList.STATUS_NEW);

        assertThat(recipients.search(PROVIDER, MessageList.STATUS_NEW, 0, 1))
                .extracting(MessageList::getId).containsExactly(newest.getId());
        assertThat(recipients.search(PROVIDER, MessageList.STATUS_NEW, 1, 1))
                .extracting(MessageList::getId).containsExactly(oldest.getId());
        assertThat(recipients.search(PROVIDER, MessageList.STATUS_NEW, 2, 1)).isEmpty();
        assertThat(recipients.searchAndReturnTotal(PROVIDER, MessageList.STATUS_NEW)).isEqualTo(2);
    }

    @Test
    void shouldRespectConfiguredOwnership_whenLocationBecomesNonCurrentAndIsRestored() {
        MessageList row = recipient(PROVIDER, FIRST_LOCAL, 0, MessageList.STATUS_NEW);
        assertThat(recipients.search(PROVIDER, null, 0, 10)).extracting(MessageList::getId)
                .containsExactly(row.getId());
        assertThat(LocalMessageRecipients.includes(row, LocalMessageRecipients.locationIds(locations))).isTrue();

        locations.find(FIRST_LOCAL).setCurrent1(0);
        entityManager.flush();

        assertThat(LocalMessageRecipients.includes(row, LocalMessageRecipients.locationIds(locations))).isFalse();
        assertThat(recipients.search(PROVIDER, null, 0, 10)).isEmpty();
        assertThat(recipients.searchAndReturnTotal(PROVIDER, null)).isZero();
        assertThat(recipients.findUnreadByProvider(PROVIDER)).isEmpty();

        locations.find(FIRST_LOCAL).setCurrent1(1);
        entityManager.flush();

        assertThat(recipients.search(PROVIDER, null, 0, 10)).extracting(MessageList::getId)
                .containsExactly(row.getId());
        assertThat(LocalMessageRecipients.includes(row, LocalMessageRecipients.locationIds(locations))).isTrue();
        entityManager.clear();
        assertThat(recipients.find(row.getId()).getStatus()).isEqualTo(MessageList.STATUS_NEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {MessageList.STATUS_READ, MessageList.STATUS_DELETED, MessageList.STATUS_SENT})
    void shouldRetainSavedParticipantAccess_whenLocalMessageHasNonUnreadStatus(String status) {
        MessageList local = recipient(PROVIDER, FIRST_LOCAL, 0, status);
        MessageList remote = recipient(PROVIDER, REMOTE, 0, status);

        assertThat(recipients.search(PROVIDER, status, 0, 10)).extracting(MessageList::getId)
                .containsExactly(local.getId());
        assertThat(recipients.searchAndReturnTotal(PROVIDER, status)).isEqualTo(1);
        assertThat(recipients.findByProviderAndStatus(PROVIDER, status)).extracting(MessageList::getId)
                .containsExactly(local.getId());
        assertThat(MsgAttachmentAccess.find(login(), (int) local.getMessage())).isNotNull()
                .extracting(MessageTbl::getId).isEqualTo((int) local.getMessage());
        assertThat(MsgAttachmentAccess.find(login(), (int) remote.getMessage())).isNull();
        // Raw participant lookups still return every recipient for existing reply/mutation callers.
        assertThat(recipients.findByProviderNoAndMessageNo(PROVIDER, remote.getMessage()))
                .extracting(MessageList::getId).containsExactly(remote.getId());
        assertThat(recipients.find(local.getId()).getStatus()).isEqualTo(status);
        assertThat(recipients.find(remote.getId()).getStatus()).isEqualTo(status);
    }

    @Test
    void shouldRetainSenderAccess_whenMessageWasSentWithoutLocalRecipient() {
        MessageList remoteRecipient = recipient("994455", REMOTE, 0, MessageList.STATUS_NEW);
        MessageTbl sent = entityManager.find(MessageTbl.class, (int) remoteRecipient.getMessage());
        sent.setSentByNo(PROVIDER);
        sent.setSentByLocation(FIRST_LOCAL);
        entityManager.flush();

        assertThat(MsgAttachmentAccess.find(login(), sent.getId())).isNotNull()
                .extracting(MessageTbl::getId).isEqualTo(sent.getId());
        assertThat(recipients.search(PROVIDER, null, 0, 10)).isEmpty();
        assertThat(recipients.find(remoteRecipient.getId()).getStatus()).isEqualTo(MessageList.STATUS_NEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {MessageList.STATUS_NEW, MessageList.STATUS_DELETED})
    void shouldAlignLegacyPageAndCount_whenRemoteFacilitySharesCurrentLocation(String status) {
        MessageList first = recipient(PROVIDER, FIRST_LOCAL, 0, status);
        MessageList nullable = recipient(PROVIDER, FIRST_LOCAL, 0, status);
        entityManager.createNativeQuery("UPDATE messagelisttbl SET destinationFacilityId=NULL WHERE id=:id")
                .setParameter("id", nullable.getId()).executeUpdate();
        recipient(PROVIDER, FIRST_LOCAL, 7, status);
        recipient(PROVIDER, REMOTE, 0, status);
        recipient("994455", FIRST_LOCAL, 0, status);
        entityManager.flush();
        entityManager.clear();
        MsgDisplayMessagesBean page = new MsgDisplayMessagesBean();
        page.setProviderNo(PROVIDER);
        page.setFilter("inbox locality");
        // Pick this fixture's configured current location without relying on seeded-location order.
        ReflectionTestUtils.setField(page, "currentLocationId", Integer.toString(FIRST_LOCAL));

        boolean deleted = MessageList.STATUS_DELETED.equals(status);
        List<MsgDisplayMessage> shown = deleted ? page.estDeletedInbox(null, 1) : page.estInbox(null, 1);

        assertThat(shown).extracting(MsgDisplayMessage::getMessageId)
                .containsExactly(Long.toString(nullable.getMessage()), Long.toString(first.getMessage()));
        assertThat(page.getTotalMessages(deleted ? 2 : 0)).isEqualTo(2);
        if (!deleted) {
            assertThat(recipients.findByProviderNoAndLocationNo(PROVIDER, FIRST_LOCAL))
                    .extracting(MessageList::getId).containsExactly(first.getId(), nullable.getId());
        }
    }

    private LoggedInInfo login() {
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER);
        LoggedInInfo login = new LoggedInInfo();
        login.setLoggedInProvider(provider);
        return login;
    }

    private void location(int id, int current) {
        OscarCommLocations location = new OscarCommLocations();
        location.setId(id);
        location.setCurrent1(current);
        location.setLocationDesc("FAKE inbox locality fixture");
        location.setAddressBook("<addressBook/>");
        if (current == 0) {
            location.setRemoteServerURL("https://remote.invalid");
        }
        entityManager.persist(location);
    }

    private MessageList recipient(String provider, int location, int facility, String status) {
        MessageTbl message = new MessageTbl();
        Date sent = new Date(1_700_000_000_000L + ++sequence * 86_400_000L);
        message.setDate(sent);
        message.setTime(sent);
        message.setSubject("FAKE inbox locality fixture");
        message.setMessage("FAKE inbox locality fixture body");
        message.setType(0);
        entityManager.persist(message);
        MessageList row = new MessageList();
        row.setProviderNo(provider);
        row.setMessage(message.getId());
        row.setRemoteLocation(location);
        row.setDestinationFacilityId(facility);
        row.setStatus(status);
        recipients.persist(row);
        MsgDemoMap attached = new MsgDemoMap();
        attached.setMessageID(message.getId());
        attached.setDemographic_no(994451);
        entityManager.persist(attached);
        entityManager.flush();
        return row;
    }
}
