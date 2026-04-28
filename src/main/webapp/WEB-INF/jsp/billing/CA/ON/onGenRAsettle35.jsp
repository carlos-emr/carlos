<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.

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
  Page role: Renders `onGenRAsettle35.jsp` for the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.service.OnGenRAsettleService" %>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%-- ViewOnGenRAsettle352Action enforces _billing w + POST and runs the
     I2/35 settle mutation (with Q-code allow-list) via OnGenRAsettleService
     — the 3 inline DAO lookups (RaHeaderDao, BillingDao, RaDetailDao)
     the JSP body used to perform are now in the assembler. --%>
<script LANGUAGE="JavaScript">
    self.close();
    self.opener.refresh();
</script>
