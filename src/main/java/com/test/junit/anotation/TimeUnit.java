package com.test.junit.anotation;

public enum TimeUnit {
    MILLISECOND, SECOND, MINUTE;

    public java.util.concurrent.TimeUnit toJavaTimeUnit() {
        switch(this) {
            case MILLISECOND: return java.util.concurrent.TimeUnit.MILLISECONDS;
            case SECOND: return java.util.concurrent.TimeUnit.SECONDS;
            case MINUTE: return java.util.concurrent.TimeUnit.MINUTES;
            default: throw new IllegalArgumentException("Unknown TimeUnit");
        }
    }
}
