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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html>
    <security:oscarSec roleName="${ sessionScope.userrole }" objectName="_admin" rights="r" reverse="${ false }">

        <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
            <title><fmt:message key="messenger.config.MessengerAdmin.title"/></title>

            <%-- global-head.jspf provides jQuery 3.7.1 core + jquery-compat, Bootstrap
                 (JS bundle + CSS), jQuery UI CSS, Font Awesome, the CSRFGuard client
                 script (so the $.post calls below to the CSRF-protected /messenger
                 route carry the token instead of being rejected 403), and carlos-ajax.
                 This page previously loaded jQuery UI's JS with NO jQuery core, so $
                 was undefined and every handler and autocomplete on the page died. --%>
            <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
            <%-- jQuery UI JS is page-specific (needed here for the provider
                 autocomplete); load it after jQuery core from the include above. --%>
            <script type="text/javascript"
                    src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.js"></script>
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
                    $.post(ctx + "/messenger?method=add&member=" + memberId + "&group=" + groupId).done(function () {
                        // Reload the group member list to show the new member
                        $('#group-member-list-' + groupId).load(ctx + '/messenger?method=fetch #group-member-list-' + groupId);
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
                        $.post(ctx + "/messenger?method=remove&member=" + memberId).done(function () {
                            // Remove from groups view display. Quote the attribute value:
                            // provider ids are numeric, and i[id^=123] is an invalid CSS
                            // identifier that would silently match nothing.
                            $('div#manageGroups i[id^="' + memberId + '"]').parent().parent().remove();
                        });
                    }
                }

                function removeGroupMember(memberId, groupId) {
                    if (memberId) {
                        $.post(ctx + "/messenger?method=remove&member=" + memberId + "&group=" + groupId).done(function () {
                            /*
                             * Add the group id back into selector as it is used to make the id's unique.
                             * Remove the selected value from the user interface
                             */
                            $('#' + memberId + '-' + groupId).parent().parent().remove();
                        });
                    }
                }

                function createGroup(groupName) {
                    // Wait for the create to commit before reloading the panel (the
                    // previous code raced the reload against the POST), then re-attach
                    // the provider autocomplete to the freshly-loaded search boxes.
                    $.post(ctx + "/messenger?method=create&groupName=" + encodeURIComponent(groupName)).done(function () {
                        $('#manageGroups').load(ctx + '/messenger?method=fetch #manageGroups', function () {
                            if (window.initProviderAutocomplete) { window.initProviderAutocomplete(); }
                        });
                    });
                }

                function deleteGroup(groupId) {
                    $.post(ctx + "/messenger?method=remove&group=" + encodeURIComponent(groupId)).done(function () {
                        $('#manageGroups').load(ctx + '/messenger?method=fetch #manageGroups', function () {
                            if (window.initProviderAutocomplete) { window.initProviderAutocomplete(); }
                        });
                    });
                }

                // Build the provider list for the group-search typeahead. The source
                // spans are class="provider-name" — the previous selector
                // "span.providers-name" matched nothing, so the autocomplete had an
                // empty source and offered no options.
                function collectProviders() {
                    var providers = [];
                    $("span.provider-name").each(function () {
                        providers.push({value: this.id, label: $(this).text().trim()});
                    });
                    return providers;
                }

                // Attach the provider typeahead to each group's search box. The input
                // is class="search-provider" (the previous ".search-providers" matched
                // nothing). Exposed on window and idempotent so it can be re-run after
                // createGroup()/deleteGroup() reload the #manageGroups panel.
                window.initProviderAutocomplete = function () {
                    var providers = collectProviders();
                    $(".search-provider").each(function () {
                        if ($(this).data("uiAutocomplete")) {
                            return; // already initialised on this input
                        }
                        $(this).autocomplete({
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
                };

                $(document).ready(function () {
                    // Contact checkboxes live in #addContacts, which is not reloaded,
                    // so a direct binding is fine here.
                    $("input:checkbox").on("change", function () {
                        if (this.checked) {
                            addMember(this.value, 0);
                        } else {
                            removeMember(this.value, 0)
                        }
                    });

                    // The group-management controls live inside #manageGroups, which
                    // createGroup()/deleteGroup() replace via .load(). Bind these with
                    // event delegation on document so they keep working after a reload
                    // (previously the direct bindings were lost on the first reload).
                    $(document).on("click", ".add-member-btn", function () {
                        var groupId = this.id.replace("add-", '');
                        var memberId = $("#add-member-id-" + groupId).val();
                        if (memberId) {
                            addMember(memberId, groupId);
                            $(".search-provider").val('');
                        }
                    });

                    $(document).on("click", "#add-group-btn", function () {
                        var groupName = $("#new-group-name").val();
                        if (groupName) {
                            createGroup(groupName);
                        }
                    });

                    $(document).on("click", ".delete-group-btn", function () {
                        var groupId = this.id;
                        if (groupId) {
                            groupId = groupId.replace("delete-", '');
                            deleteGroup(groupId);
                        }
                    });

                    window.initProviderAutocomplete();
                });
            </script>

        </head>

        <body>

        <div class="container-fluid">

            <div class="navbar">
                <div class="container-fluid">
                    <a class="navbar-brand" href="#">
                        Messenger Group Admin
                    </a>
                    <ul class="nav nav-tabs">
                        <li class="nav-item">
                            <a class="nav-link active" href="#addContacts" data-bs-toggle="tab"><fmt:message key="messenger.config.MessengerAdmin.manageContacts"/></a>
                        </li>
                        <li class="nav-item">
                            <a class="nav-link" href="#manageGroups" data-bs-toggle="tab"><fmt:message key="messenger.config.MessengerAdmin.manageContactGroups"/></a>
                        </li>
                    </ul>
                </div>
            </div>

            <div class="row tab-content">
                <div class="tab-pane active" id="addContacts">
                    <p><fmt:message key="messenger.config.MessengerAdmin.msgEnableDisableProviders"/></p>
                    <ul class="nav nav-tabs">
                        <li class="nav-item">
                            <a class="nav-link active" data-bs-toggle="tab" href="#local-contacts">
                                <fmt:message key="messenger.config.MessengerAdmin.localProviders"/>
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
									${carlos:forHtml(contact.lastName)}, ${carlos:forHtml(contact.firstName)}
								</span>
                                        <span class="text-muted">
									${carlos:forHtml(contact.providerType)}
								</span>
                                        </label>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>

                    </div>
                </div>
                <div class="tab-pane" id="manageGroups">
                    <p><fmt:message key="messenger.config.MessengerAdmin.msgManageOscarMessengerGroups"/></p>
                    <ul class="nav nav-tabs">
                        <c:forEach items="${ groups }" var="group" varStatus="count">
                            <li class="nav-item">
                                <a class="nav-link${ count.index eq 0 ? ' active' : '' }" data-bs-toggle="tab" href="#group-${ group.key.id }">
                                    ${carlos:forHtml(group.key.groupDesc)}
                                </a>
                            </li>
                        </c:forEach>
                        <li class="nav-item">
                            <a data-bs-toggle="tab" href="#new-group" class="nav-link text-muted">
                                <i class="fa-solid fa-plus add-group-tab" title="<fmt:message key='messenger.config.MessengerAdmin.newGroup'/>"></i>
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
                                                   title="<fmt:message key='messenger.config.MessengerAdmin.removeContact'/>"
                                                   id="${ member.id.compositeId }-${ group.key.id }"></i>
                                                <span class="provider-name">
											${carlos:forHtml(member.lastName)}, ${carlos:forHtml(member.firstName)}
										</span>
                                                <span class="text-muted">
											${carlos:forHtml(member.providerType)}
										</span>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </div>
                                <div class="mb-3 contact-group-buttons">
                                    <div class="input-group">
                                        <div class="autocomplete">
                                            <input type='text' placeholder="<fmt:message key='messenger.config.MessengerAdmin.lastFirst'/>" id="${ group.key.id }"
                                                   class="search-provider"/>
                                            <input type='hidden' id="add-member-id-${ group.key.id }" value=""/>
                                            <button id="add-${ group.key.id }" class="btn add-member-btn"><fmt:message key="messenger.config.MessengerAdmin.btnAddContact"/>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                                <div class="row" style="background-color:white;">
                                    <button id="delete-${ group.key.id }" class="btn delete-group-btn float-end"><fmt:message key="messenger.config.MessengerAdmin.btnDeleteGroup"/>
                                    </button>
                                </div>
                            </div>
                        </c:forEach>

                        <div class="tab-pane" id="new-group">
                            <div class="mb-3">
                                <div class="input-group">
                                    <input type='text' placeholder="<fmt:message key='messenger.config.MessengerAdmin.groupName'/>" class="group-name-input"
                                           id="new-group-name"/>
                                    <button id="add-group-btn" class="btn btn-secondary">
                                        <fmt:message key="messenger.config.MessengerAdmin.btnAddGroup"/>
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
