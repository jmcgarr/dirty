package net.sonofgarr.dirty.util;

public enum ANSICode {

    RESET (0),
    RED(31),
    GREEN(32),
    YELLOW(33);

    private final int code;
    ANSICode(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }
}
