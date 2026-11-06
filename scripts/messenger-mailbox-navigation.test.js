/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/messenger/DisplayMessages.jsp'), 'utf8');

// Render the actual native-tab href, including its JSP URI-component expressions. Unknown
// expressions fail closed so a changed template cannot make this fixture silently stop testing it.
function nativeTab(boxType, orderby, filter, scheduleNav) {
    const template = source.match(new RegExp('href="([^"\\n]*DisplayMessages\\?boxType='
        + boxType + '[^"\\n]*)"'));
    assert.ok(template, 'native mailbox tab exists');
    const expressions = {
        scheduleNavQuerySuffix: scheduleNav ? '&amp;scheduleNav=1' : '',
        inboxFilterSuffix: '&amp;searchString=' + encodeURIComponent(filter),
        'SafeEncode.forUriComponent(orderby)': encodeURIComponent(orderby)
    };
    const rendered = template[1].replace('${pageContext.request.contextPath}', '/carlos')
        .replace(/<%=\s*([^%]+?)\s*%>/g, (_, expression) => {
            assert.ok(Object.hasOwn(expressions, expression), 'known JSP expression: ' + expression);
            return expressions[expression];
        }).replaceAll('&amp;', '&');
    return new URL(rendered, 'https://clinic.test');
}

for (const boxType of [0, 1, 2]) {
    for (const orderby of ['subject', '!subject', '!date']) {
        test(`native mailbox ${boxType} retains ${orderby} and exact search/schedule state`, () => {
            const filter = 'FAKE patient & orderby=!from + café #?="';
            const url = nativeTab(boxType, orderby, filter, true);
            assert.equal(url.pathname, '/carlos/messenger/DisplayMessages');
            assert.deepEqual([...url.searchParams], [
                ['boxType', String(boxType)], ['scheduleNav', '1'],
                ['searchString', filter], ['orderby', orderby]
            ]);
            assert.equal(url.hash, '');
        });
    }
}

test('moving through Sent, Deleted and Inbox keeps the selected descending Subject order', () => {
    let orderby = '!subject';
    for (const boxType of [1, 2, 0]) {
        const url = nativeTab(boxType, orderby, '', false);
        orderby = url.searchParams.get('orderby') || '!date';
        assert.equal(orderby, '!subject');
        assert.equal(url.searchParams.has('scheduleNav'), false);
        assert.equal(url.searchParams.get('searchString'), '');
    }
});
