<%--

    Copyright (c) 2012- Centre de Medecine Integree

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

    This software was written for
    Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
    as part of the OSCAR McMaster EMR System


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="java.util.*" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProp" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<jsp:useBean id="oscarVariables" class="java.util.Properties" scope="page"/>

<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="java.net.URLDecoder, java.net.URLEncoder,java.util.Date, java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.documentManager.EDocUtil,io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@page import="java.util.Hashtable" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlDocClassDao,io.github.carlos_emr.carlos.commn.dao.QueueDao" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_edoc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    String demographicID = request.getParameter("demographicID");
    String categoryKey = request.getParameter("categorykey");
    // errorMessage is populated by DocumentRefile2Action via a redirect query
    // param when a refile throws. Read it here so the alert block below renders.
    String errorMessage = request.getParameter("errorMessage");
    if (errorMessage == null) errorMessage = "";

// NOTE: Historic GET-triggered delete / undelete / refile scriptlets
// were removed in favor of POST-only Struts2 mutation actions:
//   /documentManager/DocumentDelete   (_edoc w)
//   /documentManager/DocumentUndelete (_admin.edocdelete w)
//   /documentManager/DocumentRefile   (_edoc w)
// The submit-helpers below (doDelete, doUndelete, doRefile) now point
// the DisplayDoc form at the appropriate action route before posting.

    QueueDao queueDao = SpringUtils.getBean(QueueDao.class);
    List<Hashtable> queues = queueDao.getQueues();
    int queueId = 1;

    String viewstatus = request.getParameter("viewstatus");
    if (viewstatus == null) {
        viewstatus = "active";
    }
    String view = "all";
    if (request.getParameter("view") != null) {
        view = (String) request.getParameter("view");
    } else if (request.getAttribute("view") != null) {
        view = (String) request.getAttribute("view");
    }
    view = URLDecoder.decode(view, "UTF-8");
    String module = "";

    String moduleid = "";
    if (request.getParameter("function") != null) {
        module = request.getParameter("function");
        moduleid = request.getParameter("functionid");
    } else if (request.getAttribute("function") != null) {
        module = (String) request.getAttribute("function");
        moduleid = (String) request.getAttribute("functionid");
    }
    String winwidth = "";
    String winheight = "";
    if (request.getParameter("winwidth") != null) {
        try { winwidth = String.valueOf(Integer.parseInt(request.getParameter("winwidth"))); } catch (NumberFormatException e) { winwidth = ""; }
    }

    if (request.getParameter("winheight") != null) {
        try { winheight = String.valueOf(Integer.parseInt(request.getParameter("winheight"))); } catch (NumberFormatException e) { winheight = ""; }
    }

    if (!"".equalsIgnoreCase(moduleid) && (demographicID == null || demographicID.equalsIgnoreCase("null"))) {
        demographicID = moduleid;
    }
    ArrayList doctypes = EDocUtil.getDoctypes(module);


    ArrayList<ArrayList<EDoc>> categories = new ArrayList<ArrayList<EDoc>>();
    ArrayList<EDoc> docs = new ArrayList<EDoc>();

    String sortorder = "";
    EDocUtil.EDocSort sort = null;
    if (request.getParameter("sortorder") != null && request.getParameter("sortorder").equals("Observation")) {
        sort = EDocUtil.EDocSort.OBSERVATIONDATE;
        sortorder = "Observation";
    } else if (request.getParameter("sortorder") != null && request.getParameter("sortorder").equals("Update")) {
        sort = EDocUtil.EDocSort.DATE;
        sortorder = "Update";
    } else {
        sort = EDocUtil.EDocSort.CONTENTDATE;
        sortorder = "Content";
    }

    if (categoryKey.indexOf("Private") >= 0) {
        docs = EDocUtil.listDocs(loggedInInfo, module, moduleid, view, EDocUtil.PRIVATE, sort, viewstatus);

    } else if (categoryKey.indexOf("Public") >= 0) {
        docs = EDocUtil.listDocs(loggedInInfo, module, moduleid, view, EDocUtil.PUBLIC, sort, viewstatus);

    } else {%>
Remote documents not supported
<%
    }

%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="dms.documentBrowser.title"/></title>

    <script type="text/javascript">
        window.moveTo(0, 0);

        function popup(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
            var popup = window.open(page, "popup1", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function ReLoadDoc() {
            document.DisplayDoc.viewstatus.value = document.DisplayDoc.selviewstatus.options[document.DisplayDoc.selviewstatus.selectedIndex].value;
            document.DisplayDoc.sortorder.value = document.DisplayDoc.selsortorder.options[document.DisplayDoc.selsortorder.selectedIndex].value;
            document.DisplayDoc.submit();
        }

        function LoadView(viewstr) {
            document.DisplayDoc.winwidth.value = getWidth();
            document.DisplayDoc.winheight.value = getHeight();
            document.DisplayDoc.view.value = viewstr;
            document.DisplayDoc.viewstatus.value = document.DisplayDoc.selviewstatus.options[document.DisplayDoc.selviewstatus.selectedIndex].value;
            document.DisplayDoc.submit();
        }

        function DeleteDoc() {
            document.DisplayDoc.action = '<%= request.getContextPath() %>/documentManager/DocumentDelete';
            document.DisplayDoc.delDocumentNo.value = docid;
            document.DisplayDoc.viewstatus.value = document.DisplayDoc.selviewstatus.options[document.DisplayDoc.selviewstatus.selectedIndex].value;
            document.DisplayDoc.submit();
        }

        function UnDeleteDoc() {
            document.DisplayDoc.action = '<%= request.getContextPath() %>/documentManager/DocumentUndelete';
            document.DisplayDoc.undelDocumentNo.value = docid;
            document.DisplayDoc.viewstatus.value = document.DisplayDoc.selviewstatus.options[document.DisplayDoc.selviewstatus.selectedIndex].value;
            document.DisplayDoc.submit();
        }

        function RefileDoc() {
            document.DisplayDoc.action = '<%= request.getContextPath() %>/documentManager/DocumentRefile';
            document.DisplayDoc.refileDocumentNo.value = docid;
            document.DisplayDoc.viewstatus.value = document.DisplayDoc.selviewstatus.options[document.DisplayDoc.selviewstatus.selectedIndex].value;
            document.DisplayDoc.submit();
        }

        function setQueue() {
            document.DisplayDoc.queueId.value = document.getElementById('queueList').options[document.getElementById('queueList').selectedIndex].value;
        }

        function getWidth() {
            var myWidth = 0;
            if (typeof (window.innerWidth) == 'number') {
                //Non-IE
                myWidth = window.innerWidth;
            } else if (document.documentElement && document.documentElement.clientWidth) {
                //IE 6+ in 'standards compliant mode'
                myWidth = document.documentElement.clientWidth;
            } else if (document.body && document.body.clientHeight) {
                //IE 4 compatible
                myWidth = document.body.clientWidth;
            }
            return myWidth;
        }


        function getHeight() {
            var myHeight = 0;
            if (typeof (window.innerHeight) == 'number') {
                //Non-IE
                myHeight = window.innerHeight;
            } else if (document.documentElement && document.documentElement.clientHeight) {
                //IE 6+ in 'standards compliant mode'
                myHeight = document.documentElement.clientHeight;
            } else if (document.body && (document.body.clientHeight)) {
                //IE 4 compatible
                myHeight = document.body.clientHeight;
            }
            return myHeight;
        }

        showPageImg = function (curdocid) {
            var height = 700;
            if (getHeight() > 750) {
                height = getHeight() - 50;
            }

            var width = 600;
            if (getWidth() > 1250) {
                width = getWidth() - 650;
            }
            if (curdocid != "0") {


                var url2 = '<%=request.getContextPath()%>' + '/documentManager/ManageDocument?method=display&doc_no='
                    + curdocid;
                document.getElementById('docdisp').innerHTML = '<iframe	src="' + url2 + '"  width="' + width + '" height="' + height + '"></iframe>';

                var url4 = '<%=request.getContextPath()%>' + '/documentManager/ManageDocument?method=viewDocumentInfo&doc_no=' + curdocid;
                document.getElementById('docextrainfo').innerHTML = '<object data="' + url4 + '"  height=250px width="100%" type="text/html" ></object>';


            } else {
                document.getElementById('docdisp').innerHTML = '';
                document.getElementById('docextrainfo').innerHTML = '';

            }

        }
        showPageCombineImg = function (doclist) {
            var height = 700;
            if (getHeight() > 750) {
                height = getHeight() - 50;
            }

            var width = 600;
            if (getWidth() > 1250) {
                width = getWidth() - 650;
            }
            var url2 = '<%=request.getContextPath()%>' + '/documentManager/combinePDFs?ContentDisposition=inline' + doclist;
            document.getElementById('docdisp').innerHTML = '<object	data="' + url2 + '" type="application/pdf" width="' + width + '" height="' + height + '"></object>';
            document.getElementById('docextrainfo').innerHTML = '';

        }

        function getSelected(opt) {
            var selected = new Array();
            var index = 0;
            for (var intLoop = 0; intLoop < opt.length; intLoop++) {
                if (opt[intLoop].selected) {
                    index = selected.length;
                    selected[index] = new Object;
                    selected[index].value = opt[intLoop].value;
                    selected[index].title = opt[intLoop].title;
                    selected[index].index = intLoop;
                }
            }
            return selected;
        }

        function getDoc() {
            var th = document.getElementById('doclist');
            var selected = new Array();
            selected = getSelected(th);


            if (selected.length == 0) {

                var div_ref = document.getElementById("docbuttons");
                div_ref.style.visibility = "hidden";
                docid = "0";
                showPageImg(docid);

            }
            if (selected.length >= 2) {
                var div_ref = document.getElementById("docbuttons");
                div_ref.style.visibility = "hidden";

                var docList = '';
                var combinePdf = true;
                for (k = 0; k < selected.length; k++) {
                    var docnoindexend = selected[k].value.indexOf('-');


                    var docno = selected[k].value.substring(0, docnoindexend);
                    var doctype = selected[k].value.substring(docnoindexend + 1, selected[k].value.length);
                    if (doctype == "text/html") combinePdf = false;
                    docList = docList + '&docNo=' + docno;
                }

                if (combinePdf == true) {
                    showPageCombineImg(docList);
                } else {
                    alert("<fmt:message key="dms.documentBrowser.msgOnlyPDFCanBeCombined"/>");
                    setdefaultdoc();
                }

            } else if (selected.length == 1) {
                var docidindexend = selected[0].value.indexOf('-');
                docid = selected[0].value.substring(0, docidindexend);

                showPageImg(docid);
                var div_ref = document.getElementById("docbuttons");
                div_ref.style.visibility = "visible";
                if (doctype == "text/html") {
                    var div_ref = document.getElementById("refilebutton");
                    div_ref.style.visibility = "hidden";
                } else {
                    var div_ref = document.getElementById("refilebutton");
                    div_ref.style.visibility = "visible";
                }

            }
        }


        function setdefaultdoc() {

            var doclistObj = document.getElementById('doclist');
            if (doclistObj.length >= 1) {
                doclistObj.selectedIndex = 0;
                doclistObj.focus();

                getDoc();
                doclistObj.focus();
            } else if (doclistObj.length == 0) {
                div_ref = document.getElementById("docbuttons");
                div_ref.style.visibility = "hidden";

            }

        }

        function AddTickler() {
            <c:set var="__enc_1"><carlos:encode value='<%= demographicID %>' context="uriComponent"/></c:set>
            popup(450, 600, '<%=request.getContextPath()%>/tickler/ForwardDemographicTickler?docType=DOC&docId=' + docid + '&demographic_no=<carlos:encode value='${__enc_1}' context="javaScript"/>', 'tickler');
        }


        function DocEdit() {
            var th = document.getElementById('doclist');
            var selected = new Array();
            selected = getSelected(th);
            var docidindexend = selected[0].value.indexOf('-');
            docid = selected[0].value.substring(0, docidindexend);
            var doctype = selected[0].value.substring(docidindexend + 1, selected[0].value.length);

            if (doctype == 'text/html') {
                <c:set var="__enc_2"><carlos:encode value='<%= module %>' context="uriComponent"/></c:set>
                <c:set var="__enc_3"><carlos:encode value='<%= demographicID %>' context="uriComponent"/></c:set>
                popup(450,                
 600, '<%= request.getContextPath() %>/documentManager/ViewAddEditHtml?editDocumentNo=' + docid + '&function=<carlos:encode value='${__enc_2}' context="javaScript"/>&functionid=<carlos:encode value='${__enc_3}' context="javaScript"/>', 'EditDoc');
            } else {

                <c:set var="__enc_4"><carlos:encode value='<%= module %>' context="uriComponent"/></c:set>
                <c:set var="__enc_5"><carlos:encode value='<%= demographicID %>' context="uriComponent"/></c:set>
                popup(350, 500, '<%= request.getContextPath() %>/docume                
ntManager/ViewEditDocument?editDocumentNo=' + docid + '&function=<carlos:encode value='${__enc_4}' context="javaScript"/>&functionid=<carlos:encode value='${__enc_5}' context="javaScript"/>', 'EditDoc');
            }
        }

    </script>
</head>
<body onload="window.innerWidth=<%=winwidth.length()>0?winwidth:"screen.availWidth*0.9"%>;window.innerHeight=<%=winheight.length()>0?winheight:"screen.availHeight*0.9"%>;">
<form name="DisplayDoc" method="post" action="<%= request.getContextPath() %>/documentManager/ViewDocumentBrowser">

    <table>
        <%if (errorMessage.length() > 0) {%>
        <tr>
            <td><b><font color="red"><carlos:encode value='<%= errorMessage %>' context="html"/>
            </font></b></td>
        </tr>
        <%}%>
        <tr>
            <td align="left" valign="top" style="width: 400px">
                <oscar:nameage demographicNo="<%=moduleid%>"/><br>
                <carlos:encode value='<%= categoryKey %>' context="html"/>
                <br>

                <input type="hidden" name="viewstatus" value="<carlos:encode value='<%= viewstatus %>' context="htmlAttribute"/>">
                <input type="hidden" name="sortorder" value="<carlos:encode value='<%= sortorder %>' context="htmlAttribute"/>">
                <input type="hidden" name="function" value="<carlos:encode value='<%= module %>' context="htmlAttribute"/>">
                <input type="hidden" name="functionid" value="<carlos:encode value='<%= moduleid %>' context="htmlAttribute"/>">
                <input type="hidden" name="categorykey" value="<carlos:encode value='<%= categoryKey %>' context="htmlAttribute"/>">

                <fmt:message key="dms.documentBrowser.msgViewStatus"/> <select id="selviewstatus" name="selviewstatus"
                                                                                onchange="ReLoadDoc()">
                <option value="all"
                        <%=viewstatus.equalsIgnoreCase("all") ? "selected" : ""%>><fmt:message key="dms.documentBrowser.msgAll"/></option>
                <option value="deleted"
                        <%=viewstatus.equalsIgnoreCase("deleted") ? "selected" : ""%>><fmt:message key="dms.documentBrowser.msgDeleted"/></option>
                <option value="active"
                        <%=viewstatus.equalsIgnoreCase("active") ? "selected" : ""%>><fmt:message key="dms.documentBrowser.msgPublished"/></option>
            </select>

                <fmt:message key="dms.documentBrowser.msgSortDate"/>
                <select id="selsortorder" name="selsortorder" onchange="ReLoadDoc()">
                    <option value="Content"
                            <%=sortorder.equalsIgnoreCase("Content") ? "selected" : ""%>><fmt:message key="dms.documentBrowser.msgContent"/></option>
                    <option value="Observation"
                            <%=sortorder.equalsIgnoreCase("Observation") ? "selected" : ""%>><fmt:message key="dms.documentBrowser.msgObservation"/></option>
                    <option value="Update"
                            <%=sortorder.equalsIgnoreCase("Update") ? "selected" : ""%>><fmt:message key="dms.documentBrowser.msgUpdate"/></option>

                </select>
                <fieldset>
                    <legend><fmt:message key="dms.documentBrowser.msgView"/>:</legend>
                    <input type="hidden" name="view" value="<carlos:encode value='<%= view %>' context="htmlAttribute"/>">
                    <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= demographicID %>' context="htmlAttribute"/>">
                    <input type="hidden" name="undelDocumentNo" value="">
                    <input type="hidden" name="delDocumentNo" value="">
                    <input type="hidden" name="refileDocumentNo" value="">
                    <input type="hidden" name="queueId" value="<%=queueId%>">
                    <input type="hidden" name="source" value="browser">

                    <a
                            href="#" onclick="LoadView('all')"><%=view.equals("all") ? "<b>" : ""%>
                        All<%=view.equals("all") ? "</b>" : ""%>
                    </a> <% for (int i3 = 0; i3 < doctypes.size(); i3++) {%>
                    | <a
                        href="#"
                        onclick="LoadView('<carlos:encode value='<%= URLEncoder.encode((String) doctypes.get(i3),"UTF-8") %>' context="javaScriptAttribute"/>')"><%=view.equals((String) doctypes.get(i3)) ? "<b>" : ""%><carlos:encode value='<%= (String) doctypes.get(i3) %>' context="html"/><%=view.equals((String) doctypes.get(i3)) ? "</b>" : ""%>
                </a>
                    <%}%>
                </fieldset>

                <fieldset>
                    <legend><%
                        if (sortorder.equals("Content")) { %>
                        <fmt:message key="dms.documentBrowser.msgContent"/><%} else {%>
                        <fmt:message key="dms.documentBrowser.msgUpdate"/> <%}%>
                        <fmt:message key="dms.documentBrowser.ObservationTypeDescription"/></legend>
                    <SELECT MULTIPLE SIZE=15 id="doclist" onchange="getDoc();" style="width: 400px">
                        <%
                            for (int i2 = 0; i2 < docs.size(); i2++) {
                                EDoc cmicurdoc = docs.get(i2);
                        %>
                        <option VALUE="<carlos:encode value='<%= cmicurdoc.getDocId() + "-" + cmicurdoc.getContentType() %>' context="htmlAttribute"/>"><carlos:encode value='<%= sortorder.equals("Content") ? UtilDateUtilities.DateToString(cmicurdoc.getContentDateTime(), "yyyy-MM-dd") : cmicurdoc.getDateTimeStamp() %>' context="html"/>&nbsp;&nbsp; <carlos:encode value='<%= cmicurdoc.getObservationDate() %>' context="html"/>
                            [<carlos:encode value='<%= cmicurdoc.getType() %>' context="html"/>] <carlos:encode value='<%= cmicurdoc.getDescription() %>' context="html"/>
                        </option>
                        <%}%>
                    </SELECT>
                </fieldset>
                <div id="docbuttons">
                    <% if (viewstatus.equalsIgnoreCase("active")) {%>
                    <% if (module.equalsIgnoreCase("demographic")) {%>
                    <input type="button" value="<fmt:message key="dms.documentBrowser.msgAddTickler"/>"
                           onclick="AddTickler();"> <%}%>
                    <input type="button" value="<fmt:message key="dms.documentBrowser.msgEdit"/>" onclick="DocEdit();">
                    <input type="button" value="<fmt:message key="dms.documentBrowser.msgDelete"/>"
                           onclick="DeleteDoc();">
                    <div id="refilebutton">
                        <input type="button" value="<fmt:message key="dms.documentBrowser.msgRefile"/>"
                               onclick="RefileDoc();">
                        <select id="queueList" name="queueList" onchange="setQueue();">
                            <%
                                for (Hashtable ht : queues) {
                                    int id = (Integer) ht.get("id");
                                    String qName = (String) ht.get("queue");
                            %>
                            <option value="<%=id%>" <%=((id == queueId) ? " selected" : "")%>><carlos:encode value='<%= qName %>' context="html"/>
                            </option>
                            <%}%>
                        </select>
                    </div>
                    <%} else if (viewstatus.equalsIgnoreCase("deleted")) {%>
                    <input type="button" value="<fmt:message key="dms.documentBrowser.msgUndelete"/>"
                           onclick="UnDeleteDoc();">
                    <%}%>
                </div>
                <fieldset>
                    <div id="docextrainfo"></div>
                </fieldset>

            </td>

            <td valign="top">

                <table>
                    <tr>
                        <td>
                            <div id="docdisp"></div>
                        </td>
                    </tr>
                </table>

            </td>
        </tr>

    </table>
    <input type="hidden" name="winwidth" value="">
    <input type="hidden" name="winheight" value="">

    <script type="text/javascript">
        setdefaultdoc();
    </script>

</form>
</body>
</html>
