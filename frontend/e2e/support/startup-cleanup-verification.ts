import { existsSync, promises as fs } from 'node:fs';
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { runtimeFile, startRuntime, stopRuntime } from './runtime';

async function eventually(check: () => Promise<boolean>, label: string) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    if (await check()) return;
    await delay(250);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function responds(url: string) {
  try {
    await fetch(url);
    return true;
  } catch {
    return false;
  }
}

async function dockerContainerExists(name: string) {
  return await new Promise<boolean>((resolve) => {
    const child = spawn('docker', ['inspect', name], {
      stdio: 'ignore',
      shell: false,
      windowsHide: true
    });
    child.on('error', () => resolve(false));
    child.on('close', code => resolve(code === 0));
  });
}

let state: { backendPid: number; frontendPid: number; dbContainer: string } | null = null;
try {
  await startRuntime({
    frontendCommand: process.execPath,
    frontendArgs: ['-e', 'process.exit(1)'],
    frontendUrl: 'http://127.0.0.1:5174/',
    frontendStartupTimeoutMs: 1_000
  });
  throw new Error('Intentional frontend startup failure did not fail runtime setup.');
} catch (error) {
  if (existsSync(runtimeFile)) {
    state = JSON.parse(await fs.readFile(runtimeFile, 'utf8'));
  }
  if (!state?.backendPid || !state?.frontendPid || !state?.dbContainer) {
    throw new Error(`Runtime did not register startup resources before failure: ${error}`);
  }
} finally {
  await stopRuntime();
}

await eventually(async () => !(await responds('http://127.0.0.1:8080/actuator/health')), 'backend shutdown');
await eventually(async () => !(await responds('http://127.0.0.1:5174/')), 'frontend shutdown');
if (await dockerContainerExists(state!.dbContainer)) {
  throw new Error(`PostgreSQL container still exists: ${state!.dbContainer}`);
}

console.log('STARTUP_FAILURE_CLEANUP=PASS');
console.log('ORPHAN_BACKEND=NO');
console.log('ORPHAN_FRONTEND=NO');
console.log('ORPHAN_POSTGRES=NO');
