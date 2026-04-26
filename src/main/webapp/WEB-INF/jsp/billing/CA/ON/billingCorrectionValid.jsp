<%--

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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
    billingCorrectionValid.jsp (view) - Ontario billing correction
    session-staging fallback view.

    BillingCorrectionValid2Action handles the heavy lifting:
      - enforces _billing w + POST-only
      - rebuilds the BillingBean / BillingDataBean / BillingPatientDataBean
        from request parameters using the legacy pricing/diagnostic
        normalisation
      - stashes them on the session under the legacy keys
      - issues a redirect to /billing/CA/ON/BillingCorrectionReview

    The action returns NONE (no view) on the normal POST path, so this
    JSP is only reached by the defensive-fallback case (e.g. someone
    chained to the success result without going through the action).
    The body intentionally renders nothing — control should already have
    been redirected by the time anything here is evaluated.
    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>

<html>
<head>
    <title></title>
</head>
<body>
</body>
</html>
