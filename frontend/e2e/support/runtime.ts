import { createWriteStream } from 'node:fs';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import type { FixtureState } from './fixture';

export const runtimeFile = path.resolve(process.cwd(), 'test-results', 'stage26-runtime.json');
const repoRoot = path.resolve(process.cwd(), '..');
const runtimeDir = path.resolve(process.cwd(), 'test-results', 'stage26-runtime');
const dbContainer = `campus-stage26-e2e-${process.pid}`;
const dbName = 'campus_e2e';
const dbUser = 'postgres';
const dbPassword = 'stage26-e2e-password';

type RuntimeState = {
  dbContainer: string;
  dbPort: number;
  backendPid: number;
  frontendPid: number;
  fixture: FixtureState;
};

function mavenCommand() {
  return process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
}

function spawnLogged(command: string, args: string[], cwd: string, env: NodeJS.ProcessEnv) {
  const logPath = path.join(runtimeDir, `${path.basename(command)}.log`);
  const executable = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : command;
  const executableArgs = process.platform === 'win32' ? ['/d', '/s', '/c', command, ...args] : args;
  const child = spawn(executable, executableArgs, {
    cwd,
    env,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
    windowsHide: true
  });
  const output = createWriteStream(logPath, { flags: 'a' });
  child.stdout?.pipe(output);
  child.stderr?.pipe(output);
  return { child, logPath };
}

async function run(command: string, args: string[], input?: string) {
  const child = spawn(command, args, {
    cwd: repoRoot,
    stdio: ['pipe', 'pipe', 'pipe'],
    shell: false,
    windowsHide: true
  });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', data => { stdout += data.toString(); });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.stdin.end(input);
  const code = await new Promise<number>((resolve, reject) => {
    child.on('error', reject);
    child.on('close', value => resolve(value ?? 1));
  });
  if (code !== 0) {
    throw new Error(`${command} ${args.join(' ')} failed (${code}): ${stderr || stdout}`);
  }
  return stdout.trim();
}

async function waitFor(check: () => Promise<boolean>, label: string, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await check()) return;
    await delay(1000);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function waitForHttp(url: string, label: string) {
  await waitFor(async () => {
    try {
      const response = await fetch(url);
      return response.status < 500;
    } catch {
      return false;
    }
  }, label);
}

async function seedDatabase() {
  const sql = await fs.readFile(path.resolve(process.cwd(), 'e2e/support/fixture.sql'), 'utf8');
  const output = await run('docker', ['exec', '-i', dbContainer, 'psql', '-At', '-v', 'ON_ERROR_STOP=1',
    '-U', dbUser, '-d', dbName], sql);
  const marker = output.split(/\r?\n/).find(line => line.startsWith('FIXTURE_STATE='));
  if (!marker) throw new Error(`Fixture seed did not return state: ${output}`);
  const fixture = JSON.parse(marker.slice('FIXTURE_STATE='.length)) as FixtureState;
  await fs.writeFile(path.join(runtimeDir, 'fixture-state.json'), JSON.stringify(fixture, null, 2));
  return fixture;
}

async function stopProcess(pid: number | undefined) {
  if (!pid) return;
  if (process.platform === 'win32') {
    await run('taskkill', ['/PID', String(pid), '/T', '/F']).catch(() => undefined);
  } else {
    try {
      process.kill(pid, 'SIGTERM');
    } catch {
      // Process may already have exited.
    }
  }
}

export async function startRuntime() {
  const { DEBUG: _debug, ...runtimeEnvironment } = process.env;
  await fs.rm(runtimeDir, { recursive: true, force: true });
  await fs.mkdir(runtimeDir, { recursive: true });
  await run('docker', ['rm', '-f', dbContainer]).catch(() => undefined);
  await run('docker', [
    'run', '-d', '--name', dbContainer,
    '-e', `POSTGRES_USER=${dbUser}`,
    '-e', `POSTGRES_PASSWORD=${dbPassword}`,
    '-e', `POSTGRES_DB=${dbName}`,
    '-p', '127.0.0.1::5432',
    'postgres:18.4'
  ]);
  const portText = await run('docker', ['port', dbContainer, '5432/tcp']);
  const dbPort = Number(portText.match(/:(\d+)\s*$/m)?.[1]);
  if (!Number.isInteger(dbPort)) throw new Error(`Could not parse PostgreSQL port: ${portText}`);
  await waitFor(async () => {
    const output = await run('docker', [
      'exec', dbContainer, 'pg_isready', '-U', dbUser, '-d', dbName
    ]).catch(() => '');
    return output.includes('accepting connections');
  }, 'PostgreSQL');

  const backend = spawnLogged(mavenCommand(), [
    '-DskipTests',
    'spring-boot:run',
    '-Dspring-boot.run.arguments=--server.port=8080'
  ], repoRoot, {
    ...runtimeEnvironment,
    JAVA_HOME: undefined,
    SPRING_APPLICATION_JSON: JSON.stringify({
      logging: {
        level: {
          root: 'INFO',
          'com.campusguinness': 'INFO'
        }
      }
    }),
    DB_HOST: '127.0.0.1',
    DB_PORT: String(dbPort),
    DB_NAME: dbName,
    DB_USERNAME: dbUser,
    DB_PASSWORD: dbPassword
  });
  await waitForHttp('http://127.0.0.1:8080/actuator/health', 'Spring Boot');
  const fixture = await seedDatabase();
  const frontend = spawnLogged(
    process.platform === 'win32' ? 'npm.cmd' : 'npm',
    ['run', 'dev', '--', '--host', '127.0.0.1', '--port', '5173'],
    process.cwd(),
    process.env
  );
  await waitForHttp('http://127.0.0.1:5173/', 'Vite');

  await fs.writeFile(runtimeFile, JSON.stringify({
    dbContainer,
    dbPort,
    backendPid: backend.child.pid ?? 0,
    frontendPid: frontend.child.pid ?? 0,
    fixture
  } satisfies RuntimeState, null, 2));
}

export async function stopRuntime() {
  let state: RuntimeState | null = null;
  try {
    state = JSON.parse(await fs.readFile(runtimeFile, 'utf8')) as RuntimeState;
  } catch {
    // Setup may have failed before the state file was written.
  }
  await stopProcess(state?.frontendPid);
  await stopProcess(state?.backendPid);
  await run('docker', ['rm', '-f', state?.dbContainer ?? dbContainer]).catch(() => undefined);
}
