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
<!DOCTYPE html>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ViewDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.View" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="java.util.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%!
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    ViewDao viewDao = SpringUtils.getBean(ViewDao.class);
%>

<%
    String labReqVer = io.github.carlos_emr.OscarProperties.getInstance().getProperty("onare_labreqver", "07");
    if (labReqVer.isEmpty()) {
        labReqVer = "07";
    }

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String user_no = (String) session.getAttribute("user");
    String createReport = request.getParameter("Submit");
    boolean doCreateReport = createReport != null && createReport.equals("Create Report");
    String userRole = (String) session.getAttribute("userrole");

    String demographic_no = request.getParameter("demoview");
    if (demographic_no == null || demographic_no.isEmpty()) {
        demographic_no = "0";
    }
    boolean isDemoView = !"0".equals(demographic_no) && demographic_no != null;

    Map<String, View> ticklerView = viewDao.getView("tickler", userRole, user_no);

    String providerview = "all";
    if (!"0".equals(demographic_no)) {
        // do nothing
    } else if (ticklerView.get("providerview") != null && !doCreateReport) {
        providerview = ticklerView.get("providerview").getValue();
    } else if (request.getParameter("providerview") != null) {
        providerview = request.getParameter("providerview");
    }

    String assignedTo = "all";
    if (!"0".equals(demographic_no)) {
        // do nothing
    } else if (ticklerView.get("assignedTo") != null && !doCreateReport) {
        assignedTo = ticklerView.get("assignedTo").getValue();
    } else if (request.getParameter("assignedTo") != null) {
        assignedTo = request.getParameter("assignedTo");
    }

    String mrpview = "all";
    if (!"0".equals(demographic_no)) {
        // do nothing
    } else if (ticklerView.get("mrpview") != null && !doCreateReport) {
        mrpview = ticklerView.get("mrpview").getValue();
    } else if (request.getParameter("mrpview") != null) {
        mrpview = request.getParameter("mrpview");
    }

    String ticklerview = "A";
    View statusView = ticklerView.get("ticklerview");
    if (statusView != null && !doCreateReport) {
        ticklerview = statusView.getValue();
    } else if (request.getParameter("ticklerview") != null) {
        ticklerview = request.getParameter("ticklerview");
    }

    String xml_vdate = "";
    if (request.getParameter("xml_vdate") != null) {
        xml_vdate = request.getParameter("xml_vdate");
    }

    Calendar now = Calendar.getInstance();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    String xml_appointment_date = MyDateFormat.getMysqlStandardDate(curYear, curMonth, curDay);
    if (request.getParameter("xml_appointment_date") != null) {
        xml_appointment_date = request.getParameter("xml_appointment_date");
    }
    if (!"0".equals(demographic_no)) {
        xml_appointment_date = "8888-12-31";
    }

    String parentAjaxId = request.getParameter("parentAjaxId");
    String demoviewParam = request.getParameter("demoview");
%>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.managerHeading"/></title>

        <%@ include file="/includes/global-head.jspf" %>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.js"></script>
        <link href="<%= request.getContextPath() %>/library/DataTables/DataTables-1.13.4/css/jquery.dataTables.css"
              rel="stylesheet" type="text/css"/>
        <link rel="stylesheet" type="text/css" media="print" href="<%= request.getContextPath() %>/css/print.css"/>

        <style>
            /* Comment rows */
            table tr.comment-row td:nth-of-type(3) { color: transparent; }
            tr.comment-row td { color: grey; background-color: white !important; }
            a.noteDialogLink { text-decoration: none !important; }

            tr.error td { color: red !important; }

            /* Table links — CARLOS primary blue */
            #ticklerResults a { color: #337ab7; }
            #ticklerResults a:hover { color: #28619a; }

            /* 3. Table headers — prevent wrapping */
            #ticklerResults thead th {
                white-space: nowrap;
                font-size: 13px;
            }

            /* 2. Message column — constrain width with truncation on hover */
            #ticklerResults td:nth-child(10) {
                max-width: 220px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
            #ticklerResults td:nth-child(10):hover {
                white-space: normal;
                overflow: visible;
                word-break: break-word;
            }

            /* 1. Filter bar — compact inline controls */
            .tickler-filters {
                padding: 10px 15px;
            }
            .tickler-filters label {
                margin-bottom: 0;
                margin-right: 2px;
            }
            .tickler-filters .form-select,
            .tickler-filters .form-control {
                display: inline-block;
                width: auto;
            }
            .tickler-filters .form-select {
                max-width: 180px;
            }

            /* 4. Action bar — grouped sections */
            .action-bar {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                margin-top: 8px;
                margin-bottom: 50px;
                gap: 6px;
            }
            .action-bar .action-group {
                display: flex;
                align-items: center;
                gap: 6px;
            }
            .action-bar .action-separator {
                width: 1px;
                height: 24px;
                background: #ddd;
                margin: 0 6px;
            }

            /* 5. Hide DataTables "Show entries" — filter bar controls the view */
            .dataTables_wrapper .dataTables_length { display: none; }

            /* CARLOS primary (#337ab7) on all buttons */
            .btn-primary {
                background-color: #337ab7 !important;
                border-color: #337ab7 !important;
            }
            .btn-primary:hover {
                background-color: #286090 !important;
                border-color: #204d74 !important;
            }

            /* DataTables — CARLOS design overrides */
            table.dataTable thead th {
                background-color: #f5f5f5 !important;
                border-bottom: 2px solid #337ab7 !important;
                color: #333;
                font-weight: 600;
            }
            .dataTables_wrapper .dataTables_paginate .paginate_button.current,
            .dataTables_wrapper .dataTables_paginate .paginate_button.current:hover {
                background: #337ab7 !important;
                color: white !important;
                border-color: #337ab7 !important;
            }
            .dataTables_wrapper .dataTables_info,
            .dataTables_wrapper .dataTables_paginate {
                font-size: 13px;
            }

            @media print {
                .searchBox, .page-header-bar { display: none; }
            }
        </style>
        <script type="application/javascript">


            const ctx = '${pageContext.request.contextPath}';
            const i18nEditTickler = '<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.tooltipEdit"/>';
            const i18nAddNote = '<fmt:message key="tickler.ticklerMain.tooltipAddNote"/>';
            const i18nViewAttachment = '<fmt:message key="tickler.ticklerMain.tooltipViewAttachment"/>';
            let ticklerResultsTable;
            document.addEventListener('DOMContentLoaded', function () {
                jQuery("#note-form").dialog({
                    autoOpen: false,
                    height: 200,
                    width: 450,
                    modal: true,
                    close: function () {

                    }
                });

                var savedPageLength = localStorage.getItem('ticklerPageLength');
                var parsedPageLength = savedPageLength ? parseInt(savedPageLength, 10) : 50;
                var initialPageLength = [25, 50, 100].indexOf(parsedPageLength) !== -1 ? parsedPageLength : 50;

                ticklerResultsTable = jQuery("#ticklerResults").DataTable({
                    serverSide: true,
                    processing: true,
                    searching: true,
                    lengthMenu: [[25, 50, 100, -1], [25, 50, 100, '<fmt:message key="oscarEncounter.LeftNavBar.AllLabs"/>']],
                    pageLength: initialPageLength,
                    order: [[4, 'desc']],
                    language: {
                        url: '${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18nLanguagecode"/>.json'
                        },
                    ajax: {
                        url: ctx + '/tickler/ListTicklers.do',
                        type: 'GET',
                        data: function(d) {
                            d.status = document.getElementById('ticklerview').value || 'A';
                            d.provider = document.getElementById('providerview') ? document.getElementById('providerview').value : '';
                            d.assignee = document.getElementById('assignedTo') ? document.getElementById('assignedTo').value : '';
                            d.mrp = document.getElementById('mrpview') ? document.getElementById('mrpview').value : '';
                            d.startDate = document.getElementById('xml_vdate') ? document.getElementById('xml_vdate').value : '';
                            d.endDate = document.getElementById('xml_appointment_date') ? document.getElementById('xml_appointment_date').value : '';
                            d.demographicNo = (document.querySelector('input[name=demoview]') || {}).value || '';
                            if (d.provider === 'all') d.provider = '';
                            if (d.assignee === 'all') d.assignee = '';
                            if (d.mrp === 'all') d.mrp = '';
                            if (d.demographicNo === '0') d.demographicNo = '';
                        },
                        dataSrc: function(json) {
                            window._ticklerComments = json.comments || {};
                            return json.data;
                        }
                    },
                    columns: [
                        {
                            data: 'id',
                            orderable: false,
                            render: function(data) {
                                return '<input type="checkbox" name="checkbox" value="' + escapeHtml(String(data)) + '" class="noprint">';
                            }
                        },
                        {
                            data: 'id',
                            orderable: false,
                            render: function(data) {
                                return '<a href="javascript:void(0)" title="' + i18nEditTickler + '" onClick="openTicklerEdit(this,' + encodeURIComponent(data) + ')"><span class="fas fa-pencil-alt"></span></a>';
                            }
                        },
                        {
                            data: null,
                            orderable: false,
                            render: function(data) {
                                var name = escapeHtml(data.demographicName || 'N/A');
                                return '<a class="nav-link" href="javascript:void(0)" onClick="popupPage(600,800,\'' + ctx + '/demographic/demographiccontrol.jsp?demographic_no=' + encodeURIComponent(data.demographicNo) + '&displaymode=edit&dboperation=search_detail\')">' + name + '</a>';
                            }
                        },
                        {
                            data: 'creatorName',
                            orderable: false,
                            render: function(data) {
                                return escapeHtml(data || 'N/A');
                            }
                        },
                        {
                            data: 'serviceDate',
                            render: function(data) {
                                return escapeHtml(data || '');
                            }
                        },
                        {
                            data: 'createDate',
                            orderable: false,
                            render: function(data) {
                                return escapeHtml(data || '');
                            }
                        },
                        {
                            data: 'priority',
                            render: function(data) {
                                return escapeHtml(data || 'Normal');
                            }
                        },
                        {
                            data: 'assigneeName',
                            orderable: false,
                            render: function(data) {
                                return escapeHtml(data || 'N/A');
                            }
                        },
                        {
                            data: 'statusDesc',
                            orderable: false,
                            render: function(data) {
                                return escapeHtml(data || '');
                            }
                        },
                        {
                            data: null,
                            orderable: false,
                            render: function(data) {
                                var html = '<span style="white-space:pre-wrap">' + escapeHtml(data.message || '') + '</span>';
                                if (data.links && data.links.length > 0) {
                                    for (var i = 0; i < data.links.length; i++) {
                                        html += buildAttachmentLink(data.links[i].tableName, data.links[i].tableId);
                                    }
                                }
                                return html;
                            }
                        },
                        {
                            data: null,
                            orderable: false,
                            render: function(data) {
                                return '<a href="javascript:void(0)" class="noteDialogLink noprint" onClick="openNoteDialog(\'' + escapeHtml(String(data.demographicNo)) + '\',\'' + escapeHtml(String(data.id)) + '\')" title="' + i18nAddNote + '"><span class="fas fa-comment"></span></a>';
                            }
                        }
                    ],
                    createdRow: function(row, data) {
                        if (data.warning) {
                            row.classList.add('error');
                        }
                    },
                    drawCallback: function(settings) {
                        var api = this.api();
                        var rows = api.rows({page: 'current'}).nodes();
                        var comments = window._ticklerComments || {};

                        api.rows({page: 'current'}).data().each(function(data, i) {
                            var ticklerId = String(data.id);
                            if (comments[ticklerId]) {
                                var commentRows = '';
                                for (var c = 0; c < comments[ticklerId].length; c++) {
                                    var cm = comments[ticklerId][c];
                                    commentRows += '<tr class="comment-row">';
                                    commentRows += '<td></td><td></td>';
                                    commentRows += '<td>' + escapeHtml(data.demographicName || '') + '</td>';
                                    commentRows += '<td>' + escapeHtml(cm.providerName || '') + '</td>';
                                    commentRows += '<td>' + escapeHtml(data.serviceDate || '') + '</td>';
                                    commentRows += '<td>' + escapeHtml(cm.updateDate || '') + '</td>';
                                    commentRows += '<td>' + escapeHtml(data.priority || '') + '</td>';
                                    commentRows += '<td></td><td></td>';
                                    commentRows += '<td style="white-space:pre-wrap">' + escapeHtml(cm.message || '') + '</td>';
                                    commentRows += '<td></td>';
                                    commentRows += '</tr>';
                                }
                                jQuery(rows).eq(i).after(commentRows);
                            }
                        });

                        localStorage.setItem('ticklerPageLength', api.page.len());
                    }
                });

                document.getElementById('ticklerview').addEventListener('change', function () {
                    ticklerResultsTable.ajax.reload();
                });

                jQuery("#formSubmitBtn").off('click').on('click', function(e) {
                    e.preventDefault();
                    ticklerResultsTable.ajax.reload();
                });

            });

            /**
             * Opens the tickler edit popup and toggles the row's pencil icon to a
             * checkmark so the user has a visual cue that this tickler has been opened.
             */
            function openTicklerEdit(link, ticklerNo) {
                window.open(ctx + '/tickler/ticklerEdit.jsp?tickler_no=' + ticklerNo, 'edit_tickler', 'width=800, height=650');
                var icon = link.querySelector('span');
                if (icon) {
                    icon.classList.remove('fa-pencil-alt');
                    icon.classList.add('fa-check');
                    icon.style.color = '#198754'; // Bootstrap success green
                }
            }

            function escapeHtml(text) {
                if (!text) return '';
                var div = document.createElement('div');
                div.appendChild(document.createTextNode(text));
                return div.innerHTML;
            }

            function buildAttachmentLink(tableName, tableId) {
                var url = '';
                if (tableName === 'MDS') {
                    url = 'javascript:reportWindow(\'SegmentDisplay.jsp?segmentID=' + tableId + '\')';
                } else if (tableName === 'CML') {
                    url = 'javascript:reportWindow(\'' + ctx + '/lab/CA/ON/CMLDisplay.jsp?segmentID=' + tableId + '\')';
                } else if (tableName === 'HL7') {
                    url = 'javascript:reportWindow(\'' + ctx + '/lab/CA/ALL/labDisplay.jsp?segmentID=' + tableId + '\')';
                } else if (tableName === 'DOC' || tableName === 'document') {
                    url = 'javascript:reportWindow(\'' + ctx + '/documentManager/ManageDocument.do?method=display&doc_no=' + tableId + '\')';
                } else if (tableName === 'HRM') {
                    url = 'javascript:reportWindow(\'' + ctx + '/hospitalReportManager/Display.do?id=' + tableId + '&segmentID=' + tableId + '\')';
                } else {
                    url = 'javascript:reportWindow(\'' + ctx + '/lab/CA/BC/labDisplay.jsp?segmentID=' + tableId + '\')';
                }
                return ' <a title="' + i18nViewAttachment + '" href="' + url + '"><i class="fas fa-paperclip"></i></a>';
            }

            function openNoteDialog(demographicNo, ticklerNo) {

                document.getElementById('tickler_note_demographicNo').value = demographicNo;
                document.getElementById('tickler_note_ticklerNo').value = ticklerNo;
                document.getElementById('tickler_note_noteId').value = '';
                document.getElementById('tickler_note').value = '';
                document.getElementById('tickler_note_revision').innerHTML = '';
                document.getElementById('tickler_note_revision_url').setAttribute('onclick', '');
                document.getElementById('tickler_note_editor').innerHTML = '';
                document.getElementById('tickler_note_obsDate').innerHTML = '';

                jQuery.ajax({
                    method: "POST", url: ctx + '/CaseManagementEntry.do',
                    data: {method: "ticklerGetNote", ticklerNo: document.getElementById('tickler_note_ticklerNo').value},
                    async: false,
                    dataType: 'json',
                    success: function (data) {
                        if (data != null) {
                            document.getElementById('tickler_note_noteId').value = data.noteId;
                            document.getElementById('tickler_note').value = data.note;
                            document.getElementById('tickler_note_revision').textContent = data.revision;
                            document.getElementById('tickler_note_revision_url').setAttribute("onclick", "window.open(" + ctx + "'/CaseManagementEntry.do?method=notehistory&noteId='+data.noteId')')");
                            document.getElementById('tickler_note_editor').textContent = data.editor;
                            document.getElementById('tickler_note_obsDate').textContent = data.obsDate;
                        }
                    },
                    error: function (jqXHR, textStatus, errorThrown) {
                        console.log(errorThrown);
                    }
                });

                jQuery("#note-form").dialog("open");
            }

            function closeNoteDialog() {
                jQuery("#note-form").dialog("close");
            }

            function saveNoteDialog() {
                jQuery.ajax({
                    url: ctx + '/CaseManagementEntry.do',
                    data: {
                        method: "ticklerSaveNote",
                        noteId: document.getElementById('tickler_note_noteId').value,
                        value: document.getElementById('tickler_note').value,
                        demographicNo: document.getElementById('tickler_note_demographicNo').value,
                        ticklerNo: document.getElementById('tickler_note_ticklerNo').value
                    },
                    async: false,
                    success: function (data) {
                        jQuery("#note-form").dialog("close");
                    },
                    error: function (jqXHR, textStatus, errorThrown) {
                        alert(errorThrown);
                    }
                });
            }

            function popupPage(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                var popup = window.open(page, "attachment", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function setfocus() {
                this.focus();
            }

            function allYear() {
                document.serviceform.xml_appointment_date.value = "8888-12-31";
                document.serviceform.xml_vdate.value = "1900-01-01";
            }

            function Check(e) { e.checked = true; }
            function Clear(e) { e.checked = false; }

            function reportWindow(page) {
                windowprops = "height=660, width=960, location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
                var popup = window.open(page, "labreport", windowprops);
                popup.focus();
            }

            function CheckAll() {
                var ml = document.ticklerform;
                var len = ml.elements.length;
                for (var i = 0; i < len; i++) {
                    var e = ml.elements[i];
                    if (e.name === "checkbox") Check(e);
                }
            }

            function ClearAll() {
                var ml = document.ticklerform;
                var len = ml.elements.length;
                for (var i = 0; i < len; i++) {
                    var e = ml.elements[i];
                    if (e.name === "checkbox") Clear(e);
                }
            }

            function saveView() {
                let url = ctx + "/saveWorkView.do";
                let params = {
                    method: 'save',
                    view_name: 'tickler',
                    userrole: '<%= Encode.forJavaScript(userRole) %>',
                    providerno: '<%= Encode.forJavaScript(user_no) %>',
                    ticklerview: document.getElementById('ticklerview').value,
                    providerview: document.getElementById('providerview').value,
                    assignedTo: document.getElementById('assignedTo').value,
                    mrpview: document.getElementById('mrpview').value
                };
                jQuery.post(url, params).done(function () {
                    jQuery("#saveViewButton").addClass('btn-success').removeClass('btn-primary');
                })
                    .fail(function () {
                        jQuery("#saveViewButton").addClass('btn-danger').removeClass('btn-primary');
                    });
            }

            function generateRenalLabReq(demographicNo) {
                var url = ctx + '/form/formlabreq<%=labReqVer%>.jsp?demographic_no=' + demographicNo + '&formId=0&provNo=<%=org.owasp.encoder.Encode.forJavaScript(org.owasp.encoder.Encode.forUriComponent(session.getAttribute("user").toString()))%>&fromSession=true';
                jQuery.ajax({
                    type: 'POST',
                    url: ctx + '/renal/Renal.do',
                    data: {method: 'createLabReq', demographicNo: demographicNo},
                    async: false,
                    success: function (data) {
                        popupPage(900, 850, url);
                    }
                });
            }

            // Listen for tickler refresh broadcasts from edit/add popup
            try {
                var ticklerChannel = new BroadcastChannel('carlos_tickler_refresh');
                ticklerChannel.onmessage = function(event) {
                    var data = event.data;
                    if (data && (data === 'refresh' || data.action === 'refresh')) {
                        if (typeof ticklerResultsTable !== 'undefined' && ticklerResultsTable) {
                            ticklerResultsTable.ajax.reload(null, false);
                        } else {
                            location.reload();
                        }
                    }
                };
            } catch (e) {}

        </script>

    </head>

    <body>
    <div class="container">
        <div class="searchBox">

            <div class="page-header-bar">
                <h4 class="page-header-title">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon bi bi-feather"
                         viewBox="0 0 16 16">
                        <path d="M15.807.531c-.174-.177-.41-.289-.64-.363a3.765 3.765 0 0 0-.833-.15c-.62-.049-1.394 0-2.252.175C10.365.545 8.264 1.415 6.315 3.1c-1.95 1.686-3.168 3.724-3.758 5.423-.294.847-.44 1.634-.429 2.268.005.316.05.62.154.88.017.04.035.082.056.122A68.362 68.362 0 0 0 .08 15.198a.528.528 0 0 0 .157.72.504.504 0 0 0 .705-.16 67.606 67.606 0 0 1 2.158-3.26c.285.141.616.195.958.182.513-.02 1.098-.188 1.723-.49 1.25-.605 2.744-1.787 4.303-3.642l1.518-1.55a.528.528 0 0 0 0-.739l-.729-.744 1.311.209a.504.504 0 0 0 .443-.15c.222-.23.444-.46.663-.684.663-.68 1.292-1.325 1.763-1.892.314-.378.585-.752.754-1.107.163-.345.278-.773.112-1.188a.524.524 0 0 0-.112-.172ZM3.733 11.62C5.385 9.374 7.24 7.215 9.309 5.394l1.21 1.234-1.171 1.196a.526.526 0 0 0-.027.03c-1.5 1.789-2.891 2.867-3.977 3.393-.544.263-.99.378-1.324.39a1.282 1.282 0 0 1-.287-.018Zm6.769-7.22c1.31-1.028 2.7-1.914 4.172-2.6a6.85 6.85 0 0 1-.4.523c-.442.533-1.028 1.134-1.681 1.804l-.51.524-1.581-.25Zm3.346-3.357C9.594 3.147 6.045 6.8 3.149 10.678c.007-.464.121-1.086.37-1.806.533-1.535 1.65-3.415 3.455-4.976 1.807-1.561 3.746-2.36 5.31-2.68a7.97 7.97 0 0 1 1.564-.173Z"/>
                    </svg>
                    &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.managerHeading"/>
                </h4>
            </div>

            <form name="serviceform" method="get" action="ticklerMain.jsp">
                <input type="hidden" name="Submit" value="">
                <input type="hidden" name="demoview" value="<%= Encode.forHtmlAttribute(demoviewParam != null ? demoviewParam : "") %>">

        <h2>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-feather"
                 viewBox="0 0 16 16">
                <path d="M15.807.531c-.174-.177-.41-.289-.64-.363a3.765 3.765 0 0 0-.833-.15c-.62-.049-1.394 0-2.252.175C10.365.545 8.264 1.415 6.315 3.1c-1.95 1.686-3.168 3.724-3.758 5.423-.294.847-.44 1.634-.429 2.268.005.316.05.62.154.88.017.04.035.082.056.122A68.362 68.362 0 0 0 .08 15.198a.528.528 0 0 0 .157.72.504.504 0 0 0 .705-.16 67.606 67.606 0 0 1 2.158-3.26c.285.141.616.195.958.182.513-.02 1.098-.188 1.723-.49 1.25-.605 2.744-1.787 4.303-3.642l1.518-1.55a.528.528 0 0 0 0-.739l-.729-.744 1.311.209a.504.504 0 0 0 .443-.15c.222-.23.444-.46.663-.684.663-.68 1.292-1.325 1.763-1.892.314-.378.585-.752.754-1.107.163-.345.278-.773.112-1.188a.524.524 0 0 0-.112-.172ZM3.733 11.62C5.385 9.374 7.24 7.215 9.309 5.394l1.21 1.234-1.171 1.196a.526.526 0 0 0-.027.03c-1.5 1.789-2.891 2.867-3.977 3.393-.544.263-.99.378-1.324.39a1.282 1.282 0 0 1-.287-.018Zm6.769-7.22c1.31-1.028 2.7-1.914 4.172-2.6a6.85 6.85 0 0 1-.4.523c-.442.533-1.028 1.134-1.681 1.804l-.51.524-1.581-.25Zm3.346-3.357C9.594 3.147 6.045 6.8 3.149 10.678c.007-.464.121-1.086.37-1.806.533-1.535 1.65-3.415 3.455-4.976 1.807-1.561 3.746-2.36 5.31-2.68a7.97 7.97 0 0 1 1.564-.173Z"/>
            </svg>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.title"/>
        </h2>

        <form name="serviceform" method="get" action="ticklerMain.jsp">
            <input type="hidden" name="Submit" value="">
            <input type="hidden" name="demoview" value="<%=org.owasp.encoder.Encode.forHtmlAttribute(hasDemoView ? demographic_no : "")%>">

            <c:if test="${not hasDemoView}">
                <div class="row mb-2">
                    <div class="col-12">
                        <label class="fw-semibold">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formDateRange"/>
                            <a href="javascript:void(0)" id="dateRange" onClick="allYear()">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnViewAll"/>
                            </a>
                        </label>
                    </div>
                </div>
                <div class="row mb-2">
                    <label for="xml_vdate" class="col-sm-3 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formFrom"/></label>
                    <div class="col-sm-9">
                        <input type="date" class="form-control" name="xml_vdate" id="xml_vdate"
                               value="<%=org.owasp.encoder.Encode.forHtmlAttribute(xml_vdate)%>">
                    </div>
                </div>
                <div class="row mb-2">
                    <label for="xml_appointment_date" class="col-sm-3 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formTo"/></label>
                    <div class="col-sm-9">
                        <input type="date" class="form-control" name="xml_appointment_date" id="xml_appointment_date"
                               value="<%=org.owasp.encoder.Encode.forHtmlAttribute(xml_appointment_date)%>">
                    </div>
                </div>
                <div class="row mb-2">
                    <label for="mrpview" class="col-sm-3 col-form-label">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.MRP"/>
                    </label>
                    <div class="col-sm-9">
                        <select id="mrpview" class="form-select" name="mrpview">
                            <option value="all" <%=mrpview.equals("all") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formAllProviders"/></option>
                            <%
                                for (Provider p : providers) {
                            %>
                            <option value="<%=org.owasp.encoder.Encode.forHtmlAttribute(p.getProviderNo())%>" <%=mrpview.equals(p.getProviderNo()) ? "selected" : ""%>><%=org.owasp.encoder.Encode.forHtml(p.getLastName())%>,<%=org.owasp.encoder.Encode.forHtml(p.getFirstName())%></option>
                            <%
                                }
                            %>
                        </select>
                    </div>
                </div>
                <div class="row mb-2">
                    <label for="providerview" class="col-sm-3 col-form-label">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgCreator"/>
                    </label>
                    <div class="col-sm-9">
                        <select id="providerview" class="form-select" name="providerview">
                            <option value="all" <%=providerview.equals("all") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formAllProviders"/></option>
                            <%
                                for (Provider p : providers) {
                            %>
                            <option value="<%=org.owasp.encoder.Encode.forHtmlAttribute(p.getProviderNo())%>" <%=providerview.equals(p.getProviderNo()) ? "selected" : ""%>><%=org.owasp.encoder.Encode.forHtml(p.getLastName())%>,<%=org.owasp.encoder.Encode.forHtml(p.getFirstName())%></option>
                            <%
                                }
                            %>
                        </select>
                    </div>
                </div>
                <div class="row mb-2">
                    <label for="assignedTo" class="col-sm-3 col-form-label">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgAssignedTo"/>
                    </label>
                    <div class="col-sm-9">
                        <%
                            if (io.github.carlos_emr.carlos.commn.IsPropertiesOn.isMultisitesEnable()) {
                                SiteDao siteDao = (SiteDao) SpringUtils.getBean(SiteDao.class);
                                List<Site> sites = siteDao.getActiveSitesByProviderNo(user_no);
                        %>
                        <script>
                            let _providers = {};
                            <%for (int i=0; i<sites.size(); i++) {%>
                            _providers["<%=Encode.forJavaScript(String.valueOf(sites.get(i).getSiteId()))%>"] = "<%Iterator<Provider> iter = sites.get(i).getProviders().iterator();
                            while (iter.hasNext()) {
                                Provider p=iter.next();
                                if ("1".equals(p.getStatus())) {%><option value='<%=Encode.forHtmlAttribute(p.getProviderNo())%>'><%=Encode.forHtml(p.getLastName())%>, <%=Encode.forHtml(p.getFirstName())%></option><%}%>";
                            <%}}%>

                            function changeSite(sel) {
                                sel.form.assignedTo.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                            }
                        </script>
                        <select id="site" class="form-select mb-1" name="site" onchange="changeSite(this)">
                            <option value="none"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.selectClinic"/></option>
                            <%
                                for (int i = 0; i < sites.size(); i++) {
                            %>
                            <option value="<%=org.owasp.encoder.Encode.forHtmlAttribute(sites.get(i).getSiteId().toString())%>" <%=sites.get(i).getSiteId().toString().equals(request.getParameter("site")) ? "selected" : ""%>><%=org.owasp.encoder.Encode.forHtml(sites.get(i).getName())%></option>
                            <%
                                }
                            %>
                        </select>
                        <select id="assignedTo" name="assignedTo" class="form-select"></select>
                        <%
                            if (request.getParameter("assignedTo") != null) {
                        %>
                        <script>
                            changeSite(document.getElementById("site"));
                            document.getElementById("assignedTo").value = '<%=org.owasp.encoder.Encode.forJavaScript(request.getParameter("assignedTo"))%>';
                        </script>
                        <%
                            }
                        } else {
                        %>
                        <select id="assignedTo" class="form-select" name="assignedTo">
                            <%
                                boolean ticklerDefaultAssignedProvier = OscarProperties.getInstance().isPropertyActive("tickler_default_assigned_provider");
                                if (ticklerDefaultAssignedProvier) {
                                    if ("all".equals(assignedTo)) {
                                        assignedTo = user_no;
                                    }
                                }
                            %>
                            <option value="all" <%=assignedTo.equals("all") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formAllProviders"/></option>
                            <%
                                List<Provider> providersActive = providerDao.getActiveProviders();
                                for (Provider p : providersActive) {
                            %>
                            <option value="<%=org.owasp.encoder.Encode.forHtmlAttribute(p.getProviderNo())%>" <%=assignedTo.equals(p.getProviderNo()) ? "selected" : ""%>><%=org.owasp.encoder.Encode.forHtml(p.getLastName())%>, <%=org.owasp.encoder.Encode.forHtml(p.getFirstName())%></option>
                            <%
                                }
                            %>
                        </select>
                        <%
                            }
                        %>
                    </div>
                </div>
                <div class="row mb-2">
                    <label for="ticklerview" class="col-sm-3 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formFilter"/></label>
                    <div class="col-sm-9">
                        <select id="ticklerview" class="form-select" name="ticklerview">
                            <option value="A" <%=ticklerview.equals("A") ? "selected" : ""%>>
                                <fmt:setBundle basename="oscarResources"/>
                                <fmt:message key="tickler.ticklerMain.formActive"/></option>
                            <option value="C" <%=ticklerview.equals("C") ? "selected" : ""%>>
                                <fmt:setBundle basename="oscarResources"/>
                                <fmt:message key="tickler.ticklerMain.formCompleted"/></option>
                            <option value="D" <%=ticklerview.equals("D") ? "selected" : ""%>>
                                <fmt:setBundle basename="oscarResources"/>
                                <fmt:message key="tickler.ticklerMain.formDeleted"/></option>
                        </select>

                        <input type="button" class="btn btn-sm btn-primary noprint" id="formSubmitBtn"
                               value="<fmt:setBundle basename='oscarResources'/><fmt:message key='tickler.ticklerMain.btnCreateReport'/>">
                        <input type="button" class="btn btn-sm btn-primary noprint" id="saveViewButton"
                               value="<fmt:setBundle basename='oscarResources'/><fmt:message key='tickler.ticklerMain.msgSaveView'/>" onclick="saveView();">
                    </div>
                </div>
                <div class="row mb-3">
                    <div class="col-sm-9 offset-sm-3">
                        <input type="button" class="btn btn-primary mbttn noprint" id="formSubmitBtn"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnCreateReport"/>">
                        <input type="button" class="btn btn-secondary ms-2" id="saveViewButton"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgSaveView"/>" onclick="saveView();">
                    </div>
                </div>

            </c:if>
            <c:if test="${hasDemoView}">
                <div class="row mb-3">
                    <label for="ticklerview" class="col-sm-3 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formFilter"/></label>
                    <div class="col-sm-9">
                        <select id="ticklerview" class="form-select" name="ticklerview">
                            <option value="A" <%=ticklerview.equals("A") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formActive"/></option>
                            <option value="C" <%=ticklerview.equals("C") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formCompleted"/></option>
                            <option value="D" <%=ticklerview.equals("D") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.formDeleted"/></option>
                        </select>
                    </div>
                </div>
            </c:if>
        </form>

        <form name="ticklerform" method="post" action="dbTicklerMain.jsp">
            <input type="hidden" name="parentAjaxId" value="<c:out value='${param.parentAjaxId}' />"/>
            <table id="ticklerResults" class="table table-striped table-sm" style="width:100%">
                <thead>
                <tr>
                    <th class="col-checkbox">&nbsp;</th>
                    <th>&nbsp;</th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgDemographicName"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgCreator"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgDate"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgDateofMsg"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.Priority"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.taskAssignedTo"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.status"/></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.msgMessage"/></th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                </tbody>
            </table>

            <table id="tablefoot">

                <tr class="noprint">
                    <td class="white"><a id="checkAllLink" name="checkAllLink"
                                         href="javascript:CheckAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnCheckAll"/></a> - <a href="javascript:ClearAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnClearAll"/></a>

                        <input type="hidden" name="submit_form" value="">
                        <%
                            if (ticklerview.compareTo("D") == 0) {
                        %>
                        <input type="button" class="btn btn-secondary"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnEraseCompletely"/>" class="sbttn"
                               onclick="document.forms['ticklerform'].submit_form.value='Erase Completely'; document.forms['ticklerform'].submit();">
                        <%
                        } else {
                        %>
                        <input type="button" class="btn btn-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnComplete"/>"
                               class="sbttn"
                               onclick="document.forms['ticklerform'].submit_form.value='Complete'; document.forms['ticklerform'].submit();">
                        <input type="button" class="btn btn-danger"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnDelete"/>" class="sbttn"
                               onclick="document.forms['ticklerform'].submit_form.value='Delete'; document.forms['ticklerform'].submit();">
                        <%
                            }
                        %>
                        <input type="button" class="btn btn-primary" name="button"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.btnAddTickler"/>"
                               onClick="popupPage('500','800', 'ticklerAdd.jsp?updateParent=true&parentAjaxId=${parentAjaxId}&bFirstDisp=false&messageID=null&demographic_no=${param.demoview}')"
                               class="sbttn">
                        <input type="button" name="button" class="btn btn-warning"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>" onClick="window.close()" class="sbttn">
                    </td>
                </tr>
            </table>

                <div class="action-separator"></div>

                <div class="action-group">
                    <input type="button" class="btn btn-sm btn-primary"
                           value="<fmt:setBundle basename='oscarResources'/><fmt:message key='tickler.ticklerMain.btnAddTickler'/>"
                           onClick="popupPage('500','800', 'ticklerAdd.jsp?updateParent=true&parentAjaxId=<%= Encode.forUriComponent(parentAjaxId != null ? parentAjaxId : "") %>&bFirstDisp=false&messageID=null&demographic_no=<%= Encode.forUriComponent(demoviewParam != null ? demoviewParam : "") %>')">
                    <input type="button" class="btn btn-sm btn-secondary"
                           value="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.btnBack'/>" onClick="try{var bc=new BroadcastChannel('carlos_tickler_refresh');bc.postMessage({action:'refresh'});bc.close();}catch(e){}window.close()">
                </div>
            </div>
        </form>

        <p class="yesprint">
            <%=OscarProperties.getConfidentialityStatement()%>
        </p>

        <div id="note-form" title="<fmt:message key='tickler.ticklerMain.noteDialogTitle'/>" style="display:none;">
            <form>
                <input type="hidden" name="tickler_note_demographicNo" id="tickler_note_demographicNo" value=""/>
                <input type="hidden" name="tickler_note_ticklerNo" id="tickler_note_ticklerNo" value=""/>
                <input type="hidden" name="tickler_note_noteId" id="tickler_note_noteId" value=""/>

                <table style="width:100%;">
                    <tr>
                        <td>
                            <label for="tickler_note"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.noteLabel"/></label>
                            <textarea class="form-control" id="tickler_note" rows="5" name="tickler_note"
                                      style="width:100%;"
                                      oninput='this.style.height = "";this.style.height = this.scrollHeight + "px"'
                                      onfocus='this.style.height = "";this.style.height = this.scrollHeight + "px"'></textarea>
                        </td>
                    </tr>
                    <tr>
                        <td nowrap="nowrap">
                            <label for="tickler_note_obsDate"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.noteDate"/></label>
                            <span id="tickler_note_obsDate"></span>

                            <label for="tickler_note_revision_url"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.noteRev"/></label>
                            <a id="tickler_note_revision_url" href="javascript:void(0)" onClick="">
                                <span id="tickler_note_revision"></span>
                            </a>

                            <label for="tickler_note_editor"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.noteEditor"/></label>
                            <span id="tickler_note_editor"></span>
                        </td>
                    </tr>

                </table>
                <div class="float-end">
                    <button class="btn btn-primary" onclick="saveNoteDialog()"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.save"/></button>
                    <button class="btn btn-danger" onclick="closeNoteDialog()"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/></button>
                </div>
            </form>
        </div>

    </div>
    </body>
</html>
