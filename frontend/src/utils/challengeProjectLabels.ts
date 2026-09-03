const PROJECT_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已上架',
  ARCHIVED: '已下架'
};

const SCORE_STORAGE_LABELS: Record<string, string> = {
  INTEGER: '整数',
  DECIMAL: '小数',
  DURATION: '时长',
  GRADE: '等级'
};

const SCORE_INDICATOR_LABELS: Record<string, string> = {
  NUMERIC: '数值',
  TIME: '时间',
  GRADE: '等级'
};

const COMPARISON_DIRECTION_LABELS: Record<string, string> = {
  HIGHER_BETTER: '越大越好',
  LOWER_BETTER: '越小越好',
  GRADE_ORDER: '按等级顺序',
  NO_RANKING: '不参与排名'
};

const EFFECTIVE_SCORE_RULE_LABELS: Record<string, string> = {
  BEST: '最好成绩',
  LAST: '最后成绩',
  ADMIN_DESIGNATED: '管理员指定'
};

// Categories remain extensible; known platform identifiers receive user-facing labels.
const CATEGORY_LABELS: Record<string, string> = {
  ATHLETICS: '田径运动',
  SPEED: '速度挑战',
  ACADEMIC: '学术挑战',
  MATH: '数学挑战',
  SCIENCE: '科学挑战',
  SPORT: '体育运动',
  SPORTS: '体育运动'
};

function label(value: string | null | undefined, labels: Record<string, string>): string {
  if (!value) return '未填写';
  return labels[value] ?? value;
}

export function labelForProjectStatus(value: string | null | undefined): string {
  return label(value, PROJECT_STATUS_LABELS);
}

export function labelForCategory(value: string | null | undefined): string {
  return label(value, CATEGORY_LABELS);
}

export function labelForScoreStorageType(value: string | null | undefined): string {
  return label(value, SCORE_STORAGE_LABELS);
}

export function labelForScoreIndicatorType(value: string | null | undefined): string {
  return label(value, SCORE_INDICATOR_LABELS);
}

export function labelForComparisonDirection(value: string | null | undefined): string {
  return label(value, COMPARISON_DIRECTION_LABELS);
}

export function labelForEffectiveScoreRule(value: string | null | undefined): string {
  return label(value, EFFECTIVE_SCORE_RULE_LABELS);
}
