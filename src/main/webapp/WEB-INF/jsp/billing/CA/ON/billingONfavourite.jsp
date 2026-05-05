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
  Purpose: Supports billingONfavourite in the Ontario billing workflow.
  Expected request model data includes: favouriteModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title>Add/Edit Service Code</title>

        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet"> <!-- Bootstrap -->

        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                // Form input named "name" shadows the form's .name property,
                // but `.name` on a form returns the FORM's name string when
                // no input has name="name" — so .select()/.focus() fail.
                // Resolve via elements[] which always returns the element.
                var nameInput = document.forms[0].elements['name'];
                if (nameInput && typeof nameInput.focus === 'function') {
                    nameInput.focus();
                    if (typeof nameInput.select === 'function') {
                        nameInput.select();
                    }
                }
            }

            function onSearch() {
                return true;
            }

            function onDelete() {
                return confirm("Are you sure you want to Delete?");
            }

            function onSave() {
                var ret = checkAllFields();
                if (ret === true) {
                    ret = confirm("Are you sure you want to save?");
                }
                return ret;
            }

            function isServiceCode(s) {
                // temp for 0.
                if (s.length == 0) return true;
                if (s.length != 5) return false;
                if ((s.charAt(0) < "A") || (s.charAt(0) > "Z")) return false;
                if ((s.charAt(4) < "A") || (s.charAt(4) > "Z")) return false;

                var i;
                for (i = 1; i < s.length - 1; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (((c < "0") || (c > "9"))) return false;
                }
                return true;
            }

            function checkAllFields() {
                var b = true;
                for (var i = 0; i < 10; i++) {
                    var fieldItem = eval("document.forms[1].serviceCode" + i);
                    if (fieldItem.value.length > 0) {
                        if (!isServiceCode(fieldItem.value)) {
                            b = false;
                            alert("You must type in a Service Code in the field!");
                        }
                    }
                    var fieldItem1 = eval("document.forms[1].serviceUnit" + i);
                    var fieldItem2 = eval("document.forms[1].serviceAt" + i);
                    if (fieldItem1.value.length > 0) {
                        if (!isNumber(fieldItem1.value)) {
                            b = false;
                            alert("You must type in a number in the field!");
                        }
                    }
                    if (fieldItem2.value.length > 0) {
                        if (!isNumber(fieldItem2.value)) {
                            b = false;
                            alert("You must type in a number in the field!");
                        }
                    }
                }
                var fieldItemDx = eval("document.forms[1].dx");
                if (fieldItemDx.value.length > 0) {
                    if (!isNumber(fieldItemDx.value) || fieldItemDx.value.length != 3) {
                        b = false;
                        alert("You must type in a number in the right Dx field!");
                    }
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                // All characters are numbers.
                return true;
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            //-->

        </script>
    </head>
    <body onLoad="setfocus()">
    <h4>Add/Edit Service Code</h4>
    <table style="width:100%;">
        <tr class="myDarkGreen">
            <%--
              ${favouriteModel.message} contains assembler-built trusted HTML.
              The producer (BillingOnFavouriteViewModelAssembler) builds the
              message from constants only — every user value is wrapped in
              SafeEncode.forHtml() before concatenation. No untrusted data
              reaches this rendering point. Do not change this contract
              without updating the assembler's safety invariant comment.
            --%>
            <th class="alert alert-info">${favouriteModel.message}
            </th>
        </tr>
    </table>

    <form method="post" name="baseur0" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONFavourite">
        <table style="width:100%;">
            <tr>
                <td style="width:50%; text-align: center;"><select name="name" id="name">
                    <option selected="selected" value="">- choose one -</option>
                    <c:forEach var="favName" items="${favouriteModel.names}">
                        <option value="<carlos:encode value='${favName.value}' context='htmlAttribute'/>"><carlos:encode value="${favName.value}" context="html"/>
                        </option>
                    </c:forEach>
                </select></td>
                <td><input class="form-control form-control-sm d-inline-block w-auto" type="hidden" name="submit" value="Search"> <input class="btn btn-secondary"
                                                                                                 type="submit"
                                                                                                 name="action"
                                                                                                 value=" Edit "> <input
                        class="btn btn-secondary"
                        type="submit" name="action" value="Delete"
                        onClick="javascript:return onDelete();"></td>
            </tr>

        </table>
    </form>
    <form method="post" name="baseurl" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONFavourite">
        <table style="width:100%;" class="table table-striped table-sm">

            <tr class="myGreen">
                <td style="text-align:right"><b>Name</b></td>
                <td><input class="form-control d-inline-block w-auto" type="text" name="name"
                           value="<carlos:encode value='${favouriteModel.formFields[\"name\"]}' context='htmlAttribute'/>" maxlength='50'/>
                    (e.g. Flu shot) <input class="btn btn-secondary" type="submit" name="submit" value="Search"
                                           onclick="javascript:return onSearch();"></td>
            </tr>

            <c:forEach begin="0" end="${favouriteModel.serviceFieldCount - 1}" var="i">
                <c:set var="codeKey" value="serviceCode${i}"/>
                <c:set var="unitKey" value="serviceUnit${i}"/>
                <c:set var="atKey" value="serviceAt${i}"/>
                <tr>
                    <td style="text-align:right"><b>Service Code ${i + 1}
                    </b></td>
                    <td><input class="form-control form-control-sm d-inline-block w-auto" type="text" name="serviceCode${i}"
                               value="<carlos:encode value='${favouriteModel.formFields[codeKey]}' context='htmlAttribute'/>"
                               maxlength='50' onblur="upCaseCtrl(this)"/> (e.g. A001A) <b>Unit</b><input class="form-control form-control-sm d-inline-block w-auto"
                                                                                                         type="text"
                                                                                                         name="serviceUnit${i}"
                                                                                                         value="<carlos:encode value='${favouriteModel.formFields[unitKey]}' context='htmlAttribute'/>"
                                                                                                         maxlength='2'/>
                        (e.g. 1, 12) <b>@</b><input class="form-control form-control-sm d-inline-block w-auto" type="text"
                                                    name="serviceAt${i}"
                                                    value="<carlos:encode value='${favouriteModel.formFields[atKey]}' context='htmlAttribute'/>"
                                                    maxlength='4'/> (e.g. 0.85)
                    </td>
                </tr>
            </c:forEach>

            <tr>
                <td style="text-align:right"><b>Dx</b></td>
                <td><input class="form-control form-control-sm d-inline-block w-auto" type="text" name="dx"
                           value="<carlos:encode value='${favouriteModel.formFields[\"dx\"]}' context='htmlAttribute'/>" maxlength='4'/>
                    (e.g. 012) <b>Dx1</b> <input class="form-control form-control-sm d-inline-block w-auto" type="text" name="dx1"
                                                 value="<carlos:encode value='${favouriteModel.formFields[\"dx1\"]}' context='htmlAttribute'/>" maxlength='4'/> <b>Dx2</b>
                    <input class="form-control form-control-sm d-inline-block w-auto" type="text" name="dx2" value="<carlos:encode value='${favouriteModel.formFields[\"dx2\"]}' context='htmlAttribute'/>"
                           maxlength='4'/></td>
            </tr>
            <tr>
                <td style="text-align:center" class="myGreen" colspan="2"><input
                        type="hidden" name="action" value='<carlos:encode value="${favouriteModel.action}" context="htmlAttribute"/>'> <input
                        type="submit" name="submit" class="btn btn-primary"
                        value="<fmt:message key="admin.resourcebaseurl.btnSave"/>"
                        onclick="javascript:return onSave();"> <input class="btn btn-secondary" type="button"
                                                                      name="Cancel"
                                                                      value="<fmt:message key="admin.resourcebaseurl.btnExit"/>"
                                                                      onClick="window.close()"></td>
            </tr>
        </table>
    </form>
    </body>
</html>