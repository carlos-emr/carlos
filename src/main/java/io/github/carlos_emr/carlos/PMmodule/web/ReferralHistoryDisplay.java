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

import java.util.Comparator;
import java.util.Date;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;

public class ReferralHistoryDisplay {

    public static final Comparator<ReferralHistoryDisplay> REFERRAL_DATE_COMPARATOR = new Comparator<ReferralHistoryDisplay>() {
        public int compare(ReferralHistoryDisplay arg0, ReferralHistoryDisplay arg1) {
            return (arg1.referralDate.compareTo(arg0.referralDate));
        }
    };

    private int id;
    private String destinationProgramName;
    private String destinationProgramType;
    private Date referralDate;
    private Date completionDate;
    private String sourceProgramName;
    private String external;

    public ReferralHistoryDisplay(ClientReferral clientReferral) {
        id = clientReferral.getId().intValue();
        destinationProgramName = clientReferral.getProgramName();
        destinationProgramType = clientReferral.getProgramType();
        referralDate = clientReferral.getReferralDate();
        completionDate = clientReferral.getCompletionDate();
        sourceProgramName = clientReferral.getCompletionNotes();
        external = clientReferral.getNotes();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDestinationProgramName() {
        return (StringEscapeUtils.escapeHtml4(destinationProgramName));
    }

    public void setDestinationProgramName(String destinationProgramName) {
        this.destinationProgramName = destinationProgramName;
    }

    public String getDestinationProgramType() {
        return (StringEscapeUtils.escapeHtml4(destinationProgramType));
    }

    public void setDestinationProgramType(String destinationProgramType) {
        this.destinationProgramType = destinationProgramType;
    }

    public Date getReferralDate() {
        return referralDate;
    }

    public void setReferralDate(Date referralDate) {
        this.referralDate = referralDate;
    }

    public String getReferralDateFormatted() {
        if (referralDate != null) return (DateFormatUtils.ISO_DATETIME_FORMAT.format(referralDate));
        else return ("");
    }

    public Date getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(Date completionDate) {
        this.completionDate = completionDate;
    }

    public String getCompletionDateFormatted() {
        if (completionDate != null) return (DateFormatUtils.ISO_DATETIME_FORMAT.format(completionDate));
        else return ("");
    }

    public String getSourceProgramName() {
        return (StringEscapeUtils.escapeHtml4(sourceProgramName));
    }

    public void setSourceProgramName(String sourceProgramName) {
        this.sourceProgramName = sourceProgramName;
    }

    public String getExternal() {
        return (StringEscapeUtils.escapeHtml4(external));
    }

    public void setExternal(String external) {
        this.external = external;
    }
}
