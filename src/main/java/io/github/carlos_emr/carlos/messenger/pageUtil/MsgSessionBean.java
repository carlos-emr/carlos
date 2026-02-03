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

package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Session bean for maintaining message composition state across requests in the messaging module.
 * 
 * <p>This serializable bean maintains the state of a message being composed, including
 * attachments, recipients, and message content. It is stored in the HTTP session to
 * preserve state across multiple page requests during the message composition process.</p>
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Maintain message composition state (subject, body, attachments)</li>
 *   <li>Track current user/provider information</li>
 *   <li>Manage attachment metadata including PDFs</li>
 *   <li>Handle patient demographic associations</li>
 *   <li>Provide XML-based attachment formatting for storage</li>
 * </ul>
 * </p>
 * 
 * <p>The bean uses a custom XML format for storing PDF attachments with status tracking
 * and content encoding.</p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgCreateMessage2Action
 * @see MsgSendMessage2Action
 */
public class MsgSessionBean implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Provider number of the current user composing the message.
     */
    private String providerNo = null;
    
    /**
     * Display name of the current user.
     */
    private String userName = null;
    
    /**
     * General attachment information or identifiers.
     */
    private String attach = null;
    
    /**
     * XML-formatted PDF attachment data with embedded content.
     */
    private String pdfAttach = null;
    
    /**
     * ID of the message being replied to or forwarded.
     */
    private String messageId = null;
    
    /**
     * Associated patient demographic number if message is patient-related.
     */
    private String demographic_no = null;
    
    /**
     * Total number of attachments expected for this message.
     */
    private int totalAttachmentCount = 0;
    
    /**
     * Current attachment being processed (for multi-attachment messages).
     */
    private int currentAttachmentCount = 0;
    
    /**
     * The message body content.
     */
    private String message;
    
    /**
     * The message subject line.
     */
    private String subject;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }


    public String getProviderNo() {
        return this.providerNo;
    }

    public void setProviderNo(String RHS) {
        this.providerNo = RHS;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String RHS) {
        this.userName = RHS;
    }

    /**
     * Establishes the username by looking up the provider's full name from the database.
     * 
     * <p>This method queries the provider database using the stored provider number
     * and sets the userName field to the provider's full name.</p>
     * 
     * <p>Note: Method name "estUserName" is shortened from "establishUserName"</p>
     */
    public void estUserName() {
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        Provider p = dao.getProvider(providerNo);
        if (p != null) {
            userName = p.getFullName();
        }
    }

    public String getAttachment() {
        return this.attach;
    }

    public void setAttachment(String str) {
        this.attach = str;
    }

    public void setPDFAttachment(String binStr) {
        this.pdfAttach = binStr;
    }

    public String getPDFAttachment() {
        return this.pdfAttach;
    }

    /**
     * Appends a PDF attachment to the existing attachment string using XML format.
     * 
     * <p>This method creates an XML structure for the PDF attachment including:
     * status (OK/BAD), title, and base64-encoded content. Multiple PDFs can be
     * appended sequentially, each with its own XML block.</p>
     * 
     * @param binStr The base64-encoded PDF content
     * @param pdfTitle The title/filename of the PDF attachment
     */
    public void setAppendPDFAttachment(String binStr, String pdfTitle) {

        String currentAtt = "";

        if (this.getPDFAttachment() != null) {
            currentAtt = this.getPDFAttachment();
        }

        // Note: String comparison should use .equals(), not !=
        if (binStr != "" && binStr != null) {
            this.setPDFAttachment(currentAtt + " " + getPDFStartTag() + getStatusTag("OK") + getPDFTitleTag(pdfTitle) + getContentTag(binStr) + getPDFEndTag());
        } else {
            this.setPDFAttachment(currentAtt + " " + getPDFStartTag() + getStatusTag("BAD") + getPDFTitleTag(pdfTitle + " (N/A)") + getContentTag(binStr) + getPDFEndTag());
        }

    }

    public String getPDFStartTag() {
        return "<PDF><FILE_ID>" + currentAttachmentCount + "</FILE_ID>";
    }

    public String getPDFTitleTag(String pdfTitle) {
        return "<TITLE>" + pdfTitle + "</TITLE>";
    }

    public String getContentTag(String binStr) {
        return "<CONTENT>" + binStr + "</CONTENT>";
    }

    public String getStatusTag(String statusStr) {
        return "<STATUS>" + statusStr + "</STATUS>";
    }

    public String getPDFEndTag() {
        return "</PDF>";
    }

    /**
     * Clears all attachment data and resets attachment counters.
     * 
     * <p>This method is typically called when starting a new message or
     * clearing the current message composition state.</p>
     */
    public void nullAttachment() {
        this.attach = null;
        this.pdfAttach = null;
        this.totalAttachmentCount = 0;
        this.currentAttachmentCount = 0;
    }

    public int getTotalAttachmentCount() {
        return this.totalAttachmentCount;
    }

    public void setTotalAttachmentCount(int totalAttachment) {
        this.totalAttachmentCount = totalAttachment;
    }

    public int getCurrentAttachmentCount() {
        return this.currentAttachmentCount;
    }

    public void setCurrentAttachmentCount(int currentAttachmentCount) {
        this.currentAttachmentCount = currentAttachmentCount;
    }

    public String getMessageId() {
        if (this.messageId == null) {
            this.messageId = new String();
        }
        return this.messageId;
    }

    public void setMessageId(String str) {
        this.messageId = str;
    }

    public String getDemographic_no() {
        if (this.demographic_no == null) {
            this.demographic_no = new String();
        }
        return this.demographic_no;
    }

    public void setDemographic_no(String str) {
        this.demographic_no = str;
    }

    /**
     * Clears the message ID reference.
     * 
     * <p>Used when transitioning from a reply/forward to a new message.</p>
     */
    public void nullMessageId() {
        this.messageId = null;
    }

    /**
     * Validates that the session bean has the minimum required data.
     * 
     * <p>A session is considered valid if it has a provider number set,
     * indicating an authenticated user is composing the message.</p>
     * 
     * @return true if the session has a valid provider number, false otherwise
     */
    public boolean isValid() {
        if (this.providerNo != null && this.providerNo.length() > 0) {
            return true;
        }
        return false;
    }

}
