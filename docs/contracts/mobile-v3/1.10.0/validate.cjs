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
const parent=read('../1.0.0/schemas.json');
const written=read('../1.7.0/openapi.json').components.schemas;
for(const [key,value] of Object.entries(definitions)){
 if(parent.definitions[key])assert.deepEqual(value,parent.definitions[key]);
 else if(written[key])assert.deepEqual(value,JSON.parse(JSON.stringify(written[key]).replaceAll('#/components/schemas/','#/definitions/')));
 else assert(['CorrectionObjective','CorrectionAnswer','CorrectionRequest'].includes(key));
}
for(const item of Object.values(doc.paths))for(const operation of Object.values(item)){assert.equal(operation['x-production-enabled'],false);assert.equal(operation['x-required-draft-migrations'].at(-1),'V3_022');}
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8}),validators={};
for(const name of ['CorrectionRequest','FinalizationResponse','ResultReadback','AnalyticsResponse','SyncReadback'])validators[name]=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});
const cases=[['request','CorrectionRequest'],['created','FinalizationResponse'],['replayed','FinalizationResponse'],['result','ResultReadback'],['analytics','AnalyticsResponse'],['sync','SyncReadback'],['written-request','CorrectionRequest'],['written-result','FinalizationResponse']];
function check(folder){
 const values={};
 for(const [name,schema]of cases){const value=JSON.parse(fs.readFileSync(path.join(folder,name+'.json'),'utf8'));const valid=validators[schema];assert.equal(valid(value),true,name+': '+JSON.stringify(valid.errors));values[name]=value;}
 const {request,created,replayed,result,analytics,sync}=values;
 assert.equal(created.data.scoreVersion,request.expectedScoreVersion+1);assert.equal(created.data.resultStatus,'finalized');assert.equal(created.data.scoreChanged,true);
 assert.deepEqual({...created.data,scoreChanged:false},replayed.data);
 assert.equal(result.data.resultUuid,created.data.resultUuid);assert.equal(result.data.revision,request.expectedRevision+1);
 assert.equal(result.data.resultStatus,'finalized');assert.equal(result.data.scoreVersion,created.data.scoreVersion);assert.equal(result.data.officialScore.totalScore,created.data.totalScore);
 assert.equal(analytics.data.status,'ready');assert.equal(analytics.data.scoreVersion,created.data.scoreVersion);assert.equal(analytics.data.metrics.totalScore,created.data.totalScore);
 assert.equal(sync.data.syncUuid,request.syncUuid);assert.equal(sync.data.syncStatus,'success');assert.equal(sync.data.items.length,1);assert.equal(sync.data.items[0].resultUuid,result.data.resultUuid);assert.equal(sync.data.items[0].pageOutcomes.length,0);
 assert.equal(values['written-result'].data.scoreVersion,values['written-request'].expectedScoreVersion+1);
 // Contract rejects client-supplied objective points and ambiguous objective decisions.
 const bad=structuredClone(request);bad.answers[0].evaluation.points=1;assert.equal(validators.CorrectionRequest(bad),false);
 const partial=structuredClone(request);partial.answers=[];assert.equal(validators.CorrectionRequest(partial),false);
}
check(path.join(__dirname,'fixtures'));
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtimeSamples=0;
if(process.argv.includes('--runtime-samples')){check(path.resolve(__dirname,'../../../../target/mobile-correction-samples'));runtimeSamples=cases.length;}
console.log(JSON.stringify({status:'PASS',packVersion:'1.10.0',schemas:Object.keys(definitions).length,fixtures:cases.length,runtimeSamplesChecked:runtimeSamples,productionEnabled:false},null,2));
