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
package io.github.carlos_emr.carlos.messenger.service;

import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import org.springframework.stereotype.Service;

/**
 * Loads a patient-linked message for a chart-local draft.
 * @since 2026-10-03
 */
@Service
public class MessageEncounterService {
    private final MessageTblDao messages;
    private final MsgDemoMapDao links;
    private final SecurityInfoManager security;

    /**
     * @param messages message records
     * @param links message-to-patient associations
     * @param security caller privilege checks
     */
    public MessageEncounterService(MessageTblDao messages, MsgDemoMapDao links, SecurityInfoManager security) {
        this.messages = messages;
        this.links = links;
        this.security = security;
    }

    /**
     * Returns plain message text after checking message/chart access and the patient link.
     * Callers must encode it for the rendering context; this method never saves a note.
     *
     * @param loggedInInfo authenticated caller
     * @param messageId message selected by Write to Encounter
     * @param demographicNo destination patient
     * @return sender, recipients, date, subject and body of the linked message
     * @throws SecurityException if access or the patient link is missing
     * @throws IllegalArgumentException if the message no longer exists
     */
    public String load(LoggedInInfo loggedInInfo, int messageId, int demographicNo) {
        if (!security.hasPrivilege(loggedInInfo, "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        if (!security.hasPrivilege(loggedInInfo, "_eChart", "r", Integer.toString(demographicNo))) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        if (links.findByMessageId(messageId).stream()
                .noneMatch(link -> Integer.valueOf(demographicNo).equals(link.getDemographic_no()))) {
            throw new SecurityException("Message is not linked to this patient");
        }
        var message = messages.find(messageId);
        if (message == null) {
            throw new IllegalArgumentException("Message no longer exists");
        }
        return "From: " + message.getSentBy() + "\nTo: " + message.getSentTo()
                + "\nDate: " + ConversionUtils.toDateString(message.getDate()) + " "
                + ConversionUtils.toTimeString(message.getTime())
                + "\nSubject: " + message.getSubject() + "\n" + message.getMessage();
    }
}
