<!--
Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
-->
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>

<c:if test="${page eq 1}">
<div id="inboxViewItems" class="inbox-view-items pe-1">
</c:if>
    <c:if test="${page ge 1}">
    <c:forEach var="labResult" items="${labDocs}" varStatus="loopStatus">
        <div class="document-card card mb-1 shadow-sm" id="labdoc_${labResult.segmentID}" style="height: 100%;">
            <div class="card-body">
                <div class="card-title fw-bold">
                    <c:out value="${labResult.labType}: " />
                    <e:forHtmlContent value='${labResult.patientName}' />
                </div>
                <iframe 
                    src="${e:forHtml(labLinks[loopStatus.index])}"
                    width="100%" 
                    height="100%"
                    style="padding-bottom: 25px;"
                    loading="lazy"
                    title="Lab Result Document">
                    <p>Your browser does not support iframes. Unable to display the document.</p>
                </iframe>
            </div>
        </div>
    </c:forEach>
    </c:if>
<c:if test="${page eq 1}">
</div>
<script>
    jQuery(document).ready(function() {
        // Scroll event handler to fetch more data when reaching the bottom
        jQuery("#inboxViewItems").on('scroll', function() {
            if (jQuery("#inboxViewItems").scrollTop() + jQuery("#inboxViewItems").innerHeight() >= jQuery("#inboxViewItems")[0].scrollHeight - 10) {
                if (!isFetchingData) {
                    fetchInboxhubViewData();
                }
            }
        });
    });
</script>
</c:if>
<c:if test="${!hasMoreData}">
<script>
    hasMoreData = false;
</script>
</c:if>