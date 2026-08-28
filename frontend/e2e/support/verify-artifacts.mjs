import { existsSync, promises as fs } from 'node:fs';
import path from 'node:path';

const candidates = [
  path.resolve(process.cwd(), 'playwright-report'),
  path.resolve(process.cwd(), 'test-results', 'stage26-e2e-junit.xml')
];
const workflowPath = path.resolve(process.cwd(), '..', '.github', 'workflows', 'stage26-e2e.yml');
const forbiddenPath = /(?:^|[\\/])(trace(?:\.zip)?|.*\.trace|.*\.har|storage[-_]?state.*|.*\.env|.*credentials.*)(?:$|[\\/])/i;
const forbiddenVisualPath = /\.(?:png|jpe?g|webp|gif|mp4|webm)$/i;
const sensitivePatterns = [
  /(?:^|[^\w-])(?:JSESSIONID|SESSION|XSRF-TOKEN)\s*=/i,
  /\b(?:csrf(?:[-_ ]?token)?|xsrf[-_ ]?token)\s*[:=]\s*\S+/i,
  /\b(?:cookie|set-cookie)\s*:/i,
  /\bauthorization\s*:\s*(?:bearer|basic)\b/i,
  /stage26-e2e-password/i,
  /\b(?:db_password|database_password)\b\s*[:=]\s*\S+/i
];

async function filesAt(target) {
  if (!existsSync(target)) return [];
  const entry = await fs.stat(target);
  if (entry.isFile()) return [target];
  const entries = await fs.readdir(target, { withFileTypes: true });
  const nested = await Promise.all(entries.map(entry => filesAt(path.join(target, entry.name))));
  return nested.flat();
}

if (!existsSync(workflowPath)) {
  throw new Error(`Stage26 workflow not found: ${workflowPath}`);
}
const workflow = await fs.readFile(workflowPath, 'utf8');
if (!/id:\s*artifact_guard\b/.test(workflow)) {
  throw new Error('Artifact guard step must expose id: artifact_guard.');
}
if (!/if:\s*\$\{\{\s*always\(\)\s*&&\s*steps\.artifact_guard\.outcome\s*==\s*['"]success['"]\s*\}\}/.test(workflow)) {
  throw new Error('Artifact upload must require artifact_guard success.');
}
if (/frontend\/test-results\/\*\*\*/.test(workflow)) {
  throw new Error('Workflow must not upload the broad frontend/test-results/** path.');
}

const files = (await Promise.all(candidates.map(filesAt))).flat();
const forbidden = files.filter(file => forbiddenPath.test(path.relative(process.cwd(), file)));
if (forbidden.length) {
  throw new Error(`Forbidden E2E artifact files found: ${forbidden.join(', ')}`);
}
const visualArtifacts = files.filter(file => forbiddenVisualPath.test(file));
if (visualArtifacts.length) {
  throw new Error(`Screenshot/video E2E artifacts must not be uploaded: ${visualArtifacts.join(', ')}`);
}

for (const file of files) {
  const content = await fs.readFile(file);
  const text = content.toString('utf8');
  const matching = sensitivePatterns.find(pattern => pattern.test(text));
  if (matching) {
    throw new Error(`Sensitive E2E artifact content found in ${file}: ${matching}`);
  }
}

console.log(`ARTIFACT_ALLOWLIST=PASS files=${files.length}`);
console.log('UPLOAD_REQUIRES_GUARD_SUCCESS=YES');
console.log('TRACE_FILES=0');
console.log('HAR_FILES=0');
console.log('STORAGE_STATE_FILES=0');
console.log('SCREENSHOT_VIDEO_FILES=0');
console.log('ARTIFACT_SECRET_SCAN=PASS');
