package com.campusguinness.result.application.format;

/*
 * Format-only edit acceptance ledger. Every entry names the test method that owns its assertion.
 *
 * FMT-01  appendIsImmutableAndAdvancesExplicitHead - immutable ResultVersion.
 * FMT-02  appendIsImmutableAndAdvancesExplicitHead - immutable ActivityResult pointers/status.
 * FMT-03  appendIsImmutableAndAdvancesExplicitHead - append-only records.
 * FMT-04  appendIsImmutableAndAdvancesExplicitHead - explicit head advances.
 * FMT-05  appendIsImmutableAndAdvancesExplicitHead - per-version revision increments.
 * FMT-06  exactVersionEligibilityMatrix[first-not-submitted] - first-publication internal allowed.
 * FMT-07  exactVersionEligibilityMatrix[first-pending] - pending candidate denied.
 * FMT-08  exactVersionEligibilityMatrix[first-approved] - approved candidate denied.
 * FMT-09  exactVersionEligibilityMatrix[public] - exact active public allowed.
 * FMT-10  exactVersionEligibilityMatrix[replacement-draft-candidate] - candidate-only denied.
 * FMT-11  exactVersionEligibilityMatrix[replacement-draft-old-public] - old public allowed.
 * FMT-12  exactVersionEligibilityMatrix[replacement-internal] - exact internal allowed.
 * FMT-13  exactVersionEligibilityMatrix[replacement-pending-candidate] - replacement pending denied.
 * FMT-14  exactVersionEligibilityMatrix[replacement-pending-old-public] - pending old public allowed.
 * FMT-15  exactVersionEligibilityMatrix[replacement-approved-candidate] - replacement approved denied.
 * FMT-16  exactVersionEligibilityMatrix[replacement-approved-old-public] - approved old public allowed.
 * FMT-17  exactVersionEligibilityMatrix[replacement-rejected-candidate] - rejected candidate denied.
 * FMT-18  exactVersionEligibilityMatrix[replacement-rejected-old-public] - rejected old public allowed.
 * FMT-19  deniesIneligibleLifecycleStates[ANOMALY_PENDING] - anomaly denied.
 * FMT-20  deniesIneligibleLifecycleStates[CANCELLED] - cancelled denied.
 * FMT-21  exactVersionEligibilityMatrix[withdrawn-internal] - historical internal denied.
 * FMT-22  exactVersionEligibilityMatrix[withdrawn-active-public] - independently active public allowed.
 * FMT-23  exactVersionEligibilityMatrix[reset-blocked-public] - blocked historical public denied.
 * FMT-24  exactVersionEligibilityMatrix[post-reset-exact-internal] - post-reset exact internal allowed.
 * FMT-25  exactVersionEligibilityMatrix[historical-non-current] - non-current version denied.
 * FMT-26  rejectsInvalidReasons - reason required, trimmed and bounded.
 * FMT-27  rejectsInvalidPayloads[null/array] - presentation object required.
 * FMT-28  rejectsInvalidPayloads[unknown] - top-level presentation keys strict.
 * FMT-29  rejectsInvalidPayloads[paragraph shape] - paragraph collection required.
 * FMT-30  rejectsInvalidPayloads[emphasis shape] - emphasis collection required.
 * FMT-31  rejectsInvalidPayloads[paragraph style] - paragraph style allowlist.
 * FMT-32  rejectsInvalidPayloads[paragraph gap] - full plain-text coverage.
 * FMT-33  rejectsInvalidPayloads[paragraph start] - starts at zero.
 * FMT-34  rejectsInvalidPayloads[paragraph overlap] - paragraph non-overlap.
 * FMT-35  rejectsInvalidPayloads[paragraph unknown] - paragraph keys strict.
 * FMT-36  rejectsInvalidPayloads[fractional offset] - integral offsets.
 * FMT-37  rejectsInvalidPayloads[emphasis bounds] - summary bounds.
 * FMT-38  rejectsInvalidPayloads[emphasis style] - emphasis style allowlist.
 * FMT-39  rejectsInvalidPayloads[emphasis overlap] - same-style non-overlap.
 * FMT-40  rejectsInvalidPayloads[duplicate range] - duplicate range rejected.
 * FMT-41  rejectsInvalidPayloads[emphasis unknown] - emphasis keys strict.
 * FMT-42  unicodeOffsetsUseCodePoints - Unicode code-point offsets.
 * FMT-43  canonicalizesEmphasisOrder - canonical persistence order.
 * FMT-44  firstEditRaceHasOneWinnerAndNoOrphanAcrossTwentyRuns - first-head race.
 * FMT-45  existingHeadRaceHasOneWinnerAndNoOrphanAcrossTwentyRuns - existing-head CAS race.
 * FMT-46  db01MissingResultRejected - record result FK.
 * FMT-47  db02WrongResultVersionPairRejected - record same-result version FK.
 * FMT-48  db03DuplicateRevisionRejected - revision uniqueness.
 * FMT-49  db04OneHeadPerVersion - one head per version.
 * FMT-50  db05HeadWrongResultVersionPair/db06HeadRecordFromOtherVersion/db07HeadRecordFromOtherResult - exact head FKs.
 * FMT-51  db08MissingEditorRejected - editor FK.
 * FMT-52  db09BlankReasonRejected/db10LongReasonRejected - DB reason checks.
 * FMT-53  db11ZeroRevisionRejected/db12NonObjectPayloadRejected - DB revision/payload checks.
 * FMT-54  db13ReferencedVersionDeleteRestricted/db14HeadRecordDeleteRestricted - delete restrictions.
 * FMT-55  missingHeadIsValidNoOverlayForEveryAudience - missing head means no overlay.
 * FMT-56  malformedPublicOverlayFailsClosedAndNeverFallsBackToBase - public broken head 404.
 * FMT-57  malformedPreferredInternalOverlayFailsClosedWithoutPublicFallback - student no fallback.
 * FMT-58  malformedManagementOverlayReturnsControlledConsistencyConflict - management broken head 409.
 * FMT-59  exactVersionOverlaysRemainIsolatedAndPublicStudentDtosHideAuditMetadata - V1/V2 isolation.
 * FMT-60  exactVersionOverlaysRemainIsolatedAndPublicStudentDtosHideAuditMetadata - DTO metadata policy.
 * FMT-61  makePublicStateRetainsOverlayBoundToExactVersion - makePublic preservation.
 * FMT-62  takedownAndResetPreserveExactVersionHistoryWhilePublicReadStaysDenied - blocked history preservation.
 * FMT-63  historyIsVersionIsolatedAndOrderedByRevisionAscending - exact-version history isolation.
 * FMT-64  historyIsVersionIsolatedAndOrderedByRevisionAscending - revision ASC authority.
 * FMT-65  ResultFormatEditControllerTest - HTTP role, anti-enumeration and unknown-key contract.
 * FMT-66  malformedUnselectedPublicOverlayDoesNotPoisonSelectedInternal - select before overlay.
 * FMT-67  malformedPreferredInternalOverlayFailsClosedWithoutPublicFallback - selected corruption fails.
 * FMT-68  semanticInvalidStoredStyleMakesPublicReadNotFound - stored style revalidation.
 * FMT-69  semanticInvalidStoredParagraphCoverageMakesSelectedStudentReadNotFound - stored coverage revalidation.
 * FMT-70  semanticInvalidStoredEmphasisMakesManagementReadConflict - stored emphasis revalidation.
 * FMT-71  unknownStoredPresentationKeyFailsClosed - stored unknown-key revalidation.
 * FMT-72  appendIsImmutableAndAdvancesExplicitHead - response/history persisted editedAt equality.
 * FMT-73  ResultFormatEditControllerTest and SecurityConfig matchers - filter and method security.
 */
final class FmtCoverageLedger {
    private FmtCoverageLedger() {
    }
}
