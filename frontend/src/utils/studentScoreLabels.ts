const STATUS_LABELS: Record<string, string> = {
  APPROVED: '已确认'
};

const STORAGE_LABELS: Record<string, string> = {
  INTEGER: '整数值',
  DECIMAL: '小数值',
  DURATION: '用时',
  GRADE: '等级'
};

export function labelForStudentScoreStatus(value: string | null | undefined): string {
  return STATUS_LABELS[value ?? ''] ?? '成绩状态';
}

export function labelForStudentScoreStorageType(value: string | null | undefined): string {
  return STORAGE_LABELS[value ?? ''] ?? '成绩类型';
}

export function formatStudentScore(value: string | null, storageType: string | null, unit: string | null): string {
  if (value === null || value === undefined || value === '') return '未提供';
  if (storageType === 'DURATION') {
    const milliseconds = Number(value);
    if (Number.isFinite(milliseconds)) {
      const seconds = milliseconds / 1000;
      return `${seconds.toFixed(3).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')} 秒`;
    }
  }
  return unit ? `${value} ${unit}` : value;
}
