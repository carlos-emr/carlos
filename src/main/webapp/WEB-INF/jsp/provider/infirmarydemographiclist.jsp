<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Tickler" %>
<%@ page import="io.github.carlos_emr.*,io.github.carlos_emr.carlos.utility.*,io.github.carlos_emr.carlos.util.*,java.net.URLEncoder"%>
<fmt:setBundle basename="oscarResources"/>
<c:if test="${infirmaryView_isOscar == 'false'}">
<%
	LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoFromSession(request);
	session.setAttribute("case_program_id", session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID));
	java.util.Date todayDate=new java.util.Date();
	todayDate.setHours(23);
	todayDate.setMinutes(59);
	todayDate.setSeconds(59);
	Boolean userAvail = Boolean.valueOf(request.getParameter("userAvail"));
	TicklerManager ticklerManager= SpringUtils.getBean(TicklerManager.class);
	String strDate = request.getParameter("strDate");
	SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
	
	if ((session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID) == null || "0".equals(session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID)))) {%>
		<p><b>No Assigned Program.</b></p>
	<%}else	if (session.getAttribute("infirmaryView_date")!=null && todayDate.before((java.util.Date) session.getAttribute("infirmaryView_date"))) { %>
		<p><b>Future clients list is unavailable.</b></p>
	<%} else {
		if ((session.getAttribute("infirmaryView_demographicBeans") == null || ((java.util.List<?>) session.getAttribute("infirmaryView_demographicBeans")).isEmpty())) { %>
			<p><b>no client in this program.</b></p>
		<%if(session.getAttribute("archiveView")==null || "false".equals(String.valueOf(session.getAttribute("archiveView")))) { %>
			<b>You are in Case Management View! </b>
			&nbsp; <a href="<%=request.getContextPath() %>/ArchiveView">Click here for Archive View</a>
		<%} else {%>
			<b>You are in the archive view ! </b>&nbsp; <a href="<%=request.getContextPath() %>/ArchiveView?method=cmm">Back to Case Management View</a>
		<%} %>
	 <%}else{
	%>
	<%-- Layout tables (a banner and a one-column client list), so they carry role="presentation"
	     instead of header cells; the spacer cells no longer hold empty, HTML5-obsolete <font> tags. --%>
	<table role="presentation" border="1" cellpadding="0"
		bgcolor="<%=userAvail?"#486ebd":"silver"%>" cellspacing="0"
		width="100%">
		<tr>
			<td>
			<%if(session.getAttribute("archiveView")==null || "false".equals(String.valueOf(session.getAttribute("archiveView")))) { %>
			<b>You are in Case Management View! </b> &nbsp; <a href="<%=request.getContextPath() %>/ArchiveView">Click here for Archive View</a>
			<%} else {%> <b>You are in Archive View ! </b>&nbsp; <a
				href="<%=request.getContextPath() %>/ArchiveView?method=cmm">Back
			to Case Management View</a> <%} %>
			</td>
		</tr>
		<tr>
			<td width="1"></td>
		</tr>
	</table>

	<table role="presentation" border="1" cellpadding="0"
		bgcolor="<%=userAvail?"#486ebd":"silver"%>" cellspacing="0"
		width="100%">
		<c:forEach var="de" varStatus="row" items="${infirmaryView_demographicBeans}">
            <c:set var="demographic_no" value="${de.value}"/>
			<tr>
				<td width="1"></td>

				<%
					int demographic_no = Integer.parseInt(String.valueOf(pageContext.getAttribute("demographic_no")));
					
					String tickler_no = "";
					String tickler_note = "";
					// Match the schedule's per-patient tickler privilege check.
                    if (securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", demographic_no)) {
					for (Tickler t : ticklerManager.search_tickler(loggedInInfo, demographic_no, MyDateFormat.getSysDate(strDate))) {
						tickler_no = t.getId().toString();
						tickler_note = t.getMessage() == null ? tickler_note : tickler_note + "\n" + t.getMessage();
					}
                    }
                    pageContext.setAttribute("tickler_no", tickler_no);
                    pageContext.setAttribute("tickler_note", tickler_note);
                    pageContext.setAttribute("bShowEncounterLink", securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", demographic_no));
				%>

				<c:set var="bgColor" value="${row.count % 2 == 0 ? '#FDFEC7' : '#FFBBFF'}"/>
				<td bgcolor="${bgColor}" rowspan="1" nowrap>
					<img src="<%= request.getContextPath() %>/images/todo.gif" border="0" height="10" title="appointment">

					<!-- Handling tickler logic with JSP logic embedded in JSTL -->
					<c:if test="${demographic_no == 0}">
						<c:choose>
							<c:when test="${not empty tickler_no}">
									<a href="#" onClick="popupPage(700, 1000, '<%= request.getContextPath() %>/tickler/ViewTicklerDemoMain?demoview=0'); return false;" title="${carlos:forHtmlAttribute(tickler_note)}">
										<span style="color: red;">!</span>
									</a>
							</c:when>
							<c:otherwise>
								<b>${carlos:forHtml(de.label)}</b>
							</c:otherwise>
						</c:choose>
					</c:if>

					<!-- Display demographic link -->
					<a href="${pageContext.request.contextPath}/PMmodule/ClientManager?id=${carlos:forUriComponent(demographic_no)}">
						${carlos:forHtml(de.label)}
					</a>

					<!-- Conditionally add encounter link based on bShowEncounterLink -->
					<c:if test="${bShowEncounterLink}">
                        <%-- A program client is not an appointment. Build the real patient
                             encounter route without invented appointment fields. --%>
                        <c:url var="eURL" value="/encounter/IncomingEncounter">
                            <c:param name="demographicNo" value="${demographic_no}"/>
                            <c:param name="providerNo" value="${sessionScope.user}"/>
                        </c:url>
						<a href="#" onClick="popupWithApptNo(710, 1024, '${carlos:forJavaScriptAttribute(eURL)}', 'encounter'); return false;" title="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.encounter'/>">
							|<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnE"/>
						</a>
					</c:if>
				</td>
			</tr>
		</c:forEach>

	</table>
	<%
	}
}%>
</c:if>
