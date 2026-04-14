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

 Now maintained by the CARLOS EMR Project (2026+).
 https://github.com/carlos-emr/carlos
 CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
 ticklerEditSuccess.jsp - Minimal sentinel response for tickler edit success.

 Purpose:
   Rendered inside a hidden iframe by ticklerEdit.jsp after a successful EditTickler.do
   action. Provides a DOM sentinel element (#tickler-edit-ok) that the iframe.onload
   callback uses to confirm the save was successful, instead of relying on URL inspection.

   All refresh/close logic is handled by the ticklerEdit.jsp iframe.onload callback;
   this page intentionally contains no JavaScript.

 @since CARLOS EMR 2026
--%>
<!DOCTYPE html>
<html>
<head><title></title></head>
<body>
<span id="tickler-edit-ok" style="display:none;"></span>
</body>
</html>
