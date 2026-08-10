package com.dsmod.probe;

/** Pure state policy for DeepSeek's native automatic continue-generation action. */
final class AutoContinuePolicy {
    private AutoContinuePolicy() {}

    static boolean shouldResume(boolean enabled, String previousStatus,
                                String nextStatus, String role) {
        return enabled
                && "ASSISTANT".equals(role)
                && "INCOMPLETE".equals(nextStatus)
                && ("WIP".equals(previousStatus) || "CHECKING".equals(previousStatus));
    }
}
