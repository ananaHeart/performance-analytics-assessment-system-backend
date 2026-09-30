'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = __dirname;
const repo = path.resolve(base, '../../../..');
const read = name => JSON.parse(fs.readFileSync(path.join(base, name), 'utf8'));
let Ajv;
for (const name of [process.env.SMART_CONTRACT_AJV_PATH, 'ajv', path.resolve(repo, '../../web-dashboard/node_modules/ajv')].filter(Boolean)) {
  try { Ajv = require(name); break; } catch (error) { if (error.code !== 'MODULE_NOT_FOUND') throw error; }
}
if (!Ajv) throw new Error('Use an existing Ajv 6 installation through SMART_CONTRACT_AJV_PATH.');
const document = read('openapi.json');
assert.equal(document.openapi, '3.1.0');
const operation = document.paths['/api/v3/mobile/verification-batches'].post;
assert.equal(operation['x-production-enabled'], false);
assert.equal(operation['x-max-body-bytes'], 2097152);
assert.deepEqual(operation['x-supported-evaluations'], ['objective']);
const definitions = JSON.parse(JSON.stringify(document.components.schemas).replaceAll('#/components/schemas/', '#/definitions/'));
const parent = read('../1.0.0/schemas.json');
parent.definitions.AnswerEvaluation.properties.evaluation = { $ref: '#/definitions/ObjectiveEvaluation' };
for (const [name, definition] of Object.entries(definitions)) assert.deepEqual(definition, parent.definitions[name], name + ' changed beyond the objective scope');
const schema = { $schema: 'http://json-schema.org/draft-07/schema#', definitions };
const ajv = new Ajv({ allErrors: true });
assert.equal(ajv.validateSchema(schema), true, JSON.stringify(ajv.errors));
const request = ajv.compile({ ...schema, $ref: '#/definitions/VerificationBatch' });
const response = ajv.compile({ ...schema, $ref: '#/definitions/VerificationResponse' });
for (const name of ['verification-objective', 'verification-rescan', 'verification-partial-request'])
  assert.equal(request(read('fixtures/' + name + '.json')), true, JSON.stringify(request.errors));
for (const name of ['verification-partial-response', 'verification-partial-replayed', 'verification-created', 'verification-replayed'])
  assert.equal(response(read('fixtures/' + name + '.json')), true, JSON.stringify(response.errors));
for (const name of ['verification-manual', 'verification-rubric'])
  assert.equal(request(read('fixtures/' + name + '.json')), false, 'Unsupported evaluation accepted: ' + name);
const bad = read('fixtures/verification-objective.json');
bad.items[0].answers[0].evaluation.selectedOption = 'B';
assert.equal(request(bad), false, 'Client option override accepted');
for (const ref of JSON.stringify(document).matchAll(/"\$ref":"(#[^"]+)"/g)) {
  let target = document;
  for (const segment of ref[1].slice(2).split('/')) target = target?.[segment];
  assert(target, 'Unresolved reference ' + ref[1]);
}
const created = read('fixtures/verification-created.json').data.items[0];
const replayed = read('fixtures/verification-replayed.json').data.items[0];
assert.deepEqual({ ...created, disposition: 'replayed' }, replayed);
console.log(JSON.stringify({ status: 'PASS', packVersion: '1.2.0', checkedAt: new Date().toISOString(),
  schemas: 9, fixtures: 9, scope: 'objective_verification', productionEnabled: false,
  runtimeTestsExecuted: false, physicalScannerValidated: false }, null, 2));
