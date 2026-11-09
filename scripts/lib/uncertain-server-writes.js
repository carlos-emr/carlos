/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const owners=new WeakSet(),pending=new WeakMap();
function owner(context){return typeof context?.browser==='function'?(context.browser()||context):context;}
function retainFixtures(context){const key=owner(context);if(key&&typeof key==='object')owners.add(key);}
function hasUncertainWrites(context){const key=owner(context);return !!key&&owners.has(key);}
function trackWrite(context,operation){const key=owner(context);let writes=pending.get(key);if(!writes){writes=new Set();pending.set(key,writes);}writes.add(operation);operation.then(()=>writes.delete(operation),()=>writes.delete(operation));}
// eslint-disable-next-line unicorn/no-useless-spread -- Snapshot the owned batch before Promise assimilation can mutate pending writes.
async function drainWrites(context){const key=owner(context);if(!key)return;const writes=pending.get(key);while(writes?.size)await Promise.allSettled([...writes]);}
module.exports={retainFixtures,hasUncertainWrites,trackWrite,drainWrites};
