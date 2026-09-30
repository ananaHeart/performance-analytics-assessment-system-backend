'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const read=name=>JSON.parse(fs.readFileSync(path.join(__dirname,name),'utf8'));
let Ajv;
for(const name of [process.env.SMART_CONTRACT_AJV_PATH,'ajv',path.resolve(__dirname,'../../../../../../web-dashboard/node_modules/ajv')].filter(Boolean)){
 try{Ajv=require(name);break;}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}
}
if(!Ajv)throw Error('An existing Ajv 6 installation is required.');
const doc=read('openapi.json');assert.equal(doc.openapi,'3.1.0');assert.equal(doc['x-mobile-production-enabled'],false);
const definitions=JSON.parse(JSON.stringify(doc.components.schemas).replaceAll('#/components/schemas/','#/definitions/'));
const parent=read('../1.0.0/schemas.json');for(const [key,value]of Object.entries(definitions))assert.deepEqual(value,parent.definitions[key]);
for(const path of Object.values(doc.paths))for(const operation of Object.values(path)){assert.equal(operation['x-production-enabled'],false);assert.equal(operation['x-required-draft-migrations'].at(-1),'V3_021');}
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8}),validators={};
for(const name of ['ReopenRequest','LifecycleAck','ResultReadback','AnalyticsResponse','SyncReadback'])validators[name]=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});
const cases=[['request','ReopenRequest'],['created','LifecycleAck'],['replayed','LifecycleAck'],['result','ResultReadback'],['analytics','AnalyticsResponse'],['sync','SyncReadback']];
function check(folder){
 const values={};
 for(const [name,schema]of cases){const value=JSON.parse(fs.readFileSync(path.join(folder,name+'.json'),'utf8'));const valid=validators[schema];assert.equal(valid(value),true,JSON.stringify(valid.errors));values[name]=value;}
 const {request,created,replayed,result,analytics,sync}=values;
 assert(request.reasonCode.trim());assert.equal(created.data.revision,request.expectedRevision+1);assert.equal(created.data.scoreVersion,request.expectedScoreVersion);
 assert.equal(created.data.disposition,'created');assert.deepEqual({...created.data,disposition:'replayed'},replayed.data);
 assert.equal(result.data.resultUuid,created.data.resultUuid);assert.equal(result.data.revision,created.data.revision);
 assert.equal(result.data.resultStatus,'pending_verification');assert.equal(result.data.officialScore,null);assert(result.data.pendingReasons.includes('RESULT_REOPENED'));
 assert.equal(analytics.data.status,'stale');assert.equal(analytics.data.reasonCode,'RESULT_REOPENED');assert.equal(analytics.data.metrics,null);
 assert.equal(sync.data.syncUuid,request.syncUuid);assert.equal(sync.data.syncStatus,'success');assert.equal(sync.data.items.length,1);
 assert.equal(sync.data.items[0].resultUuid,result.data.resultUuid);assert.equal(sync.data.items[0].pageOutcomes.length,0);
}
check(path.join(__dirname,'fixtures'));
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtimeSamples=0;
if(process.argv.includes('--runtime-samples')){check(path.resolve(__dirname,'../../../../target/mobile-reopen-samples'));runtimeSamples=cases.length;}
console.log(JSON.stringify({status:'PASS',packVersion:'1.9.0',schemas:Object.keys(definitions).length,fixtures:cases.length,runtimeSamplesChecked:runtimeSamples,productionEnabled:false},null,2));
