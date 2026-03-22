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


package io.github.carlos_emr.carlos.messenger.data;

/**
 * Data transfer object for displaying messages in the OpenO EMR messaging system interface.
 * 
 * <p>This class encapsulates all the information needed to display a message in the user interface,
 * including metadata, content, attachments, and patient associations. It serves as a view model
 * for message presentation in JSP pages and other UI components.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Message metadata (ID, position, status)</li>
 *   <li>Message content (subject, body, date/time)</li>
 *   <li>Sender and recipient information</li>
 *   <li>Attachment indicators for documents and PDFs</li>
 *   <li>Patient demographic associations</li>
 *   <li>Message type and action links</li>
 * </ul>
 * </p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgMessageData
 * @see MsgProviderData
 */
/**
 * Value object holding the display representation of a single message in the messenger inbox list.
 *
 * @since 2001-01-01
 */
public class MsgDisplayMessage {

    /**
     * Unique identifier for the message in the database.
     */
    private String messageId;
    
    /**
     * Position of this message in a conversation thread or list.
     */
    private String messagePosition;
    
    /**
     * Flag indicating if this is the last message in a thread.
     */
    private boolean isLastMsg;
    
    /**
     * Status of the message (e.g., "new", "read", "archived").
     */
    private String status;
    
    /**
     * Subject line of the message.
     */
    private String thesubject;
    
    /**
     * Date when the message was sent (formatted for display).
     */
    private String thedate;
    
    /**
     * Time when the message was sent (formatted for display).
     */
    private String thetime;
    
    /**
     * Name or identifier of the message sender.
     */
    private String sentby;
    
    /**
     * Name(s) or identifier(s) of message recipient(s).
     */
    private String sentto;
    
    /**
     * Indicator or list of general attachments.
     */
    private String attach;
    
    /**
     * Indicator or list of PDF attachments specifically.
     */
    private String pdfAttach;
    
    /**
     * Associated patient demographic number if message is patient-related.
     */
    private String demographic_no;
    
    /**
     * Formatted patient name and age string for display.
     */
    private String nameage;
    
    /**
     * The main content/body of the message.
     */
    private String messageBody;
    
    /**
     * Message type code (e.g., 1=normal, 2=urgent, etc.).
     */
    private int type;
    
    /**
     * URL or action link associated with the message type.
     */
    private String typeLink;

    /**
     * Gets the unique message identifier.
     * 
     * @return The message ID string
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Sets the unique message identifier.
     * 
     * @param messageId The message ID to set
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * Gets the position of this message in a thread or list.
     * 
     * @return The message position string
     */
    public String getMessagePosition() {
        return messagePosition;
    }

    /**
     * Sets the position of this message in a thread or list.
     * 
     * @param messagePosition The position to set
     */
    public void setMessagePosition(String messagePosition) {
        this.messagePosition = messagePosition;
    }

    /**
     * Checks if this is the last message in a thread.
     * 
     * @return true if this is the last message, false otherwise
     */
    public boolean isLastMsg() {
        return isLastMsg;
    }

    /**
     * Sets whether this is the last message in a thread.
     * 
     * @param isLastMsg true if this is the last message
     */
    public void setLastMsg(boolean isLastMsg) {
        this.isLastMsg = isLastMsg;
    }

    /**
     * Gets the status of the message.
     * 
     * @return The status string (e.g., "new", "read", "archived")
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status of the message.
     * 
     * @param status The status to set (e.g., "new", "read", "archived")
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the subject line of the message.
     * 
     * @return The message subject
     */
    public String getThesubject() {
        return thesubject;
    }

    /**
     * Sets the subject line of the message.
     * 
     * @param thesubject The subject to set
     */
    public void setThesubject(String thesubject) {
        this.thesubject = thesubject;
    }

    /**
     * Gets the date when the message was sent.
     * 
     * @return The formatted date string
     */
    public String getThedate() {
        return thedate;
    }

    /**
     * Sets the date when the message was sent.
     * 
     * @param thedate The date string to set
     */
    public void setThedate(String thedate) {
        this.thedate = thedate;
    }

    /**
     * Gets the time when the message was sent.
     * 
     * @return The formatted time string
     */
    public String getThetime() {
        return thetime;
    }

    /**
     * Sets the time when the message was sent.
     * 
     * @param theime The time string to set (note: parameter name has typo but kept for compatibility)
     */
    public void setThetime(String theime) {
        this.thetime = theime;
    }

    /**
     * Gets the name or identifier of the message sender.
     * 
     * @return The sender's name or ID
     */
    public String getSentby() {
        return sentby;
    }

    /**
     * Sets the name or identifier of the message sender.
     * 
     * @param sentby The sender's name or ID to set
     */
    public void setSentby(String sentby) {
        this.sentby = sentby;
    }

    /**
     * Gets the name(s) or identifier(s) of message recipient(s).
     * 
     * @return The recipient information
     */
    public String getSentto() {
        return sentto;
    }

    /**
     * Sets the name(s) or identifier(s) of message recipient(s).
     * 
     * @param sentto The recipient information to set
     */
    public void setSentto(String sentto) {
        this.sentto = sentto;
    }

    /**
     * Gets the general attachment indicator or list.
     * 
     * @return The attachment information
     */
    public String getAttach() {
        return attach;
    }

    /**
     * Sets the general attachment indicator or list.
     * 
     * @param attach The attachment information to set
     */
    public void setAttach(String attach) {
        this.attach = attach;
    }

    /**
     * Gets the PDF attachment indicator or list.
     * 
     * @return The PDF attachment information
     */
    public String getPdfAttach() {
        return pdfAttach;
    }

    /**
     * Sets the PDF attachment indicator or list.
     * 
     * @param pdfAttach The PDF attachment information to set
     */
    public void setPdfAttach(String pdfAttach) {
        this.pdfAttach = pdfAttach;
    }

    /**
     * Gets the associated patient demographic number.
     * 
     * @return The patient demographic number, or null if not patient-related
     */
    public String getDemographic_no() {
        return demographic_no;
    }

    /**
     * Sets the associated patient demographic number.
     * 
     * @param demographic_no The patient demographic number to set
     */
    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }

    /**
     * Gets the formatted patient name and age string.
     * 
     * @return The patient name and age for display
     */
    public String getNameage() {
        return nameage;
    }

    /**
     * Sets the formatted patient name and age string.
     * 
     * @param nameage The patient name and age to set
     */
    public void setNameage(String nameage) {
        this.nameage = nameage;
    }

    /**
     * Gets the main content/body of the message.
     * 
     * @return The message body text
     */
    public String getMessageBody() {
        return messageBody;
    }

    /**
     * Sets the main content/body of the message.
     * 
     * @param messageBody The message body text to set
     */
    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    /**
     * Gets the message type code.
     * 
     * @return The type code (e.g., 1=normal, 2=urgent)
     */
    public int getType() {
        return type;
    }

    /**
     * Sets the message type code.
     * 
     * @param type The type code to set (e.g., 1=normal, 2=urgent)
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * Gets the URL or action link for the message type.
     * 
     * @return The type-specific link or action URL
     */
    public String getTypeLink() {
        return typeLink;
    }

    /**
     * Sets the URL or action link for the message type.
     * 
     * @param typeLink The type-specific link or action URL to set
     */
    public void setTypeLink(String typeLink) {
        this.typeLink = typeLink;
    }

}
