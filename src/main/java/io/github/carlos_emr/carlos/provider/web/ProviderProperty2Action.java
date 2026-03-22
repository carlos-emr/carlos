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


package io.github.carlos_emr.carlos.provider.web;

import java.util.Comparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.*;
import java.util.function.Supplier;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * ProviderProperty2Action
 *
 * Purpose:
 *     Struts2 action class managing provider-specific preferences and settings.
 *     Provides method-based routing to handle viewing and saving various provider
 *     configuration options including lab macros, prevention warnings, and HL7
 *     lab result delivery preferences.
 *
 * Architecture:
 *     - Implements Struts2 ActionSupport for MVC pattern integration
 *     - Uses method-based action routing via methodMap for flexibility
 *     - Delegates to Spring-managed service/DAO layers for business logic
 *     - Supports multiple provider preference types through single action class
 *
 * Key Methods (focus of this documentation):
 *     - viewLabMacroPrefs(): Display lab macro configuration form
 *     - saveLabMacroPrefs(): Persist lab macro JSON to database with validation
 *
 * Security Model:
 *     - All methods require a valid session (centralized null-session guard in execute())
 *     - Lab-related methods enforce role-based access control via SecurityInfoManager with the "_lab" security object
 *     - Enforces READ/WRITE privilege levels for lab macro and HL7 lab result methods
 *     - Other preference methods rely on session authentication only (non-null LoggedInInfo)
 *     - Throws SecurityException for null/invalid session checks
 *     - Throws RuntimeException on privilege violations
 *
 * Data Flow:
 *     - Request parameters parsed and validated by action methods
 *     - Spring-managed DAOs handle database operations
 *     - Preferences stored as UserProperty records keyed by name
 *     - JSP layer receives status attributes for user feedback
 *
 * Lab Macro Workflow:
 *     1. Provider navigates to setLabMacroPrefs.jsp via method=viewLabMacroPrefs
 *     2. JSP renders existing macros from database
 *     3. Provider fills form fields or edits raw JSON directly
 *     4. Form submission calls method=saveLabMacroPrefs
 *     5. Action validates JSON and persists to UserProperty table
 *     6. JSP displays success or error status
 *
 * Exception Handling:
 *     - Invalid JSON triggers validation error (not exception throw)
 *     - Null session checks throw SecurityException
 *     - Privilege violations throw RuntimeException (consistent with file pattern)
 *     - Database errors propagate to framework (transactional)
 *
 * Internationalization:
 *     - Status attributes use simple flag strings ("success", "error") that the JSP maps to localized messages
 *     - JSP layer uses JSTL fmt:message tags for localization
 *     - Currently supports English and Portuguese Brazilian
 *
 * @since 2007-12-21 (Migrated to Struts2 2024-12-06, lab macro methods enhanced 2026-02-17)
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.util.LabelValueBean;

public class ProviderProperty2Action extends ActionSupport {
    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private ProviderManager2 providerManager2 = SpringUtils.getBean(ProviderManager2.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);

    /**
     * Dispatches to the appropriate preference method based on the "method" request parameter.
     *
     * <p>Routes to method implementations registered in {@link #init()}. Falls back to
     * {@link #view()} when the method parameter is null or unrecognized.</p>
     *
     * <p>Validates the provider session before dispatching. All routed methods can safely
     * assume a valid, non-null {@link LoggedInInfo} with a non-null provider number.</p>
     *
     * @return String the Struts2 result name from the dispatched method
     * @throws SecurityException if no valid session or provider number is found
     */
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }

        String method = request.getParameter("method");
        return methodMap.getOrDefault(method, this::view).get();
    }

    /**
     * Updates the provider's message-received notification preference.
     *
     * <p>Saves the {@code value} request parameter as the provider's
     * {@link UserProperty#OSCAR_MSG_RECVD} property. The provider identity is
     * derived from the current session to prevent one provider from modifying
     * another's preferences.</p>
     *
     * @return {@code null} (no Struts result navigation)
     * @throws SecurityException if no valid session is found
     */
    public String OscarMsgRecvd() {
        // Derive provider identity from session to prevent one provider from modifying another's preferences
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String value = request.getParameter("value");
        if (value != null) {
            // OSCAR_MSG_RECVD expects "H:m" format (e.g., "9:0", "14:30")
            if (value.matches("^\\d{1,2}:\\d{1,2}$")) {
                userPropertyDAO.saveProp(providerNo, UserProperty.OSCAR_MSG_RECVD, value);
            } else {
                logger.warn("OscarMsgRecvd called with invalid value format: expected H:m");
            }
        } else {
            logger.debug("OscarMsgRecvd called with null value parameter; no preference saved");
        }

        return null;
    }

    /**
     * Removes the stale-note-date and stale-format user properties for the logged-in provider.
     *
     * @return {@link #SUCCESS} after deletion, with {@code status} request attribute set to {@code "success"}
     * @throws SecurityException if no valid session is found
     */
    public String remove() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }
        String provider = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(provider, UserProperty.STALE_NOTEDATE);
        if (prop != null) {
            this.userPropertyDAO.delete(prop);
        }

        UserProperty prop2 = this.userPropertyDAO.getProp(provider, UserProperty.STALE_FORMAT);
        if (prop2 != null) {
            this.userPropertyDAO.delete(prop2);
        }

        request.setAttribute("status", "success");
        return SUCCESS;
    }

    /**
     * Displays the provider property configuration form with stale-note-date and stale-format options.
     *
     * <p>Loads the current property values from the database (or creates defaults)
     * and populates dropdown option lists for the JSP view.</p>
     *
     * @return {@link #SUCCESS} to forward to the property configuration JSP
     * @throws SecurityException if no valid session is found
     */
    public String view() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }
        String provider = loggedInInfo.getLoggedInProviderNo();
        request.setAttribute("providerNo", provider);

        UserProperty prop = this.userPropertyDAO.getProp(provider, UserProperty.STALE_NOTEDATE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setProviderNo(provider);
            prop.setName(UserProperty.STALE_NOTEDATE);
        }
        this.setDateProperty(prop);

        UserProperty prop2 = this.userPropertyDAO.getProp(provider, UserProperty.STALE_FORMAT);
        if (prop2 == null) {
            prop2 = new UserProperty();
            prop2.setProviderNo(provider);
            prop2.setName(UserProperty.STALE_FORMAT);
        }
        this.setSingleViewProperty(prop2);

        List<LabelValueBean> staleOptions = new ArrayList<>();
        staleOptions.add(new LabelValueBean("All", "A"));
        for (int i = 0; i <= 36; i++) {
            staleOptions.add(new LabelValueBean(String.valueOf(i), "-" + i));
        }
        request.setAttribute("staleDateOptions", staleOptions);

        List<LabelValueBean> viewOptions = new ArrayList<>();
        viewOptions.add(new LabelValueBean("No", "no"));
        viewOptions.add(new LabelValueBean("Yes", "yes"));
        request.setAttribute("viewOptions", viewOptions);

        return SUCCESS;
    }


    /**
     * Saves user stale note date and single view format preferences.
     *
     * @return SUCCESS constant for Struts navigation
     * @throws SecurityException if no valid session is found
     */
    public String save() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            throw new SecurityException("No valid session found");
        }
        String provider = loggedInInfo.getLoggedInProviderNo();

        String staleDateValue = request.getParameter("dateProperty.value");
        String singleViewValue = request.getParameter("singleViewProperty.value");

        UserProperty prop = this.userPropertyDAO.getProp(provider, UserProperty.STALE_NOTEDATE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setProviderNo(provider);
            prop.setName(UserProperty.STALE_NOTEDATE);
        }
        prop.setValue(staleDateValue);
        userPropertyDAO.saveProp(prop);

        UserProperty prop2 = this.userPropertyDAO.getProp(provider, UserProperty.STALE_FORMAT);
        if (prop2 == null) {
            prop2 = new UserProperty();
            prop2.setProviderNo(provider);
            prop2.setName(UserProperty.STALE_FORMAT);
        }
        prop2.setValue(singleViewValue);
        userPropertyDAO.saveProp(prop2);

        request.setAttribute("status", "success");
        return SUCCESS;
    }

    /**
     * typically set from inside the JSP class providerupdatepreference.jsp
     *
     * @param request
     */
    public static void updateOrCreateProviderProperties(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        UserPropertyDAO propertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        List<UserProperty> userProperties = new ArrayList<>();
        String propertyValue;
        UserProperty property;

        propertyValue = StringUtils.trimToNull(request.getParameter(UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS));
        property = propertyDAO.getProp(providerNo, UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
        if (property == null) {
            property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
        }
        property.setValue(String.valueOf(Boolean.parseBoolean(propertyValue)));
        propertyDAO.saveProp(property);

    }

    /**
     * Displays the default patient sex preference configuration form.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewDefaultSex() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.DEFAULT_SEX);

        if (prop == null) {
            prop = new UserProperty();
        }

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        serviceList.add(new LabelValueBean("M", "M"));
        serviceList.add(new LabelValueBean("F", "F"));

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("dateProperty", prop);

        request.setAttribute("providertitle", "provider.setDefaultSex.title");
        request.setAttribute("providermsgPrefs", "provider.setDefaultSex.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setDefaultSex.msgDefaultSex");
        request.setAttribute("providermsgEdit", "provider.setDefaultSex.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setDefaultSex.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setDefaultSex.msgSuccess");
        request.setAttribute("method", "saveDefaultSex");

        this.setDateProperty(prop);
        return "gen";
    }

    /**
     * Saves the provider's default patient sex preference.
     *
     * @return String {@code "gen"} to re-display the configuration form with success status
     */
    public String saveDefaultSex() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty prop = this.getDateProperty();
        String fmt = prop != null ? prop.getValue() : "";
        UserProperty saveProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.DEFAULT_SEX);

        if (saveProperty == null) {
            saveProperty = new UserProperty();
            saveProperty.setProviderNo(providerNo);
            saveProperty.setName(UserProperty.DEFAULT_SEX);
        }

        saveProperty.setValue(fmt);
        this.userPropertyDAO.saveProp(saveProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.setDefaultSex.title");
        request.setAttribute("providermsgPrefs", "provider.setDefaultSex.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setDefaultSex.msgDefaultSex");
        request.setAttribute("providermsgEdit", "provider.setDefaultSex.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setDefaultSex.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setDefaultSex.msgSuccess");
        request.setAttribute("method", "saveDefaultSex");

        return "gen";
    }
    /////

    /**
     * Displays the default health card type preference configuration form.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewHCType() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.HC_TYPE);

        if (prop == null) {
            prop = new UserProperty();
        }

        // Add all provinces / states to serviceList
        ArrayList<LabelValueBean> serviceList = constructProvinceList();

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("dateProperty", prop);

        request.setAttribute("providertitle", "provider.setHCType.title");
        request.setAttribute("providermsgPrefs", "provider.setHCType.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setHCType.msgHCType");
        request.setAttribute("providermsgEdit", "provider.setHCType.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setHCType.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setHCType.msgSuccess");
        request.setAttribute("method", "saveHCType");

        this.setDateProperty(prop);
        return "gen";
    }

    /**
     * Saves the provider's default health card type preference.
     *
     * @return String {@code "gen"} to re-display the configuration form with success status
     */
    public String saveHCType() {


        UserProperty prop = this.getDateProperty();
        String fmt = prop != null ? prop.getValue() : "";
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty saveProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.HC_TYPE);

        if (saveProperty == null) {
            saveProperty = new UserProperty();
            saveProperty.setProviderNo(providerNo);
            saveProperty.setName(UserProperty.HC_TYPE);
        }

        saveProperty.setValue(fmt);
        this.userPropertyDAO.saveProp(saveProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.setHCType.title");
        request.setAttribute("providermsgPrefs", "provider.setHCType.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setHCType.msgHCType");
        request.setAttribute("providermsgEdit", "provider.setHCType.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setHCType.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setHCType.msgSuccess");
        request.setAttribute("method", "saveHCType");

        return "gen";
    }


    /////


    /**
     * Displays the prescription page size preference configuration form.
     *
     * @return String {@code "genRxPageSize"} to forward to the Rx page size configuration JSP
     */
    public String viewRxPageSize() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_PAGE_SIZE);

        if (prop == null) {
            prop = new UserProperty();
        }

        request.setAttribute("providertitle", "provider.setRxPageSize.title");
        request.setAttribute("providermsgPrefs", "provider.setRxPageSize.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxPageSize.msgPageSize");
        request.setAttribute("providermsgEdit", "provider.setRxPageSize.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxPageSize.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setRxPageSize.msgSuccess");
        request.setAttribute("method", "saveRxPageSize");

        this.setRxPageSizeProperty(prop);
        return "genRxPageSize";
    }

    /**
     * Saves the provider's prescription page size preference (e.g., A4, A6).
     *
     * @return String {@code "genRxPageSize"} to re-display with success status
     */
    public String saveRxPageSize() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty UPageSize = getRxPageSizeProperty();
        String rxPageSize = "";
        if (UPageSize != null)
            rxPageSize = UPageSize.getValue();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_PAGE_SIZE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.RX_PAGE_SIZE);
            prop.setProviderNo(providerNo);
        }
        prop.setValue(rxPageSize);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("rxPageSizeProperty", prop);
        request.setAttribute("providertitle", "provider.setRxPageSize.title");
        request.setAttribute("providermsgPrefs", "provider.setRxPageSize.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxPageSize.msgPageSize");
        request.setAttribute("providermsgEdit", "provider.setRxPageSize.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxPageSize.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setRxPageSize.msgSuccess");
        request.setAttribute("method", "saveRxPageSize");
        return "genRxPageSize";
    }

    /**
     * Saves the provider's default document queue preference, supporting both existing and new queues.
     *
     * @return String {@code "genDefaultDocQueue"} to re-display with success or error status
     */
    public String saveDefaultDocQueue() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty existingQ = this.getExistingDefaultDocQueueProperty();
        UserProperty newQ = this.getNewDefaultDocQueueProperty();
        String mode = request.getParameter("chooseMode");
        String defaultQ = "";
        if (mode.equalsIgnoreCase("new") && newQ != null)
            defaultQ = newQ.getValue();
        else if (mode.equalsIgnoreCase("existing") && existingQ != null)
            defaultQ = existingQ.getValue();
        else {
            request.setAttribute("status", "success");
            request.setAttribute("providertitle", "provider.setDefaultDocumentQueue.title");
            request.setAttribute("providermsgPrefs", "provider.setDefaultDocumentQueue.msgPrefs");
            request.setAttribute("providermsgProvider", "provider.setDefaultDocumentQueue.msgProfileView");
            request.setAttribute("providermsgSuccess", "provider.setDefaultDocumentQueue.msgNotSaved");
            request.setAttribute("method", "saveDefaultDocQueue");
            return "genDefaultDocQueue";
        }
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.DOC_DEFAULT_QUEUE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.DOC_DEFAULT_QUEUE);
            prop.setProviderNo(providerNo);
        }
        if (mode.equals("new")) {
            QueueDao queueDao = (QueueDao) SpringUtils.getBean(QueueDao.class);
            queueDao.addNewQueue(defaultQ);
            String lastId = queueDao.getLastId();
            prop.setValue(lastId);
            this.userPropertyDAO.saveProp(prop);
        } else {
            prop.setValue(defaultQ);
            this.userPropertyDAO.saveProp(prop);
        }
        request.setAttribute("status", "success");
        request.setAttribute("defaultDocQueueProperty", prop);
        request.setAttribute("providertitle", "provider.setDefaultDocumentQueue.title");
        request.setAttribute("providermsgPrefs", "provider.setDefaultDocumentQueue.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setDefaultDocumentQueue.msgProfileView");
        request.setAttribute("providermsgSuccess", "provider.setDefaultDocumentQueue.msgSuccess");
        request.setAttribute("method", "saveDefaultDocQueue");
        return "genDefaultDocQueue";
    }

    //public String viewDefaultDocQueue(){
    //    return "genDefaultDocQueue";
    //}
    /**
     * Displays the default document queue preference configuration form with available queues.
     *
     * @return String {@code "genDefaultDocQueue"} to forward to the queue configuration JSP
     */
    public String viewDefaultDocQueue() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.DOC_DEFAULT_QUEUE);
        UserProperty propNew = new UserProperty();

        if (prop == null) {
            prop = new UserProperty();
        }
        QueueDao queueDao = (QueueDao) SpringUtils.getBean(QueueDao.class);
        List<Hashtable> queues = queueDao.getQueues();
        Collection<LabelValueBean> viewChoices = new ArrayList<LabelValueBean>();
        viewChoices.add(new LabelValueBean("None", "-1"));
        for (Hashtable ht : queues) {
            viewChoices.add(new LabelValueBean((String) ht.get("queue"), (String) ht.get("id")));
        }
        request.setAttribute("viewChoices", viewChoices);
        request.setAttribute("providertitle", "provider.setDefaultDocumentQueue.title");
        request.setAttribute("providermsgPrefs", "provider.setDefaultDocumentQueue.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setDefaultDocumentQueue.msgProfileView");
        request.setAttribute("providermsgEditFromExisting", "provider.setDefaultDocumentQueue.msgEditFromExisting");
        request.setAttribute("providermsgEditSaveNew", "provider.setDefaultDocumentQueue.msgEditSaveNew");
        request.setAttribute("providerbtnSubmit", "provider.setDefaultDocumentQueue.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setDefaultDocumentQueue.msgSuccess");
        request.setAttribute("method", "saveDefaultDocQueue");
        this.setExistingDefaultDocQueueProperty(prop);
        this.setNewDefaultDocQueueProperty(propNew);
        return "genDefaultDocQueue";
    }

    /**
     * Displays the prescription profile view preference form with multi-select filter options.
     *
     * @return String {@code "genRxProfileView"} to forward to the Rx profile view JSP
     */
    public String viewRxProfileView() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_PROFILE_VIEW);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        String[] propertyArray = new String[7];
        String[] va = {" show_current ", " show_all ", " active ", " inactive ", " all ", " longterm_acute ", " longterm_acute_inactive_external "};

        for (int i = 0; i < propertyArray.length; i++) {
            if (propValue.contains(va[i])) {
                propertyArray[i] = va[i].trim();
            }//element of array has to match exactly with viewChoices values
        }
        prop.setValueArray(propertyArray);
        Collection<LabelValueBean> viewChoices = new ArrayList<LabelValueBean>();
        viewChoices.add(new LabelValueBean("Current", "show_current"));
        viewChoices.add(new LabelValueBean("All", "show_all"));
        viewChoices.add(new LabelValueBean("Active", "active"));
        viewChoices.add(new LabelValueBean("Expired", "inactive"));
        viewChoices.add(new LabelValueBean("Longterm/Acute", "longterm_acute"));
        viewChoices.add(new LabelValueBean("Longterm/Acute/Inactive/External", "longterm_acute_inactive_external"));
        request.setAttribute("viewChoices", viewChoices);
        request.setAttribute("providertitle", "provider.setRxProfileView.title");
        request.setAttribute("providermsgPrefs", "provider.setRxProfileView.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxProfileView.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setRxProfileView.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxProfileView.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setRxProfileView.msgSuccess");
        request.setAttribute("method", "saveRxProfileView");


        this.setRxProfileViewProperty(prop);

        return "genRxProfileView";
    }

    /**
     * Saves the provider's prescription profile view filter preferences.
     *
     * @return String {@code "genRxProfileView"} to re-display with success or error status
     */
    public String saveRxProfileView() {

        try {
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            String providerNo = loggedInInfo.getLoggedInProviderNo();


            UserProperty UProfileView = this.getRxProfileViewProperty();
            String[] va = null;
            if (UProfileView != null)
                va = UProfileView.getValueArray();
            UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_PROFILE_VIEW);
            if (prop == null) {
                prop = new UserProperty();
                prop.setName(UserProperty.RX_PROFILE_VIEW);
                prop.setProviderNo(providerNo);
            }

            String rxProfileView = "";
            if (va != null) {
                for (int i = 0; i < va.length; i++) {
                    rxProfileView += " " + va[i] + " ";
                }
            }
            prop.setValue(rxProfileView);
            this.userPropertyDAO.saveProp(prop);

            request.setAttribute("status", "success");
            request.setAttribute("defaultDocQueueProperty", prop);
            request.setAttribute("providertitle", "provider.setRxProfileView.title");
            request.setAttribute("providermsgPrefs", "provider.setRxProfileView.msgPrefs");
            request.setAttribute("providermsgProvider", "provider.setRxProfileView.msgProfileView");
            request.setAttribute("providermsgEdit", "provider.setRxProfileView.msgEdit");
            request.setAttribute("providerbtnSubmit", "provider.setRxProfileView.btnSubmit");
            request.setAttribute("providermsgSuccess", "provider.setRxProfileView.msgSuccess");
            request.setAttribute("method", "saveRxProfileView");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to save Rx profile view preference", e);
            request.setAttribute("status", "error");
        }

        return "genRxProfileView";
    }

    /**
     * Displays the "show patient date of birth on prescriptions" checkbox preference form.
     *
     * @return String {@code "genShowPatientDOB"} to forward to the DOB display configuration JSP
     */
    public String viewShowPatientDOB() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_SHOW_PATIENT_DOB);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        //String [] propertyArray= new String[7];
        boolean checked;
        if (propValue.equalsIgnoreCase("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("rxShowPatientDOBProperty", prop);
        request.setAttribute("providertitle", "provider.setShowPatientDOB.title");
        request.setAttribute("providermsgPrefs", "provider.setShowPatientDOB.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setShowPatientDOB.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setShowPatientDOB.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setShowPatientDOB.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setShowPatientDOB.msgSuccess");
        request.setAttribute("method", "saveShowPatientDOB");

        this.setRxShowPatientDOBProperty(prop);
        return "genShowPatientDOB";
    }

    /**
     * Saves the provider's preference for showing patient date of birth on prescriptions.
     *
     * @return String {@code "genShowPatientDOB"} to re-display with success status
     */
    public String saveShowPatientDOB() {
        String checkboxValue = request.getParameter("rxShowPatientDOBProperty.checked");

        UserProperty UShowPatientDOB = this.getRxShowPatientDOBProperty();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_SHOW_PATIENT_DOB);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.RX_SHOW_PATIENT_DOB);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String showPatientDOB = "no";
        if (checked)
            showPatientDOB = "yes";
        prop.setValue(showPatientDOB);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("rxShowPatientDOBProperty", prop);
        request.setAttribute("providertitle", "provider.setShowPatientDOB.title");
        request.setAttribute("providermsgPrefs", "provider.setShowPatientDOB.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setShowPatientDOB.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setShowPatientDOB.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setShowPatientDOB.btnSubmit");
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.setShowPatientDOB.msgSuccess_selected");
        else
            request.setAttribute("providermsgSuccess", "provider.setShowPatientDOB.msgSuccess_unselected");
        request.setAttribute("method", "saveShowPatientDOB");
        return "genShowPatientDOB";
    }



    /**
     * Displays the "use Rx3 prescription format" checkbox preference form.
     *
     * @return String {@code "genRxUseRx3"} to forward to the Rx3 configuration JSP
     */
    public String viewUseRx3() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_USE_RX3);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equalsIgnoreCase("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("rxUseRx3Property", prop);
        request.setAttribute("providertitle", "provider.setRxRxUseRx3.title");
        request.setAttribute("providermsgPrefs", "provider.setRxRxUseRx3.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxRxUseRx3.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setRxUseRx3.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxUseRx3.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setRxUseRx3.msgSuccess_selected");
        request.setAttribute("method", "saveUseRx3");

        this.setRxUseRx3Property(prop);
        return "genRxUseRx3";
    }

    /**
     * Saves the provider's Rx3 prescription format preference.
     *
     * @return String {@code "genRxUseRx3"} to re-display with success status
     */
    public String saveUseRx3() {
        String checkboxValue = request.getParameter("rxUseRx3Property.checked");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty UUseRx3 = this.getRxUseRx3Property();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.RX_USE_RX3);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.RX_USE_RX3);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String useRx3 = "no";
        if (checked)
            useRx3 = "yes";
        prop.setValue(useRx3);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("rxUseRx3Property", prop);
        request.setAttribute("providertitle", "provider.setRxRxUseRx3.title");
        request.setAttribute("providermsgPrefs", "provider.setRxRxUseRx3.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxRxUseRx3.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setRxUseRx3.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxUseRx3.btnSubmit");
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.setRxUseRx3.msgSuccess_selected");
        else
            request.setAttribute("providermsgSuccess", "provider.setRxUseRx3.msgSuccess_unselected");
        request.setAttribute("method", "saveUseRx3");
        return "genRxUseRx3";
    }

    /**
     * Displays the default prescription quantity preference configuration form.
     *
     * @return String {@code "genRxDefaultQuantity"} to forward to the quantity configuration JSP
     */
    public String viewDefaultQuantity() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty quantity = this.userPropertyDAO.getProp(providerNo, "rxDefaultQuantityProperty");

        if (quantity == null) {
            quantity = new UserProperty();
        }

        //request.setAttribute("propert", propertyToSet);
        request.setAttribute("quantity", quantity);
        request.setAttribute("providertitle", "provider.setRxDefaultQuantity.title");
        request.setAttribute("providermsgPrefs", "provider.setRxDefaultQuantity.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxDefaultQuantity.msgDefaultQuantity");
        request.setAttribute("providermsgEdit", "provider.setRxDefaultQuantity.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxDefaultQuantity.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setRxDefaultQuantity.msgSuccess");
        request.setAttribute("method", "saveDefaultQuantity");

        this.setRxDefaultQuantityProperty(quantity);

        return "genRxDefaultQuantity";
    }

    /**
     * Saves the provider's default prescription quantity preference.
     *
     * @return String {@code "genRxDefaultQuantity"} to re-display with success status
     */
    public String saveDefaultQuantity() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty UDefaultQuantity = this.getRxDefaultQuantityProperty();
        String quantity = UDefaultQuantity != null ? UDefaultQuantity.getValue() : "";

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, "rxDefaultQuantityProperty");
        if (prop == null) {
            prop = new UserProperty();
            prop.setName("rxDefaultQuantityProperty");
            prop.setProviderNo(providerNo);
        }
        prop.setValue(quantity);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("quantity", quantity);
        request.setAttribute("providertitle", "provider.setRxDefaultQuantity.title");
        request.setAttribute("providermsgPrefs", "provider.setRxDefaultQuantity.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setRxDefaultQuantity.msgDefaultQuantity");
        request.setAttribute("providermsgEdit", "provider.setRxDefaultQuantity.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setRxDefaultQuantity.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setRxDefaultQuantity.msgSuccess");
        request.setAttribute("method", "saveDefaultQuantity");
        return "genRxDefaultQuantity";
    }

    /////


    /**
     * Displays the Ontario MD credentials configuration form.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewOntarioMDId() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.ONTARIO_MD_USERNAME);
        UserProperty prop2 = this.userPropertyDAO.getProp(providerNo, UserProperty.ONTARIO_MD_PASSWORD);

        if (prop == null) {
            prop = new UserProperty();
        }

        if (prop2 == null) {
            prop2 = new UserProperty();
        }

        //request.setAttribute("propert", propertyToSet);
        request.setAttribute("dateProperty", prop);
        request.setAttribute("dateProperty2", prop2);


        request.setAttribute("providertitle", "provider.setOntarioMD.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setOntarioMD.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setOntarioMD.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setOntarioMD.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setOntarioMD.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setOntarioMD.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveOntarioMDId");

        this.setDateProperty(prop);
        return "gen";
    }


    /**
     * Saves the provider's Ontario MD username and password credentials.
     *
     * @return String {@code "gen"} to re-display with success status
     */
    public String saveOntarioMDId() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty UdrugrefId = this.getDateProperty();
        String drugrefId = "";

        if (UdrugrefId != null) {
            drugrefId = UdrugrefId.getValue();
        }

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.ONTARIO_MD_USERNAME);

        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.ONTARIO_MD_USERNAME);
            prop.setProviderNo(providerNo);
        }
        prop.setValue(drugrefId);

        this.userPropertyDAO.saveProp(prop);


        UserProperty UdrugrefId2 = this.getDateProperty2();
        String drugrefId2 = "";

        if (UdrugrefId2 != null) {
            drugrefId2 = UdrugrefId2.getValue();
        }

        UserProperty prop2 = this.userPropertyDAO.getProp(providerNo, UserProperty.ONTARIO_MD_PASSWORD);

        if (prop2 == null) {
            prop2 = new UserProperty();
            prop2.setName(UserProperty.ONTARIO_MD_PASSWORD);
            prop2.setProviderNo(providerNo);
        }
        prop2.setValue(drugrefId2);

        this.userPropertyDAO.saveProp(prop2);


        request.setAttribute("status", "success");
        request.setAttribute("dateProperty", prop);
        request.setAttribute("providertitle", "provider.setOntarioMD.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setOntarioMD.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setOntarioMD.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setOntarioMD.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setOntarioMD.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setOntarioMD.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveOntarioMDId");
        return "gen";
    }
    /*ontario md*/

    /**
     * Displays the consultation request cutoff date warning preference form.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewConsultationRequestCuffOffDate() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_TIME_PERIOD_WARNING);

        if (prop == null) {
            prop = new UserProperty();
        }

        request.setAttribute("dateProperty", prop);

        request.setAttribute("providertitle", "provider.setConsultationCutOffDate.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setConsultationCutOffDate.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setConsultationCutOffDate.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setConsultationCutOffDate.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setConsultationCutOffDate.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setConsultationCutOffDate.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveConsultationRequestCuffOffDate");

        this.setDateProperty(prop);
        return "gen";
    }


    /**
     * Saves the provider's consultation request cutoff date warning preference.
     *
     * @return String {@code "gen"} to re-display with success status
     */
    public String saveConsultationRequestCuffOffDate() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty UdrugrefId = this.getDateProperty();
        String drugrefId = "";

        if (UdrugrefId != null) {
            drugrefId = UdrugrefId.getValue();
        }

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_TIME_PERIOD_WARNING);

        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.CONSULTATION_TIME_PERIOD_WARNING);
            prop.setProviderNo(providerNo);
        }
        prop.setValue(drugrefId);

        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("dateProperty", prop);
        request.setAttribute("providertitle", "provider.setConsultationCutOffDate.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setConsultationCutOffDate.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setConsultationCutOffDate.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setConsultationCutOffDate.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setConsultationCutOffDate.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setConsultationCutOffDate.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveConsultationRequestCuffOffDate");
        return "gen";
    }


    //// CONSULT TEAM

    /**
     * Displays the consultation request team warning preference form with available teams.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewConsultationRequestTeamWarning() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_TEAM_WARNING);

        if (prop == null) {
            prop = new UserProperty();
        }

        EctConsultationFormRequestUtil conUtil = new EctConsultationFormRequestUtil();
        conUtil.estTeams();
        Vector<String> vect = conUtil.teamVec;

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        serviceList.add(new LabelValueBean("All", "-1"));
        for (String s : vect) {
            serviceList.add(new LabelValueBean(s, s));
        }
        serviceList.add(new LabelValueBean("None", ""));


        //conUtil.teamVec.add("All");
        //conUtil.teamVec.add("None");

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("dateProperty", prop);

        request.setAttribute("providertitle", "provider.setConsultationTeamWarning.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setConsultationTeamWarning.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setConsultationTeamWarning.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setConsultationTeamWarning.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setConsultationTeamWarning.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setConsultationTeamWarning.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveConsultationRequestTeamWarning");

        this.setDateProperty(prop);
        return "gen";
    }


    /**
     * Saves the provider's consultation team warning preference.
     *
     * @return String {@code "gen"} to re-display with success status
     */
    public String saveConsultationRequestTeamWarning() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty UdrugrefId = this.getDateProperty();
        String drugrefId = "";

        if (UdrugrefId != null) {
            drugrefId = UdrugrefId.getValue();
        }

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_TEAM_WARNING);

        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.CONSULTATION_TEAM_WARNING);
            prop.setProviderNo(providerNo);
        }
        prop.setValue(drugrefId);

        this.userPropertyDAO.saveProp(prop);


        EctConsultationFormRequestUtil conUtil = new EctConsultationFormRequestUtil();
        conUtil.estTeams();
        Vector<String> vect = conUtil.teamVec;

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        serviceList.add(new LabelValueBean("All", "-1"));
        for (String s : vect) {
            serviceList.add(new LabelValueBean(s, s));
        }
        serviceList.add(new LabelValueBean("None", ""));
        request.setAttribute("dropOpts", serviceList);


        request.setAttribute("status", "success");
        request.setAttribute("dateProperty", prop);
        request.setAttribute("providertitle", "provider.setConsultationTeamWarning.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setConsultationTeamWarning.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setConsultationTeamWarning.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setConsultationTeamWarning.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setConsultationTeamWarning.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setConsultationTeamWarning.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveConsultationRequestTeamWarning");
        return "gen";
    }


    /**
     * Displays the workload management billing service type preference form.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewWorkLoadManagement() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty UdrugrefId = this.userPropertyDAO.getProp(providerNo, "dateProperty");

        if (UdrugrefId == null)
            UdrugrefId = new UserProperty();

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        CtlBillingServiceDao dao = SpringUtils.getBean(CtlBillingServiceDao.class);
        for (Object[] service : dao.getUniqueServiceTypes())
            serviceList.add(new LabelValueBean(String.valueOf(service[0]), String.valueOf(service[1])));
        serviceList.add(new LabelValueBean("None", ""));

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("UdrugrefId", UdrugrefId);

        request.setAttribute("providertitle", "provider.setWorkLoadManagement.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setWorkLoadManagement.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setWorkLoadManagement.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setWorkLoadManagement.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setWorkLoadManagement.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setWorkLoadManagement.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveWorkLoadManagement");

        this.setDateProperty(UdrugrefId);
        return "gen";
    }

    /**
     * Saves the provider's workload management billing service type preference.
     *
     * @return String {@code "gen"} to re-display with success status
     */
    public String saveWorkLoadManagement() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.getDateProperty();
        String UdrugrefId = prop != null ? prop.getValue() : "";
        UserProperty saveProperty = this.userPropertyDAO.getProp(providerNo, "dateProperty");

        if (saveProperty == null) {
            saveProperty = new UserProperty();
            saveProperty.setName("dateProperty");
            saveProperty.setProviderNo(providerNo);
        }
        saveProperty.setValue(UdrugrefId);
        this.userPropertyDAO.saveProp(saveProperty);

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        CtlBillingServiceDao dao = SpringUtils.getBean(CtlBillingServiceDao.class);
        for (Object[] service : dao.getUniqueServiceTypes())
            serviceList.add(new LabelValueBean(String.valueOf(service[0]), String.valueOf(service[1])));

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("status", "success");
        request.setAttribute("dateProperty", prop);
        request.setAttribute("providertitle", "provider.setWorkLoadManagement.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.setWorkLoadManagement.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setWorkLoadManagement.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.setWorkLoadManagement.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.setWorkLoadManagement.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setWorkLoadManagement.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveWorkLoadManagement");
        return "gen";
    }

    /**
     * Displays the consultation request paste format preference form (single line vs multi line).
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewConsultPasteFmt() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_REQ_PASTE_FMT);

        if (prop == null) {
            prop = new UserProperty();
        }

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        serviceList.add(new LabelValueBean("Single Line", "single"));
        serviceList.add(new LabelValueBean("Multi Line", "multi"));

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("dateProperty", prop);

        request.setAttribute("providertitle", "provider.setConsulReqtPasteFmt.title");
        request.setAttribute("providermsgPrefs", "provider.setConsulReqtPasteFmt.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setConsulReqtPasteFmt.msgProvider");
        request.setAttribute("providermsgEdit", "provider.setConsulReqtPasteFmt.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setConsulReqtPasteFmt.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setConsulReqtPasteFmt.msgSuccess");
        request.setAttribute("method", "saveConsultPasteFmt");

        this.setDateProperty(prop);
        return "gen";
    }

    /**
     * Saves the provider's consultation request paste format preference.
     *
     * @return String {@code "gen"} to re-display with success status
     */
    public String saveConsultPasteFmt() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        UserProperty prop = this.getDateProperty();
        String fmt = prop != null ? prop.getValue() : "";

        UserProperty saveProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_REQ_PASTE_FMT);

        if (saveProperty == null) {
            saveProperty = new UserProperty();
            saveProperty.setProviderNo(providerNo);
            saveProperty.setName(UserProperty.CONSULTATION_REQ_PASTE_FMT);
        }

        saveProperty.setValue(fmt);
        this.userPropertyDAO.saveProp(saveProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.setConsulReqtPasteFmt.title");
        request.setAttribute("providermsgPrefs", "provider.setConsulReqtPasteFmt.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setConsulReqtPasteFmt.msgProvider");
        request.setAttribute("providermsgEdit", "provider.setConsulReqtPasteFmt.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setConsulReqtPasteFmt.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setConsulReqtPasteFmt.msgSuccess");
        request.setAttribute("method", "saveConsultPasteFmt");

        return "gen";
    }

    /**
     * Constructs a list of Canadian provinces/territories and US states for health card type dropdown.
     *
     * @return ArrayList&lt;LabelValueBean&gt; province/state options with display labels and code values
     */
    public ArrayList<LabelValueBean> constructProvinceList() {

        ArrayList<LabelValueBean> provinces = new ArrayList<LabelValueBean>();

        provinces.add(new LabelValueBean("AB-Alberta", "AB"));
        provinces.add(new LabelValueBean("BC-British Columbia", "BC"));
        provinces.add(new LabelValueBean("MB-Manitoba", "MB"));
        provinces.add(new LabelValueBean("NB-New Brunswick", "NB"));
        provinces.add(new LabelValueBean("NL-Newfoundland", "NL"));
        provinces.add(new LabelValueBean("NT-Northwest Territory", "NT"));
        provinces.add(new LabelValueBean("NS-Nova Scotia", "NS"));
        provinces.add(new LabelValueBean("NU-Nunavut", "NU"));
        provinces.add(new LabelValueBean("ON-Ontario", "ON"));
        provinces.add(new LabelValueBean("PE-Prince Edward Island", "PE"));
        provinces.add(new LabelValueBean("QC-Quebec", "QC"));
        provinces.add(new LabelValueBean("SK-Saskatchewan", "SK"));
        provinces.add(new LabelValueBean("YT-Yukon", "YK"));
        provinces.add(new LabelValueBean("US resident", "US"));
        provinces.add(new LabelValueBean("US-AK-Alaska", "US-AK"));
        provinces.add(new LabelValueBean("US-AL-Alabama", "US-AL"));
        provinces.add(new LabelValueBean("US-AR-Arkansas", "US-AR"));
        provinces.add(new LabelValueBean("US-AZ-Arizona", "US-AZ"));
        provinces.add(new LabelValueBean("US-CA-California", "US-CA"));
        provinces.add(new LabelValueBean("US-CO-Colorado", "US-CO"));
        provinces.add(new LabelValueBean("US-CT-Connecticut", "US-CT"));
        provinces.add(new LabelValueBean("US-CZ-Canal Zone", "US-CZ"));
        provinces.add(new LabelValueBean("US-DC-District of Columbia", "US-DC"));
        provinces.add(new LabelValueBean("US-DE-Delaware", "US-DE"));
        provinces.add(new LabelValueBean("US-FL-Florida", "US-FL"));
        provinces.add(new LabelValueBean("US-GA-Georgia", "US-GA"));
        provinces.add(new LabelValueBean("US-GU-Guam", "US-GU"));
        provinces.add(new LabelValueBean("US-HI-Hawaii", "US-HI"));
        provinces.add(new LabelValueBean("US-IA-Iowa", "US-IA"));
        provinces.add(new LabelValueBean("US-ID-Idaho", "US-ID"));
        provinces.add(new LabelValueBean("US-IL-Illinois", "US-IL"));
        provinces.add(new LabelValueBean("US-IN-Indiana", "US-IN"));
        provinces.add(new LabelValueBean("US-KS-Kansas", "US-KS"));
        provinces.add(new LabelValueBean("US-KY-Kentucky", "US-KY"));
        provinces.add(new LabelValueBean("US-LA-Louisiana", "US-LA"));
        provinces.add(new LabelValueBean("US-MA-Massachusetts", "US-MA"));
        provinces.add(new LabelValueBean("US-MD-Maryland", "US-MD"));
        provinces.add(new LabelValueBean("US-ME-Maine", "US-ME"));
        provinces.add(new LabelValueBean("US-MI-Michigan", "US-MI"));
        provinces.add(new LabelValueBean("US-MN-Minnesota", "US-MN"));
        provinces.add(new LabelValueBean("US-MO-Missouri", "US-MO"));
        provinces.add(new LabelValueBean("US-MS-Mississippi", "US-MS"));
        provinces.add(new LabelValueBean("US-MT-Montana", "US-MT"));
        provinces.add(new LabelValueBean("US-NC-North Carolina", "US-NC"));
        provinces.add(new LabelValueBean("US-ND-North Dakota", "US-ND"));
        provinces.add(new LabelValueBean("US-NE-Nebraska", "US-NE"));
        provinces.add(new LabelValueBean("US-NH-New Hampshire", "US-NH"));
        provinces.add(new LabelValueBean("US-NJ-New Jersey", "US-NJ"));
        provinces.add(new LabelValueBean("US-NM-New Mexico", "US-NM"));
        provinces.add(new LabelValueBean("US-NU-Nunavut", "US-NU"));
        provinces.add(new LabelValueBean("US-NV-Nevada", "US-NV"));
        provinces.add(new LabelValueBean("US-NY-New York", "US-NY"));
        provinces.add(new LabelValueBean("US-OH-Ohio", "US-OH"));
        provinces.add(new LabelValueBean("US-OK-Oklahoma", "US-OK"));
        provinces.add(new LabelValueBean("US-OR-Oregon", "US-OR"));
        provinces.add(new LabelValueBean("US-PA-Pennsylvania", "US-PA"));
        provinces.add(new LabelValueBean("US-PR-Puerto Rico", "US-PR"));
        provinces.add(new LabelValueBean("US-RI-Rhode Island", "US-RI"));
        provinces.add(new LabelValueBean("US-SC-South Carolina", "US-SC"));
        provinces.add(new LabelValueBean("US-SD-South Dakota", "US-SD"));
        provinces.add(new LabelValueBean("US-TN-Tennessee", "US-TN"));
        provinces.add(new LabelValueBean("US-TX-Texas", "US-TX"));
        provinces.add(new LabelValueBean("US-UT-Utah", "US-UT"));
        provinces.add(new LabelValueBean("US-VA-Virginia", "US-VA"));
        provinces.add(new LabelValueBean("US-VI-Virgin Islands", "US-VI"));
        provinces.add(new LabelValueBean("US-VT-Vermont", "US-VT"));
        provinces.add(new LabelValueBean("US-WA-Washington", "US-WA"));
        provinces.add(new LabelValueBean("US-WI-Wisconsin", "US-WI"));
        provinces.add(new LabelValueBean("US-WV-West Virginia", "US-WV"));
        provinces.add(new LabelValueBean("US-WY-Wyoming", "US-WY"));

        return provinces;
    }


    /**
     * Displays the favourite eForm group preference form with available groups.
     *
     * @return String {@code "gen"} to forward to the generic preference configuration JSP
     */
    public String viewFavouriteEformGroup() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.EFORM_FAVOURITE_GROUP);

        if (prop == null) {
            prop = new UserProperty();
        }

        this.setDateProperty(prop);
        ArrayList<HashMap<String, String>> groups = EFormUtil.getEFormGroups();
        ArrayList<LabelValueBean> groupList = new ArrayList<LabelValueBean>();
        String name;
        groupList.add(new LabelValueBean("None", ""));
        for (HashMap<String, String> h : groups) {
            name = h.get("groupName");
            groupList.add(new LabelValueBean(name, name));
        }

        request.setAttribute("dropOpts", groupList);

        request.setAttribute("dateProperty", prop);

        request.setAttribute("providertitle", "provider.setFavEfrmGrp.title"); //=Set Favourite Eform Group
        request.setAttribute("providermsgPrefs", "provider.setFavEfrmGrp.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setFavEfrmGrp.msgProvider"); //=Default Eform Group
        request.setAttribute("providermsgEdit", "provider.setFavEfrmGrp.msgEdit"); //=Select your favourite Eform Group
        request.setAttribute("providerbtnSubmit", "provider.setFavEfrmGrp.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setFavEfrmGrp.msgSuccess"); //=Favourite Eform Group saved
        request.setAttribute("method", "saveFavouriteEformGroup");
        return "gen";
    }

    /**
     * Saves the provider's favourite eForm group preference, or deletes it if "None" is selected.
     *
     * @return String {@code "gen"} to re-display with success status
     */
    public String saveFavouriteEformGroup() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.getDateProperty();
        String group = prop != null ? prop.getValue() : "";

        UserProperty saveProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.EFORM_FAVOURITE_GROUP);

        if (saveProperty == null) {
            saveProperty = new UserProperty();
            saveProperty.setProviderNo(providerNo);
            saveProperty.setName(UserProperty.EFORM_FAVOURITE_GROUP);
        }

        if (group.equalsIgnoreCase("")) {
            this.userPropertyDAO.delete(saveProperty);
        } else {
            saveProperty.setValue(group);
            this.userPropertyDAO.saveProp(saveProperty);
        }

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.setFavEfrmGrp.title"); //=Set Favourite Eform Group
        request.setAttribute("providermsgPrefs", "provider.setFavEfrmGrp.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.setFavEfrmGrp.msgProvider"); //=Default Eform Group
        request.setAttribute("providermsgEdit", "provider.setFavEfrmGrp.msgEdit"); //=Select your favourite Eform Group
        request.setAttribute("providerbtnSubmit", "provider.setFavEfrmGrp.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setFavEfrmGrp.msgSuccess"); //=Favourite Eform Group saved
        request.setAttribute("method", "saveFavouriteEformGroup");

        return "gen";
    }

    /**
     * Displays the CPP single-line display mode checkbox preference form.
     *
     * @return String {@code "genCppSingleLine"} to forward to the CPP single line configuration JSP
     */
    public String viewCppSingleLine() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CPP_SINGLE_LINE);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equalsIgnoreCase("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("cppSingleLineProperty", prop);
        request.setAttribute("providertitle", "provider.setCppSingleLine.title"); //=Select if you want to use Rx3
        request.setAttribute("providermsgPrefs", "provider.setCppSingleLine.msgPrefs"); //=Preferences
        request.setAttribute("providermsgProvider", "provider.setCppSingleLine.msgProfileView"); //=Use Rx3
        request.setAttribute("providermsgEdit", "provider.setCppSingleLine.msgEdit"); //=Do you want to use Rx3?
        request.setAttribute("providerbtnSubmit", "provider.setCppSingleLine.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setCppSingleLine.msgSuccess_selected"); //=CPP Single Line saved
        request.setAttribute("method", "saveUseCppSingleLine");

        this.setCppSingleLineProperty(prop);

        return "genCppSingleLine";
    }


    /**
     * Saves the provider's CPP single-line display mode preference.
     *
     * @return String {@code "genCppSingleLine"} to re-display with success status
     */
    public String saveUseCppSingleLine() {
        String checkboxValue = request.getParameter("cppSingleLineProperty.checked");

        UserProperty UUseRx3 = this.getCppSingleLineProperty();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.CPP_SINGLE_LINE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.CPP_SINGLE_LINE);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String useRx3 = "no";
        if (checked)
            useRx3 = "yes";

        prop.setValue(useRx3);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("cppSingleLineProperty", prop);
        request.setAttribute("providertitle", "provider.setCppSingleLine.title"); //=Select if you want to use Rx3
        request.setAttribute("providermsgPrefs", "provider.setCppSingleLine.msgPrefs"); //=Preferences
        request.setAttribute("providermsgProvider", "provider.setCppSingleLine.msgProfileView"); //=Use Rx3
        request.setAttribute("providermsgEdit", "provider.setCppSingleLine.msgEdit"); //=Check if you want to use Rx3
        request.setAttribute("providerbtnSubmit", "provider.setCppSingleLine.btnSubmit"); //=Save
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.setCppSingleLine.msgSuccess_selected"); //=Rx3 is selected
        else
            request.setAttribute("providermsgSuccess", "provider.setCppSingleLine.msgSuccess_unselected"); //=Rx3 is unselected
        request.setAttribute("method", "saveUseCppSingleLine");

        return "genCppSingleLine";
    }

    /**
     * Displays the eDocument browser in document report checkbox preference form.
     *
     * @return String {@code "genEDocBrowserInDocumentReport"} to forward to the configuration JSP
     */
    public String viewEDocBrowserInDocumentReport() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.EDOC_BROWSER_IN_DOCUMENT_REPORT);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equalsIgnoreCase("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("eDocBrowserInDocumentReportProperty", prop);
        request.setAttribute("providertitle", "provider.setEDocBrowserInDocumentReport.title");
        request.setAttribute("providermsgPrefs", "provider.setEDocBrowserInDocumentReport.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setEDocBrowserInDocumentReport.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setEDocBrowserInDocumentReport.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setEDocBrowserInDocumentReport.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setEDocBrowserInDocumentReport.msgSuccess_selected");
        request.setAttribute("method", "saveEDocBrowserInDocumentReport");

        this.seteDocBrowserInDocumentReportProperty(prop);

        return "genEDocBrowserInDocumentReport";
    }


    /**
     * Saves the provider's eDocument browser in document report preference.
     *
     * @return String {@code "genEDocBrowserInDocumentReport"} to re-display with success status
     */
    public String saveEDocBrowserInDocumentReport() {
        String checkboxValue = request.getParameter("eDocBrowserInDocumentReportProperty.checked");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty Uprop = this.geteDocBrowserInDocumentReportProperty();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.EDOC_BROWSER_IN_DOCUMENT_REPORT);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.EDOC_BROWSER_IN_DOCUMENT_REPORT);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String propValue = "no";
        if (checked)
            propValue = "yes";

        prop.setValue(propValue);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("eDocBrowserInDocumentReportProperty", prop);
        request.setAttribute("providertitle", "provider.setEDocBrowserInDocumentReport.title");
        request.setAttribute("providermsgPrefs", "provider.setEDocBrowserInDocumentReport.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setEDocBrowserInDocumentReport.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setEDocBrowserInDocumentReport.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setEDocBrowserInDocumentReport.btnSubmit");
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.setEDocBrowserInDocumentReport.msgSuccess_selected");
        else
            request.setAttribute("providermsgSuccess", "provider.setEDocBrowserInDocumentReport.msgSuccess_unselected");
        request.setAttribute("method", "saveEDocBrowserInDocumentReport");

        return "genEDocBrowserInDocumentReport";
    }

    /**
     * Displays the eDocument browser in master file checkbox preference form.
     *
     * @return String {@code "genEDocBrowserInMasterFile"} to forward to the configuration JSP
     */
    public String viewEDocBrowserInMasterFile() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.EDOC_BROWSER_IN_MASTER_FILE);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equalsIgnoreCase("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("eDocBrowserInMasterFileProperty", prop);
        request.setAttribute("providertitle", "provider.setEDocBrowserInMasterFile.title"); //=Select if you want to use Rx3
        request.setAttribute("providermsgPrefs", "provider.setEDocBrowserInMasterFile.msgPrefs"); //=Preferences
        request.setAttribute("providermsgProvider", "provider.setEDocBrowserInMasterFile.msgProfileView"); //=Use Rx3
        request.setAttribute("providermsgEdit", "provider.setEDocBrowserInMasterFile.msgEdit"); //=Do you want to use Rx3?
        request.setAttribute("providerbtnSubmit", "provider.setEDocBrowserInMasterFile.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.setEDocBrowserInMasterFile.msgSuccess_selected"); //=EDoc Browser saved
        request.setAttribute("method", "saveEDocBrowserInMasterFile");

        this.seteDocBrowserInMasterFileProperty(prop);

        return "genEDocBrowserInMasterFile";
    }

    /**
     * Saves the provider's eDocument browser in master file preference.
     *
     * @return String {@code "genEDocBrowserInMasterFile"} to re-display with success status
     */
    public String saveEDocBrowserInMasterFile() {
        String checkboxValue = request.getParameter("eDocBrowserInMasterFileProperty.checked");

        UserProperty Uprop = this.geteDocBrowserInMasterFileProperty();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.EDOC_BROWSER_IN_MASTER_FILE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.EDOC_BROWSER_IN_MASTER_FILE);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String propValue = "no";
        if (checked)
            propValue = "yes";

        prop.setValue(propValue);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("eDocBrowserInMasterFileProperty", prop);
        request.setAttribute("providertitle", "provider.setEDocBrowserInMasterFile.title");
        request.setAttribute("providermsgPrefs", "provider.setEDocBrowserInMasterFile.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setEDocBrowserInMasterFile.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setEDocBrowserInMasterFile.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setEDocBrowserInMasterFile.btnSubmit");
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.setEDocBrowserInMasterFile.msgSuccess_selected");
        else
            request.setAttribute("providermsgSuccess", "provider.setEDocBrowserInMasterFile.msgSuccess_unselected");
        request.setAttribute("method", "saveEDocBrowserInMasterFile");

        return "genEDocBrowserInMasterFile";
    }

    /**
     * Displays the lab acknowledgment comment checkbox preference form.
     *
     * @return String {@code "genLabComment"} to forward to the lab comment configuration JSP
     */
    public String viewCommentLab() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_ACK_COMMENT);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equalsIgnoreCase("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("labAckComment", prop);
        request.setAttribute("providertitle", "provider.setAckComment.title");
        request.setAttribute("providermsgPrefs", "provider.setAckComment.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setAckComment.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setAckComment.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setAckComment.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setAckComment.msgSuccess_selected");
        request.setAttribute("method", "saveCommentLab");

        this.setLabAckCommentProperty(prop);

        return "genAckCommentLab";
    }

    /**
     * Saves the provider's lab acknowledgment comment preference.
     *
     * @return String {@code "genAckCommentLab"} to re-display with success status
     */
    public String saveCommentLab() {
        String checkboxValue = request.getParameter("labAckCommentProperty.checked");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty Uprop = this.getLabAckCommentProperty();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_ACK_COMMENT);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.LAB_ACK_COMMENT);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String disableComment = "no";
        if (checked)
            disableComment = "yes";
        
        prop.setValue(disableComment);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("labAckComment", prop);
        request.setAttribute("providertitle", "provider.setAckComment.title");
        request.setAttribute("providermsgPrefs", "provider.setAckComment.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setAckComment.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setAckComment.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setAckComment.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setAckComment.msgSuccess_selected");
        request.setAttribute("method", "saveCommentLab");

        if (checked)
            request.setAttribute("providermsgSuccess", "provider.setAckComment.msgSuccess_selected");
        else
            request.setAttribute("providermsgSuccess", "provider.setAckComment.msgSuccess_unselected");

        return "genAckCommentLab";
    }

    @SuppressWarnings("unchecked")
    /**
     * Displays the lab recall and prevention warning preferences form.
     *
     * @return String {@code "genPreventionPrefs"} to forward to the prevention preferences JSP
     */
    public String viewLabRecall() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty delegate = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_DELEGATE);
        UserProperty subject = this.userPropertyDAO.getProp(providerNo, "labRecallMsgSubject");
        UserProperty ticklerAssignee = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_TICKLER_ASSIGNEE);
        UserProperty priority = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_TICKLER_PRIORITY);

        if (delegate == null) {
            delegate = new UserProperty();
        }

        if (subject == null) {
            subject = new UserProperty();
        }

        String defaultToDelegate = "";
        if (ticklerAssignee == null) {
            ticklerAssignee = new UserProperty();
        } else {
            defaultToDelegate = ticklerAssignee.getValue();
        }

        if (priority == null) {
            priority = new UserProperty();
        }

        ticklerAssignee.setChecked("yes".equalsIgnoreCase(ticklerAssignee.getValue()));

        ArrayList<LabelValueBean> providerList = new ArrayList<LabelValueBean>();
        providerList.add(new LabelValueBean("Select", "")); //key, value

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> ps = dao.getProviders();
        Collections.sort(ps, Comparator.comparing(Provider::getLastName));
        try {
            for (Provider p : ps) {
                if (!p.getProviderNo().equals("-1")) {
                    providerList.add(new LabelValueBean(p.getLastName() + ", " + p.getFirstName(), p.getProviderNo()));
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        request.setAttribute("providerSelect", providerList);

        ArrayList<LabelValueBean> priorityList = new ArrayList<LabelValueBean>();
        priorityList.add(new LabelValueBean("Select", "")); //key, value
        priorityList.add(new LabelValueBean("High", "High"));
        priorityList.add(new LabelValueBean("Normal", "Normal"));
        priorityList.add(new LabelValueBean("Low", "Low"));

        request.setAttribute("prioritySelect", priorityList);

        request.setAttribute("labRecallDelegate", delegate);
        request.setAttribute("subject", subject);
        request.setAttribute("labRecallTicklerAssignee", ticklerAssignee);
        request.setAttribute("labRecallTicklerPriority", priority);

        request.setAttribute("providertitle", "provider.setLabRecall.title");
        request.setAttribute("providermsgPrefs", "provider.setLabRecall.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setLabRecall.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setLabRecall.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setLabRecall.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.setLabRecall.msgSuccess");
        request.setAttribute("method", "saveLabRecallPrefs");

        this.setLabRecallDelegate(delegate);
        this.setLabRecallMsgSubject(subject);
        this.setLabRecallTicklerAssignee(ticklerAssignee);
        this.setLabRecallTicklerPriority(priority);

        return "genLabRecallPrefs";
    }

    /**
     * Saves the provider's lab recall and prevention warning preferences.
     *
     * @return String {@code "genPreventionPrefs"} to re-display with success status
     */
    public String saveLabRecallPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty s = this.getLabRecallMsgSubject();
        String subject = s != null ? s.getValue() : "";

        String delegate = request.getParameter("labRecallDelegate.value"); 
        String priority = request.getParameter("labRecallTicklerPriority.value");

        boolean assignee = request.getParameter("labRecallTicklerAssignee.checked") != null;

        boolean delete = false;
        if (delegate.equals("")) {
            delete = true;
        }

        // Save delegate (dropdown)
        UserProperty dProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_DELEGATE);
        if (dProperty == null) {
            dProperty = new UserProperty();
            dProperty.setProviderNo(providerNo);
            dProperty.setName(UserProperty.LAB_RECALL_DELEGATE);
        }
        if (delete) {
            userPropertyDAO.delete(dProperty);
        } else {
            dProperty.setValue(delegate);
            userPropertyDAO.saveProp(dProperty);
        }

        // Save subject text field
        UserProperty sProperty = this.userPropertyDAO.getProp(providerNo, "labRecallMsgSubject");
        if (sProperty == null) {
            sProperty = new UserProperty();
            sProperty.setProviderNo(providerNo);
            sProperty.setName("labRecallMsgSubject");
        }
        if (delete) {
            userPropertyDAO.delete(sProperty);
        } else {
            sProperty.setValue(subject);
            userPropertyDAO.saveProp(sProperty);
        }

        // Save tickler assignee checkbox
        String defaultToDelegate = "no";
        UserProperty aProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_TICKLER_ASSIGNEE);
        if (aProperty == null) {
            aProperty = new UserProperty();
            aProperty.setProviderNo(providerNo);
            aProperty.setName(UserProperty.LAB_RECALL_TICKLER_ASSIGNEE);
        }
        if (delete) {
            userPropertyDAO.delete(aProperty);
        } else {
            aProperty.setValue(assignee ? "yes" : "no");
            userPropertyDAO.saveProp(aProperty);
        }

        // Save tickler priority (dropdown)
        UserProperty pProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_TICKLER_PRIORITY);
        if (pProperty == null) {
            pProperty = new UserProperty();
            pProperty.setProviderNo(providerNo);
            pProperty.setName(UserProperty.LAB_RECALL_TICKLER_PRIORITY);
        }
        if (delete) {
            userPropertyDAO.delete(pProperty);
        } else {
            pProperty.setValue(priority);
            userPropertyDAO.saveProp(pProperty);
        }
        
        aProperty.setChecked(assignee);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.setLabRecall.title");
        request.setAttribute("providermsgPrefs", "provider.setLabRecall.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.setLabRecall.msgProfileView");
        request.setAttribute("providermsgEdit", "provider.setLabRecall.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.setLabRecall.btnSubmit");

        String msgSuccess = "provider.setLabRecall.msgSuccess";
        if (delete) {
            msgSuccess = "provider.setLabRecall.msgDeleted";
        }
        request.setAttribute("providermsgSuccess", msgSuccess);
        request.setAttribute("method", "saveLabRecallPrefs");

        return "genLabRecallPrefs";
    }

    /**
     * Displays the tickler task assignee default preference form with provider list.
     *
     * @return String {@code "genTicklerTaskAssignee"} to forward to the tickler configuration JSP
     */
    public String viewTicklerTaskAssignee() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty ticklerTaskAssignee = this.userPropertyDAO.getProp(providerNo, UserProperty.TICKLER_TASK_ASSIGNEE);

        String defaultTo = "";
        if (ticklerTaskAssignee == null) {
            ticklerTaskAssignee = new UserProperty();
            ticklerTaskAssignee.setValue("default");
            defaultTo = "default";
        } else {
            String value = ticklerTaskAssignee.getValue();
            if ("default".equals(value) || "mrp".equals(value)) {
                defaultTo = value;
            } else {
                defaultTo = "providers";

                if ("providers".equals(defaultTo)) {
                    request.setAttribute("selectedProvider", ticklerTaskAssignee.getValue());
                }
            }
        }

        ArrayList<LabelValueBean> providerList = new ArrayList<LabelValueBean>();
        providerList.add(new LabelValueBean("Select", ""));

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> ps = dao.getProviders();
        Collections.sort(ps, Comparator.comparing(Provider::getLastName));
        try {
            for (Provider p : ps) {
                if (!p.getProviderNo().equals("-1")) {
                    providerList.add(new LabelValueBean(p.getLastName() + ", " + p.getFirstName(), p.getProviderNo()));
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        request.setAttribute("providerSelect", providerList);
        request.setAttribute("providertitle", "provider.ticklerPreference.title");
        request.setAttribute("providermsgPrefs", "provider.ticklerPreference.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnSubmit", "provider.ticklerPreference.btnSubmit"); //=Save
        request.setAttribute("providerbtnCancel", "provider.ticklerPreference.btnCancel"); //=Cancel
        request.setAttribute("method", "saveTicklerTaskAssignee");
        request.setAttribute("taskAssigneeSelection", ticklerTaskAssignee);
        this.setTaskAssigneeSelection(ticklerTaskAssignee);
        this.setTaskAssigneeMRP(ticklerTaskAssignee);

        request.setAttribute("providerMsg", "");
        request.setAttribute("taskAssigneeMRPValue", defaultTo);

        UserProperty t = this.getTaskAssigneeMRP();
        t.setValue(defaultTo);

        return SUCCESS;
    }

    /**
     * Saves the provider's default tickler task assignee preference.
     *
     * @return String {@code "genTicklerTaskAssignee"} to re-display with success status
     */
    public String saveTicklerTaskAssignee() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String radioValue = request.getParameter("taskAssigneeMRP.value");
        String providerValue = request.getParameter("taskAssigneeSelection.value");

        UserProperty a = this.getTaskAssigneeSelection();

        if ("provider".equals(radioValue)) {
            a.setValue(providerValue); // providerNo
        } else {
            a.setValue(radioValue); // default or mrp
        }

        String tickerTaskAssignee = a != null ? a.getValue() : "";

        boolean delete = "".equals(tickerTaskAssignee);

        UserProperty property = this.userPropertyDAO.getProp(providerNo, UserProperty.TICKLER_TASK_ASSIGNEE);
        if (property == null) {
            property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(UserProperty.TICKLER_TASK_ASSIGNEE);
        }

        try {
            if (delete) {
                if (property.getId() != null) {
                    userPropertyDAO.delete(property);
                }
            } else {
                property.setValue(tickerTaskAssignee);
                userPropertyDAO.saveProp(property);
            }
        } catch (Exception e) {
            // Return to the success page even though the pereference is not changed from default
            // Avoid the error displays
            request.setAttribute("status", "success");
            return "complete";
        }

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.ticklerPreference.title");
        request.setAttribute("providermsgPrefs", "provider.ticklerPreference.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnSubmit", "provider.ticklerPreference.btnSubmit"); //=Save
        request.setAttribute("providerbtnCancel", "provider.ticklerPreference.btnCancel"); //=Cancel
        request.setAttribute("providerbtnClose", "provider.ticklerPreference.providerbtnClose"); //=Close Window
        request.setAttribute("providerMsg", "provider.ticklerPreference.savedMsg");
        request.setAttribute("method", "saveTicklerTaskAssignee");

        return "complete";
    }

    /**
     * Displays the encounter window size preference form (width and height).
     *
     * @return String {@code "genEncounterWindowSize"} to forward to the window size configuration JSP
     */
    public String viewEncounterWindowSize() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty width = this.userPropertyDAO.getProp(providerNo, "encounterWindowWidth");
        UserProperty height = this.userPropertyDAO.getProp(providerNo, "encounterWindowHeight");
        UserProperty maximize = this.userPropertyDAO.getProp(providerNo, "encounterWindowMaximize");

        if (width == null) {
            width = new UserProperty();
        }
        if (height == null) {
            height = new UserProperty();
        }
        if (maximize == null) {
            maximize = new UserProperty();
        }
        if (maximize.getValue() != null) {
            maximize.setChecked(maximize.getValue().equals("yes") ? true : false);
        }

        request.setAttribute("width", width);
        request.setAttribute("height", height);
        request.setAttribute("maximize", maximize);


        request.setAttribute("providertitle", "provider.encounterWindowSize.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.encounterWindowSize.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.encounterWindowSize.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.encounterWindowSize.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.encounterWindowSize.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.encounterWindowSize.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveEncounterWindowSize");

        this.setEncounterWindowWidth(width);
        this.setEncounterWindowHeight(height);
        this.setEncounterWindowMaximize(maximize);

        return "genEncounterWindowSize";
    }

    /**
     * Saves the provider's encounter window size preferences (width, height, and maximize flag).
     *
     * @return String {@code "genEncounterWindowSize"} to re-display with success status
     */
    public String saveEncounterWindowSize() {
        String checkboxValue = request.getParameter("encounterWindowMaximize.checked");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty w = this.getEncounterWindowWidth();
        UserProperty h = this.getEncounterWindowHeight();
        UserProperty m = this.getEncounterWindowMaximize();

        String width = w != null ? w.getValue() : "";
        String height = h != null ? h.getValue() : "";

        UserProperty wProperty = this.userPropertyDAO.getProp(providerNo, "encounterWindowWidth");
        if (wProperty == null) {
            wProperty = new UserProperty();
            wProperty.setProviderNo(providerNo);
            wProperty.setName("encounterWindowWidth");
        }
        wProperty.setValue(width);
        userPropertyDAO.saveProp(wProperty);

        UserProperty hProperty = this.userPropertyDAO.getProp(providerNo, "encounterWindowHeight");
        if (hProperty == null) {
            hProperty = new UserProperty();
            hProperty.setProviderNo(providerNo);
            hProperty.setName("encounterWindowHeight");
        }
        hProperty.setValue(height);
        userPropertyDAO.saveProp(hProperty);

        UserProperty mProperty = this.userPropertyDAO.getProp(providerNo, "encounterWindowMaximize");
        if (mProperty == null) {
            mProperty = new UserProperty();
            mProperty.setProviderNo(providerNo);
            mProperty.setName("encounterWindowMaximize");
        }
        boolean checked = checkboxValue != null;
        String maximizeSetting = "no";
        if (checked)
            maximizeSetting = "yes";
        mProperty.setValue(maximizeSetting);
        userPropertyDAO.saveProp(mProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.encounterWindowSize.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.encounterWindowSize.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.encounterWindowSize.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.encounterWindowSize.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.encounterWindowSize.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.encounterWindowSize.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveEncounterWindowSize");

        return "genEncounterWindowSize";
    }

    /**
     * Displays the quick chart size preference form.
     *
     * @return String {@code "genQuickChartSize"} to forward to the configuration JSP
     */
    public String viewQuickChartSize() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty size = this.userPropertyDAO.getProp(providerNo, "quickChartSize");

        if (size == null) {
            size = new UserProperty();
        }

        request.setAttribute("size", size);

        request.setAttribute("providertitle", "provider.quickChartSize.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.quickChartSize.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.quickChartSize.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.quickChartSize.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.quickChartSize.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.quickChartSize.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveQuickChartSize");

        this.setQuickChartSize(size);

        return "genQuickChartSize";
    }

    /**
     * Saves the provider's quick chart size preference.
     *
     * @return String {@code "genQuickChartSize"} to re-display with success status
     */
    public String saveQuickChartSize() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty s = this.getQuickChartSize();

        String size = s != null ? s.getValue() : "";

        UserProperty wProperty = this.userPropertyDAO.getProp(providerNo, "quickChartSize");
        if (wProperty == null) {
            wProperty = new UserProperty();
            wProperty.setProviderNo(providerNo);
            wProperty.setName("quickChartSize");
        }
        wProperty.setValue(size);
        userPropertyDAO.saveProp(wProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.quickChartSize.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.quickChartSize.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.quickChartSize.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.quickChartSize.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.quickChartSize.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.quickChartSize.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveQuickChartSize");

        return "genQuickChartSize";
    }

    /**
     * Displays the patient name display length preference form.
     *
     * @return String {@code "genPatientNameLength"} to forward to the configuration JSP
     */
    public String viewPatientNameLength() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty length = this.userPropertyDAO.getProp(providerNo, "patientNameLength");

        if (length == null) {
            length = new UserProperty();
        }


        request.setAttribute("length", length);


        request.setAttribute("providertitle", "provider.patientNameLength.title");
        request.setAttribute("providermsgPrefs", "provider.patientNameLength.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.patientNameLength.msgProvider");
        request.setAttribute("providermsgEdit", "provider.patientNameLength.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.patientNameLength.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.patientNameLength.msgSuccess");
        request.setAttribute("method", "savePatientNameLength");

        this.setPatientNameLength(length);

        return "genPatientNameLength";
    }

    /**
     * Saves the provider's patient name display length preference.
     *
     * @return String {@code "genPatientNameLength"} to re-display with success status
     */
    public String savePatientNameLength() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty s = this.getPatientNameLength();

        String length = s != null ? s.getValue() : "";

        UserProperty wProperty = this.userPropertyDAO.getProp(providerNo, "patientNameLength");
        if (wProperty == null) {
            wProperty = new UserProperty();
            wProperty.setProviderNo(providerNo);
            wProperty.setName("patientNameLength");
        }
        wProperty.setValue(length);
        userPropertyDAO.saveProp(wProperty);


        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.patientNameLength.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.patientNameLength.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.patientNameLength.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.patientNameLength.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.patientNameLength.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.patientNameLength.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "savePatientNameLength");

        return "genPatientNameLength";
    }

    /**
     * Displays the document display mode preference form.
     *
     * @return String {@code "genDisplayDocumentAs"} to forward to the configuration JSP
     */
    public String viewDisplayDocumentAs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty fmt = this.userPropertyDAO.getProp(providerNo, UserProperty.DISPLAY_DOCUMENT_AS);

        if (fmt == null) {
            fmt = new UserProperty();
        }

        ArrayList<LabelValueBean> serviceList = new ArrayList<LabelValueBean>();
        serviceList.add(new LabelValueBean(UserProperty.PDF, UserProperty.PDF));
        serviceList.add(new LabelValueBean(UserProperty.IMAGE, UserProperty.IMAGE));

        request.setAttribute("dropOpts", serviceList);

        request.setAttribute("fmt", fmt);

        request.setAttribute("providertitle", "provider.displayDocumentAs.title");
        request.setAttribute("providermsgPrefs", "provider.displayDocumentAs.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.displayDocumentAs.msgProvider");
        request.setAttribute("providermsgEdit", "provider.displayDocumentAs.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.displayDocumentAs.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.displayDocumentAs.msgSuccess");
        request.setAttribute("method", "saveDisplayDocumentAs");

        this.setDisplayDocumentAsProperty(fmt);
        return "genDisplayDocumentAs";
    }

    /**
     * Saves the provider's document display mode preference.
     *
     * @return String {@code "genDisplayDocumentAs"} to re-display with success status
     */
    public String saveDisplayDocumentAs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = this.getDisplayDocumentAsProperty();
        String fmt = prop != null ? prop.getValue() : "";
        UserProperty saveProperty = this.userPropertyDAO.getProp(providerNo, UserProperty.DISPLAY_DOCUMENT_AS);

        if (saveProperty == null) {
            saveProperty = new UserProperty();
            saveProperty.setProviderNo(providerNo);
            saveProperty.setName(UserProperty.DISPLAY_DOCUMENT_AS);
        }

        saveProperty.setValue(fmt);
        this.userPropertyDAO.saveProp(saveProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.displayDocumentAs.title");
        request.setAttribute("providermsgPrefs", "provider.displayDocumentAs.msgPrefs");
        request.setAttribute("providermsgProvider", "provider.displayDocumentAs.msgProvider");
        request.setAttribute("providermsgEdit", "provider.displayDocumentAs.msgEdit");
        request.setAttribute("providerbtnSubmit", "provider.displayDocumentAs.btnSubmit");
        request.setAttribute("providermsgSuccess", "provider.displayDocumentAs.msgSuccess");
        request.setAttribute("method", "saveDisplayDocumentAs");

        return "genDisplayDocumentAs";
    }



    /**
     * Displays the preference form for hiding the legacy eChart link in the appointment view.
     *
     * @return String {@code "genHideOldEchartLinkInAppt"} to forward to the configuration JSP
     */
    public String viewHideOldEchartLinkInAppt() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equals("Y"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("hideOldEchartLinkInApptProperty", prop);
        request.setAttribute("providertitle", "provider.hideOldEchartLinkInAppt.title"); //=Hide Old Echart Link in Appointment
        request.setAttribute("providermsgPrefs", "provider.hideOldEchartLinkInAppt.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnSubmit", "provider.hideOldEchartLinkInAppt.btnSubmit"); //=Save
        request.setAttribute("providerbtnCancel", "provider.hideOldEchartLinkInAppt.btnCancel"); //=Cancel
        request.setAttribute("method", "saveHideOldEchartLinkInAppt");

        this.setHideOldEchartLinkInApptProperty(prop);

        return "genHideOldEchartLinkInAppt";
    }


    /**
     * Saves the provider's preference for hiding the legacy eChart link in appointments.
     *
     * @return String {@code "genHideOldEchartLinkInAppt"} to re-display with success status
     */
    public String saveHideOldEchartLinkInAppt() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty Uprop = this.getHideOldEchartLinkInApptProperty();

        boolean checked = false;
        if (Uprop != null)
            checked = Uprop.isChecked();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT);
            prop.setProviderNo(providerNo);
        }
        String propValue = "N";
        if (checked) propValue = "Y";

        prop.setValue(propValue);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("hideOldEchartLinkInApptProperty", prop);
        request.setAttribute("providertitle", "provider.hideOldEchartLinkInAppt.title"); //=Hide Old Echart Link in Appointment
        request.setAttribute("providermsgPrefs", "provider.hideOldEchartLinkInAppt.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnClose", "provider.hideOldEchartLinkInAppt.btnClose"); //=Close
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.hideOldEchartLinkInAppt.msgSuccess_selected"); //=Old Echart Link Hidden in Appointment
        else
            request.setAttribute("providermsgSuccess", "provider.hideOldEchartLinkInAppt.msgSuccess_unselected"); //Old Echart Link Shown in Appointment
        request.setAttribute("method", "saveHideOldEchartLinkInAppt");

        return "genHideOldEchartLinkInAppt";
    }


    /**
     * Displays the dashboard sharing preference checkbox form.
     *
     * @return String {@code "genDashboardPrefs"} to forward to the configuration JSP
     */
    public String viewDashboardPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.DASHBOARD_SHARE);

        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equals("yes"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);
        request.setAttribute("dashboardShareProperty", prop);
        request.setAttribute("providertitle", "provider.dashboardPrefs.title");
        request.setAttribute("providermsgPrefs", "provider.dashboardPrefs.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnSubmit", "provider.dashboardPrefs.btnSubmit"); //=Save
        request.setAttribute("providerbtnCancel", "provider.dashboardPrefs.btnCancel"); //=Cancel
        request.setAttribute("method", "saveDashboardPrefs");

        this.setDashboardShareProperty(prop);

        return "genDashboardPrefs";
    }

    /**
     * Saves the provider's dashboard sharing preference.
     *
     * @return String {@code "genDashboardPrefs"} to re-display with success status
     */
    public String saveDashboardPrefs() {
        String checkboxValue = request.getParameter("dashboardShareProperty.checked");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty Uprop = this.getDashboardShareProperty();

        UserProperty prop = this.userPropertyDAO.getProp(providerNo, UserProperty.DASHBOARD_SHARE);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(UserProperty.DASHBOARD_SHARE);
            prop.setProviderNo(providerNo);
        }
        boolean checked = checkboxValue != null;
        String propValue = "no";
        if (checked) propValue = "yes";

        prop.setValue(propValue);
        this.userPropertyDAO.saveProp(prop);

        request.setAttribute("status", "success");
        request.setAttribute("dashboardShareProperty", prop);
        request.setAttribute("providertitle", "provider.dashboardPrefs.title");
        request.setAttribute("providermsgPrefs", "provider.dashboardPrefs.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnClose", "provider.dashboardPrefs.btnClose"); //=Close
        if (checked)
            request.setAttribute("providermsgSuccess", "provider.dashboardPrefs.msgSuccess_selected");
        else
            request.setAttribute("providermsgSuccess", "provider.dashboardPrefs.msgSuccess_unselected");
        request.setAttribute("method", "saveDashboardPrefs");

        return "genDashboardPrefs";
    }

    /**
     * Displays the appointment card customization preference form (name, phone, fax).
     *
     * @return String {@code "genAppointmentCardPrefs"} to forward to the configuration JSP
     */
    public String viewAppointmentCardPrefs() {
        String provider = (String) request.getSession().getAttribute("user");

        UserProperty name = this.userPropertyDAO.getProp(provider, "appointmentCardName");
        UserProperty phone = this.userPropertyDAO.getProp(provider, "appointmentCardPhone");
        UserProperty fax = this.userPropertyDAO.getProp(provider, "appointmentCardFax");

        if (name == null) {
            name = new UserProperty();
        }
        if (phone == null) {
            phone = new UserProperty();
        }
        if (fax == null) {
            fax = new UserProperty();
        }

        request.setAttribute("name", name);
        request.setAttribute("phone", phone);
        request.setAttribute("fax", fax);

        request.setAttribute("providertitle", "provider.appointmentCardPrefs.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.appointmentCardPrefs.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.appointmentCardPrefs.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.appointmentCardPrefs.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.appointmentCardPrefs.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.appointmentCardPrefs.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveAppointmentCardPrefs");

        this.setAppointmentCardName(name);
        this.setAppointmentCardPhone(phone);
        this.setAppointmentCardFax(fax);

        return "genAppointmentCardPrefs";
    }

    public String saveAppointmentCardPrefs() {
        UserProperty n = this.getAppointmentCardName();
        UserProperty p = this.getAppointmentCardPhone();
        UserProperty f = this.getAppointmentCardFax();

        String name = n != null ? n.getValue() : "";
        String phone = p != null ? p.getValue() : "";
        String fax = f != null ? f.getValue() : "";

        String provider = (String) request.getSession().getAttribute("user");

        UserProperty wProperty = this.userPropertyDAO.getProp(provider, "appointmentCardName");
        if (wProperty == null) {
            wProperty = new UserProperty();
            wProperty.setProviderNo(provider);
            wProperty.setName("appointmentCardName");
        }
        wProperty.setValue(name);

        userPropertyDAO.saveProp(wProperty);

        UserProperty hProperty = this.userPropertyDAO.getProp(provider, "appointmentCardPhone");
        if (hProperty == null) {
            hProperty = new UserProperty();
            hProperty.setProviderNo(provider);
            hProperty.setName("appointmentCardPhone");
        }
        hProperty.setValue(phone);
        userPropertyDAO.saveProp(hProperty);

        UserProperty mProperty = this.userPropertyDAO.getProp(provider, "appointmentCardFax");
        if (mProperty == null) {
            mProperty = new UserProperty();
            mProperty.setProviderNo(provider);
            mProperty.setName("appointmentCardFax");
        }
        mProperty.setValue(fax);
        userPropertyDAO.saveProp(mProperty);

        request.setAttribute("status", "success");
        request.setAttribute("providertitle", "provider.appointmentCardPrefs.title"); //=Set Configuration
        request.setAttribute("providermsgPrefs", "provider.appointmentCardPrefs.msgPrefs"); //=Preferences"); //
        request.setAttribute("providermsgProvider", "provider.appointmentCardPrefs.msgProvider"); //=Configuration ID
        request.setAttribute("providermsgEdit", "provider.appointmentCardPrefs.msgEdit"); //=Enter configuration value
        request.setAttribute("providerbtnSubmit", "provider.appointmentCardPrefs.btnSubmit"); //=Save
        request.setAttribute("providermsgSuccess", "provider.appointmentCardPrefs.msgSuccess"); //=Configuration saved
        request.setAttribute("method", "saveAppointmentCardPrefs");

        return "genAppointmentCardPrefs";
    }




    public String viewPreventionPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        UserProperty prop = loadProperty(providerNo, UserProperty.PREVENTION_SSO_WARNING);
        UserProperty prop2 = loadProperty(providerNo, UserProperty.PREVENTION_ISPA_WARNING);
        UserProperty prop3 = loadProperty(providerNo, UserProperty.PREVENTION_NON_ISPA_WARNING);

        request.setAttribute("preventionSSOWarningProperty", prop);
        request.setAttribute("preventionISPAWarningProperty", prop2);
        request.setAttribute("preventionNonISPAWarningProperty", prop3);

        request.setAttribute("providertitle", "provider.preventionPrefs.title");
        request.setAttribute("providermsgPrefs", "provider.preventionPrefs.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnSubmit", "provider.preventionPrefs.btnSubmit"); //=Save
        request.setAttribute("providerbtnCancel", "provider.preventionPrefs.btnCancel"); //=Cancel
        request.setAttribute("method", "savePreventionPrefs");

        this.setPreventionSSOWarningProperty(prop);
        this.setPreventionISPAWarningProperty(prop2);
        this.setPreventionNonISPAWarningProperty(prop3);

        return "genPreventionPrefs";
    }


    private UserProperty saveProperty(String providerNo, UserProperty Uprop, String name) {
        boolean checked = false;
        if (Uprop != null)
            checked = Uprop.isChecked();
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, name);
        if (prop == null) {
            prop = new UserProperty();
            prop.setName(name);
            prop.setProviderNo(providerNo);
        }
        String propValue = "false";
        if (checked) propValue = "true";

        prop.setValue(propValue);
        this.userPropertyDAO.saveProp(prop);

        return prop;

    }

    private UserProperty loadProperty(String providerNo, String name) {
        UserProperty prop = this.userPropertyDAO.getProp(providerNo, name);
        String propValue = "";
        if (prop == null) {
            prop = new UserProperty();
        } else {
            propValue = prop.getValue();
        }

        boolean checked;
        if (propValue.equals("true"))
            checked = true;
        else
            checked = false;

        prop.setChecked(checked);

        return prop;
    }

    public String savePreventionPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String checkboxValue1 = request.getParameter("preventionSSOWarningProperty.checked");
        String checkboxValue2 = request.getParameter("preventionISPAWarningProperty.checked");
        String checkboxValue3 = request.getParameter("preventionNonISPAWarningProperty.checked");

        if (preventionSSOWarningProperty == null) {
            preventionSSOWarningProperty = new UserProperty();
        }
        if (preventionISPAWarningProperty == null) {
            preventionISPAWarningProperty = new UserProperty();
        }
        if (preventionNonISPAWarningProperty == null) {
            preventionNonISPAWarningProperty = new UserProperty();
        }

        preventionSSOWarningProperty.setChecked(checkboxValue1 != null);
        preventionISPAWarningProperty.setChecked(checkboxValue2 != null);
        preventionNonISPAWarningProperty.setChecked(checkboxValue3 != null);

        saveProperty(providerNo, getPreventionSSOWarningProperty(), UserProperty.PREVENTION_SSO_WARNING);
        saveProperty(providerNo, getPreventionISPAWarningProperty(), UserProperty.PREVENTION_ISPA_WARNING);
        saveProperty(providerNo, getPreventionNonISPAWarningProperty(), UserProperty.PREVENTION_NON_ISPA_WARNING);

        request.setAttribute("status", "success");

        request.setAttribute("providertitle", "provider.preventionPrefs.title");
        request.setAttribute("providermsgPrefs", "provider.preventionPrefs.msgPrefs"); //=Preferences
        request.setAttribute("providerbtnClose", "provider.preventionPrefs.btnClose"); //=Close

        request.setAttribute("method", "savePreventionPrefs");

        return "genPreventionPrefs";
    }

    /**
     * viewLabMacroPrefs()
     *
     * Purpose:
     *     Display the lab macro preferences form for the logged-in provider.
     *     This method loads and displays the setLabMacroPrefs.jsp page where
     *     providers can view, create, edit, or delete lab result macro templates.
     *
     * Security:
     *     - Requires authenticated provider session (injected via LoggedInInfo)
     *     - Checks _lab READ privilege via SecurityInfoManager
     *     - Throws RuntimeException if privilege is missing
     *
     * Flow:
     *     1. Extract logged-in provider info from HTTP session
     *     2. Verify provider has READ access to "_lab" security object
     *     3. Forward to setLabMacroPrefs.jsp (JSP loads macro data from database)
     *
     * @return "genLabMacroPrefs" - Struts2 result name mapping to setLabMacroPrefs.jsp
     * @throws SecurityException if no valid session found
     * @throws RuntimeException if provider lacks _lab READ privilege
     * @since 2024-12-06 (Security check added 2026-02-17)
     */
    public String viewLabMacroPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("No valid session found");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required security object _lab");
        }

        return "genLabMacroPrefs";
    }

    /**
     * saveLabMacroPrefs()
     *
     * Purpose:
     *     Persists lab macro preferences for the logged-in provider.
     *     Receives macro definitions from the form submission (either assembled
     *     from form fields or raw JSON from advanced editor), validates JSON
     *     structure, and stores in the UserProperty table.
     *
     * Security:
     *     - Requires authenticated provider session (injected via LoggedInInfo)
     *     - Checks _lab WRITE privilege via SecurityInfoManager
     *     - Throws RuntimeException if privilege is missing
     *     - Server-side JSON validation prevents malformed data persistence
     *     - Audit log records save operation (without sensitive data content)
     *
     * Input Processing:
     *     - Retrieves macro JSON via Struts2 OGNL property injection (getLabMacroJSON())
     *     - Defaults to empty string if no macros provided
     *     - Allows empty string (user can delete all macros)
     *
     * Validation:
     *     - Uses Jackson ObjectMapper.readTree() to parse JSON
     *     - If non-empty JSON fails parsing, returns "error" status
     *     - Empty strings bypass validation (valid state = no macros)
     *
     * Storage:
     *     - Creates or updates UserProperty record with name="labMacroJSON"
     *     - Provider number is extracted from logged-in session
     *     - UserProperty is persisted to database via userPropertyDAO
     *
     * Audit Logging:
     *     - Records action to audit log as "LabMacroPreferences"
     *     - Logs "updated" event (not full JSON content, for privacy)
     *     - Audit visible to administrators for compliance/forensics
     *
     * Flow:
     *     1. Extract logged-in provider and verify WRITE privilege
     *     2. Get macro JSON from form submission
     *     3. Validate JSON structure (if non-empty)
     *     4. On validation error: set status="error" and return to form
     *     5. On success: persist to database, set status="success"
     *     6. Return to setLabMacroPrefs.jsp with status message
     *
     * @return "genLabMacroPrefs" - Struts2 result name mapping to setLabMacroPrefs.jsp
     * @throws SecurityException if no valid session found
     * @throws RuntimeException if provider lacks _lab WRITE privilege
     * @since 2024-12-06 (JSON validation added 2026-02-17)
     */
    public String saveLabMacroPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("No valid session found");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required security object _lab");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Retrieve macro JSON via Struts2 OGNL property injection.
        // The form textarea name "labMacroJSON.value" maps to setLabMacroJSON().setValue() on this action.
        UserProperty s = this.getLabMacroJSON();

        // Extract JSON string, or empty string if no macros submitted.
        String prefs = s != null ? s.getValue() : "";

        // ================================================================
        // SERVER-SIDE JSON VALIDATION
        // ================================================================
        // If user submitted non-empty JSON (from either form assembly or
        // raw editor), validate that it parses correctly as JSON. This
        // prevents corrupted data from being stored.
        // Empty string is always valid (user can delete all macros).
        if (!prefs.isEmpty()) {
            try {
                // Attempt to parse JSON. If successful, structure is valid.
                // If parsing fails, the exception is caught below.
                new ObjectMapper().readTree(prefs);
            } catch (IOException e) {
                // JSON parsing failed. Log warning (admin visibility) but
                // do not persist. Show user-friendly error message on JSP.
                MiscUtils.getLogger().warn("User submitted invalid JSON for lab macros", e);
                request.setAttribute("status", "error");
                // Preserve submitted JSON so user can correct it in the raw editor
                // without losing their input on validation error.
                request.setAttribute("submittedJSON", prefs);
                return "genLabMacroPrefs";
            }
        }

        // ================================================================
        // DATABASE PERSISTENCE
        // ================================================================
        // Create or update UserProperty record for this provider's macros.
        // Each provider has one UserProperty record with name="labMacroJSON".
        UserProperty wProperty = this.userPropertyDAO.getProp(providerNo, "labMacroJSON");
        if (wProperty == null) {
            // First time saving macros for this provider: create new record
            wProperty = new UserProperty();
            wProperty.setProviderNo(providerNo);
            wProperty.setName("labMacroJSON");
        }
        wProperty.setValue(prefs);
        userPropertyDAO.saveProp(wProperty);

        // ================================================================
        // AUDIT LOGGING
        // ================================================================
        // Record the save action to audit log for compliance/forensics.
        // Log the action name only (not full JSON content, for privacy).
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request),
            "LabMacroPreferences", "labMacroJSON", "", null, "updated");

        request.setAttribute("status", "success");
        return "genLabMacroPrefs";
    }

    public String viewHl7LabResultPrefs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
			throw new RuntimeException("missing required security object _lab");
		}

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        boolean offerFileForOthers = providerManager2.isHl7OfferFileForOthers(loggedInInfo, providerNo);
        boolean allowOthersFileForYou = providerManager2.isHl7AllowOthersFileForYou(loggedInInfo, providerNo);

        request.setAttribute("offerFileForOthers", offerFileForOthers);
        request.setAttribute("allowOthersFileForYou", allowOthersFileForYou);

        return "genHl7LabResultPrefs";
    }

     public String setOfferFileForOthersPref() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.WRITE, null)) {
			throw new RuntimeException("missing required security object _lab");
		}
        
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String value = request.getParameter("value");
        boolean status = providerManager2.updateHl7OfferFileForOthers(loggedInInfo, providerNo, value);

        Map<String, Object> json = new HashMap<>();
        json.put("status", status);
        writeJsonResponse(response, json);
        return null;
    }

    public String setAllowOthersFileForYouPref() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.WRITE, null)) {
			throw new RuntimeException("missing required security object _lab");
		}

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String value = request.getParameter("value");
        boolean status = providerManager2.updateHl7AllowOthersFileForYou(loggedInInfo, providerNo, value);

        Map<String, Object> json = new HashMap<>();
        json.put("status", status);
        writeJsonResponse(response, json);
        return null;
    }

    /**
     * Serializes an object as JSON and writes it to the HTTP response.
     *
     * <p>On serialization failure, logs the error and sets HTTP 500 status
     * (if the response has not already been committed) so the client can
     * detect the failure rather than receiving an empty 200 response.</p>
     *
     * @param response HttpServletResponse the servlet response to write to
     * @param json Object the object to serialize as JSON
     */
    private void writeJsonResponse(HttpServletResponse response, Object json) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

       try {
          ObjectMapper mapper = new ObjectMapper();
          mapper.writeValue(response.getWriter(), json);
        } catch (Exception e) {
            logger.error("An error occurred while writing JSON response to the output stream", e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private final Map<String, Supplier<String>> methodMap = new HashMap<>();

    @PostConstruct
    public void init() {
        methodMap.put("OscarMsgRecvd", this::OscarMsgRecvd);
        methodMap.put("remove", this::remove);
        methodMap.put("save", this::save);
        methodMap.put("viewDefaultSex", this::viewDefaultSex);
        methodMap.put("saveDefaultSex", this::saveDefaultSex);
        methodMap.put("viewHCType", this::viewHCType);
        methodMap.put("saveHCType", this::saveHCType);
        methodMap.put("viewRxPageSize", this::viewRxPageSize);
        methodMap.put("saveRxPageSize", this::saveRxPageSize);
        methodMap.put("saveDefaultDocQueue", this::saveDefaultDocQueue);
        methodMap.put("viewDefaultDocQueue", this::viewDefaultDocQueue);
        methodMap.put("viewRxProfileView", this::viewRxProfileView);
        methodMap.put("saveRxProfileView", this::saveRxProfileView);
        methodMap.put("viewShowPatientDOB", this::viewShowPatientDOB);
        methodMap.put("saveShowPatientDOB", this::saveShowPatientDOB);
        methodMap.put("viewUseRx3", this::viewUseRx3);
        methodMap.put("saveUseRx3", this::saveUseRx3);
        methodMap.put("viewDefaultQuantity", this::viewDefaultQuantity);
        methodMap.put("saveDefaultQuantity", this::saveDefaultQuantity);
        methodMap.put("viewOntarioMDId", this::viewOntarioMDId);
        methodMap.put("saveOntarioMDId", this::saveOntarioMDId);
        methodMap.put("viewConsultationRequestCuffOffDate", this::viewConsultationRequestCuffOffDate);
        methodMap.put("saveConsultationRequestCuffOffDate", this::saveConsultationRequestCuffOffDate);
        methodMap.put("viewConsultationRequestTeamWarning", this::viewConsultationRequestTeamWarning);
        methodMap.put("saveConsultationRequestTeamWarning", this::saveConsultationRequestTeamWarning);
        methodMap.put("viewWorkLoadManagement", this::viewWorkLoadManagement);
        methodMap.put("saveWorkLoadManagement", this::saveWorkLoadManagement);
        methodMap.put("viewConsultPasteFmt", this::viewConsultPasteFmt);
        methodMap.put("saveConsultPasteFmt", this::saveConsultPasteFmt);
        methodMap.put("viewFavouriteEformGroup", this::viewFavouriteEformGroup);
        methodMap.put("saveFavouriteEformGroup", this::saveFavouriteEformGroup);
        methodMap.put("viewCppSingleLine", this::viewCppSingleLine);
        methodMap.put("saveUseCppSingleLine", this::saveUseCppSingleLine);
        methodMap.put("viewEDocBrowserInDocumentReport", this::viewEDocBrowserInDocumentReport);
        methodMap.put("saveEDocBrowserInDocumentReport", this::saveEDocBrowserInDocumentReport);
        methodMap.put("viewEDocBrowserInMasterFile", this::viewEDocBrowserInMasterFile);
        methodMap.put("saveEDocBrowserInMasterFile", this::saveEDocBrowserInMasterFile);
        methodMap.put("viewCommentLab", this::viewCommentLab);
        methodMap.put("saveCommentLab", this::saveCommentLab);
        methodMap.put("viewLabRecall", this::viewLabRecall);
        methodMap.put("saveLabRecallPrefs", this::saveLabRecallPrefs);
        methodMap.put("viewTicklerTaskAssignee", this::viewTicklerTaskAssignee);
        methodMap.put("saveTicklerTaskAssignee", this::saveTicklerTaskAssignee);
        methodMap.put("viewEncounterWindowSize", this::viewEncounterWindowSize);
        methodMap.put("saveEncounterWindowSize", this::saveEncounterWindowSize);
        methodMap.put("viewQuickChartSize", this::viewQuickChartSize);
        methodMap.put("saveQuickChartSize", this::saveQuickChartSize);
        methodMap.put("viewPatientNameLength", this::viewPatientNameLength);
        methodMap.put("savePatientNameLength", this::savePatientNameLength);
        methodMap.put("viewDisplayDocumentAs", this::viewDisplayDocumentAs);
        methodMap.put("saveDisplayDocumentAs", this::saveDisplayDocumentAs);
        methodMap.put("viewHideOldEchartLinkInAppt", this::viewHideOldEchartLinkInAppt);
        methodMap.put("saveHideOldEchartLinkInAppt", this::saveHideOldEchartLinkInAppt);
        methodMap.put("viewDashboardPrefs", this::viewDashboardPrefs);
        methodMap.put("saveDashboardPrefs", this::saveDashboardPrefs);
        methodMap.put("viewAppointmentCardPrefs", this::viewAppointmentCardPrefs);
        methodMap.put("saveAppointmentCardPrefs", this::saveAppointmentCardPrefs);
        methodMap.put("viewPreventionPrefs", this::viewPreventionPrefs);
        methodMap.put("savePreventionPrefs", this::savePreventionPrefs);
        methodMap.put("viewLabMacroPrefs", this::viewLabMacroPrefs);
        methodMap.put("saveLabMacroPrefs", this::saveLabMacroPrefs);
        methodMap.put("viewHl7LabResultPrefs", this::viewHl7LabResultPrefs);
        methodMap.put("setOfferFileForOthersPref", this::setOfferFileForOthersPref);
        methodMap.put("setAllowOthersFileForYouPref", this::setAllowOthersFileForYouPref);
    }

    private UserProperty dateProperty;
    private UserProperty singleViewProperty;
    private UserProperty rxPageSizeProperty;
    private UserProperty existingDefaultDocQueueProperty;
    private UserProperty newDefaultDocQueueProperty;
    private UserProperty rxProfileViewProperty;
    private UserProperty rxShowPatientDOBProperty;
    private UserProperty rxUseRx3Property;
    private UserProperty rxDefaultQuantityProperty;
    private UserProperty dateProperty2;
    private UserProperty cppSingleLineProperty;
    private UserProperty eDocBrowserInDocumentReportProperty;
    private UserProperty eDocBrowserInMasterFileProperty;
    private UserProperty labAckCommentProperty;
    private UserProperty labRecallDelegate;
    private UserProperty labRecallMsgSubject;
    private UserProperty labRecallTicklerAssignee;
    private UserProperty labRecallTicklerPriority;
    private UserProperty taskAssigneeSelection;
    private UserProperty taskAssigneeMRP;
    private UserProperty encounterWindowWidth;
    private UserProperty encounterWindowHeight;
    private UserProperty encounterWindowMaximize;
    private UserProperty quickChartSize;
    private UserProperty patientNameLength;
    private UserProperty displayDocumentAsProperty;
    private UserProperty hideOldEchartLinkInApptProperty;
    private UserProperty dashboardShareProperty;
    private UserProperty appointmentCardName;
    private UserProperty appointmentCardPhone;
    private UserProperty appointmentCardFax;
    private UserProperty preventionSSOWarningProperty;
    private UserProperty preventionISPAWarningProperty;
    private UserProperty preventionNonISPAWarningProperty;
    private UserProperty labMacroJSON;

    @StrutsParameter(depth = 1)
    public UserProperty getDateProperty() {
        return dateProperty;
    }

    @StrutsParameter
    public void setDateProperty(UserProperty dateProperty) {
        this.dateProperty = dateProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getSingleViewProperty() {
        return singleViewProperty;
    }

    @StrutsParameter
    public void setSingleViewProperty(UserProperty singleViewProperty) {
        this.singleViewProperty = singleViewProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getRxPageSizeProperty() {
        return rxPageSizeProperty;
    }

    @StrutsParameter
    public void setRxPageSizeProperty(UserProperty rxPageSizeProperty) {
        this.rxPageSizeProperty = rxPageSizeProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getExistingDefaultDocQueueProperty() {
        return existingDefaultDocQueueProperty;
    }

    @StrutsParameter
    public void setExistingDefaultDocQueueProperty(UserProperty existingDefaultDocQueueProperty) {
        this.existingDefaultDocQueueProperty = existingDefaultDocQueueProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getNewDefaultDocQueueProperty() {
        return newDefaultDocQueueProperty;
    }

    @StrutsParameter
    public void setNewDefaultDocQueueProperty(UserProperty newDefaultDocQueueProperty) {
        this.newDefaultDocQueueProperty = newDefaultDocQueueProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getRxProfileViewProperty() {
        return rxProfileViewProperty;
    }

    @StrutsParameter
    public void setRxProfileViewProperty(UserProperty rxProfileViewProperty) {
        this.rxProfileViewProperty = rxProfileViewProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getRxShowPatientDOBProperty() {
        return rxShowPatientDOBProperty;
    }

    @StrutsParameter
    public void setRxShowPatientDOBProperty(UserProperty rxShowPatientDOBProperty) {
        this.rxShowPatientDOBProperty = rxShowPatientDOBProperty;
    }


    @StrutsParameter(depth = 1)
    public UserProperty getRxUseRx3Property() {
        return rxUseRx3Property;
    }

    @StrutsParameter
    public void setRxUseRx3Property(UserProperty rxUseRx3Property) {
        this.rxUseRx3Property = rxUseRx3Property;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getRxDefaultQuantityProperty() {
        return rxDefaultQuantityProperty;
    }

    @StrutsParameter
    public void setRxDefaultQuantityProperty(UserProperty rxDefaultQuantityProperty) {
        this.rxDefaultQuantityProperty = rxDefaultQuantityProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getDateProperty2() {
        return dateProperty2;
    }

    @StrutsParameter
    public void setDateProperty2(UserProperty dateProperty2) {
        this.dateProperty2 = dateProperty2;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getCppSingleLineProperty() {
        return cppSingleLineProperty;
    }

    @StrutsParameter
    public void setCppSingleLineProperty(UserProperty cppSingleLineProperty) {
        this.cppSingleLineProperty = cppSingleLineProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty geteDocBrowserInDocumentReportProperty() {
        return eDocBrowserInDocumentReportProperty;
    }

    @StrutsParameter
    public void seteDocBrowserInDocumentReportProperty(UserProperty eDocBrowserInDocumentReportProperty) {
        this.eDocBrowserInDocumentReportProperty = eDocBrowserInDocumentReportProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty geteDocBrowserInMasterFileProperty() {
        return eDocBrowserInMasterFileProperty;
    }

    @StrutsParameter
    public void seteDocBrowserInMasterFileProperty(UserProperty eDocBrowserInMasterFileProperty) {
        this.eDocBrowserInMasterFileProperty = eDocBrowserInMasterFileProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getLabAckCommentProperty() {
        return labAckCommentProperty;
    }

    @StrutsParameter
    public void setLabAckCommentProperty(UserProperty labAckCommentProperty) {
        this.labAckCommentProperty = labAckCommentProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getLabRecallDelegate() {
        return labRecallDelegate;
    }

    @StrutsParameter
    public void setLabRecallDelegate(UserProperty labRecallDelegate) {
        this.labRecallDelegate = labRecallDelegate;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getLabRecallMsgSubject() {
        return labRecallMsgSubject;
    }

    @StrutsParameter
    public void setLabRecallMsgSubject(UserProperty labRecallMsgSubject) {
        this.labRecallMsgSubject = labRecallMsgSubject;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getLabRecallTicklerAssignee() {
        return labRecallTicklerAssignee;
    }

    @StrutsParameter
    public void setLabRecallTicklerAssignee(UserProperty labRecallTicklerAssignee) {
        this.labRecallTicklerAssignee = labRecallTicklerAssignee;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getLabRecallTicklerPriority() {
        return labRecallTicklerPriority;
    }

    @StrutsParameter
    public void setLabRecallTicklerPriority(UserProperty labRecallTicklerPriority) {
        this.labRecallTicklerPriority = labRecallTicklerPriority;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getTaskAssigneeSelection() {
        return taskAssigneeSelection;
    }

    @StrutsParameter
    public void setTaskAssigneeSelection(UserProperty taskAssigneeSelection) {
        this.taskAssigneeSelection = taskAssigneeSelection;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getTaskAssigneeMRP() {
        return taskAssigneeMRP;
    }

    @StrutsParameter
    public void setTaskAssigneeMRP(UserProperty taskAssigneeMRP) {
        this.taskAssigneeMRP = taskAssigneeMRP;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getEncounterWindowWidth() {
        return encounterWindowWidth;
    }

    @StrutsParameter
    public void setEncounterWindowWidth(UserProperty encounterWindowWidth) {
        this.encounterWindowWidth = encounterWindowWidth;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getEncounterWindowHeight() {
        return encounterWindowHeight;
    }

    @StrutsParameter
    public void setEncounterWindowHeight(UserProperty encounterWindowHeight) {
        this.encounterWindowHeight = encounterWindowHeight;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getEncounterWindowMaximize() {
        return encounterWindowMaximize;
    }

    @StrutsParameter
    public void setEncounterWindowMaximize(UserProperty encounterWindowMaximize) {
        this.encounterWindowMaximize = encounterWindowMaximize;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getQuickChartSize() {
        return quickChartSize;
    }

    @StrutsParameter
    public void setQuickChartSize(UserProperty quickChartSize) {
        this.quickChartSize = quickChartSize;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getPatientNameLength() {
        return patientNameLength;
    }

    @StrutsParameter
    public void setPatientNameLength(UserProperty patientNameLength) {
        this.patientNameLength = patientNameLength;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getDisplayDocumentAsProperty() {
        return displayDocumentAsProperty;
    }

    @StrutsParameter
    public void setDisplayDocumentAsProperty(UserProperty displayDocumentAsProperty) {
        this.displayDocumentAsProperty = displayDocumentAsProperty;
    }


    @StrutsParameter(depth = 1)
    public UserProperty getHideOldEchartLinkInApptProperty() {
        return hideOldEchartLinkInApptProperty;
    }

    @StrutsParameter
    public void setHideOldEchartLinkInApptProperty(UserProperty hideOldEchartLinkInApptProperty) {
        this.hideOldEchartLinkInApptProperty = hideOldEchartLinkInApptProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getDashboardShareProperty() {
        return dashboardShareProperty;
    }

    @StrutsParameter
    public void setDashboardShareProperty(UserProperty dashboardShareProperty) {
        this.dashboardShareProperty = dashboardShareProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getAppointmentCardName() {
        return appointmentCardName;
    }

    @StrutsParameter
    public void setAppointmentCardName(UserProperty appointmentCardName) {
        this.appointmentCardName = appointmentCardName;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getAppointmentCardPhone() {
        return appointmentCardPhone;
    }

    @StrutsParameter
    public void setAppointmentCardPhone(UserProperty appointmentCardPhone) {
        this.appointmentCardPhone = appointmentCardPhone;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getAppointmentCardFax() {
        return appointmentCardFax;
    }

    @StrutsParameter
    public void setAppointmentCardFax(UserProperty appointmentCardFax) {
        this.appointmentCardFax = appointmentCardFax;
    }


    @StrutsParameter(depth = 1)
    public UserProperty getPreventionSSOWarningProperty() {
        return preventionSSOWarningProperty;
    }

    @StrutsParameter
    public void setPreventionSSOWarningProperty(UserProperty preventionSSOWarningProperty) {
        this.preventionSSOWarningProperty = preventionSSOWarningProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getPreventionISPAWarningProperty() {
        return preventionISPAWarningProperty;
    }

    @StrutsParameter
    public void setPreventionISPAWarningProperty(UserProperty preventionISPAWarningProperty) {
        this.preventionISPAWarningProperty = preventionISPAWarningProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getPreventionNonISPAWarningProperty() {
        return preventionNonISPAWarningProperty;
    }

    @StrutsParameter
    public void setPreventionNonISPAWarningProperty(UserProperty preventionNonISPAWarningProperty) {
        this.preventionNonISPAWarningProperty = preventionNonISPAWarningProperty;
    }

    @StrutsParameter(depth = 1)
    public UserProperty getLabMacroJSON() {
        return labMacroJSON;
    }

    @StrutsParameter
    public void setLabMacroJSON(UserProperty labMacroJSON) {
        this.labMacroJSON = labMacroJSON;
    }
}
