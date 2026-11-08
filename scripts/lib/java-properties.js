/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/** Decode the ISO-8859-1 stream format consumed by Properties.load(InputStream). */
function parseProperties(bytes) {
  const values=new Map();let logical='',continuing=false;
  const decode=text=>text.replace(/\\(u[\s\S]{0,4}|[\s\S])/g,(_match,escape)=>{
    if(escape[0]==='u') {
      if(!/^u[0-9a-fA-F]{4}$/.test(escape))throw new Error('Malformed Java properties Unicode escape');
      return String.fromCharCode(parseInt(escape.slice(1),16));
    }
    return ({t:'\t',r:'\r',n:'\n',f:'\f'})[escape]??escape;
  });
  function consume(line) {
    let split=0,escaped=false;
    for(;split<line.length;split++) {
      const char=line[split];
      if(!escaped&&/[=:\t\f ]/.test(char))break;
      escaped=char==='\\'&&!escaped;
    }
    let start=split;
    while(/[\t\f ]/.test(line[start]||'')&&start<line.length)start++;
    if(line[start]==='='||line[start]===':')start++;
    while(/[\t\f ]/.test(line[start]||'')&&start<line.length)start++;
    values.set(decode(line.slice(0,split)),decode(line.slice(start)));
  }
  for(const natural of bytes.toString('latin1').split(/\r\n|\r|\n/)) {
    const line=natural.replace(/^[\t\f ]+/,'');
    if(!continuing&&(!line||line[0]==='#'||line[0]==='!'))continue;
    logical+=line;
    const trailing=/\\+$/.exec(line)?.[0].length||0;
    continuing=trailing%2===1;
    if(continuing)logical=logical.slice(0,-1);
    else {consume(logical);logical='';}
  }
  if(continuing)consume(logical);
  return values;
}
module.exports={parseProperties};
