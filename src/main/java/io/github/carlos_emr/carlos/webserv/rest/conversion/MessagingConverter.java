//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import java.text.SimpleDateFormat;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MessageTo1;
import org.springframework.stereotype.Component;

@Component
public class MessagingConverter extends AbstractConverter<MessageList, MessageTo1> {


    //private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    private MessageTblDao messageTblDao;

    @Override
    public MessageList getAsDomainObject(LoggedInInfo loggedInInfo, MessageTo1 t) throws ConversionException {
        MessageList d = new MessageList();

        return d;
    }

    @Override
    public MessageTo1 getAsTransferObject(LoggedInInfo loggedInInfo, MessageList t) throws ConversionException {

        messageTblDao = SpringUtils.getBean(MessageTblDao.class);
        MessageTbl msg = messageTblDao.find((int) t.getMessage());

        MsgDemoMapDao msgDemoMapDao = SpringUtils.getBean(MsgDemoMapDao.class);
        List<MsgDemoMap> demos = msgDemoMapDao.findByMessageId((int) t.getMessage());

        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);

        if (msg == null) {
            return null;
        }


        MessageTo1 d = new MessageTo1();
        d.setId(msg.getId());

        String strDate = (new SimpleDateFormat("yyyy-MM-dd")).format(msg.getDate()) + " " + (new SimpleDateFormat("HH:mm")).format(msg.getTime());

        d.setDateOfMessage(strDate);
        d.setFromName(msg.getSentBy());
        d.setMessage(msg.getMessage());
        d.setSubject(msg.getSubject());

        if (demos != null && demos.size() > 0) {
            Integer demographicNo = demos.get(0).getDemographic_no();
            if (demographicNo != null) {
                Demographic demographic = demographicDao.getDemographicById(demographicNo);
                d.setDemographicNo(demographicNo);
                d.setDemographicName(demographic != null ? demographic.getFormattedName() : "N/A");
            }
        }

        return d;
    }

}
