import { stopRuntime } from './runtime';

export default async function globalTeardown() {
  await stopRuntime();
}
