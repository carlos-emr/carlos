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

package io.github.carlos_emr.carlos.messenger.tld;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Custom JSP tag for displaying new message indicators in the user interface.
 * 
 * <p>This tag checks if a provider has new unread messages and displays a visual
 * indicator ("msg") in red for new messages or black for no new messages. It provides
 * a quick at-a-glance way for healthcare providers to know if they have pending
 * messages without navigating to the messaging module.</p>
 * 
 * <p>The tag integrates with the messaging system's DAO layer to count messages
 * with "new" status for the specified provider. This is typically used in navigation
 * bars, dashboards, or header sections throughout the application.</p>
 * 
 * <p>Usage in JSP:</p>
 * <pre>
 * &lt;msg:newMessages providerNo="${user}" /&gt;
 * </pre>
 * 
 * <p>Output examples:</p>
 * <ul>
 *   <li>New messages present: Red "msg" text with Verdana font</li>
 *   <li>No new messages: Black "msg" text with Verdana font</li>
 * </ul>
 * 
 * <p>Note: The tag uses deprecated HTML FONT tags for styling, which should be
 * updated to use CSS classes for better maintainability and standards compliance.</p>
 * 
 * @version 1.0
 * @since 2003
 * @see MessageListDao
 * @see TagSupport
 */
public class MsgNewMessagesTag extends TagSupport {

    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The provider number to check for new messages.
     */
    private String providerNo;
    
    /**
     * Count of new messages for the provider.
     */
    private int numNewMessages = 0;

    /**
     * Sets the provider number to check for new messages.
     * 
     * <p>This method is called by the JSP container when processing the tag
     * attribute. The provider number identifies which healthcare provider's
     * messages should be counted.</p>
     * 
     * @param providerNo String the unique identifier of the provider
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Gets the provider number being checked for messages.
     * 
     * @return String the provider number
     */
    public String getProviderNo() {
        return this.providerNo;
    }

    /**
     * Processes the start of the tag and outputs the message indicator.
     * 
     * <p>This method is called when the JSP engine encounters the opening tag.
     * It performs the following operations:</p>
     * <ol>
     *   <li>Retrieves the MessageListDao from the Spring context</li>
     *   <li>Queries for messages with "new" status for the provider</li>
     *   <li>Outputs HTML with color-coded text based on message count</li>
     * </ol>
     * 
     * <p>The visual indicator uses deprecated FONT tags with hardcoded styling:
     * <ul>
     *   <li>Font family: VERDANA, ARIAL, or HELVETICA</li>
     *   <li>Font size: 2 (HTML size units)</li>
     *   <li>Color: Red for new messages, black for none</li>
     * </ul>
     * 
     * <p>Technical note: The method returns SKIP_BODY to indicate that this
     * tag has no body content to process.</p>
     * 
     * @return int SKIP_BODY constant indicating no body processing needed
     * @throws JspException if there's an error during tag processing
     */
    public int doStartTag() throws JspException {

        // Retrieve DAO and count new messages for this provider
        MessageListDao dao = SpringUtils.getBean(MessageListDao.class);
        numNewMessages = dao.findByProviderAndStatus(providerNo, "new").size();
        
        try {
            JspWriter out = pageContext.getOut();
            
            // Output color-coded message indicator based on new message count
            // TODO: Replace deprecated FONT tags with CSS classes
            if (numNewMessages > 0) {
                // Red text indicates new messages are waiting
                out.print("<font FACE=\"VERDANA,ARIAL,HELVETICA\" SIZE=\"2\" color=\"red\">msg</font>  ");
            } else {
                // Black text indicates no new messages
                out.print("<font FACE=\"VERDANA,ARIAL,HELVETICA\" SIZE=\"2\" color=\"black\">msg</font>  ");
            }
        } catch (Exception p) {
            MiscUtils.getLogger().error("Error", p);
        }
        
        return (SKIP_BODY);
    }

    /**
     * Processes the end of the tag.
     * 
     * <p>This method is called when the JSP engine encounters the closing tag.
     * It returns EVAL_PAGE to indicate that the rest of the JSP page should
     * continue to be evaluated normally.</p>
     * 
     * @return int EVAL_PAGE constant to continue page processing
     * @throws JspException if there's an error during tag processing
     */
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

}
