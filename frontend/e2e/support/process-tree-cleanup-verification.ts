import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { terminateProcessTree } from './runtime';

function eventually(check: () => boolean, label: string, timeoutMs = 10_000) {
  return (async () => {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (check()) return;
      await delay(100);
    }
    throw new Error(`Timed out waiting for ${label}`);
  })();
}

function alive(pid: number) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return (error as NodeJS.ErrnoException).code !== 'ESRCH';
  }
}

const childScript = 'setInterval(() => {}, 1000)';
const parentScript = [
  "const { spawn } = require('node:child_process');",
  `const child = spawn(process.execPath, ['-e', ${JSON.stringify(childScript)}], { stdio: 'ignore' });`,
  "process.stdout.write(JSON.stringify({ childPid: child.pid }) + '\\n');",
  'setInterval(() => {}, 1000);'
].join('');

const parent = spawn(process.execPath, ['-e', parentScript], {
  stdio: ['ignore', 'pipe', 'ignore'],
  shell: false,
  windowsHide: true,
  detached: process.platform !== 'win32'
});
let line = '';
parent.stdout.on('data', data => { line += data.toString(); });
await eventually(() => line.includes('\n'), 'process tree startup');
const childPid = JSON.parse(line.trim()).childPid as number;
if (!parent.pid || !childPid) throw new Error('Process tree self-test did not receive both PIDs.');

await terminateProcessTree(parent.pid);
await eventually(() => !alive(parent.pid!), 'parent termination');
await eventually(() => !alive(childPid), 'descendant termination');

console.log(`WINDOWS_PROCESS_TREE_CLEANUP=${process.platform === 'win32' ? 'PASS' : 'NOT_RUN'}`);
console.log(`POSIX_PROCESS_GROUP_IMPLEMENTATION=${process.platform === 'win32' ? 'NOT_RUN' : 'PASS'}`);
console.log('PROCESS_TREE_CLEANUP_SELF_TEST=PASS');
