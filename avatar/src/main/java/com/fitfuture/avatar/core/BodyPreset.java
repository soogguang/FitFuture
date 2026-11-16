package com.fitfuture.avatar.core;

/** 남성형·여성형 기본 체형. PNG 폴더 이름(assets/avatar/{folder})과 같다. */
public enum BodyPreset {
    MALE("male", "남성형"),
    FEMALE("female", "여성형");

    public final String folder;
    public final String label;

    BodyPreset(String folder, String label) {
        this.folder = folder;
        this.label = label;
    }

    public static BodyPreset fromFolder(String folder) {
        for (BodyPreset p : values()) {
            if (p.folder.equals(folder)) return p;
        }
        return FEMALE;
    }

    public static BodyPreset of(WeightSimulator.Sex sex) {
        return sex == WeightSimulator.Sex.MALE ? MALE : FEMALE;
    }
}
