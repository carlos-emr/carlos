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
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver" %><%@ page import="io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%
    // CopyFavorites2Action authorizes the source and prepares this model before rendering.
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    String providerNo = (String) request.getAttribute("providerNo");
    boolean share = Boolean.TRUE.equals(request.getAttribute("shareFavorites"));
    List<String> allProviders = (List<String>) request.getAttribute("sharedProviders");
    if (allProviders == null) allProviders = Collections.emptyList();
    String copyProviderNo = (String) request.getAttribute("copyProviderNo");
    if (copyProviderNo == null) copyProviderNo = "";
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath()%>/js/global.js"></script>
        <title><fmt:message key="SearchDrug.title.CopyFavorites"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        
<%-- Rx state is per patient (#3875): expose this request's bean where the page's EL expects it. --%>
<%-- No bean for the request's patient (none named and none open, a patient whose Rx is not open,
     or a malformed/conflicting demographicNo): redirect and stop here, before any scriptlet below
     dereferences the bean (#3908). --%>
<% { RxSessionBean rxResolvedBean = RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r"); if (rxResolvedBean != null) { pageContext.setAttribute("RxSessionBean", rxResolvedBean); } else { response.sendRedirect("error.html"); return; } } %>
        <c:choose>
            <c:when test="${empty RxSessionBean}">
                <c:redirect url="error.html"/>
            </c:when>
            <c:otherwise>
                <c:set var="bean" value="${RxSessionBean}" scope="page"/>
                <c:if test="${not bean.valid}">
                    <c:redirect url="error.html"/>
                </c:if>
            </c:otherwise>
        </c:choose>

        <c:set var="bean" value="${RxSessionBean}" scope="page"/>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/rx/styles.css">
    </head>

    <script language="javascript">
        function update() {
            document.getElementsByName("dispatch")[0].value = 'update';
        }
        function copy() {
            document.getElementsByName("dispatch")[0].value = 'copy';
        }
    </script>

    <body topmargin="0" leftmargin="0" vlink="#0000FF">
        <form action="<%= request.getContextPath()%>/rx/copyFavorite2" method="post">
        <input type="hidden" name="demographicNo" value="${bean.demographicNo}"/>
            <input type="hidden" name="dispatch" value="refresh"/>
            <%-- The copy action takes the source from ddl_provider and the target from the session;
                 it walks countFavorites rows and copies only the ticked ones, by id (#3908). --%>
            <input type="hidden" name="countFavorites" value="${fn:length(copyFavorites)}"/>

            <table border="0" cellpadding="0" cellspacing="0" style="border-collapse: collapse" width="100%">
                <%@ include file="TopLinks.jsp"%>
                
                <tr>
                    <td width="100%" valign="top">
                        <table width="100%" height="100%">
                            <tr>
                                <td>
                                    <div class="DivCCBreadCrumbs">
                                        <a href="<%= request.getContextPath() %>/rx/searchDrug?demographicNo=${bean.demographicNo}"> 
                                            <fmt:message key="SearchDrug.title"/>
                                        </a> > 
                                        <b>
                                            <fmt:message key="SearchDrug.title.CopyFavorites"/>
                                        </b>
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <div class="DivContentPadding">
                                        <input type="button" value="Back to Search For Drug" class="ControlPushButton" onClick="javascript:window.location.href='<%= request.getContextPath() %>/rx/searchDrug?demographicNo=${bean.demographicNo}';"/>
                                    </div>
                                </td>
                            </tr>

                            <tr>
                                <td>
                                    <fieldset class="DivContentPadding">
                                        <legend>Share my favorites</legend>
                                        <label><input type="radio" name="rb_share" value="1" <%= share ? "checked" : "" %>/> Allow other providers to copy my favorites</label>
                                        <label><input type="radio" name="rb_share" value="0" <%= !share ? "checked" : "" %>/> Keep my favorites private</label>
                                        <input type="button" value="Save sharing preference" onclick="update();this.form.submit();"/>
                                    </fieldset>
                                    <c:if test="${not empty copiedFavoritesCount}">
                                        <p role="status"><carlos:encode value="${copiedFavoritesCount}"/> favorites copied to your favorites.</p>
                                    </c:if>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <div class="DivContentPadding">
                                        <div class="DivContentTitle">Choose a provider who shares favorites</div>
                                    </div>
                                </td>
                            </tr>

                            <tr>
                                <td>
                                    <div class="DivContentPadding">
                                        <table cellspacing="0" cellpadding="2">
                                            <tr>
                                                <td>
                                                    <select name="ddl_provider" aria-label="Provider sharing favorites" onchange="this.form.elements.dispatch.value='refresh';this.form.submit();">
                                                        <option value=""> Select Provider</option>
                                                        <% for (String sharedProviderNo : allProviders) {
                                                            if (sharedProviderNo.equals(providerNo)) continue;
                                                            Provider sharedProvider = providerDao.getProvider(sharedProviderNo);
                                                            if (sharedProvider == null) continue;
                                                        %>
                                                            <option value="<carlos:encode value='<%= sharedProviderNo %>' context="htmlAttribute"/>"
                                                                <%= sharedProviderNo.equals(copyProviderNo) ? "selected" : "" %>>
                                                                <carlos:encode value='<%= sharedProvider.getFormattedName() %>'/>
                                                            </option>
                                                        <% } %>
                                                    </select>
                                                    <input type="button" onclick="copy();this.form.submit();" value="Copy to my Favorites" name="b_copy"/>
                                                </td>
                                            </tr>

                                            <c:forEach var="fav" items="${copyFavorites}" varStatus="status">
                                                <c:set var="i" value="${status.index}" />
                                                <c:set var="isCustom" value="${fav.gcnSeqNo == 0}" />
                                                
                                                <tr class="tblRow" style="background-color:#F5F5F5" name="record${i}Line1">
                                                    <td colspan="2">
                                                        <input type="checkbox" name="selected${i}" id="selected${i}" value="1"/>
                                                        <label for="selected${i}"><b>Copy <carlos:encode value="${fav.favoriteName}"/></b></label>
                                                        <input type="hidden" name="fldFavoriteId${i}" value="${carlos:forHtmlAttribute(fav.id)}"/>
                                                    </td>
                                                </tr>
                                                
                                                <tr class="tblRow" style="background-color:#F5F5F5" name="record${i}Line2">
                                                    <td><b>Drug:</b> <carlos:encode value="${isCustom ? fav.customName : fav.bn}"/></td>
                                                    <td colspan="5"><b>Generic Name:</b> ${carlos:forHtmlContent(fav.gn)}</td>
                                                </tr>

                                                <tr class="tblRow" style="background-color:#F5F5F5" name="record${i}Line3">
                                                    <td><b>Take:</b> <carlos:encode value="${fav.takeMin}"/> to <carlos:encode value="${fav.takeMax}"/></td>
                                                    <td><b>Instructions:</b> <carlos:encode value="${fav.special}"/></td>
                                                </tr>
                                            </c:forEach>
                                        </table>
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </form>
    </body>
</html>
