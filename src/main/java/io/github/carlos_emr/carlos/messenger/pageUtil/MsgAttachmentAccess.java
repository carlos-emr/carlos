/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Restricts saved message attachments to the local sender or a local recipient. */
public final class MsgAttachmentAccess {
    private MsgAttachmentAccess() { }

    /**
     * Returns the authorized message, or null without disclosing whether it exists.
     * The caller must also require the messaging module's read privilege.
     */
    public static MessageTbl find(LoggedInInfo login, int messageId) {
        if (login == null || login.getLoggedInProviderNo() == null || messageId <= 0) return null;
        String provider = login.getLoggedInProviderNo();
        MessageTbl message = SpringUtils.getBean(MessageTblDao.class).find(messageId);
        if (message == null) return null;
        java.util.Set<Integer> localLocations = new java.util.HashSet<>();
        localLocations.add(0); // Legacy local rows predate configured clinic locations.
        SpringUtils.getBean(OscarCommLocationsDao.class).findByCurrent1(1)
                .forEach(location -> localLocations.add(location.getId()));
        if (localLocations.contains(message.getSentByLocation()) && provider.equals(message.getSentByNo())) return message;
        boolean recipient = SpringUtils.getBean(MessageListDao.class)
                .findByProviderNoAndMessageNo(provider, (long) messageId).stream()
                .anyMatch(item -> localLocations.contains(item.getRemoteLocation()) && item.getDestinationFacilityId() < 1);
        return recipient ? message : null;
    }
}
