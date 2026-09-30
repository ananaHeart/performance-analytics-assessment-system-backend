'use strict';
// Read-only fixture/schema checks; runtime SQL/HTTP evidence lives in the Java test reports.
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
const operation = document.paths['/api/v3/mobile/scan-pages/{scanPageUuid}/detections'].post;
assert.equal(operation['x-production-enabled'], false);
assert.equal(operation['x-max-body-bytes'], 131072);
const definitions = JSON.parse(JSON.stringify(document.components.schemas).replaceAll('#/components/schemas/', '#/definitions/'));
const parent = read('../1.0.0/schemas.json');
for (const [name, schema] of Object.entries(definitions)) assert.deepEqual(schema, parent.definitions[name], name + ' wire shape changed');
const schema = { $schema: 'http://json-schema.org/draft-07/schema#', definitions };
const ajv = new Ajv({ allErrors: true });
assert.equal(ajv.validateSchema(schema), true, JSON.stringify(ajv.errors));
const request = ajv.compile({ ...schema, $ref: '#/definitions/DetectionBatch' });
const response = ajv.compile({ ...schema, $ref: '#/definitions/OperationAck' });
for (const name of ['detections-mc', 'detections-tf', 'detections-uncertain']) {
  assert.equal(request(read('fixtures/' + name + '.json')), true, JSON.stringify(request.errors));
}
for (const name of ['detections-created', 'detections-replayed']) {
  assert.equal(response(read('fixtures/' + name + '.json')), true, JSON.stringify(response.errors));
}
for (const name of ['detection-client-score', 'uncertain-selected-option', 'tf-display-key']) {
  assert.equal(request(read('fixtures/' + name + '.json')), false, name + ' must be rejected');
}
// Context-dependent conflict: valid shape, same immutable operation but changed option.
const conflict = read('fixtures/detection-uuid-reuse.json');
const original = read('fixtures/detections-mc.json');
assert.equal(request(conflict), true);
assert.equal(conflict.operationUuid, original.operationUuid);
assert.notDeepEqual(conflict.detections, original.detections);
for (const ref of JSON.stringify(document).matchAll(/"\$ref":"(#[^"]+)"/g)) {
  let target = document;
  for (const segment of ref[1].slice(2).split('/')) target = target?.[segment];
  assert(target, 'Unresolved reference ' + ref[1]);
}
console.log(JSON.stringify({ status: 'PASS', packVersion: '1.1.0', checkedAt: new Date().toISOString(),
  schemas: 4, fixtures: 9, parentWireShapesUnchanged: true, productionEnabled: false,
  runtimeTestsExecuted: false, physicalScannerValidated: false }, null, 2));
