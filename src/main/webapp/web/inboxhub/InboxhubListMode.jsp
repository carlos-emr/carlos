<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.lab.ca.on.*" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@page import="ca.openosp.openo.utility.MiscUtils,org.apache.commons.text.StringEscapeUtils" %>
<%@page import="org.apache.logging.log4j.Logger,ca.openosp.openo.commn.dao.OscarLogDao,ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.inboxhub.query.InboxhubQuery" %>
<%@ page import="ca.openosp.openo.mds.data.CategoryData" %>

<fmt:setBundle basename="oscarResources"/>

<c:if test="${page eq 1}">
<div class="bg-light text-light">
    <row>
        <input id="topFBtn" type="button" class="btn btn-primary btn-sm ms-1" value="<fmt:message key="oscarMDS.index.btnForward"/>" onclick="submitForward('${sessionScope.user}')">
        <input id="topFileBtn" type="button" class="btn btn-primary btn-sm" value="File" onclick="submitFile('${sessionScope.user}')"/>
        <div class="d-flex align-items-center position-relative float-end">
            <span class="d-inline me-2 py-1 text-dark fw-bold">Documents <span id="totalDocsCountStat" class="badge" style="background-color: #5a9bd3; color: white;">0</span></span>
            <span class="d-inline me-2 py-1 text-dark fw-bold">Labs <span id="totalLabssCountStat" class="badge" style="background-color: #8cbfda; color: white;">0</span></span>
            <c:if test="${!OscarProperties.getInstance().isBritishColumbiaBillingRegion()}">
                <span class="d-inline py-1 text-dark fw-bold">HRMs <span id="totalHRMsCountStat" class="badge" style="background-color: #b3d9eb; color: black;">0</span></span>
            </c:if>
            <div id="loadingLabel" class="loading-label ms-2 me-2 text-dark">Loading Search Results:</div>
            <div class="progress me-2 position-relative" id="loadInboxListProgress" style="width: 15vw; height: 25px; display: none; flex-grow: 1; background-color: #c7c7c7;">
                <div id="loadInboxListProgressBar" class="progress-bar" role="progressbar" style="width: 0%;" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
                    <span id="inboxListProgressCount" class="count text-white" style="position: absolute; width: 100%; text-align: center; font-weight: 600;">0% Complete</span>
                </div>
            </div>
            <button id="stopLoadingInboxList" onclick="stopInboxhubListProgress(0)" class="btn btn-sm btn-danger" style="display: none;">Stop</button>
        </div>
    </row>
    <row>
        <div class="inbox-table-responsive">
        <table table id="inbox_table" class="table table-striped inbox-table">
            <thead class="inbox-table-sticky-header">
            <tr>
                <th>
                    <input type="checkbox" onclick="checkAllLabs(0);" name="checkA"/>
                </th>
                <th><fmt:message key="oscarMDS.index.msgPatientName"/></th>
                <th><fmt:message key="oscarMDS.index.msgSex"/></th>
                <th><fmt:message key="oscarMDS.index.msgResultStatus"/></th>
                <th><fmt:message key="oscarMDS.index.msgLabel"/></th>
                <th><fmt:message key="oscarMDS.index.msgDateTest"/></th>
                <th><fmt:message key="oscarMDS.index.msgRequestingClient"/></th>
                <th><fmt:message key="oscarMDS.index.msgDiscipline"/></th>
                <th><fmt:message key="oscarMDS.index.msgReportStatus"/></th>
                <th>Ack #</th>
            </tr>
            </thead>
            <tbody id="inboxhubListModeTableBody">
</c:if>
            <c:if test="${page ge 1}">
            <c:forEach var="labResult" items="${labDocs}" varStatus="loopStatus">
                <tr id="labdoc_${labResult.segmentID}" class="${!labResult.isMatchedToPatient() ? 'table-warning' : (labResult.resultStatus == 'A' ? 'table-danger' : '')}">
                    <td>
                        <c:set var="disabled" value="${!labResult.matchedToPatient && labResult.labType != 'DOC' ? 'disabled' : ''}"/>
                        <input type="checkbox" name="flaggedLabs" value="${labResult.segmentID}:${labResult.labType}" ${disabled}>
                    </td>
                    <td>
                        <c:set var="labRead" value="${labResult.hasRead(sessionScope.user) ? '' : '*'}"/>
                        <a href="javascript:void(0);" 
                        onclick="reportWindow('${e:forJavaScript(labLinks[loopStatus.index])}', window.innerHeight, window.innerWidth); return false;">
                            <e:forHtmlContent value='${labRead}${labResult.patientName}' />
                        </a>
                    </td>
                    <td><c:out value="${labResult.sex}" /></td>
                    <td><c:out value="${labResult.resultStatus == 'A' ? 'Abnormal' : ''}" /></td>
                    <td><c:out value="${labResult.label}" /></td>
                    <td><c:out value="${labResult.dateTime}" /><c:out value="${labResult.document ? ' / ' : ''}" /><c:out value="${labResult.document ?  labResult.lastUpdateDate : ''}"/></td>
                    <td><c:out value="${labResult.requestingClient}" /></td>
                    <td><c:out value="${labResult.document ? (labResult.description == null ? '' : labResult.description) : labResult.disciplineDisplayString}" /></td>
                    <td><c:out value="${labResult.reportStatus}" /></td>
                    <td>
                        <c:set var="multiLabCount" value="${labResult.multipleAckCount}" />
                        <c:out value="${labResult.ackCount}" />&nbsp;
                        <c:if test="${multiLabCount >= 0}">
                            (<c:out value="${labResult.multipleAckCount}" />)
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </c:if>
<c:if test="${page eq 1}">
            </tbody>
        </table>
        </div>
    </row>
</div>
<script>
    ctx = "<e:forJavaScript value='${pageContext.request.contextPath}' />";

    jQuery('#inbox_table').DataTable({
        autoWidth: false,
        searching: false,
        scrollCollapse: true,
        paging: false,
        columnDefs: [
            {type: 'non-empty-string', targets: [1, 2, 3, 4, 6, 7, 8, 9]},
            {type: 'custom-date', targets: 5},
            {className: 'text-nowrap', targets: [1, 5, 6, 7, 8]},
            {width: '100px', targets: [3, 4]},
            {width: '10px', targets: [2, 9]},
            {orderable: false, targets: 0}
        ],
        order: [[5, 'desc']],
    });

    //Opens a popup window to a given inbox item.
    function reportWindow(page, height, width) {
        if (height && width) {
            windowprops = "height=" + height + ", width=" + width + ", location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
        } else {
            windowprops = "height=660, width=960, location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
        }
        var popup = window.open(encodeURI(page), "labreport", windowprops);
        popup.focus();
    }

    // Function to remove only <a> tags while keeping their inner text
    function removeATags(str) {
        let parser = new DOMParser();
        let doc = parser.parseFromString(str, 'text/html');

        // Extract the inner text of the <a> element
        return doc.body.textContent.trim();
    }

    // Function to normalize strings: remove <a> tags, special characters, trim spaces, and convert to lowercase
    function normalizeString(str) {
        // Remove <a> tags and keep inner text if present
        str = removeATags(str);

        // Normalize the string: remove special characters, convert to lowercase, and trim
        return str.replace(/[^a-zA-Z0-9]/g, '').toLowerCase().trim();
    }

    //this code assumes the following:
    //in this scenario (2023-09-21 / 2023-09-21), use the 10 characters following the /
    //in any other scenario, just use the original string
    function stringToDate(str) {
        return new Date(str.includes("/") ? str.split(" / ")[0] : str);
    }
    
    // Data table custom sorting to move empty or null slots on any selected sort to the bottom.
    jQuery.extend(jQuery.fn.dataTableExt.oSort, {
        "non-empty-string-asc": function (str1, str2) {
            // Handle empty values: empty values go to the end
            if (!str1.trim()) return 1; // 'str1' is empty, move it to the end
            if (!str2.trim()) return -1; // 'str2' is empty, move it to the end

            // Normalize names: trim spaces, remove special characters, convert to lowercase
            let nameA = normalizeString(str1);
            let nameB = normalizeString(str2);

            // Compare normalized values
            if (nameA < nameB) return -1;
            if (nameA > nameB) return 1;

            // If normalized values are equal, retain original order
            return 0;
        },
        "non-empty-string-desc": function (str1, str2) {
            // Handle empty values: empty values go to the end
            if (!str1.trim()) return 1; // 'str1' is empty, move it to the end
            if (!str2.trim()) return -1; // 'str2' is empty, move it to the end

            // Normalize names: trim spaces, remove special characters, convert to lowercase
            let nameA = normalizeString(str1);
            let nameB = normalizeString(str2);
            console.log(str1)

            // Compare normalized values
            if (nameA < nameB) return 1;
            if (nameA > nameB) return -1;

            // If normalized values are equal, retain original order
            return 0;
        },
        "custom-date-asc": function (str1, str2) {
            // Handle empty strings for dates: empty strings go to the end
            if (!str1.trim()) return 1; // 'str1' is empty, move it to the end
            if (!str2.trim()) return -1; // 'str2' is empty, move it to the end

            //this code assumes the following:
            //in this scenario (2023-09-21 / 2023-09-21), use the 10 characters following the /
            //in any other scenario, just use the original string
            let dateA = stringToDate(str1);
            let dateB = stringToDate(str2);

            return dateA - dateB;
        },
        "custom-date-desc": function (str1, str2) {
            // Handle empty strings for dates: empty strings go to the end
            if (!str1.trim()) return 1; // 'str1' is empty, move it to the end
            if (!str2.trim()) return -1; // 'str2' is empty, move it to the end

            //this code assumes the following:
            //in this scenario (2023-09-21 / 2023-09-21), use the 10 characters following the /
            //in any other scenario, just use the original string
            let dateA = stringToDate(str1);
            let dateB = stringToDate(str2);

            return dateB - dateA; // Reverse order for descending
        }
    });

    function removeReport(reportId) {
        const el = jQuery("#labdoc_" + reportId);
        if (el != null) {
            el.remove();
        }
    }
</script>
</c:if>
<c:if test="${!hasMoreData}">
<script>
    hasMoreData = false;
</script>
</c:if>