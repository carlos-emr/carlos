/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),{parse}=require('acorn');
const scanned=new Map();
const ENTRY_POINTS=new Set(['runCheck','runWorkflow','installCleanupSignalHandlers','createGracefulSignalCancellation','runLegacyCheck']);
const STRUCTURED_ENTRY_POINTS=new Set(['runCheck','runWorkflow','runLegacyCheck']);
const isFunction=node=>node&&['FunctionDeclaration','FunctionExpression','ArrowFunctionExpression'].includes(node.type);
function hasSignalEntryPoint(file,seen=new Set(),isRoot=true,entryPoints=ENTRY_POINTS) {
  file=path.resolve(file);const key=file+'|'+isRoot+'|'+[...entryPoints].join(',');if(seen.has(key))return false;seen.add(key);
  if(scanned.has(key)){const cached=scanned.get(key);return cached.direct||cached.dependencies.some(name=>dependencyHasEntry(file,name,seen,entryPoints));}
  const tree=parse(fs.readFileSync(file,'utf8'),{ecmaVersion:'latest',sourceType:'script',allowHashBang:true});
  const scopes=new WeakMap(),root={parent:null,bindings:new Map()},visitedFunctions=new Set(),dependencies=[];
  let direct=false;
  function bind(pattern,scope,binding) {
    if(!pattern)return;
    if(pattern.type==='Identifier'){scope.bindings.set(pattern.name,binding);return;}
    if(pattern.type==='RestElement'){bind(pattern.argument,scope,binding);return;}
    if(pattern.type==='AssignmentPattern'){bind(pattern.left,scope,binding);return;}
    if(pattern.type==='ObjectPattern')for(const property of pattern.properties){
      const importedName=property.key?.name??property.key?.value;
      bind(property.value||property.argument,scope,binding.kind==='import'?{...binding,importedName}:binding);
    }
    if(pattern.type==='ArrayPattern')for(const element of pattern.elements)bind(element,scope,binding);
  }
  function children(node,operation){for(const value of Object.values(node))if(Array.isArray(value))value.forEach(operation);else if(value&&typeof value==='object')operation(value);}
  function build(node,scope,functionScope) {
    if(!node||typeof node!=='object')return;
    if(isFunction(node)) {
      if(node.type==='FunctionDeclaration')bind(node.id,scope,{fn:node});
      const local={parent:scope,bindings:new Map()};scopes.set(node,local);
      if(node.id)bind(node.id,local,{fn:node});for(const parameter of node.params)bind(parameter,local,{kind:'parameter'});
      build(node.body,local,local);return;
    }
    if(['BlockStatement','ForStatement','ForInStatement','ForOfStatement','SwitchStatement','CatchClause'].includes(node.type)) {
      scope={parent:scope,bindings:new Map()};if(node.type==='CatchClause')bind(node.param,scope,{kind:'parameter'});
    }
    scopes.set(node,scope);
    if(node.type==='VariableDeclaration')for(const declaration of node.declarations){
      const imported=declaration.init?.type==='CallExpression'&&declaration.init.callee.name==='require';
      bind(declaration.id,node.kind==='var'?functionScope:scope,isFunction(declaration.init)?{fn:declaration.init}:{kind:imported?'import':'local'});
    }
    if(node.type==='ClassDeclaration')bind(node.id,scope,{kind:'local'});
    children(node,child=>build(child,scope,functionScope));
  }
  build(tree,root,root);
  function resolve(name,scope){for(let current=scope;current;current=current.parent)if(current.bindings.has(name))return current.bindings.get(name);return null;}
  function invoke(fn){if(visitedFunctions.has(fn))return;visitedFunctions.add(fn);visit(fn.body);}
  function visit(node) {
    if(!node||typeof node!=='object'||isFunction(node))return;
    if(!isRoot&&node.type==='IfStatement'&&isMainGuard(node.test)){visit(node.alternate);return;}
    if(node.type==='CallExpression') {
      const callee=node.callee,called=callee.type==='Identifier'?callee.name:callee.type==='MemberExpression'&&!callee.computed?callee.property.name:'';
      const binding=callee.type==='Identifier'?resolve(called,scopes.get(node)):null;
      if((entryPoints.has(called)&&(!binding||binding.kind==='import'))
        ||(binding?.kind==='import'&&entryPoints.has(binding.importedName)))direct=true;
      if(binding?.fn)invoke(binding.fn);
      if(isFunction(callee))invoke(callee);
      if(callee.type==='Identifier'&&callee.name==='require'&&node.arguments[0]?.type==='Literal'){
        const name=node.arguments[0].value;if(typeof name==='string'&&name.startsWith('.'))dependencies.push(name);
      }
    }
    children(node,visit);
  }
  visit(tree);scanned.set(key,{direct,dependencies});return direct||dependencies.some(name=>dependencyHasEntry(file,name,seen,entryPoints));
}
function dependencyHasEntry(file,name,seen,entryPoints) {
  let dependency=path.resolve(path.dirname(file),name);if(!path.extname(dependency))dependency+='.js';
  return /\.(?:js|cjs)$/.test(dependency)&&fs.existsSync(dependency)&&hasSignalEntryPoint(dependency,seen,false,entryPoints);
}
function isMainGuard(node) {
  if(!node)return false;
  if(node.type==='LogicalExpression'&&node.operator==='&&')return isMainGuard(node.left)||isMainGuard(node.right);
  const matches=(member,module)=>member?.type==='MemberExpression'&&member.object?.name==='require'&&member.property?.name==='main'&&module?.name==='module';
  return node.type==='BinaryExpression'&&['===','=='].includes(node.operator)&&(matches(node.left,node.right)||matches(node.right,node.left));
}
const hasStructuredEntryPoint=file=>hasSignalEntryPoint(file,new Set(),true,STRUCTURED_ENTRY_POINTS);
module.exports={hasSignalEntryPoint,hasStructuredEntryPoint};
