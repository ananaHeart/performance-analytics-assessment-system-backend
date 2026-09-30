'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const read=name=>JSON.parse(fs.readFileSync(path.join(__dirname,name),'utf8'));
let Ajv;
for(const name of [process.env.SMART_CONTRACT_AJV_PATH,'ajv',path.resolve(__dirname,'../../../../../../web-dashboard/node_modules/ajv')].filter(Boolean)) {
  try { Ajv=require(name);break; } catch(e) { if(e.code!=='MODULE_NOT_FOUND')throw e; }
}
if(!Ajv)throw new Error('An existing Ajv 6 installation is required.');
const document=read('openapi.json');assert.equal(document.openapi,'3.1.0');
const operation=document.paths['/api/v3/scoring/results/{testResultId}/finalize'].post;
assert.equal(operation.requestBody,undefined);assert.equal(operation['x-mobile-production-enabled'],false);
const definitions=JSON.parse(JSON.stringify(document.components.schemas).replaceAll('#/components/schemas/','#/definitions/'));
const parent=read('../1.0.0/schemas.json');
for(const [key,value] of Object.entries(definitions))assert.deepEqual(value,parent.definitions[key]);
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8});
const validate=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/FinalizationResponse'});
const created=read('fixtures/finalized-created.json'),replay=read('fixtures/finalized-replayed.json');
for(const fixture of [created,replay])assert.equal(validate(fixture),true,JSON.stringify(validate.errors));
assert.equal(validate(read('fixtures/invalid-extra-score.json')),false);
assert.deepEqual({...created.data,scoreChanged:false},replay.data);
assert.equal(created.data.totalScore/created.data.maxScore*100,created.data.percentage);
for(const ref of JSON.stringify(document).matchAll(/"\$ref":"(#[^"]+)"/g)) {
  let target=document;for(const segment of ref[1].slice(2).split('/'))target=target?.[segment];assert(target,'Unresolved '+ref[1]);
}
console.log(JSON.stringify({status:'PASS',packVersion:'1.3.0',schemas:3,fixtures:3,mobileProductionEnabled:false,runtimeTestsExecuted:false},null,2));
