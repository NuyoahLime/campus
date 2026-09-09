import { ApiError } from '../api/http';

const NO_USABLE_AUTHORIZATION = 'L3_RANKING_NO_USABLE_AUTHORIZATION';

export function describeL3RankingManagementError(value: unknown): string {
  if (value instanceof ApiError) {
    if (value.status === 401) return 'Please sign in again.';
    if (value.status === 403) return 'This account cannot manage platform rankings.';
    if (value.status === 404) return 'The ranking resource was not found.';
    if (value.status === 409 && value.code === NO_USABLE_AUTHORIZATION) {
      return 'No approved usable L3 authorization currently matches this project and RuleVersion.';
    }
    if (value.status === 409) return 'The ranking state changed. Refresh and try again.';
    if (value.status === 400) {
      return value.responseMessage
        ?? 'The ranking request is invalid. Check the selected project and RuleVersion.';
    }
  }
  return 'The request failed. Try again shortly.';
}
