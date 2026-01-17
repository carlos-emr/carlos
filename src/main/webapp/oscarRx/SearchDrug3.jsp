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
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%@page import="ca.openosp.openo.commn.dao.DrugDao" %>
<%@page import="ca.openosp.openo.commn.model.Drug" %>
<%@page import="ca.openosp.openo.commn.model.PharmacyInfo" %>
<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%@page import="ca.openosp.openo.utility.LocaleUtils" %>
<%@page import="ca.openosp.openo.rx.data.*,ca.openosp.openo.demographic.data.DemographicData,ca.openosp.OscarProperties,ca.openosp.openo.log.*" %>
<%@page import="ca.openosp.openo.casemgmt.service.CaseManagementManager" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="java.util.*" %>
<%@page import="java.util.Enumeration" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%@page import="java.util.List" %>
<%@page import="ca.openosp.openo.casemgmt.web.PrescriptDrug" %>
<%@page import="ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="java.util.ArrayList,ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@page import="ca.openosp.openo.commn.model.ProviderPreference" %>
<%@page import="ca.openosp.openo.web.admin.ProviderPreferencesUIBean" %>
<%@page import="ca.openosp.openo.casemgmt.service.CaseManagementManager" %>
<%@page import="ca.openosp.openo.casemgmt.model.CaseManagementNote" %>
<%@page import="ca.openosp.openo.casemgmt.model.Issue" %>
<%@ page import="ca.openosp.openo.services.security.SecurityManager" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPatientData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPharmacyData" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>

<%
    RxSessionBean bean = null;
    String rx_enhance = OscarProperties.getInstance().getProperty("rx_enhance");
    RxPatientData.Patient patient = (RxPatientData.Patient) request.getSession().getAttribute("Patient");
%>

<%
    if (rx_enhance != null && rx_enhance.equals("true")) {
        if (request.getParameter("ID") != null) { %>
<script>
    window.opener.location = window.opener.location;
    window.close();
</script>
<%
        }
    }
    SecurityManager securityManager = new SecurityManager();
%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<c:if test="${sessionScope.RxSessionBean == null}">
    <c:redirect url="error.html"/>
</c:if>
<c:if test="${not empty sessionScope.RxSessionBean}">
    <%
        // Directly access the RxSessionBean from the session
        bean = (RxSessionBean) session.getAttribute("RxSessionBean");
        if (bean != null && !bean.isValid()) {
            response.sendRedirect("error.html");
            return; // Ensure no further JSP processing
        }
    %>
</c:if>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<%
    String usefav = request.getParameter("usefav");
    String favid = request.getParameter("favid");
    int demoNo = bean.getDemographicNo();
%>
<security:oscarSec roleName="<%=roleName2$%>"
                   objectName='<%="_rx$"+demoNo%>' rights="o"
                   reverse="<%=false%>">
    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.accessDenied"/>
    <% response.sendRedirect(request.getContextPath() + "/acctLocked.html"); %>
</security:oscarSec>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = bean.getProviderNo();
    //String reRxDrugId=request.getParameter("reRxDrugId");
    HashMap hm = (HashMap) session.getAttribute("profileViewSpec");
    boolean show_current = true;
    boolean show_all = true;
    boolean active = true;
    boolean inactive = true;
    //boolean all=true;
    boolean longterm_acute = true;
    boolean longterm_acute_inactive_external = true;
    if (hm != null) {
        if (hm.get("show_current") != null)
            show_current = (Boolean) hm.get("show_current");
        else
            show_current = false;
        if (hm.get("show_all") != null)
            show_all = (Boolean) hm.get("show_all");
        else
            show_all = false;
        if (hm.get("active") != null)
            active = (Boolean) hm.get("active");
        else
            active = false;
        if (hm.get("inactive") != null)
            inactive = (Boolean) hm.get("inactive");
        else
            inactive = false;
        //if(hm.get("all")!=null)
        //   all=(Boolean)hm.get("all");
        //else
        //    all=false;
        if (hm.get("longterm_acute") != null)
            longterm_acute = (Boolean) hm.get("longterm_acute");
        else
            longterm_acute = false;
        if (hm.get("longterm_acute_inactive_external") != null)
            longterm_acute_inactive_external = (Boolean) hm.get("longterm_acute_inactive_external");
        else
            longterm_acute_inactive_external = false;
    }

    RxPharmacyData pharmacyData = new RxPharmacyData();
    List<PharmacyInfo> pharmacyList = pharmacyData.getPharmacyFromDemographic(Integer.toString(demoNo));

    String drugref_route = OscarProperties.getInstance().getProperty("drugref_route");
    if (drugref_route == null) {
        drugref_route = "";
    }
    String[] d_route = ("Oral," + drugref_route).split(",");

    String annotation_display = CaseManagementNoteLink.DISP_PRESCRIP;

    RxPrescriptionData.Prescription[] prescribedDrugs;
    prescribedDrugs = patient.getPrescribedDrugScripts(); //this function only returns drugs which have an entry in prescription and drugs table
    String script_no = "";

    //This checks if the providers has the ExternalPresriber feature enabled, if so then a link appear for the providers to access the ExternalPrescriber
    ProviderPreference providerPreference = ProviderPreferencesUIBean.getProviderPreference(loggedInInfo.getLoggedInProviderNo());

    boolean eRxEnabled = false;
    String eRx_SSO_URL = null;
    String eRxUsername = null;
    String eRxPassword = null;
    String eRxFacility = null;
    String eRxTrainingMode = "0"; //not in training mode

    if (providerPreference != null) {
        eRxEnabled = providerPreference.isERxEnabled();
        eRx_SSO_URL = providerPreference.getERx_SSO_URL();
        eRxUsername = providerPreference.getERxUsername();
        eRxPassword = providerPreference.getERxPassword();
        eRxFacility = providerPreference.getERxFacility();

        boolean eRxTrainingModeTemp = providerPreference.isERxTrainingMode();
        if (eRxTrainingModeTemp) eRxTrainingMode = "1";
    }

    CaseManagementManager cmgmtMgr = SpringUtils.getBean(CaseManagementManager.class);
    List<Issue> issues = cmgmtMgr.getIssueInfoByCode(loggedInInfo.getLoggedInProviderNo(), "OMeds");
    String[] issueIds = new String[issues.size()];
    int idx = 0;
    for (Issue issue : issues) {
        issueIds[idx] = String.valueOf(issue.getId());
    }
    List<CaseManagementNote> notes = cmgmtMgr.getNotes(bean.getDemographicNo() + "", issueIds);


%>

<!DOCTYPE html>
    <html lang="en">
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title"/></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarRx/styles.css">

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <script type="text/javascript">
            var ctx = '${ ctx }';
        </script>
        <script type="text/javascript" src="${ ctx }/js/jquery-1.7.1.min.js"></script>
        <script type="text/javascript" src="${ ctx }/js/jquery-ui-1.8.18.custom.min.js"></script>
        <script>
            jQuery.noConflict();
        </script>


        <link rel="stylesheet" href="<c:out value="${ctx}/share/lightwindow/css/lightwindow.css"/>" type="text/css"
              media="screen"/>

        <script type="text/javascript" src="${ctx}/js/global.js"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/prototype.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/screen.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/rx.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/scriptaculous.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/effects.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/controls.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/Oscar.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/dragiframe.js"/>"></script>
        <script type="text/javascript"
                src="<c:out value="${ctx}/share/lightwindow/javascript/lightwindow.js"/>"></script>
        <!--script type="text/javascript" src="<%--c:out value="modaldbox.js"/--%>"></script-->
        <script type="text/javascript" src="<c:out value="${ctx}/js/checkDate.js"/>"></script>

<%--        <link rel="stylesheet" type="text/css" href="<c:out value="${ctx}/share/yui/css/fonts-min.css"/>" >--%>
        <link rel="stylesheet" type="text/css" href="<c:out value="${ctx}/share/yui/css/autocomplete.css"/>">
        <script type="text/javascript" src="<c:out value="${ctx}/share/yui/js/yahoo-dom-event.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/yui/js/connection-min.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/yui/js/animation-min.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/yui/js/datasource-min.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/yui/js/autocomplete-min.js"/>"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/js/checkDate.js"/>"></script>

        <script type="text/javascript">
            let selectedReRxIDs = [];
            function saveLinks(randNumber) {
                $('method_' + randNumber).onblur();
                $('route_' + randNumber).onblur();
                $('frequency_' + randNumber).onblur();
                $('minimum_' + randNumber).onblur();
                $('maximum_' + randNumber).onblur();
                $('duration_' + randNumber).onblur();
                $('durationUnit_' + randNumber).onblur();
            }


            function handleEnter(inField, ev) {
                var charCode;
                if (ev && ev.which) {
                    charCode = ev.which;
                } else if (window.event) {
                    ev = window.event;
                    charCode = ev.keyCode;
                }
                var id = inField.id.split("_")[1];
                if (charCode == 13) {
                    showHideSpecInst('siAutoComplete_' + id);
                }
            }

            //has to be in here, not prescribe.jsp for it to work in IE 6/7 and probably 8.
            function showHideSpecInst(elementId) {
                if ($(elementId).getStyle('display') == 'none') {
                    Effect.BlindDown(elementId);
                } else {
                    Effect.BlindUp(elementId);
                }
            }

            function resetReRxDrugList() {
                var rand = Math.floor(Math.random() * 10001);
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=clearReRxDrugList";
                var data = "rand=" + rand;
                new Ajax.Request(url, {
                    method: 'post', parameters: data
                });
            }

            function onPrint(cfgPage) {
                var docF = $('printFormDD');

                docF.action = "<%= request.getContextPath() %>/form/createpdf?__title=Rx&__cfgfile=" + cfgPage + "&__template=a6blank";
                docF.target = "_blank";
                docF.submit();
                return true;
            }

            function buildRoute() {

                pickRoute = "";
            }


            function popupRxSearchWindow() {
                var winX = (document.all) ? window.screenLeft : window.screenX;
                var winY = (document.all) ? window.screenTop : window.screenY;

                var top = winY + 70;
                var left = winX + 110;
                var url = "oscarRx/searchDrug.do?rx2=true&searchString=" + $('searchString').value;
                popup2(600, 800, top, left, url, 'windowNameRxSearch<%=demoNo%>');

            }


            function popupRxReasonWindow(demographic, id) {
                var winX = (document.all) ? window.screenLeft : window.screenX;
                var winY = (document.all) ? window.screenTop : window.screenY;

                var top = winY + 70;
                var left = winX + 110;
                var url = "oscarRx/SelectReason.jsp?demographicNo=" + demographic + "&drugId=" + id;
                popup2(575, 650, top, left, url, 'windowNameRxReason<%=demoNo%>');

            }


            var highlightMatch = function (full, snippet, matchindex) {
                return "<a title='" + full + "'>" + full.substring(0, matchindex) +
                    "<span class=match>" + full.substr(matchindex, snippet.length) + "</span>" + full.substring(matchindex + snippet.length) + "</a>";
            };

            var highlightMatchInactiveMatchWord = function (full, snippet, matchindex) {
                //oscarLog(full+"--"+snippet+"--"+matchindex);
                return "<a title='" + full + "'>" + "<span class=matchInactive>" + full.substring(0, matchindex) +
                    "<span class=match>" + full.substr(matchindex, snippet.length) + "</span>" + full.substring(matchindex + snippet.length) + "</span>" + "</a>";
            };
            var highlightMatchInactive = function (full, snippet, matchindex) {
                /* oscarLog(full+"--"+snippet+"--"+matchindex);
                oscarLog(" aa "+full.substring(0, matchindex) );
                oscarLog(" bb "+full.substr(matchindex, snippet.length) );
                oscarLog(" cc "+ full.substring(matchindex + snippet.length));*/
                /*return "<a title='"+full+"'>"+"<span class=matchInactive>"+full.substring(0, matchindex) +
                full.substr(matchindex, snippet.length) +full.substring(matchindex + snippet.length)+"</span>"+"</a>";*/
                return "<a title='" + full + "'>" + "<span class=matchInactive>" + full + "</span>" + "</a>";
            };
            var resultFormatter = function (oResultData, sQuery, sResultMatch) {
                //oscarLog("oResultData, sQuery, sResultMatch="+oResultData+"--"+sQuery+"--"+sResultMatch);
                //oscarLog("oResultData[0]="+oResultData[0]);
                //oscarLog("oResultData.name="+oResultData.name);
                //oscarLog("oResultData.name="+oResultData.id);
                var query = sQuery.toUpperCase();
                var drugName = oResultData[0];

                var mIndex = drugName.toUpperCase().indexOf(query);
                var display = '';

                if (mIndex > -1) {
                    display = highlightMatch(drugName, query, mIndex);
                } else {
                    display = drugName;
                }
                return display;
            };
            var resultFormatter2 = function (oResultData, sQuery, sResultMatch) {
                /*oscarLog("oResultData, sQuery, sResultMatch="+oResultData+"--"+sQuery+"--"+sResultMatch);
               oscarLog("oResultData[0]="+oResultData[0]);
               oscarLog("oResultData.name="+oResultData.name);
               oscarLog("oResultData.name="+oResultData.id);*/
                var query = sQuery.toUpperCase();
                var drugName = oResultData.name;
                var isInactive = oResultData.isInactive;
                //oscarLog("isInactive="+isInactive);

                var mIndex = drugName.toUpperCase().indexOf(query);
                var display = '';
                if (mIndex > -1 && (isInactive == 'true' || isInactive == true)) { //match and inactive
                    display = highlightMatchInactiveMatchWord(drugName, query, mIndex);
                } else if (mIndex > -1 && (isInactive == 'false' || isInactive == false || isInactive == undefined || isInactive == null)) { //match and active
                    display = highlightMatch(drugName, query, mIndex);
                } else if (mIndex <= -1 && (isInactive == 'true' || isInactive == true)) {//no match and inactive
                    display = highlightMatchInactive(drugName, query, mIndex);
                } else {//active and no match
                    display = drugName;
                }


                return display;
            };
        </script>

        <script type="text/javascript">
            addEvent(window, "load", sortables_init);

            var SORT_COLUMN_INDEX;

            function sortables_init() {
                // Find all tables with class sortable and make them sortable

                if (!document.getElementsByTagName) return;

                tbls = document.getElementsByTagName("table");

                for (ti = 0; ti < tbls.length; ti++) {
                    thisTbl = tbls[ti];

                    if (((' ' + thisTbl.className + ' ').indexOf("sortable") != -1) && (thisTbl.id)) {
                        //initTable(thisTbl.id);
                        ts_makeSortable(thisTbl);
                    }
                }
            }

            function ts_makeSortable(table) {
                oscarLog('making ' + table + ' sortable');
                if (table.rows && table.rows.length > 0) {
                    var firstRow = table.rows[0];
                }
                if (!firstRow) return;
                oscarLog('Gets past here');

                // We have a first row: assume it's the header, and make its contents clickable links
                for (var i = 0; i < firstRow.cells.length; i++) {
                    var cell = firstRow.cells[i];
                    var txt = ts_getInnerText(cell);
                    cell.innerHTML = '<a href="#"  class="sortheader" ' +
                        'onclick="ts_resortTable(this, ' + i + ');return false;">' +
                        txt + '<span class="sortarrow"></span></a>';
                }
            }

            function ts_getInnerText(el) {
                if (typeof el == "string") return el;
                if (typeof el == "undefined") {
                    return el
                }
                ;
                if (el.innerText) return el.innerText;	//Not needed but it is faster
                var str = "";

                var cs = el.childNodes;
                var l = cs.length;
                for (var i = 0; i < l; i++) {
                    switch (cs[i].nodeType) {
                        case 1: //ELEMENT_NODE
                            str += ts_getInnerText(cs[i]);
                            break;
                        case 3:	//TEXT_NODE
                            str += cs[i].nodeValue;
                            break;
                    }
                }
                return str;
            }

            function ts_resortTable(lnk, clid) {
                // get the span
                var span;
                for (var ci = 0; ci < lnk.childNodes.length; ci++) {
                    if (lnk.childNodes[ci].tagName && lnk.childNodes[ci].tagName.toLowerCase() == 'span') span = lnk.childNodes[ci];
                }
                var spantext = ts_getInnerText(span);
                var td = lnk.parentNode;
                var column = clid;
                var table = getParent(td, 'TABLE');

                // Work out a type for the column
                if (table.rows.length <= 1) return;


                var itm = ts_getInnerText(table.rows[1].cells[column]).trim();
                sortfn = ts_sort_caseinsensitive;
                if (itm.match(/^\d\d[\/-]\d\d[\/-]\d\d\d\d$/)) sortfn = ts_sort_date;
                if (itm.match(/^\d\d[\/-]\d\d[\/-]\d\d$/)) sortfn = ts_sort_date;
                if (itm.match(/^[\Uffffffff$]/)) sortfn = ts_sort_currency;
                if (itm.match(/^[\d\.]+$/)) sortfn = ts_sort_numeric;
                SORT_COLUMN_INDEX = column;
                var firstRow = new Array();
                var newRows = new Array();
                for (i = 0; i < table.rows[0].length; i++) {
                    firstRow[i] = table.rows[0][i];
                }
                for (j = 1; j < table.rows.length; j++) {
                    newRows[j - 1] = table.rows[j];
                }

                newRows.sort(sortfn);

                if (span.getAttribute("sortdir") == 'down') {
                    ARROW = '&nbsp;&nbsp;&uarr;';
                    newRows.reverse();
                    span.setAttribute('sortdir', 'up');
                } else {
                    ARROW = '&nbsp;&nbsp;&darr;';
                    span.setAttribute('sortdir', 'down');
                }

                // We appendChild rows that already exist to the tbody, so it moves them rather than creating new ones
                // don't do sortbottom rows
                for (i = 0; i < newRows.length; i++) {
                    if (!newRows[i].className || (newRows[i].className && (newRows[i].className.indexOf('sortbottom') == -1))) table.tBodies[0].appendChild(newRows[i]);
                }
                // do sortbottom rows only
                for (i = 0; i < newRows.length; i++) {
                    if (newRows[i].className && (newRows[i].className.indexOf('sortbottom') != -1)) table.tBodies[0].appendChild(newRows[i]);
                }

                // Delete any other arrows there may be showing
                var allspans = document.getElementsByTagName("span");
                for (var ci = 0; ci < allspans.length; ci++) {
                    if (allspans[ci].className == 'sortarrow') {
                        if (getParent(allspans[ci], "table") == getParent(lnk, "table")) { // in the same table as us?
                            allspans[ci].innerHTML = '';
                        }
                    }
                }

                span.innerHTML = ARROW;
            }

            function getParent(el, pTagName) {
                if (el == null) return null;
                else if (el.nodeType == 1 && el.tagName.toLowerCase() == pTagName.toLowerCase())	// Gecko bug, supposed to be uppercase
                    return el;
                else
                    return getParent(el.parentNode, pTagName);
            }

            function ts_sort_date(a, b) {
                // y2k notes: two digit years less than 50 are treated as 20XX, greater than 50 are treated as 19XX
                aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]);
                bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]);
                if (aa.length == 10) {
                    dt1 = aa.substr(6, 4) + aa.substr(3, 2) + aa.substr(0, 2);
                } else {
                    yr = aa.substr(6, 2);
                    if (parseInt(yr) < 50) {
                        yr = '20' + yr;
                    } else {
                        yr = '19' + yr;
                    }
                    dt1 = yr + aa.substr(3, 2) + aa.substr(0, 2);
                }
                if (bb.length == 10) {
                    dt2 = bb.substr(6, 4) + bb.substr(3, 2) + bb.substr(0, 2);
                } else {
                    yr = bb.substr(6, 2);
                    if (parseInt(yr) < 50) {
                        yr = '20' + yr;
                    } else {
                        yr = '19' + yr;
                    }
                    dt2 = yr + bb.substr(3, 2) + bb.substr(0, 2);
                }
                if (dt1 == dt2) return 0;
                if (dt1 < dt2) return -1;
                return 1;
            }

            function ts_sort_currency(a, b) {
                aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]).replace(/[^0-9.]/g, '');
                bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]).replace(/[^0-9.]/g, '');
                return parseFloat(aa) - parseFloat(bb);
            }

            function ts_sort_numeric(a, b) {
                aa = parseFloat(ts_getInnerText(a.cells[SORT_COLUMN_INDEX]));
                if (isNaN(aa)) aa = 0;
                bb = parseFloat(ts_getInnerText(b.cells[SORT_COLUMN_INDEX]));
                if (isNaN(bb)) bb = 0;
                return aa - bb;
            }

            function ts_sort_caseinsensitive(a, b) {
                aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]).toLowerCase();
                bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]).toLowerCase();
                if (aa == bb) return 0;
                if (aa < bb) return -1;
                return 1;
            }

            function ts_sort_default(a, b) {
                aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]);
                bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]);
                if (aa == bb) return 0;
                if (aa < bb) return -1;
                return 1;
            }


            function addEvent(elm, evType, fn, useCapture)
// addEvent and removeEvent
// cross-browser event handling for IE5+,  NS6 and Mozilla
// By Scott Andrew
            {
                if (elm.addEventListener) {
                    elm.addEventListener(evType, fn, useCapture);
                    return true;
                } else if (elm.attachEvent) {
                    var r = elm.attachEvent("on" + evType, fn);
                    return r;
                } else {
                    alert("Handler could not be removed");
                }
            }

            function checkFav() {
                //oscarLog("****** in checkFav");
                var usefav = '<%=usefav%>';
                var favid = '<%=favid%>';
                if (usefav == "true" && favid != null && favid != 'null') {
                    //oscarLog("****** favid "+favid);
                    useFav2(favid);
                } else {
                }
            }

            //not used , represcribe a drug
            function represcribeOnLoad(drugId) {
                var data = "drugId=" + drugId + "&rand=" + Math.floor(Math.random() * 10001);
                var url = '<c:out value="${ctx}"/>/oscarRx/rePrescribe2.do?method=saveReRxDrugIdToStash';
                new Ajax.Updater('rxText', url, {
                    method: 'get', parameters: data, evalScripts: true, insertion: Insertion.Bottom
                });

            }


            function moveDrugDown(drugId, swapDrugId, demographicNo) {
                new Ajax.Request('<c:out value="${ctx}"/>/oscarRx/reorderDrug.do?method=update&direction=down&drugId=' + drugId + '&swapDrugId=' + swapDrugId + '&demographicNo=' + demographicNo + "&rand=" + Math.floor(Math.random() * 10001), {
                    method: 'get',
                    onSuccess: function (transport) {
                        callReplacementWebService("ListDrugs.jsp", 'drugProfile');
                        resetReRxDrugList();
                        resetStash();
                    }
                });
            }

            function moveDrugUp(drugId, swapDrugId, demographicNo) {
                new Ajax.Request('<c:out value="${ctx}"/>/oscarRx/reorderDrug.do?method=update&direction=up&drugId=' + drugId + '&swapDrugId=' + swapDrugId + '&demographicNo=' + demographicNo + "&rand=" + Math.floor(Math.random() * 10001), {
                    method: 'get',
                    onSuccess: function (transport) {
                        callReplacementWebService("ListDrugs.jsp", 'drugProfile');
                        resetReRxDrugList();
                        resetStash();
                    }
                });
            }

            function showPreviousPrints(scriptNo) {
                popupWindow(720, 700, 'oscarRx/ShowPreviousPrints.jsp?scriptNo=' + scriptNo, 'ShowPreviousPrints')
            }


            /*<![CDATA[*/
            var Lst;

            function CngClass(obj) {
                document.getElementById("selected_default").removeAttribute("style");
                if (Lst) Lst.className = '';
                obj.className = 'selected';
                Lst = obj;
            }

            /*]]>*/

            function toggleStartDateUnknown(rand) {
                var cb = document.getElementById('startDateUnknown_' + rand);
                var txt = document.getElementById('rxDate_' + rand);
                if (cb.checked) {
                    <%
    			java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd");
    			String today = formatter.format(new java.util.Date());
    		%>
                    txt.disabled = true;
                    txt.value = '<%=today%>';
                } else {
                    txt.disabled = false;
                }
            }


            //this is a SJHH specific feature
            function completeMedRec() {
                var ok = confirm("Are you sure you would like to mark the Med Rec as complete?");
                if (ok) {
                    var url = "<c:out value="${ctx}"/>" + "/oscarRx/completeMedRec.jsp?demographicNo=<%=bean.getDemographicNo()%>";
                    var data;
                    new Ajax.Request(url, {
                        method: 'get', parameters: data, onSuccess: function (transport) {
                            alert('Completed.')
                        }
                    });
                }
            }

            function printDrugProfile() {
                var ids = [];
                jQuery("input[type='checkbox'][id ^= 'reRxCheckBox']").each(function () {
                    if (jQuery(this).is(":checked")) {
                        var name = jQuery(this).attr('name').substring(9);
                        ids.push(name);
                    }
                });
                if (ids.length > 0) {
                    popupWindow(720, 700, 'oscarRx/PrintDrugProfile2.jsp?ids=' + ids.join(','), 'PrintDrugProfile');
                } else {
                    popupWindow(720, 700, 'oscarRx/PrintDrugProfile2.jsp', 'PrintDrugProfile');
                }
            }


        </script>

        <style type="text/css" media="print">


            noprint {
                display: none;
            }

            justforprint {
                float: left;
            }
        </style>

        <style type="text/css">

    .floatingWindow {
        position: fixed;
        top: 70%;
        right: 2px;
        border-radius: 10px;
        padding: 12px 24px;
        font-size: 16px;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.2);
        z-index: 5000;
        background-color: #ccf5ff;
        max-width: 25%;
        opacity: 0;
        transform: translateX(50px);
        visibility: hidden;
        transition: opacity 0.3s ease, transform 0.3s ease, visibility 0.3s ease;
    }

    /* Active state for showing */
    .floatingWindow.show {
        opacity: 0.95;
        transform: translateX(0);
        visibility: visible;
    }

            .ControlPushButton {
                font-size: x-small !important;
                padding: 3px !important;
            }

            #verificationLink {
                color: white;
            }

            table.legend {
                font-size: 12px;
                padding-left: 20px;

            }


            table.legend_items td {
                font-size: 12px;
                text-align: left;
                height: 30px;
                padding-right: 10px;
            }

            .rxStr a:hover {
                color: blue;
                text-decoration: underline;
            }

            .rxStr a {
                color: black;
                text-decoration: none;
            }

            .match {
                font-weight: bold;
            }

            .matchInactive {
                background-color: #C0C0C0;
            }

            #myAutoComplete {
                width: 25em; /* set width here or else widget will expand to fit its container */
                padding-bottom: 2em;
            }

            body {
                margin: 0;
                padding: 0;
            }

            .currentDrug {
                font-weight: bold;
            }

            .archivedDrug {
                text-decoration: line-through;
            }

            .expireInReference {
                color: orange;
                font-weight: bold;
            }

            .expiredDrug {
                color: gray;
            }

            .longTermMed {
                font-style: italic;
            }

            .discontinued {
                text-decoration: line-through;

            }

            .deleted {
                text-decoration: line-through;

            }

            .external {
                color: purple;
            }

            .sortheader {
                text-decoration: none;
                color: black;

            }
        </style>

        <!--[if IE]>
        <style type="text/css">

            table.legend {
                margin-left: 20px;
            }

        </style>
        <![endif]-->

    </head>

    <%
        boolean showall = false;

        if (request.getParameter("show") != null) if (request.getParameter("show").equals("all")) showall = true;
    %>


    <body vlink="#0000FF" onload="checkFav();iterateStash();rxPageSizeSelect();checkReRxLongTerm();load()"
          class="yui-skin-sam">
    <%=WebUtils.popErrorAndInfoMessagesAsHtml(session)%>
        <table id="AutoNumber1">
        <%@ include file="TopLinks2.jspf" %><!-- Row One included here-->
        <tr>
                <td height="100%" ><%@ include file="SideLinksEditFavorites2.jsp"%></td>
                <td style="padding-right:15px;"><!--Column Two Row Two-->

                    <div class="floatingWindow" id="reRxConfirmBox">
                        <p style="margin-bottom: 12px; font-size: 11px; text-align: end">
                            You have selected <span style="font-weight: bold" id="selectedCount">0</span> ReRx
                            medications. Click Stage Medication to add them to your prescriptions.
                        </p>
                        <div style="display: flex; gap: 10px; justify-content: flex-end;">
                            <input type="button" name="cancel" class="ControlPushButton" value="Cancel"
                                   onclick="cancelAndClearSelection()" title="Cancel">
                            <input type="button" name="stage" class="ControlPushButton" value="Stage Medication"
                                   onclick="stageSelectedReRxMedications()" title="Stage Medications">
                        </div>
                    </div>

                <table cellpadding="0" cellspacing="0" style="border-collapse: collapse" bordercolor="#111111">


                    <tr><!--put this left-->
                        <td valign="top" align="left">
                            <%if (securityManager.hasWriteAccess("_rx", roleName2$, true)) {%>
                            <form action="${pageContext.request.contextPath}/oscarRx/searchDrug.do" onsubmit="return checkEnterSendRx();"
                                       style="display: inline; margin-bottom:0;" id="drugForm" name="drugForm" method="post">
                                <div id="interactingDrugErrorMsg" style="display:none"></div>
                                <div id="rxText" style="float:left;"></div>
                                <br style="clear:left;">
                                <input type="hidden" id="deleteOnCloseRxBox" value="false">
                                <input type="hidden" id="rxPharmacyId" name="rxPharmacyId" value=""/>

                                <input type="hidden" name="demographicNo" id="demographicNo"
                                             value="<%=new Integer(patient.getDemographicNo()).toString()%>"/>
                                <table border="0">
                                    <tr valign="top">
                                        <td style="width:330px">
                                            <label for="searchString"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.drugSearchTextBox"/></label>
                                            <input type="text" id="searchString" name="searchString"
                                                       onfocus="changeContainerHeight();"
                                                       onblur="changeContainerHeight();"
                                                       onclick="changeContainerHeight();"
                                                       onkeydown="changeContainerHeight();"
                                                       style="width:248px;\" autocomplete=\"off"/>
                                            <div id="autocomplete_choices" style="overflow:auto;width:600px"></div>
                                            <span id="indicator1" style="display: none"> <!--img src="/images/spinner.gif" alt="Working..." --></span>
                                        </td>
                                        <td>
                                            <input type="button" name="search" class="ControlPushButton"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgSearch"/>"
                                                   onclick="popupRxSearchWindow();"
                                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.Search"/>">
                                            <input id="customDrug" type="button" class="ControlPushButton"
                                                   onclick="customWarning2();"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgCustomDrugRx3"/>"
                                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.CustomDrug"/>"/>
                                            <input id="customNote" type="button" class="ControlPushButton"
                                                   onclick="customNoteWarning();"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgNoteRx3"/>"
                                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.CustomNote"/>"/>
                                            <input id="reset" type="button" class="ControlPushButton"
                                                   title="Clear pending prescriptions" onclick="resetStash();"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgResetPrescriptionRx3"/>"/>
                                            <%if (OscarProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
                                            <a href="javascript:goOMD();"
                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.OMD"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgOMDLookup"/></a>
                                            <%}%>
                                            <br>
                                            <security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="x">
                                                <input id="saveButton" type="button" class="ControlPushButton"
                                                       onclick="updateSaveAllDrugsPrintCheckContinue();"
                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgSaveAndPrint"/>"
                                                       title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.SaveAndPrint"/>"/>
                                            </security:oscarSec>

                                            <input id="saveOnlyButton" type="button" class="ControlPushButton"
                                                   onclick="updateSaveAllDrugsCheckContinue();"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgSaveOnly"/>"
                                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.Save"/>"/>
                                            <%
                                                if (OscarProperties.getInstance().getProperty("oscarrx.medrec", "false").equals("true")) {
                                            %>
                                            <input id="completeMedRecButton" class="ControlPushButton" type="button"
                                                   onclick="completeMedRec();" value="Complete Med Rec"/>
                                            <% } %>

                                            <% if (eRxEnabled) { %>
                                            <a href="<%=eRx_SSO_URL%>User=<%=eRxUsername%>&Password=<%=eRxPassword%>&Clinic=<%=eRxFacility%>&PatientIdPMIS=<%=patient.getDemographicNo()%>&IsTraining=<%=eRxTrainingMode%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.eRx.msgExternalPrescriber"/></a>
                                            <% } %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="3">
                                                <%-- input type="button" class="ControlPushButton" onclick="customWarning();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgCustomDrug"/>" / --%>
                                        </td>
                                    </tr>
                                </table>
                            </form>
                            <div id="previewForm" style="display:none;"></div>
                            <%} %>
                        </td>
                    </tr>
                    <tr><!--put this left-->
                        <td align="left" valign="top">
                            <div>
                                <table width="100%"><!--drug profile, view and listdrugs.jsp-->
                                    <tr>
                                        <td>
                                            <div class="DivContentSectionHead">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.section2Title"/>
                                                &nbsp;
                                                <a href="javascript:void(0)" onClick="printDrugProfile();"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.Print"/></a>
                                                &nbsp;
                                                <%if (securityManager.hasWriteAccess("_rx", roleName2$, true)) {%>
                                                <a href="#" onclick="$('reprint').toggle();return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.Reprint"/></a>
                                                &nbsp;
                                                <a href="javascript:void(0);" name="cmdRePrescribe"
                                                   onclick="RePrescribeLongTerm();"
                                                   style="width: 200px"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReprescribeLongTermMed"/></a>
                                                &nbsp;
                                                <% } %>
                                                <a href="javascript:popupWindow(720,920,'oscarRx/chartDrugProfile.jsp?demographic_no=<%=demoNo%>','PrintDrugProfile2')">Timeline
                                                    Drug Profile</a>
                                                &nbsp;
                                                &nbsp;&nbsp;
                                            </div>

                                        </td>
                                    </tr>
                                    <tr>
                                            <td style="height: 150px; overflow: auto; border: thin solid #DCDCDC; display: none;" id="reprint">

                        <% for (int i = 0; prescribedDrugs.length > i; i++) {
                                                        RxPrescriptionData.Prescription drug = prescribedDrugs[i];
                        %>

                                                    <%
                                                        if (drug.getScript_no() != null && script_no.equals(drug.getScript_no())) {
                                                %>


                                                    <div style="text-indent: 5px">
                                                    <a href="javascript:void(0);" onclick="reprint2('<%=drug.getScript_no()%>')">
                                                        <%=drug.getRxDisplay()%>
                                                    </a>
                                                    </div>

                                                <%
                                                } else {

                                         if(i != 0) { %>
                                            </div> <!-- closes the reprintRxItem wrapper -->
                                        <%}%>
                                                    <div class="reprintRxItem">
                                                        <div class="reprintRxItemHeading">
                                                            <div>
                                                            <strong>Rx: <%=drug.getRxDate()%></strong>
                                                            </div>
                                                            <div>
                                                            <a href="javascript:void(0)" onclick="showPreviousPrints(<%=drug.getScript_no() %>);return false;">
                                                            <%=drug.getNumPrints()%>&nbsp;Print(s)
                                                </a>
                                            </div>
                                                        </div>
                                                        <div style="text-indent: 5px">
                                                        <a href="javascript:void(0);" onclick="reprint2('<%=drug.getScript_no()%>')"><%=drug.getRxDisplay()%></a>
                                                        </div>


                            <%} %>

                            <% script_no = drug.getScript_no() == null ? "" : drug.getScript_no();
							if(prescribedDrugs.length == i+1) { %>
                                    </div> <!-- closes the LAST reprintRxItem wrapper -->
                            <%}}%>

<%--                                                </div>--%>
<%--                                                --%>

                                        </td>
                                    </tr>
                                    <tr><!--move this left-->
                                        <td>
                                                <table>
                                                <tr>
                                                    <td>
                                                            <table class="legend">
                                                            <tr>
                                                                <td style="text-align: left; width:100px;">
                                                                    <a href="javascript:void(0);"
                                                                       title="View drug profile legend"
                                                                       onclick="ThemeViewer();"
                                                                       style="font-style:normal;color:#000000">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgProfileLegend"/>:
                                                                    </a>
                                                                    <a href="#"
                                                                       title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.rxChangeProfileViewMessage"/>"
                                                                       onclick="popupPage(230,860,'<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewRxProfileView');"
                                                                       style="color:red;text-decoration:none">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.rxChangeProfileView"/>
                                                                    </a>
                                                                </td>

																	    <td>

                                                                    <table class="legend_items" align="left">
                                                                        <tr>
                                                                            <%if (show_current) {%>
                                                                            <td>
                                                                                <a href="javascript:void(0);"
                                                                                   onclick="callReplacementWebService('ListDrugs.jsp','drugProfile');CngClass(this);"
                                                                                   id="selected_default"
                                                                                   style="color:#000000; text-decoration: none;"
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgShowCurrentDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgShowCurrent"/>
                                                                                </a>
                                                                            </td>
                                                                            <%
                                                                                }
                                                                                if (show_all) {
                                                                            %>
                                                                            <td>
                                                                                <a href="javascript:void(0);"
                                                                                   onclick="callReplacementWebService('ListDrugs.jsp?show=all','drugProfile');CngClass(this);"
                                                                                   Title="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgShowAllDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgShowAll"/>
                                                                                </a>
                                                                            </td>
                                                                            <%
                                                                                }
                                                                                if (active) {
                                                                            %>
                                                                            <td>
                                                                                <a href="javascript:void(0);"
                                                                                   onclick="callReplacementWebService('ListDrugs.jsp?status=active','drugProfile');CngClass(this);"
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgActiveDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgActive"/>
                                                                                </a>
                                                                            </td>
                                                                            <%
                                                                                }
                                                                                if (inactive) {
                                                                            %>
                                                                            <td>
                                                                                <a href="javascript:void(0);"
                                                                                   onclick="callReplacementWebService('ListDrugs.jsp?status=inactive','drugProfile');CngClass(this);"
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgInactiveDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgInactive"/>
                                                                                </a>
                                                                            </td>
                                                                            <%
                                                                                }
                                                                                if (!OscarProperties.getInstance().getProperty("prescript.profile_legend.hide", "false").equals("true")) {

                                                                                    if (longterm_acute) {
                                                                            %>
                                                                            <td>
                                                                                <a href="javascript:void(0);"
                                                                                   onclick="callReplacementWebService('ListDrugs.jsp?longTermOnly=true&heading=Long Term Meds','drugProfile'); callAdditionWebService('ListDrugs.jsp?longTermOnly=acute&heading=Acute','drugProfile');CngClass(this);"
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgLongTermAcuteDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgLongTermAcute"/>
                                                                                </a>
                                                                            </td>
                                                                            <%
                                                                                }
                                                                                if (longterm_acute_inactive_external) {
                                                                            %>
                                                                            <td>
                                                                                <a href="javascript:void(0);"
                                                                                   onclick="callReplacementWebService('ListDrugs.jsp?longTermOnly=true&heading=Long Term Meds','drugProfile'); callAdditionWebService('ListDrugs.jsp?longTermOnly=acute&heading=Acute&status=active','drugProfile');callAdditionWebService('ListDrugs.jsp?longTermOnly=acute&heading=Inactive&status=inactive','drugProfile');callAdditionWebService('ListDrugs.jsp?heading=External&drugLocation=external','drugProfile');CngClass(this);"
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgLongTermAcuteInactiveExternalDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgLongTermAcuteInactiveExternal"/>
                                                                                </a>
                                                                            </td>
                                                                            <%
                                                                                    }
                                                                                }
                                                                            %>
                                                                        </tr>
                                                                    </table>

                                                                </td>

                                                            </tr>
                                                        </table>

                                                        <div id="drugProfile"></div>

                                                        <form action="${pageContext.request.contextPath}/oscarRx/rePrescribe.do" method="post">
                                                            <input type="hidden" name="drugList" id="drugList"/>
                                                            <input type="hidden" name="method">
                                                        </form> <br>
                                                        <form action="${pageContext.request.contextPath}/oscarRx/deleteRx.do" method="post">
                                                            <input type="hidden" name="drugList" id="drugList"/>
                                                        </form></td>

                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                                <table width="100%"><!--drug profile, view and listdrugs.jsp-->
                                    <tr>
                                        <td>
                                            <div class="DivContentSectionHead">
                                                Other Medications
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table class="sortable" id="OMedsTabls" width="50%" border="0"
                                                   cellpadding="3">
                                                <tr>
                                                    <th align="left">Date Entered</th>
                                                    <th align="left">Medication</th>
                                                </tr>
                                                <%
                                                    // java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd");

                                                    for (CaseManagementNote note : notes) {
                                                        if (!note.isLocked() && !note.isArchived()) {
                                                            String str = note.getNote();
                                                %>
                                                <tr>
                                                    <td><%=formatter.format(note.getCreate_date()) %>
                                                    </td>
                                                    <td><%=StringEscapeUtils.escapeHtml4(str)%>
                                                    </td>
                                                </tr>
                                                <%
                                                        }
                                                    }
                                                %>

                                            </table>
                                        </td>
                                    </tr>
                                </table>

                            </div>
                        </td>
                    </tr>
                </table>
                    <%-- End List Drugs Prescribed --%>

            </td>
            <td width="300px" valign="top">
            </td>
        </tr>

    </table>




    <div id="dragifm" style="top:0px;left:0px;"></div>
    <div id="discontinueUI"
         style="position: absolute;display:none;width:500px;height:200px;background-color:white;padding:20px;border:1px solid grey">
        <h3>Discontinue :<span id="disDrug"></span></h3>
        <input type="hidden" name="disDrugId" id="disDrugId"/>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.msgReason"/>
        <select name="disReason" id="disReason">
            <option value="adverseReaction"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.AdverseReaction"/></option>
            <option value="allergy"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.Allergy"/></option>
            <option value="cost"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.Cost"/></option>
            <option value="discontinuedByAnotherPhysician"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.DiscontinuedByAnotherPhysician"/></option>
            <option value="doseChange"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.DoseChange"/></option>
            <option value="drugInteraction"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.DrugInteraction"/></option>
            <option value="increasedRiskBenefitRatio"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.IncreasedRiskBenefitRatio"/></option>
            <option value="ineffectiveTreatment"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.IneffectiveTreatment"/></option>
            <option value="newScientificEvidence"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.NewScientificEvidence"/></option>
            <option value="noLongerNecessary"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.NoLongerNecessary"/></option>
            <option value="enteredInError"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.EnteredInError"/></option>
            <option value="patientRequest"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.PatientRequest"/></option>
            <option value="prescribingError"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.PrescribingError"/></option>
            <option value="simplifyingTreatment"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.SimplifyingTreatment"/></option>
            <option value="unknown"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.Unknown"/></option>

            <option value="other"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.Other"/></option>
        </select>


        <br/>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.discontinuedReason.msgComment"/><br/>
        <textarea id="disComment" rows="3" cols="45"></textarea><br/>
        <input type="button" onclick="$('discontinueUI').hide();" value="Cancel"/>
        <input type="button"
               onclick="Discontinue2($('disDrugId').value,$('disReason').value,$('disComment').value,$('disDrug').innerHTML);"
               value="Discontinue"/>

    </div>

    <div id="themeLegend"
         style="position: absolute;display:none; width:500px;height:200px;background-color:white;padding:20px;border:1px solid grey">
        <a href="javascript:void(0);" class="currentDrug">Drug that is current</a><br/>
        <%if (!OscarProperties.getInstance().getProperty("prescript.delete_drug.hide", "false").equals("true")) {%>
        <a href="javascript:void(0);" class="archivedDrug">Drug that is archived</a><br/>
        <%} %>
        <a href="javascript:void(0);" class="expireInReference">Drug that is current but will expire within the
            reference range</a><br/>
        <a href="javascript:void(0);" class="expiredDrug">Drug that is expired</a><br/>
        <a href="javascript:void(0);" class="longTermMed">Long Term Med Drug</a><br/>
        <a href="javascript:void(0);" class="discontinued">Discontinued Drug</a><br/>
        <a href="javascript:void(0);" class="external">Prescribed by an outside provider</a><br/><br/><br/><br/>
        <a href="javascript:void(0);" onclick="$('themeLegend').hide()">Close</a>
    </div>

    <%
        if (pharmacyList != null) {
    %>
    <style>
        #Layer1 {
            position: absolute;
            left: 130px;
            top: 50px;
            width: 350px;
            height: auto;
            visibility: hidden;
            z-index: 1;
            background-color: white;
            border: 2px solid grey;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        }

        .hiddenLayer {
            width: 100%;
            padding: 2 10px 10px 10px;
            box-sizing: border-box;
        }

        .wcblayerTitle {
            width: 40%;
            padding-left: 20px;
            font-weight: bold;
            background-color: #f2f2f2;
            text-align: center;
        }

        .wcblayerContent {
            padding-left: 20px;
        }

    </style>

    <div id="Layer1"><!--  This should be changed to automagically fill if this changes often -->

        <table class="hiddenLayer">
            <tr>
                <td>&nbsp;</td>
                <td align="right"><a href="javascript: function myFunction() {return false; }"
                                     onclick="hidepic('Layer1');" style="text-decoration: none;"><img
                        src='<c:out value="${ctx}/images/close.png"/>' border="0"></a></td>
            </tr>

            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgName"/></td>
                <td class="wcblayerContent" id="pharmacyName"></td>
            </tr>

            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgAddress"/></td>
                <td class="wcblayerContent" id="pharmacyAddress"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgCity"/></td>
                <td class="wcblayerContent" id="pharmacyCity"></td>
            </tr>

            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgProvince"/></td>
                <td class="wcblayerContent" id="pharmacyProvince"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgPostalCode"/></td>
                <td class="wcblayerContent" id="pharmacyPostalCode"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgPhone1"/></td>
                <td class="wcblayerContent" id="pharmacyPhone1"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgPhone2"/></td>
                <td class="wcblayerContent" id="pharmacyPhone2"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgFax"/></td>
                <td class="wcblayerContent" id="pharmacyFax"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgEmail"/></td>
                <td class="wcblayerContent" id="pharmacyEmail"></td>
            </tr>
            <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgNotes"/></td>
                <td class="wcblayerContent" id="pharmacyNotes"></td>
            </tr>
        </table>

    </div>

<%
                        }
%>
<script type="text/javascript">
        function changeLt(element, drugId) {
            if (confirm('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.Prescription.changeDrugLongTermConfirm"/>') === true) {
            const data = "ltDrugId=" + drugId + "&isLongTerm=" + element.checked + "&rand=" + Math.floor(Math.random() * 10001);
            const url = ctx + "/oscarRx/WriteScript.do?parameterValue=updateLongTermStatus";
            new Ajax.Request(url, {
                method: 'post',
                parameters: data,
                onSuccess: function (transport) {
                    const json = transport.responseText.evalJSON();
                    if (json != null && (json.success === 'true' || json.success === true)) {
                        callReplacementWebService('ListDrugs.jsp','drugProfile');
                    } else {
                        checkboxRevertStatus(element);
                    }
                },
                onFailure: function () {
                    checkboxRevertStatus(element);
                }
            });
            } else {
                checkboxRevertStatus(element);
            }
        }

    function checkboxRevertStatus(checkbox) {
        setTimeout(function () {
            checkbox.checked = !checkbox.checked;
        }, 500);
    }

        function checkReRxLongTerm() {
            var url = window.location.href;
            var match = url.indexOf('ltm=true');
            if (match > -1) {
                RePrescribeLongTerm();
            }
        }

        function changeContainerHeight(ele) {
            var ss = $('searchString').value;
            ss = trim(ss);
            if (ss.length == 0)
                $('autocomplete_choices').setStyle({height: '0%'});
            else
                $('autocomplete_choices').setStyle({height: '100%'});
        }

        function addInstruction(content, randomId) {
            $('instructions_' + randomId).value = content;
            parseIntr($('instructions_' + randomId));
        }

        function addSpecialInstruction(content, randomId) {
            if ($('siAutoComplete_' + randomId).getStyle('display') == 'none') {
                Effect.BlindDown('siAutoComplete_' + randomId);
            } else {
            }
            $('siInput_' + randomId).value = content;
            $('siInput_' + randomId).setStyle({color: 'black'});
        }

        function hideMedHistory() {
            mb.hide();
        }

        var modalBox = function () {
            this.show = function (randomId, displaySRC, H) {
                if (!document.getElementById("xmaskframe")) {
                    var divFram = document.createElement('iframe');
                    divFram.setAttribute("id", "xmaskframe");
                    divFram.setAttribute("name", "xmaskframe");
                    //divFram.setAttribute("src","displayMedHistory.jsp?randomId="+randomId);
                    divFram.setAttribute("allowtransparency", "false");
                    document.body.appendChild(divFram);
                    var divSty = document.getElementById("xmaskframe").style;
                    divSty.position = "fixed";
                    divSty.top = "0px";
                    divSty.right = "0px";
                    divSty.width = "390px"
                    //divSty.border="solid";
                    divSty.backgroundColor = "#F5F5F5";
                    divSty.zIndex = "45";
                    //divSty.cursor="move";
                }
                this.waitifrm = document.getElementById("xmaskframe");

                this.waitifrm.setAttribute("src", displaySRC + ".jsp?randomId=" + randomId);
                this.waitifrm.style.display = "block";
                this.waitifrm.style.height = H;

                $("dragifm").appendChild(this.waitifrm);
                Effect.Appear('xmaskframe');
            };
            this.hide = function () {
                Effect.Fade('xmaskframe');

            };
        }
        var mb = new modalBox();

        function displayMedHistory(randomId) {
            var data = "randomId=" + randomId;
            new Ajax.Request("<c:out value='${ctx}'/>" + "/oscarRx/WriteScript.do?parameterValue=listPreviousInstructions",
                {
                    method: 'post', parameters: data, asynchronous: false, onSuccess: function (transport) {
                        mb.show(randomId, 'displayMedHistory', '200px');
                    }
                });
        }

        function displayInstructions(randomId) {
            var data = "randomId=" + randomId;
            mb.show(randomId, '<%= request.getContextPath() %>/oscarRx/displayInstructions', '600px');

        }

        function updateProperty(elementId) {
            var randomId = elementId.split("_")[1];
            if (randomId != null) {
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateProperty";
                var data = "";
                if (elementId.match("prnVal_") != null)
                    data = "elementId=" + elementId + "&propertyValue=" + $(elementId).value;
                else if (elementId.match("repeats_") != null)
                    data = "elementId=" + elementId + "&propertyValue=" + $(elementId).value;
                else
                    data = "elementId=" + elementId + "&propertyValue=" + $(elementId).innerHTML;
                data = data + "&rand=" + Math.floor(Math.random() * 10001);
                new Ajax.Request(url, {method: 'post', parameters: data});
            }
        }

        function lookNonEdittable(elementId) {
            $(elementId).className = '';
        }

        function lookEdittable(elementId) {
            $(elementId).className = 'highlight';
        }

        function setPrn(randomId) {
            var prnStr = $('prn_' + randomId).innerHTML;
            prnStr = prnStr.strip();
            var prnStyle = $('prn_' + randomId).getStyle('textDecoration');
            if (prnStr == 'prn' || prnStr == 'PRN' || prnStr == 'Prn') {
                if (prnStyle.match("line-through") != null) {
                    $('prn_' + randomId).setStyle({textDecoration: 'none'});
                    $('prnVal_' + randomId).value = true;
                } else {
                    $('prn_' + randomId).setStyle({textDecoration: 'line-through'});
                    $('prnVal_' + randomId).value = false;
                }
            }
        }

        function focusTo(elementId) {
            $(elementId).contentEditable = 'true';
            $(elementId).focus();
            //IE 6/7 bug..will this call onfocus twice?? may need to do browser check.
            document.getElementById(elementId).onfocus();

        }

        function updateSpecialInstruction(elementId) {
            var randomId = elementId.split("_")[1];
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateSpecialInstruction";
            var data = "randomId=" + randomId + "&specialInstruction=" + $(elementId).value;
            data = data + "&rand=" + Math.floor(Math.random() * 10001);
            new Ajax.Request(url, {method: 'post', parameters: data});
        }

        function changeText(elementId) {
            if ($(elementId).value == 'Enter Special Instruction') {
                $(elementId).value = "";
                $(elementId).setStyle({color: 'black'});
            } else if ($(elementId).value == '') {
                $(elementId).value = 'Enter Special Instruction';
                $(elementId).setStyle({color: 'gray'});
            }

        }

        function updateMoreLess(elementId) {
            if ($(elementId).innerHTML == 'more')
                $(elementId).innerHTML = 'less';
            else
                $(elementId).innerHTML = 'more';
        }

        function changeDrugName(randomId, origDrugName) {
            if (confirm('If you change the drug name and write your own drug, you will lose the following functionality:'
                + '\n  *  Known Dosage Forms / Routes'
                + '\n  *  Drug Allergy Information'
                + '\n  *  Drug-Drug Interaction Information'
                + '\n  *  Drug Information'
                + '\n\nAre you sure you wish to use this feature?') == true) {

                //call another function to bring up prescribe.jsp
                var url = '<c:out value="${ctx}"/>/oscarRx/WriteScript.do?parameterValue=normalDrugSetCustom';
                var customDrugName = $("drugName_" + randomId).getValue();
                var data = "randomId=" + randomId + "&customDrugName=" + customDrugName;
                new Ajax.Updater('rxText', url, {
                    method: 'get',
                    parameters: data,
                    asynchronous: true,
                    insertion: Insertion.Bottom,
                    onSuccess: function (transport) {
                        $('set_' + randomId).remove();

                    }
                });
            } else {
                $("drugName_" + randomId).value = origDrugName;
            }
        }

        function resetStash() {
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=clearStash";
            var data = "rand=" + Math.floor(Math.random() * 10001);
            new Ajax.Request(url, {
                method: 'post', parameters: data
            });
            $('rxText').innerHTML = "";//make pending prescriptions disappear.
            $("searchString").focus();
        }

        function iterateStash() {
            var url = '<c:out value="${ctx}"/>/oscarRx/WriteScript.do?parameterValue=iterateStash';
            var data = "rand=" + Math.floor(Math.random() * 10001);
            new Ajax.Updater('rxText', url, {
                method: 'get', parameters: data, asynchronous: true, evalScripts: true,
                insertion: Insertion.Bottom, onSuccess: function (transport) {
                }
            });

        }

        function rxPageSizeSelect() {
            var ran_number = Math.round(Math.random() * 1000000);
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/GetRxPageSizeInfo.do?method=view";
            var params = "demographicNo=<%=demoNo%>&rand=" + ran_number;  //hack to get around ie caching the page
            new Ajax.Request(url, {method: 'post', parameters: params});
        }

        function reprint2(scriptNo) {
            var data = "scriptNo=" + scriptNo + "&rand=" + Math.floor(Math.random() * 10001);
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/rePrescribe2.do?method=reprint2";
            new Ajax.Request(url,
                {
                    method: 'post', postBody: data,
                    onSuccess: function (transport) {
                        popForm2(scriptNo);

                    }
                });
            return false;
        }

        function deletePrescribe(randomId) {
            var data = "randomId=" + randomId;
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/rxStashDelete.do?parameterValue=deletePrescribe";
            new Ajax.Request(url, {
                method: 'get', parameters: data, onSuccess: function (transport) {
                    if ($('deleteOnCloseRxBox').value == 'true') {
                        deleteRxOnCloseRxBox(randomId);
                    }
                }
            });
        }

        function deleteRxOnCloseRxBox(randomId) {

            var data = "randomId=" + randomId;
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=DeleteRxOnCloseRxBox";
            new Ajax.Request(url, {
                method: 'get', parameters: data, onSuccess: function (transport) {
                    var json = transport.responseText.evalJSON();
                    if (json != null) {
                        var id = json.drugId;
                        var rxDate = "rxDate_" + id;
                        var reRx = "reRx_" + id;
                        var del = "del_" + id;
                        var discont = "discont_" + id;
                        var prescrip = "prescrip_" + id;
                        $(rxDate).style.textDecoration = 'line-through';
                        $(reRx).style.textDecoration = 'line-through';
                        $(del).style.textDecoration = 'line-through';
                        $(discont).style.textDecoration = 'line-through';
                        $(prescrip).style.textDecoration = 'line-through';
                    }
                }
            });

        }

        function ThemeViewer() {

            var xy = Position.page($('drugProfile'));
            var x = (xy[0] + 200) + 'px';
            var y = xy[1] + 'px';
            var wid = ($('drugProfile').getWidth() - 300) + 'px';
            var styleStr = {left: x, top: y, width: wid};

            $('themeLegend').setStyle(styleStr);
            $('themeLegend').show();
        }

        var skipParseInstr = false;

        function useFav2(favoriteId) {
            var randomId = Math.round(Math.random() * 1000000);
            var data = "favoriteId=" + favoriteId + "&randomId=" + randomId;
            var url = '<c:out value="${ctx}"/>/oscarRx/useFavorite.do?parameterValue=useFav2';
            new Ajax.Updater('rxText', url, {
                method: 'get',
                parameters: data,
                asynchronous: true,
                evalScripts: true,
                insertion: Insertion.Bottom
            });

            skipParseInstr = true;
        }

        function calculateRxData(randomId) {

            if (skipParseInstr) {
                return false;
            }

            var dummie = parseIntr($('instructions_' + randomId));
            if (dummie)
                updateQty($('quantity_' + randomId));
        }

        function Delete2(element) {

            if (confirm('Are you sure you wish to delete the selected prescriptions?') == true) {
                var id_str = (element.id).split("_");
                var id = id_str[1];
                //var id=element.id;
                var rxDate = "rxDate_" + id;
                var reRx = "reRx_" + id;
                var del = "del_" + id;
                var discont = "discont_" + id;
                var prescrip = "prescrip_" + id;

                var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=Delete2";
                var data = "deleteRxId=" + element.id + "&rand=" + Math.floor(Math.random() * 10001);
                new Ajax.Request(url, {
                    method: 'post', postBody: data, onSuccess: function (transport) {
                        $(rxDate).style.textDecoration = 'line-through';
                        $(reRx).style.textDecoration = 'line-through';
                        $(del).style.textDecoration = 'line-through';
                        $(discont).style.textDecoration = 'line-through';
                        $(prescrip).style.textDecoration = 'line-through';
                    }
                });
            }
            return false;
        }

        function checkAllergy(id, atcCode) {
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/getAllergyData.jsp";
            var data = "atcCode=" + atcCode + "&id=" + id + "&rand=" + Math.floor(Math.random() * 10001);
            new Ajax.Request(url, {
                method: 'post', postBody: data, onSuccess: function (transport) {

                    var jsonResults = transport.responseText.evalJSON();

                    // if(jsonResults != null && jsonResults.id != null && jsonResults.results.length == 0) {
                    //     console.log(jsonResults.id);
                    //     var str = "<span style='color:blue'>No allergy to drug matched with patient profile. Please review patient allergies to ensure accuracy.</span>" ;
                    //     $('alleg_'+jsonResults.id).innerHTML = str;
                    //     document.getElementById('alleg_tbl_'+jsonResults.id).style.display='inline-block';
                    // }
                    let resultHtml = "";
                    if (jsonResults != null && jsonResults.results.length != 0) {
                        for (let i = 0; i < jsonResults.results.length; i++) {
                            let result = jsonResults.results[i];
                            resultHtml += "<span style='color:red'><b>Allergy: </b></span>" + result.DESCRIPTION;
                            resultHtml += "<span style='color:red'><b> Reaction: </b></span>" + result.reaction;
                            resultHtml += "<span style='color:red'><b> Severity: </b></span>" + result.severity;
                        }
                        $('alleg_' + jsonResults.id).innerHTML = resultHtml + '<br />';
                        document.getElementById('alleg_tbl_' + jsonResults.id).style.display = 'inline-block';
                    }
                }
            });
        }

        function checkIfInactive(id, dinNumber) {
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/getInactiveDate.jsp";
            var data = "din=" + dinNumber + "&id=" + id + "&rand=" + Math.floor(Math.random() * 10001);
            new Ajax.Request(url, {
                method: 'post', postBody: data, onSuccess: function (transport) {
                    var json = transport.responseText.evalJSON();
                    if (json != null) {
                        var str = "Inactive Drug Since: " + new Date(json.vec[0].time).toDateString();
                        $('inactive_' + json.id).innerHTML = str;
                    }
                }
            });
        }

        function Discontinue(event, element) {
            var id_str = (element.id).split("_");
            var id = id_str[1];
            var widVal = ($('drugProfile').getWidth() - 300);
            var widStr = widVal + 'px';
            var heightDrugProfile = $('discontinueUI').getHeight();
            var posx = 0, posy = 0;
            if (event.pageX || event.pageY) {
                posx = event.pageX;
                posx = posx - widVal;
                posy = event.pageY - heightDrugProfile / 2;
                posx = posx + 'px';
                posy = posy + 'px';
            } else if (event.clientX || event.clientY) {
                posx = event.clientX + document.body.scrollLeft
                    + document.documentElement.scrollLeft;
                posx = posx - widVal;
                posy = event.clientY + document.body.scrollTop
                    + document.documentElement.scrollTop - heightDrugProfile / 2;
                posx = posx + 'px';
                posy = posy + 'px';
            } else {
                var xy = Position.page($('drugProfile'));
                posx = (xy[0] + 200) + 'px';
                if (xy[1] >= 0)
                    posy = xy[1] + 'px';
                else
                    posy = 0 + 'px';
            }
            var styleStr = {left: posx, top: posy, width: widStr};

            var drugName = $('prescrip_' + id).innerHTML;
            $('discontinueUI').setStyle(styleStr);
            $('disDrug').innerHTML = drugName;
            $('discontinueUI').show();
            $('disDrugId').value = id;


        }

        function Discontinue2(id, reason, comment, drugSpecial) {
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/deleteRx.do?parameterValue=Discontinue";
            var demoNo = '<%=patient.getDemographicNo()%>';
            var data = "drugId=" + id + "&reason=" + reason + "&comment=" + comment + "&demoNo=" + demoNo + "&drugSpecial=" + drugSpecial + "&rand=" + Math.floor(Math.random() * 10001);
            new Ajax.Request(url, {
                method: 'post', postBody: data, onSuccess: function (transport) {
                    var json = transport.responseText.evalJSON();
                    $('discontinueUI').hide();
                    $('rxDate_' + json.id).style.textDecoration = 'line-through';
                    $('reRx_' + json.id).style.textDecoration = 'line-through';
                    $('del_' + json.id).style.textDecoration = 'line-through';
                    $('discont_' + json.id).innerHTML = json.reason;
                    $('prescrip_' + json.id).style.textDecoration = 'line-through';
                }
            });

        }


        //represcribe long term meds
        function RePrescribeLongTerm() {
            var demoNo = '<%=patient.getDemographicNo()%>';
            var data = "demoNo=" + demoNo + "&showall=<%=showall%>&rand=" + Math.floor(Math.random() * 10001);
            var url = '<c:out value="${ctx}"/>/oscarRx/rePrescribe2.do?method=repcbAllLongTerm';
            new Ajax.Updater('rxText', url, {
                method: 'get',
                parameters: data,
                asynchronous: true,
                insertion: Insertion.Bottom
            });
            return false;
        }

        function customNoteWarning() {
            if (confirm('This feature will allow you to manually enter a prescription.'
                + '\nWarning: you will lose the following functionality:'
                + '\n  *  Quantity and Repeats'
                + '\n  *  Known Dosage Forms / Routes'
                + '\n  *  Drug Allergy Information'
                + '\n  *  Drug-Drug Interaction Information'
                + '\n  *  Drug Information'
                + '\n\nAre you sure you wish to use this feature?') == true) {
                var randomId = Math.round(Math.random() * 1000000);
                var url = '<c:out value="${ctx}"/>/oscarRx/WriteScript.do?parameterValue=newCustomNote';
                var data = "randomId=" + randomId;
                new Ajax.Updater('rxText', url, {
                    method: 'get',
                    parameters: data,
                    asynchronous: true,
                    evalScripts: true,
                    insertion: Insertion.Bottom
                });
            }
        }

        function customWarning2() {
            if (confirm('This feature will allow you to manually enter a drug.'
                + '\nWarning: Only use this feature if absolutely necessary, as you will lose the following functionality:'
                + '\n  *  Known Dosage Forms / Routes'
                + '\n  *  Drug Allergy Information'
                + '\n  *  Drug-Drug Interaction Information'
                + '\n  *  Drug Information'
                + '\n\nAre you sure you wish to use this feature?') == true) {
                //call another function to bring up prescribe.jsp
                var randomId = Math.round(Math.random() * 1000000);
                var searchString = $("searchString").value;
                var url = '<c:out value="${ctx}"/>/oscarRx/WriteScript.do?parameterValue=newCustomDrug&name=' + searchString;
                var data = "randomId=" + randomId;
                new Ajax.Updater('rxText', url, {
                    method: 'get', parameters: data, asynchronous: true, evalScripts: true,
                    insertion: Insertion.Bottom, onComplete: function (transport) {
                        updateQty($('quantity_' + randomId));
                    }
                });

            }

        }

        function saveCustomName(element) {
            var elemId = element.id;
            var ar = elemId.split("_");
            var rand = ar[1];
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=saveCustomName";
            var data = "customName=" + encodeURIComponent(element.value) + "&randomId=" + rand;
            var instruction = "instructions_" + rand;
            var quantity = "quantity_" + rand;
            var repeat = "repeats_" + rand;
            new Ajax.Request(url, {
                method: 'get', parameters: data
            });
        }

        function updateDeleteOnCloseRxBox() {
            $('deleteOnCloseRxBox').value = 'true';
        }

        function popForm2(scriptId) {
            try {
                //oscarLog("popForm2 called");
                var url1 = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=checkNoStashItem&rand=" + Math.floor(Math.random() * 10001);
                var data = "";
                var h = 900;
                new Ajax.Request(url1, {
                    method: 'get', parameters: data, onSuccess: function (transport) {
                        //output default instructions
                        var json = transport.responseText.evalJSON();
                        var n = json.NoStashItem;
                        if (n > 4) {
                            h = h + (n - 4) * 100;
                        }
                        //oscarLog("h="+h+"--n="+n);
                        var url;
                        var json = jQuery("#Calcs").val();
                        //oscarLog(json);
                        if (json != null && json != "") {

                            var pharmacy = JSON.parse(json);

                            if (pharmacy != null) {
                                url = "<c:out value="${ctx}"/>" + "/oscarRx/ViewScript2.jsp?scriptId=" + scriptId + "&pharmacyId=" + pharmacy.id;
                            } else {
                                url = "<c:out value="${ctx}"/>" + "/oscarRx/ViewScript2.jsp?scriptId=" + scriptId;
                            }
                        } else {
                            url = "<c:out value="${ctx}"/>" + "/oscarRx/ViewScript2.jsp?scriptId=" + scriptId;
                        }

                        //oscarLog( "preview2 done");
                        myLightWindow.activateWindow({
                            href: url,
                            width: 980,
                            height: h
                        });
                        var editRxMsg = '<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.Preview.EditRx"/>';
                        $('lightwindow_title_bar_close_link').update(editRxMsg);
                        $('lightwindow_title_bar_close_link').onclick = updateDeleteOnCloseRxBox;
                    }
                });

            } catch (er) {
                oscarLog(er);
            }
            //oscarLog("bottom of popForm");
        }


        function callAdditionWebService(url, id) {
            var contextPath = '<c:out value="${ctx}"/>';
            if (url.indexOf(contextPath) != 0) {
                url = contextPath + "/oscarRx/" + url;
            }
            var ran_number = Math.round(Math.random() * 1000000);
            var params = "demographicNo=<%=demoNo%>&rand=" + ran_number;  //hack to get around ie caching the page
            var updater = new Ajax.Updater(id, url, {
                method: 'get',
                parameters: params,
                insertion: Insertion.Bottom,
                evalScripts: true
            });
        }

        function callReplacementWebService(url, id) {
            var contextPath = '<c:out value="${ctx}"/>';
            if (url.indexOf(contextPath) != 0) {
                url = contextPath + "/oscarRx/" + url;
            }
            var ran_number = Math.round(Math.random() * 1000000);
            var params = "demographicNo=<%=demoNo%>&rand=" + ran_number;  //hack to get around ie caching the page
            var updater = new Ajax.Updater(id, url, {method: 'get', parameters: params, evalScripts: true});
        }

        //callReplacementWebService("InteractionDisplay.jsp",'interactionsRx');
        callReplacementWebService("ListDrugs.jsp", 'drugProfile');

        YAHOO.example.FnMultipleFields = function () {
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/searchDrug.do?method=jsonSearch";
            var oDS = new YAHOO.util.XHRDataSource(url, {connMethodPost: true, connXhrMode: 'ingoreStaleResponse'});
            oDS.responseType = YAHOO.util.XHRDataSource.TYPE_JSON;// Set the responseType
            // Define the schema of the delimited results
            oDS.responseSchema = {
                resultsList: "results",
                fields: ["name", "id", "isInactive"]
            };
            // Enable caching
            oDS.maxCacheEntries = 0;
            oDS.connXhrMode = "cancelStaleRequests";
            // Instantiate AutoComplete
            var oAC = new YAHOO.widget.AutoComplete("searchString", "autocomplete_choices", oDS);
            oAC.useShadow = true;
            oAC.resultTypeList = false;
            oAC.queryMatchSubset = true;
            oAC.minQueryLength = 3;
            oAC.maxResultsDisplayed = 40;
            oAC.formatResult = resultFormatter2;


            // Define an event handler to populate a hidden form field
            // when an item gets selected and populate the input field
            //var myHiddenField = YAHOO.util.Dom.get("myHidden");
            var myHandler = function (type, args) {
                var arr = args[2];
                var url = '<c:out value="${ctx}"/>/oscarRx/WriteScript.do?parameterValue=createNewRx'; //"prescribe.jsp";
                var ran_number = Math.round(Math.random() * 1000000);
                var name = encodeURIComponent(arr.name);
                var params = "demographicNo=<%=demoNo%>&drugId=" + arr.id + "&text=" + name + "&randomId=" + ran_number;  //hack to get around ie caching the page
                new Ajax.Updater('rxText', url, {
                    method: 'get', parameters: params, evalScripts: true,
                    insertion: Insertion.Bottom, onSuccess: function (transport) {
                    }
                });

                $('searchString').value = "";

            };

            oAC.doBeforeExpandContainer = function (sQuery, oResponse) {
                if (oAC._nDisplayedItems < oAC.maxResultsDisplayed) {
                    oAC.setFooter("");
                } else {
                    oAC.setFooter("<a href='javascript:void(0)' onClick='popupRxSearchWindow();oAC.collapseContainer();'>See more results...</a>");
                }

                return true;
            }


            oAC.itemSelectEvent.subscribe(myHandler);
            var collapseFn = function () {
                $('autocomplete_choices').hide();
            }
            oAC.containerCollapseEvent.subscribe(collapseFn);
            var expandFn = function () {
                $('autocomplete_choices').show();
            }
            oAC.dataRequestEvent.subscribe(expandFn);
            return {
                oDS: oDS,
                oAC: oAC
            };


        }();

        function addFav(randomId, brandName) {
            var favoriteName = window.prompt('Please enter a name for the Favorite:', brandName);
            if (favoriteName == null) {
                return;
            }
            favoriteName = encodeURIComponent(favoriteName);
            if (favoriteName.length > 0) {
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/addFavorite2.do?parameterValue=addFav2";
                var data = "randomId=" + randomId + "&favoriteName=" + favoriteName;
                new Ajax.Request(url, {
                    method: 'get', parameters: data, onSuccess: function (transport) {
                        window.location.href = "<%= request.getContextPath() %>/oscarRx/SearchDrug3.jsp";
                    }
                })
            }
        }

        var resHidden2 = 0;//not used
        //not used
        function showHiddenRes() {
            var list = $$('div.hiddenResource');
            if (resHidden2 == 0) {
                list.invoke('show');
                resHidden2 = 1;
                $('showHiddenResWord').update('hide');
                var url = "updateHiddenResources.jsp";
                var params = "hiddenResources=&rand=" + Math.floor(Math.random() * 10001);
                new Ajax.Request(url, {method: 'post', parameters: params});
            } else {
                $('showHiddenResWord').update('show');
                list.invoke('hide');
                resHidden2 = 0;
            }
        }

        var showOrHide = 0;

        function showOrHideRes(hiddenRes) {
            hiddenRes = hiddenRes.replace(/\{/g, "");
            hiddenRes = hiddenRes.replace(/\}/g, "");
            hiddenRes = hiddenRes.replace(/\s/g, "");
            var arr = hiddenRes.split(",");
            var numberOfHiddenResources = 0;
            if (showOrHide == 0) {
                numberOfHiddenResources = 0;
                for (var i = 0; i < arr.length; i++) {
                    var element = arr[i];
                    var elementArr = element.split("=");
                    var resId = elementArr[0];
                    var resUpdated = elementArr[1];
                    var id = resId + "." + resUpdated;
                    $(id).show();
                    $('show_' + id).hide();
                    $('showHideWord').update('hide');

                    showOrHide = 1;
                    numberOfHiddenResources++;
                }
            } else {
                numberOfHiddenResources = 0
                for (var i = 0; i < arr.length; i++) {
                    var element = arr[i];
                    var elementArr = element.split("=");
                    var resId = elementArr[0];
                    var resUpdated = elementArr[1];
                    var id = resId + "." + resUpdated;
                    oscarLog("id=" + id);
                    $(id).hide();
                    $('show_' + id).show();
                    $('showHideWord').update('show');
                    showOrHide = 0;
                    numberOfHiddenResources++;
                }
            }
            $('showHideNumber').update(numberOfHiddenResources);

        }

        var addTextView = 0;

        function showAddText(randId) {
            var addTextId = "addText_" + randId;
            var addTextWordId = "addTextWord_" + randId;
            if (addTextView == 0) {
                $(addTextId).show();
                addTextView = 1;
                $(addTextWordId).update("less")
            } else {
                $(addTextId).hide();
                addTextView = 0;
                $(addTextWordId).update("more")
            }
        }


        function setSearchedDrug(drugId, name) {
            var url = '<c:out value="${ctx}"/>/oscarRx/WriteScript.do?parameterValue=createNewRx';
            var ran_number = Math.round(Math.random() * 1000000);
            name = encodeURIComponent(name);
            var params = "demographicNo=<%=demoNo%>&drugId=" + drugId + "&text=" + name + "&randomId=" + ran_number;
            new Ajax.Updater('rxText', url, {
                method: 'get',
                parameters: params,
                asynchronous: true,
                evalScripts: true,
                insertion: Insertion.Bottom
            });
            $('searchString').value = "";
        }

        var counterRx = 0;

        function updateReRxDrugId(elementId) {
            var ar = elementId.split("_");
            var drugId = ar[1];
            if (drugId != null && $(elementId).checked == true) {
                var data = "reRxDrugId=" + drugId + "&action=addToReRxDrugIdList&rand=" + Math.floor(Math.random() * 10001);
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateReRxDrug";
                new Ajax.Request(url, {method: 'get', parameters: data});
            } else if (drugId != null) {
                var data = "reRxDrugId=" + drugId + "&action=removeFromReRxDrugIdList&rand=" + Math.floor(Math.random() * 10001);
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateReRxDrug";
                new Ajax.Request(url, {method: 'get', parameters: data});
            }
        }

        function removeReRxDrugId(drugId) {
            if (drugId != null) {
        const data = "reRxDrugId=" + drugId + "&action=removeFromReRxDrugIdList&rand=" + Math.floor(Math.random() * 10001);
        const url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateReRxDrug";
                new Ajax.Request(url, {method: 'get', parameters: data});
            }
        }

        //represcribe a drug
        function represcribe(element, toArchive) {
            skipParseInstr = true;
            var elemId = element.id;
            var ar = elemId.split("_");
            var drugId = ar[1];
            if (drugId != null && $("reRxCheckBox_" + drugId).checked == true) {
                var url = '<c:out value="${ctx}"/>/oscarRx/rePrescribe2.do?method=represcribeMultiple&rand=' + Math.floor(Math.random() * 10001);
                new Ajax.Updater('rxText', url, {
                    method: 'get', parameters: data, asynchronous: false, evalScripts: true,
                    insertion: Insertion.Bottom, onSuccess: function (transport) {
                    }
                });
            } else if (drugId != null) {
                var dataUpdateId = "reRxDrugId=" + toArchive + "&action=addToReRxDrugIdList&rand=" + Math.floor(Math.random() * 10001);
                var urlUpdateId = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateReRxDrug";
                new Ajax.Request(urlUpdateId, {method: 'get', parameters: dataUpdateId});

                var data = "drugId=" + drugId;
                var url = '<c:out value="${ctx}"/>/oscarRx/rePrescribe2.do?method=represcribe2&rand=' + Math.floor(Math.random() * 10001);
                new Ajax.Updater('rxText', url, {
                    method: 'get', parameters: data, evalScripts: true,
                    insertion: Insertion.Bottom, onSuccess: function (transport) {
                    }
                });

            }
        }

/**
 * Updates the re-prescribing status of a prescribed drug in the UI and session.
 *
 * @param element The checkbox element that triggered the update.
 * @param drugId The ID of the drug being updated.
 */
function updateReRxStatusForPrescribedDrug(element, drugId) {
    const uiRefId = element.id.split('_')[1];
    if (drugId == null || uiRefId == null) {
        return;
    }

    if (element.checked === true) {
        this.addDrugToReRxList(uiRefId, drugId);
        selectedReRxIDs.push(drugId);
    } else {
        this.removeDrugFromReRxList(uiRefId, drugId);
        selectedReRxIDs = selectedReRxIDs.filter(id => id !== drugId);
    }
    this.updateReRxStageConfirmBoxVisibility();
}

    function updateReRxStageConfirmBoxVisibility() {
        const count = selectedReRxIDs.length;
        document.getElementById("selectedCount").innerText = count;

        const confirmBox = document.getElementById("reRxConfirmBox");
        if (count > 0) {
            confirmBox.classList.add("show");
        } else {
            confirmBox.classList.remove("show");
        }
    }

    function cancelAndClearSelection() {
        selectedReRxIDs.forEach(drugId => uncheckReRxForExistingPrescribedDrug(drugId));
        selectedReRxIDs = [];
        this.updateReRxStageConfirmBoxVisibility();
    }

    function stageSelectedReRxMedications() {
        this.rePrescribeMulti();
        selectedReRxIDs = [];
        this.updateReRxStageConfirmBoxVisibility();
}

/**
 * Sets off instruction parsing and adds a drug to the re-prescribe list in the UI and session.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to add.
 */
function addDrugToReRxList(uiRefId, drugId) {
    skipParseInstr = true;

    this.addDrugToReRxListInSession(uiRefId, drugId);
}

/**
 * Add ReRx drug to UI by making an AJAX request to update the 'rxText' element.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to re-prescribe.
 */
function rePrescribe2(uiRefId, drugId) {
    const data = "drugId=" + drugId;
    const url = "<c:out value="${ctx}"/>" + "/oscarRx/rePrescribe2.do?method=represcribe2&rand=" + uiRefId;
    new Ajax.Updater('rxText', url, {
        method: 'get', parameters: data, evalScripts: true,
        insertion: Insertion.Bottom, onSuccess: function (transport) {
        }
    });
}

    function rePrescribeMulti() {
        const url = "<c:out value="${ctx}"/>" + "/oscarRx/rePrescribe2.do?method=represcribeMultiple&rand=" + Math.floor(Math.random() * 10001);
        new Ajax.Updater('rxText', url, {
            method: 'get', asynchronous: false, evalScripts: true,
            insertion: Insertion.Bottom, onSuccess: function (transport) {
            }
        });
}

/**
 * Adds a drug to the re-prescribe list in the session.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to add.
 */
function addDrugToReRxListInSession(uiRefId, drugId) {
    const dataUpdateId = "reRxDrugId=" + drugId + "&action=addToReRxDrugIdList&rand=" + uiRefId;
    const urlUpdateId = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateReRxDrug";
    new Ajax.Request(urlUpdateId, {method: 'get', parameters: dataUpdateId});
}

/**
 * Removes a drug from the re-prescribe list and updates the UI.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to remove.
 */
function removeDrugFromReRxList(uiRefId, drugId) {
    this.removeElementFromUI(this.getPrescribingDrugCardByUiRefId(uiRefId));
    this.removeReRxDrugId(drugId);
}

/**
 * Removes a prescribing drug entry from both the UI and the backend.
 * @param cardId The id of the card from which to delete
 * @param drugId The id of the drug to remove
 */
function removePrescribingDrug(cardId, drugId) {
    const uiRefId = cardId.id.split('_')[1];
    this.deletePrescribingDrugFromUI(uiRefId, drugId);
    this.uncheckReRxForExistingPrescribedDrug(drugId)
}

/**
 * Deletes a prescribing drug from UI and calls deletePrescribe.
 * @param uiRefId The unique id for referencing the UI element.
 * @param drugId The id of the drug to delete.
 */
function deletePrescribingDrugFromUI(uiRefId, drugId) {
    this.removeElementFromUI(this.getPrescribingDrugCardByUiRefId(uiRefId));
    this.deletePrescribe(drugId);
}

/**
 * Removes a DOM element from the UI.
 * @param {HTMLElement} element The element to remove.
 */
function removeElementFromUI(element) {
    if (element)
        element.remove();
}

/**
 * Unchecks the "re-prescribe" checkbox for an existing prescribed drug and removes its ID from the re-prescribe list.
 * @param uiRefId The UI reference ID for the drug.
 * @param drugId The ID of the drug.
 */
function uncheckReRxForExistingPrescribedDrug(drugId) {
    const checkbox = this.getReRxCheckboxByUiRefId(drugId);
    if (checkbox)
        checkbox.checked = false;
    this.removeReRxDrugId(drugId);
}

/**
 * Gets the prescribing/staged drug container element by its UI reference ID.
 * @param uiRefId The UI reference ID.
 * @returns {HTMLElement|null} The drug container element, or null if not found.
 */
function getPrescribingDrugCardByUiRefId(uiRefId) {
    return $('set_' + uiRefId);
}

/**
 * Gets the re-prescribe checkbox element by its UI reference ID.
 * @param uiRefId The UI reference ID.
 * @returns {HTMLElement|null} The checkbox element, or null if not found.
 */
function getReRxCheckboxByUiRefId(uiRefId) {
    return $('reRxCheckBox_' + uiRefId);
}

        function updateQty(element) {
            var elemId = element.id;
            var ar = elemId.split("_");
            var rand = ar[1];
            var data = "randomId=" + rand + "&action=updateQty&quantity=" + element.value;
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateDrug";

            var rxMethod = "rxMethod_" + rand;
            var rxRoute = "rxRoute_" + rand;
            var rxFreq = "rxFreq_" + rand;
            var rxDrugForm = "rxDrugForm_" + rand;
            var rxDuration = "rxDuration_" + rand;
            var rxDurationUnit = "rxDurationUnit_" + rand;
            var rxAmt = "rxAmount_" + rand;
            var str;
            // var rxString="rxString_"+rand;
            var methodStr = "method_" + rand;
            var routeStr = "route_" + rand;
            var frequencyStr = "frequency_" + rand;
            var minimumStr = "minimum_" + rand;
            var maximumStr = "maximum_" + rand;
            var durationStr = "duration_" + rand;
            var durationUnitStr = "durationUnit_" + rand;
            var quantityStr = "quantityStr_" + rand;
            var unitNameStr = "unitName_" + rand;
            var prnStr = "prn_" + rand;
            var prnVal = "prnVal_" + rand;
            new Ajax.Request(url, {
                method: 'get', parameters: data, onSuccess: function (transport) {
                    var json = transport.responseText.evalJSON();
                    $(methodStr).innerHTML = json.method;
                    $(routeStr).innerHTML = json.route;
                    $(frequencyStr).innerHTML = json.frequency;
                    $(minimumStr).innerHTML = json.takeMin;
                    $(maximumStr).innerHTML = json.takeMax;
                    if (json.duration == null || json.duration == "null") {
                        $(durationStr).innerHTML = '';
                    } else {
                        $(durationStr).innerHTML = json.duration;
                    }
                    $(durationUnitStr).innerHTML = json.durationUnit;
                    $(quantityStr).innerHTML = json.calQuantity;
                    if (json.unitName != null && json.unitName != "null" && json.unitName != "NULL" && json.unitName != "Null") {
                        $(unitNameStr).innerHTML = json.unitName;
                    } else {
                        $(unitNameStr).innerHTML = '';
                    }
                    if (json.prn) {
                        $(prnStr).innerHTML = "prn";
                        $(prnVal).value = true;
                    } else {
                        $(prnStr).innerHTML = "";
                        $(prnVal).value = false;
                    }

                }
            });
            return true;
        }

        function parseIntr(element) {
            var elemId = element.id;
            var ar = elemId.split("_");
            var rand = ar[1];
            var instruction = "instruction=" + element.value + "&action=parseInstructions&randomId=" + rand;
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/UpdateScript.do?parameterValue=updateDrug";
            var quantity = "quantity_" + rand;
            var str;
            var methodStr = "method_" + rand;
            var routeStr = "route_" + rand;
            var frequencyStr = "frequency_" + rand;
            var minimumStr = "minimum_" + rand;
            var maximumStr = "maximum_" + rand;
            var durationStr = "duration_" + rand;
            var durationUnitStr = "durationUnit_" + rand;
            var quantityStr = "quantityStr_" + rand;
            var unitNameStr = "unitName_" + rand;
            var prnStr = "prn_" + rand;
            var prnVal = "prnVal_" + rand;
            new Ajax.Request(url, {
                method: 'get', parameters: instruction, asynchronous: false, onSuccess: function (transport) {
                    var json = transport.responseText.evalJSON();
                    if (json.policyViolations != null && json.policyViolations.length > 0) {
                        for (var x = 0; x < json.policyViolations.length; x++) {
                            alert(json.policyViolations[x]);
                        }
                        $("saveButton").disabled = true;
                        $("saveOnlyButton").disabled = true;
                    } else {
                        $("saveButton").disabled = false;
                        $("saveOnlyButton").disabled = false;
                    }

                    $(methodStr).innerHTML = json.method;
                    $(routeStr).innerHTML = json.route;
                    $(frequencyStr).innerHTML = json.frequency;
                    $(minimumStr).innerHTML = json.takeMin;
                    $(maximumStr).innerHTML = json.takeMax;
                    if (json.duration == null || json.duration == "null") {
                        $(durationStr).innerHTML = '';
                    } else {
                        $(durationStr).innerHTML = json.duration;
                    }
                    $(durationUnitStr).innerHTML = json.durationUnit;
                    if (json.unitName != null && json.unitName != "null" && json.unitName != "NULL" && json.unitName != "Null") {
                        $(unitNameStr).innerHTML = json.unitName;
                    } else {
                        $(unitNameStr).innerHTML = '';
                    }
                    if (json.calQuantity != 0) {
                        //this is oftentimes zero when re-prescribing a drug where the unitName != null.
                        //Until a more reliable calculated quantity is being returned, don't update if the calculated quantity is 0
                        //silently changing to 0 can be problematic in situations where the quantity has already been set
                        //to an appropriate value.
                        $(quantityStr).innerHTML = json.calQuantity;
                        if ($(unitNameStr).innerHTML != '')
                            $(quantity).value = $(quantityStr).innerHTML + " " + $(unitNameStr).innerHTML;
                        else
                            $(quantity).value = $(quantityStr).innerHTML;

                    }
                    if (json.prn) {
                        $(prnStr).innerHTML = "prn";
                        $(prnVal).value = true;
                    } else {
                        $(prnStr).innerHTML = "";
                        $(prnVal).value = false;
                    }
                }
            });
            return true;
        }

        function addLuCode(eleId, luCode) {
            $(eleId).value = $(eleId).value + " LU Code: " + luCode;
        }

        function getRenalDosingInformation(divId, atcCode) {
            var url = '<c:out value="${ctx}"/>/oscarRx/RenalDosing.jsp';
            var ran_number = Math.round(Math.random() * 1000000);
            var params = "demographicNo=<%=demoNo%>&atcCode=" + atcCode + "&divId=" + divId + "&rand=" + ran_number;
            new Ajax.Updater(divId, url, {
                method: 'get',
                parameters: params,
                insertion: Insertion.Bottom,
                asynchronous: true
            });
        }

        function getLUC(divId, randomId, din) {
            var url = '<c:out value="${ctx}"/>/oscarRx/LimitedUseCode.jsp';
            var params = "randomId=" + randomId + "&din=" + din;
            new Ajax.Updater(divId, url, {
                method: 'get',
                parameters: params,
                insertion: Insertion.Bottom,
                asynchronous: true
            });
        }

        function validateRxDate() {
            var x = true;
            jQuery('input[name^="rxDate__"]').each(function () {
                var str1 = jQuery(this).val();

                 var dt = str1.split("-");
                 if (dt.length>3) {
                 	jQuery(this).focus();
                     alert('Start Date wrong format! Must be yyyy or yyyy-mm or yyyy-mm-dd');
                     x = false;
                     return;
                 }

                 var dt1=1, mon1=0, yr1=parseInt(dt[0],10);
                 if (isNaN(yr1) || yr1<0 || yr1>9999) {
                 	jQuery(this).focus();
                     alert('Invalid Start Date! Please check the year');
                     x = false;
                     return;
                 }
                 if (dt.length>1) {
                 	mon1 = parseInt(dt[1],10)-1;
                 	if (isNaN(mon1) || mon1<0 || mon1>11) {
                 		jQuery(this).focus();
                 		alert('Invalid Start Date! Please check the month');
                         x = false;
                         return;
                 	}
                 }
                 if (dt.length>2) {
                 	dt1 = parseInt(dt[2],10);
                     if (isNaN(dt1) || dt1<1 || dt1>31) {
                     	jQuery(this).focus();
                         alert('Invalid Start Date! Please check the day');
                         x = false;
                         return;
                     }
                 }
                 var date1 = new Date(yr1, mon1, dt1);
                 var now  = new Date();

                 if(date1 > now) {
                 	jQuery(this).focus();
                     alert('Start Date cannot be in the future. (' + str1 +')');
                     x = false;
                     return;
     	        }
             });
             return x;
         }
      
        function validateWrittenDate() {
          var x = true;
            jQuery('input[name^="writtenDate_"]').each(function(){
                var str1  = jQuery(this).val();
    
                var dt = str1.split("-");
                if (dt.length>3) {
                  jQuery(this).focus();
                    alert('Written Date wrong format! Must be yyyy or yyyy-mm or yyyy-mm-dd');
                    x = false;
                    return;
                }
    
                var dt1 = 1, mon1 = 0, yr1 = parseInt(dt[0], 10);
                if (isNaN(yr1) || yr1 < 0 || yr1 > 9999) {
                    jQuery(this).focus();
                    alert('Invalid Written Date! Please check the year');
                    x = false;
                    return;
                }
                if (dt.length > 1) {
                    mon1 = parseInt(dt[1], 10) - 1;
                    if (isNaN(mon1) || mon1 < 0 || mon1 > 11) {
                        jQuery(this).focus();
                        alert('Invalid Written Date! Please check the month');
                        x = false;
                        return;
                    }
                }
                if (dt.length > 2) {
                    dt1 = parseInt(dt[2], 10);
                    if (isNaN(dt1) || dt1 < 1 || dt1 > 31) {
                        jQuery(this).focus();
                        alert('Invalid Written Date! Please check the day');
                        x = false;
                        return;
                    }
                }
                var date1 = new Date(yr1, mon1, dt1);
                var now = new Date();
    
                if (date1 > now) {
                    jQuery(this).focus();
                    alert('Written Date cannot be in the future. (' + str1 + ')');
                    x = false;
                    return;
                }
            });
            return x;
        }
    
    
        function updateSaveAllDrugsPrintCheckContinue() {
            updateSaveAllDrugsPrintContinue();
        }
    
        function updateSaveAllDrugsCheckContinue() {
            updateSaveAllDrugsContinue();
        }
    
        const CONFIRMATION_MESSAGE = {
            SINGLE: 'is 1 unstaged ReRx drug',
            MULTIPLE: (count) => "are " + count + " unstaged ReRx drugs"
        };
    
        const SAVE_WARNING = 'If you continue, the unstaged ReRx drug(s) will not be re-prescribed.';
        const SAVE_PROMPT = 'Are you sure you want to save this prescription?';
    
        function showUnstagedReRxConfirmation(onConfirm) {
            if (selectedReRxIDs.length === 0) {
                onConfirm();
                return;
            }
    
            const message = buildConfirmationMessage(selectedReRxIDs.length);
            if (confirm(message)) {
                cancelAndClearSelection();
                onConfirm();
            }
        }
    
        function buildConfirmationMessage(count) {
            const statusMessage = count === 1
                ? CONFIRMATION_MESSAGE.SINGLE
                : CONFIRMATION_MESSAGE.MULTIPLE(count);
            return "There " + statusMessage + ".\n" + SAVE_WARNING + "\n" + SAVE_PROMPT;
        }
    
<%
		// MyMeds study functionality removed
%>
    
    
        function updateSaveAllDrugsPrintContinue() {
            if (!validateWrittenDate()) {
                return false;
            }
            if (!validateRxDate()) {
                return false;
            }
    
        <%if (OscarProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>
        if(!checkMedTerm()){
          return false;
        }
        <%}%>
        setPharmacyId();
            var data=Form.serialize($('drugForm'));
            var url= "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateSaveAllDrugs&rand="+ Math.floor(Math.random()*10001);
            new Ajax.Request(url,
            {
                method: 'post', postBody: data, asynchronous: false,
                onSuccess: function (transport) {
                    callReplacementWebService("ListDrugs.jsp", 'drugProfile');
                    const hasDrugs = jQuery("[id^='drugName_']").length > 0;
                    if (hasDrugs) {
                        popForm2(null);
                    } else {
                        alert("Please add at least one drug first");
                    }
                    resetReRxDrugList();
                }
            });
            return false;
        }
        
        function updateSaveAllDrugsContinue(){
          if(!validateWrittenDate()) {
            return false;
          }
        if(!validateRxDate()) {
            return false;
          }
        
        <%if (OscarProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>
        if(!checkMedTerm()){
          return false;
        }
        <%}%>		
        setPharmacyId();
            var data = Form.serialize($('drugForm'));
            var url = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateSaveAllDrugs&rand="+ Math.floor(Math.random()*10001);
            new Ajax.Request(url,
            {
              method: 'post', postBody: data, asynchronous: false,
              onSuccess: function (transport) {
                  callReplacementWebService("ListDrugs.jsp", 'drugProfile');
                  resetReRxDrugList();
                  resetStash();
              }
            });
            return false;
        }
        
        /**
        * Gets the selected preferred pharmacy id and then sets it into the 
        * rxPharmacyId hidden input for submission with each drug in 
        * a prescription. 
        */
        function setPharmacyId() {
            var selectedPharmacy = jQuery("#Calcs option:selected").val();
            var selectedPharmacyId = "";
            if(selectedPharmacy) {
              selectedPharmacyId = JSON.parse(selectedPharmacy).id;
            }
            jQuery("#rxPharmacyId").val(selectedPharmacyId);
        }
    
        function checkEnterSendRx() {
            popupRxSearchWindow();
            return false;
        }
    
    
    <%if (OscarProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>
    function checkMedTerm() {
        var randId = 0;
        var isAnyTermChecked = false;
        jQuery("fieldset[id^='set_']").each(function() {
            randId = jQuery(this).attr("id").replace('set_','');
            isAnyTermChecked = isMedTermChecked(randId);
        });
        
        if (!isAnyTermChecked) {
          alert("Please review drug(s) and specify medication term!");
        } else {
          return true;
        }
        
        return false;
    }// end checkMedTerm
    
    function isMedTermChecked(rnd){
        var termChecked = false;
        var longTermY = jQuery("#longTermY_" + rnd);
        var longTermN = jQuery("#longTermN_" + rnd);
        
        var shortTerm = jQuery("#shortTerm_" + rnd);
        var medTermWrap = jQuery("#medTerm_" + rnd);
          
        if(longTermY.is(":checked") || longTermN.is(":checked")) {
          termChecked = true;
          medTermWrap.css('color', 'black');	
        } else {
          termChecked = false; 
          medTermWrap.css('color', 'red');
        }
        
        return termChecked;
    }
    
    <%} //end rx_strict_med_term check %>


    function medTermCheckOne(rnd, el){
        var longTerm = jQuery("#longTerm_" + rnd);
        var shortTerm = jQuery("#shortTerm_" + rnd);
      
        if(el.prop( "checked" )){
          if(el.attr("id")=="longTerm_" + rnd){
            shortTerm.attr("checked",false);
          }else{
            longTerm.attr("checked",false);
          }
        }	
    }


    jQuery( document ).ready(function() {
        jQuery( document ).on( 'change', '.med-term', function() {
            var randId = jQuery( this ).attr("id").split("_").pop();
           
            <%if (OscarProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>   
            isMedTermChecked(randId);
            <%}%> 
            
            //var el = jQuery( this );
            //medTermCheckOne(randId, el);
          });
    });
</script>

    <script>
        function updateShortTerm(rand,val) {
            if(val) {
                jQuery("#shortTerm_" + rand).prop("checked",true);
            } else {
                jQuery("#shortTerm_" + rand).prop("checked",false);
            }
        }

        function updateLongTerm(rand, repeatEl) {
            <%if("true".equals(OscarProperties.getInstance().getProperty("rx_select_long_term_when_repeat", "true"))) { %>
            var repeats = jQuery('#repeats_' + rand).val().trim();
            if (!isNaN(repeats) && repeats > 0) {
                jQuery("#longTermY_" + rand).prop("checked", true);
            }
            <% } %>
        }
    </script>
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/css.js"></script>
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/common.js"></script>
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/standardista-table-sorting.js"></script>

    </body>
</html>
