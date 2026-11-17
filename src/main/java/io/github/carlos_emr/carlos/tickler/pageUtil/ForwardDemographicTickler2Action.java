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


package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService;
import io.github.carlos_emr.carlos.documentManager.data.TicklerAttachmentParameters;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;

/**
 * This class is used to forward to the add tickler screen with the demographic preselected
 *
 * <p>A forward from a document ({@code docType=DOC}) keeps the patient only when the document
 * is that patient's own (#4839). Otherwise the patient is dropped: the request attribute
 * {@link #PATIENT_DROPPED_FOR_DOCUMENT} is set, and {@code ticklerAdd.jsp} then opens with no
 * patient and asks the user to choose one.</p>
 *
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ForwardDemographicTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Request attribute set to {@link Boolean#TRUE} when the forwarded {@code demographic_no} was
     * dropped because the forwarding document is not that patient's. {@code ticklerAdd.jsp} then
     * ignores the parameter and asks for a patient.
     */
    public static final String PATIENT_DROPPED_FOR_DOCUMENT = "ticklerPatientDroppedForDocument";

    private final SecurityInfoManager securityInfoManager;
    private final TicklerAttachmentService ticklerAttachmentService;

    /**
     * Creates a new instance of ForwardDemographicTickler2Action
     */
    public ForwardDemographicTickler2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(TicklerAttachmentService.class));
    }

    ForwardDemographicTickler2Action(SecurityInfoManager securityInfoManager,
                                     TicklerAttachmentService ticklerAttachmentService) {
        this.securityInfoManager = securityInfoManager;
        this.ticklerAttachmentService = ticklerAttachmentService;
    }

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "u", null)) {
            throw new RuntimeException("missing required sec object (_tickler)");
        }

        String demoNo = request.getParameter("demographic_no");
        String providerNo = request.getParameter("providerNo");

        // A document's Tickler link names a patient by number. The provider's own eDoc list used
        // to send the provider number there, which opened the form for whichever patient shared
        // that number (#4839). Keep the patient only when the document is theirs, by the same
        // rule the save uses to attach it; HRM, lab and plain forwards are unchanged.
        if (demoNo != null && !demoNo.trim().isEmpty() && !keepsForwardedPatient(loggedInInfo, demoNo)) {
            request.setAttribute(PATIENT_DROPPED_FOR_DOCUMENT, Boolean.TRUE);
            return SUCCESS;
        }
        
        // Ensure that both demographic number of patient and provier number are both not null
        if (demoNo != null && providerNo != null) {
            // If demographic number does not equal to providers number (only for patient tickler), get the patient name
            // Else get the providers name (only for doctor tickler)
       	  demoNo = Encode.forHtmlContent(demoNo);
            if (!demoNo.equals(providerNo)) {
                Map<String, String> h = DemographicNameAgeString.getInstance().getNameAgeSexHashtable(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo);
                request.setAttribute("demographic_no", demoNo);
                request.setAttribute("demoName", h.get("lastName") + ", " + h.get("firstName"));
            } else {
                String providerName = EDocUtil.getProviderName(providerNo);
                request.setAttribute("demographic_no", providerNo);
                request.setAttribute("demoName", providerName);
            }

            String docType = request.getParameter("docType");
            String docId = request.getParameter("docId");

	  if (docType != null) {
		  docType = Encode.forHtmlContent(docType);
	  }
	  if (docId != null) {
		  docId = Encode.forHtmlContent(docId);
	  }
          request.setAttribute("docType", docType);
          request.setAttribute("docId", docId);
        }
        return SUCCESS;
    }

    /**
     * Whether the forwarded patient may stay on the form: always for a forward that is not from
     * a document, and for a document forward only when the document is a live document of that
     * patient. A missing or unparseable document id, or a non-numeric patient, does not qualify.
     */
    private boolean keepsForwardedPatient(LoggedInInfo loggedInInfo, String demoNo) {
        if (TicklerAttachmentParameters.fromLegacyDocType(request.getParameter("docType")) != DocumentType.DOC) {
            return true;
        }
        String docId = request.getParameter("docId");
        try {
            return docId != null && ticklerAttachmentService.belongsToPatient(loggedInInfo, DocumentType.DOC,
                    Integer.parseInt(docId.trim()), null, Integer.valueOf(demoNo.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
