package io.github.carlos_emr.carlos.messenger.data;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.managers.MessengerGroupManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What {@link MsgMessageData} stores of a message's chart PDF records: the bytes the viewers read back, and never a
 * text the {@code pdfattachment} column would cut off without a word.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MsgMessageData unit tests")
class MsgMessageDataUnitTest extends CarlosUnitTestBase {

    private MessageTblDao messageTblDao;
    private MsgMessageData data;

    @BeforeEach
    void setUp() {
        messageTblDao = mock(MessageTblDao.class);
        registerMock(MessageTblDao.class, messageTblDao);
        registerMock(MessageListDao.class, mock(MessageListDao.class));
        registerMock(MessengerGroupManager.class, mock(MessengerGroupManager.class));
        OscarCommLocationsDao locations = mock(OscarCommLocationsDao.class);
        OscarCommLocations here = mock(OscarCommLocations.class);
        when(here.getId()).thenReturn(1);
        when(locations.findByCurrent1(1)).thenReturn(List.of(here));
        registerMock(OscarCommLocationsDao.class, locations);
        data = new MsgMessageData();
    }

    private void sendMessage2(String pdfRecords) {
        data.sendMessage2("FAKE body", "FAKE subject", "FAKE sender", "FAKE recipient", "999998", new ArrayList<>(), null,
                pdfRecords, 1);
    }

    private void sendMessageReview(String pdfRecords) {
        data.sendMessageReview("FAKE body", "FAKE subject", "FAKE sender", "FAKE recipient", "999998", new ArrayList<>(),
                null, pdfRecords, 1, null);
    }

    @Test
    @DisplayName("should store PDF records that fill the column exactly, as the UTF-8 bytes the viewers read")
    void shouldStoreRecordsAsUtf8_whenTheyFillTheColumnExactly() {
        String exact = "é".repeat(32_767) + "A";

        sendMessage2(exact);
        sendMessageReview(exact);

        ArgumentCaptor<MessageTbl> stored = ArgumentCaptor.forClass(MessageTbl.class);
        verify(messageTblDao, org.mockito.Mockito.times(2)).persist(stored.capture());
        for (MessageTbl message : stored.getAllValues()) {
            assertThat(message.getPdfAttachment()).hasSize(MessageTbl.PDF_ATTACHMENT_MAX_BYTES)
                    .isEqualTo(exact.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("should refuse PDF records longer than the column, in bytes, and store nothing")
    void shouldRefuseRecordsLongerThanTheColumn_andStoreNothing() {
        String twoBytesOver = "é".repeat(32_768);

        assertThatThrownBy(() -> sendMessage2("A".repeat(65_536))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65536 bytes");
        assertThatThrownBy(() -> sendMessage2(twoBytesOver)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sendMessageReview("A".repeat(65_536))).isInstanceOf(IllegalArgumentException.class);

        verify(messageTblDao, never()).persist(any());
    }

    @Test
    @DisplayName("should store a message without PDF records as it did")
    void shouldStoreNoPdfAttachment_whenThereAreNoRecords() {
        sendMessage2(null);

        ArgumentCaptor<MessageTbl> stored = ArgumentCaptor.forClass(MessageTbl.class);
        verify(messageTblDao).persist(stored.capture());
        assertThat(stored.getValue().getPdfAttachment()).isNull();
    }
}
