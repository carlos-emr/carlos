#!/bin/bash
cat << 'PATCH' > fix_infirmarydemographiclist.diff
--- src/main/webapp/WEB-INF/jsp/provider/infirmarydemographiclist.jspf
+++ src/main/webapp/WEB-INF/jsp/provider/infirmarydemographiclist.jspf
@@ -65,8 +65,9 @@
					<%
						k++;
						java.util.Date apptime = new java.util.Date();
-						int demographic_no = Integer.parseInt(de.value);
-						String demographic_name = de.label;
+						io.github.carlos_emr.carlos.commn.model.utils.DropdownExt de = (io.github.carlos_emr.carlos.commn.model.utils.DropdownExt) pageContext.getAttribute("de");
+						int demographic_no = Integer.parseInt(de.getValue());
+						String demographic_name = de.getLabel();
						String tickler_no = "";
						String tickler_note = "";
						// Using your ticklerManager logic in JSP to retrieve ticklers.
@@ -102,9 +103,9 @@
						<!-- Conditionally add encounter link based on bShowEncounterLink -->
						<c:if test="${bShowEncounterLink}">
							<%
-								String eURL = base_eURL + "&appointmentNo=" + rsAppointNO + "&demographicNo=" + demographic_no + "&reason=" + URLEncoder.encode(reason) + "&startTime=" + apptime.getHours() + ":" + apptime.getMinutes() + "&status=" + status;
+								String eURL = base_eURL + "&appointmentNo=0&demographicNo=" + demographic_no + "&reason=&startTime=" + apptime.getHours() + ":" + apptime.getMinutes() + "&status=";
							%>
-							<a href="#" onClick="popupWithApptNo(710, 1024, '${eURL}', 'encounter'); return false;" title="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.encounter'/>">
+							<a href="#" onClick="popupWithApptNo(710, 1024, '<%= eURL %>', 'encounter'); return false;" title="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.encounter'/>">
								|<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnE"/>
							</a>
						</c:if>
PATCH
patch -p0 < fix_infirmarydemographiclist.diff
rm fix_infirmarydemographiclist.diff
