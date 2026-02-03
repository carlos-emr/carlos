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
package io.github.carlos_emr.carlos.messenger.data;

import java.util.Date;

/**
 * Data model for system-generated messages within the OpenO EMR messaging framework.
 * 
 * <p>This class represents automated system messages that are sent to healthcare providers
 * for various administrative and clinical notifications. System messages can include
 * alerts, reminders, lab results notifications, appointment confirmations, and other
 * automated communications within the healthcare facility.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Support for multiple recipients (broadcast messages)</li>
 *   <li>Attachment support for documents and PDFs</li>
 *   <li>Patient demographic associations</li>
 *   <li>Message type classification with action links</li>
 *   <li>Automatic defaults for system-originated fields</li>
 * </ul>
 * </p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgMessageData
 * @see MsgDisplayMessage
 */
public class MessengerSystemMessage {

    /**
     * Date and time when the system message was generated.
     */
    private Date dateTime;
    
    /**
     * The main body content of the system message.
     */
    private String message;
    
    /**
     * Subject line for the message, defaults to "System Message" if not specified.
     */
    private String subject;
    
    /**
     * Display name of the sender, defaults to "System" for automated messages.
     */
    private String sentBy;
    
    /**
     * Provider number of the sender, defaults to "-1" for system-generated messages.
     */
    private String sentByNo;
    
    /**
     * Location/facility ID where the message originated.
     */
    private int sentByLocation;
    
    /**
     * General attachment information or identifiers.
     */
    private String attachment;
    
    /**
     * PDF-specific attachment information.
     */
    private String pdfAttachment;
    
    /**
     * Status or action required for this message.
     */
    private String actionStatus;
    
    /**
     * Message type code for categorization (e.g., lab result, appointment, alert).
     */
    private Integer type;
    
    /**
     * URL or link associated with the message type for taking action.
     */
    private String type_link;
    
    /**
     * Array of provider numbers who should receive this message.
     */
    private String[] recipients;
    
    /**
     * Array of patient demographic numbers associated with this message.
     */
    private Integer[] attachedDemographicNo;

    /**
     * Creates a new system message with specified recipients.
     * 
     * <p>This constructor initializes a system message that will be sent to
     * the specified list of healthcare providers. The recipients array should
     * contain valid provider numbers for all intended recipients.</p>
     *
     * @param recipients Array of provider numbers who should receive this message
     */
    public MessengerSystemMessage(String[] recipients) {
        setRecipients(recipients);
    }

    /**
     * Gets the date and time when the message was created.
     * 
     * @return The message creation date and time
     */
    public Date getDate() {
        return dateTime;
    }

    /**
     * Sets the date and time for the message.
     * 
     * @param dateTime The date and time to set
     */
    public void setDate(Date dateTime) {
        this.dateTime = dateTime;
    }

    /**
     * Gets the message body content.
     * 
     * @return The main message text
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message body content.
     * 
     * @param message The message text to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the message subject line.
     * 
     * @return The subject, or "System Message" if not set
     */
    public String getSubject() {
        if (subject == null) {
            return "System Message";
        }
        return subject;
    }

    /**
     * Sets the message subject line.
     * 
     * @param subject The subject to set
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Gets the sender's display name.
     * 
     * @return The sender name, or "System" if not set
     */
    public String getSentBy() {
        if (sentBy == null) {
            return "System";
        }
        return sentBy;
    }

    /**
     * Sets the sender's display name.
     * 
     * @param sentBy The sender name to set
     */
    public void setSentBy(String sentBy) {
        this.sentBy = sentBy;
    }

    /**
     * Gets the sender's provider number.
     * 
     * @return The provider number, or "-1" for system messages
     */
    public String getSentByNo() {
        if (sentByNo == null) {
            return "-1";
        }
        return sentByNo;
    }

    /**
     * Sets the sender's provider number.
     * 
     * @param sentByNo The provider number to set
     */
    public void setSentByNo(String sentByNo) {
        this.sentByNo = sentByNo;
    }

    /**
     * Gets the location/facility ID where the message originated.
     * 
     * @return The sender's location ID
     */
    public int getSentByLocation() {
        return sentByLocation;
    }

    /**
     * Sets the location/facility ID where the message originated.
     * 
     * @param sentByLocation The location ID to set
     */
    public void setSentByLocation(int sentByLocation) {
        this.sentByLocation = sentByLocation;
    }

    /**
     * Gets the general attachment information.
     * 
     * @return The attachment identifier or description
     */
    public String getAttachment() {
        return attachment;
    }

    /**
     * Sets the general attachment information.
     * 
     * @param attachment The attachment identifier or description to set
     */
    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    /**
     * Gets the PDF attachment information.
     * 
     * @return The PDF attachment identifier or path
     */
    public String getPdfAttachment() {
        return pdfAttachment;
    }

    /**
     * Sets the PDF attachment information.
     * 
     * @param pdfAttachment The PDF attachment identifier or path to set
     */
    public void setPdfAttachment(String pdfAttachment) {
        this.pdfAttachment = pdfAttachment;
    }

    /**
     * Gets the action status for this message.
     * 
     * @return The action status or required action
     */
    public String getActionStatus() {
        return actionStatus;
    }

    /**
     * Sets the action status for this message.
     * 
     * @param actionStatus The action status to set
     */
    public void setActionStatus(String actionStatus) {
        this.actionStatus = actionStatus;
    }

    /**
     * Gets the message type code.
     * 
     * @return The type code for categorization
     */
    public Integer getType() {
        return type;
    }

    /**
     * Sets the message type code.
     * 
     * @param type The type code to set
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * Gets the URL or link associated with the message type.
     * 
     * @return The action link for the message type
     */
    public String getType_link() {
        return type_link;
    }

    /**
     * Sets the URL or link associated with the message type.
     * 
     * @param type_link The action link to set
     */
    public void setType_link(String type_link) {
        this.type_link = type_link;
    }

    /**
     * Gets the array of recipient provider numbers.
     * 
     * @return Array of provider numbers for all recipients
     */
    public String[] getRecipients() {
        return recipients;
    }

    /**
     * Sets the array of recipient provider numbers.
     * 
     * @param recipients Array of provider numbers to set
     */
    public void setRecipients(String[] recipients) {
        this.recipients = recipients;
    }

    /**
     * Gets the array of associated patient demographic numbers.
     * 
     * @return Array of demographic numbers for associated patients
     */
    public Integer[] getAttachedDemographicNo() {
        return attachedDemographicNo;
    }

    /**
     * Sets the array of associated patient demographic numbers.
     * 
     * @param attachedDemographicNo Array of demographic numbers to set
     */
    public void setAttachedDemographicNo(Integer[] attachedDemographicNo) {
        this.attachedDemographicNo = attachedDemographicNo;
    }

}
