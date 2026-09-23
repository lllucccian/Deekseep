package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Module settings sub-screen for manual context compaction: the three numbers that shape a capsule,
 * the size of the conversation the user is looking at, and the way into the stored capsules.
 *
 * <p>Nothing here triggers on its own. {@link #show} used to carry two switches that armed an
 * automatic fold; both are gone, and the threshold is now only a line the button reports against.
 */
final class ContextCompactionUi {
    private static int dp(Activity a, float n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }

    static void show(final Activity a) {
        final boolean dark = (a.getResources().getConfiguration().uiMode & 48) == 32;
        final int ink = dark ? 0xFFF2F2F4 : 0xFF18181C;
        final int muted = dark ? 0xFFA8A8B0 : 0xFF6C6C74;
        final int canvas = dark ? 0xFF17171B : 0xFFF7F7F9;
        final int surface = dark ? 0xFF242429 : Color.WHITE;
        final ChatContextCompactor.Value draft = ChatContextCompactor.get();
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(canvas, dp(a, 24)));
        LinearLayout header = new LinearLayout(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(a, 8), dp(a, 8), dp(a, 12), dp(a, 8));
        TextView back = text(a, "‹", ink, 34, Gravity.CENTER);
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        TextView heading = text(a, "上下文压缩", ink, 20, Gravity.CENTER_VERTICAL);
        heading.setTypeface(null, 1);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(a, 44), 1));
        root.addView(header);

        ScrollView scroll = new ScrollView(a);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 28));
        scroll.addView(body);

        final TextView thresholdValue = valueText(a, charsLabel(a, draft.thresholdChars), ink);
        body.addView(clickableRow(a, surface, ink, muted, "建议压缩线",
                "当前对话超过这个字数时，聊天页的「压缩对话」按钮会提示已超过建议值；不会自动压缩",
                thresholdValue,
                v -> promptNumber(a, "建议压缩线", draft.thresholdChars,
                        ChatContextCompactor.MIN_THRESHOLD,
                        ChatContextCompactor.MAX_THRESHOLD, value -> {
                            draft.thresholdChars = value;
                            thresholdValue.setText(charsLabel(a, value));
                        })));

        final TextView keepValue = valueText(a, turnsLabel(a, draft.keepRecentTurns), ink);
        body.addView(clickableRow(a, surface, ink, muted, "保留最近轮数",
                "最近这些轮对话按原文保留，更早的折叠为摘要", keepValue,
                v -> promptNumber(a, "保留最近轮数", draft.keepRecentTurns,
                        ChatContextCompactor.MIN_KEEP_TURNS,
                        ChatContextCompactor.MAX_KEEP_TURNS, value -> {
                            draft.keepRecentTurns = value;
                            keepValue.setText(turnsLabel(a, value));
                        })));

        final TextView capsuleValue = valueText(a, charsLabel(a, draft.capsuleChars), ink);
        body.addView(clickableRow(a, surface, ink, muted, "摘要字数上限",
                "压缩后摘要的最大长度，也是注入时的上下文开销", capsuleValue,
                v -> promptNumber(a, "摘要字数上限", draft.capsuleChars,
                        ChatContextCompactor.MIN_CAPSULE_CHARS,
                        ChatContextCompactor.MAX_CAPSULE_CHARS, value -> {
                            draft.capsuleChars = value;
                            capsuleValue.setText(charsLabel(a, value));
                        })));

        final String sid = ChatCompactionEntryUi.currentConversationId();
        final ClassLoader cl = Main.hostClassLoaderForUi();
        int used = ChatCompactionBridge.usageFor(sid, cl);
        LinearLayout usageRow = settingRow(a, surface);
        usageRow.addView(copy(a, "当前对话用量",
                        used > 0 ? charsLabel(a, used) + " / " + charsLabel(a, draft.thresholdChars)
                                : "暂时读不到当前对话", ink, muted),
                new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams usageLp = new LinearLayout.LayoutParams(-1, -2);
        usageLp.topMargin = dp(a, 10);
        body.addView(usageRow, usageLp);

        LinearLayout recordsRow = linkRow(a, surface, ink, muted, "压缩记录",
                "查看、编辑、放入当前对话或删除已保存的摘要",
                v -> {
                    dialog.dismiss();
                    ChatCompactionEntryUi.showRecords(a);
                });
        LinearLayout.LayoutParams recordsLp = new LinearLayout.LayoutParams(-1, -2);
        recordsLp.topMargin = dp(a, 10);
        recordsRow.setLayoutParams(recordsLp);
        body.addView(recordsRow);

        TextView compactNow = text(a, "压缩对话（新建并继续）", Color.WHITE, 16, Gravity.CENTER);
        compactNow.setTypeface(null, 1);
        compactNow.setBackground(round(dark ? 0xFF3A3A42 : 0xFF4D6BFE, dp(a, 13)));
        LinearLayout.LayoutParams compactLp = new LinearLayout.LayoutParams(-1, dp(a, 50));
        compactLp.topMargin = dp(a, 18);
        body.addView(compactNow, compactLp);

        TextView save = text(a, "保存并应用", Color.WHITE, 16, Gravity.CENTER);
        save.setTypeface(null, 1);
        save.setBackground(round(0xFF4D6BFE, dp(a, 13)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(a, 52));
        saveLp.topMargin = dp(a, 10);
        body.addView(save, saveLp);

        compactNow.setOnClickListener(v -> {
            // The fold reads its numbers from disk, not from this draft, so persist first: otherwise
            // a threshold the user just typed would not be the one the confirmation reports against.
            ChatContextCompactor.save(draft);
            dialog.dismiss();
            ChatCompactionEntryUi.showCompactConfirm(a);
        });
        save.setOnClickListener(v -> {
            if (ChatContextCompactor.save(draft)) {
                Toast.makeText(a, UiLanguage.dynamic(a, "上下文压缩设置已保存"),
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(a, UiLanguage.dynamic(a, "上下文压缩设置保存失败"),
                        Toast.LENGTH_SHORT).show();
            }
        });

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            int screenWidth = a.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = a.getResources().getDisplayMetrics().heightPixels;
            // The card scrolls, so its height must not be clamped to the width: on a tall screen a
            // square window pushed the 压缩记录 row and both buttons out of view.
            int width = Math.min(screenWidth - dp(a, 32), dp(a, 460));
            int height = screenHeight - dp(a, 96);
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogWindow.setDimAmount(0.28f);
            dialogWindow.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialogWindow.setLayout(width, height);
        }
    }

    private interface IntSink {
        void accept(int value);
    }

    private static void promptNumber(Activity a, String title, int current,
            final int min, final int max, final IntSink sink) {
        final EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(current));
        input.setSelectAllOnFocus(true);
        int inset = dp(a, 20);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(a);
        frame.setPadding(inset, 0, inset, 0);
        frame.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(a)
                .setTitle(UiLanguage.dynamic(a, title) + "（" + min + "–" + max + "）")
                .setView(frame)
                .setNegativeButton(UiLanguage.dynamic(a, "取消"), null)
                .setPositiveButton(UiLanguage.dynamic(a, "保存"), (dialog, which) -> {
                    int value;
                    try {
                        value = Integer.parseInt(input.getText().toString().trim());
                    } catch (Throwable ignored) {
                        value = Integer.MIN_VALUE;
                    }
                    if (value == Integer.MIN_VALUE || value < min || value > max) {
                        Toast.makeText(a, UiLanguage.dynamic(a, "请输入 ") + min + " – " + max,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sink.accept(value);
                })
                .show();
    }

    private static String charsLabel(Activity a, int count) {
        return count + UiLanguage.dynamic(a, " 字");
    }

    private static String turnsLabel(Activity a, int count) {
        return count + UiLanguage.dynamic(a, " 轮");
    }

    private static LinearLayout clickableRow(Activity a, int surface, int ink, int muted,
            String title, String desc, TextView value, View.OnClickListener onClick) {
        LinearLayout row = settingRow(a, surface);
        row.addView(copy(a, title, desc, ink, muted),
                new LinearLayout.LayoutParams(0, -2, 1));
        value.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, android.R.drawable.arrow_down_float, 0);
        value.setCompoundDrawablePadding(dp(a, 4));
        row.addView(value, new LinearLayout.LayoutParams(dp(a, 104), dp(a, 44)));
        row.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 10);
        row.setLayoutParams(lp);
        return row;
    }

    /** A row that only navigates: no value, no switch, just the copy and a chevron. */
    private static LinearLayout linkRow(Activity a, int surface, int ink, int muted,
            String title, String desc, View.OnClickListener onClick) {
        LinearLayout row = settingRow(a, surface);
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(copy(a, title, desc, ink, muted),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text(a, "›", muted, 22, Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(-2, dp(a, 44)));
        row.setOnClickListener(onClick);
        return row;
    }

    private static TextView valueText(Activity a, String value, int ink) {
        return text(a, value, ink, 15, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
    }

    private static LinearLayout settingRow(Activity a, int color) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 10), dp(a, 12));
        row.setBackground(round(color, dp(a, 16)));
        return row;
    }

    private static LinearLayout copy(Activity a, String title, String desc, int ink, int muted) {
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(a, title, ink, 16, Gravity.START);
        heading.setTypeface(null, 1);
        column.addView(heading);
        if (desc != null && desc.length() > 0) {
            TextView detail = text(a, desc, muted, 12, Gravity.START);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(a, 3);
            column.addView(detail, lp);
        }
        return column;
    }

    private static TextView text(Activity a, String value, int color, float size, int gravity) {
        TextView view = new TextView(a);
        view.setText(UiLanguage.dynamic(a, value));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setGravity(gravity);
        return view;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
