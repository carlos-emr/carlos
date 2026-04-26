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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingformBilltypeViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.ManageBillingformBilltypeDataAssembler" %>
<fmt:setBundle basename="oscarResources"/>

<%--
  Defensive model-resolver: ensures ${billtypeModel} is set on the request even
  when this fragment is reached without going through
  ManageBillingformBilltype2Action. The action's _admin.billing w privilege
  check is duplicated here for parity.
--%>
<%
    if (request.getAttribute("billtypeModel") == null) {
        io.github.carlos_emr.carlos.utility.LoggedInInfo loggedInInfo =
                io.github.carlos_emr.carlos.utility.LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("manageBillingform_billtype.jsp fallback: missing session");
        }
        io.github.carlos_emr.carlos.managers.SecurityInfoManager __secMgr;
        try {
            __secMgr = io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                    io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "manageBillingform_billtype.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("manageBillingform_billtype.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("manageBillingform_billtype.jsp fallback: missing required sec object (_admin.billing)");
        }
        request.setAttribute("billtypeModel",
                new ManageBillingformBilltypeDataAssembler().assemble(request));
    }
%>

<table width=95%>
    <tr>
        <td class="black" width="15%"><carlos:encode value='${billtypeModel.typeId}' context="html"/>
        </td>
        <td class="black" height="30"><carlos:encode value='${billtypeModel.typeName}' context="html"/>
        </td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td class="white">
            <p>&nbsp;<br>
                <fmt:message key="billing.manageBillingform_add.formDefaultBillType"/>
                :<br>
                <input type="hidden" name="bill_servicetype" value="<carlos:encode value='${billtypeModel.typeId}' context="htmlAttribute"/>">
                <input type="hidden" name="billtype_old" value="<carlos:encode value='${billtypeModel.billType}' context="htmlAttribute"/>">
                <select name="billtype_new">
                    <option value="no" <c:if test="${billtypeModel.billType eq 'no'}">selected</c:if>>--
                        no --
                    </option>
                    <option value="ODP" <c:if test="${billtypeModel.billType eq 'ODP'}">selected</c:if>>Bill
                        OHIP
                    </option>
                    <option value="WCB" <c:if test="${billtypeModel.billType eq 'WCB'}">selected</c:if>>WSIB</option>
                    <option value="NOT" <c:if test="${billtypeModel.billType eq 'NOT'}">selected</c:if>>Do
                        Not Bill
                    </option>
                    <option value="IFH" <c:if test="${billtypeModel.billType eq 'IFH'}">selected</c:if>>IFH</option>
                    <option value="PAT" <c:if test="${billtypeModel.billType eq 'PAT'}">selected</c:if>>3rd
                        Party
                    </option>
                    <option value="OCF" <c:if test="${billtypeModel.billType eq 'OCF'}">selected</c:if>>-OCF</option>
                    <option value="ODS" <c:if test="${billtypeModel.billType eq 'ODS'}">selected</c:if>>-ODSP</option>
                    <option value="CPP" <c:if test="${billtypeModel.billType eq 'CPP'}">selected</c:if>>-CPP</option>
                    <option value="STD" <c:if test="${billtypeModel.billType eq 'STD'}">selected</c:if>>-STD/LTD</option>
                </select> <input type="button" value="Change"
                                 onclick="manageBillType(bill_servicetype.value, billtype_old.value, billtype_new.value);"><br>
            </p>
            <p><input type="button" value="Delete Billing Form"
                      onclick="onUnbilled('<carlos:encode value='${billtypeModel.typeId}' context="javaScriptAttribute"/>');">
            <p><input type="button" value="Cancel"
                      onclick="showManageType(false);"></p>
        </td>
    </tr>
</table>
