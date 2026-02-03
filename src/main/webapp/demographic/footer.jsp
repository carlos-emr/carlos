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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>

<hr width="100%" color="orange">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr>
        <td><a href="search.jsp"> <img src="<%= request.getContextPath() %>/images/leftarrow.gif"
                                       border="0" width="25" height="20" align="absmiddle"> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.footer.btnBack"/></a></td>

        <td align="right">
            <caisi:isModuleLoad moduleName="caisi">
            <a href='${request.contextPath}/PMmodule/ProviderInfo.do'>
                </caisi:isModuleLoad>
                <caisi:isModuleLoad moduleName="caisi" reverse="true">
                <a href="#" onClick="self.close();">
                    </caisi:isModuleLoad>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.footer.btnClose"/><img
                        src="<%= request.getContextPath() %>/images/rightarrow.gif" border="0" width="25" height="20"
                        align="absmiddle"></a>

        </td>
    </tr>
</table>
