<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `billingON_dx_desc.jsp` for the Ontario billing workflow.
  Expected request model data includes: dxDescModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingONDxDescViewModel" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingDxCodeDataAssembler" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // ViewBillingONDxDesc2Action enforces _billing r and assembles the
    // truncated description via BillingDxCodeDataAssembler.assembleDescription.
    BillingONDxDescViewModel dxDescModel =
            (BillingONDxDescViewModel) request.getAttribute("dxDescModel");
    if (dxDescModel == null) {
        dxDescModel = BillingONDxDescViewModel.builder().build();
    }
%><carlos:encode value="${dxDescModel.description}" context="html"/>
