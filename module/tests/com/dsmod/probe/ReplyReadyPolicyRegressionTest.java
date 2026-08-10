package com.dsmod.probe;

public final class ReplyReadyPolicyRegressionTest {
    public static void main(String[] args) {
        require(ReplyReadyPolicy.shouldNotify(
                        "WIP", "FINISHED", "ASSISTANT", false),
                "a background assistant WIP -> FINISHED transition must notify");
        require(ReplyReadyPolicy.shouldNotify(
                        "CHECKING", "FINISHED", "ASSISTANT", false),
                "a background checking -> finished transition must notify");
        require(ReplyReadyPolicy.shouldNotify(
                        "INCOMPLETE", "FINISHED", "ASSISTANT", false),
                "a background streamed response finishing must notify");

        require(!ReplyReadyPolicy.shouldNotify(
                        "WIP", "FINISHED", "ASSISTANT", true),
                "foreground completion must not disturb the user");
        require(!ReplyReadyPolicy.shouldNotify(
                        "WIP", "FINISHED", "USER", false),
                "a user-message status update must not notify");
        require(!ReplyReadyPolicy.shouldNotify(
                        "FINISHED", "FINISHED", "ASSISTANT", false),
                "loading an already-finished history item must not notify");
        require(!ReplyReadyPolicy.shouldNotify(
                        null, "FINISHED", "ASSISTANT", false),
                "a completion without a preceding generating state must not notify");
        require(!ReplyReadyPolicy.shouldNotify(
                        "WIP", "CONTENT_FILTER", "ASSISTANT", false),
                "a failed or filtered generation must not masquerade as ready");

        System.out.println("Reply-ready notification policy regression passed.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
