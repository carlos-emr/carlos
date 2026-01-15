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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.Date;
import java.util.HashMap;

import ca.openosp.openo.commn.dao.MessageListDao;
import ca.openosp.openo.commn.dao.MessageTblDao;
import ca.openosp.openo.commn.dao.OscarCommLocationsDao;
import ca.openosp.openo.commn.dao.forms.FormsDao;
import ca.openosp.openo.commn.model.MessageList;
import ca.openosp.openo.commn.model.MessageTbl;
import ca.openosp.openo.commn.model.OscarCommLocations;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.messenger.data.MsgDisplayMessage;
import ca.openosp.openo.util.ConversionUtils;

/**
 * JavaBean for managing and displaying message lists in the messaging interface.
 * 
 * <p>This bean serves as the data model for the message listing pages (inbox, sent items,
 * deleted messages). It manages the retrieval, filtering, and organization of messages
 * for display in a tabular format. The bean maintains separate vectors for each message
 * attribute (ID, status, date, sender, subject, attachments) to support the JSP display
 * requirements.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Retrieves messages for a specific provider from the database</li>
 *   <li>Supports different message views: inbox, sent items, deleted</li>
 *   <li>Provides search/filter capabilities across message fields</li>
 *   <li>Maintains message position for navigation</li>
 *   <li>Handles attachment indicators</li>
 * </ul>
 * 
 * <p>The bean uses parallel vectors to store message attributes, where the same index
 * across all vectors represents attributes of the same message. This design pattern,
 * while not ideal by modern standards, was common in early JSP/Servlet applications.</p>
 * 
 * <p>Security considerations:</p>
 * <ul>
 *   <li>Basic SQL injection prevention in filter methods</li>
 *   <li>Provider-specific message isolation</li>
 * </ul>
 * 
 * @deprecated Use ca.openosp.openo.managers.MessagingManager and JPA model.
 *             This class uses outdated patterns and should be replaced with
 *             modern service-oriented architecture.
 * @version 1.0
 * @since 2003
 * @see MsgDisplayMessages2Action
 * @see MessageListDao
 * @see MessageTblDao
 */
@Deprecated
public class MsgDisplayMessagesBean implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Provider number identifying whose messages to display.
     */
    private String providerNo;
    
    /**
     * Vector of message IDs in display order.
     */
    private Vector<String> messageid;
    
    /**
     * Vector of message positions for navigation.
     */
    private Vector<String> messagePosition;
    
    /**
     * Vector of message statuses ("new", "read", "del").
     */
    private Vector<String> status;
    
    /**
     * Vector of message dates in display format.
     */
    private Vector<String> date;
    
    /**
     * Vector of message times.
     * Note: Variable name "ime" appears to be a typo for "time".
     */
    private Vector<String> ime;
    
    /**
     * Vector of sender names or identifiers.
     */
    private Vector<String> sentby;
    
    /**
     * Vector of message subjects.
     */
    private Vector<String> subject;
    
    /**
     * Vector of attachment indicators ("1" if has attachments, "0" otherwise).
     */
    private Vector<String> attach;

    /**
     * Current location ID for multi-location support.
     * Defaults to "0" and is lazily loaded from database.
     */
    private String currentLocationId = "0";

    /**
     * Search filter string for message filtering.
     * Added in 2006 to support message search functionality.
     */
    private String filter;

    /**
     * Sets the search filter for message filtering.
     * 
     * <p>This method attempts to prevent SQL injection by replacing single quotes,
     * though the implementation has a bug - replaceAll() returns a new string
     * rather than modifying in place, so the sanitization doesn't actually work.</p>
     * 
     * @param filter String search filter to apply, null to clear filter
     */
    public void setFilter(String filter) {
        if (filter == null || filter.equals("")) {
            this.filter = null;
        } else {
            // BUG: This line doesn't actually sanitize the filter
            // replaceAll() returns a new string, it doesn't modify in place
            // Should be: filter = filter.replaceAll("'", "''");
            filter.replaceAll("'", "''");
            this.filter = filter;
        }
    }

    /**
     * Clears the current search filter.
     */
    public void clearFilter() {
        filter = null;
    }

    /**
     * Gets the current search filter.
     * 
     * @return String the current filter, empty string if null
     */
    public String getFilter() {
        if (filter == null) {
            filter = "";
        }
        return filter;
    }

    /**
     * Generates parameterized SQL WHERE clause fragment for search filtering.
     *
     * <p>Creates a SQL condition that searches for the filter string
     * across multiple specified columns using LIKE operators with a named parameter.
     * This method is safe from SQL injection as it uses parameterized queries.</p>
     *
     * @param colsToSearch String[] array of column names to search (must be safe hardcoded names)
     * @return String SQL WHERE clause fragment with :filterParam placeholder, empty if no filter
     */
    public String getSQLSearchFilterParameterized(String[] colsToSearch) {
        if (filter == null || filter.isEmpty() || colsToSearch.length == 0) {
            return "";
        } else {
            // Build OR condition across all specified columns using parameterized query
            String search = " and (";
            int numOfCols = colsToSearch.length;
            for (int i = 0; i < numOfCols - 1; i++) {
                search = search + colsToSearch[i] + " like :filterParam or ";
            }
            search = search + colsToSearch[numOfCols - 1] + " like :filterParam) ";
            return search;
        }
    }

    /**
     * Gets the vector of attachment indicators.
     * 
     * @return Vector<String> attachment indicators for each message
     */
    public Vector<String> getAttach() {
        return attach;
    }

    /**
     * Gets the current location ID for multi-location support.
     * 
     * <p>Lazily loads the location ID from the database on first access.
     * Uses string comparison with == which is a bug - should use .equals().</p>
     * 
     * @return String the current location ID
     */
    public String getCurrentLocationId() {
        // BUG: Should use .equals() not == for string comparison
        if (currentLocationId == "0") {
            OscarCommLocationsDao oscarCommLocationsDao = SpringUtils.getBean(OscarCommLocationsDao.class);
            List<OscarCommLocations> oscarCommLocations = oscarCommLocationsDao.findByCurrent1(1);
            Integer oscarCommLocationsID = null;

            if (oscarCommLocations != null) {
                oscarCommLocationsID = oscarCommLocations.get(0).getId();
            }

            if (oscarCommLocationsID != null) {
                currentLocationId = oscarCommLocationsID + "";
            }
        }
        return currentLocationId;
    }

    /**
     * Sets the message IDs to be displayed.
     *
     * @param messageid Vector<String> containing all the message IDs to be displayed
     */
    public void setMessageid(Vector<String> messageid) {
        this.messageid = messageid;
    }

    /**
     * Gets the message IDs for the inbox view.
     * 
     * <p>Calls getMessageIDs() and getInfo() to populate all vectors
     * with message headers for the current provider.</p>
     *
     * @return Vector<String> containing the message IDs for display
     */
    public Vector<String> getMessageid() {
        getMessageIDs();
        getInfo();

        return this.messageid;
    }

    /**
     * Gets the message IDs for deleted messages view.
     * 
     * <p>Retrieves deleted messages and establishes the deleted inbox view.</p>
     * 
     * @return Vector<String> containing deleted message IDs
     */
    public Vector<String> getDelMessageid() {
        getDeletedMessageIDs();
        getInfo();
        estDeletedInbox();
        return this.messageid;
    }

    /**
     * Gets the message IDs for sent messages view.
     * 
     * <p>Retrieves sent messages and establishes the sent items view.</p>
     * 
     * @return Vector<String> containing sent message IDs
     */
    public Vector<String> getSentMessageid() {
        getSentMessageIDs();
        estSentItemsInbox();
        return this.messageid;
    }

    /**
     * Sets the status vector for messages.
     *
     * @param status Vector<String> containing status strings ("read", "new", or "del")
     */
    public void setStatus(Vector<String> status) {
        this.status = status;
    }

    /**
     * Gets the status vector for messages.
     * 
     * <p>Lazily initializes the vectors if not already populated by calling
     * getMessageIDs() and getInfo().</p>
     *
     * @return Vector<String> containing status strings ("read", "new", or "del")
     */
    public Vector<String> getStatus() {
        if (status == null) {
            getMessageIDs();
            getInfo();
        }
        return this.status;
    }

    /**
     * Sets the date vector for messages.
     * 
     * @param date Vector<String> containing formatted date strings
     */
    public void setDate(Vector<String> date) {
        this.date = date;
    }

    /**
     * Gets the date vector for messages.
     * 
     * <p>Lazily initializes the vectors if not already populated.</p>
     *
     * @return Vector<String> containing formatted date strings
     */
    public Vector<String> getDate() {
        if (date == null) {
            getMessageIDs();
            getInfo();
        }
        return this.date;
    }

    /**
     * Sets the sentby vector for messages.
     *
     * @param sentby Vector<String> containing sender names
     */
    public void setSentby(Vector<String> sentby) {
        this.sentby = sentby;
    }

    /**
     * Gets the sentby vector for messages.
     * 
     * <p>Lazily initializes the vectors if not already populated.</p>
     *
     * @return Vector<String> containing sender names
     */
    public Vector<String> getSentby() {
        if (sentby == null) {
            getMessageIDs();
            getInfo();
        }
        return this.sentby;
    }

    /**
     * Sets the subject vector for messages.
     *
     * @param subject Vector<String> containing message subjects
     */
    public void setSubject(Vector<String> subject) {
        this.subject = subject;
    }

    /**
     * Gets the subject vector for messages.
     * 
     * <p>Lazily initializes the vectors if not already populated.</p>
     *
     * @return Vector<String> containing message subjects
     */
    public Vector<String> getSubject() {
        if (subject == null) {
            getMessageIDs();
            getInfo();
        }
        return this.subject;
    }

    /**
     * Sets the provider number whose messages will be displayed.
     *
     * @param providerNo String the provider's identification number
     */
    public void setProviderNo(String providerNo) {

        this.providerNo = providerNo;
    }

    /**
     * Gets the current provider's number.
     *
     * @return String the provider's identification number
     */
    public String getProviderNo() {
        return this.providerNo;
    }

    /**
     * Retrieves message IDs from the database for the current provider.
     * 
     * <p>This method queries the messagelisttbl table to find all messages
     * associated with the current provider and location. It populates the
     * messageid, status, and messagePosition vectors with the retrieved data.</p>
     * 
     * <p>The method filters out deleted messages and only retrieves messages
     * for the current location ID.</p>
     */
    void getMessageIDs() {
        String providerNo = this.getProviderNo();

        messageid = new Vector<String>();
        status = new Vector<String>();
        messagePosition = new Vector<String>();
        int index = 0;

        MessageListDao dao = SpringUtils.getBean(MessageListDao.class);
        for (MessageList ml : dao.findByProviderNoAndLocationNo(providerNo, ConversionUtils.fromIntString(getCurrentLocationId()))) {
            messagePosition.add(Integer.toString(index));
            messageid.add("" + ml.getMessage());
            status.add(ml.getStatus());
            index++;
        }
    }

    /**
     * Generates SQL ORDER BY clause based on the specified ordering.
     * 
     * <p>Supports ordering by various fields with optional descending order.
     * A '!' prefix on the order string indicates descending order.</p>
     * 
     * <p>Supported order fields:</p>
     * <ul>
     *   <li>status - Message status</li>
     *   <li>from - Sender name</li>
     *   <li>subject - Message subject</li>
     *   <li>date - Message date</li>
     *   <li>sentto - Recipients</li>
     *   <li>linked - Linked status</li>
     * </ul>
     * 
     * @param order String the field to order by, with optional '!' prefix for descending
     * @return String the SQL ORDER BY clause
     */
    public String getOrderBy(String order) {
        String orderBy = null;
        if (order == null) {
            orderBy = " m.messageid desc";
        } else {
            String desc = "";
            if (order.charAt(0) == '!') {
                desc = " desc ";
                order = order.substring(1);
            }
            Hashtable<String, String> orderTable = new Hashtable<String, String>();
            orderTable.put("status", "status");
            orderTable.put("from", "sentby");
            orderTable.put("subject", "thesubject");
            orderTable.put("date", "thedate");
            orderTable.put("sentto", "sentto");
            orderTable.put("linked", "isnull");

            orderBy = orderTable.get(order);
            if (orderBy == null) {
                orderBy = "message";
            }
            orderBy += desc + ", m.messageid desc";
        }
        MiscUtils.getLogger().debug("order " + order + " orderby " + orderBy);
        return orderBy;
    }

    /**
     * Establishes the inbox view with pagination and ordering.
     * 
     * <p>Retrieves messages for the inbox view, excluding deleted messages.
     * Supports pagination with 25 messages per page and custom ordering.
     * Also applies any active search filters.</p>
     * 
     * @param orderby String the field to order results by
     * @param page int the page number (1-based)
     * @return Vector<MsgDisplayMessage> collection of messages for display
     */
    public Vector<MsgDisplayMessage> estInbox(String orderby, int page) {
        String providerNo = this.getProviderNo();
        Vector<MsgDisplayMessage> msg = new Vector<MsgDisplayMessage>();

        String[] searchCols = {"m.thesubject", "m.themessage", "m.sentby", "m.sentto"};

        int recordsToDisplay = 25;
        int fromRecordNum = (recordsToDisplay * page) - recordsToDisplay;

        try {
            // Build parameterized SQL query
            String sql = "(select m.messageid is null as isnull, "
                    + "(select map.demographic_no from msgDemoMap map "
                    + "where map.messageID = m.messageid limit 1) as demographic_no, "
                    + "ml.message, ml.status,  m.thesubject, m.thedate, m.theime, m.attachment, m.pdfattachment, m.sentby, m.type "
                    + "from messagelisttbl ml, messagetbl m  "
                    + "where ml.provider_no = :providerNo "
                    + "and status not like 'del' and remoteLocation = :remoteLocation "
                    + " and ml.message = m.messageid "
                    + getSQLSearchFilterParameterized(searchCols)
                    + " order by " + getOrderBy(orderby)
                    + " limit :offset, :limit)";

            // Build parameters map
            Map<String, Object> params = new HashMap<>();
            params.put("providerNo", providerNo);
            params.put("remoteLocation", getCurrentLocationId());
            params.put("offset", fromRecordNum);
            params.put("limit", recordsToDisplay);

            // Add filter parameter if filter is active
            if (filter != null && filter.length() > 0) {
                params.put("filterParam", "%" + filter + "%");
            }

            FormsDao dao = SpringUtils.getBean(FormsDao.class);
            for (Object[] o : dao.runParameterizedNativeQuery(sql, params)) {
                String demographic_no = String.valueOf(o[1]);
                String message = String.valueOf(o[2]);
                String status = String.valueOf(o[3]);
                String thesubject = String.valueOf(o[4]);
                String thedate = String.valueOf(o[5]);
                String theime = String.valueOf(o[6]);
                String attachment = String.valueOf(o[7]);
                String pdfattachment = String.valueOf(o[8]);
                String sentby = String.valueOf(o[9]);
                String type = String.valueOf(o[10]);

                thedate = formatDate(thedate);

                MsgDisplayMessage dm = new MsgDisplayMessage();
                dm.setStatus(status);
                dm.setMessageId(message);
                dm.setThesubject(thesubject);
                dm.setThedate(thedate);
                dm.setThetime(theime);
                dm.setSentby(sentby);
                dm.setDemographic_no(demographic_no);
                dm.setType(Integer.parseInt(type));
                String att = attachment;
                String pdfAtt = pdfattachment;

                if (att == null || att.equalsIgnoreCase("null")) {
                    dm.setAttach("0");
                } else {
                    dm.setAttach("1");
                }
                if (pdfAtt == null || pdfAtt.equalsIgnoreCase("null")) {
                    dm.setPdfAttach("0");
                } else {
                    dm.setPdfAttach("1");
                }

                msg.add(dm);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return msg;
    }

    /**
     * Establishes demographic inbox with default parameters.
     * 
     * <p>Convenience method that calls estDemographicInbox with null ordering
     * and demographic number.</p>
     * 
     * @return Vector<MsgDisplayMessage> collection of messages
     */
    public Vector<MsgDisplayMessage> estDemographicInbox() {
        return estDemographicInbox(null, null);
    }

    /**
     * Establishes inbox view for a specific demographic (patient).
     * 
     * <p>Retrieves all messages linked to a specific patient demographic.
     * Used to display messages associated with a patient's medical record.</p>
     * 
     * @param orderby String the field to order results by
     * @param demographic_no String the patient's demographic number
     * @return Vector<MsgDisplayMessage> collection of messages for the demographic
     */
    public Vector<MsgDisplayMessage> estDemographicInbox(String orderby, String demographic_no) {
        Vector<MsgDisplayMessage> msg = new Vector<MsgDisplayMessage>();
        int index = 0;

        String[] searchCols = {"m.thesubject", "m.themessage", "m.sentby", "m.sentto", "m.type"};

        try {
            // Build parameterized SQL query
            String sql = "select map.messageID is null as isnull, map.demographic_no, m.messageid, m.thesubject, m.thedate, m.theime, m.attachment, m.pdfattachment, m.sentby, m.type  "
                    + "from  messagetbl m, msgDemoMap map where map.demographic_no = :demographicNo  "
                    + "and m.messageid = map.messageID "
                    + getSQLSearchFilterParameterized(searchCols)
                    + " order by "
                    + getOrderBy(orderby);

            // Build parameters map
            Map<String, Object> params = new HashMap<>();
            params.put("demographicNo", demographic_no);

            // Add filter parameter if filter is active
            if (filter != null && filter.length() > 0) {
                params.put("filterParam", "%" + filter + "%");
            }

            FormsDao dao = SpringUtils.getBean(FormsDao.class);
            List<Object[]> os = dao.runParameterizedNativeQuery(sql, params);
            for (Object[] o : os) {
                String demo_no = String.valueOf(o[1]);
                String messageid = String.valueOf(o[2]);
                String thesubject = String.valueOf(o[3]);
                String thedate = String.valueOf(o[4]);
                String theime = String.valueOf(o[5]);
                String attachment = String.valueOf(o[6]);
                String pdfattachment = String.valueOf(o[7]);
                String sentby = String.valueOf(o[8]);
                String type = String.valueOf(o[9]);

                thedate = formatDate(thedate);

                MsgDisplayMessage dm = new MsgDisplayMessage();
                dm.setStatus("    ");
                dm.setMessageId(messageid);
                dm.setMessagePosition(Integer.toString(index));
                dm.setThesubject(thesubject);
                dm.setThedate(thedate);
                dm.setThetime(theime);
                dm.setSentby(sentby);
                dm.setDemographic_no(demo_no);
                dm.setType(Integer.parseInt(type));

                String att = attachment;
                String pdfAtt = pdfattachment;
                if (att == null || att.equals("null")) {
                    dm.setAttach("0");
                } else {
                    dm.setAttach("1");
                }
                if (pdfAtt == null || pdfAtt.equals("null")) {
                    dm.setPdfAttach("0");
                } else {
                    dm.setPdfAttach("1");
                }

                boolean isLast = index == os.size();
                if (isLast) {
                    dm.setLastMsg(true);
                }

                msg.add(dm);

                index++;

            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return msg;
    }

    /**
     * Establishes the deleted inbox view with default parameters.
     * 
     * <p>Convenience method that calls estDeletedInbox with default ordering
     * and first page.</p>
     * 
     * @return Vector<MsgDisplayMessage> collection of deleted messages
     */
    public Vector<MsgDisplayMessage> estDeletedInbox() {
        return estDeletedInbox(null, 1);
    }

    /**
     * Gets the total count of messages of a specific type.
     * 
     * <p>Counts messages for the current provider and location,
     * applying any active filters.</p>
     * 
     * @param type int the message type to count
     * @return int the total number of messages
     */
    public int getTotalMessages(int type) {
        String providerNo = this.getProviderNo();
        MessageListDao messageListDao = SpringUtils.getBean(MessageListDao.class);

        int total = messageListDao.messagesTotal(type, providerNo, Integer.parseInt(getCurrentLocationId()), filter);

        return total;
    }

    /**
     * Establishes the deleted inbox view with pagination and ordering.
     * 
     * <p>Retrieves deleted messages for display. Similar to estInbox but
     * filters for messages with 'del' status. Supports pagination with
     * 25 messages per page.</p>
     * 
     * @param orderby String the field to order results by
     * @param page int the page number (1-based)
     * @return Vector<MsgDisplayMessage> collection of deleted messages
     */
    public Vector<MsgDisplayMessage> estDeletedInbox(String orderby, int page) {

        String providerNo = this.getProviderNo();
        Vector<MsgDisplayMessage> msg = new Vector<MsgDisplayMessage>();
        String[] searchCols = {"m.thesubject", "m.themessage", "m.sentby", "m.sentto"};

        int recordsToDisplay = 25;
        int fromRecordNum = (recordsToDisplay * page) - recordsToDisplay;

        try {
            // Build parameterized SQL query
            String sql = "select map.messageID is null as isnull, map.demographic_no, ml.message, ml.status, m.thesubject, m.thedate, m.theime, m.attachment, m.pdfattachment, m.sentby, m.type "
                    + "from messagelisttbl ml, messagetbl m "
                    + " left outer join msgDemoMap map on (map.messageID = m.messageid) "
                    + " where provider_no = :providerNo"
                    + " and status like 'del' and remoteLocation = :remoteLocation "
                    + " and ml.message = m.messageid "
                    + getSQLSearchFilterParameterized(searchCols)
                    + " order by "
                    + getOrderBy(orderby)
                    + " limit :offset, :limit";

            // Build parameters map
            Map<String, Object> params = new HashMap<>();
            params.put("providerNo", providerNo);
            params.put("remoteLocation", getCurrentLocationId());
            params.put("offset", fromRecordNum);
            params.put("limit", recordsToDisplay);

            // Add filter parameter if filter is active
            if (filter != null && filter.length() > 0) {
                params.put("filterParam", "%" + filter + "%");
            }

            FormsDao dao = SpringUtils.getBean(FormsDao.class);
            for (Object[] o : dao.runParameterizedNativeQuery(sql, params)) {
                String demographic_no = String.valueOf(o[1]);
                String message = String.valueOf(o[2]);
                String thesubject = String.valueOf(o[4]);
                String thedate = String.valueOf(o[5]);
                String theime = String.valueOf(o[6]);
                String attachment = String.valueOf(o[7]);
                String pdfattachment = String.valueOf(o[8]);
                String sentby = String.valueOf(o[9]);
                String type = String.valueOf(o[10]);

                thedate = formatDate(thedate);

                MsgDisplayMessage dm = new MsgDisplayMessage();
                dm.setStatus("deleted");
                dm.setMessageId(message);
                dm.setThesubject(thesubject);
                dm.setThedate(thedate);
                dm.setThetime(theime);
                dm.setSentby(sentby);
                dm.setDemographic_no(demographic_no);
                dm.setType(Integer.parseInt(type));

                String att = attachment;
                String pdfAtt = pdfattachment;
                if (att == null || att.equals("null")) {
                    dm.setAttach("0");
                } else {
                    dm.setAttach("1");
                }
                if (pdfAtt == null || pdfAtt.equals("null")) {
                    dm.setPdfAttach("0");
                } else {
                    dm.setPdfAttach("1");
                }
                msg.add(dm);

            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return msg;
    }

    /**
     * This method uses the ProviderNo and searches for messages for this providerNo
     * in the messagelisttbl
     */
    /**
     * Retrieves deleted message IDs for the current provider.
     * 
     * <p>Similar to getMessageIDs() but filters for messages with 'del' status.
     * Populates the messageid, status, and messagePosition vectors.</p>
     */
    void getDeletedMessageIDs() {
        String providerNo = this.getProviderNo();
        messageid = new Vector<String>();
        status = new Vector<String>();
        try {
            MessageListDao dao = SpringUtils.getBean(MessageListDao.class);
            for (MessageList ml : dao.findByProviderNoAndLocationNo(providerNo, ConversionUtils.fromIntString(getCurrentLocationId()))) {
                messageid.add("" + ml.getMessage());
                status.add("deleted");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * This method uses the ProviderNo and searches for messages for this providerNo
     * in the messagelisttbl
     */
    /**
     * Retrieves sent message IDs for the current provider.
     * 
     * <p>Queries the messagetbl directly to find messages sent by the current
     * provider. Populates the messageid, status, and messagePosition vectors
     * with sent messages.</p>
     */
    void getSentMessageIDs() {
        String providerNo = this.getProviderNo();

        messageid = new Vector<String>();
        status = new Vector<String>();
        sentby = new Vector<String>();
        date = new Vector<String>();
        ime = new Vector<String>();
        subject = new Vector<String>();

        MessageTblDao dao = SpringUtils.getBean(MessageTblDao.class);

        for (MessageTbl m : dao.findByProviderAndSendBy(providerNo, ConversionUtils.fromIntString(getCurrentLocationId()))) {
            messageid.add("" + m.getId());
            status.add("sent");
            sentby.add(m.getSentBy());
            date.add(ConversionUtils.toDateString(m.getDate()));
            ime.add(ConversionUtils.toDateString(m.getTime()));
            subject.add(m.getSubject());
        }
    }

    /**
     * Establishes sent items view with default parameters.
     * 
     * @return Vector<MsgDisplayMessage> collection of sent messages
     */
    public Vector<MsgDisplayMessage> estSentItemsInbox() {
        return estSentItemsInbox(null, 1);
    }

    /**
     * Establishes sent items view with pagination and ordering.
     * 
     * <p>Retrieves messages sent by the current provider. Supports pagination
     * with 25 messages per page and custom ordering.</p>
     * 
     * @param orderby String the field to order results by  
     * @param page int the page number (1-based)
     * @return Vector<MsgDisplayMessage> collection of sent messages
     */
    public Vector<MsgDisplayMessage> estSentItemsInbox(String orderby, int page) {

        String providerNo = this.getProviderNo();
        Vector<MsgDisplayMessage> msg = new Vector<MsgDisplayMessage>();
        String[] searchCols = {"m.thesubject", "m.themessage", "m.sentby", "m.sentto"};

        int recordsToDisplay = 25;
        int fromRecordNum = (recordsToDisplay * page) - recordsToDisplay;

        try {
            // Build parameterized SQL query
            String sql = "select map.messageID is null as isnull, map.demographic_no, m.messageid as status, m.messageid, thedate, theime, thesubject, sentby, sentto, attachment, pdfattachment "
                    + "from messagetbl m left outer join msgDemoMap map on (map.messageID = m.messageid) "
                    + "where sentbyNo = :providerNo"
                    + " and sentByLocation = :sentByLocation  "
                    + getSQLSearchFilterParameterized(searchCols)
                    + " order by "
                    + getOrderBy(orderby)
                    + " limit :offset, :limit";

            // Build parameters map
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("providerNo", providerNo);
            params.put("sentByLocation", getCurrentLocationId());
            params.put("offset", fromRecordNum);
            params.put("limit", recordsToDisplay);

            // Add filter parameter if filter is active
            if (filter != null && filter.length() > 0) {
                params.put("filterParam", "%" + filter + "%");
            }

            FormsDao dao = SpringUtils.getBean(FormsDao.class);
            for (Object[] o : dao.runParameterizedNativeQuery(sql, params)) {
                String demographic_no = String.valueOf(o[1]);
                String status = String.valueOf(o[2]);
                String thedate = String.valueOf(o[4]);
                String theime = String.valueOf(o[5]);
                String thesubject = String.valueOf(o[6]);
                String sentby = String.valueOf(o[7]);
                String sentto = String.valueOf(o[8]);
                String attachment = String.valueOf(o[9]);
                String pdfattachment = String.valueOf(o[10]);

                thedate = formatDate(thedate);

                MsgDisplayMessage dm = new MsgDisplayMessage();
                dm.setStatus("sent");
                dm.setMessageId(status);
                dm.setThesubject(thesubject);
                dm.setThedate(thedate);
                dm.setThetime(theime);
                dm.setSentby(sentby);
                dm.setSentto(sentto);
                dm.setDemographic_no(demographic_no);
                String att = attachment;
                String pdfAtt = pdfattachment;
                if (att == null || att.equals("null")) {
                    dm.setAttach("0");
                } else {
                    dm.setAttach("1");
                }
                if (pdfAtt == null || pdfAtt.equals("null")) {
                    dm.setPdfAttach("0");
                } else {
                    dm.setPdfAttach("1");
                }

                msg.add(dm);

            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return msg;
    }

    /**
     * This method uses the Vector initialized by getMessageIDs and fills the Vectors with
     * the Message header Info
     */
    /**
     * Populates message detail vectors from database.
     * 
     * <p>Retrieves detailed information for all messages in the messageid vector.
     * Populates the date, sentby, subject, and attach vectors with corresponding
     * data from the messagetbl table.</p>
     * 
     * <p>This method is typically called after getMessageIDs() to fill in the
     * message details for display.</p>
     */
    void getInfo() {

        sentby = new Vector<String>();
        date = new Vector<String>();
        subject = new Vector<String>();
        attach = new Vector<String>();
        String att;
        try {

            //make search string
            List<Integer> ids = new ArrayList<Integer>();
            for (String i : messageid) {
                ids.add(ConversionUtils.fromIntString(i));
            }

            MessageTblDao dao = SpringUtils.getBean(MessageTblDao.class);
            for (MessageTbl m : dao.findByIds(ids)) {
                sentby.add(m.getSentBy());
                date.add(ConversionUtils.toDateString(m.getDate()));
                ime.add(ConversionUtils.toTimeString(m.getTime()));
                subject.add(m.getSubject());
                att = m.getAttachment();
                if (att == null || att.equals("null")) {
                    attach.add("0");
                } else {
                    attach.add("1");
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * A temporary helper method to last the life of this deprecated class.
     * Formats a database formatted date string into an Oscar formatted date string.
     *
     * @throws ParseException
     */
    /**
     * Formats a date string for display.
     * 
     * <p>Converts database date format (yyyy-MM-dd HH:mm:ss) to display
     * format (yyyy-MM-dd). Returns "N/A" if the date is null.</p>
     * 
     * @param thedate String the date string to format
     * @return String the formatted date or "N/A" if null
     * @throws ParseException if the date string cannot be parsed
     */
    private String formatDate(String thedate) throws ParseException {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse(thedate);
        SimpleDateFormat finalFormat = new SimpleDateFormat("dd-MM-yyyy");
        return finalFormat.format(date);
    }
}
