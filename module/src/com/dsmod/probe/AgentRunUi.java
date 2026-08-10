package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** User-visible history and recovery controls for the interactive Agent pipeline. */
final class AgentRunUi {
    private AgentRunUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeekseepUi.isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F0 : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF73777E;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 8), statusBarHeight(activity),
                dp(activity, 10), 0);
        bar.setBackgroundColor(barColor);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));

        TextView back = text(activity, "\u2039", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        back.setClickable(true);
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));
        TextView title = text(activity,
                UiLanguage.text(activity, "Agent 运行记录", "Agent runs"),
                18, textColor, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        bar.addView(title, titleParams);
        final TextView refresh = text(activity,
                UiLanguage.text(activity, "刷新", "Refresh"),
                14, dark ? 0xFF9DB1FF : 0xFF3158D8, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(dp(activity, 10), dp(activity, 8),
                dp(activity, 10), dp(activity, 8));
        refresh.setClickable(true);
        bar.addView(refresh);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable render = new Runnable() {
            @Override public void run() {
                render(activity, content, dark, cardColor, textColor, subColor, this);
            }
        };
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                Main.resumeAgentOutbox(activity);
                render.run();
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                close(dialog, root);
            }
        });

        render.run();
        UiLanguage.localizeTree(activity, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(background));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
        root.setTranslationX(activity.getResources().getDisplayMetrics().widthPixels);
        root.animate().translationX(0f).setDuration(220L).start();
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(
                    android.content.DialogInterface ignored,
                    int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    close(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static void render(
            final Activity activity, final LinearLayout content,
            final boolean dark, final int cardColor,
            final int textColor, final int subColor,
            final Runnable rerender) {
        content.removeAllViews();
        final List<AgentRunStore.Record> records = AgentRunStore.snapshot();
        int active = 0;
        int pending = 0;
        int finished = 0;
        for (AgentRunStore.Record record : records) {
            if (record.hasPendingResult()) pending++;
            else if (record.isFinished()) finished++;
            else active++;
        }

        LinearLayout summary = card(activity, cardColor);
        TextView headline = text(activity,
                UiLanguage.text(activity,
                        "执行中 " + active + " · 待回传 " + pending
                                + " · 已结束 " + finished,
                        "Active " + active + " · Pending delivery " + pending
                                + " · Finished " + finished),
                15, textColor, true);
        headline.setPadding(dp(activity, 15), dp(activity, 14),
                dp(activity, 15), dp(activity, 5));
        summary.addView(headline);
        TextView explanation = text(activity,
                UiLanguage.text(activity,
                        "工具结果会绑定原对话持久保存。切回对应对话后会自动继续；"
                                + "进程重启不会自动重做有副作用的操作。",
                        "Tool results remain bound to their original chat. They resume when that "
                                + "chat is available, and side effects are never repeated after a restart."),
                12, subColor, false);
        explanation.setPadding(dp(activity, 15), 0,
                dp(activity, 15), dp(activity, 12));
        explanation.setLineSpacing(dp(activity, 2), 1f);
        summary.addView(explanation);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(activity, 10), 0,
                dp(activity, 10), dp(activity, 12));
        TextView retryAll = action(activity,
                UiLanguage.text(activity, "重试待回传", "Retry pending"),
                dark, textColor);
        retryAll.setEnabled(pending > 0);
        retryAll.setAlpha(pending > 0 ? 1f : 0.42f);
        actions.addView(retryAll, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = action(activity,
                UiLanguage.text(activity, "清除已结束", "Clear finished"),
                dark, textColor);
        clear.setEnabled(finished > 0);
        clear.setAlpha(finished > 0 ? 1f : 0.42f);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearParams.leftMargin = dp(activity, 8);
        actions.addView(clear, clearParams);
        summary.addView(actions);
        content.addView(summary);

        retryAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                int count = 0;
                for (AgentRunStore.Record record : AgentRunStore.pending()) {
                    if (Main.retryAgentOutbox(activity, record.outboxId)) count++;
                }
                Toast.makeText(activity, UiLanguage.text(activity,
                        "已重新排队 " + count + " 条结果",
                        count + " result(s) queued again"),
                        Toast.LENGTH_SHORT).show();
                rerender.run();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                int count = Main.clearFinishedAgentRuns();
                Toast.makeText(activity, UiLanguage.text(activity,
                        "已清除 " + count + " 条记录",
                        "Cleared " + count + " record(s)"),
                        Toast.LENGTH_SHORT).show();
                rerender.run();
            }
        });

        if (records.isEmpty()) {
            TextView empty = text(activity,
                    UiLanguage.text(activity,
                            "还没有 Agent 工具运行记录",
                            "There are no Agent tool runs yet"),
                    14, subColor, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(activity, 16), dp(activity, 56),
                    dp(activity, 16), dp(activity, 30));
            content.addView(empty);
            DeekseepUi.addBuildFooter(activity, content, subColor);
            return;
        }

        for (final AgentRunStore.Record record : records) {
            LinearLayout item = card(activity, cardColor);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            itemParams.topMargin = dp(activity, 10);
            content.addView(item, itemParams);

            LinearLayout heading = new LinearLayout(activity);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setPadding(dp(activity, 15), dp(activity, 13),
                    dp(activity, 15), dp(activity, 3));
            boolean chinese = UiLanguage.isChinese(activity);
            TextView tool = text(activity,
                    AgentToolConfig.displayName(record.tool, chinese),
                    15, textColor, true);
            heading.addView(tool, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView state = text(activity,
                    stateLabel(activity, record.state), 12,
                    stateColor(record.state, dark), true);
            state.setPadding(dp(activity, 8), dp(activity, 4),
                    dp(activity, 8), dp(activity, 4));
            state.setBackground(rounded(
                    stateBackground(record.state, dark), dp(activity, 9)));
            heading.addView(state);
            item.addView(heading);

            String time = new SimpleDateFormat(
                    "MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(record.updatedAt));
            String scope = compactId(record.scope);
            String callId = compactId(record.callId);
            TextView metadata = text(activity,
                    time + "  ·  " + scope + "  ·  " + callId,
                    11, subColor, false);
            metadata.setPadding(dp(activity, 15), 0,
                    dp(activity, 15), dp(activity, 9));
            item.addView(metadata);

            if (record.detail.length() > 0
                    && !AgentRunStore.STATE_CANCELLED.equals(record.state)) {
                TextView detail = text(activity, record.detail,
                        12, subColor, false);
                detail.setLineSpacing(dp(activity, 2), 1f);
                detail.setPadding(dp(activity, 15), 0,
                        dp(activity, 15), dp(activity, 10));
                item.addView(detail);
            }

            if (record.hasPendingResult()) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(activity, 10), 0,
                        dp(activity, 10), dp(activity, 11));
                TextView retry = action(activity,
                        UiLanguage.text(activity, "立即重试", "Retry now"),
                        dark, textColor);
                row.addView(retry, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView cancel = action(activity,
                        UiLanguage.text(activity, "取消回传", "Cancel delivery"),
                        dark, dark ? 0xFFFF9B9B : 0xFFC23B3B);
                LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cancelParams.leftMargin = dp(activity, 8);
                row.addView(cancel, cancelParams);
                item.addView(row);
                retry.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        boolean ok = Main.retryAgentOutbox(activity, record.outboxId);
                        Toast.makeText(activity, UiLanguage.text(activity,
                                ok ? "结果已重新排队" : "结果暂时无法重试",
                                ok ? "Result queued again" : "Result cannot be retried yet"),
                                Toast.LENGTH_SHORT).show();
                        rerender.run();
                    }
                });
                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        new AlertDialog.Builder(activity)
                                .setTitle(UiLanguage.text(activity,
                                        "取消这条结果回传？",
                                        "Cancel this result delivery?"))
                                .setMessage(UiLanguage.text(activity,
                                        "工具不会重新执行，但模型也不会收到这条结果并继续当前 Agent 循环。",
                                        "The tool will not run again, but the model will not receive "
                                                + "this result or continue the current Agent loop."))
                                .setNegativeButton(UiLanguage.text(activity,
                                        "返回", "Back"), null)
                                .setPositiveButton(UiLanguage.text(activity,
                                        "取消回传", "Cancel delivery"),
                                        (dialog, which) -> {
                                            Main.cancelAgentOutbox(record.outboxId);
                                            rerender.run();
                                        })
                                .show();
                    }
                });
            }
        }
        DeekseepUi.addBuildFooter(activity, content, subColor);
    }

    private static String stateLabel(Context context, String state) {
        if (AgentRunStore.STATE_EXECUTING.equals(state)) {
            return UiLanguage.text(context, "执行中", "Running");
        }
        if (AgentRunStore.STATE_WAITING_USER.equals(state)) {
            return UiLanguage.text(context, "等待回答", "Waiting for answer");
        }
        if (AgentRunStore.STATE_RESULT_READY.equals(state)) {
            return UiLanguage.text(context, "等待回传", "Pending delivery");
        }
        if (AgentRunStore.STATE_DELIVERING.equals(state)) {
            return UiLanguage.text(context, "正在回传", "Delivering");
        }
        if (AgentRunStore.STATE_WAITING_CHAT.equals(state)) {
            return UiLanguage.text(context, "等待原对话", "Waiting for chat");
        }
        if (AgentRunStore.STATE_COMPLETED.equals(state)) {
            return UiLanguage.text(context, "已完成", "Completed");
        }
        if (AgentRunStore.STATE_FAILED.equals(state)) {
            return UiLanguage.text(context, "失败", "Failed");
        }
        return UiLanguage.text(context, "已取消", "Cancelled");
    }

    private static int stateColor(String state, boolean dark) {
        if (AgentRunStore.STATE_FAILED.equals(state)
                || AgentRunStore.STATE_CANCELLED.equals(state)) {
            return dark ? 0xFFFFA4A4 : 0xFFC23B3B;
        }
        if (AgentRunStore.STATE_COMPLETED.equals(state)) {
            return dark ? 0xFF8FE2B1 : 0xFF238653;
        }
        return dark ? 0xFFAFC0FF : 0xFF3158D8;
    }

    private static int stateBackground(String state, boolean dark) {
        if (AgentRunStore.STATE_FAILED.equals(state)
                || AgentRunStore.STATE_CANCELLED.equals(state)) {
            return dark ? 0x333F1111 : 0xFFFFE8E8;
        }
        if (AgentRunStore.STATE_COMPLETED.equals(state)) {
            return dark ? 0x33205B38 : 0xFFE4F6EC;
        }
        return dark ? 0x333F4E86 : 0xFFE7ECFF;
    }

    private static String compactId(String value) {
        String safe = value == null ? "" : value;
        if (safe.length() <= 14) return safe;
        return safe.substring(0, 6) + "…" + safe.substring(safe.length() - 6);
    }

    private static TextView action(
            Context context, String value, boolean dark, int color) {
        TextView out = text(context, value, 13, color, true);
        out.setGravity(Gravity.CENTER);
        out.setPadding(dp(context, 8), dp(context, 9),
                dp(context, 8), dp(context, 9));
        out.setBackground(rounded(
                dark ? 0xFF38383C : 0xFFF0F1F4, dp(context, 10)));
        out.setClickable(true);
        return out;
    }

    private static LinearLayout card(Context context, int color) {
        LinearLayout out = new LinearLayout(context);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setBackground(rounded(color, dp(context, 14)));
        return out;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static TextView text(
            Context context, String value, float size,
            int color, boolean bold) {
        TextView out = new TextView(context);
        out.setText(value == null ? "" : value);
        out.setTextSize(size);
        out.setTextColor(color);
        if (bold) out.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return out;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return id <= 0 ? 0 : context.getResources().getDimensionPixelSize(id);
    }

    private static void close(final Dialog dialog, final View root) {
        if (dialog == null || !dialog.isShowing()) return;
        root.animate().translationX(root.getResources().getDisplayMetrics().widthPixels)
                .setDuration(180L).withEndAction(new Runnable() {
                    @Override public void run() {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                    }
                }).start();
    }
}
