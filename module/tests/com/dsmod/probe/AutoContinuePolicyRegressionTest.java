package com.dsmod.probe;

public final class AutoContinuePolicyRegressionTest {
    public static void main(String[] args) {
        require(AutoContinuePolicy.shouldResume(
                        true, "WIP", "INCOMPLETE", "ASSISTANT"),
                "a paused assistant stream must resume");
        require(AutoContinuePolicy.shouldResume(
                        true, "CHECKING", "INCOMPLETE", "ASSISTANT"),
                "a paused checking stream must resume");

        require(!AutoContinuePolicy.shouldResume(
                        false, "WIP", "INCOMPLETE", "ASSISTANT"),
                "the default-off switch must be honored");
        require(!AutoContinuePolicy.shouldResume(
                        true, "INCOMPLETE", "INCOMPLETE", "ASSISTANT"),
                "loading an old incomplete message must not resume it");
        require(!AutoContinuePolicy.shouldResume(
                        true, "WIP", "INCOMPLETE", "USER"),
                "a user message must not invoke assistant resume");
        require(!AutoContinuePolicy.shouldResume(
                        true, "WIP", "FINISHED", "ASSISTANT"),
                "a completed response must not resume");
        require(!AutoContinuePolicy.shouldResume(
                        true, "WIP", "INTERRUPTED", "ASSISTANT"),
                "a non-resumable interruption must not be forced");

        System.out.println("Auto-continue generation policy regression passed.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
