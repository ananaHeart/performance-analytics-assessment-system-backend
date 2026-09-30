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
for(const path of Object.values(doc.paths))for(const operation of Object.values(path)){assert.equal(operation['x-production-enabled'],false);assert.equal(operation['x-required-draft-migrations'].at(-1),'V3_023');}
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8}),validators={};
for(const name of ['SupersedeRequest','LifecycleAck','ResultReadback','AnalyticsResponse','SyncReadback','ScanMetadata','ScanResponse'])validators[name]=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});
const cases=[['request','SupersedeRequest'],['created','LifecycleAck'],['replayed','LifecycleAck'],['link','LifecycleAck'],['result','ResultReadback'],['analytics','AnalyticsResponse'],['replacement','ResultReadback'],['sync','SyncReadback'],['rescan-request','ScanMetadata'],['rescan-response','ScanResponse'],['rescan-result','ResultReadback']];
function check(folder){
 const values={};
 for(const [name,schema] of cases){const value=JSON.parse(fs.readFileSync(path.join(folder,name+'.json'),'utf8'));const validate=validators[schema];assert.equal(validate(value),true,name+': '+JSON.stringify(validate.errors));values[name]=value;}
 const {request,created,replayed,link,result,analytics,replacement,sync}=values;
 assert(request.reasonCode.trim());assert.equal(created.data.revision,request.expectedRevision+1);assert.equal(created.data.scoreVersion,request.expectedScoreVersion);
 assert.equal(created.data.resultStatus,'superseded');assert.equal(created.data.replacementResultUuid,request.replacementResultUuid);
 assert.deepEqual({...created.data,disposition:'replayed'},replayed.data);assert.deepEqual(created.data,link.data);
 assert.equal(result.data.resultUuid,created.data.resultUuid);assert.equal(result.data.revision,created.data.revision);assert.equal(result.data.officialScore,null);assert.equal(result.data.resultStatus,'superseded');
 assert.equal(analytics.data.status,'stale');assert.equal(analytics.data.reasonCode,'RESULT_SUPERSEDED');assert.equal(analytics.data.metrics,null);
 assert.equal(replacement.data.resultUuid,request.replacementResultUuid);assert.equal(replacement.data.resultStatus,'finalized');assert.equal(replacement.data.analytics.status,'ready');
 assert.equal(sync.data.syncUuid,request.syncUuid);assert.equal(sync.data.syncStatus,'success');assert.equal(sync.data.items.length,1);assert.equal(sync.data.items[0].pageOutcomes.length,0);
 const scan=values['rescan-request'],response=values['rescan-response'].data,current=values['rescan-result'].data;
 assert.equal(scan.captureNumber,2);assert.equal(scan.scanPageUuid,response.scanPageUuid);assert.equal(response.contentHash,scan.imageHash);assert.equal(response.pageStatus,'captured');assert.equal(response.uploadStatus,'created');
 assert.equal(current.resultStatus,'finalized');assert.equal(current.pages.filter(p=>p.pageStatus==='superseded').length,1);assert.equal(current.pages.filter(p=>p.pageStatus==='accepted').length,1);
}
check(path.join(__dirname,'fixtures'));
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtimeSamples=0;
if(process.argv.includes('--runtime-samples')){check(path.resolve(__dirname,'../../../../target/mobile-supersede-samples'));runtimeSamples=cases.length;}
console.log(JSON.stringify({status:'PASS',packVersion:'1.11.0',schemas:Object.keys(definitions).length,fixtures:cases.length,runtimeSamplesChecked:runtimeSamples,productionEnabled:false},null,2));
