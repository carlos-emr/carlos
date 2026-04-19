<%--
    Redirect stub — the add patient form is now behind WEB-INF/jsp/demographic/DemographicAdd
    served by DemographicAdd2Action. This stub preserves the old URL for all callers.
--%>
<%
    StringBuilder url = new StringBuilder(request.getContextPath())
            .append("/demographic/DemographicAdd");
    String qs = request.getQueryString();
    if (qs != null && !qs.isEmpty()) {
        url.append("?").append(qs);
    }
    response.sendRedirect(url.toString());
%>
