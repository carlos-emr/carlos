/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.dashboard.handler;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.commn.model.OscarMsgType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.messenger.data.MessengerSystemMessage;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.MessagingManager;

public class MessageHandler {

    private static final String SYSTEM_USER_ID = "-1";
    private static final String SYSTEM_USER_NAME = "System";

    public void notifyProvider(
            String subject,
            String messageBody,
            String providerNo
    ) {
        notifyProvider(
                subject,
                messageBody,
                providerNo,
                null);
    }

    // This is based on EaapsHandler.notifyProvider
    public void notifyProvider(
            String subject,
            String messageBody,
            String providerNo,
            List<Integer> linkedDemographicNumbers
    ) {
        String userName = SYSTEM_USER_NAME;
        String userNo = SYSTEM_USER_ID;

        MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);
        ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);
        Provider provider = providerManager.getProvider(SYSTEM_USER_ID);
        Security security = new Security();
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);

        MessengerSystemMessage systemMessage = new MessengerSystemMessage(new String[]{providerNo});
        systemMessage.setType(OscarMsgType.GENERAL_TYPE);
        systemMessage.setSentByNo(userNo);
        systemMessage.setSentBy(userName);
        systemMessage.setSubject(subject);
        systemMessage.setMessage(messageBody);

        if (linkedDemographicNumbers != null && !linkedDemographicNumbers.isEmpty()) {
            systemMessage.setAttachedDemographicNo(linkedDemographicNumbers.toArray(new Integer[linkedDemographicNumbers.size()]));
        }

        messagingManager.sendSystemMessage(loggedInInfo, systemMessage);

    }
}
