package com.campusguinness.achievement.application.exception;

public class AchievementVerificationCodeGenerationException
        extends RuntimeException {

    public AchievementVerificationCodeGenerationException() {
        super("Unable to generate a unique verification code");
    }
}
