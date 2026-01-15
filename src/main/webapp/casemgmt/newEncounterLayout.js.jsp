<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada

--%>
    <%@page contentType="text/javascript; charset=UTF-8" pageEncoding="UTF-8"%>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
    <c:set var="ctx" value="${pageContext.request.contextPath}"	scope="request" />

    Messenger.options = {
        delay: 10,
        extraClasses: 'messenger-fixed messenger-on-top messenger-on-left',
        theme: 'future'
    };

// global message
    var msg;


//////Timer
    var d = new Date();  //the start

    var totalSeconds = 0;
    var myVar = setInterval(setTime, 1000);
    var toggle = true;

    function toggleATimer(e) {
        const pause = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-pause-fill" viewBox="0 0 16 16">' +
            '<path d="M5.5 3.5A1.5 1.5 0 0 1 7 5v6a1.5 1.5 0 0 1-3 0V5a1.5 1.5 0 0 1 1.5-1.5m5 0A1.5 1.5 0 0 1 12 5v6a1.5 1.5 0 0 1-3 0V5a1.5 1.5 0 0 1 1.5-1.5"></path>' +
            '</svg>';
        const play = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-play-fill" viewBox="0 0 16 16">' +
            '<path d="m11.596 8.697-6.363 3.692c-.54.313-1.233-.066-1.233-.697V4.308c0-.63.692-1.01 1.233-.696l6.363 3.692a.802.802 0 0 1 0 1.393"></path>' +
            '</svg>';
        if (toggle) {
            e.innerHTML = play;
            clearInterval(myVar);
            toggle = false;
        } else {
            e.innerHTML = pause;
            myVar = setInterval(setTime, 1000);
            toggle = true;
        }
    }

    function pasteTimer() {
        var ed = new Date();
        const timerNote = document.getElementById("timer-note");
        let note = "";
        if (timerNote.value) {
            note = "\n" + timerNote.value;
        }
        $(caseNote).value += "\n"
            + note
            + "\n" + document.getElementById("startTag").value + ": "
            + d.getHours() + ":" + pad(d.getMinutes()) + "\n"
            + document.getElementById("endTag").value + ": "
            + ed.getHours() + ":" + pad(ed.getMinutes()) + "\n"
            + pad(parseInt(totalSeconds / 3600)) + ":"
            + pad(parseInt((totalSeconds / 60) % 60)) + ":"
            + pad(totalSeconds % 60);
        adjustCaseNote();
        timerNote.value = '';
    }

    function setTime() {
        ++totalSeconds;
        const aTimerButton = document.getElementById("aTimer");
        if (totalSeconds > 5) {
            aTimerButton.innerHTML = pad(parseInt(totalSeconds / 60)) + ":" + pad(totalSeconds % 60);
        }
        if (totalSeconds === 1200) {
            aTimerButton.style.background = "#DFF0D8";
        } //1200 sec = 20 min light green
        if (totalSeconds === 3000) {
            aTimerButton.style.background = "#FDFEC7";
        } //3600 sec = 50 min light yellow
    }

    function pad(val) {
        var valString = val + "";
        if (valString.length < 2) {
            return "0" + valString;
        } else {
            return valString;
        }
    }
