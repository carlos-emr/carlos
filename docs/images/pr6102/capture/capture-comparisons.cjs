'use strict';
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {chromium}=require('/workspace/carlos/node_modules/playwright');
const root='/workspace/carlos';
const out=path.join(root,'docs/images/pr6102');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
(async()=>{
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const manifest={baseline:'9004536dc8314d5f50542b48905ca3ca4099a916',candidate:'9b0bb15c9f2',browser:browser.version(),jdk:'Temurin 25.0.3',scope:'Actual Java chart configurations rendered with each revision\'s bundled scripts; these are renderer fixtures, not installed native UI qualification.',captures:[]};
 try {
  for(const phase of ['before','after']) for(const locale of ['en','fr']) {
   const scripts=phase==='before'?'/workspace/work/measurement-before/src/main/webapp/library/chartjs':path.join(root,'src/main/webapp/library/chartjs');
   const models=path.join(out,'configs',phase+'-'+locale);
   for(const file of fs.readdirSync(models).filter(f=>f.endsWith('.json')).sort()) {
    const page=await browser.newPage({viewport:{width:1000,height:600},deviceScaleFactor:1});
    await page.setContent('<!doctype html><html><body></body></html>');
    await page.addScriptTag({path:path.join(scripts,'chart.umd.min.js')});
    await page.addScriptTag({path:path.join(scripts,'carlos-chart-draw.js')});
    const configPath=path.join(models,file);const config=JSON.parse(fs.readFileSync(configPath));
    const error=await page.evaluate(spec=>{try{window.carlosDrawChart(spec,1000,600);return null;}catch(e){return e.message;}},config);
    if(error) await page.evaluate(message=>{const text=document.createElement('pre');text.textContent='Renderer failure: '+message;text.style.cssText='position:fixed;top:0;left:0;background:#fff;color:#900;padding:12px;font:16px sans-serif';document.body.append(text);},error);
    const name='edge-'+locale+'-'+file.replace('.json','.png');
    const dest=path.join(out,phase,name);await page.screenshot({path:dest});
    manifest.captures.push({phase,locale:locale==='fr'?'fr_CA':'en_CA',case:file.replace('.json',''),image:phase+'/'+name,imageSha256:sha(dest),config:'configs/'+phase+'-'+locale+'/'+file,configSha256:sha(configPath),drawScriptSha256:sha(path.join(scripts,'carlos-chart-draw.js')),error});
    await page.close();
    if(phase==='after'&&error)throw Error('Candidate render failed: '+name+': '+error);
   }
  }
  fs.writeFileSync(path.join(out,'comparison-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  console.log(JSON.stringify({captures:manifest.captures.length,browser:manifest.browser,baselineFailures:manifest.captures.filter(c=>c.phase==='before'&&c.error).length,candidateFailures:manifest.captures.filter(c=>c.phase==='after'&&c.error).length}));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
