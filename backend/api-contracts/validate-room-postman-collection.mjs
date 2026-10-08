import fs from 'node:fs';

const collectionPath = new URL('./rra-roomiq-room-postman-collection.json', import.meta.url);
const collection = JSON.parse(fs.readFileSync(collectionPath, 'utf8'));
const failures = [];

if (collection.info?.schema !== 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json') {
  failures.push('collection must declare the Postman v2.1 schema');
}

const variables = new Map((collection.variable ?? []).map(variable => [variable.key, variable.value]));
for (const requiredVariable of [
  'baseUrl', 'accessToken', 'correlationId', 'officeBuildingId', 'floorId', 'departmentId',
  'roomTypeId', 'facilityTypeId', 'roomId', 'facilityAssignmentId', 'roomRuleId',
  'buildingRuleId', 'photoId', 'maintenancePeriodId'
]) {
  if (!variables.has(requiredVariable)) failures.push(`missing collection variable: ${requiredVariable}`);
}

const requests = [];
function flatten(items) {
  for (const item of items ?? []) {
    if (item.item) flatten(item.item);
    else requests.push(item);
  }
}
flatten(collection.item);

const expected = new Set([
  'GET /api/v1/rooms?page=0&size=20&sortBy=code&sortDirection=ASC',
  'POST /api/v1/rooms', 'GET /api/v1/rooms/{{roomId}}', 'PUT /api/v1/rooms/{{roomId}}',
  'PATCH /api/v1/rooms/{{roomId}}/status', 'GET /api/v1/rooms/{{roomId}}/status-history',
  'DELETE /api/v1/rooms/{{roomId}}',
  'GET /api/v1/room-types?page=0&size=20', 'POST /api/v1/room-types',
  'GET /api/v1/room-types/{{roomTypeId}}', 'PUT /api/v1/room-types/{{roomTypeId}}',
  'GET /api/v1/facility-types?page=0&size=20', 'POST /api/v1/facility-types',
  'GET /api/v1/facility-types/{{facilityTypeId}}', 'PUT /api/v1/facility-types/{{facilityTypeId}}',
  'GET /api/v1/rooms/{{roomId}}/facilities', 'POST /api/v1/rooms/{{roomId}}/facilities',
  'PUT /api/v1/rooms/{{roomId}}/facilities/{{facilityAssignmentId}}',
  'GET /api/v1/rooms/{{roomId}}/rules', 'POST /api/v1/rooms/{{roomId}}/rules',
  'GET /api/v1/office-buildings/{{officeBuildingId}}/room-rules',
  'POST /api/v1/office-buildings/{{officeBuildingId}}/room-rules',
  'GET /api/v1/room-rules/{{roomRuleId}}', 'PUT /api/v1/room-rules/{{roomRuleId}}',
  'GET /api/v1/rooms/{{roomId}}/photos', 'POST /api/v1/rooms/{{roomId}}/photos',
  'PUT /api/v1/rooms/{{roomId}}/photos/{{photoId}}',
  'DELETE /api/v1/rooms/{{roomId}}/photos/{{photoId}}',
  'GET /api/v1/rooms/{{roomId}}/maintenance-periods',
  'POST /api/v1/rooms/{{roomId}}/maintenance-periods',
  'DELETE /api/v1/rooms/{{roomId}}/maintenance-periods/{{maintenancePeriodId}}'
]);

const endpointKey = request => {
  const rawUrl = typeof request.url === 'string' ? request.url : request.url?.raw ?? '';
  return `${request.method} ${rawUrl.replace('{{baseUrl}}', '')}`;
};
const actual = new Set(requests.map(item => endpointKey(item.request)));
for (const endpoint of expected) if (!actual.has(endpoint)) failures.push(`missing request: ${endpoint}`);
for (const endpoint of actual) if (!expected.has(endpoint)) failures.push(`unexpected request: ${endpoint}`);

if (actual.size !== requests.length) failures.push('duplicate method/path request found');
if (collection.auth?.type !== 'bearer'
    || !collection.auth.bearer?.some(item => item.key === 'token' && item.value === '{{accessToken}}')) {
  failures.push('collection must use the accessToken bearer variable');
}
for (const item of requests) {
  if (!(item.request.header ?? []).some(header => header.key === 'X-Correlation-ID'
      && header.value === '{{correlationId}}')) {
    failures.push(`${item.name} lacks the correlation header`);
  }
}
const photoUpload = requests.find(item => item.name === 'Upload room photo');
if (photoUpload?.request.body?.mode !== 'formdata'
    || !photoUpload.request.body.formdata?.some(field => field.key === 'file' && field.type === 'file')) {
  failures.push('photo upload must use a multipart file field');
}

const serialized = JSON.stringify(collection);
for (const forbidden of ['JWT_SECRET=', 'DB_PASSWORD=', 'MAIL_PASSWORD=', 'CLOUDINARY_API_SECRET=', 'RABBITMQ_PASSWORD=']) {
  if (serialized.includes(forbidden)) failures.push(`collection contains forbidden secret marker: ${forbidden}`);
}

if (failures.length) {
  console.error(failures.map(failure => `- ${failure}`).join('\n'));
  process.exit(1);
}

console.log(`Room Postman collection valid: ${requests.length} operations, ${variables.size} variables`);