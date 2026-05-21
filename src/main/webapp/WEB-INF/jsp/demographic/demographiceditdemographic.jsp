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
<%--
    Compatibility wrapper for the legacy demographic edit JSP path.

    The original scriptlet-heavy page exceeded the JVM 65,535-byte method-code
    limit when JSPC generated its _jspService method. The page implementation now
    lives behind DemographicEdit2Action and the split /WEB-INF/jsp/demographic/edit*.jsp
    fragments, so this legacy target stays tiny for JSP precompilation while
    preserving request parameters for older Struts results that still resolve here.

    @since 2026-05-21
--%>
<jsp:forward page="/demographic/DemographicEdit"/>
