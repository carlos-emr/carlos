/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),{parse}=require('acorn');
const scanned=new Map();
const ENTRY_POINTS=new Set(['runCheck','runWorkflow','installCleanupSignalHandlers','createGracefulSignalCancellation','runLegacyCheck']);
function hasSignalEntryPoint(file,seen=new Set(),isRoot=true) {
  file=path.resolve(file);const key=file+'|'+isRoot;if(seen.has(key))return false;seen.add(key);
  if(scanned.has(key)) {
    const cached=scanned.get(key);
    return cached.direct || cached.dependencies.some(name=>dependencyHasEntry(file,name,seen));
  }
  const tree=parse(fs.readFileSync(file,'utf8'),{ecmaVersion:'latest',sourceType:'script',allowHashBang:true});
  let direct=false;const dependencies=[];
  function visit(node) {
    if(!node||typeof node!=='object')return;
    // A helper import does not execute the imported browser script's CLI.
    if(!isRoot&&node.type==='IfStatement'&&isMainGuard(node.test)){visit(node.alternate);return;}
    if(node.type==='CallExpression') {
      const called=node.callee.type==='Identifier'?node.callee.name:node.callee.type==='MemberExpression'&&!node.callee.computed?node.callee.property.name:'';
      if(ENTRY_POINTS.has(called))direct=true;
      if(node.callee.type==='Identifier'&&node.callee.name==='require'&&node.arguments[0]?.type==='Literal') {
        const name=node.arguments[0].value;
        if(typeof name==='string'&&name.startsWith('.'))dependencies.push(name);
      }
    }
    for(const value of Object.values(node))if(Array.isArray(value))value.forEach(visit);else if(value&&typeof value==='object')visit(value);
  }
  visit(tree);scanned.set(key,{direct,dependencies});if(direct)return true;
  return dependencies.some(name=>dependencyHasEntry(file,name,seen));
}
function dependencyHasEntry(file,name,seen) {
  let dependency=path.resolve(path.dirname(file),name);
  if(!path.extname(dependency))dependency+='.js';
  return /\.(?:js|cjs)$/.test(dependency)&&fs.existsSync(dependency)&&hasSignalEntryPoint(dependency,seen,false);
}
function isMainGuard(node) {
  if(!node)return false;
  if(node.type==='LogicalExpression'&&node.operator==='&&')return isMainGuard(node.left)||isMainGuard(node.right);
  const matches=(member,module)=>member?.type==='MemberExpression'&&member.object?.name==='require'
    &&member.property?.name==='main'&&module?.name==='module';
  return node.type==='BinaryExpression'&&['===','=='].includes(node.operator)
    &&(matches(node.left,node.right)||matches(node.right,node.left));
}
module.exports={hasSignalEntryPoint};
