import { existsSync, promises as fs } from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

const spec = path.resolve(process.cwd(), 'e2e/specs/stage26-trace-guard.spec.ts');
const output = path.resolve(process.cwd(), 'test-results', 'trace-guard');

function run(command, args) {
  return new Promise((resolve, reject) => {
    const executable = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : command;
    const executableArgs = process.platform === 'win32' ? ['/d', '/s', '/c', command, ...args] : args;
    const child = spawn(executable, executableArgs, { stdio: 'inherit', shell: false, windowsHide: true });
    child.on('error', reject);
    child.on('close', code => resolve(code ?? 1));
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
  "test('authenticated trace guard intentionally fails', async ({ page }) => {",
  "  await loginUi(page, actors.schoolAdminA);",
  "  await expect(page.locator('body')).toContainText('stage26-trace-guard-impossible');",
  "});",
  ""
].join('\n'));

try {
  await fs.rm(output, { recursive: true, force: true });
  const exitCode = await run(process.platform === 'win32' ? 'npx.cmd' : 'npx',
    ['playwright', 'test', 'stage26-trace-guard.spec.ts', '--output', output]);
  if (exitCode === 0) throw new Error('The deliberate authenticated failure unexpectedly passed.');
  const files = await filesAt(output);
  const forbidden = files.filter(file => /(?:trace(?:\.zip)?|\.trace|\.har|storage[-_]?state)/i.test(path.basename(file)));
  if (forbidden.length) throw new Error(`Trace-related artifacts were generated: ${forbidden.join(', ')}`);
  console.log('TRACE_FAILURE_GUARD=PASS');
  console.log('TRACE_FILES_GENERATED=0');
  console.log('HAR_FILES=0');
  console.log('STORAGE_STATE_FILES=0');
} finally {
  await fs.rm(spec, { force: true });
}
