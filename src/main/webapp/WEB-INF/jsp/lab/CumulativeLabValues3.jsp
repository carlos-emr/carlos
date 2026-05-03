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

<%-- ========== PAGE IMPORTS ========== --%>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.*" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.util.CumulativeLabValuesComparator" %>
<%@ page import="org.jdom2.*" %>
<%@ page import="io.github.carlos_emr.carlos.db.*" %>
<%@ page import="org.jdom2.input.*" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%-- ========== TAGLIB DECLARATIONS ========== --%>
<%@ taglib uri="jakarta.tags.core"      prefix="c"   %>
<%@ taglib uri="jakarta.tags.fmt"       prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn"  %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld"  prefix="oscar"    %>
<%@ taglib uri="/WEB-INF/security.tld"   prefix="security" %>

<%-- ========== SECURITY CHECK ========== --%>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false;%>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%-- ========== BUSINESS LOGIC ========== --%>
<%
    String demographic_no = request.getParameter("demographic_no");

    LinkedHashMap<String, String> nameMap = new LinkedHashMap<>();
    List<String> idList = new ArrayList<>();
    Map<String, LinkedHashMap<String, Hashtable>> measIdMap = new HashMap<>();
    List<Hashtable<String, String>> dateList = new ArrayList<>();

    try {
        InputStream is = application.getResource("/WEB-INF/measurements.xml").openStream();
        SAXBuilder saxParser = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureSAXBuilder();
        Document doc = saxParser.build(is);
        is.close();

        Element root = doc.getRootElement();
        List items = root.getChildren();

        for (int i = 0; i < items.size(); i++) {
            Element elem = (Element) items.get(i);
            String loinc_code = elem.getAttributeValue("loinc_code");
            String name       = elem.getAttributeValue("name");

            if (!loinc_code.equalsIgnoreCase("NULL")) {
                LinkedHashMap<String, Hashtable> idMap = new LinkedHashMap<>();
                ArrayList labList = CommonLabTestValues.findValuesByLoinc(demographic_no, loinc_code);
                for (int j = 0; j < labList.size(); j++) {
                    Hashtable h  = (Hashtable) labList.get(j);
                    String date  = (String) h.get("date");
                    String id    = (String) h.get("lab_no");
                    idMap.put(id, h);
                    if (!idList.contains(id)) {
                        idList.add(id);
                        Hashtable<String, String> dateIdHash = new Hashtable<>();
                        dateIdHash.put("date", date);
                        dateIdHash.put("id",   id);
                        dateList.add(dateIdHash);
                    }
                }
                if (!labList.isEmpty()) {
                    measIdMap.put(loinc_code, idMap);
                    nameMap.put(loinc_code, name);
                }
            } else if (nameMap.isEmpty() && !name.equals("NULL")) {
                nameMap.put("NULL" + i, name);
            } else if (!nameMap.isEmpty()) {
                String[] nameMapKeys = nameMap.keySet().toArray(new String[0]);
                String lastKey = nameMapKeys[nameMapKeys.length - 1];
                if (lastKey.startsWith("NULL") && (name.equalsIgnoreCase("NULL") || !nameMap.get(lastKey).equalsIgnoreCase("NULL"))) {
                    nameMap.remove(lastKey);
                    if (nameMapKeys.length > 1) {
                        lastKey = nameMapKeys[nameMapKeys.length - 2];
                        if (nameMap.get(lastKey) != null && nameMap.get(lastKey).equalsIgnoreCase("NULL") && name.equalsIgnoreCase("NULL")) {
                            nameMap.remove(lastKey);
                        }
                    }
                }
                nameMap.put("NULL" + i, name);
            }
        }

        if (!nameMap.isEmpty()) {
            String[] nameMapKeys = nameMap.keySet().toArray(new String[0]);
            if (nameMapKeys[nameMapKeys.length - 1].startsWith("NULL")) {
                nameMap.remove(nameMapKeys[nameMapKeys.length - 1]);
            }
        }

    } catch (Exception ex) {
        MiscUtils.getLogger().error("Error loading cumulative lab values", ex);
    }

    CumulativeLabValuesComparator comp = new CumulativeLabValuesComparator();
    Collections.sort(dateList, comp);

    // Pre-format column header dates once so the view stays scriptlet-free
    for (Hashtable<String, String> dateEntry : dateList) {
        String rawDate   = dateEntry.get("date");
        Date   labDate   = UtilDateUtilities.StringToDate(rawDate, "yyyy-MM-dd HH:mm:ss");
        String formatted = (labDate != null) ? UtilDateUtilities.DateToString(labDate, "dd MMM yy") : rawDate;
        dateEntry.put("formattedDate", formatted);
    }

    // Build a flat, view-friendly row list: one map per data or section-header row
    List<Map<String, Object>> tableRows = new ArrayList<>();
    for (Map.Entry<String, String> entry : nameMap.entrySet()) {
        String loincCode = entry.getKey();
        String testName  = entry.getValue();
        Map<String, Object> row = new HashMap<>();
        row.put("loincCode",  loincCode);
        row.put("testName",   testName);
        row.put("isSection",  loincCode.startsWith("NULL"));

        if (!loincCode.startsWith("NULL")) {
            LinkedHashMap<String, Hashtable> idMap = measIdMap.get(loincCode);
            String latestVal  = "";
            String latestDate = "";
            String latestAbn  = "N";
            if (idMap != null && !idMap.isEmpty()) {
                Hashtable ht = idMap.get(idMap.keySet().iterator().next());
                latestVal  = StringUtils.noNull((String) ht.get("result"));
                latestDate = StringUtils.noNull((String) ht.get("date"));
                latestAbn  = StringUtils.noNull((String) ht.get("abn"));
                if (latestDate.length() >= 10) {
                    latestDate = latestDate.substring(0, 10);
                }
            }
            row.put("latestVal",  StringUtils.maxLenString(latestVal, 9, 8, "..."));
            row.put("latestDate", latestDate);
            row.put("latestAbn",  latestAbn);

            // One cell map per dated column, in sorted date order
            List<Map<String, String>> cells = new ArrayList<>();
            for (Hashtable<String, String> dateEntry : dateList) {
                String labNo   = dateEntry.get("id");
                String cellVal = "";
                String cellAbn = "N";
                if (idMap != null) {
                    Hashtable ht = idMap.get(labNo);
                    if (ht != null) {
                        cellVal = StringUtils.noNull((String) ht.get("result"));
                        cellAbn = StringUtils.noNull((String) ht.get("abn"));
                    }
                }
                Map<String, String> cell = new HashMap<>();
                cell.put("val", StringUtils.maxLenString(cellVal, 9, 8, "..."));
                cell.put("abn", cellAbn);
                cells.add(cell);
            }
            row.put("cells", cells);
        }
        tableRows.add(row);
    }

    pageContext.setAttribute("demographicNo", demographic_no);
    pageContext.setAttribute("dateList",      dateList);
    pageContext.setAttribute("tableRows",     tableRows);
    pageContext.setAttribute("ctx",           request.getContextPath());
    pageContext.setAttribute("providerNo",    session.getAttribute("user"));
%>

<%-- ========== BUNDLE (single, pre-DOCTYPE) ========== --%>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="lab.cumulativeLab3.pageTitle"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>

    <style>
        body { padding: 0.75rem; }
        /* Prevent column text from wrapping — lab values are compact */
        #cumulativeLabTable th,
        #cumulativeLabTable td { white-space: nowrap; font-size: 0.8rem; }
        /* Section-header rows (e.g. "Hematology") */
        .lab-section-header > td {
            font-weight: 600;
            background-color: #e8eaf6;
            padding: 0.2rem 0.4rem;
        }
        /* Abnormal value highlighting */
        td.abn-Y, td.abn-H { color: #d32f2f; font-weight: 600; }
        td.abn-A, td.abn-L { color: #e65100; font-weight: 600; }
        .dataTables_wrapper .dataTables_filter,
        .dataTables_wrapper .dataTables_info,
        .dataTables_wrapper .dataTables_paginate { font-size: 0.8rem; }
    </style>
</head>
<body>

<div class="d-flex align-items-center justify-content-between mb-2 pb-2 border-bottom">
    <span class="fw-semibold small">
        <oscar:nameage demographicNo="${e:forHtmlAttribute(demographicNo)}"/>
    </span>
    <span class="small text-muted">
        <a href="javascript:popupStart(300,400,'About.jsp')"><fmt:message key="global.about"/></a>
        &nbsp;|&nbsp;
        <a href="javascript:popupStart(300,400,'License.jsp')"><fmt:message key="global.license"/></a>
    </span>
</div>

<div class="table-responsive">
    <table id="cumulativeLabTable" class="table table-sm table-bordered table-hover">
        <thead class="table-light">
            <tr>
                <th><fmt:message key="lab.cumulativeLab3.colTest"/></th>
                <th><fmt:message key="lab.cumulativeLab3.colLatestValue"/></th>
                <th><fmt:message key="lab.cumulativeLab3.colLastDone"/></th>
                <c:forEach items="${dateList}" var="dateEntry">
                    <th>
                        <a href="#"
                           data-seg-id="${e:forHtmlAttribute(dateEntry['id'])}"
                           data-provider-no="${e:forHtmlAttribute(providerNo)}"
                           onclick="reportWindow('${e:forJavaScriptAttribute(ctx)}/lab/CA/ALL/ViewLabDisplay?segmentID=' + this.dataset.segId + '&amp;providerNo=' + this.dataset.providerNo); return false;">
                            ${e:forHtml(dateEntry['formattedDate'])}
                        </a>
                    </th>
                </c:forEach>
            </tr>
        </thead>
        <tbody>
            <c:forEach items="${tableRows}" var="row">
                <c:choose>
                    <c:when test="${row['isSection']}">
                        <c:choose>
                            <c:when test="${row['testName'] eq 'NULL'}">
                                <tr class="lab-section-header">
                                    <td colspan="${3 + fn:length(dateList)}">&nbsp;</td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <tr class="lab-section-header">
                                    <td colspan="${3 + fn:length(dateList)}">${e:forHtml(row['testName'])}</td>
                                </tr>
                            </c:otherwise>
                        </c:choose>
                    </c:when>
                    <c:otherwise>
                        <tr>
                            <td>${e:forHtml(row['testName'])}</td>
                            <td class="abn-${e:forHtmlAttribute(row['latestAbn'])}">${e:forHtml(row['latestVal'])}</td>
                            <td>${e:forHtml(row['latestDate'])}</td>
                            <c:forEach items="${row['cells']}" var="cell">
                                <td class="abn-${e:forHtmlAttribute(cell['abn'])}">${e:forHtml(cell['val'])}</td>
                            </c:forEach>
                        </tr>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </tbody>
    </table>
</div>

<script>

    function reportWindow(page) {
        var props = 'height=660,width=960,location=no,scrollbars=yes,menubar=no,toolbar=no,resizable=yes,top=0,left=0';
        var popup = window.open(page, 'labreport', props);
        if (popup) { popup.focus(); }
    }
</script>

</body>
</html>
