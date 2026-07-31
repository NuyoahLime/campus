package com.campusguinness.achievement.application.exception;

public class AchievementNotFoundException extends RuntimeException {

    public AchievementNotFoundException() {
        super("Achievement record not found");
    }
}
