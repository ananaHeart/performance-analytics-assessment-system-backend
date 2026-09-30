'use strict';

// Read-only contract checks. No HTTP, SQL, package installs, or app initialization.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const base = __dirname;
const repo = path.resolve(base, '../../../..');
const read = relative => JSON.parse(fs.readFileSync(path.join(base, relative), 'utf8'));
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
let Ajv;
for (const candidate of [process.env.SMART_CONTRACT_AJV_PATH, 'ajv',
  path.resolve(repo, '../../web-dashboard/node_modules/ajv')].filter(Boolean)) {
  try { Ajv = require(candidate); break; } catch (error) {
    if (error.code !== 'MODULE_NOT_FOUND') throw error;
  }
}
if (!Ajv) throw new Error('Ajv 6 required; set SMART_CONTRACT_AJV_PATH to an existing package.');
const ajv = new Ajv({ allErrors: true, multipleOfPrecision: 2, schemaId: 'auto' });
const schema = read('schemas.json');
const index = read('index.json');
assert.equal(ajv.validateSchema(schema), true, JSON.stringify(ajv.errors));
const validators = new Map();
for (const name of Object.keys(schema.definitions)) {
  validators.set(name, ajv.compile({ ...schema, $ref: '#/definitions/' + name }));
}
const statuses = new Set(['SOURCE_IMPLEMENTED_UNVERIFIED', 'EXISTING_DTO_ONLY',
  'PROPOSED_NOT_IMPLEMENTED']);
const routes = new Map(index.routes.map(route => [route.id, route]));
assert.equal(routes.size, index.routes.length, 'Duplicate route ID');
for (const route of routes.values()) {
  assert(statuses.has(route.status), 'Unknown route status');
  assert(route.path.startsWith('/api/v3/'), 'Unexpected route namespace');
  for (const name of [route.requestSchema, route.responseSchema].filter(Boolean)
    .flatMap(value => value.split(' | '))) assert(validators.has(name), 'Missing schema: ' + name);
}
assert.equal(routes.get('scan-page').status, 'EXISTING_DTO_ONLY');
assert.equal(routes.get('finalize').requestSchema, null, 'Existing finalizer has no body');
assert(!index.routes.some(route => route.path.includes('/refresh')), 'No invented refresh route');
assert(!index.routes.some(route => route.path.includes('/sf1')), 'SF1 out of scope');
assert.equal(index.expectedCentralBaseline.liveVerified, false);
assert.equal(index.mobileDraft.productionMigrationAuthorized, false);

let positive = 0, negative = 0, copies = 0, semantics = 0;
const fixturePaths = new Set();
const failures = [];
function semanticErrors(value, name) {
  const errors = [];
  if (name === 'AttachmentMetadata' && value.crop) {
    const c = value.crop;
    if (c.x + c.width > c.baseWidthPx || c.y + c.height > c.baseHeightPx)
      errors.push('CROP_OUT_OF_BOUNDS');
  }
  if (name === 'DetectionBatch') {
    const original = read('fixtures/proposed/detections-mc.json');
    if (value.operationUuid === original.operationUuid &&
      JSON.stringify(value) !== JSON.stringify(original)) errors.push('IDEMPOTENCY_KEY_REUSE');
  }
  if (name === 'VerificationBatch') {
    const resultIds = value.items.map(item => item.resultUuid);
    if (new Set(resultIds).size !== resultIds.length) errors.push('DUPLICATE_RESULT');
    for (const item of value.items) for (const answer of item.answers) {
      const e = answer.evaluation;
      if (e.kind === 'manual' && e.points > 5) errors.push('MANUAL_SCORE_OUT_OF_RANGE');
      if (e.kind === 'manual' || e.kind === 'rubric') {
        if (e.answerStatus === 'answered' && !e.responseText?.trim() && !e.attachmentUuids.length)
          errors.push('WRITTEN_RESPONSE_MISSING');
      }
      if (e.kind === 'rubric') {
        const expected = new Map([[6101, 3], [6102, 2]]); // Explicit synthetic essay context.
        const ids = e.criterionScores.map(c => c.rubricCriterionId);
        if (ids.length !== expected.size || new Set(ids).size !== ids.length ||
          [...expected.keys()].some(id => !ids.includes(id))) errors.push('RUBRIC_SCORE_INCOMPLETE');
        if (e.criterionScores.some(c => c.pointsAwarded > (expected.get(c.rubricCriterionId) ?? -1)))
          errors.push('RUBRIC_SCORE_OUT_OF_RANGE');
      }
    }
  }
  return errors;
}

for (const fixture of index.fixtures) {
  assert(!fixturePaths.has(fixture.path), 'Duplicate fixture path');
  fixturePaths.add(fixture.path);
  assert(routes.has(fixture.routeId), 'Unknown fixture route');
  assert(statuses.has(fixture.status), 'Unknown fixture status');
  const value = read(fixture.path);
  const validate = validators.get(fixture.schema);
  assert(validate, 'Missing fixture schema');
  const valid = validate(value);
  if (valid !== fixture.expectedSchemaValid) failures.push({ path: fixture.path, errors: validate.errors });
  if (fixture.expectedSchemaValid) positive++; else negative++;
  if (valid) {
    const errors = semanticErrors(value, fixture.schema);
    if (fixture.expectedSemanticError) {
      assert(errors.includes(fixture.expectedSemanticError), 'Expected semantic rejection: ' + fixture.path);
      semantics++;
    } else assert.deepEqual(errors, [], 'Unexpected semantic rejection: ' + fixture.path);
  }
  if (fixture.source) {
    const copied = fs.readFileSync(path.join(base, fixture.path));
    const source = fs.readFileSync(path.join(repo, fixture.source));
    assert.equal(digest(copied), fixture.sha256, 'Copied fixture modified');
    assert.equal(digest(source), fixture.sha256, 'Source fixture drift; publish a new pack');
    copies++;
  }
}
assert.deepEqual(failures, [], JSON.stringify(failures, null, 2));
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const absolute = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(absolute) : [absolute];
  });
}
const actual = walk(path.join(base, 'fixtures')).map(file =>
  path.relative(base, file).split(path.sep).join('/'));
assert.deepEqual([...fixturePaths].sort(), actual.sort(), 'Unindexed or missing fixture');

function sourceFields(file, recordName) {
  const source = fs.readFileSync(path.join(repo, file), 'utf8');
  const match = source.match(new RegExp('public record ' + recordName + '\\(([\\s\\S]*?)\\n\\)'));
  assert(match, 'Cannot inspect Java record ' + recordName);
  return [...match[1].matchAll(/^\s*[A-Za-z][A-Za-z0-9_]*(?:<[^>]+>)?\s+(\w+)\s*,?\s*$/gm)]
    .map(m => m[1]).sort();
}
const java = 'src/main/java/com/capstone/assessment/v3/';
for (const [schemaName, file, recordName, examplePath, useData] of [
  ['ScanMetadata', 'mobile/dto/V3ScanPageUploadMetadata.java', 'V3ScanPageUploadMetadata', 'fixtures/current/mobile/scan-page-upload-metadata.json', false],
  ['ScanResponse', 'mobile/dto/V3ScanPageUploadResponse.java', 'V3ScanPageUploadResponse', 'fixtures/current/mobile/scan-page-upload-created-response.json', true],
  ['LoginSuccess', 'auth/dto/V3LoginResponse.java', 'V3LoginResponse', 'fixtures/current/auth/login-success.json', true],
  ['MeResponse', 'auth/dto/V3CurrentUserResponse.java', 'V3CurrentUserResponse', 'fixtures/current/auth/me.json', true],
  ['FinalizationResponse', 'scoring/dto/V3ScoredResultResponse.java', 'V3ScoredResultResponse', 'fixtures/current/scoring/finalized-response.json', true],
  ['Download', 'mobile/dto/V3MobileDownloadResponse.java', 'V3MobileDownloadResponse', 'fixtures/current/mobile/download-response.json', true],
  ['Manifest', 'mobile/dto/V3AnswerSheetManifestResponse.java', 'V3AnswerSheetManifestResponse', 'fixtures/current/mobile/answer-sheet-manifest-response.json', true],
]) {
  const example = read(examplePath);
  assert.deepEqual(Object.keys(useData ? example.data : example).sort(), sourceFields(java + file, recordName),
    'Source DTO fields drifted: ' + schemaName);
}

const get = name => read('fixtures/' + name + '.json');
function replayEqual(created, replayed, field, expected) {
  const first = structuredClone(created.data), second = structuredClone(replayed.data);
  assert.equal(second[field], expected);
  delete first[field]; delete second[field];
  // Existing scan fixtures use a fresh acknowledgement time for each replay.
  delete first.acknowledgedAt; delete second.acknowledgedAt;
  assert.deepEqual(first, second, 'Replay changed acknowledged identity/content');
}
replayEqual(get('current/mobile/scan-page-upload-created-response'),
  get('current/mobile/scan-page-upload-replayed-response'), 'uploadStatus', 'replayed');
replayEqual(get('proposed/detections-created'), get('proposed/detections-replayed'), 'disposition', 'replayed');
replayEqual(get('current/scoring/finalized-response'), get('current/scoring/finalized-replayed'), 'scoreChanged', false);
const partial = get('proposed/verification-partial-response').data;
const request = get('proposed/verification-partial-request');
assert.deepEqual(partial.items.map(item => item.resultUuid), request.items.map(item => item.resultUuid));
assert.equal(partial.syncUuid, request.syncUuid);
assert(partial.items.some(item => item.status === 'success') && partial.items.some(item => item.status === 'failed'));
assert.equal(partial.syncStatus, 'partial_success');
for (const item of partial.items) {
  assert.equal(item.error === null, item.status === 'success');
  if (item.status === 'failed') assert.equal(item.idMappings.length, 0);
}
const partialReplay = get('proposed/verification-partial-replayed').data;
assert.equal(partialReplay.items[0].disposition, 'replayed');
assert.deepEqual(partialReplay.items[0].idMappings, partial.items[0].idMappings);
assert.deepEqual(partialReplay.items[1], partial.items[1]);

for (const name of ['proposed/result-pending', 'proposed/result-finalized', 'proposed/result-reopened']) {
  const result = get(name).data;
  assert.equal(result.officialScore !== null, result.resultStatus === 'finalized');
  const resultMap = result.idMappings.find(m => m.entityType === 'test_result');
  assert.equal(resultMap.uuid, result.resultUuid); assert.equal(resultMap.centralId, result.testResultId);
  for (const page of result.pages) {
    assert(result.idMappings.some(m => m.entityType === 'answer_attachment' && m.uuid === page.originalAttachmentUuid));
  }
  if (result.officialScore) {
    const score = result.officialScore;
    assert.equal(score.scoreVersion, result.scoreVersion);
    assert.equal(score.resultUuid, result.resultUuid);
    assert.equal(score.testResultId, result.testResultId);
    assert.equal(score.totalScore, score.parts.reduce((sum, part) => sum + part.totalScore, 0));
    assert.equal(score.maxScore, score.parts.reduce((sum, part) => sum + part.maxScore, 0));
    assert.equal(score.percentage, score.totalScore / score.maxScore * 100);
  }
}
const ready = get('proposed/analytics-ready-score-only').data;
const result = get('proposed/result-finalized').data;
assert.equal(ready.scoreVersion, result.scoreVersion);
assert.equal(ready.resultUuid, result.resultUuid);
assert.equal(ready.metrics.totalScore, result.officialScore.totalScore);
assert.equal(get('proposed/analytics-unavailable').data.metrics, null);
for (const module of ready.unavailableModules) assert.equal(ready.metrics[module], null);
const download = get('current/mobile/download-response').data;
assert(Array.isArray(download.classAssignmentSchedules));
assert(download.classAssignments.every(item => typeof item.classStatus === 'string'));
// Written scenarios have their own identities and point to their own crops/criteria.
const evaluationReference = get('proposed/evaluation-reference').data;
for (const [reviewName, cropName] of [['proposed/verification-manual', 'proposed/answer-crop-metadata'],
  ['proposed/verification-rubric', 'proposed/essay-crop-metadata']]) {
  const review = get(reviewName), crop = get(cropName), item = review.items[0], answer = item.answers[0];
  assert.equal(review.assignmentUuid, evaluationReference.assignmentUuid);
  assert.equal(item.resultUuid, crop.resultUuid);
  assert.equal(answer.regionUuid, crop.regionUuid);
  assert.equal(answer.scanPageUuid, crop.scanPageUuid);
  assert(answer.evaluation.attachmentUuids.includes(crop.attachmentUuid));
  assert(evaluationReference.questions.some(q => q.questionUuid === answer.questionUuid));
}
const blockedKeys = new Set(['image_uri', 'localPath', 'storageKey', 'verifiedByUserId', 'isCorrect']);
function inspectKeys(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert(!blockedKeys.has(key), 'Private/authoritative client field in positive fixture: ' + key);
    inspectKeys(child);
  }
}
for (const fixture of index.fixtures.filter(f => f.expectedSchemaValid)) inspectKeys(read(fixture.path));

console.log(JSON.stringify({
  status: 'PASS', checkedAt: new Date().toISOString(), packVersion: index.packVersion,
  routes: routes.size, schemas: validators.size, fixtures: fixturePaths.size,
  schemaPositiveFixtures: positive, schemaNegativeFixtures: negative,
  semanticNegativeFixtures: semantics, unchangedSourceCopies: copies,
  sourceDtoRootFieldChecks: 7,
  checks: ['schema compilation', 'fixture schemas', 'negative examples', 'source copy integrity',
    'source DTO root fields', 'replay identity', 'partial outcome correlation',
    'result/score/analytics consistency', 'download additions', 'private field exclusion', 'fixture index completeness'],
  schemasSha256: digest(fs.readFileSync(path.join(base, 'schemas.json'))),
  indexSha256: digest(fs.readFileSync(path.join(base, 'index.json'))),
  runtimeTestsExecuted: false, databaseTestsExecuted: false, mobileTestsExecuted: false,
  physicalScannerValidated: false, productionReady: false,
}, null, 2));
