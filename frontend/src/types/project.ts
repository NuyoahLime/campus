export interface PublicProjectItem {
  projectId: string;
  name: string;
  category: string;
  descriptionSummary: string;
  scoreStorageType: string;
  comparisonDirection: string;
  scoreUnit: string;
}

export interface PublicProjectDetail {
  projectId: string;
  name: string;
  category: string;
  description: string | null;
  venueRequirements: string | null;
  equipmentRequirements: string | null;
  rulesText: string | null;
  scoreStorageType: string;
  scoreIndicatorType: string;
  comparisonDirection: string;
  effectiveScoreRule: string | null;
  allowTie: boolean;
  scoreUnit: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
}

export interface ProjectListFilter {
  keyword?: string;
  category?: string;
  scoreStorageType?: string;
  venueKeyword?: string;
  equipmentKeyword?: string;
}
