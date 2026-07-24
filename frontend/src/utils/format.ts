export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString('zh-CN');
}

export function formatDurationMs(ms: number | null | undefined): string {
  if (ms == null) return '';
  if (ms < 1000) return ms + 'ms';
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return seconds + '秒';
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  if (minutes < 60) return `${minutes}分${remainingSeconds}秒`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}时${remainingMinutes}分${remainingSeconds}秒`;
}
