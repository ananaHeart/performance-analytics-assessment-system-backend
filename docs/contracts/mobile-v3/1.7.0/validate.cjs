'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const read=name=>JSON.parse(fs.readFileSync(path.join(__dirname,name),'utf8'));
let Ajv;for(const name of [process.env.SMART_CONTRACT_AJV_PATH,'ajv',path.resolve(__dirname,'../../../../../../web-dashboard/node_modules/ajv')].filter(Boolean)){try{Ajv=require(name);break;}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}}
if(!Ajv)throw new Error('Existing Ajv is required.');
const doc=read('openapi.json'),parent=read('../1.0.0/schemas.json'),definitions=JSON.parse(JSON.stringify(doc.components.schemas).replaceAll('#/components/schemas/','#/definitions/'));
for(const [key,value]of Object.entries(definitions))if(parent.definitions[key])assert.deepEqual(value,parent.definitions[key]);
assert.equal(doc.paths['/api/v3/mobile/verification-batches'].post['x-production-enabled'],false);assert.equal(definitions.WrittenBatch.properties.contractVersion.const,'3.1');
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8});
function check(name,value){const validate=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});assert(validate(value),JSON.stringify(validate.errors));}
for(const name of ['manual','rubric'])check('WrittenBatch',read('fixtures/'+name+'-request.json'));
check('VerificationResponse',read('fixtures/partial-response.json'));check('EvaluationReference',read('fixtures/evaluation-reference.json'));
const missing=read('fixtures/manual-request.json');delete missing.items[0].evaluationReferenceHash;assert.throws(()=>check('WrittenBatch',missing));
const old=read('fixtures/manual-request.json');old.contractVersion='3.0';assert.throws(()=>check('WrittenBatch',old));
const blank=read('fixtures/manual-request.json');blank.items[0].answers[0].evaluation.answerStatus='blank';assert.throws(()=>check('WrittenBatch',blank));
const client=read('fixtures/rubric-request.json');client.items[0].answers[0].evaluation.totalPoints=7;assert.throws(()=>check('WrittenBatch',client));
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtime=0;if(process.argv.includes('--runtime-samples'))for(const [file,schema]of [['manual-request','WrittenBatch'],['rubric-request','WrittenBatch'],['manual-response','VerificationResponse'],['sync','SyncReadback']]){check(schema,JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../../../target/mobile-written-contract-samples/'+file+'.json'),'utf8')));runtime++;}
console.log(JSON.stringify({status:'PASS',packVersion:'1.7.0',writtenRequestVersion:'3.1',schemas:Object.keys(definitions).length,fixtures:4,runtimeSamplesChecked:runtime,previousNamedSchemasUnchanged:true,productionEnabled:false},null,2));
