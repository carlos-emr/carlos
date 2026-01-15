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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>

<head>

    <title>Community Comprehensive Geriatric Assessment Form</title>
    <meta name="viewport" content="width=device-width, user-scalable=false;">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap2-datepicker/datepicker3.css">
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/js/jqplot/jquery.jqplot.min.css">
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/form/eCARES/eCARES_v1.css">
</head>

<body>

<div class="patient-info-bar">
    <%@include file="sections/patient_info.jsp" %>
</div>

<div class="container main-container" style="padding-bottom: 80px;">
    <noscript>Your browser either does not support JavaScript, or has it turned off.</noscript>
    <form name="formeCARES" class="form-inline" role="form" method="post"
          action="${pageContext.servletContext.contextPath}/formeCARES.do" id="formeCARES">
        <input type="hidden" name="demographicNo"
               value="${ not empty param.demographicNo ? param.demographicNo : param.demographic_no }"/>
        <input type="hidden" name="appointmentNo"
               value="${ not empty param.appointmentNo ? param.appointmentNo : param.appointment_no }"/>
        <input type="hidden" name="form_class" value=""/>
        <input type="hidden" name="formId" value="${ param.formId }"/>
        <input type="hidden" name="providerNo" value="${ param.provNo }"/>
        <input type="hidden" name="method" value="save"/>
        <input type="hidden" name="ticklerId" value="0"/>
        <div id="contextPath" class="hidden">${pageContext.servletContext.contextPath}</div>
        <fieldset>
            <div class="form-container center-container">

                <h4 class="text-underline font-bold mb-4">Community Comprehensive Geriatric Assessment Form</h4>

                <!-- FORM BODY -->

                <div class="flex items-end border-bottom-gray pb-1">
                    <%@include file="sections/action_required.jsp" %>
                    <div class="flex flex-col pl-2">
                        <%@include file="sections/legend.jsp" %>
                        <%@include file="sections/info.jsp" %>
                    </div>
                </div>

                <div>
                    <%@include file="sections/crcl.jsp" %>
                </div>
                <div>
                    <%@include file="sections/cognition.jsp" %>
                    <hr class="ecares-hr"/>
                </div>
                <div>
                    <%@include file="sections/emotional.jsp" %>
                    <hr class="ecares-hr"/>
                </div>
                <div>
                    <%@include file="sections/motivational.jsp" %>
                    <hr class="ecares-hr"/>
                </div>
                <div>
                    <%@include file="sections/communication.jsp" %>
                    <hr class="ecares-hr"/>
                </div>

                <div>
                    <%@include file="sections/sleep.jsp" %>
                    <hr class="ecares-hr"/>
                </div>

                <div>
                    <%@include file="sections/immunizations.jsp" %>
                    <hr class="ecares-hr"/>
                </div>

                <div>
                    <%@include file="sections/advance_directive.jsp" %>
                    <hr class="ecares-hr"/>
                </div>
                <div>
                    <%@include file="sections/control_of_life.jsp" %>
                    <hr class="ecares-hr"/>
                </div>
                <div>
                    <%@include file="sections/exercise.jsp" %>
                    <hr class="ecares-hr"/>
                </div>
                <div>
                    <%@include file="sections/strength.jsp" %>
                    <hr class="ecares-hr"/>
                </div>

                <div class="flex w-full" style="align-items: flex-start">

                    <div class="flex-8">
                        <div>
                            <%@include file="sections/balance.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/mobility.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/nutrition.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/elimination.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/adls.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/iadls.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/income.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/marital.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/home.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/supports.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/caregiver_relationship.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <%@include file="sections/caregiver_stress.jsp" %>
                            <hr class="ecares-hr"/>
                        </div>
                        <div>
                            <div class="flex items-center">
                                <div>
                                    <svg height="10" width="10">
                                        <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
                                    </svg>
                                    <input type="checkbox" class="action-required" name="adj_required" data-score="1">
                                    <strong class="highlight" data-field-name="adj_required">Adjustment
                                        Required</strong>
                                </div>
                                <div id="form-status-container">
                                    <div class="flex pl-2 items-center">
                                        <label class="inline-block font-bold" for="completed">Form Status: </label>
                                        <select name="completed" id="completed" class="ecares-input">
                                            <option value="false">incomplete</option>
                                            <option value="true">complete</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="flex-2">
                        <div class="flex h-full">
                            <%@include file="sections/clinical_frailty.jsp" %>
                        </div>
                        <div id="frailty-index-container">
                            <div class="flex pl-2 items-center">
                                <span class="flex-1" title="Deficit based Frailty Score"><strong>Frailty Index:</strong></span>
                                <span class="tt" data-html="true" data-placement="top"
                                      title="Non-frail (0 to &#8804;0.1) <br />
                                Vulnerable (&#62;0.1 to &#8804;0.2) <br />
                                Mild (&#62;0.2 to &#60;0.3) <br />
                                Moderate (&#62;0.3 or to &#60;0.4) <br />
                                Severe (&#62;0.4 or to &#60;0.5) <br />
                                Extreme (Over 0.5)">
                                <input style="width: 40px; color:black; border: 1px solid gray; background-color: rgb(223, 223, 223);"
                                       name="deficit_based_frailty_score" readonly type="text"/>
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="flex mt-2">
                    <%@include file="sections/feedback_comments.jsp" %>
                </div>
                <div class="flex mt-2">
                    <div class="flex-1 pr-1">
                        <%@include file="sections/problems.jsp" %>
                    </div>
                    <div class="flex-1 pl-1">
                        <%@include file="sections/medications.jsp" %>
                    </div>
                </div>

                <div class="flex mt-2 w-full justify-end">
                    <%@include file="sections/assessor.jsp" %>
                    <%@include file="sections/date.jsp" %>
                </div>

            </div><!-- form-container -->
        </fieldset>
    </form>

    <!-- Modal -->
    <div class="modal fade" id="myModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel"
         aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                    <h4 class="modal-title">Please confirm</h4>
                </div>
                <div class="modal-body">
                    <p><span class="glyphicon glyphicon-ok-circle text-success"></span> All information entered on
                        the eCGA has been saved.</p>

                    <span class="glyphicon glyphicon-warning-sign text-warning"></span> The eCGA is not complete,
                    are you sure you want to close?
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default continueButton" data-dismiss="modal">No, continue
                        editing
                    </button>
                    <button type="button" class="btn btn-warning closeWindowButton">Yes, exit</button>
                </div>
            </div><!-- /.modal-content -->
        </div><!-- /.modal-dialog -->
    </div><!-- /.modal -->


    <!-- Modal -->
    <div class="modal fade" id="ticklerModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel"
         aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                    <h4 class="modal-title" id="ticklerModalTitle"></h4>
                </div>
                <div class="modal-body">
                </div>

                <div class="modal-footer">
                    <button type="button" class="btn btn-default save-tickler">Create Tickler</button>
                    <button type="button" class="btn btn-warning" data-dismiss="modal">Cancel</button>
                </div>
            </div>
        </div><!-- /.modal-content -->

    </div><!-- /.modal-dialog -->
</div><!-- /.modal -->

<!-- Modal -->
<div class="modal fade" id="chartModal" tabindex="-1" role="dialog" aria-labelledby="chartModalLabel"
     aria-hidden="true">
    <div class="modal-dialog modal-lg" style="width:60%;">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                <h4 class="modal-title">eFI - Frailty Index</h4>
            </div>
            <div class="modal-body">
                <div id="chart" style="width:90%;margin:auto"></div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
            </div>
        </div><!-- /.modal-content -->
    </div><!-- /.modal-dialog -->
</div><!-- /.modal -->

<div class="top-controls hidden-print">
    <div class="center-container wcag-container row">

        <div class="col-xs-10 text-right">
            <input type="button" class="btn btn-info print" value="Print">
            <input type="button" class="btn btn-primary calculate" value="Calculate Frailty Index">
            <input type="button" class="btn btn-primary save" value="Save">
            <input type="button" class="btn btn-danger exit" value="Save & Close">
            <a data-toggle="modal" href="#chartModal" class="btn btn-info">Graph eFI</a>
            <input type="button" class="btn btn-info tickler" value="Create Tickler">
            <input type="button" class="btn btn-info export" value="Export">
        </div>

    </div><!-- ./wcag-container -->
</div>

<script type="text/javascript" src="${pageContext.request.contextPath}/js/jquery-1.9.1.min.js"></script>
<script type="text/javascript"
        src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"></script>
<script type="text/javascript"
        src="https://cdnjs.cloudflare.com/ajax/libs/jquery.serializeJSON/3.2.1/jquery.serializejson.min.js"
        integrity="sha512-Br5bdkAjeKASBYCbM5qNfb5DB7tGLX3OSwYlDQxKKPNPWmKEcChhEpk7kIEhFLUjTxw0FdGkB/uJNEzbfYFxWkA=="
        crossorigin="anonymous"></script>
<script type="text/javascript"
        src="${pageContext.request.contextPath}/library/bootstrap2-datepicker/bootstrap-datepicker.js"></script>

<!--jqplot js includes-->
<script class="include" type="text/javascript"
        src="${pageContext.request.contextPath}/js/jqplot/jquery.jqplot.min.js"></script>
<script class="include" type="text/javascript"
        src="${pageContext.request.contextPath}/js/jqplot/plugins/jqplot.highlighter.min.js"></script>
<script class="include" type="text/javascript"
        src="${pageContext.request.contextPath}/js/jqplot/jqplot.dateAxisRenderer.min.js"></script>
<script type="text/javascript" src="${pageContext.request.contextPath}/form/eCARES/dictionary.js"></script>
<script type="text/javascript" src="${pageContext.request.contextPath}/form/eCARES/eCARES_v1.js"></script>

</body>

</html>