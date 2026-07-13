//    This script and many more are available free online at
//    The JavaScript Source!! http://javascript.internet.com
//    Original:  Patrick Lewis (gtrwiz@aol.com)
//    Web Site:  http://www.patricklewis.net


let mouseOverX;
let mouseOverY;
var overdiv = "0";

//  #########  CREATES POP UP BOXES
function popLayer(content) {

    pad = "1";
    bord = "0";

    desc = "<table cellspacing=0 cellpadding=" + pad + " border=" + bord + "  bgcolor=000000><tr><td>\n"
        + "<table cellspacing=0 cellpadding=3 border=0 width=100%><tr><td bgcolor=ffffdd><center><font size=-1>\n"
        + content
        + "\n</td></tr></table>\n"
        + "</td></tr></table>";

    document.getElementById("object1").innerHTML = desc;
    document.getElementById("object1").style.left = mouseOverX + 15;
    document.getElementById("object1").style.top = mouseOverY - 5;
}

function hideLayer() {
    if (overdiv == "0") {
        document.getElementById("object1").style.top = "-500";
    }
}

//  ########  TRACKS MOUSE POSITION FOR POPUP PLACEMENT

function handlerMM(e) {
    mouseOverX = event.clientX + document.body.scrollLeft;
    mouseOverY = event.clientY + document.body.scrollTop;
}


document.captureEvents(Event.MOUSEMOVE);
document.onmousemove = handlerMM;
