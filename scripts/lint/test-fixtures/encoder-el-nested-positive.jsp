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
  Fixture role: Defines `encoder-el-nested-positive.jsp` as an encoder null-safety lint fixture.
  It is used only by the lint test harness and should keep the unsafe or safe
  encoding pattern obvious to future maintainers.
--%>
<%--
  POSITIVE fixture for scripts/lint/check-encoder-null-safety.py (#5477).
  Each line below calls an OWASP e:forXxx EL function in a shape the
  lint's earlier pattern, \${\s*e:for..., let through: nested as the
  argument of another function (the DisplayWaitingList.jsp link fixed in
  #5477), after a ternary, in a deferred #{...} expression, and after an
  earlier expression whose string literal holds a closing brace. The lint
  MUST report all four as Class B EL sites. No taglib is declared, so
  Class B is the only class that can fire.

  Used only by scripts/lint/test-encoder-null-safety.sh. Not deployed.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<a href="#" onclick="popup('/demographic/DemographicEdit?demographic_no=${carlos:forJavaScript(e:forUriComponent(row.demographicNo))}')">x</a>
<span>${empty row.note ? '' : e:forHtml(row.note)}</span>
<span>#{e:forHtmlContent(row.name)}</span>
<input value="${carlos:forHtmlAttribute(fn:trim('}'))}${carlos:forHtmlAttribute(e:forUri(row.url))}"/>
