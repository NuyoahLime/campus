import { existsSync, promises as fs } from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

const spec = path.resolve(process.cwd(), 'e2e/specs/stage26-trace-guard.spec.ts');
const output = path.resolve(process.cwd(), 'test-results', 'trace-guard');

function run(command, args) {
  return new Promise((resolve, reject) => {
    const executable = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : command;
    const executableArgs = process.platform === 'win32' ? ['/d', '/s', '/c', command, ...args] : args;
    const child = spawn(executable, executableArgs, {
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: false,
      windowsHide: true,
      env: { ...process.env, STAGE26_ARTIFACT_SECURITY_PROBE: '1' }
    });
    let output = '';
    child.stdout.on('data', data => {
      const text = data.toString();
      output += text;
      process.stdout.write(text);
    });
    child.stderr.on('data', data => {
      const text = data.toString();
      output += text;
      process.stderr.write(text);
    });
    child.on('error', reject);
    child.on('close', code => resolve({ code: code ?? 1, output }));
  });
}

async function filesAt(target) {
  if (!existsSync(target)) return [];
  const entry = await fs.stat(target);
  if (entry.isFile()) return [target];
  const entries = await fs.readdir(target, { withFileTypes: true });
  return (await Promise.all(entries.map(entry => filesAt(path.join(target, entry.name))))).flat();
}

await fs.writeFile(spec, [
  "import { expect, test } from '@playwright/test';",
  "import { actors, loginUi } from '../support/auth';",
  "test('STAGE26_INTENTIONAL_ARTIFACT_PROBE_FAILURE', async ({ page }) => {",
  "  await loginUi(page, actors.schoolAdminA);",
  "  await expect(page).toHaveURL(/\\/school-admin(?:$|\\/)/);",
  "  await expect(page.locator('body')).toContainText('SESSION IDENTITY');",
  "  console.log('STAGE26_AUTHENTICATED_PRODUCT_PAGE_READY');",
  "  if (process.env.STAGE26_ARTIFACT_SECURITY_PROBE === '1') {",
  "    throw new Error('STAGE26_INTENTIONAL_ARTIFACT_PROBE_FAILURE');",
  "  }",
  "});",
  ""
].join('\n'));

try {
  await fs.rm(output, { recursive: true, force: true });
  const result = await run(process.platform === 'win32' ? 'npx.cmd' : 'npx',
    ['playwright', 'test', 'stage26-trace-guard.spec.ts', '--output', output]);
  if (result.code === 0) throw new Error('The deliberate authenticated failure unexpectedly passed.');
  const files = await filesAt(output);
  const forbidden = files.filter(file => /(?:trace(?:\.zip)?|\.trace|\.har|storage[-_]?state)/i.test(path.basename(file)));
  if (forbidden.length) throw new Error(`Trace-related artifacts were generated: ${forbidden.join(', ')}`);
  const junit = files.find(file => path.basename(file) === 'results.xml');
  const junitText = junit ? (await fs.readFile(junit, 'utf8')).toString('utf8') : '';
  const setupProved = result.output.includes('STAGE26_AUTHENTICATED_PRODUCT_PAGE_READY');
  const failureProved = result.output.includes('STAGE26_INTENTIONAL_ARTIFACT_PROBE_FAILURE')
    || junitText.includes('STAGE26_INTENTIONAL_ARTIFACT_PROBE_FAILURE');
  if (!setupProved || !failureProved) {
    throw new Error('The probe did not prove authenticated setup and intentional assertion failure.');
  }
  console.log('TRACE_FAILURE_GUARD=PASS');
  console.log('AUTHENTICATED_FAILURE_PROBE=PASS');
  console.log('FAILURE_CAUSE_VERIFIED=YES');
  console.log('FAILURE_TRACE_PRESENT=NO');
  console.log('TRACE_FILES_GENERATED=0');
  console.log('HAR_FILES=0');
  console.log('STORAGE_STATE_FILES=0');
} finally {
  await fs.rm(spec, { force: true });
}
