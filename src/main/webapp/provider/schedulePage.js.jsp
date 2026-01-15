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
<%@ page contentType="application/javascript; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String newticklerwarningwindow = null;
    String cbiReminderWindow = null;

    if (ca.openosp.openo.commn.IsPropertiesOn.isCaisiEnable() && ca.openosp.openo.commn.IsPropertiesOn.propertiesOn("CBI_REMINDER_WINDOW")) {
        cbiReminderWindow = (String) session.getAttribute("cbiReminderWindow");
    }

%>
function storeApptNo(apptNo) {
var url = "storeApptInSession.jsp?appointment_no="+apptNo;
new Ajax.Request(url, {method:'get'});
}

function getElementsByClass(searchClass,node,tag) {
var classElements = new Array();
if ( node == null )
node = document;
if ( tag == null )
tag = '*';
var els = document.getElementsByTagName(tag);
var elsLen = els.length;
var pattern = new RegExp("(^|\\s)"+searchClass+"(\\s|$)");
for (i = 0, j = 0; i < elsLen; i++) {
if ( pattern.test(els[i].className) ) {
classElements[j] = els[i];
j++;
}
}
return classElements;
}

document.addEventListener('DOMContentLoaded', function() {
    setDefaultReasonView();
});

function setDefaultReasonView() {
    const hideReasonEl = document.getElementById("hideReason");
    const currentDefault = hideReasonEl ? hideReasonEl.value : "false";

    // True to show the reason. Default is to hide.
    // Apply default tooltip state to all providers (before per-provider overrides)
    const showReasonDefault = (currentDefault === "true");
    if (showReasonDefault) {
        // Remove hideReason class from all spans
        document.querySelectorAll("span.hideReason").forEach(function(el) {
            el.classList.remove("hideReason");
        });
    } else {
        // If default is to hide, also hide reason from all tooltips initially
        document.querySelectorAll(".appt-reason-tooltip").forEach(function(el) {
            const titleShort = el.dataset.titleShort;
            if (titleShort) {
                el.setAttribute("title", titleShort);
            }
        });
    }

    // Toggle reason views for each of the provider preferences.
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        const value = localStorage.getItem(key);

        // Keys are stored as "reason_<providerNo>" but used as CSS selectors ".reason_<providerNo>"
        if (key.startsWith("reason_")) {
            const selector = "." + key;
            const providerNo = key.substring(7); // Extract provider number from "reason_<providerNo>"

            // If true show the reason. If false hide the reason
            document.querySelectorAll(selector).forEach(function(el) {
                if (value === "false") {
                    el.classList.add("hideReason");
                }
                if (value === "true") {
                    el.classList.remove("hideReason");
                }
            });

            // Update tooltips based on stored preference
            const showReason = (value === "true");
            updateTooltipsForProvider(providerNo, showReason);
        }
    }

    // Hide all elements with hideReason class
    document.querySelectorAll(".hideReason").forEach(function(el) {
        el.style.display = "none";
    });
}

function toggleReason(event, providerNo) {
    event.preventDefault();
    const selector = ".reason_" + providerNo;
    const elements = document.querySelectorAll(selector);
    let isVisible = false;

    elements.forEach(function(el) {
        // Toggle visibility
        if (el.style.display === "none" || el.classList.contains("hideReason")) {
            el.style.display = "";
            el.classList.remove("hideReason");
            isVisible = true;
        } else {
            el.style.display = "none";
            isVisible = false;
        }
    });

    const storageKey = "reason_" + providerNo;
    localStorage.setItem(storageKey, isVisible);

    // Update tooltips for this provider's appointments to respect privacy toggle
    updateTooltipsForProvider(providerNo, isVisible);
}

/**
 * Updates the tooltip (title attribute) for all appointments of a given provider
 * to show or hide reason/notes based on the visibility flag.
 *
 * @param providerNo the provider number
 * @param showReason true to show full tooltip with reason/notes, false to hide them
 */
function updateTooltipsForProvider(providerNo, showReason) {
    const selector = ".appt-tooltip-provider-" + providerNo;
    document.querySelectorAll(selector).forEach(function(el) {
        const titleAttr = showReason ? el.dataset.titleFull : el.dataset.titleShort;
        if (titleAttr) {
            el.setAttribute("title", titleAttr);
        }
    });
}


function confirmPopupPage(height, width, queryString, doConfirm, allowDay, allowWeek){
if (doConfirm == "Yes") {
if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.confirmBooking"/>")){
popupPage(height, width, queryString);
}
}
else if (doConfirm == "Day"){
if (allowDay == "No") {
alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.sameDay"/>");
}
else {
popupPage(height, width, queryString);
}
}
else if (doConfirm == "Wk"){
if (allowWeek == "No") {
alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.sameWeek"/>");
}
else {
popupPage2(queryString, 'appointment', height, width);
}
}
else if( doConfirm == "Onc" ) {
if( allowDay == "No" ) {
if( confirm("This is an On Call Urgent appointment. Are you sure you want to book?") ) {
popupPage(height, width, queryString);
}
}
else {
popupPage2(queryString, 'appointment', height, width);
}
}
else {
popupPage2(queryString, 'appointment', height, width);
}
}

function setfocus() {
this.focus();
}

function popupPage(vheight,vwidth,varpage) {
var page = "" + varpage;
windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
var popup=window.open(page, "<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>", windowprops);
if (popup != null) {
if (popup.opener == null) {
popup.opener = self;
}
popup.focus();
}
}

function popUpEncounter(vheight,vwidth,varpage) {
var page = "" + varpage;
windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
var popup=window.open(page, "Encounter", windowprops);

if (popup != null) {
if (popup.opener == null) {
popup.opener = self;
}
popup.focus();
}
}

function popupPageOfChangePassword(){
<%
    Integer ed;
    String expired_days = "";
    if (session.getAttribute("expired_days") != null) {
        expired_days = (String) session.getAttribute("expired_days");
    }
    if (!(expired_days.equals(" ") || expired_days.equals("") || expired_days == null)) {
        //javascript
%>

window.open("changePassword.jsp","changePassword","resizable=yes,scrollbars=yes,width=400,height=300");
changePassword.moveTo(0,0);
<%}%>
}
function popupInboxManager(varpage, height = 700, width = 1215) {
var page = "" + varpage;
var windowname="apptProviderInbox";
    windowprops = "height=" + height + ",width=" + width + ",location=no,"
+ "scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=10,left=0";
var popup = window.open(page, windowname, windowprops);
if (popup != null) {
if (popup.opener == null) {
popup.opener = self;
}
popup.focus();
}
}

function popupPage2(varpage) {
popupPage2(varpage, "apptProviderSearch");
}

function popupPage2(varpage, windowname) {
popupPage2(varpage, windowname, 700, 1024);
}

function popupPage2(varpage, windowname, vheight, vwidth) {
// Provide default values for windowname, vheight, and vwidth incase popupPage2
// is called with only 1 or 2 arguments (must always specify varpage)
windowname = typeof(windowname)!= 'undefined' ? windowname : 'apptProviderSearch';
vheight = typeof(vheight) != 'undefined' ? vheight : '700px';
vwidth = typeof(vwidth) != 'undefined' ? vwidth : '1024px';
var page = "" + varpage;
windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
var popup = window.open(page, windowname, windowprops);
if (popup != null) {
if (popup.opener == null) {
popup.opener = self;
}
popup.focus();
}
}

<!--messenger code block-->
function popupOscarRx(vheight,vwidth,varpage) {
var page = varpage;
windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
var popup=window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="global.oscarRx"/>_appt", windowprops);
if (popup != null) {
if (popup.opener == null) {
popup.opener = self;
}
popup.focus();
}
}

function popupWithApptNo(vheight,vwidth,varpage,name,apptNo) {
if (apptNo) storeApptNo(apptNo);
if (name=='master')
popup(vheight,vwidth,varpage,name);
else if (name=='encounter')
popup(vheight, vwidth, varpage, name);
else
popupOscarRx(vheight,vwidth,varpage);
}


function refresh() {
document.location.reload();
}

function refresh1() {
var u = self.location.href;
if(u.lastIndexOf("view=1") > 0) {
self.location.href = u.substring(0,u.lastIndexOf("view=1")) + "view=0" + u.substring(eval(u.lastIndexOf("view=1")+6));
} else {
document.location.reload();
}
}

function onUnbilled(url) {
if(confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.onUnbilled"/>")) {
popupPage(700,720, url);
}
}

function onUpdatebill(url) {
popupPage(700,720, url);
}

//popup a new tickler warning window
function load() {
var cbi = "<%=cbiReminderWindow%>";
if(cbi!="null" && cbi!="") {
alert(cbi);
<%request.getSession().setAttribute("cbiReminderWindow", "null");%>
}

if ("<%=newticklerwarningwindow%>"=="enabled") {
if (IsPopupBlocker()) {
alert("You have a popup blocker, so you can not see the new tickler warning window. Please disable the pop blocker in your google bar, yahoo bar or IE ...");
} else{
var pu=window.open("<%=request.getContextPath()%>/UnreadTickler.do",'viewUnreadTickler',"height=120,width=250,location=no,scrollbars=no,menubars=no,toolbars=no,resizable=yes,top=500,left=700");
if(window.focus)
pu.focus();
}
}

popupPageOfChangePassword();
refreshAllTabAlerts();
}

function IsPopupBlocker() {
var oWin = window.open("","testpopupblocker","width=100,height=50,top=5000,left=5000");
if (oWin==null || typeof(oWin)=="undefined") {
return true;
} else {
oWin.close();
return false;
}
}

<%-- Refresh tab alerts --%>
function refreshAllTabAlerts() {
refreshTabAlerts("oscar_new_lab");
refreshTabAlerts("oscar_new_msg");
refreshTabAlerts("oscar_new_tickler");
refreshTabAlerts("oscar_aged_consults");
refreshTabAlerts("oscar_scratch");
}

function callRefreshTabAlerts(id) {
setTimeout("refreshTabAlerts('"+id+"')", 10);
}

function refreshTabAlerts(id) {
var url = "<%= request.getContextPath() %>/provider/tabAlertsRefresh.jsp";
var pars = "id=" + id;
jQuery.ajax({
url: url,
type: "get",
dataType: "html",
data: pars,
success: function(returnData){
jQuery("#" + id).html(returnData);
},
error: function(e){
console.log(e);
}
});
}

function refreshSameLoc(mypage) {
var X = (window.pageXOffset?window.pageXOffset:window.document.body.scrollLeft);
var Y = (window.pageYOffset?window.pageYOffset:window.document.body.scrollTop);
window.location.href = mypage + "&x=" + X + "&y=" + Y;
}

function scrollOnLoad() {
var X = getParameter("x");
var Y = getParameter("y");
if(X!=null && Y!=null) {
window.scrollTo(parseInt(X),parseInt(Y));
}
}

function getParameter(paramName) {
var searchString = window.location.search.substring(1);
var i,val;
var params = searchString.split("&");

for (i=0;i<params.length;i++) {
val = params[i].split("=");
if (val[0] == paramName) {
return val[1];
}
}
return null;
}
