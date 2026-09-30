'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const read=name=>JSON.parse(fs.readFileSync(path.join(__dirname,name),'utf8'));
let Ajv;for(const name of [process.env.SMART_CONTRACT_AJV_PATH,'ajv',path.resolve(__dirname,'../../../../../../web-dashboard/node_modules/ajv')].filter(Boolean)){
 try{Ajv=require(name);break;}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}}
if(!Ajv)throw new Error('An existing Ajv 6 installation is required.');
const doc=read('openapi.json'),parent=read('../1.0.0/schemas.json');assert.equal(doc.openapi,'3.1.0');
const definitions=JSON.parse(JSON.stringify(doc.components.schemas).replaceAll('#/components/schemas/','#/definitions/'));
for(const [key,value]of Object.entries(definitions))assert.deepEqual(value,parent.definitions[key]);
assert.equal(Object.keys(doc.paths).length,1);assert.equal(Object.values(doc.paths)[0].get['x-production-enabled'],false);
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8});
function compile(name){return ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});}
function hash(data){return crypto.createHash('sha256').update(JSON.stringify({contractVersion:data.contractVersion,assignmentUuid:data.assignmentUuid,testVersionNumber:data.testVersionNumber,
 questions:data.questions.map(q=>({questionUuid:q.questionUuid,maximumPoints:q.maximumPoints.toFixed(2),rubricId:q.rubricId,expectedResponseCount:q.expectedResponseCount})),
 rubrics:data.rubrics.map(r=>({rubricId:r.rubricId,name:r.name,criteria:r.criteria.map(c=>({rubricCriterionId:c.rubricCriterionId,name:c.name,maximumPoints:c.maximumPoints.toFixed(2),isRequired:c.isRequired}))}))}),'utf8').digest('hex');}
const valid=compile('EvaluationReference');
function check(value){assert.equal(valid(value),true,JSON.stringify(valid.errors));assert.equal(hash(value.data),value.data.evaluationReferenceHash);}
for(const name of ['evaluation-reference','evaluation-manual-only','evaluation-unicode'])check(read('fixtures/'+name+'.json'));
assert.notEqual(read('fixtures/evaluation-reference.json').data.evaluationReferenceHash,read('fixtures/evaluation-unicode.json').data.evaluationReferenceHash);
for(const [name,schema]of [['verification-manual','ManualEvaluation'],['verification-rubric','RubricEvaluation']]){
 const request=read('fixtures/'+name+'.json');const validate=compile(schema);for(const item of request.items)for(const answer of item.answers)assert.equal(validate(answer.evaluation),true,JSON.stringify(validate.errors));
}
const blank={kind:'manual',answerStatus:'blank',responseText:null,attachmentUuids:[],points:1};assert.equal(compile('ManualEvaluation')(blank),false);
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtimeSamples=0;if(process.argv.includes('--runtime-samples')){check(JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../../../target/mobile-evaluation-contract-samples/evaluation-reference.json'),'utf8')));runtimeSamples++;}
console.log(JSON.stringify({status:'PASS',packVersion:'1.5.0',schemas:Object.keys(definitions).length,fixtures:5,runtimeSamplesChecked:runtimeSamples,writeRoutesImplemented:false,productionEnabled:false},null,2));
