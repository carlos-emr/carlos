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
    Purpose: forward a committed legacy Teleplan export to the activity list.
    Features: generation, file publication and claim updates complete in
    ViewGenTeleplanGroupReport2Action before this result is dispatched.
    Parameters: none; the action validates the provider/month selection.
    Authorization: the action enforces reporting read access, billing write access and POST-only requests.
    @since 2026-04-13
--%>
<jsp:forward page='/billing/CA/BC/ViewBillingTeleplanGroupReport'>
    <jsp:param name="year" value=''/>
</jsp:forward>
