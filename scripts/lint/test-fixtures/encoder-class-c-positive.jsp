<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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
  Fixture role: Defines `encoder-class-c-positive.jsp` as an encoder null-safety lint fixture.
  It is used only by the lint test harness and should keep the unsafe or safe
  encoding pattern obvious to future maintainers.
--%>
<%--
  POSITIVE fixture for scripts/lint/check-encoder-null-safety.py.
  This file MUST trigger a Class C violation — `forHtmlContent` used inside
  an HTML attribute value context. If the lint stops flagging this, the
  d2db61d4 bug class can recur silently.

  Used only by scripts/lint/test-encoder-null-safety.sh. Not deployed.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<input type="hidden" name="demoNo" value="${carlos:forHtmlContent(model.demoNo)}"/>
