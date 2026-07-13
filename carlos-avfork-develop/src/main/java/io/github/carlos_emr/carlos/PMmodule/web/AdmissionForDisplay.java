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

package io.github.carlos_emr.carlos.PMmodule.web;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.Admission;

import io.github.carlos_emr.carlos.util.DateUtils;

/**
 * This is a display object for the history tab of a clients admissions.
 */
public class AdmissionForDisplay {

    public static final Comparator<AdmissionForDisplay> ADMISSION_DATE_COMPARATOR = new Comparator<AdmissionForDisplay>() {
        public int compare(AdmissionForDisplay arg0, AdmissionForDisplay arg1) {
            return (arg1.admissionDate.compareTo(arg0.admissionDate));
        }
    };

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm";

    private SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT);

    private Integer admissionId;
    private String programName;
    private String programType;
    private String facilityName;
    private String admissionDate;
    private String facilityAdmission;
    private String dischargeDate;
    private String facilityDischarge;
    private int daysInProgram;
    private String temporaryAdmission;
    private Integer programId;

    public AdmissionForDisplay(Admission admission) {
        admissionId = admission.getId().intValue();
        programName = admission.getProgramName();
        programType = admission.getProgramType();
        facilityName = "local";

        admissionDate = dateFormatter.format(admission.getAdmissionDate());
        facilityAdmission = String.valueOf(!admission.isAdmissionFromTransfer());
        programId = admission.getProgramId();

        if (admission.getDischargeDate() != null) {
            dischargeDate = dateFormatter.format(admission.getDischargeDate());
            daysInProgram = DateUtils.calculateDayDifference(admission.getAdmissionDate(), admission.getDischargeDate());
        } else {
            daysInProgram = DateUtils.calculateDayDifference(admission.getAdmissionDate(), new Date());
        }

        facilityDischarge = String.valueOf(!admission.isDischargeFromTransfer());
        temporaryAdmission = String.valueOf(admission.isTemporaryAdmission());
    }

    public Integer getAdmissionId() {
        return admissionId;
    }

    public String getProgramName() {
        return programName;
    }

    public String getProgramType() {
        return programType;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public String getAdmissionDate() {
        return admissionDate;
    }

    public String getFacilityAdmission() {
        return facilityAdmission;
    }

    public String getDischargeDate() {
        return dischargeDate;
    }

    public String getFacilityDischarge() {
        return facilityDischarge;
    }

    public int getDaysInProgram() {
        return daysInProgram;
    }

    public String getTemporaryAdmission() {
        return temporaryAdmission;
    }

    public Integer getProgramId() {
        return programId;
    }

    public Integer getClientId() {
        return admissionId;
    }
}
