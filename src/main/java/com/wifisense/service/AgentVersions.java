package com.wifisense.service;

/**
 * Compares agent versions such as "1.9.0" or "2.0.0-beta" number by number ("1.10.0" is newer than "1.9.0", which a
 * text comparison would get wrong). Anything that is not a number counts as 0, so a malformed version is old.
 */
public final class AgentVersions {

    private AgentVersions() {
    }

    /** True when {@code version} is {@code minimum} or newer; false for a missing version. */
    public static boolean atLeast(String version, String minimum) {
        if (version == null || version.isBlank()) {
            return false;
        }
        int[] have = parts(version);
        int[] need = parts(minimum);
        for (int i = 0; i < Math.max(have.length, need.length); i++) {
            int a = i < have.length ? have[i] : 0;
            int b = i < need.length ? need[i] : 0;
            if (a != b) {
                return a > b;
            }
        }
        return true;
    }

    private static int[] parts(String version) {
        String core = version.strip().split("[-+]", 2)[0];
        String[] pieces = core.split("\\.");
        int[] numbers = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                numbers[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException e) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
