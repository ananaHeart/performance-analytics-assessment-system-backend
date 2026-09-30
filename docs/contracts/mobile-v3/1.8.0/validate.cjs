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
for(const path of Object.values(doc.paths))for(const operation of Object.values(path)){
 assert.equal(operation['x-production-enabled'],false);assert.equal(operation.requestBody,undefined);
 assert.equal(operation['x-required-draft-migrations'].at(-1),'V3_020');
}
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8}),validators={};
for(const name of ['FinalizationResponse','ResultReadback','AnalyticsResponse','SyncReadback'])validators[name]=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});
const cases=[['finalized','FinalizationResponse'],['replayed','FinalizationResponse'],['result','ResultReadback'],['analytics','AnalyticsResponse']];
function check(folder){
 for(const [name,schema]of cases){const value=JSON.parse(fs.readFileSync(path.join(folder,name+'.json'),'utf8'));const valid=validators[schema];assert.equal(valid(value),true,JSON.stringify(valid.errors));}
}
check(path.join(__dirname,'fixtures'));
const finalized=read('fixtures/finalized.json').data,replayed=read('fixtures/replayed.json').data;
assert.equal(finalized.totalScore,23.5);assert.equal(finalized.maxScore,28);assert.equal(finalized.percentage,83.93);
assert.equal(finalized.scoreChanged,true);assert.equal(replayed.scoreChanged,false);
// The replay sample comes from a separate synthetic test result.
assert.equal(replayed.totalScore,23.5);assert.equal(replayed.maxScore,28);assert.equal(replayed.percentage,83.93);
const result=read('fixtures/result.json').data,analytics=read('fixtures/analytics.json').data;
assert.equal(result.resultStatus,'finalized');assert.equal(analytics.status,'ready');assert.equal(analytics.unavailableModules.length,4);
assert.deepEqual({...finalized,scoreChanged:false},result.officialScore);
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtimeSamples=0;
if(process.argv.includes('--runtime-samples')){check(path.resolve(__dirname,'../../../../target/mobile-written-finalization-samples'));runtimeSamples=cases.length;}
console.log(JSON.stringify({status:'PASS',packVersion:'1.8.0',schemas:Object.keys(definitions).length,fixtures:cases.length,runtimeSamplesChecked:runtimeSamples,productionEnabled:false},null,2));
