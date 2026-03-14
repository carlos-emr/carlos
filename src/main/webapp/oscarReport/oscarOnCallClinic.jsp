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
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>On Call Clinic Calendar</title>
    <link href="<%=request.getContextPath()%>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/css/fontawesome-all.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/css/bootstrap-year-calendar.min.css" rel="stylesheet">
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="<%=request.getContextPath()%>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
    <script src="<%=request.getContextPath()%>/js/bootstrap-year-calendar.min.js"></script>


    <script type="text/javascript">


        $(document).ready(function () {

            $('.calendar').calendar({

                startYear: new Date().getFullYear(),
                minDate: new Date("2017-01-01"),

                language: 'en', // or 'fr'
                allowOverlap: true,
                displayWeekNumber: false,
                displayDisabledDataSource: false,
                displayHeader: true,
                alwaysHalfDay: false,
                dataSource: [{}],
                style: 'custom',
                enableRangeSelection: false,
                disabledDays: [],
                disabledWeekDays: [],
                hiddenWeekDays: [],
                roundRangeLimits: false,
                enableContextMenu: false, // enable context menu
                contextMenuItems: [], // an array of menu items,
                customDataSourceRenderer: function (element, date) {
                    var styles = {
                        backgroundColor: "#326b53",
                        color: "white",
                        "border-radius": "50%"

                    };
                    $(element).css(styles);

                },


                // Callback Events
                clickDay: function (d) {
                    bootstrap.Popover.getInstance(d.element)?.hide();
                    var dataSource = $('#calendar').data('calendar').getDataSource();
                    var newId = 0;
                    var delId = 0;
                    var del = false;
                    for (var i in dataSource) {
                        if (dataSource[i].startDate == d.date.getTime()) {
                            delId = dataSource[i].id;
                            dataSource.splice(i, 1);
                            del = true;
                            break;
                        }

                        if (newId < dataSource[i].id) {
                            newId = dataSource[i].id;
                        }

                    }


                    if (del) {
                        $.post("<%=request.getContextPath()%>/admin/oncallClinic.do?method=Delete", {id: delId}, function (data) {

                            var result = JSON.parse(data);
                            if (result.error == "true") {
                                alert("Your calendar has not been saved! Please reload the page and try again");
                            } else {
                                $('#calendar').data('calendar').setDataSource(dataSource);
                            }
                        });


                    } else {
                        newId++;
                        var event = {
                            id: newId,
                            name: "PCN On-Call Clinic",
                            location: "Hamilton, ON",
                            startDate: d.date.getTime(),
                            endDate: d.date.getTime(),
                            color: "#326b53"
                        };
                        var data = JSON.stringify(event);
                        $.post("<%=request.getContextPath()%>/admin/oncallClinic.do?method=Save", {event: data}, function (returnedData) {
                            var result = JSON.parse(returnedData);
                            if (result.error == "true") {
                                alert("Your calendar has not been saved! Please reload the page and try again");
                            } else {
                                dataSource.push(event);
                                $('#calendar').data('calendar').setDataSource(dataSource);
                            }
                        });

                    }
                },
                dayContextMenu: null,
                selectRange: null,
                mouseOnDay: function (e) {
                    var content = '';
                    if (e.events.length > 0) {

                        for (var i in e.events) {
                            content += '<div class="event-tooltip-content">'
                                + '<div class="event-name" style="color:' + e.events[i].color + '">' + e.events[i].name + '</div>'
                                + '<div class="event-location">' + e.events[i].location + '</div>'
                                + '<div class="text-muted">To remove this On-Call Clinic, left click on the date</div>'
                                + '</div>';
                        }

                        var existingPopover = bootstrap.Popover.getInstance(e.element);
                        if (existingPopover) { existingPopover.dispose(); }
                        new bootstrap.Popover(e.element, {
                            trigger: 'manual',
                            container: 'body',
                            html: true,
                            content: content
                        }).show();
                    }

                },
                mouseOutDay: function (e) {
                    if ($(".event-tooltip-content").is(":visible")) {
                        bootstrap.Popover.getInstance(e.element)?.hide();
                    }

                },
                renderEnd: null

            });

            $.getJSON("<%=request.getContextPath()%>/admin/oncallClinic.do?method=Load", function (data) {
                $('#calendar').data('calendar').setDataSource(data);
            });

            new bootstrap.Modal(document.getElementById('modal')).show();
        });
    </script>
</head>
<body>
<div id="calendar" class="calendar" oncontextmenu="return false;">
</div>
<div id="modal" class="modal fade" aria-labelledby="onCallModalTitle">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="modal-title" id="onCallModalTitle">Instructions For Using CARLOS On-Call Calendar</h4>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p>To set a date for the On-Call Clinic Calendar click on the date in the calendar and the date will
                    turn green</p>
                <p>To remove a date from the On-Call Clinic Calendar click on the green date</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            </div>
        </div><!-- /.modal-content -->
    </div><!-- /.modal-dialog -->
</div><!-- /.modal -->
</body>
</html>