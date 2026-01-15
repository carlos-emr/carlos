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
package ca.openosp.openo.commn.model.inbox;

import com.twelvemonkeys.lang.StringUtil;
import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.lab.ca.on.HRMResultsData;
import ca.openosp.openo.lab.ca.on.LabResultData;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Entity
public class InboxResponse {
    @Id
    private Integer id;
    @Column(name = "documentCount")
    private Integer documentCount;
    @Column(name = "labCount")
    private Integer labCount;
    @Column(name = "hrmCount")
    private Integer hrmCount;
    @Transient
    private List<InboxItem> inboxItems;
    @Transient
    private List<InboxItemDemographicCount> inboxDemographicCounts;

    public Integer getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(Integer documentCount) {
        this.documentCount = documentCount;
    }

    public Integer getLabCount() {
        return labCount;
    }

    public void setLabCount(Integer labCount) {
        this.labCount = labCount;
    }

    public Integer getHrmCount() {
        return hrmCount;
    }

    public void setHrmCount(Integer hrmCount) {
        this.hrmCount = hrmCount;
    }

    public List<InboxItem> getInboxItems() {
        return inboxItems;
    }

    public void setInboxItems(List<InboxItem> inboxItems) {
        this.inboxItems = inboxItems;
    }

    public List<InboxItemDemographicCount> getInboxDemographicCounts() {
        return inboxDemographicCounts;
    }

    public void setInboxDemographicCounts(List<InboxItemDemographicCount> inboxDemographicCounts) {
        this.inboxDemographicCounts = inboxDemographicCounts;
    }

    public List<LabResultData> getLabResultData(LoggedInInfo loggedInInfo) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdfDateOnly = new SimpleDateFormat("yyyy-MM-dd");
        List<LabResultData> resultDataList = new ArrayList<LabResultData>();
        for (InboxItem inboxItem : inboxItems) {
            LabResultData resultData = new LabResultData();
            resultData.setSegmentID(String.valueOf(inboxItem.getId().getSegmentId()));
            resultData.setLabPatientId(inboxItem.getLabPatientId());
            resultData.setAcknowledgedStatus(inboxItem.getStatus() != null ? inboxItem.getStatus() : "U");
            resultData.accessionNumber = inboxItem.getAccessionNumber();
            resultData.patientName = inboxItem.getLastName() + ", " + inboxItem.getFirstName();
            resultData.sex = StringUtils.trimToEmpty(inboxItem.getSex());
            ;
            resultData.healthNumber = StringUtils.trimToEmpty(inboxItem.getHealthNumber());
            if (StringUtils.isEmpty(inboxItem.getFirstName()) || StringUtils.isEmpty(inboxItem.getLastName())) {
                resultData.patientName = "Not, Assigned";
                if ("HRM".equals(inboxItem.getId().getLabType())) {
                    resultData = HRMResultsData.populateHrmResultWithDemographicFromReport(loggedInInfo, resultData);
                }
            }
            resultData.resultStatus = inboxItem.getResultStatus();
            if (StringUtil.isNumber(inboxItem.getFinalResultsCount())) {
                resultData.finalResultsCount = Integer.valueOf(inboxItem.getFinalResultsCount());
            }
            resultData.lastUpdateDate = inboxItem.getLastUpdateDate();
            if (inboxItem.getDateTime() != null) {
                if ("DOC".equals(inboxItem.getId().getLabType())) {
                    resultData.dateTime = sdfDateOnly.format(inboxItem.getDateTime());
                } else {
                    resultData.dateTime = sdf.format(inboxItem.getDateTime());
                }
                resultData.setDateObj(inboxItem.getDateTime());
            }
            resultData.priority = StringUtils.trimToEmpty(inboxItem.getPriority());
            resultData.requestingClient = StringUtils.trimToEmpty(inboxItem.getRequestingClient());
            resultData.discipline = StringUtils.trimToEmpty(inboxItem.getDiscipline());
            resultData.reportStatus = inboxItem.getReportStatus();
            resultData.abn = inboxItem.getAbnormal() != null ? inboxItem.getAbnormal() : false;
            resultData.labType = inboxItem.getId().getLabType();
            resultData.finalRes = (resultData.reportStatus != null && (resultData.reportStatus.equals("F") || resultData.reportStatus.equals("C"))) || "HRM".equals(resultData.labType);
            resultData.setLabel(inboxItem.getLabel());
            resultData.description = inboxItem.getReportDescription();
            resultData.setAckCount(inboxItem.getAcknowledgeCount());
            resultDataList.add(resultData);
        }
        return resultDataList;
    }
}
