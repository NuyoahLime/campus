package com.campusguinness.achievement.application.exception;

public class AchievementVerificationCodeCollisionException
        extends RuntimeException {

    public AchievementVerificationCodeCollisionException() {
        super("Verification code collision");
    }
}
