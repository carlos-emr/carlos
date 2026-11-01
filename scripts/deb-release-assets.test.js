/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),{spawnSync}=require('node:child_process');
test('published upgrade package selection binds all assets and checksums to the requested release',()=>{
  const script=String.raw`
import copy,importlib.util,sys
spec=importlib.util.spec_from_file_location('assets',sys.argv[1]);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
tag='2026.08.0-beta2'
names=['carlos-emr_2026.08.0.beta2_amd64.deb','carlos-emr-drugref_2026.08.0.beta2_all.deb','carlos-ctl_1.2.1_all.deb']
release={'tag_name':tag,'draft':False,'assets':[{'name':name,'browser_download_url':f'https://github.com/carlos-emr/carlos/releases/download/{tag}/{name}','digest':'sha256:'+'a'*64} for name in names]}
assert len(module.select_packages(release,tag))==3
for mutation in ['tag','draft','duplicate','missing','url','digest','traversal']:
    changed=copy.deepcopy(release)
    if mutation=='tag':changed['tag_name']='2026.08.0-beta3'
    elif mutation=='draft':changed['draft']=True
    elif mutation=='duplicate':changed['assets'].append(copy.deepcopy(changed['assets'][0]))
    elif mutation=='missing':changed['assets'].pop()
    elif mutation=='url':changed['assets'][0]['browser_download_url']='https://other.invalid/evil.deb'
    elif mutation=='digest':changed['assets'][0]['digest']='md5:'+'a'*32
    elif mutation=='traversal':changed['assets'][0]['name']='../'+names[0]
    try:module.select_packages(changed,tag)
    except ValueError:pass
    else:raise AssertionError(mutation+' was accepted')
`;
  const result=spawnSync('python3',['-B','-c',script,path.join(__dirname,'lib/deb-release-assets.py')],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
});
test('cached package checksums work without Python 3.11 file_digest and close each input',()=>{
  const script=String.raw`
import hashlib,importlib.util,pathlib,sys,tempfile
spec=importlib.util.spec_from_file_location('assets',sys.argv[1]);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
if hasattr(hashlib,'file_digest'):del hashlib.file_digest
with tempfile.TemporaryDirectory() as folder:
    target=pathlib.Path(folder)/'cached.deb'
    target.write_bytes(b'owned cached package'*(1<<17))
    expected=hashlib.sha256(target.read_bytes()).hexdigest()
    for _ in range(4):assert module.sha256_file(target)==expected
    target.unlink()
`;
  const result=spawnSync('python3',['-B','-W','error::ResourceWarning','-c',script,path.join(__dirname,'lib/deb-release-assets.py')],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
});
