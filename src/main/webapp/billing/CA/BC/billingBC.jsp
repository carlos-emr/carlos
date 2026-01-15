<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

--%>

<%@page import="java.net.URLEncoder" %>
<%@page import="java.nio.charset.StandardCharsets" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>

<%@taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@page import="java.util.*, ca.openosp.openo.billing.ca.bc.data.*,ca.openosp.openo.billing.ca.bc.pageUtil.*,ca.openosp.*,ca.openosp.openo.entities.*" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.BillingreferralDao" %>
<%@ page import="ca.openosp.openo.dxresearch.util.dxResearchCodingSystem" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.commn.dao.PropertyDao" %>
<%@ page import="ca.openosp.openo.commn.model.Property" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager,ca.openosp.openo.billings.ca.bc.MSP.ServiceCodeValidationLogic" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="ca.openosp.openo.entities.PaymentType" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.pageUtil.BillingSessionBean" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingPreferencesDAO" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.pageUtil.ServiceCodeAssociation" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.pageUtil.BillingAssociationPersistence" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.pageUtil.BillingCreateBillingForm" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.SupServiceCodeAssocDAO" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingFormData" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingPreference" %>
<%@ page import="ca.openosp.OscarProperties" %>

<%!

    public void fillDxcodeList(BillingFormData.BillingService[] servicelist, Map<String, String> dxcodeList) {
        for (int i = 0; i < servicelist.length; i++) {
            BillingAssociationPersistence per = new BillingAssociationPersistence();
            ServiceCodeAssociation assoc = per.getServiceCodeAssocByCode(servicelist[i].getServiceCode());
            List dxcodes = assoc.getDxCodes();
            if (!dxcodes.isEmpty()) {
                dxcodeList.put(servicelist[i].getServiceCode(), (String) dxcodes.get(0));
            }
        }
    }

    public String createAssociationJS(Map<String, String> assocCodeMap, String assocArrayName) {

        Set e = assocCodeMap.keySet();
        StringBuffer out = new StringBuffer();
        out.append("var " + assocArrayName + "  = new Array();");
        out.append("\n");
        int index = 0;
        for (Iterator iter = e.iterator(); iter.hasNext(); ) {
            String key = (String) iter.next();
            String value = (String) assocCodeMap.get(key);
            String rowName = assocArrayName + "row";
            out.append("var " + rowName + index + " = new Array(2);\n");
            out.append(rowName + index + "[0]='" + key + "'; ");
            out.append(rowName + index + "[1]='" + value + "';\n");
            out.append(assocArrayName + "[" + index + "]=" + rowName + index + ";\n");
            index++;
        }
        return out.toString();
    }

    PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    BillingFormData billform = new BillingFormData();
    ServiceCodeValidationLogic lgc = new ServiceCodeValidationLogic();
    BillingreferralDao billingReferralDao = SpringUtils.getBean(BillingreferralDao.class);
    SupServiceCodeAssocDAO supDao = SpringUtils.getBean(SupServiceCodeAssocDAO.class);
    dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
    BillingPreferencesDAO billingPreferencesDAO = SpringUtils.getBean(BillingPreferencesDAO.class);
%>
<%
    // current data is held in the session.
    BillingSessionBean bean = (BillingSessionBean) pageContext.findAttribute("billingSessionBean");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Demographic demo = demographicManager.getDemographic(loggedInInfo, bean.getPatientNo());

    // billing type setting MSP, Private, ICBC, WCB, etc..
    String frmType = request.getParameter("billType");

    // not sure why this is needed. It's too costly to rewrite.
    String loadFromSession = request.getParameter("loadFromSession");

    // load new WCB type
    String newWCBClaim = (String) request.getAttribute("newWCBClaim");

    /* billing from appointment will fetch the defaults for the providers whom the appointment was with
     * "sign save and bill" will fetch the defaults for the logged in providers who wrote and signed the encounter note
     * If "xml_provider" is null then the logged in providers is used
     */
    String targetProvider = loggedInInfo.getLoggedInProviderNo();
    String alternateProvider = request.getParameter("xml_provider");
    if (alternateProvider != null && !alternateProvider.isEmpty()) {
        targetProvider = alternateProvider;
    }

    /*
     * find and default providers set for the current target providers in the
     * billing preference settings.
     * Target providers now becomes the providers set in the invoice settings.
     */
    List<Property> defaultBillingProviderPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.default_billing_provider, targetProvider);
    if (defaultBillingProviderPropertyList != null && !defaultBillingProviderPropertyList.isEmpty()) {
        Property defaultBillingProvider = defaultBillingProviderPropertyList.get(0);
        if (defaultBillingProvider != null && !"clinicdefault".equalsIgnoreCase(defaultBillingProvider.getValue())) {
            targetProvider = defaultBillingProvider.getValue();
        }
    }

    /* the value set preset in the session overrides the parameter value
     * The target providers becomes "none". The remaining values are overridden by the
     * session bean.
     */
    if (bean.getBillingProvider() == null) {
        bean.setBillingProvider(targetProvider);
    } else {
        targetProvider = "none";
    }

    /* Billing settings:
     * 1. load the selected users preferences (targetProvider)
     * 2. if no selected user load the logged-in users preferences
     * 3. load system default preferences if no selected user and logged in user has no preferences (system default)
     */


    /* sort out which Billing form to use based on global and providers settings.
     * 1. Default value set in the properties file
     */
    String defaultBillingForm = OscarProperties.getInstance().getProperty("default_view");
    if (defaultBillingForm != null) {
        defaultBillingForm = defaultBillingForm.trim();
    }

    // 2. Default value set in Billing Settings
    List<Property> defaultBillingFormPropertyList = propertyDao.findGlobalByName(Property.PROPERTY_KEY.default_billing_form.name());
    if (defaultBillingFormPropertyList != null && !defaultBillingFormPropertyList.isEmpty()) {
        Property defaultBillingFormProperty = defaultBillingFormPropertyList.get(0);
        String billingForm = null;
        if (defaultBillingFormProperty != null) {
            billingForm = defaultBillingFormProperty.getValue();
        }
        if (billingForm != null && !billingForm.isEmpty()
                && !Property.PROPERTY_VALUE.clinicdefault.name().equalsIgnoreCase(billingForm)) {
            defaultBillingForm = billingForm;
        }
    }

    // 3. individual providers billing preferences.
    List<Property> userSetDefaultBillingFormPropertyList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.default_billing_form, targetProvider);
    if (userSetDefaultBillingFormPropertyList != null && !userSetDefaultBillingFormPropertyList.isEmpty()) {
        Property userSetDefaultBillingFormProperty = userSetDefaultBillingFormPropertyList.get(0);
        String userSetBillingForm = null;
        if (userSetDefaultBillingFormProperty != null) {
            userSetBillingForm = userSetDefaultBillingFormProperty.getValue();
        }
        if (userSetBillingForm != null && !userSetBillingForm.isEmpty()
                && !Property.PROPERTY_VALUE.clinicdefault.name().equalsIgnoreCase(userSetBillingForm.trim())) {
            // override the billingform preset/default in session with the providers preference.
            defaultBillingForm = userSetBillingForm;
        }
    }

    // 3.a. in some situations if the default billing form is set as Private it may be possible
    // to set the Billing Type to private as well
    // The commons code for a private billing form is PRI
    if ("PRI".equals(defaultBillingForm)) {
        bean.setBillType("Pri");
    }

    // 4. logged in user is overriding billing form preference during billing process.
    /* horrible hack. Do not repeat.
     * If the targetProvider is set to "none" then this indicates that the
     * Billing sheet selection has been overridden from the billing form.
     * The strange part is that the billing form selection has already been
     * set into the "bean" object before reaching this point.
     * Normally not a big deal - but - in this case the billing sheet is loaded
     * into the billing interface dynamically.
     */
    if ("none".equals(targetProvider)) {
        defaultBillingForm = bean.getBillForm();
    }

    if (billform.serviceExists(defaultBillingForm)) {
        bean.setBillForm(defaultBillingForm);
    }

    // 1. global default billing visit location: oscar properties
    String defaultServiceLocation = OscarProperties.getInstance().getProperty("visittype");

    // 2. global billing settings
    List<Property> userSetDefaultServiceLocationList = propertyDao.findGlobalByName(Property.PROPERTY_KEY.bc_default_service_location.toString());
    if (userSetDefaultServiceLocationList != null && !userSetDefaultServiceLocationList.isEmpty()) {
        Property uerSetDefaultServiceLocationProperty = userSetDefaultServiceLocationList.get(0);
        String userSetDefaultServiceLocation = null;
        if (uerSetDefaultServiceLocationProperty != null) {
            userSetDefaultServiceLocation = uerSetDefaultServiceLocationProperty.getValue();
        }
        if (userSetDefaultServiceLocation != null) {
            defaultServiceLocation = userSetDefaultServiceLocation;
        }
    }

    // 3. override visit location "visittype" code preference by providers
    List<Property> defaultServiceLocationList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.bc_default_service_location, targetProvider);
    if (defaultServiceLocationList != null && !defaultServiceLocationList.isEmpty()) {
        Property defaultServiceLocationProperty = defaultServiceLocationList.get(0);
        String providerSetDefaultLocation = null;
        if (defaultServiceLocationProperty != null) {
            providerSetDefaultLocation = defaultServiceLocationProperty.getValue();
        }
        if (providerSetDefaultLocation != null && !providerSetDefaultLocation.isEmpty()
                && !Property.PROPERTY_VALUE.clinicdefault.name().equalsIgnoreCase(providerSetDefaultLocation)) {
            // override default from Oscar properties or billing properties with the providers preference
            defaultServiceLocation = providerSetDefaultLocation;
        }
    }

    // this captures and modifies any legacy codes that may be still hanging around
    if (defaultServiceLocation.contains("|")) {
        defaultServiceLocation = defaultServiceLocation.split("\\|")[0].trim();
    }

    /*
     * only override if the session value is not already set.
     */
    if (bean.getVisitType() == null) {
        bean.setVisitType(defaultServiceLocation);
    }

    /*
     * Get the users preference for "Refer to" or "Refer by"
     */
    BillingPreference billingPreference = billingPreferencesDAO.getUserBillingPreference(targetProvider);
    String userReferralPref = "";
    if (billingPreference != null) {
        if (billingPreference.getReferral() == 1) {
            userReferralPref = "T";
        } else if (billingPreference.getReferral() == 2) {
            userReferralPref = "B";
        }
    }

    // set up the selected billing form
    BillingFormData.BillingService[] billlist1 = lgc.filterServiceCodeList(billform.getServiceList("Group1", bean.getBillForm(), bean.getBillRegion(), new Date()), demo);
    BillingFormData.BillingService[] billlist2 = lgc.filterServiceCodeList(billform.getServiceList("Group2", bean.getBillForm(), bean.getBillRegion(), new Date()), demo);
    BillingFormData.BillingService[] billlist3 = lgc.filterServiceCodeList(billform.getServiceList("Group3", bean.getBillForm(), bean.getBillRegion(), new Date()), demo);

    HashMap<String, String> assocCodeMap = new HashMap<>();
    fillDxcodeList(billlist1, assocCodeMap);
    fillDxcodeList(billlist2, assocCodeMap);
    fillDxcodeList(billlist3, assocCodeMap);

    String group1Header = billform.getServiceGroupName("Group1", bean.getBillForm());
    String group2Header = billform.getServiceGroupName("Group2", bean.getBillForm());
    String group3Header = billform.getServiceGroupName("Group3", bean.getBillForm());

    if (frmType != null && frmType.equals("Pri")) {
        billform.setPrivateFees(billlist1);
        billform.setPrivateFees(billlist2);
        billform.setPrivateFees(billlist3);
    }

    // set up the form data elements
    BillingFormData.BillingPhysician[] billphysician = billform.getProviderList();
    List<BillingFormData.BillingVisit> billvisit = billform.getVisitType(bean.getBillRegion());
    BillingFormData.Location[] billlocation = billform.getLocationList(bean.getBillRegion());
    BillingFormData.Diagnostic[] diaglist = billform.getDiagnosticList(bean.getBillForm(), bean.getBillRegion());
    BillingFormData.BillingForm[] billformlist = billform.getFormList();
    ArrayList<String> recentReferralDoctorList = billform.getRecentReferralDoctorsList(demo.getDemographicNo());


    if (request.getAttribute("loadFromSession") != null) {
        loadFromSession = "y";
    }
    if (loadFromSession == null) {
        request.getSession().removeAttribute("BillingCreateBillingForm");
    }

    // set up the referr-ing doctor
    String mRecRefDoctor = "";
    String mRecRefDoctorNum = "";

    if (!"".equals(demo.getFamilyDoctorNumber())) {
        mRecRefDoctor = demo.getFamilyDoctorName();
        mRecRefDoctorNum = demo.getFamilyDoctorNumber();
    } else {
        mRecRefDoctor = "none";
    }


    // set up useful pagecontext attributes.
    pageContext.setAttribute("dxCodeSystemList", codingSys);

%>
<!DOCTYPE html>
<html>
<head>
    <title>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.bc.title"/>
    </title>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>

    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/css/bootstrap-datetimepicker-standalone.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/css/bootstrap-datetimepicker.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/bootstrap/3.0.0/css/bootstrap.min.css"/>

    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/moment.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/bootstrap-datetimepicker.min.js"></script>

    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/share/calendar/calendar.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/calendar/calendar-setup.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/javascript/prototype.js"></script>
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/share/javascript/Oscar.js"></script>
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/share/javascript/boxover.js"></script>
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/dxJSONCodeSearch.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery.validate-1.19.5.min.js"></script>

    <style>

        :root * {
            font-family: "Helvetica Neue", Helvetica, Arial, sans-serif, 'Glyphicons Halflings';

            line-height: 1 !important;
        }

        :root *:not(h1,h2,h3) {
            font-size: 12px;
        }

        table {
            margin-bottom: unset !important;
            width: 100%;
        }

        .btn {
            font-size: 14px !important;
            font-weight: normal;
            line-height: 1.428571429 !important;
        }

        .container-fluid ul {
            margin: 0;
            padding: 0;
        }

        div.tool-table {
            background-color: #fff;
        }

        div.tool-table table tr td {
            padding: unset;
        }

        td > div.tool-table div.input-group, td > div.tool-table .form-control,
        td > div.tool-table div .form-control {
            padding-bottom: 5px;

        }

        div.tool-table table tr td:nth-of-type(2) {
            padding-left: 3px !important;
        }

        table {
            margin-bottom: unset !important;
        }


        div.tool-table:last-of-type {
            margin-right: 3px;
        }

        div.tool-table:first-of-type {
            margin-left: 3px;
        }

        div#wcbForms p {
            padding: 0;
            margin: 0;
        }

        div#wcbForms table {
            margin: 0 0 5px 0;
        }

        div#wcbForms table th {
            font-weight: normal;
        }

        div#wcbForms pre {
            display: none;
        }

        div#wcbForms table tr td, div#wcbForms table tr th, div#wcbForms table {
            border-color: #ddd;
        }

        table.tool-bar tr td {
            vertical-align: bottom;
        }

        strong, label {
            font-weight: normal !important;
            padding: 0 3px 0 0;
        }

        #billingPatientInfo {
            margin-top: 5px;
        }

        .wrapper {
            margin: auto 15px;
        }

        div#page-header {
            position: sticky;
            width: 100%;
            top: 0;
            left: 0;
            right: 0;
            z-index: 100000;
            border-bottom: 1px solid #ccc;
            padding: 1px 5px;
            background-color: #F3F3F3;
            display: flex;
            align-items: first baseline;
            gap: 5px;
        }

        div#page-header h3 {
            margin: 0;
        }

        table#billingFormTable table.tool-table tr td table {
            background-color: whitesmoke;
            margin: 4px 0;
            border: #ccc thin solid;
        }

        table#billingFormTable table.tool-table tr:nth-of-type(2) td table {
            margin: 0 0 4px 0;
        }

        table#billingFormTable table.tool-table {
            background-color: whitesmoke;
            margin-top: 5px;
            border: #ccc thin solid;
        }

        table#billingFormTable table.tool-table tr td table tr:first-of-type td {
            padding-top: 5px !important;
        }

        table#billingFormTable table.tool-table tr td table tr:last-of-type td {
            padding-bottom: 5px !important;
        }

        .serviceCodesTable {
            display: block;
            display: -webkit-inline-box;
            display: -webkit-box;
            margin-bottom: 5px !important;
            border-radius: 4px;
        }

        .serviceCodesTable tr:nth-child(even) {
            background: #f5f5f5
        }

        .serviceCodesTable tr:nth-child(odd) {
            background: #FFF
        }

        #billingFormTable table tr td {
            padding: 1px 5px !important;
        }

        tr#buttonRow td {
            padding-top: 15px !important;
            padding-bottom: 15px !important;
        }

        .ui-autocomplete {
            max-height: 200px;
            overflow-y: auto;
            /* prevent horizontal scrollbar */
            overflow-x: hidden;
            width: 200px;
        }

        /* IE 6 doesn't support max-height
               * we use height instead, but this forces the menu to always be this tall
               */
        * html .ui-autocomplete {
            height: 100px;
        }

        table.table-borderless tr td, table.table-borderless tr th, table.table-borderless {
            border: none;
            border: 0;
        }

        .has-error {
            background-color: #f2dede;
        }

        table#billingFormTable tr td {
            vertical-align: top;
        }

        .icon-container {
            align-self: flex-start;
            padding: 3px 10px;
        }


    </style>
    <script type="text/javascript">

        jQuery.noConflict();

        // set the context path for javascript functions
        var ctx = '${pageContext.servletContext.contextPath}';

        //creates a javaspt array of associated dx codes
        <%=createAssociationJS(assocCodeMap,"jsAssocCodes")%>
        <%=createAssociationJS(supDao.getAssociationKeyValues(),"trayAssocCodes")%>


        function codeEntered(svcCode) {
            myform = document.forms[0];
            return ((myform.xml_other1.value == svcCode) || (myform.xml_other2.value == svcCode) || (myform.xml_other3.value == svcCode))
        }

        function addSvcCode(svcCode) {
            myform = document.forms[0];
            if (myform.service) {
                for (var i = 0; i < myform.service.length; i++) {
                    if (myform.service[i].value == svcCode) {
                        if (myform.service[i].checked) {
                            if (myform.xml_other1.value == "") {
                                myform.xml_other1.value = svcCode;
                                var trayCode = getAssocCode(svcCode, trayAssocCodes);
                                if (trayCode != '') {
                                    myform.xml_other2.value = trayCode;
                                }
                                myform.xml_diagnostic_detail1.value = getAssocCode(svcCode, jsAssocCodes);
                            } else if (myform.xml_other2.value == "") {
                                myform.xml_other2.value = svcCode;
                                var trayCode = getAssocCode(svcCode, trayAssocCodes);
                                if (trayCode != '') {
                                    myform.xml_other3.value = trayCode;
                                }
                                myform.xml_diagnostic_detail2.value = getAssocCode(svcCode, jsAssocCodes);
                            } else if (myform.xml_other3.value == "") {
                                myform.xml_other3.value = svcCode;
                                myform.xml_diagnostic_detail3.value = getAssocCode(svcCode, jsAssocCodes);
                            } else {
                                alert("There are already three service codes entered");
                                myform.service[i].checked = false;
                                return;
                            }
                        } else {
                            if (myform.xml_other1.value == svcCode) {
                                myform.xml_other1.value = "";
                                myform.xml_other2.value = "";
                                myform.xml_diagnostic_detail1.value = "";
                            } else if (myform.xml_other2.value == svcCode) {
                                myform.xml_other2.value = "";
                                myform.xml_diagnostic_detail2.value = "";
                            } else if (myform.xml_other3.value == svcCode) {
                                myform.xml_other3.value = "";
                                myform.xml_diagnostic_detail3.value = "";
                            }
                        }
                        return;
                    }
                }
            }
        }

        function getAssocCode(svcCode, assocCodes) {
            var retcode = ""
            for (var i = 0; i < assocCodes.length; i++) {
                var row = assocCodes[i];

                if (row[0] == svcCode) {
                    return row[1];
                }
            }
            return retcode;
        }

        function checkSelectedCodes() {
            myform = document.forms[0];
            if (myform.service) {
                for (var i = 0; i < myform.service.length; i++) {
                    if (myform.service[i].checked) {
                        if (!codeEntered(myform.service[i].value)) {
                            myform.service[i].checked = false;
                        }
                    }
                }
            }
        }

        function HideElementById(ele) {
            document.getElementById(ele).style.display = 'none';
        }

        function ShowElementById(ele) {
            document.getElementById(ele).style.display = '';
        }

        function CheckType() {
            if (document.BillingCreateBillingForm.xml_billtype.value === "ICBC") {
                ShowElementById('ICBC');
                document.BillingCreateBillingForm.mva_claim_code.options[1].selected = true;
            } else {
                HideElementById('ICBC');
                document.BillingCreateBillingForm.mva_claim_code.options[0].selected = true;
            }

            toggleWCB();
        }

        function callReplacementWebService(url, id) {
            var ran_number = Math.round(Math.random() * 1000000);
            var params = "demographicNo=<%=bean.getPatientNo()%>&wcb=&rand=" + ran_number;  //hack to get around ie caching the page
            new Ajax.Updater(id, url, {method: 'get', parameters: params, asynchronous: true});
        }

        <%
        String wcb = "";
        Integer wcbid = (Integer) request.getAttribute("WCBFormId");
        if (wcbid != null){
            wcb = "?wcbid="+wcbid;
        }
        %>


        function toggleWCB() {
            <%
           if(! "1".equals(newWCBClaim)){
            %>
            if (document.BillingCreateBillingForm.xml_billtype.value == "WCB") {
                document.BillingCreateBillingForm.fromBilling.value = "true";
            } else {
                document.BillingCreateBillingForm.fromBilling.value = "false";
            }
            <%}
            %>

            if (document.BillingCreateBillingForm.xml_billtype.value == "WCB") {
                callReplacementWebService("wcbForms.jsp<%=wcb%>", 'wcbForms');
            } else {
                jQuery("#wcbForms").empty();
            }

        }

        function replaceWCB(id) {
            oscarLog("In replaceWCB");
            var ur = "wcbForms.jsp?wcbid=" + id;
            callReplacementWebService(ur, 'wcbForms');
            oscarLog("replaceWCB out == " + ur);
        }

        function gotoPrivate() {

            if (document.BillingCreateBillingForm.xml_billtype.value === "Pri") {
                // change the billing sheet to private billing
                jQuery("#selectBillingForm").val("PRI").trigger('change');
            }

            // otherwise go back to the default billing form
            <%--else {--%>
            <%--   jQuery("#selectBillingForm").val('${ billingSessionBean.billForm }').trigger('change');--%>
            <%--}--%>
        }

        function correspondenceNote() {
            if (document.BillingCreateBillingForm.correspondenceCode.value === "0") {
                HideElementById('CORRESPONDENCENOTE');
            } else if (document.BillingCreateBillingForm.correspondenceCode.value === "C") {
                HideElementById('CORRESPONDENCENOTE');
            } else if (document.BillingCreateBillingForm.correspondenceCode.value === "N") {
                ShowElementById('CORRESPONDENCENOTE');
            } else {
                (document.BillingCreateBillingForm.correspondenceCode.value === "B")
                ShowElementById('CORRESPONDENCENOTE');
            }
        }

        function quickPickDiagnostic(diagnos) {

            if (document.BillingCreateBillingForm.xml_diagnostic_detail1.value === "") {
                document.BillingCreateBillingForm.xml_diagnostic_detail1.value = diagnos;
            } else if (document.BillingCreateBillingForm.xml_diagnostic_detail2.value === "") {
                document.BillingCreateBillingForm.xml_diagnostic_detail2.value = diagnos;
            } else if (document.BillingCreateBillingForm.xml_diagnostic_detail3.value === "") {
                document.BillingCreateBillingForm.xml_diagnostic_detail3.value = diagnos;
            } else {
                alert("All of the Diagnostic Coding Boxes are full");
            }
        }

        function isNumeric(strString) {
            var validNums = "0123456789.";
            var strChar;
            var retval = true;

            for (i = 0; i < strString.length && retval === true; i++) {
                strChar = strString.charAt(i);
                if (validNums.indexOf(strChar) === -1) {
                    retval = false;
                }
            }
            return retval;
        }

        function checkTextLimit(textField, maximumlength) {
            if (textField.value.length > maximumlength + 1) {
                alert("Maximun " + maximumlength + " characters");
            }
            if (textField.value.length > maximumlength) {
                textField.value = textField.value.substring(0, maximumlength);
            }
        }

        function RecordAttachments(Files, File0, File1, File2) {
            window.document.serviceform.elements["File0Data"].value = File0;
            window.document.serviceform.elements["File1Data"].value = File1;
            window.document.serviceform.elements["File2Data"].value = File2;
            window.document.all.Atts.innerText = Files;
        }

        var remote = null;

        function rs(n, u, w, h, x) {
            args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
            remote = window.open(u, n, args);
            if (remote != null) {
                if (remote.opener == null)
                    remote.opener = self;
            }
            if (x === 1) {
                return remote;
            }
        }


        var awnd = null;

        function OtherScriptAttach() {
            t0 = escape(document.BillingCreateBillingForm.xml_other1.value);
            t1 = escape(document.BillingCreateBillingForm.xml_other2.value);
            t2 = escape(document.BillingCreateBillingForm.xml_other3.value);
            awnd = rs('att', '<rewrite:reWrite jspPage="billingCodeNewSearch.jsp"/>?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=', 820, 740, 1);
            awnd.focus();
        }

        function ReferralScriptAttach1() {
            ReferralScriptAttach('xml_refer1');
        }

        function ReferralScriptAttach2() {
            ReferralScriptAttach('xml_refer2');
        }


        function ReferralScriptAttach(elementName) {
            var d = elementName;
            t0 = escape(document.BillingCreateBillingForm.elements[d].value);
            t1 = escape("");
            awnd = rs('att', '<rewrite:reWrite jspPage="billingReferCodeSearch.jsp"/>?name=' + t0 + '&name1=' + t1 + '&name2=&search=&formElement=' + d + '&formName=BillingCreateBillingForm', 600, 600, 1);
            awnd.focus();
        }


        function ResearchScriptAttach() {
            t0 = escape(document.serviceform.xml_referral1.value);
            t1 = escape(document.serviceform.xml_referral2.value);

            awnd = rs('att', '<%= request.getContextPath() %>/billing/CA/<rewrite:reWrite jspPage="billingReferralCodeSearch.jsp"/>?name=' + t0 + '&name1=' + t1 + '&search=', 600, 600, 1);
            awnd.focus();
        }

        function POP(n, h, v) {
            window.open(n, 'OSCAR', 'toolbar=no,location=no,directories=no,status=yes,menubar=no,resizable=yes,copyhistory=no,scrollbars=yes,width=' + h + ',height=' + v + ',top=100,left=200');
        }


        function grabEnter(event, callb) {
            if ((window.event && window.event.keyCode == 13) || (event && event.which == 13)) {
                eval(callb);
                return false;
            }
        }

        function reloadPage(init) {  //reloads the window if Nav4 resized
            if (init === true) with (navigator) {
                if ((appName == "Netscape") && (parseInt(appVersion) == 4)) {
                    document.pgW = innerWidth;
                    document.pgH = innerHeight;
                    onresize = reloadPage;
                }
            }
            else if (innerWidth != document.pgW || innerHeight != document.pgH) location.reload();
        }

        reloadPage(true);


        function findObj(n, d) { //v4.0
            var p, i, x;
            if (!d) d = document;
            if ((p = n.indexOf("?")) > 0 && parent.frames.length) {
                d = parent.frames[n.substring(p + 1)].document;
                n = n.substring(0, p);
            }
            if (!(x = d[n]) && d.all) x = d.all[n];
            for (i = 0; !x && i < d.forms.length; i++) x = d.forms[i][n];
            for (i = 0; !x && d.layers && i < d.layers.length; i++) x = findObj(n, d.layers[i].document);
            if (!x && document.getElementById) x = document.getElementById(n);
            return x;
        }

        function getOffsetLeft(el) {
            var ol = el.offsetLeft;
            while ((el = el.offsetParent) != null) {
                ol += el.offsetLeft;
            }
            return ol;
        }

        function getOffsetTop(el) {
            var ot = el.offsetTop;
            while ((el = el.offsetParent) != null) {
                ot += el.offsetTop;
            }
            return ot;
        }

        var objPopup = null;
        var shim = null;

        function formPopup(event, objectId) {
            objPopTrig = document.getElementById(event);
            objPopup = document.getElementById(objectId);
            shim = document.getElementById('DivShim');
            xPos = getOffsetLeft(objPopTrig);
            yPos = getOffsetTop(objPopTrig) + objPopTrig.offsetHeight;

            objPopup.style.zIndex = 9999;

            shim.style.width = objPopup.offsetWidth + 2;
            shim.style.height = objPopup.offsetHeight;
            shim.style.top = objPopup.style.top;
            shim.style.left = objPopup.style.left;
            shim.style.zIndex = objPopup.style.zIndex - 1;
            shim.style.display = "block";
            objPopup.style.display = "block";
            shim.style.visibility = 'visible';
            objPopup.style.visibility = 'visible';
        }

        function formPopupHide() {
            objPopup.style.visibility = 'hidden';
            shim.style.visibility = 'hidden';
            objPopup = null;
            shim = null;
        }

        function addCodeToList(svcCode) {
            if (myform.xml_other1.value == "") {
                myform.xml_other1.value = svcCode;
                return true;
            } else if (myform.xml_other2.value == "") {
                myform.xml_other2.value = svcCode;
                return true;
            } else if (myform.xml_other3.value == "") {
                myform.xml_other3.value = svcCode;
                return true;
            }
        }

        function setCodeToChecked(svcCode) {
            myform = document.forms[0];
            var codeset = false;
            for (var i = 0; i < myform.service.length; i++) {
                if (myform.service[i].value == svcCode) {
                    var wasAbleToAddCode = addCodeToList(svcCode);
                    if (wasAbleToAddCode) {
                        myform.service[i].checked = true;
                        codeset = true;
                    }
                    return;
                }
            }

            if (codeEntered(svcCode) == false) {
                if (myform.xml_other1.value == "") {
                    myform.xml_other1.value = svcCode;
                    return;
                    //myform.xml_diagnostic_detail1.value = "";
                } else if (myform.xml_other2.value === "") {
                    myform.xml_other2.value = svcCode;
                    //myform.xml_diagnostic_detail2.value = "";
                } else if (myform.xml_other3.value === "") {
                    myform.xml_other3.value = svcCode;
                    //myform.xml_diagnostic_detail3.value = "";
                }
            }
        }

        function checkifSet(icd9, feeitem, extrafeeitem) {
            myform = document.forms[0];
            oscarLog("icd9 " + icd9 + " ,feeitem " + feeitem + " " + codeEntered(feeitem) + " extrafeeitem " + extrafeeitem + " " + codeEntered(extrafeeitem));
            if (myform.xml_diagnostic_detail1.value == "") {
                myform.xml_diagnostic_detail1.value = icd9;
            }
            setCodeToChecked(feeitem);
            oscarLog("feeitem did put " + codeEntered(feeitem));
            setCodeToChecked(extrafeeitem);

            oscarLog("extra feeitem did put" + codeEntered(extrafeeitem));
        }

        jQuery(document).ready(function (jQuery) {

            jQuery("#bcBillingForm").attr('autocomplete', 'off');

            jQuery(document).on('click', ".referral-doctor", function () {

                mRecordRefDocNum = jQuery(this).attr('data-num');
                mRecordRefDoc = jQuery(this).attr('data-doc');

                one = jQuery('[name="xml_refer1"]');
                two = jQuery('[name="xml_refer2"]');

                if (one.val().length > 0) {
                    two.val(mRecordRefDocNum);
                    two.attr("title", mRecordRefDoc);
                } else {
                    one.val(mRecordRefDocNum);
                    one.attr("title", mRecordRefDoc);
                }
            });

            jQuery(document).on('change', '#xml_provider', function () {
                let url = '${pageContext.servletContext.contextPath}/billing.do?demographic_no=' + '<%=Encode.forUriComponent(bean.getPatientNo())%>' + '&appointment_no=' + '<%=Encode.forUriComponent(bean.getApptNo())%>' + '&apptProvider_no=' + '<%=Encode.forUriComponent(bean.getApptProviderNo())%>' + '&demographic_name=' + '<%=URLEncoder.encode(bean.getPatientName(), StandardCharsets.UTF_8)%>' + '&billRegion=BC&xml_provider=' + this.value;

                jQuery("#billingPatientInfoWrapper").load(url + " #billingPatientInfo", function () {
                    // re-bind all the javascript
                    jQuery("#selectBillingForm").trigger('change');
                    getDxInformation();
                    bindDxJSONEvents();
                })
            });

            /* for setting times */
            jQuery(function () {
                jQuery('.datetimepicker').datetimepicker({
                    format: 'HH:mm'
                });
            });

            /* New billing form selection method*/
            jQuery(document).on('change', "#selectBillingForm", function () {
                let selectedValue = this.value;
                let url = ctx + '/billing.do?demographic_no=' + '<%=Encode.forUriComponent(bean.getPatientNo())%>' + '&appointment_no=' + '<%=Encode.forUriComponent(bean.getApptNo())%>' + '&apptProvider_no=' + '<%=Encode.forUriComponent(bean.getApptProviderNo())%>' + '&demographic_name=' + '<%=URLEncoder.encode(bean.getPatientName(), StandardCharsets.UTF_8)%>' + '&xml_provider=none&billRegion=BC&billForm=' + selectedValue;
                jQuery("#billingFormTableWrapper").load(url + " #billingFormTable", function () {
                    // if the selected billing type is private, then change the billing type to private
                    if (selectedValue === 'PRI') {
                        jQuery("#xml_billtype").val('Pri');
                    }
                    getDxInformation();
                    bindDxJSONEvents();
                });
            });

            jQuery(document).on('blur', "#serviceStartTime", function () {
                var time = this.value;
                if (time) {
                    var hour = time.split(":")[0];
                    var minute = time.split(":")[1];
                    var endtime = jQuery("#serviceEndTime").val();
                    if (endtime) {
                        timeCompare(hour + minute, endtime.replace(":", ""));
                    }
                    jQuery("#xml_starttime_hr").val(hour);
                    jQuery("#xml_starttime_min").val(minute);
                }
            })


            jQuery(document).on('blur', "#serviceEndTime", function () {
                var time = this.value;

                if (time) {
                    var hour = time.split(":")[0];
                    var minute = time.split(":")[1];
                    var starttime = jQuery("#serviceStartTime").val();
                    timeCompare(starttime.replace(":", ""), hour + minute);
                    jQuery("#xml_endtime_hr").val(hour);
                    jQuery("#xml_endtime_min").val(minute);
                }
            })

            function timeCompare(start, end) {
                if (!start || start > end) {
                    alert("Warning: the start time is greater than the end time.");
                }
            }

            /*
             * All form validation code following
             *
             */
            jQuery("#bcBillingForm").validate({

                rules: {
                    /*
                     * Service date absolutely required
                     */
                    xml_appointment_date: {
                        required: function (element) {
                            return element.value.length === 0;
                        }
                    },
                    /*
                     * Is providers selected
                     */
                    xml_provider: {
                        required: function (element) {
                            return element.value.length === 0;
                        }
                    },

                    /*
                     * Validate all 3 service codes and
                     * unit values
                     */
                    xml_other1: {
                        required: true
                    },
                    xml_other1_unit: {
                        number: true
                    },
                    xml_other2_unit: {
                        number: true
                    },
                    xml_other3_unit: {
                        number: true
                    },
                    WCBid: {
                        required: function (element) {
                            return document.BillingCreateBillingForm.xml_billtype.value == "WCB";
                        }
                    },
                    /*
                     * Validate all 3 Diagnostic codes.
                     */
                    xml_diagnostic_detail1: {
                        required: function (element) {
                            return document.BillingCreateBillingForm.xml_billtype.value != "Pri";
                        },
                        remote: {
                            url: ctx + "\/dxCodeSearchJSON.do",
                            type: "post",
                            dataType: "json",
                            data: {
                                keyword: function (element) {
                                    return jQuery("input[name='xml_diagnostic_detail1']").val();
                                },
                                method: "validateCode",
                                codeSystem: function () {
                                    return (jQuery('#codingSystem option:selected, input#codingSystem').val()).toLowerCase();
                                }
                            },
                            dataFilter: function (response) {
                                var data = JSON.parse(response);
                                return data.dxvalid;
                            }
                        }
                    },
                    xml_diagnostic_detail2: {
                        required: false,
                        remote: {
                            url: ctx + "\/dxCodeSearchJSON.do",
                            type: "post",
                            dataType: "json",
                            data: {
                                keyword: function (element) {
                                    return jQuery("input[name='xml_diagnostic_detail2']").val();
                                },
                                method: "validateCode",
                                codeSystem: function () {
                                    return (jQuery('#codingSystem option:selected, input#codingSystem').val()).toLowerCase();
                                }
                            },
                            dataFilter: function (response) {
                                var data = JSON.parse(response);
                                return data.dxvalid;
                            }
                        }
                    },
                    xml_diagnostic_detail3: {
                        required: false,
                        remote: {
                            url: ctx + "\/dxCodeSearchJSON.do",
                            type: "post",
                            dataType: "json",
                            data: {
                                keyword: function (element) {
                                    return jQuery("input[name='xml_diagnostic_detail3']").val();
                                },
                                method: "validateCode",
                                codeSystem: function () {
                                    return (jQuery('#codingSystem option:selected, input#codingSystem').val()).toLowerCase();
                                }
                            },
                            dataFilter: function (response) {
                                var data = JSON.parse(response);
                                return data.dxvalid;
                            }
                        }
                    }
                },

                /*
                 * Error messages to return on each validation
                 */
                messages: {
                    xml_appointment_date: {
                        required: "Service date is required"
                    },
                    xml_diagnostic_detail1: {
                        required: "At least 1 diagnostic code is required",
                        remote: "Invalid diagnostic code 1"
                    },
                    xml_diagnostic_detail2: {
                        remote: "Invalid diagnostic code 2"
                    },
                    xml_diagnostic_detail3: {
                        remote: "Invalid diagnostic code 3"
                    },
                    xml_other1_unit: "Service code units must be numeric",
                    xml_other2_unit: "Service code units must be numeric",
                    xml_other3_unit: "Service code units must be numeric",
                    xml_provider: "Select a billing physician",
                    xml_other1: "At least 1 service code is required",
                    WCBid: "A WCB Form must be selected."
                },

                /*
                 * Error highlight and message methods
                 */
                highlight: function (element) {
                    jQuery(element).addClass('has-error');
                },
                unhighlight: function (element) {
                    jQuery(element).removeClass('has-error');
                },
                submitHandler: function (form) {
                    toggleWCB();
                    form.submit();
                },
                onkeyup: false,
                onfocusout: false,
                focusInvalid: true,
                errorElement: 'div',
                errorClass: 'alert alert-danger',
                errorLabelContainer: '#bcBillingError',

            })
            /*
             * End form validation code
             *
             */


            /*
            *  clears out the dx code list everytime
            *  the code system is changed.
            */
            jQuery(document).on('change', "#codingSystem", function () {
                jQuery("#jsonDxSearchInput-1").val("");
                jQuery("#jsonDxSearchInput-2").val("");
                jQuery("#jsonDxSearchInput-3").val("");
            })

        }) //<!-- End Document Ready //-->
    </script>
</head>

<body style="background-color:#FFFFFF;" onLoad="CheckType();correspondenceNote();">
<div id="page-header">
    <div class="icon-container">
        <img alt="OSCAR EMR" src="${pageContext.servletContext.contextPath}/images/oscar_logo_small.png" width="19px">
    </div>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.bc.title"/></h3>
    <span class="badge badge-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.patient"/></span>
    <label class="label-text"><%=Encode.forHtmlContent(demo.getLastName())%>
        , <%=Encode.forHtmlContent(demo.getFirstName())%>
    </label>

    <span class="badge badge-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.patient.age"/></span>
    <label class="label-text"><%=demo.getAge()%>
    </label>

    <%-- 	Keep until confirmed not needed.

            <span class="badge badge-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.patient.status"/></span>
            <strong class="label-text"><%=demo.getPatientStatus()%></label>

      <span class="badge badge-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.patient.roster"/></span>
            <label><%=demo.getRosterStatus()%></label>
    --%>
    <span class="badge badge-primary" title="Most Responsible Provider"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.provider.assignedProvider"/></span>
    <label class="label-text">
        <c:choose>
            <c:when test="<%= demo.getProviderNo() != null && ! demo.getProviderNo().trim().isEmpty() %>">
                <%=billform.getProviderName(demo.getProviderNo())%>
            </c:when>
            <c:otherwise>
                Unknown
            </c:otherwise>
        </c:choose>
    </label>

    <security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="x">
        <button type="button" class="btn btn-link" title="View this patient's Electronic Chart"
                onclick="popup(710, 1024,'${pageContext.servletContext.contextPath}/oscarEncounter/IncomingEncounter.do?providerNo=<%=loggedInInfo.getLoggedInProviderNo()%>&appointmentNo=&demographicNo=<%=demo.getDemographicNo()%>&curProviderNo=<%=loggedInInfo.getLoggedInProviderNo()%>&reason=&encType=face+to+face+encounter+with+client&userName=&curDate=<%= new Date().toString() %>&appointmentDate=&startTime=&status=&apptProvider_no=&providerview=<%=loggedInInfo.getLoggedInProviderNo()%>','encounter', 12556);return false;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.patient.encounter"/>
        </button>
    </security:oscarSec>

    <button type="button" class="btn btn-link" title="View previous invoices for this patient"
            onclick="popup(800, 1000, 'billStatus.jsp?lastName=<%=demo.getLastName()%>&firstName=<%=demo.getFirstName()%>&filterPatient=true&demographicNo=<%=demo.getDemographicNo()%>','InvoiceList');return false;">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInvoiceList"/>
    </button>

</div>
<div class="wrapper">

    <div class="container-fluid">
        <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>

        <!-- end error row -->

        <%
            List<String> wcbneeds = (List) request.getAttribute("WCBFormNeeds");
            if (wcbneeds != null) {%>
        <div>
            WCB Form needs:
            <ul>
                <%for (String s : wcbneeds) { %>
                <li><fmt:setBundle basename="oscarResources"/><fmt:message key="<%=s%>"/></li>
                <%}%>
            </ul>
        </div>
        <%}%>

        <form style="bcBillingForm" class="form-inline"
              action="${pageContext.request.contextPath}/billing/CA/BC/CreateBilling.do" onsubmit="toggleWCB();">

            <input autocomplete="false" name="hidden" type="text" style="display:none;">
            <input type="hidden" name="fromBilling" value="">

            <%
                // set up this form
                BillingCreateBillingForm thisForm = (BillingCreateBillingForm) request.getSession().getAttribute("BillingCreateBillingForm");

                if (thisForm != null) {

                    thisForm.setXml_provider(bean.getBillingProvider());
                    String visitLocation = OscarProperties.getInstance().getProperty("visitlocation");

                    // sometimes the visit locations tend to contain punctuation that is offending to struts.
                    if (visitLocation != null && !visitLocation.isEmpty()) {
                        // split the code and description, then reassemble without punctuation
                        String visitLocationCode = visitLocation.split("\\|")[0];
                        String visitLocationDescription = visitLocation.split("\\|")[1];
                        visitLocationDescription = visitLocationDescription.replaceAll("[^A-Za-z]+", "").toUpperCase();
                        visitLocation = visitLocationCode + "|" + visitLocationDescription;

                        thisForm.setXml_location(visitLocation);
                    }

                    thisForm.setXml_visittype(bean.getVisitType());

                    if (OscarProperties.getInstance().getProperty("BC_DEFAULT_ALT_BILLING") != null && OscarProperties.getInstance().getProperty("BC_DEFAULT_ALT_BILLING").equalsIgnoreCase("YES")) {
                        thisForm.setXml_encounter("8");
                    }

                    String serviceDate = bean.getServiceDate();
                    if (serviceDate == null || serviceDate.isEmpty()) {
                        serviceDate = bean.getApptDate();
                    }
                    thisForm.setXml_appointment_date(serviceDate);

                    if (bean.getBillType() != null) {
                        thisForm.setXml_billtype(bean.getBillType());
                    } else if (request.getParameter("billType") != null) {
                        thisForm.setXml_billtype(request.getParameter("billType"));
                    }

                    if (demo.getVer() != null && demo.getVer().equals("66")) {
                        thisForm.setDependent("66");
                    }

                    thisForm.setCorrespondenceCode(bean.getCorrespondenceCode());

                    thisForm.setRefertype1(userReferralPref);
                    thisForm.setRefertype2(userReferralPref);

                    /*
                     * whether to auto populate referring doctor information into
                     * every billing.
                     */
                    boolean autoPopulateRefer = false;
                    if (StringUtils.isBlank(thisForm.getXml_refer1())) {
                        // Check to see if the global property for auto_populate_refer is enabled, and if so set that here
                        List<Property> globalAutoPopulateProperty = propertyDao.findGlobalByName("auto_populate_refer");
                        if (!globalAutoPopulateProperty.isEmpty()) {
                            for (Property p : globalAutoPopulateProperty) {
                                if (p.getValue().equalsIgnoreCase("true")) {
                                    autoPopulateRefer = true;
                                    break;
                                }
                            }
                        }
                        // If the global setting is not enabled, check the per-providers preference
                        if (!autoPopulateRefer) {
                            autoPopulateRefer = propertyDao.isActiveBooleanProperty(Property.PROPERTY_KEY.auto_populate_refer, targetProvider);
                        }
                        if (autoPopulateRefer) {
                            thisForm.setXml_refer1(mRecRefDoctorNum);
                        }
                    }
                }
            %>
            <table>
                <tr>
                    <td>
                        <div id="billingPatientInfoWrapper">
                            <table class="tool-bar" id="billingPatientInfo">
                                <tr>
                                    <td>
                                        <div class="form-group">

                                            <label for="selectBillingForm"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingform"/></label>

                                            <select class="form-control" id="selectBillingForm">
                                                <% for (int i = 0; i < billformlist.length; i++) { %>
                                                <option <% if (bean.getBillForm().equalsIgnoreCase(billformlist[i].getFormCode())) {%>
                                                        selected
                                                        <% } %>
                                                        value="<%=Encode.forHtmlAttribute(billformlist[i].getFormCode())%>">
                                                    <%= Encode.forHtmlContent(billformlist[i].getDescription()) %>
                                                </option>
                                                <%} %>
                                            </select>

                                        </div>

                                    </td>

                                    <td>
                                        <div class="form-group">

                                            <label for="xml_provider"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.provider.billProvider"/></label>
                                            <select id="xml_provider" class="form-control"
                                                         name="xml_provider">

                                                <option value="">
                                                    Select Provider
                                                </option>
                                                <% for (int j = 0; j < billphysician.length; j++) { %>
                                                <option
                                                        value="<%=billphysician[j].getProviderNo()%>"><%=Encode.forHtmlContent(billphysician[j].getProviderName())%>
                                                </option>
                                                <%} %>
                                            </select>


                                        </div>
                                    </td>

                                    <td>
                                        <div class="form-group">

                                            <label for="xml_billtype"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingtype"/></label>
                                            <select class="form-control" id="xml_billtype"
                                                         property="xml_billtype" onchange="CheckType();gotoPrivate();">
                                                <option value="MSP">Bill MSP</option>
                                                <option value="WCB">Bill WCB</option>
                                                <option value="ICBC">Bill ICBC</option>
                                                <option value="Pri">Private</option>
                                                <option value="DONOTBILL">Do Not Bill</option>
                                            </select>

                                        </div>

                                    </td>
                                    <td>
                                        <div class="form-group">

                                            <label for="xml_location">Clarification Code</label>
                                            <select class="form-control" id="xml_location"
                                                         name="xml_location">
                                                <%
                                                    for (int i = 0; i < billlocation.length; i++) {
                                                        String locationDescription = billlocation[i].getBillingLocation() + "|" + billlocation[i].getDescription().replaceAll("[^A-Za-z]+", "").toUpperCase();
                                                        ;
                                                %>
                                                <option
                                                        value="<%=Encode.forHtmlAttribute(locationDescription)%>"><%=Encode.forHtmlContent(billlocation[i].getDescription())%>
                                                </option>
                                                <%} %>
                                            </select>
                                        </div>

                                    </td>

                                    <td>
                                        <div class="form-group">

                                            <label for="xml_visittype">Service Location</label>
                                            <select class="form-control" id="xml_visittype"
                                                         name="xml_visittype">
                                                <%
                                                    for (BillingFormData.BillingVisit billingVisit : billvisit) {
                                                %>
                                                <option
                                                        value="<%=Encode.forHtmlAttribute(billingVisit.getVisitType())%>">
                                                    <%=Encode.forHtmlContent(billingVisit.getDescription())%>
                                                </option>
                                                <%}%>
                                            </select>

                                        </div>

                                    </td>
                                </tr>
                            </table>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <table class="tool-bar">
                            <tr>
                                <td>
                                    <div class="form-group">

                                        <a href="javascript:void(0)" id="hlSDate">
                                            <label for="xml_appointment_date"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.servicedate"/></label>
                                        </a>
                                        <input type="text" style="min-width:100px;" class="form-control"
                                                   name="xml_appointment_date" size="10" readonly="true"
                                                   id="xml_appointment_date"/>
                                    </div>

                                </td>
                                <td>
                                    <div class="form-group">

                                        <a href="javascript:void(0)" id="serviceToDate">
                                            <label for="service_to_date">To Date</label>
                                        </a>

                                        <input type="text" class="form-control" name="service_to_date" size="2"
                                                   maxlength="2" id="service_to_date"/>
                                    </div>

                                </td>
                                <td>
                                    <div class="form-group">

                                        <label for="afterHours">After Hours</label>
                                        <select class="form-control" name="afterHours"
                                                     id="afterHours">
                                            <option value="0">No</option>
                                            <option value="E">Evening</option>
                                            <option value="N">Night</option>
                                            <option value="W">Weekend</option>
                                        </select>
                                    </div>

                                </td>
                                <td title="(HHMM 24hr):">
                                    <div class="form-group">

                                        <label for="timeCall">Time Call</label>
                                        <input type="text" class="form-control" name="timeCall" id="timeCall"/>

                                    </div>
                                </td>

                                <td>

                                    <div class="form-group">
                                        <label for="serviceStartTime">Start</label>
                                        <div class='input-group date datetimepicker'>

                                            <input type='text' id="serviceStartTime" class="form-control"/>
                                            <input type=hidden id="xml_starttime_hr" name="xml_starttime_hr"/>
                                            <input type=hidden id="xml_starttime_min" name="xml_starttime_min"/>
                                            <span class="input-group-addon">
		                        <span class="glyphicon glyphicon-time"></span>
		                    </span>
                                        </div>
                                    </div>
                                <td>

                                    <div class="form-group">
                                        <label for="serviceEndTime">End</label>
                                        <div class='input-group date datetimepicker'>
                                            <input type='text' id="serviceEndTime" class="form-control"/>
                                            <input type=hidden id="xml_endtime_hr" name="xml_endtime_hr"/>
                                            <input type=hidden id="xml_endtime_min" name="xml_endtime_min"/>
                                            <span class="input-group-addon">
		                        <span class="glyphicon glyphicon-time"></span>
		                    </span>
                                        </div>
                                    </div>

                                </td>

                                <td>
                                    <div class="form-group">

                                        <label for="dependent">Dependent</label>
                                        <select class="form-control" name="dependent" id="dependent">
                                            <option value="00">No</option>
                                            <option value="66">Yes</option>
                                        </select>
                                    </div>

                                </td>
                                <td title="Submission Code">
                                    <div class="form-group">

                                        <label for="submissionCode">Sub Code</label>
                                        <select class="form-control" name="submissionCode"
                                                     id="submissionCode">
                                            <option value="0">O - Normal</option>
                                            <option value="D">D - Duplicate</option>
                                            <option value="E">E - Debit</option>
                                            <option value="C">C - Subscriber Coverage</option>
                                            <option value="R">R - Resubmitted</option>
                                            <option value="I">I - ICBC Claim > 90 Days</option>
                                            <option value="A">A - Requested Preapproval</option>
                                            <option value="W">W - WCB Rejected Claim</option>
                                            <option
                                                    value="X">X - Resubmitting Refused / Partially Paid Claim</option>
                                        </select>
                                    </div>

                                </td>
                                <td>
                                    <div class="form-group">

                                        <label for="xml_encounter">Payment Method</label>
                                        <%
                                            ArrayList<PaymentType> types = billform.getPaymentTypes();
                                            if ("Pri".equalsIgnoreCase(thisForm.getXml_billtype())) {
                                                for (int i = 0; i < types.size(); i++) {
                                                    PaymentType item = types.get(i);
                                                    if (item.getId().equals("6") || item.getId().equals("8")) {
                                                        types.remove(i);
                                                        break;
                                                    }
                                                }
                                            } else {
                                                for (int i = 0; i < types.size(); i++) {
                                                    PaymentType item = types.get(i);
                                                    if (!item.getId().equals("6") && !item.getId().equals("8")) {
                                                        types.remove(i);
                                                        i = i - 1;
                                                    }
                                                }
                                            }
                                            request.setAttribute("paymentMethodList", types);
                                            request.setAttribute("defaultPaymentMethod", OscarProperties.getInstance().getProperty("DEFAULT_PAYMENT_METHOD", ""));
                                        %>
                                        <select class="form-control" id="xml_encounter" name="xml_encounter">
                                            <c:forEach items="${paymentMethodList}" var="paymentMethod">
                                                <option value="${paymentMethod.id}" ${ defaultPaymentMethod eq paymentMethod.id ? 'selected' : ''}>${paymentMethod.paymentType}</option>
                                            </c:forEach>
                                        </select>
                                    </div>

                                </td>
                                <td>

                                    <div class="form-group">
                                        <label for="facilityNum">BCP Facility</label>
                                        <input type="text" class="form-control" name="facilityNum"
                                                   id="facilityNum" size="5" maxlength="5"/>

                                    </div>
                                </td>

                                <!-- sub facility not currently used. But it does work. Unhide to use -->
                                <td style="display: none;">

                                    <div class="form-group">
                                        <label for="facilitySubNum">Sub Facility</label>
                                        <input type="text" class="form-control" name="facilitySubNum"
                                                   id="facilitySubNum" size="5" maxlength="5"/>

                                    </div>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td>
                        <div style="display: none;">
                            <table class="tool-bar">
                                <tr>
                                    <td>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.admissiondate"/>
                                        <div class="form-group">
                                            <div class='input-group text'>

                                                <input type="text" name="xml_vdate" readonly="true" value="" size="10"
                                                           id="xml_vdate"/>
                                                <a id="hlADate">
                                                    <img title="Calendar"
                                                         src="${pageContext.servletContext.contextPath}/images/cal.gif"
                                                         alt="Calendar" border="0"/>
                                                </a>
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                            <script type="text/javascript">
                                Calendar.setup({
                                    inputField: "xml_appointment_date",
                                    ifFormat: "%Y-%m-%d",
                                    showsTime: false,
                                    button: "hlSDate",
                                    singleClick: true,
                                    step: 1
                                });
                                //Calendar.setup({inputField:"xml_appointment_date", ifFormat:""%d/%m/%Y",",button:"hlSDate", align:"Bl", singleClick:true});
                                Calendar.setup({
                                    inputField: "xml_vdate",
                                    ifFormat: "%Y-%m-%d",
                                    showsTime: false,
                                    button: "hlADate",
                                    singleClick: true,
                                    step: 1
                                });
                                Calendar.setup({
                                    inputField: "service_to_date",
                                    ifFormat: "%d",
                                    button: "serviceToDate",
                                    align: "Bl",
                                    singleClick: true
                                });
                            </script>
                        </div>


                        <div id="ICBC">
                            <table class="tool-bar">
                                <tr>
                                    <td>

                                        <div class='form-group'>
                                            <label for="icbc_claim_no">ICBC Claim No</label>
                                            <input type="text" class="form-control" name="icbc_claim_no"
                                                       id="icbc_claim_no" maxlength="8"/>
                                        </div>
                                        <div class='form-group'>
                                            <label for="mva_claim_code">MVA?</label>
                                            <select class="form-control" name="mva_claim_code"
                                                         id="mva_claim_code">
                                                <option value="N">No</option>
                                                <option value="Y">Yes</option>
                                            </select>

                                        </div>

                                    </td>

                                </tr>
                            </table>
                        </div>

                    </td>
                </tr>
                <tr>
                    <td style="display:flex;gap:3px;">

                        <div class="tool-table table-responsive" style="width:100%;flex-basis: 25%;">
                            <table class="table table-condensed table-borderless">
                                <tr>
                                    <td>
                                        <table class="table table-condensed table-borderless">
                                            <tr>
                                                <td>
                                                    <label>
                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.referral.doctor"/>
                                                    </label>
                                                </td>
                                                <td>
                                                    <label>
                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.referral.type"/>
                                                    </label>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <div class="input-group">
                                                        <input type="text" class="form-control" name="xml_refer1"
                                                                   onkeypress="return grabEnter(event,'ReferralScriptAttach1()')"/>
                                                        <span class="input-group-btn">
		                     	<button type="button" class="btn btn-primary"
                                        onclick="ReferralScriptAttach('xml_refer1')">
	                            	<span class="glyphicon glyphicon-search"></span>
	                          	</button>
                          	</span>
                                                    </div>
                                                </td>
                                                <td>
                                                    <select class="form-control" name="refertype1">
                                                        <option value="">Select Type</option>
                                                        <option value="T">Refer To</option>
                                                        <option value="B">Refer By</option>
                                                    </select>
                                                </td>
                                            </tr>

                                            <tr>
                                                <td>
                                                    <div class="input-group">
                                                        <input type="text" class="form-control" name="xml_refer2"
                                                                   onkeypress="return grabEnter(event,'ReferralScriptAttach2()')"/>
                                                        <span class="input-group-btn">
			                     	<button type="button" class="btn btn-primary"
                                            onclick="ReferralScriptAttach('xml_refer2')">
		                            	<span class="glyphicon glyphicon-search"></span>
		                          	</button>
	                          	</span>
                                                    </div>
                                                </td>
                                                <td>
                                                    <select class="form-control" name="refertype2">
                                                        <option value="">Select Type</option>
                                                        <option value="T">Refer To</option>
                                                        <option value="B">Refer By</option>
                                                    </select>
                                                </td>
                                            </tr>

                                        </table>
                                    </td>

                                </tr>
                                <tr>
                                    <td>

                                        <table class="table table-condensed table-borderless"
                                               style="max-height: 100px;display: block;overflow-y: scroll">
                                            <tr>
                                                <td>

                                                    <table class="table table-condensed table-borderless">
                                                        <tr>
                                                            <td colspan="2">Recent Referral Doctors</td>
                                                        </tr>
                                                        <%
                                                            String bgColor = "#fff";
                                                            String rProvider = "";

                                                            if (recentReferralDoctorList.size() > 0) {
                                                                for (String r : recentReferralDoctorList) {
                                                                    rProvider = billingReferralDao.getReferralDocName(r);
                                                        %>
                                                        <tr bgcolor="<%=bgColor%>">
                                                            <td>
                                                                <a href="javascript:void(0)" class="referral-doctor"
                                                                   data-num="<%=r%>" data-doc="<%=rProvider%>"><%=r%>
                                                                </a>
                                                            </td>
                                                            <td><%=rProvider%>
                                                            </td>
                                                        </tr>
                                                        <%
                                                                if (bgColor == "#fff") {
                                                                    bgColor = "#ccc";
                                                                } else {
                                                                    bgColor = "#fff";
                                                                }

                                                            }
                                                        } else {
                                                        %>
                                                        <tr>
                                                            <td></td>
                                                            <td>
                                                                none
                                                            </td>
                                                        </tr>
                                                        <%
                                                            }
                                                        %>
                                                    </table>

                                                </td>
                                                <td>

                                                    <table class="table table-condensed table-borderless">
                                                        <tr>
                                                            <td style="border-top:none;" colspan="2">Referral Doctor on
                                                                Master Record
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <a href="javascript:void(0)"
                                                                   title="Populate referral doctor from master record"
                                                                   class="referral-doctor"
                                                                   data-num="<%=mRecRefDoctorNum%>"
                                                                   data-doc="<%=mRecRefDoctor%>"><%=mRecRefDoctorNum%>
                                                                </a>
                                                            </td>
                                                            <td><%=mRecRefDoctor%>
                                                            </td>
                                                        </tr>
                                                    </table>

                                                </td>
                                            </tr>
                                        </table>

                                    </td>
                                </tr>
                            </table>


                        </div>
                        <div class="tool-table table-responsive" style="width:100%;flex-basis: 25%;">

                            <table class="table table-condensed table-borderless">
                                <tr>
                                    <td>
                                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.otherservice"/></label>
                                    </td>
                                    <td>
                                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.unit"/></label>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="input-group">
 							<span class="input-group-addon">
								1
							</span>
                                            <input type="text" class="form-control" name="xml_other1"
                                                       onblur="checkSelectedCodes()"
                                                       onkeypress="return grabEnter(event,'OtherScriptAttach()')"/>
                                            <span class="input-group-btn">
		                     	<button type="button" class="btn btn-primary" title="Search code"
                                        onclick="OtherScriptAttach('xml_other1')">
	                            	<span class="glyphicon glyphicon-search"></span>
	                          	</button>
                          	</span>
                                        </div>
                                    </td>
                                    <td style="width:40%;">
                                        <div class="input-group">
                                            <input type="text" class="form-control" name="xml_other1_unit" size="6"
                                                       maxlength="6" id="xml_other1_unit"/>
                                            <span class="input-group-btn">
                            	<button type="button" class="btn btn-primary" value=".5"
                                        onClick="$('xml_other1_unit').value = '0.5'">.5</button>
                            </span>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="input-group">
 							<span class="input-group-addon">
								2
							</span>
                                            <input type="text" class="form-control" name="xml_other2"
                                                       onblur="checkSelectedCodes()"
                                                       onkeypress="return grabEnter(event,'OtherScriptAttach()')"/>
                                            <span class="input-group-btn">
		                     	<button type="button" class="btn btn-primary" title="Search code"
                                        onclick="OtherScriptAttach('xml_other2')">
	                            	<span class="glyphicon glyphicon-search"></span>
	                          	</button>
                          	</span>
                                        </div>
                                    </td>
                                    <td>
                                        <div class="input-group">
                                            <input type="text" class="form-control" name="xml_other2_unit" size="6"
                                                       maxlength="6" id="xml_other2_unit"/>
                                            <span class="input-group-btn">
                             	<button type="button" class="btn btn-primary" value=".5"
                                        onClick="$('xml_other2_unit').value = '0.5'">.5</button>
                             </span>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="input-group">
 							<span class="input-group-addon">
								3
							</span>
                                            <input type="text" class="form-control" name="xml_other3"
                                                       onblur="checkSelectedCodes()"
                                                       onkeypress="return grabEnter(event,'OtherScriptAttach()')"/>
                                            <span class="input-group-btn">
		                     	<button type="button" class="btn btn-primary" title="Search code"
                                        onclick="OtherScriptAttach('xml_other3')">
	                            	<span class="glyphicon glyphicon-search"></span>
	                          	</button>
                          	</span>
                                        </div>
                                    </td>
                                    <td>
                                        <div class="input-group">
                                            <input type="text" class="form-control" name="xml_other3_unit"
                                                       id="xml_other3_unit"/>
                                            <span class="input-group-btn">
                            	<button type="button" class="btn btn-primary" value=".5"
                                        onClick="$('xml_other3_unit').value = '0.5'">.5</button>
                            </span>
                                        </div>
                                    </td>
                                </tr>
                                <!-- <tr>
                                <td></td>
                                  <td>
                                    <button class="btn btn-info pull-right btn-xs" onclick="javascript:OtherScriptAttach()">
                                        Code Search
                                    </button>
                                  </td>
                                </tr> -->
                            </table>


                            <!-- ONSCREEN DX CODE DISPLAY -->
                        </div>
                        <div class="tool-table table-responsive" style="width:100%;flex-basis: 15%;">
                            <table class="table table-condensed table-borderless">
                                <tr>
                                    <td style="width:60%">
                                        <div class="input-group">

                                                <%--
                                                    If the list of coding systems includes ICD10, then offer a list of options
                                                    including the specific MSP Dx table.
                                                    If the user wants a coding system but does not want the MSP table then
                                                    the DISABLE_MSP_DX_SYSTEM switch can be set in OSCAR properties. When this is
                                                    disabled the user will be presented with the other selected tables.
                                                 --%>
                                            <c:set scope="page" var="icd10" value="false"/>
                                                    <c:forEach var="codeSystem" items="${dxCodeSystemList.codingSystems}">
                                                        <c:if test="${codeSystem == 'icd10'}">
                                                            <c:set var="isIcd10" scope="page" value="true"/>
                                                        </c:if>
                                                    </c:forEach>
                                            <c:choose>
                                                <c:when test="${ isIcd10 }">
										<span class="input-group-addon">
											<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.diagnostic.code"/>
										</span>
                                                    <select style="min-width: 70px;" class="form-control"
                                                            name="dxCodeSystem" id="codingSystem">
                                                        <oscar:oscarPropertiesCheck value="false"
                                                                                    property="DISABLE_MSP_DX_SYSTEM">
                                                            <option value="msp" selected>MSP Dx</option>
                                                        </oscar:oscarPropertiesCheck>
                                                        <c:forEach var="codeSystem" items="${dxCodeSystemList.codingSystems}">
                                                            <option value="${codeSystem}">
                                                                <c:out value="${codeSystem}"/>
                                                            </option>
                                                        </c:forEach>
                                                    </select>
                                                </c:when>
                                                <c:otherwise>
                                                    <input type="hidden" id="codingSystem" value="msp"/>
                                                    <label><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.diagnostic.code"/></label>
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </td>

                                </tr>
                                <tr>
                                    <td>
                                        <div class="input-group">
								<span class="input-group-addon">
									1
								</span>
                                            <input type="text" class="form-control jsonDxSearchInput"
                                                       id="jsonDxSearchInput-1" name="xml_diagnostic_detail1"/>
                                            <span class="input-group-btn">
		                     		<button type="button" title="Search diagnostic code"
                                            class="btn btn-primary jsonDxSearchButton" value="jsonDxSearchInput-1">
	                            		<span class="glyphicon glyphicon-search"></span>
		                          	</button>
	                          	</span>
                                        </div>
                                    </td>

                                </tr>
                                <tr>
                                    <td>
                                        <div class="input-group">
  								<span class="input-group-addon">
									2
								</span>
                                            <input type="text" class="form-control jsonDxSearchInput"
                                                       id="jsonDxSearchInput-2" name="xml_diagnostic_detail2"/>
                                            <span class="input-group-btn">
		                     		<button type="button" title="Search Dx Description"
                                            class="btn btn-primary jsonDxSearchButton" value="jsonDxSearchInput-2">
	                            		<span class="glyphicon glyphicon-search"></span>
	                          		</button>
	                          	</span>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="input-group">
  								<span class="input-group-addon">
									3
								</span>
                                            <input type="text" class="form-control jsonDxSearchInput"
                                                       id="jsonDxSearchInput-3" name="xml_diagnostic_detail3"/>
                                            <span class="input-group-btn">
		                     		<button type="button" title="Search Dx Description"
                                            class="btn btn-primary jsonDxSearchButton" value="jsonDxSearchInput-3">
	                            		<span class="glyphicon glyphicon-search"></span>
	                          		</button>
	                          	</span>
                                        </div>
                                    </td>
                                </tr>


                            </table>

                            <!-- ONSCREEN DX CODE DISPLAY END-->
                        </div>
                        <div class="tool-table table-responsive" style="width:100%;flex-basis: 10%;">

                            <label for="DX_REFERENCE">Recent Dx codes</label>

                            <div id="DX_REFERENCE"></div>
                        </div>
                        <div class="tool-table table-responsive" style="width:100%;flex-basis: 25%;">

                            <table class="table table-condensed table-borderless" style="height:100%">
                                <tr>
                                    <td>
                                        <label for="shortClaimNote">Short Claim Note</label>
                                        <input type="text" id="shortClaimNote" class="form-control"
                                                   name="shortClaimNote"/>
                                    </td>

                                </tr>

                                <tr>
                                    <td align="left" colspan="2">
                                        <select class="form-control" name="correspondenceCode"
                                                     onchange="correspondenceNote();">
                                            <option value="0">No Correspondence</option>
                                            <option value="N">Electronic Correspondence</option>
                                            <option value="C">Paper Correspondence</option>
                                            <option value="B">Both</option>
                                        </select>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div id="CORRESPONDENCENOTE" style="display:none;">
                                            <textarea class="form-control notes-box" name="notes" onkeyup="checkTextLimit(this.form.notes,400);"></textarea>
                                            <small>400 characters max.</small>
                                        </div>
                                        <div>
                                            <div>
                                                <label for="billing-notes-box">Billing Notes
                                                    <small>(Internal use. Not sent to MSP)</small></label>
                                            </div>
                                            <textarea class="form-control notes-box" id="billing-notes-box" name="messageNotes"></textarea>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="height:100%;vertical-align: bottom;">
                                        <div class="row-fluid pull-right ">
                                            <div id="ignoreWarningsButton">
                                                <label class="checkbox" for="ignoreWarn"
                                                       title="Check to ignore validation warnings">
                                                    <input type="checkbox" name="ignoreWarn" id="ignoreWarn"/>
                                                    Ignore Warnings
                                                </label>
                                            </div>
                                            <div id="buttonRow" class="button-bar">
                                                <input class="btn btn-md btn-primary" type="submit" name="Submit"
                                                       value="Continue">
                                                <input class="btn btn-md btn-danger" type="button" name="Button"
                                                       value="Cancel" onClick="window.close();">
                                            </div>
                                        </div>
                                    </td>
                                </tr>

                            </table>
                        </div>

                    </td>
                </tr>
                <tr>
                    <td>
                        <div id="bcBillingError"></div>

                    </td>
                </tr>
                <tr>
                    <td>


                        <div id="billingFormTableWrapper">
                            <table id="billingFormTable">
                                <tr>
                                    <td style="width:33%;">
                                        <table class="table table-condensed serviceCodesTable">
                                            <tr style="background-color:#CCCCFF;">
                                                <td width="25%">
                                                    <div align="left">
                                                        <label>
                                                            <%=group1Header%>
                                                        </label>
                                                    </div>
                                                </td>
                                                <td width="61%" style="background-color:#CCCCFF;">
                                                    <label>
                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.desc"/>
                                                    </label>
                                                </td>
                                                <td width="14%">
                                                    <div align="right">
                                                        <label>&dollar;<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.fee"/></label>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%for (int i = 0; i < billlist1.length; i++) { %>
                                            <tr>
                                                <%String svcCall = "addSvcCode('" + billlist1[i].getServiceCode() + "')"; %>
                                                <td width="25%" valign="middle">
                                                    <label class="checkbox">
                                                        <input type="checkbox" name="service" value="<%=billlist1[i].getServiceCode()%>" onclick="<%=svcCall%>" />
                                                        <%=billlist1[i].getServiceCode()%>
                                                    </label>
                                                </td>
                                                <td width="61%">
                                                    <%=billlist1[i].getDescription()%>
                                                </td>
                                                <td width="14%">
                                                    <div align="right">
                                                        <%=billlist1[i].getPrice()%>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%} %>
                                        </table>
                                        <!-- former tool table -->


                                    </td>
                                    <td valign="top" style="width:33%;">
                                        <table class="table table-condensed serviceCodesTable">
                                            <tr style="background-color:#CCCCFF;">
                                                <td width="21%">
                                                    <label>
                                                        <%=group2Header%>
                                                    </label>
                                                </td>
                                                <td width="60%" style="background-color:#CCCCFF;">
                                                    <label><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.desc"/></label>
                                                </td>
                                                <td width="19%" align="right">
                                                    <label>&dollar;<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.fee"/></label>
                                                </td>
                                            </tr>
                                            <%for (int i = 0; i < billlist2.length; i++) { %>
                                            <tr>
                                                <%String svcCall = "addSvcCode('" + billlist2[i].getServiceCode() + "')"; %>
                                                <td width="25%">
                                                    <label class="checkbox">
                                                        <input type="checkbox" name="service" value="<%=billlist2[i].getServiceCode()%>" onclick="<%=svcCall%>"/>
                                                        <%=billlist2[i].getServiceCode()%>
                                                    </label>
                                                </td>
                                                <td width="61%">
                                                    <%=billlist2[i].getDescription()%>
                                                </td>
                                                <td width="14%">
                                                    <div align="right">
                                                        <%=billlist2[i].getPrice()%>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%} %>
                                        </table>
                                        <!-- former tool table -->
                                    </td>
                                    <td valign="top" style="width:33%;">
                                        <table class="table table-condensed serviceCodesTable">
                                            <tr style="background-color:#CCCCFF;">
                                                <td width="25%" align="left" valign="middle">
                                                    <label><%=group3Header%>
                                                    </label>
                                                </td>
                                                <td width="61%" style="background-color:#CCCCFF;">
                                                    <label><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.desc"/></label>
                                                </td>
                                                <td width="14%" align="right">
                                                    <label>&dollar;<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.fee"/></label>
                                                </td>
                                            </tr>
                                            <%for (int i = 0; i < billlist3.length; i++) { %>
                                            <tr>
                                                <%String svcCall = "addSvcCode('" + billlist3[i].getServiceCode() + "')"; %>
                                                <td width="25%">
                                                    <label class="checkbox">
                                                        <input type="checkbox" name="service" value="<%=billlist3[i].getServiceCode()%>"/>
                                                        <%=billlist3[i].getServiceCode()%>
                                                    </label>
                                                </td>
                                                <td width="61%">
                                                    <%=billlist3[i].getDescription()%>
                                                </td>
                                                <td width="14%" align="right">
                                                    <%=billlist3[i].getPrice()%>
                                                </td>
                                            </tr>
                                            <%} %>
                                        </table>


                                    </td>
                                </tr>
                            </table>
                        </div>
                    </td>
                </tr>
            </table>

            <div class="container-fluid">
                <div id="wcbForms"></div>
            </div>
        </form>
    </div>
</div>

<oscar:oscarPropertiesCheck property="BILLING_DX_REFERENCE" value="yes">
    <script type="text/javascript">
        function getDxInformation(origRequest) {
            let url = "DxReference.jsp";
            let ran_number = Math.round(Math.random() * 1000000);
            let params = "demographicNo=<%=bean.getPatientNo()%>&rand=" + ran_number;  //hack to get around ie caching the page
            new Ajax.Updater('DX_REFERENCE', url, {method: 'get', parameters: params, asynchronous: true});
        }

        getDxInformation();
    </script>
</oscar:oscarPropertiesCheck>
</body>
</html>
