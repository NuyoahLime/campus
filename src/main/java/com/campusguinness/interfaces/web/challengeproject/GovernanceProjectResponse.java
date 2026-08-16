package com.campusguinness.interfaces.web.challengeproject;

import java.util.List;

public record GovernanceProjectResponse(ChallengeProjectResponse project,
                                        List<RuleVersionResponse> ruleVersions) {}
