# Student Score Read Contract v1

## Scope

This decision defines the read-only student score experience. It does not decide who may enter, review, correct, invalidate, or otherwise mutate scores.

## Self-scope identity

The authenticated actor is resolved by `CurrentActor`. The server requires exactly one ACTIVE `STUDENT` school membership and derives both `studentId` and `schoolId` from that membership. Student score endpoints do not accept either value from the client.

## Student-visible status

Only `APPROVED` `ScoreAttempt` records are visible to the student. `DRAFT`, `PENDING_REVIEW`, `REJECTED`, and `INVALIDATED` remain hidden by default. The backend lifecycle enum is unchanged.

## Isolation

List and detail queries are filtered by the server-derived student and school identity. A detail request for another student's score or an unknown/non-visible score returns the same not-found result.

## Historical rule snapshot

Score detail reads the rule version through `ActivityProject.ruleVersionId`, preserving the rule snapshot bound to the activity. It does not substitute the current `ChallengeProject` rule version.

## Read and write boundary

This contract adds no score write capability. Existing score write behavior and its authorization remain unchanged and outside this stage.
