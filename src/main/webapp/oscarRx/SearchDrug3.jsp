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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
    SearchDrug3.jsp — Prescription Drug Search and Rx Writing Interface

    Purpose:
    Provides the interactive drug search, Rx staging, and prescription writing UI for CARLOS EMR.
    Displays the patient's current and archived drug profile, supports ReRx (re-prescribing),
    allergy checking, inactive drug detection, and previewing/printing prescriptions via ViewScript2.jsp.

    Features:
    - Drug search by brand name, ingredient, or natural health product using jQuery UI autocomplete
    - ReRx (re-prescribe) checkbox selection with allergy and inactive drug warnings
    - Drug staging via AJAX (rePrescribeMulti, renderRxStage)
    - Rx script preview in a Bootstrap modal (iframe loading ViewScript2.jsp)
    - Discontinue and other medication management actions
    - i18n support for all visible UI text via JSTL fmt:message with OWASP JS-safe encoding

    Parameters (request):
    - demographic_no  : Patient demographic ID (required)
    - providerNo      : Provider number for session context
    - scriptId        : Prescription script ID for preview (used by popForm2)
    - pharmacyId      : Pharmacy ID for print/fax (optional, passed to ViewScript2.jsp)

    @since 2001-01-01 (original OSCAR/McMaster); CARLOS fork maintained 2026+
--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.PharmacyInfo" %>
<%@page import="io.github.carlos_emr.CarlosProperties,io.github.carlos_emr.carlos.log.*" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@page import="java.util.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="java.util.List"%>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.model.Issue" %>
<%@ page import="io.github.carlos_emr.carlos.services.security.SecurityManager" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxPharmacyData" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="org.owasp.encoder.Encode" %>


<%
String rx_enhance = CarlosProperties.getInstance().getProperty("rx_enhance");
RxPatientData.Patient patient = (RxPatientData.Patient) request.getSession().getAttribute("Patient");

if (rx_enhance!=null && rx_enhance.equals("true")) {
	if (request.getParameter("ID") != null) {
%>
		<script>
			window.opener.location = window.opener.location;
			window.close();
		</script>
<%
	} 
}
    SecurityManager securityManager = new SecurityManager();
    String roleName2$ = (String)session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed=true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="r" reverse="<%=true%>">
	<%authed=false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
	if(!authed) {
		return;
	}
%>


<c:if test="${empty sessionScope.RxSessionBean}">
  <% response.sendRedirect("error.html"); %>
</c:if>
<c:if test="${not empty sessionScope.RxSessionBean}">
  <c:set var="bean" value="${sessionScope.RxSessionBean}" scope="page" />
  <c:if test="${not bean.valid}">
    <% response.sendRedirect("error.html"); %>
  </c:if>
</c:if>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<%
	RxSessionBean rxSessionBean = (RxSessionBean) pageContext.findAttribute("bean");

	String usefav = request.getParameter("usefav");
	String favid = request.getParameter("favid");
	int demoNo = rxSessionBean.getDemographicNo();

%>
<security:oscarSec roleName="<%=roleName2$%>"
	objectName='<%="_rx$"+demoNo%>' rights="o"
	reverse="<%=false%>">
    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.accessDenied"/>
    <% response.sendRedirect(request.getContextPath() + "/acctLocked.html"); %>
</security:oscarSec>

<%         
	LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = rxSessionBean.getProviderNo();
            //String reRxDrugId=request.getParameter("reRxDrugId");
            HashMap hm=(HashMap)session.getAttribute("profileViewSpec");
            boolean show_current=true;
            boolean show_all=true;
            boolean active=true;
            boolean inactive=true;
            //boolean all=true;
            boolean longterm_acute=true;
            boolean longterm_acute_inactive_external=true;
            if(hm!=null) {
             if(hm.get("show_current")!=null)
                show_current=(Boolean)hm.get("show_current");
             else
                show_current=false;
             if(hm.get("show_all")!=null)
                show_all=(Boolean)hm.get("show_all");
             else
                 show_all=false;
             if(hm.get("active")!=null)
                active=(Boolean)hm.get("active");
             else
                 active=false ;
             if(hm.get("inactive")!=null)
                inactive=(Boolean)hm.get("inactive");
             else
                 inactive=false;
             //if(hm.get("all")!=null)
             //   all=(Boolean)hm.get("all");
             //else
             //    all=false;
             if(hm.get("longterm_acute")!=null)
                longterm_acute=(Boolean)hm.get("longterm_acute");
             else
                longterm_acute=false;
             if(hm.get("longterm_acute_inactive_external")!=null)
                longterm_acute_inactive_external=(Boolean)hm.get("longterm_acute_inactive_external");
             else
                longterm_acute_inactive_external=false;
            }

            RxPharmacyData pharmacyData = new RxPharmacyData();
            List<PharmacyInfo> pharmacyList = pharmacyData.getPharmacyFromDemographic(Integer.toString(demoNo));

            String drugref_route = CarlosProperties.getInstance().getProperty("drugref_route");
            if (drugref_route == null) {
                drugref_route = "";
            }
            String[] d_route = ("Oral," + drugref_route).split(",");

            String annotation_display = CaseManagementNoteLink.DISP_PRESCRIP;

            RxPrescriptionData.Prescription[] prescribedDrugs;

  prescribedDrugs = patient.getPrescribedDrugScripts(); //this function only returns drugs which have an entry in prescription and drugs table
                        String script_no = "";
                        
            CaseManagementManager cmgmtMgr = SpringUtils.getBean(CaseManagementManager.class);
            List<Issue> issues = cmgmtMgr.getIssueInfoByCode(loggedInInfo.getLoggedInProviderNo(),"OMeds");
            String[] issueIds = new String[issues.size()];
	    int idx = 0;
	    for (Issue issue : issues) {
		issueIds[idx] = String.valueOf(issue.getId());
	    }
	   List<CaseManagementNote> notes = cmgmtMgr.getNotes(demoNo+"", issueIds);

%>

<!DOCTYPE html>
    <html lang="en">
    <head>


        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        
        <script type="text/javascript" >
        	var ctx = '${ ctx }';
        </script>
<script type="text/javascript" src="${ ctx }/library/jquery/jquery-3.7.1.min.js" ></script>
<script src="${ ctx }/library/jquery/jquery-compat.js"></script>
<script type="text/javascript" src="${ ctx }/library/jquery/jquery-ui-1.14.2.min.js" ></script>

		<link href="${ ctx }/css/searchDrug3.css" rel="stylesheet" type="text/css"/>

        <link rel="stylesheet" href="${ctx}/share/css/transitions.css" type="text/css" />
        <script type="text/javascript" src="${ctx}/js/global.js"></script>
        <script type="text/javascript" src="${ctx}/share/javascript/carlos-ajax.js"></script>
        <script type="text/javascript" src="${ctx}/share/javascript/screen.js"></script>
        <script type="text/javascript" src="${ctx}/share/javascript/rx.js"></script>
        <script type="text/javascript" src="${ctx}/share/javascript/Oscar.js"></script>
        <script type="text/javascript" src="${ctx}/js/checkDate.js"></script>


        <%-- UI library includes (Bootstrap, jQuery UI) --%>
		<link rel="stylesheet" type="text/css" href="${ctx}/library/bootstrap/5.3.3/css/bootstrap.min.css"/>
		<script type="text/javascript" src="${ctx}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
		<link rel="stylesheet" type="text/css" href="${ctx}/library/jquery/jquery-ui-1.14.2.min.css"/>

        <%-- Pre-declare all i18n messages used in JavaScript so they can be safely embedded
             in JavaScript string literals using OWASP forJavaScript() encoding --%>
        <fmt:setBundle basename="oscarResources"/>
        <fmt:message key="SearchDrug.js.handlerNotRemoved"         var="msg_handlerNotRemoved"/>
        <fmt:message key="SearchDrug.js.confirmMedRecComplete"     var="msg_confirmMedRecComplete"/>
        <fmt:message key="SearchDrug.js.medRecCompleted"           var="msg_medRecCompleted"/>
        <fmt:message key="SearchDrug.js.confirmChangeDrugName"     var="msg_confirmChangeDrugName"/>
        <fmt:message key="SearchDrug.js.confirmDeletePrescriptions" var="msg_confirmDeletePrescriptions"/>
        <fmt:message key="SearchDrug.js.confirmCustomNote"         var="msg_confirmCustomNote"/>
        <fmt:message key="SearchDrug.js.confirmCustomDrug"         var="msg_confirmCustomDrug"/>
        <fmt:message key="SearchDrug.js.startDateWrongFormat"      var="msg_startDateWrongFormat"/>
        <fmt:message key="SearchDrug.js.startDateInvalidYear"      var="msg_startDateInvalidYear"/>
        <fmt:message key="SearchDrug.js.startDateInvalidMonth"     var="msg_startDateInvalidMonth"/>
        <fmt:message key="SearchDrug.js.startDateInvalidDay"       var="msg_startDateInvalidDay"/>
        <fmt:message key="SearchDrug.js.startDateFuture"           var="msg_startDateFuture"/>
        <fmt:message key="SearchDrug.js.writtenDateWrongFormat"    var="msg_writtenDateWrongFormat"/>
        <fmt:message key="SearchDrug.js.writtenDateInvalidYear"    var="msg_writtenDateInvalidYear"/>
        <fmt:message key="SearchDrug.js.writtenDateInvalidMonth"   var="msg_writtenDateInvalidMonth"/>
        <fmt:message key="SearchDrug.js.writtenDateInvalidDay"     var="msg_writtenDateInvalidDay"/>
        <fmt:message key="SearchDrug.js.writtenDateFuture"         var="msg_writtenDateFuture"/>
        <fmt:message key="SearchDrug.js.pleaseAddDrugFirst"        var="msg_pleaseAddDrugFirst"/>
        <fmt:message key="SearchDrug.js.reviewDrugSpecifyTerm"     var="msg_reviewDrugSpecifyTerm"/>
        <fmt:message key="SearchDrug.js.unstagedReRxSingle"        var="msg_unstagedReRxSingle"/>
        <fmt:message key="SearchDrug.js.unstagedReRxMultiple"      var="msg_unstagedReRxMultiple"/>
        <fmt:message key="SearchDrug.js.saveWarning"               var="msg_saveWarning"/>
        <fmt:message key="SearchDrug.js.savePrompt"                var="msg_savePrompt"/>
        <fmt:message key="oscarRx.Preview.EditRx"                  var="msg_editRx"/>

        <script type="text/javascript">
            let selectedReRxIDs = [];
            // i18n message strings for JavaScript alerts and confirm dialogs
            var jsMsg = {
                handlerNotRemoved: '${e:forJavaScript(msg_handlerNotRemoved)}',
                confirmMedRecComplete: '${e:forJavaScript(msg_confirmMedRecComplete)}',
                medRecCompleted: '${e:forJavaScript(msg_medRecCompleted)}',
                confirmChangeDrugName: '${e:forJavaScript(msg_confirmChangeDrugName)}',
                confirmDeletePrescriptions: '${e:forJavaScript(msg_confirmDeletePrescriptions)}',
                confirmCustomNote: '${e:forJavaScript(msg_confirmCustomNote)}',
                confirmCustomDrug: '${e:forJavaScript(msg_confirmCustomDrug)}',
                startDateWrongFormat: '${e:forJavaScript(msg_startDateWrongFormat)}',
                startDateInvalidYear: '${e:forJavaScript(msg_startDateInvalidYear)}',
                startDateInvalidMonth: '${e:forJavaScript(msg_startDateInvalidMonth)}',
                startDateInvalidDay: '${e:forJavaScript(msg_startDateInvalidDay)}',
                startDateFuture: '${e:forJavaScript(msg_startDateFuture)}',
                writtenDateWrongFormat: '${e:forJavaScript(msg_writtenDateWrongFormat)}',
                writtenDateInvalidYear: '${e:forJavaScript(msg_writtenDateInvalidYear)}',
                writtenDateInvalidMonth: '${e:forJavaScript(msg_writtenDateInvalidMonth)}',
                writtenDateInvalidDay: '${e:forJavaScript(msg_writtenDateInvalidDay)}',
                writtenDateFuture: '${e:forJavaScript(msg_writtenDateFuture)}',
                pleaseAddDrugFirst: '${e:forJavaScript(msg_pleaseAddDrugFirst)}',
                reviewDrugSpecifyTerm: '${e:forJavaScript(msg_reviewDrugSpecifyTerm)}',
                unstagedReRxSingle: '${e:forJavaScript(msg_unstagedReRxSingle)}',
                unstagedReRxMultiple: '${e:forJavaScript(msg_unstagedReRxMultiple)}',
                saveWarning: '${e:forJavaScript(msg_saveWarning)}',
                savePrompt: '${e:forJavaScript(msg_savePrompt)}'
            };
	        function saveLinks(randNumber) {
	            document.getElementById('method_'+randNumber).onblur();
	            document.getElementById('route_'+randNumber).onblur();
	            document.getElementById('frequency_'+randNumber).onblur();
	            document.getElementById('minimum_'+randNumber).onblur();
	            document.getElementById('maximum_'+randNumber).onblur();
	            document.getElementById('duration_'+randNumber).onblur();
	            document.getElementById('durationUnit_'+randNumber).onblur();
	        }


	        function handleEnter(inField, ev){
	            var charCode;
	            if(ev && ev.which) {
	                charCode=ev.which;
	            }else if(window.event){
	                ev=window.event;
	                charCode=ev.keyCode;
	            }
	            var id=inField.id.split("_")[1];
	            if(charCode===13) {
	                showHideSpecInst('siAutoComplete_'+id);
	            }
	        }

        //has to be in here, not prescribe.jsp for it to work in IE 6/7 and probably 8.
        function showHideSpecInst(elementId){
            var el = document.getElementById(elementId);
            if(el.style.display === 'none' || getComputedStyle(el).display === 'none'){
                el.style.display = '';
                el.classList.remove('carlos-collapsed');
            }else{
                el.classList.add('carlos-collapsed');
                el.style.display = 'none';
            }
          }

			function resetReRxDrugList() {
				var rand = Math.floor(Math.random() * 10001);
				var url = ctx + "/oscarRx/deleteRx.do?parameterValue=clearReRxDrugList";
				var data = "rand=" + rand;
				CarlosAjax.request(url, {
					method: 'post', parameters: data, onSuccess: function (transport) {
						// updateCurrentInteractions();
					}
				});
			}

			function onPrint(cfgPage) {
				var docF = document.getElementById('printFormDD');

                docF.action = "<%= request.getContextPath() %>/form/createpdf?__title=Rx&__cfgfile=" + cfgPage + "&__template=a6blank";
                docF.target="_blank";
                docF.submit();
               return true;
            }

            function buildRoute() {

                pickRoute = "";
            }



           function popupRxSearchWindow(){
               var winX = (document.all)?window.screenLeft:window.screenX;
               var winY = (document.all)?window.screenTop:window.screenY;

               var top = winY+70;
               var left = winX+110;
                var url = ctx + "/oscarRx/searchDrug.do?rx2=true&searchString=" + encodeURIComponent(document.getElementById('searchString').value);
               popup2(600, 800, top, left, url, 'windowNameRxSearch<%=demoNo%>');

           }


           function popupRxReasonWindow(demographic,id){
               var winX = (document.all)?window.screenLeft:window.screenX;
               var winY = (document.all)?window.screenTop:window.screenY;

               var top = winY+70;
               var left = winX+110;
                var url = ctx + "/oscarRx/SelectReason.jsp?demographicNo=" + demographic + "&drugId=" + encodeURIComponent(id);
               popup2(575, 650, top, left, url, 'windowNameRxReason<%=demoNo%>');

           }


           var highlightMatch = function(full, snippet, matchindex) {
                return "<a title='"+full+"'>"+full.substring(0, matchindex) +
                "<span class=match>" +full.substr(matchindex, snippet.length) + "</span>" + full.substring(matchindex + snippet.length)+"</a>";
           };

           var highlightMatchInactiveMatchWord = function(full, snippet, matchindex) {
               //oscarLog(full+"--"+snippet+"--"+matchindex);
                return "<a title='"+full+"'>"+"<span class=matchInactive>"+full.substring(0, matchindex) +
                "<span class=match>" +full.substr(matchindex, snippet.length) +"</span>" + full.substring(matchindex + snippet.length)+"</span>"+"</a>";
           };
           var highlightMatchInactive = function(full, snippet, matchindex) {
               /* oscarLog(full+"--"+snippet+"--"+matchindex);
                oscarLog(" aa "+full.substring(0, matchindex) );
                oscarLog(" bb "+full.substr(matchindex, snippet.length) );
                oscarLog(" cc "+ full.substring(matchindex + snippet.length));*/
               /*return "<a title='"+full+"'>"+"<span class=matchInactive>"+full.substring(0, matchindex) +
                full.substr(matchindex, snippet.length) +full.substring(matchindex + snippet.length)+"</span>"+"</a>";*/
                return "<a title='"+full+"'>"+"<span class=matchInactive>"+full+"</span>"+"</a>";
           };
           var resultFormatter = function(oResultData, sQuery, sResultMatch) {
               //oscarLog("oResultData, sQuery, sResultMatch="+oResultData+"--"+sQuery+"--"+sResultMatch);
               //oscarLog("oResultData[0]="+oResultData[0]);
               //oscarLog("oResultData.name="+oResultData.name);
               //oscarLog("oResultData.name="+oResultData.id);
               var query = sQuery.toUpperCase();
               var drugName = oResultData[0];

               var mIndex = drugName.toUpperCase().indexOf(query);
               var display = '';

               if(mIndex > -1){
                   display = highlightMatch(drugName,query,mIndex);
               }else{
                   display = drugName;
               }
               return  display;
           };
            var resultFormatter2 = function(oResultData, sQuery, sResultMatch) {
               /*oscarLog("oResultData, sQuery, sResultMatch="+oResultData+"--"+sQuery+"--"+sResultMatch);
               oscarLog("oResultData[0]="+oResultData[0]);
               oscarLog("oResultData.name="+oResultData.name);
               oscarLog("oResultData.name="+oResultData.id);*/
               var query = sQuery.toUpperCase();
               var drugName = oResultData.name;
               var isInactive=oResultData.isInactive;
               //oscarLog("isInactive="+isInactive);

               var mIndex = drugName.toUpperCase().indexOf(query);
               var display = '';
               if(mIndex>-1 && (isInactive=='true'||isInactive==true)){ //match and inactive
                   display=highlightMatchInactiveMatchWord(drugName,query,mIndex);
               }
               else if(mIndex > -1 && (isInactive=='false'||isInactive==false || isInactive==undefined || isInactive==null)){ //match and active
                   display = highlightMatch(drugName,query,mIndex);
               }else if(mIndex<=-1 && (isInactive=='true'||isInactive==true)){//no match and inactive
                   display=highlightMatchInactive(drugName,query,mIndex);
               }
               else{//active and no match
                   display = drugName;
               }
               
               
               return  display;
           };
        </script>

        <script type="text/javascript">
addEvent(window, "load", sortables_init);

var SORT_COLUMN_INDEX;

function sortables_init() {
    // Find all tables with class sortable and make them sortable

    if (!document.getElementsByTagName) return;

    tbls = document.getElementsByTagName("table");

    for (ti=0;ti<tbls.length;ti++) {
        thisTbl = tbls[ti];

        if (((' '+thisTbl.className+' ').indexOf("sortable") != -1) && (thisTbl.id)) {
            //initTable(thisTbl.id);
            ts_makeSortable(thisTbl);
        }
    }
}

function ts_makeSortable(table) {
    oscarLog('making '+table+' sortable');
    if (table.rows && table.rows.length > 0) {
        var firstRow = table.rows[0];
    }
    if (!firstRow) return;
    oscarLog('Gets past here');

    // We have a first row: assume it's the header, and make its contents clickable links
    for (var i=0;i<firstRow.cells.length;i++) {
        var cell = firstRow.cells[i];
        var txt = ts_getInnerText(cell);
        var link = document.createElement('a');
        link.href = '#';
        link.className = 'sortheader';
        link.setAttribute('onclick', 'ts_resortTable(this, '+i+');return false;');
        link.textContent = txt;
        var span = document.createElement('span');
        span.className = 'sortarrow';
        link.appendChild(span);
        cell.textContent = '';
        cell.appendChild(link);
    }
}

function ts_getInnerText(el) {
	if (typeof el == "string") return el;
	if (typeof el == "undefined") { return el };
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

function ts_resortTable(lnk,clid) {
    // get the span
    var span;
    for (var ci=0;ci<lnk.childNodes.length;ci++) {
        if (lnk.childNodes[ci].tagName && lnk.childNodes[ci].tagName.toLowerCase() == 'span') span = lnk.childNodes[ci];
    }
    var spantext = ts_getInnerText(span);
    var td = lnk.parentNode;
    var column = clid;
    var table = getParent(td,'TABLE');

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
    for (i=0;i<table.rows[0].length;i++) { firstRow[i] = table.rows[0][i]; }
    for (j=1;j<table.rows.length;j++) { newRows[j-1] = table.rows[j]; }

    newRows.sort(sortfn);

    if (span.getAttribute("sortdir") == 'down') {
        ARROW = '&nbsp;&nbsp;&uarr;';
        newRows.reverse();
        span.setAttribute('sortdir','up');
    } else {
        ARROW = '&nbsp;&nbsp;&darr;';
        span.setAttribute('sortdir','down');
    }

    // We appendChild rows that already exist to the tbody, so it moves them rather than creating new ones
    // don't do sortbottom rows
    for (i=0;i<newRows.length;i++) { if (!newRows[i].className || (newRows[i].className && (newRows[i].className.indexOf('sortbottom') == -1))) table.tBodies[0].appendChild(newRows[i]);}
    // do sortbottom rows only
    for (i=0;i<newRows.length;i++) { if (newRows[i].className && (newRows[i].className.indexOf('sortbottom') != -1)) table.tBodies[0].appendChild(newRows[i]);}

    // Delete any other arrows there may be showing
    var allspans = document.getElementsByTagName("span");
    for (var ci=0;ci<allspans.length;ci++) {
        if (allspans[ci].className == 'sortarrow') {
            if (getParent(allspans[ci],"table") == getParent(lnk,"table")) { // in the same table as us?
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
function ts_sort_date(a,b) {
    // y2k notes: two digit years less than 50 are treated as 20XX, greater than 50 are treated as 19XX
    aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]);
    bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]);
    if (aa.length == 10) {
        dt1 = aa.substr(6,4)+aa.substr(3,2)+aa.substr(0,2);
    } else {
        yr = aa.substr(6,2);
        if (parseInt(yr) < 50) { yr = '20'+yr; } else { yr = '19'+yr; }
        dt1 = yr+aa.substr(3,2)+aa.substr(0,2);
    }
    if (bb.length == 10) {
        dt2 = bb.substr(6,4)+bb.substr(3,2)+bb.substr(0,2);
    } else {
        yr = bb.substr(6,2);
        if (parseInt(yr) < 50) { yr = '20'+yr; } else { yr = '19'+yr; }
        dt2 = yr+bb.substr(3,2)+bb.substr(0,2);
    }
    if (dt1==dt2) return 0;
    if (dt1<dt2) return -1;
    return 1;
}

function ts_sort_currency(a,b) {
    aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]).replace(/[^0-9.]/g,'');
    bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]).replace(/[^0-9.]/g,'');
    return parseFloat(aa) - parseFloat(bb);
}

function ts_sort_numeric(a,b) {
    aa = parseFloat(ts_getInnerText(a.cells[SORT_COLUMN_INDEX]));
    if (isNaN(aa)) aa = 0;
    bb = parseFloat(ts_getInnerText(b.cells[SORT_COLUMN_INDEX]));
    if (isNaN(bb)) bb = 0;
    return aa-bb;
}

function ts_sort_caseinsensitive(a,b) {
    aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]).toLowerCase();
    bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]).toLowerCase();
    if (aa==bb) return 0;
    if (aa<bb) return -1;
    return 1;
}

function ts_sort_default(a,b) {
    aa = ts_getInnerText(a.cells[SORT_COLUMN_INDEX]);
    bb = ts_getInnerText(b.cells[SORT_COLUMN_INDEX]);
    if (aa==bb) return 0;
    if (aa<bb) return -1;
    return 1;
}


function addEvent(elm, evType, fn, useCapture)
// addEvent and removeEvent
// cross-browser event handling for IE5+,  NS6 and Mozilla
// By Scott Andrew
{
  if (elm.addEventListener){
    elm.addEventListener(evType, fn, useCapture);
    return true;
  } else if (elm.attachEvent){
    var r = elm.attachEvent("on"+evType, fn);
    return r;
  } else {
    alert(jsMsg.handlerNotRemoved);
  }
}
function checkFav(){
    //oscarLog("****** in checkFav");
    var usefav='<%=usefav%>';
    var favid='<%=favid%>';
    if(usefav=="true" && favid!=null && favid!='null'){
        //oscarLog("****** favid "+favid);
        useFav2(favid);
    }else{}
}

function renderRxStage() {
	document.getElementById('rxText').style.display = '';
	document.getElementById('prescriptionStageSet').style.display = '';
}

     //not used , represcribe a drug
    function represcribeOnLoad(drugId){
        var data="method=saveReRxDrugIdToStash&drugId="+encodeURIComponent(drugId) + "&rand=" + Math.floor(Math.random()*10001);
        var url= ctx + "/oscarRx/rePrescribe2.do";
        CarlosAjax.updater('rxText',url, {method:'POST',parameters:data,
          requestHeaders: { 'Accept': 'application/json' },
          evalScripts:true,insertion: 'bottom',
            onSuccess:function(transport){
	            renderRxStage();
					}
				});

    }


    function moveDrugDown(drugId,swapDrugId,demographicNo) {
    	CarlosAjax.request('<c:out value="${ctx}"/>/oscarRx/reorderDrug.do', {
  		  method: 'post',
  		  parameters: {method: 'update', direction: 'down', drugId: drugId, swapDrugId: swapDrugId, demographicNo: demographicNo},
  		  onSuccess: function(transport) {
  			callReplacementWebService("ListDrugs.jsp",'drugProfile');
            resetReRxDrugList();
            resetStash();
  		  }
  		});
    }

    function moveDrugUp(drugId,swapDrugId,demographicNo) {
    	CarlosAjax.request('<c:out value="${ctx}"/>/oscarRx/reorderDrug.do', {
    		  method: 'post',
    		  parameters: {method: 'update', direction: 'up', drugId: drugId, swapDrugId: swapDrugId, demographicNo: demographicNo},
    		  onSuccess: function(transport) {
    			  callReplacementWebService("ListDrugs.jsp",'drugProfile');
                  resetReRxDrugList();
                  resetStash();
    		  }
    		});
    }

	function showPreviousPrints(scriptNo) {
                popupWindow(720, 700, ctx + '/oscarRx/ShowPreviousPrints.jsp?scriptNo=' + scriptNo, 'ShowPreviousPrints')
	}

    var Lst;

    function CngClass(obj){
    	document.getElementById("selected_default").removeAttribute("style");
     if (Lst) Lst.className='';
     obj.className='selected';
     Lst=obj;
    }

    function toggleStartDateUnknown(rand) {
    	var cb = document.getElementById('startDateUnknown_'+rand);
    	var txt = document.getElementById('rxDate_'+rand);
    	if(cb.checked) {
    		<%
    			java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd");
    			String today = formatter.format(new java.util.Date());
    		%>
    		txt.disabled=true;
    		txt.value='<%=today%>';
    	} else {
    		txt.disabled=false;
    	}
    }


    //this is a SJHH specific feature
    function completeMedRec() {
   	 var ok = confirm(jsMsg.confirmMedRecComplete);
   	 if(ok) {
					var url = ctx + "/oscarRx/completeMedRec.jsp";
   		 var data="demographicNo=<%=rxSessionBean.getDemographicNo()%>";
   		 CarlosAjax.request(url,{method: 'post',parameters:data,onSuccess:function(transport){
                alert(jsMsg.medRecCompleted)
            }});
   	 }
    }

    function printDrugProfile() {
    	var ids=[];
    	jQuery("input[type='checkbox'][id ^= 'reRxCheckBox']").each(function(){
    		if(jQuery(this).is(":checked")) {
    			var name = jQuery(this).attr('name').substring(9);
    			ids.push(name);
    		}
    	});
    	if(ids.length>0) {
                    popupWindow(720, 700, ctx + '/oscarRx/PrintDrugProfile2.jsp?ids=' + ids.join(','), 'PrintDrugProfile');
    	} else {
                    popupWindow(720, 700, ctx + '/oscarRx/PrintDrugProfile2.jsp', 'PrintDrugProfile');
    	}
    }
    
</script>
      <style media="screen">
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
          padding: 2px 10px 10px 10px;
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
        #statusDisplay {
          font-size: x-small;
          display: flex;
          flex-direction: row;
          justify-content: flex-end;
          gap:5px;

        }
        #statusDisplay label{
          font-weight: bold;
        }

      </style>
        <style media="print">
                   noprint{
                       display:none;
                   }
                   justforprint{
                       float:left;
                   }
        </style>
      <title>Rx-<%= Encode.forHtml(patient.getSurname()) %></title>
    </head>

    <%
        boolean showall = false;

		if (request.getParameter("show") != null) if (request.getParameter("show").equals("all")) showall = true;
    %>



    <body onload="checkFav();iterateStash();rxPageSizeSelect();checkReRxLongTerm();load()" class="yui-skin-sam">

    <div id="searchDrug3Wrapper">
    <%=WebUtils.popErrorAndInfoMessagesAsHtml(session)%>
        <table id="AutoNumber1">
            <%@ include file="TopLinks2.jspf" %><!-- Row On included here-->
            <tr>
                <td height="100%" ><%@ include file="SideLinksEditFavorites2.jsp"%></td>
                <td style="padding-right:15px;"><!--Column Two Row Two-->

                    <div class="floatingWindow" id="reRxConfirmBox">
                        <p style="margin-bottom: 12px; font-size: 11px; text-align: end">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReRxConfirmPrefix"/> <span style="font-weight: bold" id="selectedCount">0</span> <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReRxConfirmSuffix"/>
                        </p>
                        <div style="display: flex; gap: 10px; justify-content: flex-end;">
                            <input type="button" name="cancel" class="ControlPushButton" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgCancel"/>"
                                   onclick="cancelAndClearSelection()" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgCancel"/>">
                            <input type="button" name="stage" class="ControlPushButton" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgStageMedication"/>"
                                   onclick="stageSelectedReRxMedications()" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgStageMedications"/>">
                        </div>
                    </div>

                    <table style="border-collapse: collapse">
                        <tr id="medicationManagementRow">
                            <td>
							<%if(securityManager.hasWriteAccess("_rx",roleName2$,true)) {%>
                                <form action="${pageContext.request.contextPath}/oscarRx/searchDrug.do"  onsubmit="return checkEnterSendRx();" style="display: inline; margin-bottom:0;" id="drugForm" name="drugForm" method="post">
                                    <input type="hidden" property="demographicNo" value="<%=Integer.toString(patient.getDemographicNo())%>" />
                                    <table>
                                        <tr id="prescriptionStageRow">
                                            <td colspan="2">

                                                <div id="prescriptionStageSet" style="display:none">

                                                    <div id="interactingDrugErrorMsg" style="display:none"></div>

                                                    <div id="rxText"></div>
                                                        <%-- Prescriptions are staged here via the prescribe.jsp widget --%>

                                                    <input type="hidden" id="deleteOnCloseRxBox" value="false"/>
                                                    <input type="hidden" property="demographicNo" value="<%=patient.getDemographicNo()%>"/>

                                                </div>
                                                <input type="hidden" id="rxPharmacyId" name="rxPharmacyId" value="" />

                                            </td>
                                        </tr>
                                        <tr id="searchPrescriptionRow">
                                            <td>
                                                <div id="searchDrugSet">
                                                    <div id="searchDrugAutocompleteSet" style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                                                        <label for="searchString" style="white-space: nowrap; margin: 0;"><fmt:message key="SearchDrug.drugSearchTextBox"  /></label>
                                                        <input type="text" class="ui-widget-content" id="searchString" name="searchString" autocomplete="off" style="border: 1px solid #000;">
                                                        <div id="autocomplete_choices"></div>
                                                    </div>
                                                    <div id="advanceSearchParameters">
                                                        <fieldset id="drugCategorySet">
                                                            <input type="radio" id="allCategories" name="method"
                                                                   value="searchAllCategories" class="trigger"
                                                                   checked="checked"/>
                                                            <label for="allCategories"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.drugCategory.all"/></label>

                                                            <input type="radio" id="brandName" name="method"
                                                                   value="searchBrandName" class="trigger" disabled/>
                                                            <label for="brandName"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.drugCategory.brand"/></label>

                                                            <input type="radio" id="genericName" name="method"
                                                                   value="searchGenericName" class="trigger" disabled/>
                                                            <label for="genericName"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.drugCategory.ingredient"/></label>

                                                            <input type="radio" id="naturalRemedy" name="method"
                                                                   disabled="disabled"
                                                                   value="searchNaturalRemedy" class="trigger" />
                                                            <label for="naturalRemedy"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.drugCategory.natural"/></label>
                                                        </fieldset>
                                                        <fieldset id="searchParamSet">
                                                            <input type="radio" id="wildCardRight" name="wildcard"
                                                                   value="true" checked="checked" />
                                                            <label title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.searchParam.exactTitle"/>"
                                                                   for="wildCardRight"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.searchParam.exact"/></label>

                                                            <input type="radio" id="wildCardBoth" name="wildcard"
                                                                   value="false" />
                                                            <label title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.searchParam.anyTitle"/>"
                                                                   for="wildCardBoth"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.searchParam.any"/></label>
                                                        </fieldset>
                                                    </div>
                                                </div>
                                                <span id="indicator1" style="display: none"> <!--img src="/images/spinner.gif" alt="Working..." --></span>
                                            </td>
                                            <td>
                                                <div id="searchDrugsButtonSet">
                                                    <input type="button" name="search" class="btn btn-secondary btn-sm"  value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgSearch"/>" onclick="popupRxSearchWindow();" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.Search"/>">
                                                    <input id="customDrug" type="button" class="btn btn-secondary btn-sm" onclick="customWarning2();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgCustomDrugRx3"/>" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.CustomDrug"/>" />
                                                    <input id="customNote" type="button" class="btn btn-secondary btn-sm"  onclick="customNoteWarning();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgNoteRx3"/>" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.CustomNote"/>"/>
                                                    <input id="reset" type="button" class="btn btn-secondary btn-sm" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.clearPending"/>"   onclick="resetStash();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgResetPrescriptionRx3"/>"/>
                                                    <%if (CarlosProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
                                                    <a href="javascript:goOMD();" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.OMD"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgOMDLookup"/></a>
                                                    <%}%>
                                                    <security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="x">
                                                    <input id="saveButton" type="button"  class="btn btn-primary btn-sm" onclick="updateSaveAllDrugsPrintCheckContinue();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgSaveAndPrint"/>" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.SaveAndPrint"/>" />
                                                    </security:oscarSec>
                                                    <input id="saveOnlyButton" type="button"  class="btn btn-secondary btn-sm" onclick="updateSaveAllDrugsCheckContinue();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgSaveOnly"/>" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.help.Save"/>"/>
                                                </div>
                                            </td>

                                        </tr>
                                    </table>

                                </form>
                                <div id="previewForm" style="display:none;"></div>
                                <%} %>
                            </td>
                        </tr>
						<tr id="patientDrugListRow"><!--put this left-->
                            <td>
                                    <table><!--drug profile, view and listdrugs.jsp-->
                                        <tr>
                                            <td>
                                                <div class="DivContentSectionHead">
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.section2Title" />
                                                    &nbsp;
                                                    <a href="javascript:void(0)" onClick="printDrugProfile();"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.Print"/></a>
                                                    &nbsp;
													<%if(securityManager.hasWriteAccess("_rx",roleName2$,true)) {%>
                                                    <a href="#" onclick="var rp=document.getElementById('reprint');rp.style.display=(rp.style.display==='none')?'':'none';return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.Reprint"/></a>
                                                    &nbsp;
                                                    <a href="javascript:void(0);" id="cmdRePrescribe" onclick="RePrescribeLongTerm();" style="width: 200px" ><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReprescribeLongTermMed"/></a>
                                                    &nbsp;
													<% } %>
                                                    <a href="javascript:popupWindow(720,920, ctx + '/oscarRx/chartDrugProfile.jsp?demographic_no=<%=demoNo%>','PrintDrugProfile2')"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.timelineDrugProfile"/></a>
                                                    &nbsp;
                                                    &nbsp;&nbsp;
                                                </div>

                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="height: 150px; overflow: auto; border: thin solid #DCDCDC; display: none;" id="reprint">


                        <% for (int i = 0; prescribedDrugs.length > i; i++) {
                            RxPrescriptionData.Prescription drug =  prescribedDrugs[i];
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
                                                                            <a href="#"  title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.rxChangeProfileViewMessage"/>"
                                                                            	onclick="popupPage(230,860,'../setProviderStaleDate.do?method=viewRxProfileView');" style="color:red;text-decoration:none" >
                                                                            	<fmt:message key="provider.rxChangeProfileView"/>
                                                                            </a>
                                                                        </td>

																	    <td>

																	       <table class="legend_items" align="left">
																			<tr>
																				<%if(show_current){%>
																				<td >
		                                                                            <a href="javascript:void(0);" onclick="callReplacementWebService('ListDrugs.jsp','drugProfile');CngClass(this);" 
		                                                                            	id="selected_default" style="color:#000000; text-decoration: none;"
		                                                                            	TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key='SearchDrug.msgShowCurrentDesc'/>">
		                                                                            	<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgShowCurrent"/>
		                                                                            </a>
	                                                                            </td>
																				<%}if(show_all){%>
	                                                                            <td >
																					<a href="javascript:void(0);" onclick="callReplacementWebService('ListDrugs.jsp?show=all','drugProfile');CngClass(this);" 
																						Title="<fmt:setBundle basename="oscarResources"/><fmt:message key='SearchDrug.msgShowAllDesc'/>">
																						<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgShowAll"/>
																					</a>
	                                                                            </td>
																				<%}if(active){%>
																				<td >
																					<a href="javascript:void(0);" onclick="callReplacementWebService('ListDrugs.jsp?status=active','drugProfile');CngClass(this);" 
																						TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key='SearchDrug.msgActiveDesc'/>">
																						<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgActive"/>
																					</a>
	                                                                            </td>
																				<%}if(inactive){%>
																				<td >
																					<a href="javascript:void(0);" onclick="callReplacementWebService('ListDrugs.jsp?status=inactive','drugProfile');CngClass(this);" 
																						TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key='SearchDrug.msgInactiveDesc'/>">
																						<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgInactive"/>
																					</a>
	                                                                            </td>
																				<%} if(!CarlosProperties.getInstance().getProperty("rx.profile_legend.hide","false").equals("true")) {

																				if(longterm_acute){%>
																				<td >
																					<a href="javascript:void(0);" onclick="callReplacementWebService('ListDrugs.jsp?longTermOnly=true&heading=Long Term Meds','drugProfile'); callAdditionWebService('ListDrugs.jsp?longTermOnly=acute&heading=Acute','drugProfile');CngClass(this);" 
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgLongTermAcuteDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgLongTermAcute"/>
																					</a>
	                                                                            </td>
																				<%}if(longterm_acute_inactive_external){%>
																				<td >
																					<a href="javascript:void(0);" onclick="callReplacementWebService('ListDrugs.jsp?longTermOnly=true&heading=Long Term Meds','drugProfile'); callAdditionWebService('ListDrugs.jsp?longTermOnly=acute&heading=Acute&status=active','drugProfile');callAdditionWebService('ListDrugs.jsp?longTermOnly=acute&heading=Inactive&status=inactive','drugProfile');callAdditionWebService('ListDrugs.jsp?heading=External&drugLocation=external','drugProfile');CngClass(this);" 
                                                                                   TITLE="<fmt:setBundle basename='oscarResources'/><fmt:message key='SearchDrug.msgLongTermAcuteInactiveExternalDesc'/>">
                                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgLongTermAcuteInactiveExternal"/>
																					</a>
	                                                                            </td>
																				<%}
																				}
																				%>
																			</tr>
																			</table>

																			</td>

                                                                    </tr>
                                                                </table>

                                                            <div id="drugProfile" ></div>

                                                            <div id="themeLegend">
                                                                <a href="javascript:void(0);" class="currentDrug"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.currentDrug"/></a> |
                                                                <%if(!CarlosProperties.getInstance().getProperty("rx.delete_drug.hide","false").equals("true")) {%>
                                                                <a href="javascript:void(0);" class="archivedDrug"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.archivedDrug"/></a> |
                                                                <%} %>
                                                                <a href="javascript:void(0);" class="expireInReference"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.expireInReference"/></a> |
                                                                <a href="javascript:void(0);" class="expiredDrug"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.expiredDrug"/></a> |
                                                                <a href="javascript:void(0);" class="longTermMed"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.longTermMed"/></a> |
                                                                <a href="javascript:void(0);" class="discontinued"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.discontinued"/></a> |
                                                                <a href="javascript:void(0);" class="external"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.legend.external"/></a>
                                                            </div>

                                                            <form action="/oscarRx/rePrescribe">
                                                                <input type="hidden" property="drugList" />
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
                                    <table><!--drug profile, view and listdrugs.jsp-->
                                        <tr>
                                            <td>
                                                <div class="DivContentSectionHead">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.otherMedications"/>
                                             </div>
                                            </td>
                                        </tr>
                                        <tr>
                                        	<td>
                                        	<table class="sortable" id="OMedsTabls" width="50%" border="0" cellpadding="3">
                                        		<tr>
                                        			<th align="left"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.OMeds.dateEntered"/></th>
                                        			<th align="left"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.OMeds.medication"/></th>
                                        		</tr>
                                        		 <%
                                        		// java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd");
                                     			
                                			for(CaseManagementNote note:notes) {
                            				if (!note.isLocked() && !note.isArchived()) {
                            					String str = note.getNote();
                            					%>
                            						<tr>
                            							<td><%=formatter.format(note.getCreate_date()) %></td>
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
            </tr>
        </table>




<div id="dragifm" style="top:0px;left:0px;"></div>
    <div id="discontinueUI" style="position: absolute;display:none;width:500px;height:200px;background-color:white;padding:20px;border:1px solid grey">
        <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.discontinue.heading"/><span id="disDrug"></span></h3>
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
        <input type="button" onclick="document.getElementById('discontinueUI').style.display='none';" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.discontinue.cancel"/>"/>
        <input type="button" onclick="Discontinue2(document.getElementById('disDrugId').value,document.getElementById('disReason').value,document.getElementById('disComment').value,document.getElementById('disDrug').innerHTML);" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.discontinue.action"/>"/>

    </div>

<%
                        if (pharmacyList != null) {
%>

<div id="Layer1"><!--  This should be changed to automagically fill if this changes often -->

    <table class="hiddenLayer">
        <tr>
            <td>&nbsp;</td>
            <td align="right"><a href="javascript: function myFunction() {return false; }" onclick="hidepic('Layer1');" style="text-decoration: none;"><img src='<c:out value="${ctx}/images/close.png"/>' border="0"></a></td>
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
            <td class="wcblayerContent"  id="pharmacyPhone1"></td>
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
            <td class="wcblayerContent"  id="pharmacyEmail"></td>
        </tr>
        <tr>
                <td class="wcblayerTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.pharmacy.msgNotes"/></td>
            <td class="wcblayerContent"  id="pharmacyNotes"></td>
        </tr>
    </table>

</div>

<%
                        }
%>

<%-- Bootstrap 5 Modal (replaces LightWindow) --%>
<div class="modal fade" id="carlosModal" tabindex="-1" aria-labelledby="carlosModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="carlosModalLabel">Prescription</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body" id="carlosModalBody"></div>
            <div class="modal-footer">
                <button type="button" id="carlosModalCloseBtn" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">Close</button>
            </div>
        </div>
    </div>
</div>

<script type="text/javascript">
        function changeLt(element, drugId) {
            if (confirm('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarRx.Prescription.changeDrugLongTermConfirm"/>') === true) {
            const data = "ltDrugId=" + encodeURIComponent(drugId) + "&isLongTerm=" + element.checked + "&rand=" + Math.floor(Math.random() * 10001);
            const url = ctx + "/oscarRx/WriteScript.do?parameterValue=updateLongTermStatus";
            CarlosAjax.request(url, {
                method: 'post',
                parameters: data,
                onSuccess: function (transport) {
                    var json = null;
                    try { json = JSON.parse(transport.responseText); } catch(e) { checkboxRevertStatus(element); return; }
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

    function checkReRxLongTerm(){
        var url=window.location.href;
        var match=url.indexOf('ltm=true');
        if(match>-1){
            RePrescribeLongTerm();
        }
    }
    function changeContainerHeight(ele){
        var ss=document.getElementById('searchString').value;
        ss=ss.trim();
        if(ss.length==0)
            document.getElementById('autocomplete_choices').style.height='0%';
        else
            document.getElementById('autocomplete_choices').style.height='100%';
    }
    function addInstruction(content,randomId){
        document.getElementById('instructions_'+randomId).value=content;
        parseIntr(document.getElementById('instructions_'+randomId));
    }
    function addSpecialInstruction(content,randomId){
                var siEl = document.getElementById('siAutoComplete_'+randomId);
                if(siEl.style.display === 'none' || getComputedStyle(siEl).display === 'none'){
                  siEl.style.display = '';
                  siEl.classList.remove('carlos-collapsed');
                }
                document.getElementById('siInput_'+randomId).value=content;
                document.getElementById('siInput_'+randomId).style.color='black';
   }
   function hideMedHistory(){
       mb.hide();
   }
   var modalBox=function(){
       this.show=function(randomId, displaySRC, H){
           if(!document.getElementById("xmaskframe")){
               var divFram=document.createElement('iframe');
               divFram.setAttribute("id","xmaskframe");
               divFram.setAttribute("name","xmaskframe");
               //divFram.setAttribute("src","displayMedHistory.jsp?randomId="+randomId);
               divFram.setAttribute("allowtransparency","false");
               document.body.appendChild(divFram);
               var divSty=document.getElementById("xmaskframe").style;
               divSty.position="fixed";
               divSty.top="0px";
               divSty.right="0px";
               divSty.width="390px"
               //divSty.border="solid";
               divSty.backgroundColor="#F5F5F5";
               divSty.zIndex="45";
               //divSty.cursor="move";
           }
           this.waitifrm=document.getElementById("xmaskframe");

           this.waitifrm.setAttribute("src",displaySRC+".jsp?randomId="+randomId);
           this.waitifrm.style.display="block";
           this.waitifrm.style.height=H;

           document.getElementById("dragifm").appendChild(this.waitifrm);
           document.getElementById('xmaskframe').style.display = '';
           document.getElementById('xmaskframe').classList.remove('carlos-fade-out');
       };
        this.hide=function()
            {
                var frame = document.getElementById('xmaskframe');
                if (frame) { frame.classList.add('carlos-fade-out'); setTimeout(function(){ frame.style.display = 'none'; }, 300); }

            };
    }
    var mb=new modalBox();
    function displayMedHistory(randomId){
           var data="randomId="+randomId;
           CarlosAjax.request(ctx + "/oscarRx/WriteScript.do?parameterValue=listPreviousInstructions",
           {method: 'post',parameters:data,synchronous:true,onSuccess:function(transport){
                 mb.show(randomId, ctx + '/oscarRx/displayMedHistory', '200px');
                }});
    }

    function displayInstructions(randomId){
    	var data="randomId="+randomId;
            mb.show(randomId, '<%= request.getContextPath() %>/oscarRx/displayInstructions', '600px');

	}

    function updateProperty(elementId){
         var randomId=elementId.split("_")[1];
         if(randomId!=null){
             var url=ctx + "/oscarRx/WriteScript.do?parameterValue=updateProperty";
             var el=document.getElementById(elementId);
             var data="";
             if(elementId.match("prnVal_")!=null)
                 data="elementId="+elementId+"&propertyValue="+encodeURIComponent(el.value);
             else if(elementId.match("repeats_")!=null)
                 data="elementId="+elementId+"&propertyValue="+encodeURIComponent(el.value);
             else
                 data="elementId="+elementId+"&propertyValue="+encodeURIComponent(el.innerHTML);
             data = data + "&rand="+Math.floor(Math.random()*10001);
             CarlosAjax.request(url, {method: 'post',parameters:data});
         }
    }
    function lookNonEdittable(elementId){
        document.getElementById(elementId).className='';
    }
    function lookEdittable(elementId){
        document.getElementById(elementId).className='highlight';
    }
    function setPrn(randomId){
        var prnEl=document.getElementById('prn_'+randomId);
        var prnStr=prnEl.innerHTML.trim();
        var prnStyle=prnEl.style.textDecoration || getComputedStyle(prnEl).textDecoration;
        if(prnStr=='prn' || prnStr=='PRN'|| prnStr=='Prn'){
            if(prnStyle.match("line-through")!=null){
                prnEl.style.textDecoration='none';
                document.getElementById('prnVal_'+randomId).value=true;
            }else{
                prnEl.style.textDecoration='line-through';
                document.getElementById('prnVal_'+randomId).value=false;
            }
        }
    }
     function focusTo(elementId){
         var el = document.getElementById(elementId);
         el.contentEditable='true';
         el.focus();
         if (el.onfocus) el.onfocus();

     }

     function updateSpecialInstruction(elementId){
         var randomId=elementId.split("_")[1];
         var url=ctx+ "/oscarRx/WriteScript.do?parameterValue=updateSpecialInstruction";
         var data="randomId="+randomId+"&specialInstruction="+encodeURIComponent(document.getElementById(elementId).value);
         data = data + "&rand="+Math.floor(Math.random()*10001);
         CarlosAjax.request(url, {method: 'post',parameters:data});
     }

    function changeText(elementId){
        var el=document.getElementById(elementId);
        if(el.value=='Enter Special Instruction'){
            el.value="";
            el.style.color='black';
        }else if (el.value==''){
            el.value='Enter Special Instruction';
            el.style.color='gray';
        }

    }
    function updateMoreLess(elementId){
        var el=document.getElementById(elementId);
        if(el.textContent==='more')
            el.textContent='less';
        else
            el.textContent='more';
    }

    function changeDrugName(randomId,origDrugName){
            if (confirm(jsMsg.confirmChangeDrugName)) {

            //call another function to bring up prescribe.jsp
            var url=ctx+ "/oscarRx/WriteScript.do";
            var customDrugName=document.getElementById("drugName_"+randomId).value;
            var data="parameterValue=normalDrugSetCustom&randomId="+randomId+"&customDrugName="+encodeURIComponent(customDrugName);
            CarlosAjax.updater('rxText',url,{method:'post',parameters:data,insertion: 'bottom',onSuccess:function(transport){
                    var setEl=document.getElementById('set_'+randomId);
                    if(setEl) setEl.remove();
		            renderRxStage();
						}
					});
        }else{
            document.getElementById("drugName_"+randomId).value=origDrugName;
        }
    }
    function resetStash(){
               var url=ctx + "/oscarRx/deleteRx.do?parameterValue=clearStash";
               var data = "rand=" + Math.floor(Math.random()*10001);
               CarlosAjax.request(url, {method: 'post',parameters:data,onSuccess:function(transport){
                            // updateCurrentInteractions();
            }});
               document.getElementById('rxText').innerHTML="";//make pending prescriptions disappear.
	            renderRxStage();
               document.getElementById("searchString").focus();
    }

			/*
			 * Re-iterates the medication stack on postback and load through a session rxSessionBean
			 * and action class. A portion of this code also persists parts of the medication in a local stack.
			 */
    function iterateStash(){
        var url=ctx + "/oscarRx/WriteScript.do";
        var data="parameterValue=iterateStash&rand="+ Math.floor(Math.random()*10001);
        CarlosAjax.updater('rxText',url, {method:'POST',parameters:data,
          requestHeaders: { 'Accept': 'application/json' },
          evalScripts:true,
					insertion: 'bottom', onSuccess: function (data) {
                // updateCurrentInteractions();

						// detect postback or page load.
						if (data.responseText) {
							renderRxStage();
						}

          }
				});

			}

    function rxPageSizeSelect(){
               var ran_number=Math.round(Math.random()*1000000);
               var url=ctx + "/oscarRx/GetRxPageSizeInfo.do?method=view";
               var params = "demographicNo=<%=demoNo%>&rand="+ran_number;  //hack to get around ie caching the page
               CarlosAjax.request(url, {method: 'post',parameters:params});
    }

    function reprint2(scriptNo){
        var data="scriptNo="+scriptNo + "&rand=" + Math.floor(Math.random()*10001);
        var url= ctx + "/oscarRx/rePrescribe2.do?method=reprint2";
       CarlosAjax.request(url,
        {method: 'post',postBody:data,
            onSuccess:function(transport){
                popForm2(scriptNo);

            }});
        return false;
    }


    function deletePrescribe(randomId){
        var data="randomId="+randomId;
        var url=ctx + "/oscarRx/rxStashDelete.do";
        data += "&parameterValue=deletePrescribe";
        CarlosAjax.request(url, {method: 'post',parameters:data,onSuccess:function(transport){
                // updateCurrentInteractions();
                if(document.getElementById('deleteOnCloseRxBox').value=='true'){
                    deleteRxOnCloseRxBox(randomId);
                }

						jQuery("#set_" + randomId).remove();
						jQuery("#prescriptionMoreLessLink_" + randomId).remove();
						jQuery("#deleteMedicationFromPrescription_" + randomId).remove();
					}
				});
    }

    function deleteRxOnCloseRxBox(randomId){

            var data="randomId="+randomId;
            var url=ctx + "/oscarRx/deleteRx.do";
            data += "&parameterValue=DeleteRxOnCloseRxBox";
            CarlosAjax.request(url, {method: 'post',parameters:data,onSuccess:function(transport){
                     var json = null;
                     try { json = JSON.parse(transport.responseText); } catch(e) { return; }
                     if(json!=null){
                             var id=json.drugId;
                             var rxDate="rxDate_"+ id;
                             var reRx="reRx_"+ id;
                             var del="del_"+ id;
                             var discont="discont_"+ id;
                             var prescrip="prescrip_"+id;
                             document.getElementById(rxDate).style.textDecoration='line-through';
                             document.getElementById(reRx).style.textDecoration='line-through';
                             document.getElementById(del).style.textDecoration='line-through';
                             document.getElementById(discont).style.textDecoration='line-through';
                             document.getElementById(prescrip).style.textDecoration='line-through';
			     // updateCurrentInteractions();
                    }
                }});

    }

    skipParseInstr = false;
    function useFav2(favoriteId){
        var randomId=Math.round(Math.random()*1000000);
        var data="favoriteId="+favoriteId+"&randomId="+randomId;
        var url= ctx + "/oscarRx/useFavorite.do";
        data += "&parameterValue=useFav2";
        CarlosAjax.updater('rxText',url, {method:'post',parameters:data,evalScripts:true,insertion: 'bottom',
            onSuccess: function(transport) {
                skipParseInstr = true;
                renderRxStage();
            }
        });
    }

    function calculateRxData(randomId){
	    if(skipParseInstr){
		    return false;
	    }
        var dummie=parseIntr(document.getElementById('instructions_'+randomId));
        if(dummie)
            updateQty(document.getElementById('quantity_'+randomId));
    }
   function Delete2(element){

				if (confirm(jsMsg.confirmDeletePrescriptions)) {
             var id_str=(element.id).split("_");
             var id=id_str[1];
             //var id=element.id;
             var rxDate="rxDate_"+ id;
             var reRx="reRx_"+ id;
             var del="del_"+ id;
             var discont="discont_"+ id;
             var prescrip="prescrip_"+id;

             var url=ctx + "/oscarRx/deleteRx.do?parameterValue=Delete2"  ;
             var data="deleteRxId="+element.id + "&rand=" +  Math.floor(Math.random()*10001);
            CarlosAjax.request(url,{method: 'post',postBody:data,onSuccess:function(transport){
                  document.getElementById(rxDate).style.textDecoration='line-through';
                  document.getElementById(reRx).style.textDecoration='line-through';
                  document.getElementById(del).style.textDecoration='line-through';
                  document.getElementById(discont).style.textDecoration='line-through';
                  document.getElementById(prescrip).style.textDecoration='line-through';
		  // updateCurrentInteractions();
							location.reload();

						}
					});
        }
        return false;
    }

   function checkAllergy(id,atcCode){
        const url = ctx + "/oscarRx/showAllergy.do"
        const data="method=allergyData&atcCode="+encodeURIComponent(atcCode)+"&id="+ encodeURIComponent(id) +"&rand="+ Math.floor(Math.random()*10001);
     CarlosAjax.request(url,{method: 'post',postBody:data,
       requestHeaders: { 'Accept': 'application/json' },
       onSuccess:function(transport){
         if (!transport.responseText) return;
         var json = null;
         try { json = JSON.parse(transport.responseText); } catch(e) { return; }
         if (json != null && json.results && json.results.length > 0) {
           // Pick the first allergy warning found
           var allergy = json.results[0];
           var str = "<label style=\"color:red;\"> Allergy:</label> " + allergy.DESCRIPTION + " <label style=\"color:red;\">Reaction:</label> " + allergy.reaction;
           document.getElementById('alleg_' + json.id).innerHTML = str;
           document.getElementById('alleg_tbl_' + json.id).style.display = 'block';
         }
       }
     });
   }
   function checkIfInactive(id,dinNumber){
        var url=ctx + "/oscarRx/searchDrug.do";
         var data="method=inactiveDate&din="+dinNumber+"&id="+id +"&rand=" +  Math.floor(Math.random()*10001);
         CarlosAjax.request(url,{method: 'post',postBody:data,
           onSuccess:function(transport){
                 if (!transport.responseText) return;
                 var json = null;
                 try { json = JSON.parse(transport.responseText); } catch(e) { return; }

                if(json!=null && json.results && json.results.length > 0 && json.results[0].time != null){
                    var str = "Inactive Drug Since: "+new Date(json.results[0].time).toDateString();
                    document.getElementById('inactive_'+id).innerHTML = str;
                } else {
                    document.getElementById('inactive_'+id).innerHTML = '';
                }
            }});
   }


    function Discontinue(event,element){
       var id_str=(element.id).split("_");
       var id=id_str[1];
				var widVal = (document.getElementById('drugProfile').offsetWidth - 400);
       var widStr=widVal+'px';
       var heightDrugProfile=document.getElementById('discontinueUI').offsetHeight;
       var posx=0,posy=0;
       if(event.pageX||event.pageY){
           posx=event.pageX;
           posx=posx-widVal;
           posy=event.pageY-heightDrugProfile/2;
           posx = posx+'px';
           posy = posy+'px';
       }else if(event.clientX||event.clientY){
           posx = event.clientX + document.body.scrollLeft
			+ document.documentElement.scrollLeft;
           posx=posx-widVal;
	   posy = event.clientY + document.body.scrollTop
			+ document.documentElement.scrollTop-heightDrugProfile/2;
           posx = posx+'px';
           posy = posy+'px';
       }else{
           var rect = document.getElementById('drugProfile').getBoundingClientRect(); var xy = [rect.left + window.scrollX, rect.top + window.scrollY];
           posx = (xy[0]+200)+'px';
           if(xy[1]>=0)
               posy = xy[1]+'px';
           else
               posy=0+'px';
       }
       var styleStr= {left: posx, top: posy,width: widStr};

        var drugName = document.getElementById('prescrip_'+id).innerHTML;
       var disUI=document.getElementById('discontinueUI'); disUI.style.left=styleStr.left; disUI.style.top=styleStr.top; disUI.style.width=styleStr.width;
       document.getElementById('disDrug').innerHTML = drugName;
       document.getElementById('discontinueUI').style.display="";
       document.getElementById('disDrugId').value=id;


    }

    function Discontinue2(id,reason,comment,drugSpecial){
        var url=ctx + "/oscarRx/deleteRx.do?parameterValue=Discontinue"  ;
        var demoNo='<%=patient.getDemographicNo()%>';
        var data="drugId="+encodeURIComponent(id)+"&reason="+encodeURIComponent(reason)+"&comment="+encodeURIComponent(comment)+"&demoNo="+demoNo+"&drugSpecial="+encodeURIComponent(drugSpecial)+"&rand="+ Math.floor(Math.random()*10001);
            CarlosAjax.request(url,{method: 'post',postBody:data,onSuccess:function(transport){
                  var json = null;
                  try { json = JSON.parse(transport.responseText); } catch(e) { return; }
                  document.getElementById('discontinueUI').style.display="none";
                  document.getElementById('rxDate_'+json.id).style.textDecoration='line-through';
                  document.getElementById('reRx_'+json.id).style.textDecoration='line-through';
                  document.getElementById('del_'+json.id).style.textDecoration='line-through';
                  document.getElementById('discont_'+json.id).textContent = json.reason;
                  document.getElementById('prescrip_'+json.id).style.textDecoration='line-through';
					}
				});

    }

	/*
			 * @Deprecated avoid future use of prototype.
	 */
    function updateCurrentInteractions(){
        CarlosAjax.request(ctx + "/oscarRx/GetmyDrugrefInfo.do", {method:'post',parameters:"method=findInteractingDrugList&rand="+ Math.floor(Math.random()*10001),onSuccess:function(transport){
                            CarlosAjax.request(ctx + "/oscarRx/UpdateInteractingDrugs.jsp", {method:'post',parameters:"rand="+ Math.floor(Math.random()*10001),onSuccess:function(transport){
                                            var str=transport.responseText;
                                            str=str.replace('<script type="text/javascript">','');
                                            str=str.replace(/<\/script>/,'');
                                            eval(str);
<%--                                            <oscar:oscarPropertiesCheck property="MYDRUGREF_DS" value="yes">--%>
<%--                                              callReplacementWebService("GetmyDrugrefInfo.do?method=view&rand="+  Math.floor(Math.random()*10001),'interactionsRxMyD');--%>
<%--                                             </oscar:oscarPropertiesCheck>--%>
							}
						});
    }
				});
			}

//represcribe long term meds
    function RePrescribeLongTerm(){
       var demoNo='<%=patient.getDemographicNo()%>';
        var data="demoNo="+demoNo+"&showall=<%=showall%>&rand=" +  Math.floor(Math.random()*10001);
        var url= ctx + "/oscarRx/rePrescribe2.do";
        data += "&method=repcbAllLongTerm";
        CarlosAjax.updater('rxText',url, {method:'post',parameters:data,insertion: 'bottom',onSuccess:function(transport){
		        renderRxStage();
					}
				});
        return false;
    }

function customNoteWarning(){
    if (confirm(jsMsg.confirmCustomNote)) {
        var randomId=Math.round(Math.random()*1000000);
        var url=ctx+ "/oscarRx/WriteScript.do";
        var data="parameterValue=newCustomNote&randomId="+randomId;
					CarlosAjax.updater('rxText', url, {
						method: 'post',
						parameters: data,
						evalScripts: true,
						insertion: 'bottom',
						onSuccess: function() {
							renderRxStage();
						}
					});
    }
}

function customWarning2(){
    if (confirm(jsMsg.confirmCustomDrug)) {
	//call another function to bring up prescribe.jsp
        var randomId=Math.round(Math.random()*1000000);
		var searchString = document.getElementById("searchString").value;
        var url=ctx+ "/oscarRx/WriteScript.do";
        var data="parameterValue=newCustomDrug&name=" + encodeURIComponent(searchString) + "&randomId="+randomId;
        CarlosAjax.updater('rxText',url,{method:'post',parameters:data,evalScripts:true,
            insertion: 'bottom', onComplete:function(transport){
                updateQty(document.getElementById('quantity_'+randomId));
		            renderRxStage();
						}
					});

    }

}
function saveCustomName(element){
    var elemId=element.id;
    var ar=elemId.split("_");
    var rand=ar[1];
    var url=ctx+"/oscarRx/WriteScript.do";
    var data="parameterValue=saveCustomName&customName="+encodeURIComponent(element.value)+"&randomId="+rand;
    var instruction="instructions_"+rand;
    var quantity="quantity_"+rand;
    var repeat="repeats_"+rand;
    CarlosAjax.request(url, {method: 'post',parameters:data, onSuccess:function(transport){

            }});
}
function updateDeleteOnCloseRxBox(){
    document.getElementById('deleteOnCloseRxBox').value='true';
}
function clearPending(actionValue) {
    var form = document.forms["RxClearPendingForm"];
    if (form && form.elements["action"]) {
        form.elements["action"].value = actionValue;
        form.submit();
    } else {
        console.warn("RxClearPendingForm not found, skipping clearPending()");
    }
}
function popForm2(scriptId){
        try{
            var url = ctx + "/oscarRx/ViewScript2.jsp?scriptId="+scriptId;
            var calcs = jQuery("#Calcs").val();
            if( calcs != null && calcs != "" ) {
                try {
                    var pharmacy = JSON.parse(calcs);
                    if( pharmacy != null && pharmacy.id != null ) {
                        url= ctx + "/oscarRx/ViewScript2.jsp?scriptId="+scriptId+"&pharmacyId="+encodeURIComponent(pharmacy.id);
                    }
                } catch (e) {
                    oscarLog(e);
                }
            }
            var modalBody = document.getElementById('carlosModalBody');
            modalBody.innerHTML = '';
            var iframe = document.createElement('iframe');
            iframe.style.cssText = 'width:100%;height:890px;border:none;display:block;';
            iframe.src = url;
            modalBody.appendChild(iframe);
            var modalDialog = document.querySelector('#carlosModal .modal-dialog');
            modalDialog.style.maxWidth = '980px';
            var editRxMsg = '${e:forJavaScript(msg_editRx)}';
            var closeBtn = document.getElementById('carlosModalCloseBtn');
            closeBtn.textContent = editRxMsg;
            closeBtn.onclick = updateDeleteOnCloseRxBox;
            var modalEl = document.getElementById('carlosModal');
            var existingModal = bootstrap.Modal.getInstance(modalEl);
            if (existingModal) existingModal.dispose();
            new bootstrap.Modal(modalEl).show();
        }
        catch(er){
            oscarLog(er);
        }
    }

     function callTreatments(textId,id){
         var ele = document.getElementById(textId);
         var url = ctx + "/oscarRx/TreatmentMyD.jsp"
         var ran_number=Math.round(Math.random()*1000000);
         var params = "demographicNo=<%=demoNo%>&cond="+encodeURIComponent(ele.value)+"&rand="+ran_number;  //hack to get around ie caching the page
         CarlosAjax.updater(id,url, {method:'get',parameters:params});
         var tmEl=document.getElementById('treatmentsMyD'); tmEl.style.display=(tmEl.style.display==='none')?'':'none';
     }

     function callAdditionWebService(url,id){
         var contextPath = '<c:out value="${ctx}"/>';
         if (url.indexOf(contextPath) !== 0) {
             url = contextPath + "/oscarRx/" + url;
         }
         var ran_number=Math.round(Math.random()*1000000);
         var params = "demographicNo=<%=demoNo%>&rand="+ran_number;  //hack to get around ie caching the page
         var updater=CarlosAjax.updater(id,url, {method:'get',parameters:params,insertion: 'bottom',evalScripts:true});
     }

			function callReplacementWebService(url, id) {
            var contextPath = '<c:out value="${ctx}"/>';
            if (url.indexOf(contextPath) !== 0) {
                url = contextPath + "/oscarRx/" + url;
            }
				var ran_number = Math.round(Math.random() * 1000000);
				// alert(url + "  " + id + "  " + ran_number);
				var params = "demographicNo=<%=demoNo%>&rand=" + ran_number;  //hack to get around ie caching the page
            var updater = CarlosAjax.updater(id, url, {method: 'get', parameters: params, evalScripts: true});
			}

			//callReplacementWebService("InteractionDisplay.jsp",'interactionsRx');
			callReplacementWebService("ListDrugs.jsp", 'drugProfile');


			function searchResultsHandler(type, args) {

				var url = ctx + "/oscarRx/WriteScript.do";
				var ran_number = Math.round(Math.random() * 1000000);
				var params = "parameterValue=createNewRx"
					+ "&demographicNo="
					+ "${ demographicNo }"
					+ "&drugId="
					+ encodeURIComponent(args.id)
					+ "&text="
					+ encodeURIComponent(args.label)
					+ "&randomId="
					+ ran_number;

				CarlosAjax.updater('rxText', url, {
					method: 'POST', parameters: params, evalScripts: true,
          requestHeaders: { 'Accept': 'application/json' },
					insertion: 'top', onSuccess: function (transport) {
						renderRxStage();
					}
				});

			}

			function replaceAll(str, keyword) {
				var matcher;
				var lastkeyword;
				if (keyword !== lastkeyword) {
					matcher = new RegExp("(" + keyword + ")", "ig");
					lastkeyword = keyword;
				}
				return str.replace(matcher, "<span class='drugKeyword' >$1</span>");
			}

			jQuery(document).ready(function () {

				// block the enter key
				jQuery("#searchString").keypress(function(e) {
					let code = (e.keyCode ? e.keyCode : e.which);
					if(code === 13) {
						return false;
					}
				});

				var cache = {};
				jQuery("#searchString").autocomplete({
					source: function (request, response) {

						var term = request.term.toUpperCase(),
							element = this.element,
							cache = this.element.data('autocompleteCache') || {},
							foundInCache = false;

						jQuery.each(cache, function (key, data) {
							if (term.indexOf(key) === 0 && data.length > 0) {
								response(jQuery.map(cache.results, function (item) {
									return {
										label: item.name,
										value: item.description,
										id: item.id,
										active: item.isInactive,
										keyword: request.term
									};
								}))

								foundInCache = true;
							}
						});

						if (foundInCache) {
							return;
						}

						let param = jQuery('#drugCategorySet').serialize()
							+ "&"
							+ jQuery('#searchParamSet').serialize()
							+ "&query="
							+ request.term.toUpperCase();
						jQuery.ajax({
							url: "${ctx}/oscarRx/searchDrug.do",
							type: 'POST',
							data: param,
							dataType: "json",
							success: function (data) {
								cache[term] = data;
								element.data('autocompleteCache', cache);

								response(jQuery.map(data.results, function (item) {
									return {
										label: item.name,
										value: item.description,
										id: item.id,
										active: item.isInactive,
										keyword: request.term
									};
								}))
							}
						})
					},
					delay: 400,
					minLength: 3,
					search: function (event, ui) {
						jQuery(ui).empty();
					},
					focus: function (event, ui) {
						event.preventDefault();
					},
					select: function (event, ui) {
						event.preventDefault();
						searchResultsHandler(null, ui.item);
						jQuery('#searchString').val("");
					},
					open: function () {
						jQuery(this).removeClass("ui-corner-all").addClass("ui-corner-top");
						jQuery(this).autocomplete("widget").css({"width": (jQuery(this).width() + "px")});
						jQuery(".ui-autocomplete").css("z-index", 1000);
					},
					close: function () {
						jQuery(this).removeClass("ui-corner-top").addClass("ui-corner-all");
					}

				}).data("ui-autocomplete")._renderItem = function (ul, item) {
					var inactivedrug = item.active ? " inactiveDrug" : "";
					return jQuery("<li></li>")
						.data("item.autocomplete", item)
						.append("<a href='#' id='" + item.id + "'"
							+ " class='drugitem"
							+ inactivedrug
							+ "' >"
							+ replaceAll(item.label, jQuery.ui.autocomplete.escapeRegex(item.keyword))
							+ "</a>")
						.appendTo(ul);
				};

			})

function addFav(randomId,brandName){
    var favoriteName = window.prompt('Please enter a name for the Favorite:',  brandName);
    if(favoriteName == null) {
    	return;
    }
    favoriteName=encodeURIComponent(favoriteName);
   if (favoriteName.length > 0){
        var url= ctx + "/oscarRx/addFavorite2.do";
        var data="parameterValue=addFav2&randomId="+randomId+"&favoriteName="+favoriteName;
        CarlosAjax.request(url, {method: 'post',parameters:data, onSuccess:function(transport){
              window.location.href = ctx + "/oscarRx/SearchDrug3.jsp";
   }
					})
}
			}

    var resHidden2 = 0;
    function showHiddenRes(){
        var list = document.querySelectorAll('div.hiddenResource');
        if(resHidden2 == 0){
          list.forEach(function(el) { el.style.display = ''; });
          resHidden2 = 1;
          document.getElementById('showHiddenResWord').textContent='hide';
          var url = ctx + "/oscarRx/updateHiddenResources.jsp";
          var params="hiddenResources=&rand="+ Math.floor(Math.random()*10001);
          CarlosAjax.request(url, {method: 'post',parameters:params});
        }else{
            document.getElementById('showHiddenResWord').textContent='show';
            list.forEach(function(el) { el.style.display = 'none'; });
            resHidden2 = 0;
        }
    }
    var showOrHide=0;
    function showOrHideRes(hiddenRes){
        hiddenRes=hiddenRes.replace(/\{/g,"");
        hiddenRes=hiddenRes.replace(/\}/g,"");
        hiddenRes=hiddenRes.replace(/\s/g,"");
        var arr=hiddenRes.split(",");
        var numberOfHiddenResources=0;
        if(showOrHide==0){
            numberOfHiddenResources=0;
            for(var i=0;i<arr.length;i++){
                var element=arr[i];
                element=element.replace("mydrugref","");
                var elementArr=element.split("=");
                var resId=elementArr[0];
                var resUpdated=elementArr[1];
                var id=resId+"."+resUpdated;
                document.getElementById(id).style.display="";
                document.getElementById('show_'+id).style.display="none";
                document.getElementById('showHideWord').textContent='hide';

                showOrHide=1;
                numberOfHiddenResources++;
            }
        }else{
            numberOfHiddenResources=0
            for(var i=0;i<arr.length;i++){
                var element=arr[i];
                element=element.replace("mydrugref","");
                var elementArr=element.split("=");
                var resId=elementArr[0];
                var resUpdated=elementArr[1];
                var id=resId+"."+resUpdated;
                oscarLog("id="+id);
                document.getElementById(id).style.display="none";
                document.getElementById('show_'+id).style.display="";
                document.getElementById('showHideWord').textContent='show';
                showOrHide=0;
                numberOfHiddenResources++;
            }
        }
        document.getElementById('showHideNumber').textContent=numberOfHiddenResources;

    }
   // var totalHiddenResources=0;


    var addTextView=0;
    function showAddText(randId){
        var addTextId="addText_"+randId;
        var addTextWordId="addTextWord_"+randId;
        if(addTextView==0){
            document.getElementById(addTextId).style.display="";
            addTextView=1;
            document.getElementById(addTextWordId).textContent="less"
        }
        else{
            document.getElementById(addTextId).style.display="none";
            addTextView=0;
            document.getElementById(addTextWordId).textContent="more"
        }
    }

    function ShowW(id,resourceId,updated){

				var params = "method=setWarningToShow&resId=" + resourceId + "&updatedat=" + updated;
				var url = ctx + '/oscarRx/GetmyDrugrefInfo.do';
				CarlosAjax.updater('showHideTotal', url, {
					method: 'post',
					parameters: params,
					evalScripts: true,
					onSuccess: function (transport) {

                document.getElementById(id).style.display="";
                document.getElementById('show_'+id).style.display="none";

					}
				});
			}

			function HideW(id, resourceId, updated) {
				var url = ctx + '/oscarRx/GetmyDrugrefInfo.do';
				var ran_number = Math.round(Math.random() * 1000000);
				var params = "method=setWarningToHide&resId=" + resourceId + "&updatedat=" + updated;
				//totalHiddenResources++;
				CarlosAjax.updater('showHideTotal', url, {
					method: 'post',
					parameters: params,
					evalScripts: true,
					onSuccess: function (transport) {

                document.getElementById(id).style.display="none";
                document.getElementById("show_"+id).style.display="";

					}
				});
			}


			function setSearchedDrug(drugId, name) {

				var url = ctx + "/oscarRx/WriteScript.do";
				var ran_number = Math.round(Math.random() * 1000000);
				var params = "parameterValue=createNewRx"
          + "&demographicNo=" + <%=demoNo%>
          + "&drugId=" + encodeURIComponent(drugId)
          + "&text=" + encodeURIComponent(name)
          + "&randomId="
          + ran_number;
				CarlosAjax.updater('rxText', url, {
					method: 'POST',
					parameters: params,
					evalScripts: true,
          requestHeaders: { 'Accept': 'application/json' },
					insertion: 'bottom',
					onSuccess: function (transport) {
						renderRxStage();
					}
				});

				document.getElementById('searchString').value = "";
			}

			var counterRx = 0;

			function updateReRxDrugId(elementId) {
				var ar = elementId.split("_");
				var drugId = ar[1];
				if (drugId != null && document.getElementById(elementId).checked == true) {
					var data = "reRxDrugId=" + encodeURIComponent(drugId) + "&action=addToReRxDrugIdList&parameterValue=updateReRxDrug&rand=" + Math.floor(Math.random() * 10001);
					var url = ctx + "/oscarRx/WriteScript.do";
					CarlosAjax.request(url, {method: 'post', parameters: data});
				} else if (drugId != null) {
					var data = "reRxDrugId=" + encodeURIComponent(drugId) + "&action=removeFromReRxDrugIdList&parameterValue=updateReRxDrug&rand=" + Math.floor(Math.random() * 10001);
					var url = ctx + "/oscarRx/WriteScript.do";
					CarlosAjax.request(url, {method: 'post', parameters: data});
				}
			}


function removeReRxDrugId(drugId) {
    if (drugId != null) {
        const data = "reRxDrugId=" + encodeURIComponent(drugId) + "&action=removeFromReRxDrugIdList&parameterValue=updateReRxDrug&rand=" + Math.floor(Math.random() * 10001);
        const url = ctx + "/oscarRx/WriteScript.do";
        CarlosAjax.request(url, {method: 'post', parameters: data});
    }
}

//represcribe a drug
function represcribe(element, toArchive){

    skipParseInstr=true;
    var elemId=element.id;
    var ar=elemId.split("_");
    var drugId=ar[1];
    if(drugId!=null && document.getElementById("reRxCheckBox_"+drugId).checked === true){

        var url= ctx + "/oscarRx/rePrescribe2.do";
        var data = "method=represcribeMultiple&rand="+Math.floor(Math.random()*10001);
        CarlosAjax.updater('rxText',url, {method:'post',parameters:data,synchronous:true,evalScripts:true,
            insertion: 'bottom',onSuccess:function(transport){
		        renderRxStage();
            }
        });
    } else if(drugId!=null) {
        var dataUpdateId="reRxDrugId="+encodeURIComponent(toArchive)+"&action=addToReRxDrugIdList&parameterValue=updateReRxDrug&rand="+Math.floor(Math.random()*10001);
        var urlUpdateId= ctx + "/oscarRx/WriteScript.do";
        CarlosAjax.request(urlUpdateId, {method: 'post',parameters:dataUpdateId});

        var data="drugId="+encodeURIComponent(drugId);
        var url= ctx + "/oscarRx/rePrescribe2.do";
        data += "&method=represcribe2&rand="+Math.floor(Math.random()*10001);
        CarlosAjax.updater('rxText',url, {method:'post',parameters:data,evalScripts:true,
            insertion: 'bottom',onSuccess:function(transport){
                // updateCurrentInteractions();
            }});

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
        addDrugToReRxList(uiRefId, drugId);
        selectedReRxIDs.push(drugId);
    } else {
        removeDrugFromReRxList(uiRefId, drugId);
        selectedReRxIDs = selectedReRxIDs.filter(id => id !== drugId);
    }
    updateReRxStageConfirmBoxVisibility();
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
        updateReRxStageConfirmBoxVisibility();
    }

    function stageSelectedReRxMedications() {
        rePrescribeMulti(selectedReRxIDs.slice());
        selectedReRxIDs = [];
        updateReRxStageConfirmBoxVisibility();
}

/**
 * Sets off instruction parsing and adds a drug to the re-prescribe list in the UI and session.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to add.
 */
function addDrugToReRxList(uiRefId, drugId) {
    skipParseInstr = true;

    addDrugToReRxListInSession(uiRefId, drugId);
}

/**
 * Add ReRx drug to UI by making an AJAX request to update the 'rxText' element.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to re-prescribe.
 */
function rePrescribe2(uiRefId, drugId) {
    const data = "drugId=" + encodeURIComponent(drugId) + "&method=represcribe2&rand=" + uiRefId;
    const url = ctx + "/oscarRx/rePrescribe2.do";
        CarlosAjax.updater('rxText', url, {
            method: 'post', parameters: data, evalScripts: true,
            insertion: 'bottom', onSuccess: function (transport) {
		        renderRxStage();
            }
        });
    }

    function rePrescribeMulti(drugIds) {
        const url = ctx + "/oscarRx/rePrescribe2.do";
        let rePrescribeMultiData = "method=represcribeMultiple&rand=" + Math.floor(Math.random() * 10001);
        // Pass drug IDs directly to avoid race condition with async session update
        if (drugIds && drugIds.length > 0) {
            rePrescribeMultiData += "&drugIds=" + encodeURIComponent(drugIds.join(','));
        }
        CarlosAjax.updater('rxText', url, {
            method: 'post', parameters: rePrescribeMultiData, synchronous: true, evalScripts: true,
            insertion: 'bottom', onSuccess: function (transport) {
		        renderRxStage();
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
    const dataUpdateId = "reRxDrugId=" + encodeURIComponent(drugId) + "&action=addToReRxDrugIdList&parameterValue=updateReRxDrug&rand=" + uiRefId;
    const urlUpdateId = ctx + "/oscarRx/WriteScript.do";
    CarlosAjax.request(urlUpdateId, {method: 'post', parameters: dataUpdateId});
}

/**
 * Removes a drug from the re-prescribe list and updates the UI.
 *
 * @param uiRefId The unique ID used in the UI to reference this drug.
 * @param drugId The ID of the drug to remove.
 */
function removeDrugFromReRxList(uiRefId, drugId) {
    removeElementFromUI(getPrescribingDrugCardByUiRefId(uiRefId));
    removeReRxDrugId(drugId);
}

/**
 * Removes a prescribing drug entry from both the UI and the backend.
 * @param cardId The id of the card from which to delete
 * @param drugId The id of the drug to remove
 */
function removePrescribingDrug(cardId, drugId) {
    const uiRefId = cardId.id.split('_')[1];
    deletePrescribingDrugFromUI(uiRefId, drugId);
    uncheckReRxForExistingPrescribedDrug(drugId)
}

/**
 * Deletes a prescribing drug from UI and calls deletePrescribe.
 * @param uiRefId The unique id for referencing the UI element.
 * @param drugId The id of the drug to delete.
 */
function deletePrescribingDrugFromUI(uiRefId, drugId) {
    removeElementFromUI(getPrescribingDrugCardByUiRefId(uiRefId));
    deletePrescribe(drugId);
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
    const checkbox = getReRxCheckboxByUiRefId(drugId);
    if (checkbox)
        checkbox.checked = false;
    removeReRxDrugId(drugId);
}

/**
 * Gets the prescribing/staged drug container element by its UI reference ID.
 * @param uiRefId The UI reference ID.
 * @returns {HTMLElement|null} The drug container element, or null if not found.
 */
function getPrescribingDrugCardByUiRefId(uiRefId) {
    return document.getElementById('set_' + uiRefId);
}

/**
 * Gets the re-prescribe checkbox element by its UI reference ID.
 * @param uiRefId The UI reference ID.
 * @returns {HTMLElement|null} The checkbox element, or null if not found.
 */
function getReRxCheckboxByUiRefId(uiRefId) {
    return document.getElementById('reRxCheckBox_' + uiRefId);
}

function updateQty(element){
        var elemId=element.id;
        var ar=elemId.split("_");
        var rand=ar[1];
        var data="parameterValue=updateDrug&randomId="+rand+"&action=updateQty&quantity="+encodeURIComponent(element.value);
        var url= ctx + "/oscarRx/WriteScript.do";

        var rxMethod="rxMethod_"+rand;
        var rxRoute="rxRoute_"+rand;
        var rxFreq="rxFreq_"+rand;
        var rxDrugForm="rxDrugForm_"+rand;
        var rxDuration="rxDuration_"+rand;
        var rxDurationUnit="rxDurationUnit_"+rand;
        var rxAmt="rxAmount_"+rand;
        var str;
       // var rxString="rxString_"+rand;
       var methodStr="method_"+rand;
       var routeStr="route_"+rand;
       var frequencyStr="frequency_"+rand;
       var minimumStr="minimum_"+rand;
       var maximumStr="maximum_"+rand;
       var durationStr="duration_"+rand;
       var durationUnitStr="durationUnit_"+rand;
       var quantityStr="quantityStr_"+rand;
       var unitNameStr="unitName_"+rand;
       var prnStr="prn_"+rand;
       var prnVal="prnVal_"+rand;
        CarlosAjax.request(url, {method: 'POST',parameters:data,
          requestHeaders: { 'Accept': 'application/json' },
          onSuccess:function(transport){
                var json = null;
                try { json = JSON.parse(transport.responseText); } catch(e) { return; }
                document.getElementById(methodStr).textContent=json.method;
                document.getElementById(routeStr).textContent=json.route;
                document.getElementById(frequencyStr).textContent=json.frequency;
                document.getElementById(minimumStr).textContent=json.takeMin;
                document.getElementById(maximumStr).textContent=json.takeMax;
                if(json.duration==null || json.duration=="null"){
                    document.getElementById(durationStr).innerHTML='';
                }else{
                    document.getElementById(durationStr).textContent=json.duration;
                }
                document.getElementById(durationUnitStr).textContent=json.durationUnit;
                document.getElementById(quantityStr).textContent=json.calQuantity;
                if(json.unitName!=null && json.unitName!="null" && json.unitName!="NULL" && json.unitName!="Null"){
                    document.getElementById(unitNameStr).textContent=json.unitName;
                }else{
                    document.getElementById(unitNameStr).innerHTML='';
                }
                if(json.prn){
                    document.getElementById(prnStr).innerHTML="prn";
                    document.getElementById(prnVal).value=true;
                } else{
                    document.getElementById(prnStr).innerHTML="";document.getElementById(prnVal).value=false;
                }

            }});
        return true;
}
    function parseIntr(element){
        var elemId=element.id;
        var ar=elemId.split("_");
        var rand=ar[1];
        var instruction="parameterValue=updateDrug&instruction="+encodeURIComponent(element.value)+"&action=parseInstructions&randomId="+rand;
        var url= ctx + "/oscarRx/UpdateScript.do";
        var quantity="quantity_"+rand;
        var str;
       var methodStr="method_"+rand;
       var routeStr="route_"+rand;
       var frequencyStr="frequency_"+rand;
       var minimumStr="minimum_"+rand;
       var maximumStr="maximum_"+rand;
       var durationStr="duration_"+rand;
       var durationUnitStr="durationUnit_"+rand;
       var quantityStr="quantityStr_"+rand;
       var unitNameStr="unitName_"+rand;
       var prnStr="prn_"+rand;
       var prnVal="prnVal_"+rand;
        CarlosAjax.request(url, {method: 'POST',parameters:instruction,synchronous:true,
          requestHeaders: { 'Accept': 'application/json' },
          onSuccess:function(transport){
                var json = null;
                try { json = JSON.parse(transport.responseText); } catch(e) { return; }
                if(json.policyViolations != null && json.policyViolations.length>0) {
                       for(var x=0;x<json.policyViolations.length;x++) {
                               alert(json.policyViolations[x]);
                       }
                       document.getElementById("saveButton").disabled=true;
                       document.getElementById("saveOnlyButton").disabled=true;
                } else {
                       document.getElementById("saveButton").disabled=false;
                       document.getElementById("saveOnlyButton").disabled=false;
                }

                document.getElementById(methodStr).textContent=json.method;
                document.getElementById(routeStr).textContent=json.route;
                document.getElementById(frequencyStr).textContent=json.frequency;
                document.getElementById(minimumStr).textContent=json.takeMin;
                document.getElementById(maximumStr).textContent=json.takeMax;
                if(json.duration==null || json.duration=="null"){
                    document.getElementById(durationStr).innerHTML='';
                }else{
                    document.getElementById(durationStr).textContent=json.duration;
                }
                document.getElementById(durationUnitStr).textContent=json.durationUnit;
                if(json.unitName!=null && json.unitName!="null" && json.unitName!="NULL" && json.unitName!="Null"){
                    document.getElementById(unitNameStr).textContent=json.unitName;
                }else{
                    document.getElementById(unitNameStr).innerHTML='';
                }
                if (json.calQuantity != 0) {
                    //this is oftentimes zero when re-prescribing a drug where the unitName != null.  
                    //Until a more reliable calculated quantity is being returned, don't update if the calculated quantity is 0
                    //silently changing to 0 can be problematic in situations where the quantity has already been set
                    //to an appropriate value.                 
                    document.getElementById(quantityStr).textContent=json.calQuantity; 
                    if(document.getElementById(unitNameStr).textContent!='')
                        document.getElementById(quantity).value=document.getElementById(quantityStr).textContent+" "+document.getElementById(unitNameStr).textContent;
                    else
                        document.getElementById(quantity).value=document.getElementById(quantityStr).textContent;
                    
                }                
                if(json.prn){
                    document.getElementById(prnStr).innerHTML="prn";document.getElementById(prnVal).value=true;
                } else{
                    document.getElementById(prnStr).innerHTML="";document.getElementById(prnVal).value=false;
                }
            }});
        return true;
    }

    function addLuCode(eleId,luCode){
        document.getElementById(eleId).value = document.getElementById(eleId).value +" LU Code: "+luCode;
    }

         function getRenalDosingInformation(divId,atcCode){
               var url = "<%= request.getContextPath() %>/oscarRx/RenalDosing.jsp";
               var ran_number=Math.round(Math.random()*1000000);
               var params = "demographicNo=<%=demoNo%>&atcCode="+encodeURIComponent(atcCode)+"&divId="+divId+"&rand="+ran_number;
               CarlosAjax.updater(divId,url, {method:'get',parameters:params,insertion: 'bottom'});
         }
         function getLUC(divId,randomId,din){
             var url = ctx + "/oscarRx/LimitedUseCode.jsp";
             var params="randomId="+randomId+"&din="+encodeURIComponent(din);
             CarlosAjax.updater(divId,url,{method:'get',parameters:params,insertion: 'bottom'});
         }

         function getCost(divId, randomId, din, qty) {
            var url = ctx + "/oscarRx/DrugPrice.jsp";
            var params = "randomId=" + randomId + "&din=" +encodeURIComponent(din) + "&qty=" +encodeURIComponent(qty);
            new CarlosAjax.Updater(divId, url, {
                method: 'get',
                parameters: params,
                insertion: Insertion.Bottom,
                asynchronous: true
            });
        }  

         function validateRxDate() {
         	var x = true;
             jQuery('input[name^="rxDate__"]').each(function(){
                 var str1  = jQuery(this).val();

                 var dt = str1.split("-");
                 if (dt.length>3) {
                 	jQuery(this).focus();
                     alert(jsMsg.startDateWrongFormat);
                     x = false;
                     return;
                 }

                 var dt1=1, mon1=0, yr1=parseInt(dt[0],10);
                 if (isNaN(yr1) || yr1<0 || yr1>9999) {
                 	jQuery(this).focus();
                     alert(jsMsg.startDateInvalidYear);
                     x = false;
                     return;
                 }
                 if (dt.length>1) {
                 	mon1 = parseInt(dt[1],10)-1;
                 	if (isNaN(mon1) || mon1<0 || mon1>11) {
                 		jQuery(this).focus();
                 		alert(jsMsg.startDateInvalidMonth);
                         x = false;
                         return;
                 	}
                 }
                 if (dt.length>2) {
                 	dt1 = parseInt(dt[2],10);
                     if (isNaN(dt1) || dt1<1 || dt1>31) {
                     	jQuery(this).focus();
                         alert(jsMsg.startDateInvalidDay);
                         x = false;
                         return;
                     }
                 }
                 var date1 = new Date(yr1, mon1, dt1);
                 var now  = new Date();

                 if(date1 > now) {
                 	jQuery(this).focus();
                     alert(jsMsg.startDateFuture + ' (' + str1 +')');
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
                alert(jsMsg.writtenDateWrongFormat);
                x = false;
                return;
            }

            var dt1=1, mon1=0, yr1=parseInt(dt[0],10);
            if (isNaN(yr1) || yr1<0 || yr1>9999) {
            	jQuery(this).focus();
                alert(jsMsg.writtenDateInvalidYear);
                x = false;
                return;
            }
            if (dt.length>1) {
            	mon1 = parseInt(dt[1],10)-1;
            	if (isNaN(mon1) || mon1<0 || mon1>11) {
            		jQuery(this).focus();
            		alert(jsMsg.writtenDateInvalidMonth);
                    x = false;
                    return;
            	}
            }
            if (dt.length>2) {
            	dt1 = parseInt(dt[2],10);
                if (isNaN(dt1) || dt1<1 || dt1>31) {
                	jQuery(this).focus();
                    alert(jsMsg.writtenDateInvalidDay);
                    x = false;
                    return;
                }
            }
            var date1 = new Date(yr1, mon1, dt1);
            var now  = new Date();

            if(date1 > now) {
            	jQuery(this).focus();
                alert(jsMsg.writtenDateFuture + ' (' + str1 +')');
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
        SINGLE: jsMsg.unstagedReRxSingle,
        MULTIPLE: (count) => jsMsg.unstagedReRxMultiple.replace('{0}', count)
    };

    const SAVE_WARNING = jsMsg.saveWarning;
    const SAVE_PROMPT = jsMsg.savePrompt;

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

<%--	<%--%>
<%--		ArrayList<Object> args = new ArrayList<Object>();--%>
<%--		args.add(String.valueOf(demoNo));--%>
<%--		args.add(providerNo);--%>

<%--		Study myMeds = StudyFactory.getFactoryInstance().makeStudy(Study.MYMEDS, args);--%>
<%--		out.write(myMeds.printInitcode());--%>
<%--	%>--%>


    function updateSaveAllDrugsPrintContinue(){
    	if(!validateWrittenDate()) {
    		return false;
    	}
		if(!validateRxDate()) {
    		return false;
    	}

		<%if (CarlosProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>
		if(!checkMedTerm()){
			return false;
		}
		<%}%>
		setPharmacyId();
        var data=new URLSearchParams(new FormData(document.getElementById('drugForm'))).toString();
        var url= ctx + "/oscarRx/WriteScript.do?parameterValue=updateSaveAllDrugs&rand="+ Math.floor(Math.random()*10001);
        CarlosAjax.request(url,
        {method: 'post',postBody:data,synchronous:true,
          requestHeaders: { 'Accept': 'application/json' },
            onSuccess:function(transport){

                callReplacementWebService("ListDrugs.jsp",'drugProfile');
                const hasDrugs = jQuery("[id^='drugName_']").length > 0;
                if (hasDrugs) {
                    popForm2(null);
                } else {
                    alert(jsMsg.pleaseAddDrugFirst);
                }
                resetReRxDrugList();
            }});
        return false;
    }
    
    function updateSaveAllDrugsContinue(){
    	if(!validateWrittenDate()) {
    		return false;
    	}
		if(!validateRxDate()) {
    		return false;
    	}
		
		<%if (CarlosProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>
		if(!checkMedTerm()){
			return false;
		}
		<%}%>		
		setPharmacyId();
        var data=new URLSearchParams(new FormData(document.getElementById('drugForm'))).toString();
        var url= ctx + "/oscarRx/WriteScript.do?parameterValue=updateSaveAllDrugs&rand="+ Math.floor(Math.random()*10001);
        CarlosAjax.request(url,
        {method: 'post',postBody:data,synchronous:true,
            onSuccess:function(transport){
                callReplacementWebService("ListDrugs.jsp",'drugProfile');
                resetReRxDrugList();
                resetStash();
            }});
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

function checkEnterSendRx(){
        popupRxSearchWindow();
        return false;
}


<%if (CarlosProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>
function checkMedTerm(){
	
	var randId = 0;
	var isAnyTermChecked = false;
	jQuery("fieldset[id^='set_']").each(function() {
	    randId = jQuery( this ).attr("id").replace('set_','');
	    isAnyTermChecked = isMedTermChecked(randId);	
	});
	
	if(!isAnyTermChecked){
		alert(jsMsg.reviewDrugSpecifyTerm);
	}else{
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
 	   
	    <%if (CarlosProperties.getInstance().isPropertyActive("rx_strict_med_term")) {%>   
	    isMedTermChecked(randId);
	    <%}%>
    });
});

function updateShortTerm(rand,val) {
	if(val) {
		jQuery("#shortTerm_" + rand).prop("checked",true);
	} else {
		jQuery("#shortTerm_" + rand).prop("checked",false);
	}
	
}

function updateLongTerm(rand,repeatEl) {
	<% if("true".equals(CarlosProperties.getInstance().getProperty("rx_select_long_term_when_repeat", "true"))) { %>
	let repeats = jQuery('#repeats_' + rand).val().trim();
	if(!isNaN(repeats) && repeats > 0) {
		jQuery("#longTermY_" + rand).prop("checked",true);
	}
	<% } %>
}


</script>
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/css.js"></script>
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/common.js"></script>
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/standardista-table-sorting.js"></script>
</body>
</html>
