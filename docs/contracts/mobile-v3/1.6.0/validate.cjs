'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const read=name=>JSON.parse(fs.readFileSync(path.join(__dirname,name),'utf8'));
let Ajv;for(const name of [process.env.SMART_CONTRACT_AJV_PATH,'ajv',path.resolve(__dirname,'../../../../../../web-dashboard/node_modules/ajv')].filter(Boolean)){
 try{Ajv=require(name);break;}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}}
if(!Ajv)throw new Error('Existing Ajv 6 is required.');
const doc=read('openapi.json'),parent=read('../1.0.0/schemas.json');
const definitions=JSON.parse(JSON.stringify(doc.components.schemas).replaceAll('#/components/schemas/','#/definitions/'));
for(const [key,value]of Object.entries(definitions))assert.deepEqual(value,parent.definitions[key]);
assert.equal(doc.openapi,'3.1.0');assert.equal(doc.paths['/api/v3/mobile/attachments'].post['x-production-enabled'],false);
const ajv=new Ajv({allErrors:true,multipleOfPrecision:8});
function validate(name,value){const check=ajv.compile({$schema:'http://json-schema.org/draft-07/schema#',definitions,$ref:'#/definitions/'+name});assert(check(value),JSON.stringify(check.errors));}
for(const name of ['answer-crop-metadata','essay-crop-metadata','normalized-page-metadata','teacher-evidence-metadata'])validate('AttachmentMetadata',read('fixtures/'+name+'.json'));
for(const name of ['attachment-created','attachment-replayed'])validate('AttachmentAck',read('fixtures/'+name+'.json'));
const invalid=read('fixtures/answer-crop-metadata.json');invalid.sourceAttachmentUuid=invalid.crop.baseAttachmentUuid;
assert.throws(()=>validate('AttachmentMetadata',invalid));
for(const ref of JSON.stringify(doc).matchAll(/"\$ref":"(#[^"]+)"/g)){let value=doc;for(const key of ref[1].slice(2).split('/'))value=value?.[key];assert(value,'Unresolved '+ref[1]);}
let runtime=0;if(process.argv.includes('--runtime-samples')){
 for(const [file,schema]of [['metadata','AttachmentMetadata'],['created','AttachmentAck'],['sync','SyncReadback']]){validate(schema,JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../../../target/mobile-attachment-contract-samples/'+file+'.json'),'utf8')));runtime++;}
}
console.log(JSON.stringify({status:'PASS',packVersion:'1.6.0',schemas:Object.keys(definitions).length,fixtures:6,runtimeSamplesChecked:runtime,parentShapesUnchanged:true,productionEnabled:false},null,2));
