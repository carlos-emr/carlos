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
package io.github.carlos_emr.carlos.fax.ringcentral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RingCentral response DTO defensive-copy behavior.
 *
 * @since 2026-05-06
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("RingCentralResponse Unit Tests")
class RingCentralResponseTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should not expose mutable attachments list")
    void shouldNotExposeMutableAttachmentsList_forMessageDto() {
        RingCentralResponse.Attachment attachment =
                new RingCentralResponse.Attachment("1", "f.pdf", "application/pdf");
        List<RingCentralResponse.Attachment> attachments = new ArrayList<>();
        attachments.add(attachment);

        RingCentralResponse.Message message = new RingCentralResponse.Message();
        message.setAttachments(attachments);
        attachments.clear();

        assertThat(message.getAttachments()).hasSize(1);
        assertThatThrownBy(() -> message.getAttachments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should not expose mutable records list")
    void shouldNotExposeMutableRecordsList_forMessageListDto() {
        RingCentralResponse.Message message = new RingCentralResponse.Message();
        message.setId("123");
        List<RingCentralResponse.Message> records = new ArrayList<>();
        records.add(message);

        RingCentralResponse.MessageList messageList = new RingCentralResponse.MessageList();
        messageList.setRecords(records);
        records.clear();

        assertThat(messageList.getRecords()).hasSize(1);
        assertThatThrownBy(() -> messageList.getRecords().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
