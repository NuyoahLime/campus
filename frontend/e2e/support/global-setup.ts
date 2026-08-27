import { startRuntime } from './runtime';

export default async function globalSetup() {
  await startRuntime();
}
