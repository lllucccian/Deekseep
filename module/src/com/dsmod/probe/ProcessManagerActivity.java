package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Root-backed controller for DeepSeek and Deekseep's own application processes. */
public final class ProcessManagerActivity extends Activity {
    private static final String LOG_TAG = "DeekseepProcess";
    private static final String HOST = "com.deepseek.chat";
    private static final String MODULE = "com.dsmod.probe";
    private static final int OUTPUT_LIMIT = 128 * 1024;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout processList;
    private TextView status;
    private boolean dark;
    private int background;
    private int bar;
    private int card;
    private int text;
    private int secondary;
    private int divider;
    private int accent;
    private volatile boolean destroyed;

    static final class ProcessInfo {
        final int pid;
        final String name;
        final String state;
        final boolean cgroupFrozen;

        ProcessInfo(int pid, String name, String state) {
            this(pid, name, state, false);
        }

        ProcessInfo(int pid, String name, String state, boolean cgroupFrozen) {
            this.pid = pid;
            this.name = name;
            this.state = state == null ? "" : state;
            this.cgroupFrozen = cgroupFrozen;
        }

        boolean frozen() { return cgroupFrozen || state.indexOf('T') >= 0; }
        boolean module() { return name.equals(MODULE) || name.startsWith(MODULE + ":"); }
        boolean primary() { return name.equals(HOST); }
    }

    static final class CommandResult {
        final int code;
        final String output;

        CommandResult(int code, String output) {
            this.code = code;
            this.output = output == null ? "" : output.trim();
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiLanguage.refreshSystem(this);
        render();
        loadProcesses();
    }

    @Override protected void onResume() {
        super.onResume();
        UiLanguage.refreshSystem(this);
        boolean nextDark = isDark();
        if (nextDark != dark) {
            render();
            loadProcesses();
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void render() {
        dark = isDark();
        background = dark ? 0xFF101113 : 0xFFF4F6F8;
        bar = dark ? 0xFF1B1D21 : 0xFFFFFFFF;
        card = dark ? 0xFF1B1D21 : 0xFFFFFFFF;
        text = dark ? 0xFFF2F3F5 : 0xFF17191D;
        secondary = dark ? 0xFF9AA0AA : 0xFF68707C;
        divider = dark ? 0xFF30343A : 0xFFE2E6EB;
        accent = dark ? 0xFF7EA6FF : 0xFF315FBE;
        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(bar);
            window.setNavigationBarColor(background);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                int flags = window.getDecorView().getSystemUiVisibility();
                if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                window.getDecorView().setSystemUiVisibility(flags);
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), statusBarHeight(), dp(8), 0);
        appBar.setBackgroundColor(bar);
        root.addView(appBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56) + statusBarHeight()));

        TextView back = label("‹", 28, text, Typeface.DEFAULT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(rowTouch(false));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        appBar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = label(UiLanguage.text(this, "进程管理", "Process manager"),
                18, text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = dp(6);
        appBar.addView(title, titleLp);

        TextView refresh = label(UiLanguage.text(this, "刷新", "Refresh"),
                14, accent, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(rowTouch(true));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { loadProcesses(); }
        });
        appBar.addView(refresh, new LinearLayout.LayoutParams(dp(64), dp(40)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView intro = label(UiLanguage.text(this,
                "显示 DeepSeek 与 Deekseep 模块的全部活动进程。点击进程可冻结、解冻或杀死；"
                        + "精确进程操作需要授予 Deekseep Root 权限。",
                "Shows every active DeepSeek and Deekseep module process. Tap one to freeze, "
                        + "resume, or kill it. Precise process control requires Root for Deekseep."),
                13, secondary, Typeface.DEFAULT);
        intro.setLineSpacing(dp(2), 1f);
        intro.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(intro);

        status = label(UiLanguage.text(this, "正在读取进程…", "Reading processes…"),
                12, secondary, Typeface.DEFAULT);
        status.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(status);

        processList = new LinearLayout(this);
        processList.setOrientation(LinearLayout.VERTICAL);
        processList.setBackground(rounded(card, 12));
        content.addView(processList, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void loadProcesses() {
        if (processList == null || status == null) return;
        status.setText(UiLanguage.text(this, "正在读取进程…", "Reading processes…"));
        processList.removeAllViews();
        new Thread(new Runnable() {
            @Override public void run() {
                final CommandResult result = runRoot(
                        "/system/bin/ps -A -o PID,NAME,STAT", 6000L);
                List<ProcessInfo> found = result.code == 0
                        ? parseProcesses(result.output) : Collections.<ProcessInfo>emptyList();
                if (result.code == 0 && !found.isEmpty()) {
                    ArrayList<ProcessInfo> resolved = new ArrayList<>(found.size());
                    for (ProcessInfo info : found) {
                        resolved.add(new ProcessInfo(info.pid, info.name, info.state,
                                isCgroupFrozen(info)));
                    }
                    found = resolved;
                }
                final List<ProcessInfo> processes = found;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (destroyed || processList == null) return;
                        if (result.code != 0) {
                            Log.w(LOG_TAG, "process list failed code=" + result.code
                                    + " output=" + result.output);
                            status.setText(UiLanguage.text(ProcessManagerActivity.this,
                                    "读取失败：请在 Root 管理器中授权 Deekseep。",
                                    "Could not read processes. Grant Root access to Deekseep."));
                            addEmpty(UiLanguage.text(ProcessManagerActivity.this,
                                    "授权后返回此页并点击“刷新”",
                                    "After granting access, return here and tap Refresh"));
                            return;
                        }
                        status.setText(UiLanguage.text(ProcessManagerActivity.this,
                                "找到 " + processes.size() + " 个相关进程",
                                "Found " + processes.size() + " related processes"));
                        if (processes.isEmpty()) {
                            addEmpty(UiLanguage.text(ProcessManagerActivity.this,
                                    "DeepSeek 与模块当前均未运行",
                                    "DeepSeek and the module are not running"));
                            return;
                        }
                        for (int i = 0; i < processes.size(); i++) {
                            if (i > 0) processList.addView(makeDivider());
                            processList.addView(processRow(processes.get(i)));
                        }
                    }
                });
            }
        }, "Deekseep-Process-List").start();
    }

    private View processRow(final ProcessInfo info) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(14), dp(12));
        row.setMinimumHeight(dp(64));
        row.setBackground(rowTouch(false));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(info.name + " " + roleLabel(info), 15, text,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.addView(name);
        String stateText = info.frozen()
                ? UiLanguage.text(this, "已冻结", "Frozen")
                : UiLanguage.text(this, "运行中", "Running");
        copy.addView(label("PID " + info.pid + " · " + stateText + " · " + info.state,
                12, secondary, Typeface.DEFAULT));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chip = label(info.frozen()
                        ? UiLanguage.text(this, "冻结", "Frozen")
                        : UiLanguage.text(this, "活动", "Active"),
                11, info.frozen() ? 0xFFD29347 : accent,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(5), dp(9), dp(5));
        chip.setBackground(rounded(dark ? 0xFF262A30 : 0xFFF0F3F7, 7));
        row.addView(chip);
        TextView arrow = label("›", 22, secondary, Typeface.DEFAULT);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(-2, -2);
        arrowLp.leftMargin = dp(8);
        row.addView(arrow, arrowLp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showActions(info); }
        });
        return row;
    }

    private void showActions(final ProcessInfo info) {
        String detail = info.name + " " + roleLabel(info) + "\nPID " + info.pid + " · "
                + (info.frozen() ? UiLanguage.text(this, "已冻结", "Frozen")
                : UiLanguage.text(this, "运行中", "Running"));
        final boolean currentController = info.pid == android.os.Process.myPid();
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(UiLanguage.text(this, "进程操作", "Process actions"))
                .setMessage(detail + (currentController
                        ? "\n\n" + UiLanguage.text(this,
                        "当前控制界面不能冻结自身；可以杀死并关闭模块界面。",
                        "The controller cannot freeze itself; it may be killed to close the module UI.")
                        : ""))
                .setPositiveButton(info.frozen()
                                ? UiLanguage.text(this, "解冻", "Resume")
                                : UiLanguage.text(this, "冻结", "Freeze"), null)
                .setNeutralButton(UiLanguage.text(this, "杀死", "Kill"), null)
                .setNegativeButton(UiLanguage.text(this, "取消", "Cancel"), null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                TextView freeze = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                freeze.setTextColor(accent);
                freeze.setEnabled(!currentController);
                freeze.setAlpha(currentController ? 0.4f : 1f);
                freeze.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        dialog.dismiss();
                        confirm(info, info.frozen() ? "CONT" : "STOP");
                    }
                });
                TextView kill = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                kill.setTextColor(dark ? 0xFFFFAAA3 : 0xFFB3261E);
                kill.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        dialog.dismiss();
                        confirm(info, "KILL");
                    }
                });
            }
        });
        dialog.show();
    }

    private void confirm(final ProcessInfo info, final String action) {
        final boolean kill = "KILL".equals(action);
        final boolean resume = "CONT".equals(action);
        String verb = kill ? UiLanguage.text(this, "杀死", "kill")
                : resume ? UiLanguage.text(this, "解冻", "resume")
                : UiLanguage.text(this, "冻结", "freeze");
        new AlertDialog.Builder(this)
                .setTitle(UiLanguage.text(this, "确认进程操作", "Confirm process action"))
                .setMessage(UiLanguage.text(this,
                        "确定要" + verb + " " + info.name + "（PID " + info.pid + "）吗？",
                        "Are you sure you want to " + verb + " " + info.name
                                + " (PID " + info.pid + ")?"))
                .setPositiveButton(verb, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        executeAction(info, action);
                    }
                })
                .setNegativeButton(UiLanguage.text(this, "取消", "Cancel"), null)
                .show();
    }

    private void executeAction(final ProcessInfo info, final String action) {
        status.setText(UiLanguage.text(this, "正在执行进程操作…", "Applying process action…"));
        new Thread(new Runnable() {
            @Override public void run() {
                String command = actionCommand(info, action);
                final CommandResult result = runRoot(command, 5000L);
                Log.i(LOG_TAG, "action=" + action + " pid=" + info.pid
                        + " name=" + info.name + " code=" + result.code
                        + " output=" + result.output);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        if (result.code == 0) {
                            Toast.makeText(ProcessManagerActivity.this,
                                    UiLanguage.text(ProcessManagerActivity.this,
                                            "进程操作已完成", "Process action completed"),
                                    Toast.LENGTH_SHORT).show();
                            main.postDelayed(new Runnable() {
                                @Override public void run() { loadProcesses(); }
                            }, 300L);
                        } else {
                            status.setText(UiLanguage.text(ProcessManagerActivity.this,
                                    "操作失败：目标已变化或 Root 权限不足。",
                                    "Action failed: the target changed or Root permission is insufficient."));
                            Toast.makeText(ProcessManagerActivity.this,
                                    result.output.length() == 0 ? status.getText() : result.output,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "Deekseep-Process-Action").start();
    }

    private boolean isCgroupFrozen(ProcessInfo info) {
        CommandResult result = runRoot(targetValidation(info)
                + "events=/sys/fs/cgroup$cg/cgroup.events; "
                + "[ -r \"$events\" ] || exit 46; "
                + "/system/bin/grep -q '^frozen 1$' \"$events\"", 2500L);
        return result.code == 0;
    }

    static String actionCommand(ProcessInfo info, String action) {
        String value = "CONT".equals(action) ? "0" : "1";
        StringBuilder command = new StringBuilder(targetValidation(info));
        if ("STOP".equals(action) || "CONT".equals(action)) {
            command.append("control=/sys/fs/cgroup$cg/cgroup.freeze; ")
                    .append("[ -w \"$control\" ] || { echo freezer_unavailable; exit 46; }; ")
                    .append("/system/bin/printf ").append(value)
                    .append(" > \"$control\" || exit 47; ")
                    .append("/system/bin/sleep 0.1; ")
                    .append("/system/bin/grep -q '^frozen ").append(value)
                    .append("$' /sys/fs/cgroup$cg/cgroup.events")
                    .append(" || { echo freezer_state_mismatch; exit 48; }");
        } else if ("KILL".equals(action)) {
            command.append("control=/sys/fs/cgroup$cg/cgroup.kill; ")
                    .append("if [ -w \"$control\" ]; then ")
                    .append("/system/bin/printf 1 > \"$control\"; ")
                    .append("else /system/bin/kill -KILL ").append(info.pid).append("; fi");
        } else {
            command.append("echo invalid_action; exit 49");
        }
        return command.toString();
    }

    private static String targetValidation(ProcessInfo info) {
        String expected = shellQuote(info == null ? "" : info.name);
        int pid = info == null ? -1 : info.pid;
        return "actual=$(/system/bin/ps -p " + pid
                + " -o NAME= | /system/bin/tr -d '[:space:]'); "
                + "[ \"$actual\" = " + expected
                + " ] || { echo target_changed; exit 45; }; "
                + "uid=$(/system/bin/awk '/^Uid:/{print $2; exit}' /proc/" + pid
                + "/status); case \"$uid\" in ''|*[!0-9]*) echo invalid_uid; exit 45;; esac; "
                + "cg=$(/system/bin/awk -F: '$1==\"0\"{print $3; exit}' /proc/" + pid
                + "/cgroup); expected_cg=/apps/uid_${uid}/pid_" + pid + "; "
                + "[ \"$cg\" = \"$expected_cg\" ]"
                + " || { echo invalid_cgroup; exit 45; }; ";
    }

    static List<ProcessInfo> parseProcesses(String output) {
        ArrayList<ProcessInfo> result = new ArrayList<>();
        if (output == null) return result;
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.length() == 0 || line.startsWith("PID ")) continue;
            String[] columns = line.split("\\s+", 3);
            if (columns.length < 2 || !columns[0].matches("[0-9]{1,9}")) continue;
            String name = columns[1];
            if (!(name.equals(HOST) || name.startsWith(HOST + ":")
                    || name.equals(MODULE) || name.startsWith(MODULE + ":"))) continue;
            try {
                int pid = Integer.parseInt(columns[0]);
                if (pid > 1) result.add(new ProcessInfo(
                        pid, name, columns.length >= 3 ? columns[2] : "?"));
            } catch (NumberFormatException ignored) {}
        }
        Collections.sort(result, new Comparator<ProcessInfo>() {
            @Override public int compare(ProcessInfo left, ProcessInfo right) {
                int leftGroup = left.name.equals(HOST) ? 0
                        : left.name.startsWith(HOST + ":") ? 1
                        : left.name.equals(MODULE) ? 2 : 3;
                int rightGroup = right.name.equals(HOST) ? 0
                        : right.name.startsWith(HOST + ":") ? 1
                        : right.name.equals(MODULE) ? 2 : 3;
                if (leftGroup != rightGroup) return leftGroup - rightGroup;
                return left.name.compareTo(right.name);
            }
        });
        return result;
    }

    private CommandResult runRoot(String command, long timeoutMs) {
        java.lang.Process process = null;
        InputStream input = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("/system/bin/su", "-c", command);
            builder.redirectErrorStream(true);
            process = builder.start();
            input = process.getInputStream();
            final InputStream stream = input;
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Thread reader = new Thread(new Runnable() {
                @Override public void run() {
                    byte[] buffer = new byte[4096];
                    int count;
                    try {
                        while ((count = stream.read(buffer)) >= 0) {
                            if (count == 0) continue;
                            int allowed = Math.min(count, OUTPUT_LIMIT - bytes.size());
                            if (allowed > 0) bytes.write(buffer, 0, allowed);
                            if (bytes.size() >= OUTPUT_LIMIT) break;
                        }
                    } catch (Throwable ignored) {}
                }
            }, "Deekseep-Root-Output");
            reader.start();
            long deadline = SystemClock.elapsedRealtime() + Math.max(1000L, timeoutMs);
            Integer exit = null;
            while (SystemClock.elapsedRealtime() < deadline) {
                try {
                    exit = Integer.valueOf(process.exitValue());
                    break;
                } catch (IllegalThreadStateException running) {
                    SystemClock.sleep(25L);
                }
            }
            if (exit == null) {
                process.destroy();
                return new CommandResult(-2, "command timed out");
            }
            reader.join(800L);
            return new CommandResult(exit.intValue(),
                    new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable error) {
            return new CommandResult(-1, error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()));
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
            if (process != null) try { process.destroy(); } catch (Throwable ignored) {}
        }
    }

    private String roleLabel(ProcessInfo info) {
        if (info.primary()) return UiLanguage.text(this, "（主进程）", "(Main process)");
        if (info.module() && info.name.equals(MODULE)) {
            return UiLanguage.text(this, "（模块进程）", "(Module process)");
        }
        if (info.module()) return UiLanguage.text(this, "（模块子进程）", "(Module subprocess)");
        return UiLanguage.text(this, "（DeepSeek 子进程）", "(DeepSeek subprocess)");
    }

    private void addEmpty(String message) {
        TextView empty = label(message, 13, secondary, Typeface.DEFAULT);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(24), dp(20), dp(24));
        processList.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private View makeDivider() {
        View view = new View(this);
        view.setBackgroundColor(divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView label(String value, int sp, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(typeface);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private StateListDrawable rowTouch(boolean rounded) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(dark ? 0xFF2B2E34 : 0xFFE9EDF3);
        if (rounded) pressed.setCornerRadius(dp(8));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        if (rounded) normal.setCornerRadius(dp(8));
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        states.setEnterFadeDuration(70);
        states.setExitFadeDuration(110);
        return states;
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : getResources().getDimensionPixelSize(id);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private static String shellQuote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
    }
}
