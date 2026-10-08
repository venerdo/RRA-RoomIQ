import fs from 'node:fs';

const collectionPath = new URL('./rra-roomiq-identity-postman-collection.json', import.meta.url);
const collection = JSON.parse(fs.readFileSync(collectionPath, 'utf8'));
const failures = [];

if (collection.info?.schema !== 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json') {
  failures.push('collection must declare the Postman v2.1 schema');
}

const variables = new Map((collection.variable ?? []).map(variable => [variable.key, variable.value]));
for (const requiredVariable of [
  'baseUrl', 'userId', 'adminUserId', 'roleId', 'permissionId', 'assignmentId',
  'privilegeId', 'officeBuildingId', 'departmentId', 'activeUserEmail',
  'activeUserPassword', 'accessToken', 'refreshToken', 'correlationId'
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

const endpointKey = request => `${request.method} ${request.url.raw.replace('{{baseUrl}}', '')}`;
const expected = new Set([
  'POST /api/v1/auth/login', 'POST /api/v1/auth/refresh', 'POST /api/v1/auth/logout',
  'POST /api/v1/users', 'GET /api/v1/users?page=0&size=20&status=PENDING',
  'GET /api/v1/users/{{userId}}', 'PATCH /api/v1/users/{{userId}}',
  'PATCH /api/v1/users/{{userId}}/status', 'DELETE /api/v1/users/{{userId}}',
  'POST /api/v1/roles', 'GET /api/v1/roles', 'POST /api/v1/permissions',
  'GET /api/v1/permissions', 'POST /api/v1/roles/{{roleId}}/permissions/{{permissionId}}',
  'GET /api/v1/roles/{{roleId}}/permissions', 'DELETE /api/v1/roles/{{roleId}}/permissions/{{permissionId}}',
  'POST /api/v1/users/{{userId}}/roles', 'GET /api/v1/users/{{userId}}/roles',
  'DELETE /api/v1/users/{{userId}}/roles/{{assignmentId}}',
  'POST /api/v1/users/{{userId}}/privileges', 'GET /api/v1/users/{{userId}}/privileges',
  'GET /api/v1/users/{{userId}}/privileges/evaluate?privilegeCode=CG_BOOKING&at=2026-10-10T09:00:00Z',
  'DELETE /api/v1/users/{{userId}}/privileges/{{privilegeId}}',
  'GET /actuator/health', 'GET /v3/api-docs'
]);

const actual = new Set(requests.map(request => endpointKey(request.request)));
for (const endpoint of expected) if (!actual.has(endpoint)) failures.push(`missing request: ${endpoint}`);
for (const endpoint of actual) if (!expected.has(endpoint)) failures.push(`unexpected request: ${endpoint}`);

const publicPaths = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout', '/actuator/health', '/v3/api-docs']);
for (const item of requests) {
  const path = item.request.url.raw.replace('{{baseUrl}}', '').split('?')[0];
  const headers = new Map((item.request.header ?? []).map(header => [header.key, header.value]));
  if (!publicPaths.has(path) && !headers.has('Authorization')) failures.push(`${item.name} lacks Authorization header`);
  if (!publicPaths.has(path) && !headers.has('X-Correlation-ID')) failures.push(`${item.name} lacks correlation header`);
  if (path.startsWith('/api/v1/') && !publicPaths.has(path) && !headers.has('X-Correlation-ID')) failures.push(`${item.name} lacks correlation header`);
}

const captureRequirements = new Map([
  ['Login', 'accessToken'], ['Refresh tokens', 'refreshToken'], ['Create user', 'userId'],
  ['Create role', 'roleId'], ['Create permission', 'permissionId'],
  ['Grant role permission', 'assignmentId'], ['Assign user role', 'assignmentId'],
  ['Grant user privilege', 'privilegeId']
]);
for (const [requestName, variable] of captureRequirements) {
  const request = requests.find(candidate => candidate.name === requestName);
  const scripts = (request?.event ?? []).filter(event => event.listen === 'test')
    .flatMap(event => event.script.exec ?? []).join('\n');
  if (!scripts.includes(`'${variable}'`)) failures.push(`${requestName} does not capture ${variable}`);
}

const serialized = JSON.stringify(collection);
for (const forbidden of ['JWT_SECRET=', 'DB_PASSWORD=', 'MAIL_PASSWORD=', 'CLOUDINARY_API_SECRET=', 'RABBITMQ_PASSWORD=']) {
  if (serialized.includes(forbidden)) failures.push(`collection contains forbidden secret marker: ${forbidden}`);
}
if (!String(variables.get('activeUserEmail')).endsWith('@example.test')) {
  failures.push('activeUserEmail must use the reserved example.test domain');
}

if (failures.length) {
  console.error(failures.map(failure => `- ${failure}`).join('\n'));
  process.exit(1);
}

console.log(`Postman collection valid: ${requests.length} requests, ${variables.size} variables`);
