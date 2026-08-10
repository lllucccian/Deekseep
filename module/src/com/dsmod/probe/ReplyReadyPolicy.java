package com.dsmod.probe;

/** Pure state policy for background reply-ready notifications. */
final class ReplyReadyPolicy {
    private ReplyReadyPolicy() {}

    static boolean shouldNotify(String previousStatus, String nextStatus,
                                String role, boolean hostForeground) {
        return !hostForeground
                && "ASSISTANT".equals(role)
                && "FINISHED".equals(nextStatus)
                && isGenerating(previousStatus);
    }

    static boolean isGenerating(String status) {
        return "WIP".equals(status)
                || "CHECKING".equals(status)
                || "INCOMPLETE".equals(status);
    }
}
