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

package ca.openosp.openo.messenger.pageUtil;

import java.io.IOException;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;

import org.apache.struts2.ActionSupport;

import ca.openosp.openo.commn.dao.ProviderDataDao;
import ca.openosp.openo.commn.dao.forms.FormsDao;
import ca.openosp.openo.commn.model.ProviderData;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

/**
 * Struts2 action for viewing messages by their position in a demographic-specific message list.
 *
 * <p>This action allows navigation through messages associated with a specific
 * patient demographic based on message position (e.g., first, next, previous, last).</p>
 *
 * <p>Functionality:</p>
 * <ul>
 *   <li>Validates read permissions for messaging.</li>
 *   <li>Ensures a message session bean exists.</li>
 *   <li>Queries for a message ID based on its ordered position within a demographic's message list.</li>
 *   <li>Sets action properties to be forwarded to the message viewing action ({@link MsgViewMessage2Action}).</li>
 * </ul>
 *
 * <p>This action is configured in Struts to redirect to {@code MsgViewMessage2Action},
 * passing the retrieved {@code messageID} and other relevant parameters.</p>
 *
 * @version 2.1
 * @since 2025
 * @see MsgViewMessage2Action
 * @see MsgDisplayMessagesBean
 * @deprecated This action is deprecated and may be removed in a future release.
 */
@Deprecated
public class MsgViewMessageByPosition2Action extends ActionSupport {

    private String messageID;
    private String from;
    private String demographic_no;
    private String messagePosition;

    /**
     * Security manager for enforcing read permissions on messaging operations.
     */
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the message-by-position viewing workflow.
     *
     * <p>This method performs session initialization, permission checks, and then
     * retrieves a specific message ID by its position in an ordered list for a given
     * demographic. The results are set as action properties, which are then used by
     * the Struts2 result configuration (typically a {@code redirectAction}) to forward
     * to the appropriate message viewing action.</p>
     *
     * <p>Request Parameters:</p>
     * <ul>
     *   <li>{@code orderBy} - How to order the messages (default "date").</li>
     *   <li>{@code messagePosition} - The 0-indexed position of the message to retrieve.</li>
     *   <li>{@code demographic_no} - The patient demographic number.</li>
     * </ul>
     *
     * @return {@code SUCCESS} if the operation completes (or fails gracefully).
     * @throws IOException if there's an I/O error
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if user lacks read permissions for messaging
     */
    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();

        // Verify user has read permission for messages
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_msg", "r", null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // Get provider number from session
        String provNo = (String) request.getSession().getAttribute("user");

        // Ensure message session bean exists
        if (request.getSession().getAttribute("msgSessionBean") == null) {
            MsgSessionBean bean = new MsgSessionBean();
            bean.setProviderNo(provNo);

            // Look up provider name for session bean
            ProviderDataDao dao = SpringUtils.getBean(ProviderDataDao.class);
            ProviderData pd = dao.findByProviderNo(provNo);
            if (pd != null) {
                bean.setUserName(pd.getFirstName() + " " + pd.getLastName());
            }
            request.getSession().setAttribute("msgSessionBean", bean);
        }

        // Extract parameters for position-based navigation
        String orderBy = request.getParameter("orderBy") == null ? "date" : request.getParameter("orderBy");
        this.messagePosition = request.getParameter("messagePosition");
        this.demographic_no = request.getParameter("demographic_no");
        this.from = "encounter"; // Set 'from' parameter as in the original action

        MsgDisplayMessagesBean displayMsgBean = new MsgDisplayMessagesBean();

        String sql = "select m.messageid "
                + "from messagetbl m, msgDemoMap mapp where mapp.demographic_no = :demographic_no "
                + "and m.messageid = mapp.messageID order by " + displayMsgBean.getOrderBy(orderBy);

        FormsDao dao = SpringUtils.getBean(FormsDao.class);
        EntityManager em = dao.getEntityManager();

        try {
            Query query = em.createNativeQuery(sql);
            query.setParameter("demographic_no", this.demographic_no);
            query.setFirstResult(Integer.parseInt(this.messagePosition));
            query.setMaxResults(1);
            Integer messageIdResult = (Integer) query.getSingleResult();

            if (messageIdResult != null) {
                this.messageID = messageIdResult.toString();
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error retrieving message by position", e);
            // Optionally add an action error to be displayed on the page
            addActionError("Could not retrieve the requested message.");
        }

        return SUCCESS;
    }

    // Getters and Setters for Struts2 to use in the result
    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getDemographic_no() {
        return demographic_no;
    }

    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }

    public String getMessagePosition() {
        return messagePosition;
    }

    public void setMessagePosition(String messagePosition) {
        this.messagePosition = messagePosition;
    }
}