package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The chat-page side of manual compaction: the confirmation shown by the 「压缩」 button, the list of
 * stored summaries, and the editor for one of them.
 *
 * <p>A folded summary is only consumed by the target conversation's <em>next</em> send, so editing a
 * record after folding it still takes effect. That is why the confirmation is a plain preview and the
 * compact action does not stop to ask for edits.
 */
final class ChatCompactionEntryUi {
    private static final int BRAND = 0xFF4D6BFE;

    /** Preview length in the confirmation dialog; the whole capsule lives in the record list. */
    private static final int PREVIEW_CHARS = 240;

    /**
     * These screens are all full-screen, so a close and an open issued in the same loop turn overlap
     * their window transitions. Every hand-off between two of them is therefore deferred one beat.
     */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final long HANDOFF_DELAY_MS = 200L;

    private ChatCompactionEntryUi() {}

    /**
     * The conversation the user is looking at. The sidebar hook records it as soon as the user taps
     * a row, so this is known without waiting for a send; the last interactive send is a fallback
     * for builds whose sidebar hook did not fire.
     */
    static String currentConversationId() {
        String sid = HookSessionManagement.sidebarCurrentSid;
        if (sid != null && sid.length() > 0) return sid;
        String last = HookChatPipeline.lastInteractiveConversationId;
        return last == null ? "" : last;
    }

    /** The 「压缩」 button's action: confirm, then fold the open conversation into a new one. */
    static void showCompactConfirm(final Activity a) {
        final String sid = currentConversationId();
        if (sid.length() == 0) {
            showNotice(a, "还没有打开任何对话。已压缩的摘要都能在这里查看、编辑或放入其他对话。");
            return;
        }
        final ClassLoader cl = Main.hostClassLoaderForUi();
        final ChatCompactionBridge.Plan plan = ChatCompactionBridge.plan(sid, cl);
        if (plan == null) {
            showNotice(a, "当前对话不足两条，暂时无需压缩。已保存的摘要仍可查看和放入其他对话。");
            return;
        }
        final ChatContextCompactor.Value config = ChatContextCompactor.get();

        final boolean dark = DeekseepUi.isDark(a);
        final int ink = dark ? 0xFFF2F2F4 : 0xFF18181C;
        final int muted = dark ? 0xFFA8A8B0 : 0xFF6C6C74;
        final int surface = dark ? 0xFF242429 : Color.WHITE;
        final int border = dark ? 0xFF3B3B40 : 0xFFE2E4E8;
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(dark ? 0xFF17171B : 0xFFF7F7F9, dp(a, 20)));
        root.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 14));

        TextView heading = text(a, "压缩对话", ink, 18, Gravity.START);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);

        TextView source = text(a, plan.sourceTitle.length() == 0 ? "未命名对话" : plan.sourceTitle,
                muted, 13, Gravity.START);
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-1, -2);
        sourceLp.topMargin = dp(a, 4);
        root.addView(source, sourceLp);

        boolean past = plan.usedChars >= config.thresholdChars;
        LinearLayout numbers = new LinearLayout(a);
        numbers.setOrientation(LinearLayout.VERTICAL);
        numbers.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        numbers.setBackground(round(surface, dp(a, 12)));
        LinearLayout.LayoutParams numbersLp = new LinearLayout.LayoutParams(-1, -2);
        numbersLp.topMargin = dp(a, 12);
        root.addView(numbers, numbersLp);
        // Numbers are composed from whole phrases, never from bare words: the catalog only has to
        // carry the unit and the two sentences, so no generic word entry can leak into other screens.
        numbers.addView(text(a, plan.usedChars + " / " + config.thresholdChars + " 字"
                        + (past ? " · 已超过建议压缩线" : ""),
                past ? 0xFFD9534F : ink, 13, Gravity.START));
        TextView summaryLine = text(a, "摘要将在新对话的第一条消息里注入，共 "
                        + plan.capsuleChars() + " 字",
                muted, 12, Gravity.START);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.topMargin = dp(a, 4);
        numbers.addView(summaryLine, summaryLp);

        ScrollView preview = new ScrollView(a);
        TextView previewText = text(a, previewOf(plan.capsule), muted, 12, Gravity.START);
        previewText.setLineSpacing(dp(a, 2), 1f);
        preview.addView(previewText);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(a, 120));
        previewLp.topMargin = dp(a, 12);
        root.addView(preview, previewLp);

        TextView confirm = text(a, "压缩并新建对话", Color.WHITE, 16, Gravity.CENTER);
        confirm.setTypeface(null, Typeface.BOLD);
        confirm.setBackground(round(BRAND, dp(a, 13)));
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(-1, dp(a, 48));
        confirmLp.topMargin = dp(a, 14);
        root.addView(confirm, confirmLp);

        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            ChatCompactionBridge.Fold fold = ChatCompactionBridge.commit(plan, cl);
            if (fold == null) {
                toast(a, "压缩失败：读不到宿主数据库或无法新建对话");
            }
        });

        // The mirror of the 「压缩当前对话」 button on the records page: 压缩记录 was only reachable
        // from the settings dialog, so a user who came in through this chip had no way across.
        TextView records = text(a, "压缩记录", BRAND, 15, Gravity.CENTER);
        records.setTypeface(null, Typeface.BOLD);
        records.setBackground(outline(surface, dp(a, 13), border, dp(a, 1)));
        LinearLayout.LayoutParams recordsLp = new LinearLayout.LayoutParams(-1, dp(a, 44));
        recordsLp.topMargin = dp(a, 10);
        root.addView(records, recordsLp);
        records.setOnClickListener(v -> {
            dialog.dismiss();
            showRecords(a);
        });

        TextView cancel = text(a, "取消", muted, 15, Gravity.CENTER);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, dp(a, 42));
        cancelLp.topMargin = dp(a, 4);
        root.addView(cancel, cancelLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(a, 40),
                    dp(a, 420));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * The dead-end states of {@link #showCompactConfirm} — no open conversation, or nothing worth
     * folding. They still hand over to the records list, because the stored summaries are usable
     * regardless of what is on screen; a bare toast left the chip looking like it did nothing.
     */
    private static void showNotice(final Activity a, String message) {
        final boolean dark = DeekseepUi.isDark(a);
        final int ink = dark ? 0xFFF2F2F4 : 0xFF18181C;
        final int muted = dark ? 0xFFA8A8B0 : 0xFF6C6C74;
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(dark ? 0xFF17171B : 0xFFF7F7F9, dp(a, 20)));
        root.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 14));

        TextView heading = text(a, "压缩对话", ink, 18, Gravity.START);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);

        TextView body = text(a, message, muted, 13, Gravity.START);
        body.setLineSpacing(dp(a, 2), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(a, 6);
        root.addView(body, bodyLp);

        TextView records = text(a, "压缩记录", Color.WHITE, 16, Gravity.CENTER);
        records.setTypeface(null, Typeface.BOLD);
        records.setBackground(round(BRAND, dp(a, 13)));
        LinearLayout.LayoutParams recordsLp = new LinearLayout.LayoutParams(-1, dp(a, 48));
        recordsLp.topMargin = dp(a, 14);
        root.addView(records, recordsLp);
        records.setOnClickListener(v -> {
            dialog.dismiss();
            showRecords(a);
        });

        TextView close = text(a, "关闭", muted, 15, Gravity.CENTER);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(a, 42));
        closeLp.topMargin = dp(a, 4);
        root.addView(close, closeLp);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(a, 40),
                    dp(a, 420));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Full-screen list of stored summaries, each one editable, attachable and deletable. */
    static void showRecords(final Activity a) {
        final boolean dark = DeekseepUi.isDark(a);
        final int ink = dark ? 0xFFF0F0F2 : 0xFF17181A;
        final int muted = dark ? 0xFFAAAAB0 : 0xFF6E7279;
        final int surface = dark ? 0xFF252528 : Color.WHITE;
        final Dialog dialog = new Dialog(a,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF1B1B1D : 0xFFF6F7F9);

        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 8), DeekseepUi.statusBarHeight(a), dp(a, 16), 0);
        bar.setBackgroundColor(surface);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(a, 56)
                + DeekseepUi.statusBarHeight(a)));
        TextView back = text(a, "‹", ink, 30, Gravity.CENTER);
        back.setOnClickListener(v -> DeekseepUi.slideOutAndDismiss(dialog, root));
        bar.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        TextView title = text(a, "压缩记录", ink, 18, Gravity.CENTER_VERTICAL);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.leftMargin = dp(a, 8);
        bar.addView(title, titleLp);

        ScrollView scroll = new ScrollView(a);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        List<ChatContextCompactor.Capsule> records = ChatCompactionBridge.capsuleRecords();
        if (records.isEmpty()) {
            TextView empty = text(a, "还没有压缩记录。在聊天页按「压缩」按钮，或在本页下方手动压缩当前对话。",
                    muted, 13, Gravity.START);
            empty.setLineSpacing(dp(a, 3), 1f);
            body.addView(empty);
        }
        final String sid = currentConversationId();
        for (ChatContextCompactor.Capsule record : records) {
            body.addView(recordCard(a, dialog, root, record, sid, dark, surface, ink, muted));
        }

        TextView compactNow = text(a, "压缩当前对话", Color.WHITE, 16, Gravity.CENTER);
        compactNow.setTypeface(null, Typeface.BOLD);
        compactNow.setBackground(round(BRAND, dp(a, 13)));
        LinearLayout.LayoutParams compactLp = new LinearLayout.LayoutParams(-1, dp(a, 50));
        compactLp.topMargin = dp(a, 18);
        body.addView(compactNow, compactLp);
        compactNow.setOnClickListener(v -> {
            DeekseepUi.slideOutAndDismiss(dialog, root);
            showCompactConfirm(a);
        });

        dialog.setContentView(root);
        DeekseepUi.openWithSlide(dialog, root);
    }

    private static View recordCard(final Activity a, final Dialog parent, final View parentRoot,
            final ChatContextCompactor.Capsule record, final String sid, final boolean dark,
            final int surface, final int ink, final int muted) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 10));
        card.setBackground(round(surface, dp(a, 14)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(a, 10);
        card.setLayoutParams(cardLp);

        String label = record.sourceTitle.length() == 0 ? "未命名对话" : record.sourceTitle;
        TextView head = text(a, stamp(record.createdAt) + " · " + label + " · "
                + record.chars() + " 字", ink, 14, Gravity.START);
        head.setTypeface(null, Typeface.BOLD);
        card.addView(head);

        String state = record.delivered()
                ? "已注入" : record.targetSid != null && record.targetSid.length() > 0
                ? "待注入：下一条消息发出时" : "未使用";
        card.addView(text(a, state, muted, 12, Gravity.START));

        TextView body = text(a, record.text, muted, 12, Gravity.START);
        body.setMaxLines(3);
        body.setLineSpacing(dp(a, 2), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(a, 6);
        card.addView(body, bodyLp);

        LinearLayout actions = new LinearLayout(a);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(a, 8);
        card.addView(actions, actionsLp);
        actions.addView(action(a, "编辑", BRAND, v -> {
            DeekseepUi.slideOutAndDismiss(parent, parentRoot);
            MAIN.postDelayed(() -> showEditor(a, record.id), HANDOFF_DELAY_MS);
        }));
        // Offered unless this very conversation already received it: attaching resets the delivery
        // flag, so a record already served to this sid would be injected into it a second time.
        // A record served to some other conversation stays pickable, which is how the same digest
        // is reused in a later conversation.
        boolean servedHere = record.delivered() && sid.equals(record.targetSid);
        if (sid.length() > 0 && !servedHere) {
            actions.addView(action(a, "放入当前对话", BRAND, v -> {
                ChatCompactionBridge.injectInto(record.id, sid);
                reopenList(a, parent);
            }));
        }
        actions.addView(action(a, "删除", 0xFFD9534F, v -> {
            ChatContextCompactor.deleteCapsule(record.id);
            reopenList(a, parent);
        }));
        return card;
    }

    /** Rebuilds the list once the dismissed card's window is free, so the change is visible. */
    private static void reopenList(final Activity a, final Dialog parent) {
        try { parent.dismiss(); } catch (Throwable ignored) {}
        MAIN.postDelayed(() -> {
            if (!a.isFinishing()) showRecords(a);
        }, HANDOFF_DELAY_MS);
    }

    /**
     * Full-screen editor for one record's text. Without it the user could only accept whatever the
     * extractive renderer produced, because nothing else in the module edits a long blob of text.
     */
    static void showEditor(final Activity a, final String recordId) {
        final ChatContextCompactor.Capsule record = ChatContextCompactor.findCapsule(recordId);
        if (record == null) {
            toast(a, "这条记录已经不在了");
            return;
        }
        final boolean dark = DeekseepUi.isDark(a);
        final int ink = dark ? 0xFFF0F0F2 : 0xFF17181A;
        final int muted = dark ? 0xFFAAAAB0 : 0xFF6E7279;
        final int surface = dark ? 0xFF252528 : Color.WHITE;
        final Dialog dialog = new Dialog(a,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF1B1B1D : 0xFFF6F7F9);

        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 8), DeekseepUi.statusBarHeight(a), dp(a, 16), 0);
        bar.setBackgroundColor(surface);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(a, 56)
                + DeekseepUi.statusBarHeight(a)));
        TextView back = text(a, "‹", ink, 30, Gravity.CENTER);
        back.setOnClickListener(v -> DeekseepUi.slideOutAndDismiss(dialog, root));
        bar.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        TextView title = text(a, "编辑摘要", ink, 18, Gravity.CENTER_VERTICAL);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.leftMargin = dp(a, 8);
        bar.addView(title, titleLp);
        TextView save = text(a, "保存", BRAND, 16, Gravity.CENTER);
        save.setTypeface(null, Typeface.BOLD);
        save.setPadding(dp(a, 12), 0, dp(a, 12), 0);
        bar.addView(save, new LinearLayout.LayoutParams(-2, dp(a, 44)));

        final EditText editor = new EditText(a);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(12);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        editor.setTextColor(ink);
        editor.setText(record.text);
        editor.setBackgroundColor(surface);
        editor.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));
        ScrollView scroll = new ScrollView(a);
        scroll.addView(editor);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text(a, "保存后，这条摘要会在目标对话的下一条消息里按新的内容注入。",
                muted, 12, Gravity.START);
        hint.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 16));
        root.addView(hint);

        View.OnClickListener commit = v -> {
            String value = editor.getText().toString();
            if (value.trim().length() == 0) {
                toast(a, "摘要不能为空，可以直接删除这条记录");
                return;
            }
            if (ChatContextCompactor.updateCapsuleText(recordId, value)) {
                toast(a, "已保存");
                reopenList(a, dialog);
            } else {
                toast(a, "保存失败");
            }
        };
        save.setOnClickListener(commit);

        dialog.setContentView(root);
        DeekseepUi.openWithSlide(dialog, root);
    }

    private static String previewOf(String capsule) {
        return ChatContextCompactor.truncate(capsule, PREVIEW_CHARS);
    }

    private static String stamp(long createdAt) {
        try {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(createdAt));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static TextView action(Activity a, String label, int color,
            View.OnClickListener onClick) {
        TextView view = text(a, label, color, 13, Gravity.CENTER);
        view.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));
        view.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(a, 14);
        view.setLayoutParams(lp);
        return view;
    }

    private static void toast(Activity a, String message) {
        try {
            Toast.makeText(a, UiLanguage.dynamic(a, message), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
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

    private static GradientDrawable outline(int fill, float radius, int stroke, int width) {
        GradientDrawable drawable = round(fill, radius);
        drawable.setStroke(width, stroke);
        return drawable;
    }
}
