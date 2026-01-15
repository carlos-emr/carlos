<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>

<%@ page import="ca.openosp.OscarProperties" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    //TODO all of this below needs to be removed. It is unsafe. Consider using proper JSTL.
    @SuppressWarnings("unchecked")

//Long categoryHash       = (Long) request.getAttribute("categoryHash");
    String providerNo = (String) request.getAttribute("providerNo");
    String searchProviderNo = (String) request.getAttribute("searchProviderNo");
    boolean searchUnclaimed = "true".equalsIgnoreCase((String) request.getAttribute("searchUnclaimed"));
    String demographicNo = (String) request.getAttribute("demographicNo");
    String ackStatus = (String) request.getAttribute("ackStatus");
    String abnormalStatus = (String) request.getAttribute("abnormalStatus");

    String selectedCategory = request.getParameter("selectedCategory");
    if ("normalOnly".equals(abnormalStatus)) {
        selectedCategory = "4";
    } else if ("abnormalOnly".equals(abnormalStatus)) {
        selectedCategory = "5";
    }
//String selectedCategoryPatient = request.getParameter("selectedCategoryPatient");
    String selectedCategoryType = request.getParameter("selectedCategoryType");
    String isListView = request.getParameter("isListView");
//String currentProviderNo 	   = request.getParameter("providerNo");

    String patientFirstName = (String) request.getAttribute("patientFirstName");
    String patientLastName = (String) request.getAttribute("patientLastName");
    String patientHealthNumber = (String) request.getAttribute("patientHealthNumber");

//String startDate = (String) request.getAttribute("startDate");
//String endDate = (String) request.getAttribute("endDate");


%>

<!DOCTYPE html>
<html>
<head>
    <title>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.title"/>
    </title>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/css/showDocument.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/share/css/oscarMDSIndex.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/oscarMDS/encounterStyles.css">
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>

    <style type="text/css">
        .multiPage {
            background-color: RED;
            color: WHITE;
            font-weight: bold;
            padding: 0px 5px;
            font-size: medium;
        }

        #ticklerWrap {
            position: relative;
            top: 0px;
            background-color: #FF6600;
            width: 100%;
        }

        .completedTickler {
            opacity: 0.8;
            filter: alpha(opacity=80); /* For IE8 and earlier */
        }

        @media print {
            .DoNotPrint {
                display: none;
            }
        }

        .TDISRes {
            font-weight: bold;
            font-size: 10pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }
    </style>
</head>

<body vlink="#0000FF">
<jsp:include page="/images/spinner.jsp"/>
<div id="inbox_wrapper">
    <form name="reassignForm" method="post" action="ReportReassign.do" id="lab_form">
        <table>
            <tr>
                <td class="MainTableTopRowRightColumn" align="left">
                    <table>
                        <tr>
                            <td align="left" valign="top">
                                <input type="hidden" name="providerNo" value="<%= providerNo %>"/>
                                <input type="hidden" name="searchProviderNo" value="<%= searchProviderNo %>"/>
                                <%= (request.getParameter("lname") == null ? "" : "<input type=\"hidden\" name=\"lname\" value=\"" + request.getParameter("lname") + "\">") %>
                                <%= (request.getParameter("fname") == null ? "" : "<input type=\"hidden\" name=\"fname\" value=\"" + request.getParameter("fname") + "\">") %>
                                <%= (request.getParameter("hnum") == null ? "" : "<input type=\"hidden\" name=\"hnum\" value=\"" + request.getParameter("hnum") + "\">") %>
                                <input type="hidden" name="status" value="<%= ackStatus %>"/>
                                <input type="hidden" name="selectedProviders"/>
                                <input type="hidden" name="favorites" value=""/>
                                <input type="hidden" name="isListView" value=""/>
                                <input id="listSwitcher" type="button" style="display:none;" class="smallButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.listView"/>"
                                       onClick="switchView();"/>
                                <input id="readerSwitcher" type="button" class="smallButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.readerView"/>"
                                       onClick="switchView();"/>
                                <% if (demographicNo == null) { %>
                                <input type="button" class="smallButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnSearch"/>"
                                       onClick="window.location='${pageContext.servletContext.contextPath}/oscarMDS/Search.jsp?providerNo=<%= providerNo %>'"/>
                                <% } %>
                                <input type="button" class="smallButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnLoadAll"/>"
                                       onClick="showAllInbox()"/>
                                <input type="button" class="smallButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnClose"/>" onClick="wrapUp()"/>
                            </td>

                            <td align="right" valign="top">
                                <a href="javascript:parent.reportWindow('${pageContext.servletContext.contextPath}/oscarMDS/ForwardingRules.jsp?providerNo=<%= providerNo %>');"
                                   style="color: #FFFFFF;">Forwarding Rules</a>
                                <a href="javascript:popupStart(800,1000,'${pageContext.servletContext.contextPath}/lab/CA/ALL/testUploader.jsp')"
                                   style="color: #FFFFFF; "><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.hl7LabUpload"/></a>
                                <% if (OscarProperties.getInstance().getBooleanProperty("legacy_document_upload_enabled", "true")) { %>
                                <a href="javascript:popupStart(600,500,'${pageContext.servletContext.contextPath}/documentManager/html5AddDocuments.jsp')"
                                   style="color: #FFFFFF; "><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.uploadDoc"/></a>
                                <% } else { %>
                                <a href="javascript:popupStart(800,1000,'${pageContext.servletContext.contextPath}/documentManager/documentUploader.jsp')"
                                   style="color: #FFFFFF; "><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.uploadDoc"/></a>

                                <%--    Soon:         	<a href="javascript:void(0)" style="color:white;" class="dialog-link" id="/documentManager/documentUploader.jsp" >
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.uploadDoc"/>
                                                </a> --%>
                                <% } %>

                                <a href="javascript:popupStart(700,1100,'${pageContext.servletContext.contextPath}/documentManager/inboxManage.do?method=getDocumentsInQueues')"
                                   style="color: #FFFFFF;"><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.pendingDocs"/></a>

                                <a href="javascript:popupStart(800,1200,'${pageContext.servletContext.contextPath}/documentManager/incomingDocs.jsp')"
                                   style="color: #FFFFFF;"><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.incomingDocs"/></a>

                                <% if (!OscarProperties.getInstance().isBritishColumbiaBillingRegion()) { %>
                                <a href="javascript:popupStart(800,1000, '${pageContext.servletContext.contextPath}/oscarMDS/CreateLab.jsp')"
                                   style="color: #FFFFFF;"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.createLab"/></a>
                                <a href="javascript:popupStart(800,1000, '${pageContext.servletContext.contextPath}/olis/Search.jsp')"
                                   style="color: #FFFFFF;"><fmt:setBundle basename="oscarResources"/><fmt:message key="olis.olisSearch"/></a>
                                <a href="javascript:popupPage(400, 1050,'${pageContext.servletContext.contextPath}/hospitalReportManager/hospitalReportManager.jsp')"
                                   style="color: #FFFFFF;">HRM Status/Upload</a>
                                <% } %>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>

        <table id="readerViewTable" style="table-layout: fixed;background-color: #E0E1FF">
            <col width="175">
            <col width="100%">
            <tr>
                <td id="categoryList" valign="top" style="background-color: #E0E1FF">

                    <input type="hidden" id="categoryHash" value="${requestScope.categoryData.categoryHash}"/>
                    <div class="documentSummaryList">
                        <c:if test="${ requestScope.categoryData.totalNumDocs gt 0}">
                            <details id="masterDocumentSummary" ${ param.providerNo gt 0 ? 'open' : ''}>
                                <summary>
                                    <a id="totalAll" href="javascript:void(0);"
                                       onclick="un_bold(this);changeView(CATEGORY_ALL);">
                                        All (<span id="totalNumDocs"><c:out
                                            value="${requestScope.categoryData.totalNumDocs}"/></span>)
                                    </a>
                                </summary>
                                <ul>

                                    <c:if test="${ requestScope.categoryData.totalDocs gt 0 }">
                                        <li>
                                            <a id="totalDocs" href="javascript:void(0);"
                                               onclick="un_bold(this);changeView(CATEGORY_DOCUMENTS);"
                                               title="Documents">Documents (<span id="totalDocsNum"><c:out
                                                    value="${requestScope.categoryData.totalDocs}"/></span>)
                                            </a>
                                        </li>
                                    </c:if>

                                    <c:if test="${ requestScope.categoryData.totalLabs  gt 0 }">
                                        <li>
                                            <a id="totalHL7s" href="javascript:void(0);"
                                               onclick="un_bold(this);changeView(CATEGORY_HL7);" title="HL7">
                                                HL7 (<span id="totalHL7Num"><c:out
                                                    value="${requestScope.categoryData.totalLabs}"/></span>)
                                            </a>
                                        </li>
                                    </c:if>

                                    <li>
                                        <a id="totalNormals" href="javascript:void(0);"
                                           onclick="un_bold(this);changeView(CATEGORY_NORMAL);" title="Normal">
                                            Normal
                                        </a>
                                    </li>

                                    <li>
                                        <a id="totalAbnormals" href="javascript:void(0);"
                                           onclick="un_bold(this);changeView(CATEGORY_ABNORMAL);" title="Abnormal">
                                            Abnormal
                                        </a>
                                    </li>
                                </ul>
                            </details>
                        </c:if>

                        <c:if test="${ requestScope.categoryData.unmatchedDocs gt 0 or requestScope.categoryData.unmatchedLabs gt 0 }">
                            <details class="unmatchedList" ${ param.providerNo eq 0 ? 'open' : '' }>
                                <summary>
                                    <a id="patient0all" href="javascript:void(0);"
                                       onclick="un_bold(this);changeView(CATEGORY_PATIENT,0)"
                                       title="Unmatched">Unmatched (<span id="patientNumDocs0"><c:out
                                            value="${requestScope.categoryData.unmatchedDocs + requestScope.categoryData.unmatchedLabs}"/></span>)
                                    </a>
                                </summary>

                                <ul id="labdoc0showSublist">
                                    <c:if test="${ requestScope.categoryData.unmatchedDocs gt 0}">
                                        <li>
                                            <a id="patient0docs" href="javascript:void(0);"
                                               onclick="un_bold(this);changeView(CATEGORY_PATIENT_SUB,0,CATEGORY_TYPE_DOC);"
                                               title="Documents">
                                                Documents (<span id="pDocNum_0"><c:out
                                                    value="${requestScope.categoryData.unmatchedDocs}"/></span>)
                                            </a>
                                        </li>
                                    </c:if>
                                    <c:if test="${ requestScope.categoryData.unmatchedLabs gt 0 }">
                                        <li>
                                            <a id="patient0hl7s" href="javascript:void(0);"
                                               onclick="un_bold(this);changeView(CATEGORY_PATIENT_SUB,0,CATEGORY_TYPE_HL7);"
                                               title="HL7">
                                                HL7 (<span id="pLabNum_0"><c:out
                                                    value="${requestScope.categoryData.unmatchedLabs}"/></span>)
                                            </a>
                                        </li>
                                    </c:if>

                                </ul>
                            </details>
                        </c:if>

                        <c:if test="${ not empty requestScope.categoryData.patientList }">

                            <div id="patientsdoclabs">
                                <details id="patientsdoclabsDetails" open>
                                    <summary id="patientsdoclabsSummary">Matched</summary>
                                    <c:forEach items="${requestScope.categoryData.patientList}" var="patient">
                                        <c:set var="patientId" value="${patient.id}"/>
                                        <c:set var="patientName" value="${ patient.lastName }, ${patient.firstName}"/>
                                        <c:set var="numDocs" value="${ patient.docCount + patient.labCount }"/>

                                        <%--						<c:if test="${inboxItemDemographicCount.labType eq 'DOC'}">--%>
                                        <c:set var="docCount" value="${ patient.docCount }"/>
                                        <%--						</c:if>--%>
                                        <%--						<c:if test="${inboxItemDemographicCount.labType eq 'HL7'}">--%>
                                        <c:set var="labCount" value="${ patient.labCount }"/>
                                        <%--						</c:if>--%>
                                        <%--						<c:if test="${inboxItemDemographicCount.labType eq 'HRM'}">--%>
                                        <%--							<c:set var="hrmCount" value="${ inboxItemDemographicCount.count }" />--%>
                                        <%--						</c:if>--%>


                                        <details id="patientsdoclabsDetailList">
                                            <summary>
                                                <a id="patient${patientId}all" href="javascript:void(0);"
                                                   onclick="un_bold(this);changeView(CATEGORY_PATIENT,${patientId});"
                                                   title="<c:out value='${patientName}' />">
                                                    <c:out value='${patientName}'/> (<span
                                                        id="patientNumDocs${patientId}">${numDocs}</span>)
                                                </a>
                                            </summary>

                                            <ul id="labdoc${patientId}showSublist">
                                                <c:if test="${not empty docCount}">
                                                    <li>
                                                        <a id="patient${patientId}docs" href="javascript:void(0);"
                                                           onclick="un_bold(this);changeView(CATEGORY_PATIENT_SUB,${patientId},CATEGORY_TYPE_DOC);"
                                                           title="Documents">
                                                            Documents (<span
                                                                id="pDocNum_${patientId}">${docCount}</span>)
                                                        </a>
                                                    </li>
                                                </c:if>
                                                <c:if test="${not empty labCount}">
                                                    <li>
                                                        <a id="patient${patientId}hl7s" href="javascript:void(0);"
                                                           onclick="un_bold(this);changeView(CATEGORY_PATIENT_SUB,${patientId},CATEGORY_TYPE_HL7);"
                                                           title="HL7">
                                                            HL7 (<span id="pLabNum_${patientId}">${labCount}</span>)
                                                        </a>
                                                    </li>
                                                </c:if>
                                                    <%--									<c:if test="${not empty hrmCount}">--%>
                                                    <%--										<li>--%>
                                                    <%--											<a id="patient${patientId}hl7s" href="javascript:void(0);" onclick="un_bold(this);changeView(CATEGORY_PATIENT_SUB,${patientId},CATEGORY_TYPE_HRM);" title="HL7">--%>
                                                    <%--												HL7 (<span id="pLabNum_${patientId}">${hrmCount}</span>)--%>
                                                    <%--											</a>--%>
                                                    <%--										</li>--%>
                                                    <%--									</c:if>--%>
                                            </ul>
                                        </details>
                                        <c:if test="${requestScope.selectedCategoryPatient eq patientId}">
                                            <script>
                                                un_bold($("patient" +${patientId}<%=(selectedCategoryType.equals("CATEGORY_TYPE_HL7"))?"hl7s":(selectedCategoryType.equals("CATEGORY_TYPE_DOC")?"docs":"all")%>));
                                            </script>
                                        </c:if>
                                    </c:forEach>

                                </details>

                            </div>
                        </c:if>
                    </div>
                </td>

                <td style="background-color: #E0E1FF; height:600px;" valign="top">
                    <div id="docViews" style="overflow:scroll; height:600px; width:100%;" onscroll="handleScroll(this)">

                    </div>
                </td>
            </tr>
        </table>
    </form>


    <!-- main calendar program -->
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/share/calendar/calendar.js"></script>
    <!-- language for the calendar -->
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/calendar/lang/<fmt:setBundle basename='oscarResources'/><fmt:message key='global.javascript.calendar'/>"></script>
    <!-- the following script defines the Calendar.setup helper function, which makes
           adding a calendar a matter of 1 or 2 lines of code. -->
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/calendar/calendar-setup.js"></script>
    <!-- calendar style sheet -->


    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/js/jquery.tablesorter.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/js/jquery.tablesorter.widgets.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/js/demographicProviderAutocomplete.js"></script>

    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/javascript/jquery/jquery.form.js"></script>
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/global.js"></script>
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/share/javascript/Oscar.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/yui/js/yahoo-dom-event.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/yui/js/connection-min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/yui/js/animation-min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/share/yui/js/datasource-min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/documentManager/showDocument.js"></script>

    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/hospitalReportManager/hrmActions.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/js/documentDescriptionTypeahead.js"></script>

    <script type="text/javascript">
        jQuery.noConflict();

        const ctx = "${pageContext.servletContext.contextPath}";
        var contextpath = ctx;

        jQuery(window).on("scroll", handleScroll());

        function renderCalendar(id, inputFieldId) {
            Calendar.setup({inputField: inputFieldId, ifFormat: "%Y-%m-%d", showsTime: false, button: id});

        }

        function split(id) {
            var loc = ctx + "/oscarMDS/Split.jsp?document=" + id;
            popupStart(1100, 1100, loc, "Splitter");
        }

        var page = 1;
        var pageSize = 20;
        var selected_category = <%=(selectedCategory == null ? "1" : selectedCategory)%>;
        let selected_category_patient = "${requestScope.selectedCategoryPatient}";
        var selected_category_type = <%=(selectedCategoryType == null ? "\"\"" : selectedCategoryType)%>;
        var searchProviderNo = "<%=(searchProviderNo == null ? "" : searchProviderNo)%>";
        var firstName = "<%=(patientFirstName == null ? "" : patientFirstName)%>";
        var lastName = "<%=(patientLastName == null ? "" : patientLastName)%>";
        var hin = "<%=(patientHealthNumber == null ? "" : patientHealthNumber)%>";
        var providerNo = "<%=(providerNo == null ? "" : providerNo)%>";
        var searchStatus = "<%=(ackStatus == null ? "": ackStatus)%>";
        var abnormalStatus = "<%=abnormalStatus == null || "all".equals(abnormalStatus) ? "L" : (abnormalStatus.equals("normalOnly") ? "N" : "A")%>"
        var url = ctx + "/documentManager/inboxManage.do?";
        const startDate = "${requestScope.startDate}";
        const endDate = "${requestScope.endDate}";
        var abortController = null;
        var canLoad = true;
        console.log("<%= isListView == null %>");
        var isListView = <%= isListView %>;
        var loadingDocs = false;
        var currentBold = false;
        var oldestDate = null;

        window.changePage = function (p) {
            if (p === "Next") {
                page++;
            } else if (p === "Previous") {
                page--;
            } else {
                page = p;
            }
            if (abortController != null) {
                abortController.abort();
            }
            updateListView();
        };

        function handleScroll(e) {
            if (!canLoad || loadingDocs) {
                return false;
            }
            var evt = e || window.event;
            var loadMore = false;
            var listViewDocsEl = document.getElementById("listViewDocs");
            if (isListView && listViewDocsEl && evt.scrollHeight > listViewDocsEl.clientHeight && evt.scrollTop > 0 && evt.scrollTop + evt.offsetHeight >= evt.scrollHeight) {
                loadMore = true;
            } else if (isListView && listViewDocsEl && evt.scrollHeight <= listViewDocsEl.clientHeight) {
                loadMore = true;
            } else if (!isListView && evt.scrollTop + evt.offsetHeight >= evt.scrollHeight) {
                loadMore = true;
            }
            if (loadMore) {
                loadingDocs = true;
                changePage("Next");
            }
        }

        function fakeScroll() {
            var scroller;
            if (isListView) {
                scroller = document.getElementById("summaryView");
                jQuery("#summaryView").trigger("updateRows");
            } else {
                scroller = document.getElementById("docViews");
            }
            handleScroll(scroller);
        }

        function showAllInbox() {
            page = 1;
            pageSize = 1000;
            updateListView();
        }

        function updateListView() {
            var query = getQuery();
            console.log(query);
            var docViewsEl = document.getElementById("docViews");
            if (page == 1) {
                if (docViewsEl) docViewsEl.innerHTML = "";
                canLoad = true;
            }

            if (isListView && page == 1) {
                if (docViewsEl) docViewsEl.innerHTML = "<div id='tempLoader'><img src='" + ctx + "/images/DMSLoader.gif'> Loading reports...</div>"
            } else if (isListView) {
                var loaderEl = document.getElementById("loader");
                if (loaderEl) loaderEl.style.display = "block";
            } else {
                jQuery("#docViews").append(jQuery("<div id='tempLoader'><img src='" + ctx + "/images/DMSLoader.gif'> Loading reports...</div>"));
            }
            var div;
            if (!isListView || page == 1) {
                div = docViewsEl;
            } else {
                div = document.getElementById("summaryBody");
            }
            jQuery("#readerSwitcher").prop("disabled", true);
            jQuery("#listSwitcher").prop("disabled", true);

            abortController = new AbortController();

            fetch(url + query, {
                method: 'GET',
                signal: abortController.signal
            })
            .then(function(response) {
                return response.text();
            })
            .then(function(responseText) {
                // Append the response HTML to the target div
                if (div) {
                    div.insertAdjacentHTML('beforeend', responseText);
                }

                loadingDocs = false;
                var tmp = document.getElementById("tempLoader");
                if (tmp != null) {
                    tmp.remove();
                }
                if (isListView) {
                    if (page == 1) {
                        var tempLoader = document.getElementById("tempLoader");
                        if (tempLoader) tempLoader.remove();
                    } else {
                        var loaderEl = document.getElementById("loader");
                        if (loaderEl) loaderEl.style.display = "none";
                    }
                }

                // Parsing HTML with DOMParser, checking to see if input with the name of "NoMoreItems"
                // is not null, if true then set and update html to show information about "NoMoreItems"
                // if null, then it will fake scroll to populate the information in the list
                var parser = new DOMParser();
                var doc = parser.parseFromString(responseText, "text/html");
                var noMoreItems = doc.querySelector("input[name='NoMoreItems']");

                if (noMoreItems) {
                    canLoad = false;
                    var summaryDiv = document.getElementById("summaryBody");
                    // Only do table manipulation if we're in list view and summaryBody exists
                    if (summaryDiv && isListView) {
                        var newDiv = "<tbody id=\"newBody\"></tbody>";
                        summaryDiv.insertAdjacentHTML("beforeBegin", newDiv);
                        newDiv = document.getElementById("newBody");
                        newDiv.textContent = '';
                        while (summaryDiv.firstChild) {
                            newDiv.appendChild(summaryDiv.firstChild);
                        }
                        newDiv.id = "summaryBody";
                        summaryDiv.id = "";
                        jQuery("#summaryView").trigger("updateRows");
                    }
                } else {
                    // It is possible that the current amount of loaded items has not filled up the page enough
                    // to create a scroll bar. So we fake a scroll (since no scroll bar is equivalent to reaching the bottom).
                    setTimeout("fakeScroll()", 500);
                }

                var readerSwitcherEl = document.getElementById("readerSwitcher");
                var listSwitcherEl = document.getElementById("listSwitcher");
                if (readerSwitcherEl) readerSwitcherEl.disabled = false;
                if (listSwitcherEl) listSwitcherEl.disabled = false;

                abortController = null;
            })
            .catch(function(error) {
                if (error.name !== 'AbortError') {
                    console.error('Fetch error:', error);
                }
                loadingDocs = false;
                abortController = null;
            });
        }

        function getQuery() {
            var CATEGORY_ALL = 1, CATEGORY_DOCUMENTS = 2, CATEGORY_HL7 = 3, CATEGORY_NORMAL = 4, CATEGORY_ABNORMAL = 5,
                CATEGORY_PATIENT = 6, CATEGORY_PATIENT_SUB = 7, CATEGORY_HRM = 8, CATEGORY_TYPE_DOC = 'DOC',
                CATEGORY_TYPE_HL7 = 'HL7', CATEGORY_TYPE_HRM = 'HRM';
            var query = "method=prepareForContentPage";
            query += "&searchProviderNo=" + searchProviderNo + "&providerNo=" + providerNo + "&status=" + searchStatus + "&page=" + page
                + "&pageSize=" + pageSize + "&isListView=" + (isListView ? "true" : "false")
                + "&startDate=" + startDate + "&endDate=" + endDate;
            switch (selected_category) {
                case CATEGORY_ALL:
                    query += "&view=all";
                    query += "&fname=" + firstName + "&lname=" + lastName + "&hnum=" + hin;
                    break;
                case CATEGORY_DOCUMENTS:
                    query += "&view=documents";
                    query += "&fname=" + firstName + "&lname=" + lastName + "&hnum=" + hin;
                    if (abnormalStatus !== '') {
                        query += "&abnormalStatus=" + abnormalStatus;
                    }
                    break;
                case CATEGORY_HRM:
                    query += "&view=hrms";
                    query += "&fname=" + firstName + "&lname=" + lastName + "&hnum=" + hin;
                    if (abnormalStatus !== '') {
                        query += "&abnormalStatus=" + abnormalStatus;
                    }
                    break;
                case CATEGORY_HL7:
                    query += "&view=labs";
                    query += "&fname=" + firstName + "&lname=" + lastName + "&hnum=" + hin;
                    if (abnormalStatus !== '') {
                        query += "&abnormalStatus=" + abnormalStatus;
                    }
                    break;
                case CATEGORY_NORMAL:
                    query += "&view=normal";
                    query += "&fname=" + firstName + "&lname=" + lastName + "&hnum=" + hin;
                    break;
                case CATEGORY_ABNORMAL:
                    query += "&view=abnormal";
                    query += "&fname=" + firstName + "&lname=" + lastName + "&hnum=" + hin;
                    break;
                case CATEGORY_PATIENT:
                    query += "&view=all&demographicNo=" + selected_category_patient;
                    break;
                case CATEGORY_PATIENT_SUB:
                    query += "&demographicNo=" + selected_category_patient;
                    switch (selected_category_type) {
                        case CATEGORY_TYPE_DOC:
                            query += "&view=documents";
                            break;
                        case CATEGORY_TYPE_HL7:
                            query += "&view=labs";
                            break;
                        case CATEGORY_TYPE_HRM:
                            query += "&view=hrms";
                            break;
                    }
                    break;
            }

            if (oldestLab) {
                query += "&newestDate=" + oldestLab;
            }

            return query;
        }

        function changeView(type, patientId, subtype) {
            loadingDocs = true;
            selected_category = type;
            selected_category_patient = patientId;
            selected_category_type = subtype;
            var docViewsEl = document.getElementById("docViews");
            if (docViewsEl) docViewsEl.innerHTML = "";

            changePage(1);
        }

        function switchView() {
            var newUrl;
            if (location.href.includes("isListView")) {
                newUrl = location.href.replace("isListView=" + isListView, "isListView=" + !isListView);
            } else {
                newUrl = location.href + "&isListView=" + !isListView
            }
            window.location.href = newUrl;
        }

        jQuery(document).ready(function () {
            if (isListView == null) {
                isListView = !selected_category_patient || selected_category_patient.length === 0;
            }
            jQuery('input[name=isListView]').val(isListView);

            loadingDocs = true;
            var docViewsEl = document.getElementById("docViews");
            if (docViewsEl) docViewsEl.innerHTML = "";
            var list = document.getElementById("listSwitcher");
            var view = document.getElementById("readerSwitcher");
            var active, passive;
            if (isListView) {
                pageSize = 20;
                active = view;
                passive = list;
            } else {
                pageSize = 5;
                active = list;
                passive = view;
            }
            active.style.display = "inline";
            passive.style.display = "none";

            changePage(1);
            //un_bold($("totalAll"));
            currentBold = "totalAll";
            refreshCategoryList();
        });

        /* function ForwardSelectedRows() {
            var query = jQuery(document.reassignForm).formSerialize();
            var labs = jQuery("input[name='flaggedLabs']:checked");
            for (var i = 0; i < labs.length; i++) {
                query += "&flaggedLabs=" + labs[i].value;
                query += "&" + jQuery(labs[i]).next().name + "=" + jQuery(labs[i]).next().value;
            }
            jQuery.ajax({
                type: "POST",
                url:  ctx + "/oscarMDS/ReportReassign.do",
                data: query,
                success: function (data) {
                    jQuery("input[name='flaggedLabs']:checked").each(function () {
                        this.checked = false;
                    });
                }
            });
        }*/

        function refreshCategoryList() {
            jQuery("#categoryHash").val("-1");
            updateCategoryList();
        }

        function updateCategoryList() {
            jQuery.ajax({
                type: "GET",
                url: ctx + "/documentManager/inboxManage.do",
                data: window.location.search.substr(1) + "&ajax=true",
                success: function (data) {
                    if (jQuery("#categoryHash").length == 0 || jQuery(data)[2].value != jQuery("#categoryHash").val()) {
                        var categoryList = jQuery("#categoryList");

                        if (categoryList !== undefined) {
                            categoryList.replaceWith(jQuery(data).find("#categoryList"));
                            re_bold(currentBold);
                        }
                    }
                }
            });
        }


        window.removeReport = function (reportId) {
            var el = jQuery("#labdoc_" + reportId);
            if (el != null) {
                el.remove();
            }
            refreshCategoryList();
            fakeScroll();
        }

        // Jquery modal windows
        jQuery(document).ready(function () {

            jQuery(function () {

                jQuery("#dialog").dialog({
                    autoOpen: false,
                    modal: true
                });

                jQuery(".dialog-link").on("click", function (event) {
                    event.preventDefault();
                    var url = ctx + this.id;
                    jQuery("#dialog").load(url).dialog({modal: true});
                });
            });
        });
    </script>

</div> <!--  end wrapper  -->

<script type="text/javascript"
        src="${pageContext.servletContext.contextPath}/share/javascript/oscarMDSIndex.js"></script>
<div id="dialog"></div>

</body>
</html>



