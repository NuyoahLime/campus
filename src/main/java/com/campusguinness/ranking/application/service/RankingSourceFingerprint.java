package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.query.model.RankingProjectDetail;
import com.campusguinness.ranking.application.query.model.RankingScoreSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class RankingSourceFingerprint {

    private RankingSourceFingerprint() {
    }

    static String calculate(
            RankingProjectDetail project, List<RankingScoreSource> sources) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, project.activityProjectId());
        append(canonical, project.currentRuleVersionId());
        append(canonical, project.scoreStorageType());
        append(canonical, project.comparisonDirection());
        append(canonical, project.effectiveScoreRule());
        append(canonical, project.gradeOrder());
        append(canonical, project.allowTie());

        sources.stream()
                .sorted(Comparator
                        .comparing((RankingScoreSource source) ->
                                source.studentId().toString())
                        .thenComparing(source -> source.scoreAttemptId().toString()))
                .forEach(source -> {
                    append(canonical, source.scoreAttemptId());
                    append(canonical, source.studentId());
                    append(canonical, source.canonicalScoreValue());
                    append(canonical, source.scoreBusinessTime());
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }
}
