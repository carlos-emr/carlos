/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.email.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailData")
class EmailDataTest {

    @Test
    @DisplayName("should allow adding attachments to default list")
    void shouldAllowAddingAttachmentsToDefaultList() {
        EmailData emailData = new EmailData();

        emailData.getAttachments().add(new EmailAttachment());

        assertThat(emailData.getAttachments()).hasSize(1);
    }

    @Test
    @DisplayName("should allow adding attachments after setting null list")
    void shouldAllowAddingAttachments_whenAttachmentsAreSetToNull() {
        EmailData emailData = new EmailData();
        emailData.setAttachments(null);

        emailData.getAttachments().add(new EmailAttachment());

        assertThat(emailData.getAttachments()).hasSize(1);
    }
}
