package com.dsmod.probe;

import java.util.List;

public final class ProcessManagerRegressionTest {
    public static void main(String[] args) {
        String ps = "  PID NAME                        STAT\n"
                + "  101 system_server               S\n"
                + " 7244 com.deepseek.chat           S\n"
                + " 7249 com.deepseek.chat:push      T\n"
                + "22299 com.dsmod.probe             S\n"
                + "22310 com.dsmod.probe:audio       S\n"
                + "30000 com.deepseek.chat.evil      S\n";
        List<ProcessManagerActivity.ProcessInfo> items =
                ProcessManagerActivity.parseProcesses(ps);
        require(items.size() == 4, "unexpected target process count " + items.size());
        require(items.get(0).primary(), "DeepSeek main process must sort first");
        require(items.get(1).name.equals("com.deepseek.chat:push"),
                "DeepSeek subprocess missing");
        require(items.get(1).frozen(), "T state must be recognized as frozen");
        require(new ProcessManagerActivity.ProcessInfo(
                        7250, "com.deepseek.chat:worker", "S", true).frozen(),
                "cgroup-v2 frozen state must be recognized even when ps still reports S");
        require(items.get(2).module(), "module main process missing");
        require(items.get(3).name.equals("com.dsmod.probe:audio"),
                "module subprocess missing");
        for (ProcessManagerActivity.ProcessInfo item : items) {
            require(!item.name.endsWith(".evil"), "prefix boundary accepted unrelated package");
            require(item.pid > 1, "unsafe pid accepted");
        }
        ProcessManagerActivity.ProcessInfo target = items.get(0);
        String freeze = ProcessManagerActivity.actionCommand(target, "STOP");
        require(freeze.contains("tr -d '[:space:]'")
                        && freeze.contains("cgroup.freeze")
                        && freeze.contains("frozen 1")
                        && freeze.contains("expected_cg=/apps/uid_${uid}/pid_7244"),
                "freeze command does not normalize ps output or use the process cgroup freezer");
        String resume = ProcessManagerActivity.actionCommand(target, "CONT");
        require(resume.contains("printf 0") && resume.contains("frozen 0"),
                "resume command does not thaw and verify the process cgroup");
        String kill = ProcessManagerActivity.actionCommand(target, "KILL");
        require(kill.contains("cgroup.kill") && kill.contains("kill -KILL 7244"),
                "kill command has neither exact cgroup kill nor signal fallback");
        System.out.println("Process manager regression passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
