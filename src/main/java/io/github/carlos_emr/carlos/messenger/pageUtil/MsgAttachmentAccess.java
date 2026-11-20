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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.messenger.util.LocalMessageRecipients;

/**
 * Authorizes saved messages through local participation or an explicitly authorized patient chart.
 *
 * @since 2026-10-24
 */
public final class MsgAttachmentAccess {
    private MsgAttachmentAccess() { }

    /**
     * Returns the authorized message, or null without disclosing whether it exists.
     * The caller must also require the messaging module's read privilege.
     *
     * @param login LoggedInInfo the authenticated provider, or null for an unauthenticated request
     * @param messageId int the requested positive message identifier
     * @return MessageTbl the message when the provider is its local sender or recipient,
     *         or null for an invalid identity, missing message, or denied access
     */
    public static MessageTbl find(LoggedInInfo login, int messageId) {
        if (login == null || login.getLoggedInProviderNo() == null || messageId <= 0) return null;
        String provider = login.getLoggedInProviderNo();
        MessageTbl message = SpringUtils.getBean(MessageTblDao.class).find(messageId);
        if (message == null) return null;
        java.util.Set<Integer> localLocations = LocalMessageRecipients.locationIds(
                SpringUtils.getBean(OscarCommLocationsDao.class));
        if (localLocations.contains(message.getSentByLocation()) && provider.equals(message.getSentByNo())) return message;
        boolean recipient = SpringUtils.getBean(MessageListDao.class)
                .findByProviderNoAndMessageNo(provider, (long) messageId).stream()
                .anyMatch(item -> LocalMessageRecipients.includes(item, localLocations));
        return recipient ? message : null;
    }

    /**
     * Returns a locally owned message, or a message linked to the explicitly selected patient's
     * accessible chart. Chart access requires patient-specific messaging and demographic read
     * rights and record access; naming an unrelated or inaccessible patient grants nothing.
     * This alternative is read-only and must not authorize message deletion or patient relinking.
     *
     * @param login LoggedInInfo the authenticated provider
     * @param messageId int the requested message identifier
     * @param chartPatientNo String the explicitly selected patient, or null for mailbox-only access
     * @return MessageTbl the authorized message, or null when neither boundary permits access
     */
    public static MessageTbl find(LoggedInInfo login, int messageId, String chartPatientNo) {
        MessageTbl local = find(login, messageId);
        if (local != null) return local;
        int patient = ConversionUtils.fromIntString(chartPatientNo);
        if (login == null || login.getLoggedInProviderNo() == null || messageId <= 0 || patient <= 0) return null;
        if (!canReadChart(login, chartPatientNo)) return null;
        boolean linked = SpringUtils.getBean(MsgDemoMapDao.class).findByMessageId(messageId).stream()
                .anyMatch(item -> Integer.valueOf(patient).equals(item.getDemographic_no()));
        return linked ? SpringUtils.getBean(MessageTblDao.class).find(messageId) : null;
    }

    /** Checks the patient-specific boundary before a shared chart message list reveals metadata. */
    static boolean canReadChart(LoggedInInfo login, String chartPatientNo) {
        int patient = ConversionUtils.fromIntString(chartPatientNo);
        if (login == null || login.getLoggedInProviderNo() == null || patient <= 0) return false;
        String canonicalPatient = Integer.toString(patient);
        SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
        try {
            PatientRecordAccess.require(security, login, canonicalPatient, "_msg", "r");
            PatientRecordAccess.require(security, login, canonicalPatient, "_demographic", "r");
        } catch (SecurityException denied) {
            return false;
        }
        return true;
    }
}
