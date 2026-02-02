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

package io.github.carlos_emr.carlos.tickler;

import java.util.Calendar;
import java.util.GregorianCalendar;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * @author Jay Gallagher
 */
public class TicklerWorker extends Thread {

    private static final int OLD_REFERRAL_CUTOFF_IN_DAYS = 14;

    public String provider = null;
    public String ticklerMessage = null;
    public String status = TicklerData.ACTIVE;
    public String priority = TicklerData.NORMAL;

    public void run() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();

        Calendar calendar = GregorianCalendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -OLD_REFERRAL_CUTOFF_IN_DAYS);

        ConsultationRequestDao dao = SpringUtils.getBean(ConsultationRequestDao.class);
        TicklerData td = new TicklerData();
        for (ConsultationRequest cr : dao.getReferrals(provider, calendar.getTime())) {
            String demo = cr.getDemographicId().toString();
            String date = ConversionUtils.toDateString(cr.getReferralDate());

            ticklerMessage = DemographicNameAgeString.getInstance().getNameAgeString(loggedInInfo, Integer.valueOf(demo)) + " has an Consultation Request with a status of 'Nothing Done'. Referral Date was " + date;
            if (!td.hasTickler(demo, provider, ticklerMessage)) {
                td.addTickler(loggedInInfo, demo, ticklerMessage, status, UtilDateUtilities.getToday("yyyy-MM-dd"), "0", priority, provider);
            }
        }
        /// check to see if tickler is needed
        //if so, check to see if one is already added
        //if so add it
    }
}
