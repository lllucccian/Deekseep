package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Claude-style bottom question sheet used by the hidden {@code ask_user} tool. */
final class AgentQuestionUi {
    interface AnswerListener {
        void onAnswer(String scope, String visibleAnswer);
    }

    private static final Object LOCK = new Object();
    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static WeakReference<Dialog> activeDialog = new WeakReference<>(null);

    private AgentQuestionUi() {}

    static boolean enqueue(Activity activity, HeartbeatToolProtocol.ToolCall call,
                           AnswerListener listener) {
        if (activity == null || call == null || listener == null
                || call.questions == null || call.questions.isEmpty()
                || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            return false;
        }
        synchronized (LOCK) {
            QUEUE.addLast(new Pending(activity, call, listener));
        }
        showNext();
        return true;
    }

    private static void showNext() {
        final Pending pending;
        synchronized (LOCK) {
            Dialog active = activeDialog.get();
            if (active != null && active.isShowing()) return;
            activeDialog = new WeakReference<>(null);
            pending = QUEUE.pollFirst();
        }
        if (pending == null) return;
        final Activity activity = pending.activity.get();
        if (activity == null || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            showNext();
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!show(activity, pending)) showNext();
            }
        });
    }

    private static boolean show(final Activity activity, final Pending pending) {
        try {
            final boolean dark = DeekseepUi.isDark(activity);
            final int sheetColor = dark ? 0xFF262628 : 0xFFFFFFFF;
            final int textColor = dark ? 0xFFF2F2F2 : 0xFF1D1D1F;
            final int subColor = dark ? 0xFFAAAAB0 : 0xFF77777E;
            final int dividerColor = dark ? 0xFF3A3A3E : 0xFFE6E6E8;
            final int selectedColor = dark ? 0xFF35406B : 0xFFE9EDFF;
            final int optionColor = dark ? 0xFF303033 : 0xFFF7F7F5;
            final List<HeartbeatToolProtocol.Question> questions =
                    pending.call.questions;
            final String[] answers = new String[questions.size()];

            final Dialog dialog = new Dialog(
                    activity, android.R.style.Theme_Translucent_NoTitleBar);
            final LinearLayout sheet = new LinearLayout(activity);
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setPadding(dp(activity, 20), dp(activity, 18),
                    dp(activity, 20), dp(activity, 16));
            GradientDrawable sheetBackground = new GradientDrawable();
            sheetBackground.setColor(sheetColor);
            sheetBackground.setCornerRadii(new float[]{
                    dp(activity, 28), dp(activity, 28),
                    dp(activity, 28), dp(activity, 28),
                    0, 0, 0, 0
            });
            sheet.setBackground(sheetBackground);
            if (Build.VERSION.SDK_INT >= 21) sheet.setElevation(dp(activity, 18));

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(activity,
                    questions.size() == 1
                            ? questions.get(0).text
                            : UiLanguage.text(activity,
                            "AI 想确认几件事", "The AI has a few questions"),
                    21, textColor, true);
            title.setLineSpacing(dp(activity, 2), 1f);
            header.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView close = text(activity, "\u00d7", 28, subColor, false);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription(UiLanguage.text(
                    activity, "关闭", "Close"));
            close.setClickable(true);
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View ignored) {
                    dialog.dismiss();
                }
            });
            header.addView(close, new LinearLayout.LayoutParams(
                    dp(activity, 44), dp(activity, 44)));
            sheet.addView(header);

            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(false);
            LinearLayout questionList = new LinearLayout(activity);
            questionList.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(questionList, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            scrollParams.topMargin = dp(activity, 8);
            sheet.addView(scroll, scrollParams);

            final ArrayList<ArrayList<View>> optionViews = new ArrayList<>();
            for (int questionIndex = 0;
                 questionIndex < questions.size(); questionIndex++) {
                final int currentQuestion = questionIndex;
                HeartbeatToolProtocol.Question question =
                        questions.get(questionIndex);
                if (questions.size() > 1) {
                    TextView questionTitle = text(activity,
                            (questionIndex + 1) + ". " + question.text,
                            16, textColor, true);
                    LinearLayout.LayoutParams titleParams =
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    titleParams.topMargin = questionIndex == 0
                            ? dp(activity, 2) : dp(activity, 20);
                    titleParams.bottomMargin = dp(activity, 7);
                    questionList.addView(questionTitle, titleParams);
                }

                ArrayList<View> currentViews = new ArrayList<>();
                optionViews.add(currentViews);
                for (int optionIndex = 0;
                     optionIndex < question.options.size(); optionIndex++) {
                    final String option = question.options.get(optionIndex);
                    final LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(activity, 14), dp(activity, 11),
                            dp(activity, 14), dp(activity, 11));
                    GradientDrawable rowBackground = rounded(
                            optionColor, dp(activity, 14));
                    row.setBackground(rowBackground);
                    row.setClickable(true);
                    row.setFocusable(true);
                    TextView number = text(activity,
                            String.valueOf(optionIndex + 1),
                            15, subColor, true);
                    number.setGravity(Gravity.CENTER);
                    GradientDrawable numberBackground = rounded(
                            dark ? 0xFF3B3B3E : 0xFFF0F0ED,
                            dp(activity, 20));
                    number.setBackground(numberBackground);
                    row.addView(number, new LinearLayout.LayoutParams(
                            dp(activity, 40), dp(activity, 40)));
                    TextView label = text(activity, option, 17, textColor, false);
                    label.setLineSpacing(dp(activity, 1), 1f);
                    LinearLayout.LayoutParams labelParams =
                            new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    labelParams.leftMargin = dp(activity, 14);
                    row.addView(label, labelParams);
                    final ArrayList<View> siblingViews = currentViews;
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View ignored) {
                            answers[currentQuestion] = option;
                            for (View sibling : siblingViews) {
                                sibling.setBackground(rounded(
                                        sibling == row ? selectedColor : optionColor,
                                        dp(activity, 14)));
                            }
                            if (allAnswered(answers)) {
                                submit(dialog, pending, questions,
                                        answers, "", activity);
                            }
                        }
                    });
                    currentViews.add(row);
                    LinearLayout.LayoutParams rowParams =
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.topMargin = dp(activity, 7);
                    questionList.addView(row, rowParams);
                }
            }

            View divider = new View(activity);
            divider.setBackgroundColor(dividerColor);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
            dividerParams.topMargin = dp(activity, 14);
            sheet.addView(divider, dividerParams);

            final EditText custom = new EditText(activity);
            custom.setTextColor(textColor);
            custom.setHintTextColor(subColor);
            custom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            custom.setHint(questions.size() == 1
                    ? UiLanguage.text(activity,
                    "输入你自己的答案\u2026", "Type your own answer\u2026")
                    : UiLanguage.text(activity,
                    "输入补充；未选择的问题将使用此答案\u2026",
                    "Add details; unanswered questions use this text\u2026"));
            custom.setBackgroundColor(0x00000000);
            custom.setSingleLine(false);
            custom.setMaxLines(4);
            custom.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            custom.setImeOptions(EditorInfo.IME_ACTION_SEND);

            FrameLayout answerBar = new FrameLayout(activity);
            answerBar.setPadding(0, dp(activity, 6), 0, 0);
            answerBar.addView(custom, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            final TextView send = text(activity, "\u2191", 24,
                    dark ? 0xFFFFFFFF : 0xFFFFFFFF, false);
            send.setGravity(Gravity.CENTER);
            send.setClickable(true);
            send.setContentDescription(UiLanguage.text(
                    activity, "发送答案", "Send answer"));
            send.setBackground(rounded(
                    dark ? 0xFF6C7DD5 : DeekseepUi.BRAND,
                    dp(activity, 24)));
            FrameLayout.LayoutParams sendParams = new FrameLayout.LayoutParams(
                    dp(activity, 48), dp(activity, 48), Gravity.END | Gravity.CENTER_VERTICAL);
            answerBar.addView(send, sendParams);
            custom.setPadding(dp(activity, 4), dp(activity, 8),
                    dp(activity, 62), dp(activity, 8));
            sheet.addView(answerBar);

            View.OnClickListener submit = new View.OnClickListener() {
                @Override public void onClick(View ignored) {
                    String freeform = custom.getText() == null
                            ? "" : custom.getText().toString().trim();
                    if (freeform.length() == 0 && !allAnswered(answers)) {
                        Toast.makeText(activity, UiLanguage.text(activity,
                                "请选择答案，或输入自己的想法",
                                "Choose an answer or type your own"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submit(dialog, pending, questions,
                            answers, freeform, activity);
                }
            };
            send.setOnClickListener(submit);
            custom.setOnEditorActionListener(
                    new TextView.OnEditorActionListener() {
                        @Override public boolean onEditorAction(
                                TextView view, int actionId, KeyEvent event) {
                            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
                            send.performClick();
                            return true;
                        }
                    });

            dialog.setContentView(sheet);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override public void onDismiss(DialogInterface ignored) {
                    synchronized (LOCK) {
                        Dialog active = activeDialog.get();
                        if (active == dialog) activeDialog = new WeakReference<>(null);
                    }
                    showNext();
                }
            });
            Window window = dialog.getWindow();
            if (window == null) return false;
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.34f;
            window.setAttributes(attributes);
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            synchronized (LOCK) {
                activeDialog = new WeakReference<>(dialog);
            }
            dialog.show();
            int maxHeight = Math.round(
                    activity.getResources().getDisplayMetrics().heightPixels * 0.82f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            sheet.setTranslationY(maxHeight);
            sheet.animate().translationY(0f).setDuration(240L).start();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void submit(
            Dialog dialog, Pending pending,
            List<HeartbeatToolProtocol.Question> questions,
            String[] selected, String freeform, Context context) {
        String visible = buildVisibleAnswer(questions, selected, freeform);
        if (visible.length() == 0) return;
        try {
            pending.listener.onAnswer(pending.call.scope, visible);
        } catch (Throwable ignored) {
            Toast.makeText(context, UiLanguage.text(context,
                    "答案发送失败", "Could not send the answer"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        dialog.dismiss();
    }

    static String buildVisibleAnswer(
            List<HeartbeatToolProtocol.Question> questions,
            String[] selected, String freeform) {
        if (questions == null || questions.isEmpty()) return "";
        String custom = sanitize(freeform, 1200);
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < questions.size(); index++) {
            HeartbeatToolProtocol.Question question = questions.get(index);
            String answer = selected != null && index < selected.length
                    ? sanitize(selected[index], 500) : "";
            if (answer.length() == 0) answer = custom;
            else if (custom.length() > 0 && index == questions.size() - 1) {
                answer = answer + "\n" + custom;
            }
            if (answer.length() == 0) return "";
            if (out.length() > 0) out.append("\n\n");
            out.append("Question\uff1a")
                    .append(sanitize(question.text, 500))
                    .append("\nAnswer\uff1a")
                    .append(answer);
        }
        return out.toString();
    }

    private static boolean allAnswered(String[] answers) {
        if (answers == null || answers.length == 0) return false;
        for (String answer : answers) {
            if (answer == null || answer.trim().length() == 0) return false;
        }
        return true;
    }

    private static String sanitize(String value, int max) {
        if (value == null) return "";
        String safe = value
                .replace(HeartbeatToolProtocol.CONTROL_START, "")
                .replace(HeartbeatToolProtocol.CONTROL_END, "")
                .replace(HeartbeatToolProtocol.EVENT_START, "")
                .replace(HeartbeatToolProtocol.EVENT_END, "")
                .replace('\r', ' ')
                .trim();
        if (safe.length() > max) safe = safe.substring(0, max).trim();
        return safe;
    }

    private static TextView text(Context context, String value, float sp,
                                 int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Context context, float value) {
        return DeekseepUi.dp(context, value);
    }

    private static final class Pending {
        final WeakReference<Activity> activity;
        final HeartbeatToolProtocol.ToolCall call;
        final AnswerListener listener;

        Pending(Activity activity, HeartbeatToolProtocol.ToolCall call,
                AnswerListener listener) {
            this.activity = new WeakReference<>(activity);
            this.call = call;
            this.listener = listener;
        }
    }
}
