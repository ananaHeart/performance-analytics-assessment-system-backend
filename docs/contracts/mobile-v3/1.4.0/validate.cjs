'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const read=name=>JSON.parse(fs.readFileSync(path.join(__dirname,name),'utf8'));
let Ajv;
for(const name of [process.env.SMART_CONTRACT_AJV_PATH,'ajv',path.resolve(__dirname,'../../../../../../web-dashboard/node_modules/ajv')].filter(Boolean)){
 try{Ajv=require(name);break;}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}
}
if(!Ajv)throw new Error('An existing Ajv 6 installation is required.');
const doc=read('openapi.json');assert.equal(doc.openapi,'3.1.0');
const definitions=JSON.parse(JSON.stringify(doc.components.schemas).replaceAll('#/components/schemas/','#/definitions/'));
const parent=read('../1.0.0/schemas.json');for(const [key,value]of Object.entries(definitions))assert.deepEqual(value,parent.definitions[key]);
for(const route of Object.values(doc.paths)){assert.equal(route.get['x-production-enabled'],false);assert.equal(route.get.requestBody,undefined);}
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8});
const validators={};for(const name of ['ResultReadback','SyncReadback','AnalyticsResponse'])validators[name]=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});
let fixtures=0;
for(const [name,schema]of [['result-pending','ResultReadback'],['result-finalized','ResultReadback'],['result-reopened','ResultReadback'],['analytics-unavailable','AnalyticsResponse'],['analytics-ready-score-only','AnalyticsResponse'],['sync-page-partial','SyncReadback']]){
 const valid=validators[schema];assert.equal(valid(read('fixtures/'+name+'.json')),true,JSON.stringify(valid.errors));fixtures++;
}
const pending=read('fixtures/result-pending.json');assert.equal(pending.data.officialScore,null);
const reopened=read('fixtures/result-reopened.json');assert.equal(reopened.data.officialScore,null);
const unavailable=read('fixtures/analytics-unavailable.json');assert.equal(unavailable.data.metrics,null);
const ready=read('fixtures/analytics-ready-score-only.json');assert.equal(ready.data.status,'ready');assert.equal(ready.data.unavailableModules.length,4);
const invalid=structuredClone(ready);invalid.data.metrics.mastery={percent:0};assert.equal(validators.AnalyticsResponse(invalid),false);
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtimeSamples=0;
if(process.argv.includes('--runtime-samples')){
 const folder=path.resolve(__dirname,'../../../../target/mobile-readback-contract-samples');
 for(const [name,schema]of [['result','ResultReadback'],['analytics','AnalyticsResponse'],['sync','SyncReadback']]){
  const value=JSON.parse(fs.readFileSync(path.join(folder,name+'.json'),'utf8'));const valid=validators[schema];assert.equal(valid(value),true,JSON.stringify(valid.errors));runtimeSamples++;
 }
}
console.log(JSON.stringify({status:'PASS',packVersion:'1.4.0',schemas:Object.keys(definitions).length,fixtures,runtimeSamplesChecked:runtimeSamples,productionEnabled:false},null,2));
