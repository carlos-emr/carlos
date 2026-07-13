// Copyright � 2000 by Apple Computer, Inc., All Rights Reserved.
//
// You may incorporate this Apple sample code into your own code
// without restriction. This Apple sample code has been provided "AS IS"
// and the responsibility for its operation is yours. You may redistribute
// this code, but you are not permitted to redistribute it as
// "Apple sample code" after having made changes.
//
// ************************
// layer utility routines *
// ************************

function getStyleObject(objectId) {
    // get an object's style object given its id
    var el = document.getElementById(objectId);
    if (el) {
        return el.style;
    } else {
        return false;
    }
} // getStyleObject

function changeObjectVisibility(objectId, newVisibility) {
    // get a reference to the cross-browser style object and make sure the object exists
    var styleObject = getStyleObject(objectId);
    if (styleObject) {
        styleObject.visibility = newVisibility;
        return true;
    } else {
        // we couldn't find the object, so we can't change its visibility
        return false;
    }
} // changeObjectVisibility

function moveObject(objectId, newXCoordinate, newYCoordinate) {
    // get a reference to the cross-browser style object and make sure the object exists
    var styleObject = getStyleObject(objectId);
    if (styleObject) {
        styleObject.left = newXCoordinate;
        styleObject.top = newYCoordinate;
        return true;
    } else {
        // we couldn't find the object, so we can't very well move it
        return false;
    }
} // moveObject
