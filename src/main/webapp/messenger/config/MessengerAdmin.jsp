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
  MessengerAdmin.jsp - Administrative interface for managing messenger groups
  
  This JSP provides a comprehensive interface for administrators to manage messenger
  groups and their members. It allows creation, deletion, and modification of
  provider groups used for message distribution within the healthcare system.
  
  Main features:
  - Create new messenger groups
  - Rename existing groups
  - Delete groups (with confirmation)
  - Add/remove providers from groups
  - Display hierarchical group structure
  - Support for local groups
  
  Security:
  - Requires "_admin" object with read ("r") permissions
  - Role-based access control via security tags
  
  UI Components:
  - jQuery UI for interactive elements
  - Accordion-style group display
  - Drag-and-drop member management (if enabled)
  - Ajax-based operations for seamless updates
  
  Dependencies:
  - jQuery and jQuery UI libraries
  - MessengerAdmin2Action for backend processing
  - MsgMessengerGroupData for data access
  
  @since 2003
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!DOCTYPE html>
<html>
    <security:oscarSec roleName="${ sessionScope.userrole }" objectName="_admin" rights="r" reverse="${ false }">

        <head>
            <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.config.MessengerAdmin.title"/></title>

            <script type="text/javascript"
                    src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
            <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.min.css" rel="stylesheet"
                  type="text/css"/>
            <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css">
            <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">
            <script type="text/javascript" src="${pageContext.request.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
            <style type="text/css">
                summary {
                    cursor: pointer;
                }

                .contact-group-buttons {
                    padding-top: 10px;
                }

                i.group-member {
                    display: block;
                    float: left;
                    clear: right;
                    width: 20px;
                    margin-top: 3px;
                    margin-bottom: 3px;
                }

                #remote-contacts summary {
                    padding: 5px 10px;
                    background-color: #fafafa;
                    background-image: -moz-linear-gradient(top, #ffffff, #f2f2f2);
                    background-image: -webkit-gradient(linear, 0 0, 0 100%, from(#ffffff), to(#f2f2f2));
                    background-image: -webkit-linear-gradient(top, #ffffff, #f2f2f2);
                    background-image: -o-linear-gradient(top, #ffffff, #f2f2f2);
                    background-image: linear-gradient(to bottom, #ffffff, #f2f2f2);
                    background-repeat: repeat-x;
                    border-top: 1px solid #d4d4d4;
                    border-bottom: 1px solid #d4d4d4;
                }

                #addContacts .tab-content, #manageGroups .group-member-list {
                    background-color: #ccc;
                    border-left: #ccc thin solid;
                    border-right: #ccc thin solid;
                    height: auto;
                    max-height: 900px;
                    overflow-y: auto;
                    overflow-x: hidden;
                }

                #addContacts .contact-entry, #manageGroups .group-member-list .contact-entry {
                    background-color: white;
                    margin: 1px auto;
                    padding: 5px 0px 0px 10px;
                }

                span.provider-name {
                    display: block;
                }
            </style>

            <script type="text/javascript">
                // Store application context path for Ajax requests
                var ctx = '${pageContext.request.contextPath}';

                /**
                 * Adds a provider member to a messenger group.
                 * Updates the group member list display and checks the corresponding checkbox.
                 * 
                 * @param {string} memberId - The ID of the provider to add
                 * @param {string} groupId - The ID of the group to add the member to
                 */
                function addMember(memberId, groupId) {
                    $.post(ctx + "/messenger.do?method=add&member=" + memberId + "&group=" + groupId).success(function () {
                        // Reload the group member list to show the new member
                        $('#group-member-list-' + groupId).load(ctx + '/messenger.do?method=fetch #group-member-list-' + groupId);
                        // Check the appropriate checkbox in the member list display
                        $("div#addContacts input[type='checkbox'][value^='" + memberId + "']").prop("checked", true);
                    });
                }

                /**
                 * Removes a provider member from a messenger group.
                 * Updates both the groups view and unchecks the member checkbox.
                 * 
                 * @param {string} memberId - The ID of the provider to remove
                 * @param {string} groupId - The ID of the group (used for display updates)
                 */
                function removeMember(memberId, groupId) {
                    if (memberId) {
                        $.post(ctx + "/messenger.do?method=remove&member=" + memberId).success(function () {
                            // Remove from groups view display
                            $('div#manageGroups i[id^=' + memberId + ']').parent().parent().remove();
                        });
                    }
                }

                function removeGroupMember(memberId, groupId) {
                    if (memberId) {
                        $.post(ctx + "/messenger.do?method=remove&member=" + memberId + "&group=" + groupId).success(function () {
                            /*
                             * Add the group id back into selector as it is used to make the id's unique.
                             * Remove the selected value from the user interface
                             */
                            $('#' + memberId + '-' + groupId).parent().parent().remove();
                        });
                    }
                }

                function createGroup(groupName) {
                    $.post(ctx + "/messenger.do?method=create&groupName=" + groupName);
                    $('#manageGroups').load(ctx + '/messenger.do?method=fetch #manageGroups');
                }

                function deleteGroup(groupId) {
                    $.post(ctx + "/messenger.do?method=remove&group=" + groupId);
                    $('#manageGroups').load(ctx + '/messenger.do?method=fetch #manageGroups');
                }

                $(document).ready(function () {
                    // create the providers name array
                    var providers = new Array();


                    $("input:checkbox").on("change", function () {
                        if (this.checked) {
                            addMember(this.value, 0);
                        } else {
                            removeMember(this.value, 0)
                        }
                    });

                    $(".add-member-btn").on("click", function () {
                        var groupId = this.id;
                        groupId = groupId.replace("add-", '');
                        var memberId = $("#add-member-id-" + groupId).val();
                        if (memberId) {
                            addMember(memberId, groupId)
                            $(".search-providers").val('');
                        }
                    });

                    $("#add-group-btn").on("click", function () {
                        var groupName = $("#new-group-name").val();
                        if (groupName) {
                            createGroup(groupName);
                        }
                    });

                    $(".delete-group-btn").on("click", function () {
                        var groupId = this.id;
                        if (groupId) {
                            groupId = groupId.replace("delete-", '');
                            deleteGroup(groupId);
                        }
                    });


                    $("span.providers-name").each(function () {
                        var provider = {value: this.id, label: $(this).text().trim()}
                        providers.push(provider);
                    });

                    $(".search-providers").autocomplete({
                        source: providers,
                        focus: function (event, ui) {
                            $(this).val(ui.item.label);
                            return false;
                        },
                        select: function (event, ui) {
                            $(this).val(ui.item.label);
                            $("#add-member-id-" + this.id).val(ui.item.value);
                            return false;
                        }
                    });
                });
            </script>

        </head>

        <body>

        <div class="container-fluid">

            <div class="navbar">
                <div class="container-fluid">
                    <a class="brand" href="#">
                        Messenger Group Admin
                    </a>
                    <ul class="nav nav-tabs">
                        <li class="nav-item">
                            <a class="nav-link active" href="#addContacts" data-bs-toggle="tab">Manage Contacts</a>
                        </li>
                        <li class="nav-item">
                            <a class="nav-link" href="#manageGroups" data-bs-toggle="tab">Manage Contact Groups</a>
                        </li>
                    </ul>
                </div>
            </div>

            <div class="row tab-content">
                <div class="tab-pane active" id="addContacts">
                    <p>Enable or disable (check or uncheck) clinic providers as a contact in the
                        Messenger address book.</p>
                    <ul class="nav nav-tabs">
                        <li class="nav-item">
                            <a class="nav-link active" data-bs-toggle="tab" href="#local-contacts">
                                Local Providers
                            </a>
                        </li>
                    </ul>

                    <div class="tab-content">
                        <div class="tab-pane active" id="local-contacts">
                            <c:forEach items="${ localContacts }" var="contact" varStatus="count">
                                <div class="row contact-entry">
                                    <div class="form-check">
                                        <input type="checkbox" class="form-check-input" value="${ contact.id.compositeId }"
                                            ${ contact.member ? 'checked="checked"' : '' } />
                                        <label class="form-check-label">
                                        <span id="${ contact.id.compositeId }" class="provider-name">
									<c:out value="${ contact.lastName }"/>, <c:out value="${ contact.firstName }"/>
								</span>
                                        <span class="text-muted">
									<c:out value="${ contact.providerType }"/>
								</span>
                                        </label>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>

                    </div>
                </div>
                <div class="tab-pane" id="manageGroups">
                    <p>Manage Oscar Messenger contact groups</p>
                    <ul class="nav nav-tabs">
                        <c:forEach items="${ groups }" var="group" varStatus="count">
                            <li class="nav-item">
                                <a class="nav-link${ count.index eq 0 ? ' active' : '' }" data-bs-toggle="tab" href="#group-${ group.key.id }">
                                    <c:out value="${ group.key.groupDesc }"/>
                                </a>
                            </li>
                        </c:forEach>
                        <li class="nav-item">
                            <a data-bs-toggle="tab" href="#new-group" class="nav-link text-muted">
                                <i class="fa-solid fa-plus add-group-tab" title="New Group"></i>
                            </a>
                        </li>
                    </ul>

                    <div class="tab-content">
                        <c:forEach items="${ groups }" var="group" varStatus="count">
                            <div class="tab-pane ${ count.index eq 0 ? 'active' : '' }"
                                 id="group-${ group.key.id }">
                                <div id="group-member-list-${ group.key.id }">
                                    <c:forEach items="${ group.value }" var="member">
                                        <div class="row contact-entry">
                                            <div class="form-check">
                                                <i class="fa-solid fa-trash group-member"
                                                   onclick="removeGroupMember('${ member.id.compositeId }', '${ group.key.id }')"
                                                   title="Remove Contact"
                                                   id="${ member.id.compositeId }-${ group.key.id }"></i>
                                                <span class="provider-name">
											<c:out value="${ member.lastName }"/>, <c:out
                                                        value="${ member.firstName }"/>
										</span>
                                                <span class="text-muted">
											<c:out value="${ member.providerType }"/>
										</span>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </div>
                                <div class="mb-3 contact-group-buttons">
                                    <div class="input-group">
                                        <div class="autocomplete">
                                            <input type='text' placeholder="Last, First" id="${ group.key.id }"
                                                   class="search-provider"/>
                                            <input type='hidden' id="add-member-id-${ group.key.id }" value=""/>
                                            <button id="add-${ group.key.id }" class="btn add-member-btn">Add Contact
                                            </button>
                                        </div>
                                    </div>
                                </div>
                                <div class="row" style="background-color:white;">
                                    <button id="delete-${ group.key.id }" class="btn delete-group-btn float-end">Delete
                                        Group
                                    </button>
                                </div>
                            </div>
                        </c:forEach>

                        <div class="tab-pane" id="new-group">
                            <div class="mb-3">
                                <div class="input-group">
                                    <input type='text' placeholder="Group Name" class="group-name-input"
                                           id="new-group-name"/>
                                    <button id="add-group-btn" class="btn btn-secondary">
                                        add
                                    </button>
                                </div>
                            </div>
                        </div>

                    </div>
                </div>
            </div>
        </div>
        </body>
    </security:oscarSec>
</html>
