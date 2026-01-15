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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.FavoritesDao" %>
<%@ page import="ca.openosp.openo.commn.model.Favorites" %>
<%@ page import="ca.openosp.openo.commn.dao.FavoritesPrivilegeDao" %>
<%@ page import="ca.openosp.openo.commn.model.FavoritesPrivilege" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.prescript.data.RxCodesData" %>
<%
    FavoritesDao favoritesDao = SpringUtils.getBean(FavoritesDao.class);
    FavoritesPrivilegeDao favoritesPrivilegeDao = SpringUtils.getBean(FavoritesPrivilegeDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    // Setting default values
    String providerNo = (String) request.getAttribute("providerNo");
    boolean share = false;
    FavoritesPrivilege fp = favoritesPrivilegeDao.findByProviderNo(providerNo);
    if (fp != null) {
        share = fp.isOpenToPublic();
    }

    List<String> allProviders = favoritesPrivilegeDao.getProviders();
    String copyProviderNo = (String) request.getAttribute("copyProviderNo");
    if (copyProviderNo == null) {
        copyProviderNo = "";
    }

    RxCodesData.FrequencyCode[] freq = new RxCodesData().getFrequencyCodes();
%>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath()%>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title.CopyFavorites"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        
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
        <link rel="stylesheet" type="text/css" href="oscarRx/styles.css">
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
        <form action="<%= request.getContextPath()%>/oscarRx/copyFavorite2.do">
            <input type="hidden" name="dispatch" value="refresh"/>
            <input type="hidden" name="userProviderNo" value="<%=providerNo%>"/>
            <input type="hidden" name="copyProviderNo" value="<%=copyProviderNo%>"/>

            <table border="0" cellpadding="0" cellspacing="0" style="border-collapse: collapse" width="100%">
                <%@ include file="TopLinks.jsp"%>
                
                <tr>
                    <td width="100%" valign="top">
                        <table width="100%" height="100%">
                            <tr>
                                <td>
                                    <div class="DivCCBreadCrumbs">
                                        <a href="oscarRx/SearchDrug3.jsp"> 
                                            <fmt:setBundle basename="oscarResources"/>
                                            <fmt:message key="SearchDrug.title"/>
                                        </a> > 
                                        <b><fmt:setBundle basename="oscarResources"/>
                                            <fmt:message key="SearchDrug.title.CopyFavorites"/>
                                        </b>
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <div class="DivContentPadding">
                                        <input type="button" value="Back to Search For Drug" class="ControlPushButton" onClick="javascript:window.location.href='<%= request.getContextPath() %>/oscarRx/SearchDrug3.jsp';"/>
                                    </div>
                                </td>
                            </tr>

                            <tr>
                                <td>
                                    <div class="DivContentPadding">
                                        <div class="DivContentTitle">Choose provider who share the favorites</div>
                                    </div>
                                </td>
                            </tr>

                            <tr>
                                <td>
                                    <div class="DivContentPadding">
                                        <table cellspacing="0" cellpadding="2">
                                            <tr>
                                                <td>
                                                    <select name="ddl_provider" onchange="form.submit();">
                                                        <option value=""> Select Provider</option>
                                                        <%
                                                            for (int p = 0; p < allProviders.size(); p++) {
                                                                if (((String) allProviders.get(p)).equalsIgnoreCase(providerNo)) {
                                                                    continue;
                                                                }
                                                        %>
                                                            <option value="<%=((String) allProviders.get(p))%>"
                                                                <%=((String) allProviders.get(p)).equalsIgnoreCase(copyProviderNo) ? "SELECTED" : ""%>>
                                                                <%=providerDao.getProvider((String) allProviders.get(p)).getFormattedName()%>
                                                            </option>
                                                        <% } %>
                                                    </select>
                                                    <input type="button" onclick="copy();form.submit();" value="Copy to my Favorites" name="b_copy"/>
                                                </td>
                                            </tr>

                                            <c:forEach var="fav" items="${favoritesDao.findByProviderNo(copyProviderNo)}" varStatus="status">
                                                <c:set var="i" value="${status.index}" />
                                                <c:set var="isCustom" value="${fav.gcnSeqNo == 0}" />
                                                
                                                <tr class="tblRow" style="background-color:#F5F5F5" name="record${i}Line1">
                                                    <td colspan="2">
                                                        <b>Favorite Name:</b>
                                                        <input type="hidden" name="fldFavoriteId${i}" value="${fav.id}"/>
                                                        <input type="text" size="50" name="fldFavoriteName${i}" class="tblRow" value="${fav.favoriteName}"/>
                                                    </td>
                                                </tr>
                                                
                                                <tr class="tblRow" style="background-color:#F5F5F5" name="record${i}Line2">
                                                    <td><b>Brand Name:</b>${fav.bn}</td>
                                                    <td colspan="5"><b>Generic Name:</b>${fav.gn}</td>
                                                </tr>

                                                <tr class="tblRow" style="background-color:#F5F5F5" name="record${i}Line3">
                                                    <td><b>Take:</b>
                                                        <input type="text" name="fldTakeMin${i}" class="tblRow" size="3" value="${fav.takeMin}"/>
                                                        <span>to</span>
                                                        <input type="text" name="fldTakeMax${i}" class="tblRow" size="3" value="${fav.takeMax}"/>
                                                    </td>
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
