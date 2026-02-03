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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.encounter.data;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class EctEChartBean {

    public Date eChartTimeStamp;
    public String providerNo;
    public String userName;
    public String demographicNo;
    public String socialHistory;
    public String familyHistory;
    public String medicalHistory;
    public String ongoingConcerns;
    public String reminders;
    public String encounter;
    public String subject;

    public EctEChartBean() {
    }

    public void setEChartBean(String demoNo) {
        demographicNo = demoNo;

        EChartDao dao = SpringUtils.getBean(EChartDao.class);
        EChart ec = dao.getLatestChart(ConversionUtils.fromIntString(demoNo));

        if (ec != null) {
            eChartTimeStamp = ec.getTimestamp();
            socialHistory = ec.getSocialHistory();
            familyHistory = ec.getFamilyHistory();
            medicalHistory = ec.getMedicalHistory();
            ongoingConcerns = ec.getOngoingConcerns();
            reminders = ec.getReminders();
            encounter = ec.getEncounter();
            subject = ec.getSubject();
            providerNo = ec.getProviderNo();
        } else {
            eChartTimeStamp = null;
            socialHistory = "";
            familyHistory = "";
            medicalHistory = "";
            ongoingConcerns = "";
            reminders = "";
            encounter = "";
            subject = "";
            providerNo = "";
        }
    }

}
