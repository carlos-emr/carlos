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


package ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ca.openosp.openo.fax.core.FaxAccount;
import ca.openosp.openo.fax.core.FaxRecipient;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class EctConsultationFaxForm {

    private ObjectMapper objectMapper = new ObjectMapper();

    private String method;
    private String recipient;
    private String from;
    private String recipientFaxNumber;
    private String sendersPhone;
    private String sendersFax;
	private String senderFaxNumber;
    private String comments;
    private String requestId;
    private String transType;
    private String demographicNo;
    private String[] faxRecipients;
    private boolean coverpage;
    private Set<FaxRecipient> allFaxRecipients;
    private Set<FaxRecipient> copiedTo;
    private HttpServletRequest request;
	private FaxAccount sender;
    
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}
	public String getRecipient() {
		return recipient;
	}
	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}
	public String getFrom() {
		return from;
	}
	public void setFrom(String from) {
		this.from = from;
	}
	public String getRecipientFaxNumber() {
		if(recipientFaxNumber != null) {
			recipientFaxNumber = recipientFaxNumber.trim().replaceAll("\\D", "");
		}
		return recipientFaxNumber;
	}
	public void setRecipientFaxNumber(String recipientFaxNumber) {
		this.recipientFaxNumber = recipientFaxNumber;
	}
	public String getSendersPhone() {
		return sendersPhone;
	}
	public void setSendersPhone(String sendersPhone) {
		this.sendersPhone = sendersPhone;
	}
	public String getSendersFax() {
		return sendersFax;
	}
	public void setSendersFax(String sendersFax) {
		this.sendersFax = sendersFax;
	}

	public String getSenderFaxNumber() {
		return senderFaxNumber;
	}

	public void setSenderFaxNumber(String senderFaxNumber) {
		this.senderFaxNumber = senderFaxNumber;
	}

	public String getComments() {
		return comments;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}
	public String getRequestId() {
		return requestId;
	}
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
	public String getTransType() {
		return transType;
	}
	public void setTransType(String transType) {
		this.transType = transType;
	}
	public String getDemographicNo() {
		return demographicNo;
	}
	public void setDemographicNo(String demographicNo) {
		this.demographicNo = demographicNo;
	}
	public String[] getFaxRecipients() {
		if(faxRecipients ==  null) {
			return new String[]{};
		}
		return faxRecipients;
	}
	public void setFaxRecipients(String[] faxRecipients) {
		this.faxRecipients = faxRecipients;
	}
	public boolean isCoverpage() {
		return coverpage;
	}
	public void setCoverpage(boolean coverpage) {
		this.coverpage = coverpage;
	}
	public Set<FaxRecipient> getAllFaxRecipients() {
		if(allFaxRecipients == null) {
			allFaxRecipients = new HashSet<FaxRecipient>();
			allFaxRecipients.add( new FaxRecipient( getRecipient() , getRecipientFaxNumber() ) );
			allFaxRecipients.addAll(getCopiedTo());
		}

        return allFaxRecipients;
    }

    public Set<FaxRecipient> getCopiedTo() {
        if (copiedTo == null) {
            copiedTo = new HashSet<FaxRecipient>();
            for (String faxRecipient : getFaxRecipients()) {
                try {
                    ObjectNode jsonObject = (ObjectNode) objectMapper.readTree("{" + faxRecipient + "}");
                    copiedTo.add(new FaxRecipient(jsonObject));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse fax recipient JSON", e);
                }
            }
        }
        return copiedTo;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    public FaxAccount getSender() {
        if (sender == null) {
            sender = new FaxAccount();
        }

		sender.setFax(getSenderFaxNumber());
		sender.setLetterheadName(getFrom());
		sender.setPhone(getSendersPhone());

		return sender;
	}


}
