/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/**
 *
 */
package io.github.carlos_emr.carlos.PMmodule.web;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;

import io.github.carlos_emr.carlos.util.DateUtils;

public class ReferralSummaryDisplay {
    public static final Comparator<ReferralSummaryDisplay> REFERRAL_DATE_COMPARATOR = new Comparator<ReferralSummaryDisplay>() {
        public int compare(ReferralSummaryDisplay arg0, ReferralSummaryDisplay arg1) {
            return (arg1.referralDate.compareTo(arg0.referralDate));
        }
    };

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm";
    private SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT);

    private String programName;
    private String programType;
    private String referralDate;
    private String referringProvider;
    private int daysInQueue;
    private String selectVacancy;
    private String vacancyTemplateName;

    public ReferralSummaryDisplay(ClientReferral clientReferral) {
        programName = "local / " + clientReferral.getProgramName();
        programType = clientReferral.getProgramType();
        referralDate = dateFormatter.format(clientReferral.getReferralDate());
        referringProvider = clientReferral.getProviderFormattedName() + " / local";
        daysInQueue = DateUtils.calculateDayDifference(clientReferral.getReferralDate(), new Date());
        selectVacancy = clientReferral.getSelectVacancy();
        vacancyTemplateName = clientReferral.getVacancyTemplateName();
    }

    public String getProgramName() {
        return programName;
    }

    public String getProgramType() {
        return programType;
    }

    public String getReferralDate() {
        return referralDate;
    }

    public String getReferringProvider() {
        return referringProvider;
    }

    public int getDaysInQueue() {
        return daysInQueue;
    }

    public String getSelectVacancy() {
        return selectVacancy;
    }

    public String getVacancyTemplateName() {
        return vacancyTemplateName;
    }
}
