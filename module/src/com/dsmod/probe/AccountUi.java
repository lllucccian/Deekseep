package com.dsmod.probe;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.PathParser;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

final class AccountUi {

    private static volatile String pendingExportJson;
    private static volatile String pendingExportFileName;
    private static WeakReference<Dialog> activeDialog = new WeakReference<>(null);
    private static WeakReference<LinearLayout> activeContent = new WeakReference<>(null);
    private static final Set<String> multiSelect = new HashSet<>();
    private static boolean multiSelectMode;
    private static boolean expanded;
    private static View multiSelectToolbar;
    private static ViewGroup toolbarParent;
    private static WeakReference<FrameLayout> accountListViewport = new WeakReference<>(null);
    private static WeakReference<LinearLayout> accountListContent = new WeakReference<>(null);
    private static WeakReference<View> collapsedFade = new WeakReference<>(null);
    private static WeakReference<TextView> expandControl = new WeakReference<>(null);
    private static int collapsedListHeight;

    private AccountUi() {}

    private static boolean usesClosedAccountV2() {
        if (HostCompat.isV236()) return true;
        if (HostCompat.isV241()) return true;
        return false;
    }

    static int dp(Activity a, float v) { return DeekseepUi.dp(a, v); }
    static int dp(Context c, float v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    static void show(final Activity act) {
        if (!usesClosedAccountV2()) {
            AccountUiLegacy.show(act);
            return;
        }
        final boolean dark = DeekseepUi.isDark(act);
        final int bg    = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int card  = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text  = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub   = dark ? 0xFF9A9A9E : 0xFF888888;
        final int div   = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dlg = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        final FrameLayout root = new FrameLayout(act);
        root.setBackgroundColor(bg);

        LinearLayout main = new LinearLayout(act);
        main.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(act, 8), dp(act, 10), dp(act, 16), dp(act, 10));
        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(text);
        back.setPadding(dp(act, 12), 0, dp(act, 12), 0);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { DeekseepUi.slideOutAndDismiss(dlg, main); }
        });
        bar.addView(back);
        TextView title = new TextView(act);
        title.setText("多账号");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        bar.addView(title);
        View barSpacer = new View(act);
        bar.addView(barSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView importAction = headerAction(act, "导入", text);
        importAction.setTranslationY(dp(act, 8));
        importAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { confirmImport(act); }
        });
        bar.addView(importAction, new LinearLayout.LayoutParams(dp(act, 52), dp(act, 40)));
        TextView addAction = headerAction(act, "添加", DeekseepUi.BRAND);
        addAction.setTranslationY(dp(act, 8));
        addAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { confirmAdd(act); }
        });
        bar.addView(addAction, new LinearLayout.LayoutParams(dp(act, 52), dp(act, 40)));
        main.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(act);
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 24));
        content.setClickable(true);
        content.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (multiSelectMode) exitMultiSelect(act);
            }
        });
        sv.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        main.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbarParent = root;

        rebuild(act, dlg, content, dark, card, text, sub, div);

        activeDialog = new WeakReference<>(dlg);
        activeContent = new WeakReference<>(content);

        UiLanguage.localizeTree(act, main);
        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(bg));
        }
        DeekseepUi.openWithSlide(dlg, main);
        dlg.setOnKeyListener(new Dialog.OnKeyListener() {
            public boolean onKey(DialogInterface d, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                    if (multiSelectMode) {
                        exitMultiSelect(act);
                        return true;
                    }
                    DeekseepUi.slideOutAndDismiss(dlg, main);
                    return true;
                }
                return false;
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface d) {
                if (activeDialog.get() == dlg) {
                    activeDialog = new WeakReference<>(null);
                    activeContent = new WeakReference<>(null);
                }
                removeMultiSelectToolbar();
            }
        });
    }

    private static void rebuild(final Activity act, final Dialog dlg, final LinearLayout content,
                               boolean dark, int card, int text, int sub, int div) {
        content.removeAllViews();

        final List<AccountManager.Account> accounts =
                AccountManager.accountsForUi(act.getClassLoader());

        LinearLayout listCard = makeCard(act, card);
        listCard.setPadding(0, dp(act, 4), 0, dp(act, 4));

        if (accounts.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText("未检测到已登录账号。请先在 DeepSeek 正常登录一个账号。");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setTextColor(sub);
            empty.setPadding(dp(act, 16), dp(act, 20), dp(act, 16), dp(act, 20));
            listCard.addView(empty);
        } else {
            content.addView(accountOverview(act, accounts, text, sub), cardLp(act, 0));
            // 列表始终完整创建；折叠时仅裁剪视口。这样展开是向下延展，不会整页闪烁重建。
            final boolean isCollapsed = !expanded && accounts.size() > 4;

            FrameLayout cardFrame = new FrameLayout(act);
            LinearLayout cardInner = new LinearLayout(act);
            cardInner.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < accounts.size(); i++) {
                if (i > 0) cardInner.addView(makeDivider(act, div));
                cardInner.addView(accountRow(act, dlg, accounts.get(i), text, sub));
            }
            cardFrame.addView(cardInner, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final View fade;
            if (accounts.size() > 4) {
                fade = new View(act);
                GradientDrawable fadeBg = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0x00000000, card});
                fade.setBackground(fadeBg);
                fade.setAlpha(isCollapsed ? 1f : 0f);
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48));
                flp.gravity = Gravity.BOTTOM;
                cardFrame.addView(fade, flp);
            } else {
                fade = null;
            }
            listCard.addView(cardFrame);

            accountListViewport = new WeakReference<>(cardFrame);
            accountListContent = new WeakReference<>(cardInner);
            collapsedFade = new WeakReference<>(fade);
            // Wait for the native rows to measure, then clamp only the collapsed viewport.
            cardFrame.post(new Runnable() {
                public void run() {
                    int rowHeight = cardInner.getChildCount() == 0 ? 0
                            : cardInner.getChildAt(0).getHeight();
                    if (rowHeight <= 0) rowHeight = dp(act, 68);
                    collapsedListHeight = rowHeight * Math.min(4, accounts.size())
                            + dp(act, 1) * Math.max(0, Math.min(4, accounts.size()) - 1);
                    ViewGroup.LayoutParams lp = cardFrame.getLayoutParams();
                    if (lp != null && isCollapsed) {
                        lp.height = collapsedListHeight;
                        cardFrame.setLayoutParams(lp);
                    }
                }
            });

            if (accounts.size() > 4) {
                listCard.addView(makeDivider(act, div));
                TextView expandBtn = new TextView(act);
                expandBtn.setText(expanded
                        ? "收起  " + (accounts.size() - 4) + " 个账号"
                        : "展开全部 " + accounts.size() + " 个账号");
                expandBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                expandBtn.setTextColor(DeekseepUi.BRAND);
                expandBtn.setTypeface(Typeface.DEFAULT_BOLD);
                expandBtn.setGravity(Gravity.CENTER);
                expandBtn.setPadding(dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 14));
                expandBtn.setClickable(true);
                expandBtn.setFocusable(true);
                expandBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        animateAccountListExpansion(act, accounts.size());
                    }
                });
                listCard.addView(expandBtn);
                expandControl = new WeakReference<>(expandBtn);
            }
        }
        content.addView(listCard, cardLp(act, accounts.isEmpty() ? 0 : dp(act, 12)));

        TextView hint = new TextView(act);
        hint.setText("长按账号进入批量操作。校验会读取当前用户接口确认账号状态；导入支持一个文件中的多个账号，"
                + "导出文件含明文登录凭证，请妥善保管。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(sub);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(act, 12);
        content.addView(hint, hlp);
        DeekseepUi.addBuildFooter(act, content, sub);
        UiLanguage.localizeTree(act, content);
    }

    private static void animateAccountListExpansion(final Activity act, final int accountCount) {
        final FrameLayout viewport = accountListViewport.get();
        final LinearLayout rows = accountListContent.get();
        final TextView control = expandControl.get();
        if (viewport == null || rows == null || control == null) return;
        final int from = Math.max(0, viewport.getHeight());
        final int collapsed = collapsedListHeight > 0 ? collapsedListHeight : from;
        // The viewport is intentionally clipped while collapsed.  Measure the rows independently
        // here, otherwise Android may report the clipped height and make the first expansion hitch.
        final int width = viewport.getWidth();
        if (width > 0) {
            rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }
        final int full = Math.max(rows.getHeight(), rows.getMeasuredHeight());
        if (full <= 0) {
            viewport.post(new Runnable() { public void run() {
                animateAccountListExpansion(act, accountCount);
            }});
            return;
        }
        final boolean opening = !expanded;
        final int to = opening ? full : collapsed;
        final View fade = collapsedFade.get();
        expanded = opening;
        control.setEnabled(false);
        control.setText(opening ? "收起  " + (accountCount - 4) + " 个账号"
                : "展开全部 " + accountCount + " 个账号");
        control.setContentDescription(opening ? "收起账号列表" : "展开账号列表");
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(opening ? 220 : 180);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator value) {
                ViewGroup.LayoutParams lp = viewport.getLayoutParams();
                if (lp == null) return;
                lp.height = ((Integer) value.getAnimatedValue()).intValue();
                viewport.setLayoutParams(lp);
                if (fade != null) {
                    float p = value.getAnimatedFraction();
                    fade.setAlpha(opening ? 1f - p : p);
                }
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) {
                ViewGroup.LayoutParams lp = viewport.getLayoutParams();
                if (lp == null) return;
                lp.height = opening ? ViewGroup.LayoutParams.WRAP_CONTENT : collapsed;
                viewport.setLayoutParams(lp);
                if (fade != null) fade.setAlpha(opening ? 0f : 1f);
                control.setEnabled(true);
            }
        });
        animator.start();
    }

    /** Dense identity summary: the page is an account workspace, not a stack of feature cards. */
    private static View accountOverview(Activity act, List<AccountManager.Account> accounts,
                                        int text, int sub) {
        LinearLayout overview = new LinearLayout(act);
        overview.setOrientation(LinearLayout.VERTICAL);
        overview.setPadding(dp(act, 2), dp(act, 10), dp(act, 2), dp(act, 4));
        TextView count = new TextView(act);
        count.setText("已保存 " + accounts.size() + " 个账号");
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setTextColor(text);
        overview.addView(count);
        TextView detail = new TextView(act);
        detail.setText(providerSummary(accounts));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detail.setTextColor(sub);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(act, 4);
        overview.addView(detail, detailLp);
        return overview;
    }

    private static String providerSummary(List<AccountManager.Account> accounts) {
        int google = 0, wechat = 0, email = 0, phone = 0;
        for (AccountManager.Account account : accounts) {
            String provider = normalizedProvider(account.provider);
            if ("GOOGLE".equals(provider)) google++;
            else if ("WECHAT".equals(provider)) wechat++;
            else if ("PHONE".equals(provider)) phone++;
            else email++;
        }
        ArrayList<String> parts = new ArrayList<>();
        if (google > 0) parts.add("Google " + google);
        if (wechat > 0) parts.add("微信 " + wechat);
        if (email > 0) parts.add("邮箱 " + email);
        if (phone > 0) parts.add("手机号 " + phone);
        return parts.isEmpty() ? "登录方式待识别" : join(parts, "  ·  ");
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    private static View accountRow(final Activity act, final Dialog dlg,
                                   final AccountManager.Account a, int text, int sub) {
        FrameLayout row = new FrameLayout(act);
        LinearLayout inner = new LinearLayout(act);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 14));
        inner.setClickable(true);
        inner.setFocusable(true);

        // 账号来源图标来自本地凭证的 provider，不展示或下载个人头像。
        View avatar = providerAvatar(act, a.provider);
        FrameLayout.LayoutParams avlp = new FrameLayout.LayoutParams(dp(act, 40), dp(act, 40));
        avlp.leftMargin = dp(act, 0);
        avlp.topMargin = dp(act, 0);

        // 头像容器
        FrameLayout avatarFrame = new FrameLayout(act);
        avatarFrame.addView(avatar, avlp);

        // 选中才覆盖黑色小勾；未选账号的头像保持完全原样。
        if (multiSelectMode && multiSelect.contains(a.id)) {
            // 用 Canvas 画完整圆和勾，不依赖字体字形，避免部分系统把勾绘成多边形。
            final View checkOverlay = new SelectionMarkView(act);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    dp(act, 20), dp(act, 20));
            clp.gravity = Gravity.TOP | Gravity.END;
            clp.topMargin = dp(act, -2);
            clp.rightMargin = dp(act, -2);
            avatarFrame.addView(checkOverlay, clp);
        }

        LinearLayout.LayoutParams afp = new LinearLayout.LayoutParams(dp(act, 40), dp(act, 40));
        afp.rightMargin = dp(act, 12);
        inner.addView(avatarFrame, afp);

        // 名字 + 副标题
        LinearLayout labels = new LinearLayout(act);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(act);
        name.setText(a.label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(text);
        labels.addView(name);
        TextView subtv = new TextView(act);
        subtv.setText(subtitleOf(a));
        subtv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtv.setTextColor(sub);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = dp(act, 2);
        labels.addView(subtv, stlp);
        TextView provider = providerTag(act, a.provider);
        LinearLayout.LayoutParams providerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        providerLp.topMargin = dp(act, 6);
        labels.addView(provider, providerLp);
        inner.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧
        if (!multiSelectMode) {
            TextView tail = new TextView(act);
            if (a.current) {
                tail.setText("当前");
                tail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tail.setTextColor(0xFFFFFFFF);
                tail.setPadding(dp(act, 10), dp(act, 4), dp(act, 10), dp(act, 4));
                GradientDrawable bb = new GradientDrawable();
                bb.setColor(0xFF2ECC71);
                bb.setCornerRadius(dp(act, 10));
                tail.setBackground(bb);
            } else {
                tail.setText("\u203A");
                tail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                tail.setTextColor(sub);
            }
            inner.addView(tail, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (multiSelectMode) {
            inner.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (multiSelect.contains(a.id)) {
                        multiSelect.remove(a.id);
                    } else {
                        multiSelect.add(a.id);
                    }
                    updateMultiSelectToolbar(act);
                    refreshOpen(act);
                }
            });
            inner.setOnLongClickListener(null);
        } else {
            if (!a.current) {
                inner.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { confirmSwitch(act, a); }
                });
            }
            inner.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    enterMultiSelect(act, a.id);
                    return true;
                }
            });
        }
        row.addView(inner);
        return row;
    }

    private static void enterMultiSelect(final Activity act, String startId) {
        multiSelectMode = true;
        multiSelect.clear();
        if (startId != null) multiSelect.add(startId);
        expanded = true;
        showMultiSelectToolbar(act);
        refreshOpen(act);
    }

    private static void exitMultiSelect(final Activity act) {
        multiSelectMode = false;
        multiSelect.clear();
        removeMultiSelectToolbar();
        refreshOpen(act);
    }

    private static void showMultiSelectToolbar(final Activity act) {
        removeMultiSelectToolbar();
        if (toolbarParent == null) return;

        final boolean dark = DeekseepUi.isDark(act);
        final int card = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int div = dark ? 0xFF414145 : 0xFFE8E9EC;

        LinearLayout toolbar = new LinearLayout(act);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(act, 10), dp(act, 8), dp(act, 10), dp(act, 8));
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(card);
        tb.setCornerRadius(dp(act, 16));
        tb.setStroke(1, div);
        toolbar.setBackground(tb);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            toolbar.setElevation(dp(act, 8));
        }

        TextView countLabel = new TextView(act);
        countLabel.setText("已选 " + multiSelect.size());
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        countLabel.setTypeface(Typeface.DEFAULT_BOLD);
        countLabel.setTextColor(text);
        countLabel.setGravity(Gravity.CENTER);
        toolbar.addView(countLabel, new LinearLayout.LayoutParams(
                0, dp(act, 44), 1f));

        TextView selectAllBtn = toolbarAction(act, "全选", 0xFF3977F6);
        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                List<AccountManager.Account> accounts =
                        AccountManager.accountsForUi(act.getClassLoader());
                if (multiSelect.size() == accounts.size()) {
                    multiSelect.clear();
                } else {
                    multiSelect.clear();
                    for (AccountManager.Account account : accounts) multiSelect.add(account.id);
                }
                updateMultiSelectToolbar(act);
                refreshOpen(act);
            }
        });
        LinearLayout.LayoutParams salp = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        salp.leftMargin = dp(act, 6);
        toolbar.addView(selectAllBtn, salp);

        TextView validateBtn = toolbarAction(act, "校验", 0xFF3977F6);
        validateBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmBatchValidate(act,
                        AccountManager.accountsForUi(act.getClassLoader()));
            }
        });
        LinearLayout.LayoutParams vblp = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        vblp.leftMargin = dp(act, 6);
        toolbar.addView(validateBtn, vblp);

        TextView exportBtn = toolbarAction(act, "导出", 0xFF3977F6);
        exportBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                exportSelected(act,
                        AccountManager.accountsForUi(act.getClassLoader()));
            }
        });
        LinearLayout.LayoutParams eblp = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        eblp.leftMargin = dp(act, 6);
        toolbar.addView(exportBtn, eblp);

        TextView deleteBtn = toolbarAction(act, "删除", 0xFFE5484D);
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmDeleteSelected(act,
                        AccountManager.accountsForUi(act.getClassLoader()));
            }
        });
        LinearLayout.LayoutParams dblp = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        dblp.leftMargin = dp(act, 6);
        toolbar.addView(deleteBtn, dblp);

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        tlp.topMargin = dp(act, 56);
        tlp.leftMargin = dp(act, 42);
        tlp.rightMargin = dp(act, 42);

        toolbarParent.addView(toolbar, tlp);
        multiSelectToolbar = toolbar;

        // 从顶部滑入动画
        toolbar.setTranslationY(-dp(act, 64));
        toolbar.setAlpha(.92f);
        toolbar.animate().translationY(0).alpha(1f).setDuration(120)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    private static void updateMultiSelectToolbar(final Activity act) {
        if (multiSelectToolbar instanceof LinearLayout) {
            LinearLayout tb = (LinearLayout) multiSelectToolbar;
            if (tb.getChildCount() > 0 && tb.getChildAt(0) instanceof TextView) {
                TextView label = (TextView) tb.getChildAt(0);
                label.setText("已选 " + multiSelect.size());
            }
            if (tb.getChildCount() > 1 && tb.getChildAt(1) instanceof TextView) {
                TextView selectAll = (TextView) tb.getChildAt(1);
                int total = AccountManager.accountsForUi(act.getClassLoader()).size();
                selectAll.setText(total > 0 && multiSelect.size() == total ? "取消全选" : "全选");
            }
        }
    }

    private static void removeMultiSelectToolbar() {
        if (multiSelectToolbar != null) {
            final View v = multiSelectToolbar;
            v.animate().translationY(-dp(v.getContext(), 64)).alpha(.92f).setDuration(100)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .withEndAction(new Runnable() {
                        public void run() {
                            if (v.getParent() instanceof ViewGroup) {
                                ((ViewGroup) v.getParent()).removeView(v);
                            }
                        }
                    }).start();
            multiSelectToolbar = null;
        }
    }

    private static void exportSelected(final Activity act,
                                       final List<AccountManager.Account> accounts) {
        List<AccountCredentialCodec.Entry> entries = new ArrayList<>();
        for (AccountManager.Account a : accounts) {
            if (multiSelect.contains(a.id)) {
                entries.add(new AccountCredentialCodec.Entry(
                        a.label, a.id, a.credJson));
            }
        }
        if (entries.isEmpty()) {
            UiLanguage.toast(act, "请至少勾选一个账号", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            pendingExportJson = AccountCredentialCodec.buildExport(entries);
            pendingExportFileName = AccountCredentialCodec.suggestFileName(entries);
            openExportDestination(act, pendingExportFileName);
        } catch (AccountCredentialCodec.FormatException e) {
            pendingExportJson = null;
            pendingExportFileName = null;
            showResult(act, "无法导出", e.getMessage());
        }
    }

    private static void confirmDeleteSelected(final Activity act,
                                              final List<AccountManager.Account> accounts) {
        final List<AccountManager.Account> removable = new ArrayList<>();
        boolean selectedCurrent = false;
        for (AccountManager.Account account : accounts) {
            if (!multiSelect.contains(account.id)) continue;
            if (account.current) selectedCurrent = true;
            else removable.add(account);
        }
        if (removable.isEmpty()) {
            showResult(act, "无法删除", selectedCurrent
                    ? "当前正在登录的账号不能从账号管理中删除，请先切换到其他账号。"
                    : "请至少勾选一个账号。");
            return;
        }
        String message = "确定删除选中的 " + removable.size() + " 个账号？\n"
                + "这会移除模块保存的登录凭证，无法撤销。";
        if (selectedCurrent) {
            message += "\n\n当前正在登录的账号会被保留。";
        }
        DeekseepUi.showCustomConfirm(act, "删除账号", message,
                "取消", "删除", true, null, new Runnable() {
                    public void run() {
                        List<String> ids = new ArrayList<>();
                        for (AccountManager.Account account : removable) {
                            ids.add(account.id);
                        }
                        int removed = AccountManager.removeSlots(ids);
                        exitMultiSelect(act);
                        UiLanguage.toast(act, "已删除 " + removed + " 个账号",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void confirmSwitch(final Activity act, final AccountManager.Account a) {
        DeekseepUi.showCustomConfirm(act, "切换账号",
                "切换到「" + a.label + "」？\n将重启 DeepSeek 以新账号启动，当前账号已自动备份。",
                "取消", "切换并重启", true, null, new Runnable() {
                    public void run() {
                    boolean ok = AccountManager.switchTo(act.getClassLoader(), a.id);
                    if (!ok) {
                        showResult(act, "切换失败", "无法写入 DeepSeek 登录态，未执行重启。");
                        return;
                    }
                    UiLanguage.toast(act, "正在切换…", Toast.LENGTH_SHORT).show();
                    AccountManager.restartApp(act);
                }
        });
    }

    private static void confirmAdd(final Activity act) {
        final boolean dark = DeekseepUi.isDark(act);
        final Dialog dialog = new Dialog(act);
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 22), dp(act, 20), dp(act, 22), dp(act, 18));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(dark ? 0xFF29292D : 0xFFFFFFFF);
        panelBg.setCornerRadius(dp(act, 18));
        panel.setBackground(panelBg);

        TextView title = popupText(act, "添加账号", 20, dark ? 0xFFF2F2F2 : 0xFF171719, true);
        panel.addView(title);
        TextView detail = popupText(act,
                "当前账号不会被切换。可直接验证邮箱/手机号密码并加入候选列表，或使用原生登录页添加其他方式。",
                13, dark ? 0xFFA8A8AD : 0xFF6F7178, false);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(act, 8);
        detailLp.bottomMargin = dp(act, 16);
        panel.addView(detail, detailLp);

        TextView passwordLogin = popupAction(act, "密码登录", DeekseepUi.BRAND, dark);
        panel.addView(passwordLogin);
        TextView nativeLogin = popupAction(act, "登出并使用其他方式登录",
                dark ? 0xFFD1D1D6 : 0xFF4F5158, dark);
        LinearLayout.LayoutParams nativeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48));
        nativeLp.topMargin = dp(act, 8);
        panel.addView(nativeLogin, nativeLp);

        passwordLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                showPasswordCandidateDialog(act);
            }
        });
        nativeLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                confirmNativeAdd(act);
            }
        });
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(Math.min(dp(act, 360),
                    act.getResources().getDisplayMetrics().widthPixels - dp(act, 40)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.38f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private static void confirmNativeAdd(final Activity act) {
        DeekseepUi.showCustomConfirm(act, "添加账号",
                "将登出当前账号并进入原生登录页以登录新账号（支持微信、手机号等；若已开启“解锁 Google 登录”，"
                        + "登录页也会显示宿主原生 Google 入口）。\n"
                        + "当前账号已自动备份，登录新号后可在多账号里切回。是否继续？",
                "取消", "登出并登录新号", true, null, new Runnable() {
                    public void run() {
                    boolean ok = AccountManager.prepareAddAccount(act.getClassLoader());
                    if (!ok) {
                        showResult(act, "操作失败", "无法清除当前登录态，未执行重启。");
                        return;
                    }
                    UiLanguage.toast(act, "正在进入登录页…", Toast.LENGTH_SHORT).show();
                    AccountManager.restartApp(act);
                }
        });
    }

    private static void showPasswordCandidateDialog(final Activity act) {
        final boolean dark = DeekseepUi.isDark(act);
        final Dialog dialog = new Dialog(act);
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 22), dp(act, 20), dp(act, 22), dp(act, 18));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(dark ? 0xFF29292D : 0xFFFFFFFF);
        panelBg.setCornerRadius(dp(act, 18));
        panel.setBackground(panelBg);
        panel.addView(popupText(act, "密码登录", 20,
                dark ? 0xFFF2F2F2 : 0xFF171719, true));
        TextView note = popupText(act, "认证成功后只加入账号列表，不切换当前账号。密码不会保存。",
                13, dark ? 0xFFA8A8AD : 0xFF6F7178, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(act, 7);
        noteLp.bottomMargin = dp(act, 14);
        panel.addView(note, noteLp);

        final android.widget.EditText identity = popupInput(act, "邮箱或手机号", dark, false);
        panel.addView(identity, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 50)));
        final android.widget.EditText password = popupInput(act, "密码", dark, true);
        final FrameLayout passwordFrame = new FrameLayout(act);
        LinearLayout.LayoutParams passwordLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 50));
        passwordLp.topMargin = dp(act, 10);
        passwordFrame.addView(password, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final ImageView passwordEye = new ImageView(act);
        passwordEye.setImageResource(android.R.drawable.ic_menu_view);
        passwordEye.setColorFilter(dark ? 0xFFB7B7BC : 0xFF666971);
        passwordEye.setPadding(dp(act, 11), dp(act, 11), dp(act, 11), dp(act, 11));
        passwordEye.setContentDescription("显示密码");
        passwordEye.setClickable(true);
        passwordEye.setFocusable(true);
        FrameLayout.LayoutParams eyeLp = new FrameLayout.LayoutParams(
                dp(act, 46), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        passwordFrame.addView(passwordEye, eyeLp);
        passwordEye.setOnClickListener(new View.OnClickListener() {
            private boolean visible;
            @Override public void onClick(View v) {
                int cursor = password.getSelectionStart();
                visible = !visible;
                password.setTransformationMethod(visible ? null
                        : android.text.method.PasswordTransformationMethod.getInstance());
                password.setSelection(Math.max(0, Math.min(cursor, password.length())));
                passwordEye.setAlpha(visible ? 1f : 0.68f);
                passwordEye.setContentDescription(visible ? "隐藏密码" : "显示密码");
            }
        });
        panel.addView(passwordFrame, passwordLp);
        final TextView status = popupText(act, "", 12, DeekseepUi.BRAND, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 34));
        panel.addView(status, statusLp);
        final TextView submit = popupAction(act, "验证并添加", DeekseepUi.BRAND, dark);
        panel.addView(submit);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                submit.setEnabled(false);
                submit.setAlpha(0.62f);
                status.setText("正在通过 DeepSeek 原生链路验证…");
                String error = Main.addCandidateAccountWithPassword(act,
                        identity.getText().toString(), password.getText().toString(),
                        new Main.CandidateLoginCallback() {
                            @Override public void onResult(final boolean success,
                                    final String message) {
                                act.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (success) {
                                            dialog.dismiss();
                                            refreshOpen(act);
                                            Toast.makeText(act, message, Toast.LENGTH_SHORT).show();
                                        } else {
                                            submit.setEnabled(true);
                                            submit.setAlpha(1f);
                                            status.setText(message);
                                            Toast.makeText(act, message, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            }
                        });
                if (error != null) {
                    submit.setEnabled(true);
                    submit.setAlpha(1f);
                    status.setText(error);
                    Toast.makeText(act, error, Toast.LENGTH_SHORT).show();
                }
            }
        });
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(Math.min(dp(act, 360),
                    act.getResources().getDisplayMetrics().widthPixels - dp(act, 40)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setDimAmount(0.38f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private static TextView popupText(Activity act, String value, int sp, int color, boolean bold) {
        TextView text = new TextView(act);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private static TextView popupAction(Activity act, String value, int color, boolean dark) {
        TextView action = popupText(act, value, 14, color, true);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF37373C : 0xFFF1F3F7);
        bg.setCornerRadius(dp(act, 10));
        action.setBackground(bg);
        action.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48)));
        return action;
    }

    private static android.widget.EditText popupInput(Activity act, String hint,
            boolean dark, boolean secret) {
        android.widget.EditText input = new android.widget.EditText(act);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(dark ? 0xFFF2F2F2 : 0xFF171719);
        input.setHintTextColor(dark ? 0xFF8D8D92 : 0xFF92949A);
        input.setPadding(dp(act, 14), 0, dp(act, secret ? 50 : 14), 0);
        if (secret) input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF202024 : 0xFFF4F5F8);
        bg.setCornerRadius(dp(act, 10));
        bg.setStroke(dp(act, 1), dark ? 0xFF46464C : 0xFFD9DCE3);
        input.setBackground(bg);
        return input;
    }

    private static void confirmImport(final Activity act) {
        final boolean batchV241 = usesClosedAccountV2();
        DeekseepUi.showCustomConfirm(act, "导入账号凭证",
                (batchV241
                        ? "可同时选择多个 JSON/TXT/ZIP；ZIP 内的账号文件会安全读取并合并。"
                        : "只接受完整 JSON。")
                        + "模块会先检查全部账号的字段和类型，再使用每个候选 token 请求 "
                        + "DeepSeek 当前用户接口；请求会使用当前安装版本的宿主身份并自动限速，且必须同时通过外层和业务层校验。"
                        + "服务器若返回账号 ID，还必须与文件一致，"
                        + "之后才会一次性加入多账号列表。\n\n"
                        + "校验前不会写入 MMKV、数据库或账号槽。请只导入你本人合法持有的凭证。",
                "取消", batchV241 ? "选择文件或 ZIP" : "选择 JSON/TXT", true, null, new Runnable() {
                    public void run() { openImportPicker(act); }
                });
    }

    private static void confirmBatchValidate(final Activity act,
                                           final List<AccountManager.Account> accounts) {
        final List<AccountManager.Account> targets = new ArrayList<>();
        for (AccountManager.Account a : accounts) {
            if (multiSelect.contains(a.id)) targets.add(a);
        }
        if (targets.isEmpty()) return;
        DeekseepUi.showCustomConfirm(act, "批量验证账号",
                "将向 DeepSeek 服务器逐个验证 " + targets.size() + " 个账号是否仍可用。"
                        + "请求会自动限速，每个账号最多重试 2 次。",
                "取消", "开始验证", true, null, new Runnable() {
                    public void run() { runBatchValidation(act, targets); }
                });
    }

    private static void runBatchValidation(final Activity act,
                                           final List<AccountManager.Account> targets) {
        final ProgressPopup progress = showProgress(act, "正在批量验证账号…");
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder ok = new StringBuilder();
                final StringBuilder bad = new StringBuilder();
                int valid = 0;
                int invalid = 0;
                for (int i = 0; i < targets.size(); i++) {
                    final AccountManager.Account a = targets.get(i);
                    progress.update(act, "正在验证 " + (i + 1) + "/" + targets.size()
                            + "：" + a.label);
                    try {
                        AccountManager.ServerValidation result =
                                AccountManager.validateWithServer(
                                        act.getApplicationContext(), a.credJson);
                        if (result.valid) {
                            ok.append("● ").append(a.label).append("\n")
                                    .append("  ").append(result.detail).append("\n");
                            valid++;
                        } else {
                            bad.append("● ").append(a.label).append(" — ")
                                    .append(result.error).append("\n")
                                    .append("  ").append(result.detail).append("\n");
                            invalid++;
                        }
                    } catch (Throwable t) {
                        bad.append("● ").append(a.label).append(" — 请求异常\n")
                                .append("  校验日志：阶段=调用保护；异常=")
                                .append(t.getClass().getSimpleName())
                                .append("；本次校验未写入凭证。\n");
                        invalid++;
                    }
                }
                final String okMsg = ok.toString();
                final String badMsg = bad.toString();
                final int v = valid;
                final int iv = invalid;
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        progress.dismiss();
                        StringBuilder msg = new StringBuilder();
                        msg.append("验证完成：").append(v).append(" 个正常");
                        if (iv > 0) msg.append("，").append(iv).append(" 个异常");
                        msg.append("\n\n");
                        if (iv > 0) {
                            msg.append("以下账号异常，建议检查：\n");
                            msg.append(badMsg);
                            msg.append("\n");
                        }
                        if (v > 0) {
                            msg.append("正常账号：\n");
                            msg.append(okMsg);
                        }
                        showResult(act, iv > 0 ? "发现异常账号" : "全部正常", msg.toString());
                        exitMultiSelect(act);
                    }
                });
            }
        }, "Deekseep-account-batch-validate").start();
    }

    private static void openImportPicker(Activity act) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if (usesClosedAccountV2()) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "text/json", "text/plain", "application/zip",
                        "application/x-zip-compressed", "application/octet-stream"});
            } else {
                // Host-version isolation: every legacy branch keeps its original single-file
                // picker contract and accepted MIME list.
                intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"application/json", "text/json", "text/plain"});
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            act.startActivityForResult(intent, Main.ACCOUNT_IMPORT_REQUEST);
        } catch (Throwable t) {
            showResult(act, "无法打开文件选择器", "请确认系统文件选择器可用后重试。");
        }
    }

    private static void showExportPicker(final Activity act) {
        final List<AccountManager.Account> accounts =
                AccountManager.accountsForUi(act.getClassLoader());
        if (accounts.isEmpty()) {
            showResult(act, "没有可导出的账号", "请先登录或添加至少一个账号。");
            return;
        }

        final boolean dark = DeekseepUi.isDark(act);
        final int card = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub = dark ? 0xFFAAAAAE : 0xFF707070;
        final int div = dark ? 0xFF414145 : 0xFFE8E9EC;
        final Set<String> selected = new HashSet<>();
        for (AccountManager.Account account : accounts) {
            if (account.current) { selected.add(account.id); break; }
        }
        if (selected.isEmpty()) selected.add(accounts.get(0).id);

        final Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = makeCard(act, card);
        root.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 16));

        TextView title = new TextView(act);
        title.setText("选择要导出的账号");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        root.addView(title);

        TextView warning = new TextView(act);
        warning.setText("导出的是可登录账号的明文凭证。拿到文件的人可能直接使用你的账号，请勿分享，使用后及时删除。");
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        warning.setTextColor(0xFFE45555);
        warning.setPadding(0, dp(act, 8), 0, dp(act, 8));
        root.addView(warning);

        ScrollView scroll = new ScrollView(act);
        LinearLayout rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        final List<TextView> checks = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i++) {
            final AccountManager.Account account = accounts.get(i);
            if (i > 0) rows.addView(makeDivider(act, div));
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(act, 4), dp(act, 12), dp(act, 4), dp(act, 12));
            LinearLayout labels = new LinearLayout(act);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(act);
            name.setText(account.label + (account.current
                    ? UiLanguage.text(act, "  · 当前", "  · Current") : ""));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTextColor(text);
            labels.addView(name);
            TextView detail = new TextView(act);
            detail.setText(subtitleOf(account));
            detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            detail.setTextColor(sub);
            labels.addView(detail);
            row.addView(labels, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final TextView check = new TextView(act);
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            check.setGravity(Gravity.CENTER);
            check.setTextColor(DeekseepUi.BRAND);
            setCheckedGlyph(check, selected.contains(account.id));
            checks.add(check);
            row.addView(check, new LinearLayout.LayoutParams(dp(act, 42), dp(act, 42)));
            final int index = i;
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (selected.contains(account.id)) selected.remove(account.id);
                    else selected.add(account.id);
                    setCheckedGlyph(checks.get(index), selected.contains(account.id));
                }
            });
            rows.addView(row);
        }
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(act, 360), dp(act, 64) * accounts.size()));
        root.addView(scroll, slp);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = actionButton(act, "取消", text,
                dark ? 0xFF3A3A3E : 0xFFF0F1F4, div);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });
        TextView export = actionButton(act, "导出所选", 0xFFFFFFFF, DeekseepUi.BRAND,
                DeekseepUi.BRAND);
        export.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selected.isEmpty()) {
                    UiLanguage.toast(act, "请至少勾选一个账号", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<AccountCredentialCodec.Entry> entries = new ArrayList<>();
                for (AccountManager.Account account : accounts) {
                    if (selected.contains(account.id)) {
                        entries.add(new AccountCredentialCodec.Entry(
                                account.label, account.id, account.credJson));
                    }
                }
                try {
                    pendingExportJson = AccountCredentialCodec.buildExport(entries);
                    pendingExportFileName = AccountCredentialCodec.suggestFileName(entries);
                    dialog.dismiss();
                    openExportDestination(act, pendingExportFileName);
                } catch (AccountCredentialCodec.FormatException e) {
                    pendingExportJson = null;
                    pendingExportFileName = null;
                    showResult(act, "无法导出", e.getMessage());
                }
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        bl.rightMargin = dp(act, 8);
        buttons.addView(cancel, bl);
        LinearLayout.LayoutParams br = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        br.leftMargin = dp(act, 8);
        buttons.addView(export, br);
        LinearLayout.LayoutParams bRow = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bRow.topMargin = dp(act, 14);
        root.addView(buttons, bRow);

        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.48f;
            window.setAttributes(attrs);
            window.setLayout(act.getResources().getDisplayMetrics().widthPixels - dp(act, 32),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void setCheckedGlyph(TextView view, boolean checked) {
        view.setText(checked ? "●" : "○");
        view.setContentDescription(UiLanguage.text(view.getContext(),
                checked ? "已选择" : "未选择",
                checked ? "Selected" : "Not selected"));
    }

    private static void openExportDestination(Activity act, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            act.startActivityForResult(intent, Main.ACCOUNT_EXPORT_REQUEST);
        } catch (Throwable t) {
            pendingExportJson = null;
            pendingExportFileName = null;
            showResult(act, "无法打开保存位置", "请确认系统文件选择器可用后重试。");
        }
    }

    static void handleExportResult(final Activity act, int resultCode, Intent data) {
        if (!usesClosedAccountV2()) {
            AccountUiLegacy.handleExportResult(act, resultCode, data);
            return;
        }
        final String json = pendingExportJson;
        final String name = pendingExportFileName;
        pendingExportJson = null;
        pendingExportFileName = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            public void run() {
                OutputStream out = null;
                String error = null;
                try {
                    if (json == null) throw new Exception("导出内容已失效，请重新选择账号");
                    out = act.getContentResolver().openOutputStream(uri, "w");
                    if (out == null) throw new Exception("目标文件不可写");
                    out.write(json.getBytes("UTF-8"));
                    out.flush();
                } catch (Throwable t) {
                    error = t.getMessage() == null ? "目标文件写入失败" : t.getMessage();
                } finally {
                    if (out != null) try { out.close(); } catch (Throwable ignored) {}
                }
                final String finalError = error;
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        if (finalError == null) {
                            showResult(act, "导出完成", "已保存：" + name
                                    + "\n\n文件含明文登录凭证，请妥善保管且不要分享。");
                        } else {
                            showResult(act, "导出失败", finalError);
                        }
                    }
                });
            }
        }, "Deekseep-account-export").start();
    }

    static void handleImportResult(final Activity act, int resultCode, Intent data) {
        if (usesClosedAccountV2()) {
            handleImportResultV241(act, resultCode, data);
            return;
        }
        AccountUiLegacy.handleImportResult(act, resultCode, data);
    }

    private static void handleImportResultV241(final Activity act, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        final List<Uri> uris = selectedUris(data);
        if (uris.isEmpty()) return;
        final int permissionFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        for (Uri uri : uris) {
            try { act.getContentResolver().takePersistableUriPermission(uri, permissionFlags); }
            catch (Throwable ignored) {}
        }

        final ProgressPopup progress = showProgress(act,
                "正在读取 " + uris.size() + " 个账号文件…");
        new Thread(new Runnable() {
            @Override public void run() {
                String error = null;
                List<AccountCredentialCodec.Entry> entries = new ArrayList<>();
                final List<String> failures = new ArrayList<>();
                int importedCount = 0;
                try {
                    Set<String> ids = new LinkedHashSet<>();
                    for (int fileIndex = 0; fileIndex < uris.size(); fileIndex++) {
                        Uri uri = uris.get(fileIndex);
                        String name = displayName(act, uri);
                        progress.update(act, "正在解析 " + (fileIndex + 1) + "/"
                                + uris.size() + "：" + name);
                        List<AccountCredentialCodec.Entry> parsed = readImportSourceV241(
                                act, uri, name);
                        for (AccountCredentialCodec.Entry entry : parsed) {
                            if (!ids.add(entry.id)) {
                                throw new AccountCredentialCodec.FormatException(
                                        "多个文件中包含重复账号：" + shortAccountId(entry.id));
                            }
                            entries.add(entry);
                            if (entries.size() > AccountCredentialCodec.MAX_ACCOUNTS) {
                                throw new AccountCredentialCodec.FormatException(
                                        "单次最多导入 " + AccountCredentialCodec.MAX_ACCOUNTS
                                                + " 个账号");
                            }
                        }
                    }
                    if (entries.isEmpty()) {
                        throw new AccountCredentialCodec.FormatException(
                                "所选文件或压缩包中没有可导入的账号 JSON/TXT");
                    }
                    List<AccountCredentialCodec.Entry> validEntries = new ArrayList<>();
                    for (int i = 0; i < entries.size(); i++) {
                        AccountCredentialCodec.Entry entry = entries.get(i);
                        progress.update(act, "正在向 DeepSeek 验证 " + (i + 1) + "/"
                                + entries.size() + "：" + entry.name);
                        AccountManager.ServerValidation result = AccountManager.validateWithServer(
                                act.getApplicationContext(), entry.credentialJson);
                        if (!result.valid) {
                            failures.add("「" + entry.name + "」" + result.error);
                            continue;
                        }
                        validEntries.add(entry);
                    }
                    if (!validEntries.isEmpty()) {
                        progress.update(act, "已通过 " + validEntries.size()
                                + " 个账号，正在原子写入账号列表…");
                        if (!AccountManager.importValidated(validEntries)) {
                            throw new Exception("账号槽文件写入失败，未完成导入");
                        }
                        importedCount = validEntries.size();
                    }
                } catch (AccountCredentialCodec.FormatException format) {
                    error = "格式错误：" + format.getMessage();
                } catch (CharacterCodingException encoding) {
                    error = "账号文件不是完整有效的 UTF-8 文本";
                } catch (Throwable failure) {
                    error = failure.getMessage() == null ? "导入失败" : failure.getMessage();
                }
                final String finalError = error;
                final int imported = importedCount;
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        progress.dismiss();
                        if (finalError == null) {
                            StringBuilder message = new StringBuilder();
                            if (imported > 0) {
                                refreshOpen(act);
                                message.append("已从 ").append(uris.size())
                                        .append(" 个所选项目中验证并加入 ").append(imported)
                                        .append(" 个账号。当前登录账号未改变。");
                            } else {
                                message.append("所有候选账号均验证失败，未导入任何账号。原账号列表保持不变。");
                            }
                            if (!failures.isEmpty()) {
                                message.append("\n\n验证失败的账号：");
                                for (String failure : failures) message.append("\n• ").append(failure);
                            }
                            showResult(act, imported > 0 ? "批量导入完成" : "批量导入失败",
                                    message.toString());
                        } else {
                            showResult(act, "批量导入失败", finalError
                                    + "\n\n全部候选均未写入，原账号列表保持不变。");
                        }
                    }
                });
            }
        }, "Deekseep-account-import-v241").start();
    }

    private static List<Uri> selectedUris(Intent data) {
        LinkedHashSet<Uri> unique = new LinkedHashSet<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && unique.size() < 32; i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) unique.add(uri);
            }
        }
        if (data.getData() != null) unique.add(data.getData());
        return new ArrayList<>(unique);
    }

    private static List<AccountCredentialCodec.Entry> readImportSourceV241(
            Activity act, Uri uri, String name) throws Exception {
        InputStream raw = act.getContentResolver().openInputStream(uri);
        if (raw == null) throw new Exception("无法读取：" + name);
        BufferedInputStream input = new BufferedInputStream(raw);
        try {
            input.mark(8);
            int first = input.read();
            int second = input.read();
            input.reset();
            String lower = name == null ? "" : name.toLowerCase(java.util.Locale.US);
            String mime = act.getContentResolver().getType(uri);
            boolean zip = first == 'P' && second == 'K'
                    || lower.endsWith(".zip")
                    || "application/zip".equals(mime)
                    || "application/x-zip-compressed".equals(mime);
            if (zip) return AccountImportArchive.readZip(input, name);
            String text = readStrictUtf8(input, AccountCredentialCodec.MAX_IMPORT_BYTES,
                    "文件超过 1 MiB 上限");
            return AccountCredentialCodec.parseImport(text);
        } finally {
            try { input.close(); } catch (Throwable ignored) {}
        }
    }


    private static String readStrictUtf8(InputStream input, int limit, String tooLarge)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > limit) throw new Exception(tooLarge);
            output.write(buffer, 0, read);
        }
        return decodeStrictUtf8(output.toByteArray());
    }

    private static String decodeStrictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static String displayName(Activity act, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = act.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && value.trim().length() > 0) return value.trim();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
        }
        String last = uri == null ? null : uri.getLastPathSegment();
        return last == null || last.length() == 0 ? "账号文件" : last;
    }

    private static String shortAccountId(String id) {
        if (id == null) return "<unknown>";
        return id.length() <= 10 ? id : id.substring(0, 6) + "…" + id.substring(id.length() - 4);
    }

    private static String readUriStrictUtf8(Activity act, Uri uri) throws Exception {
        InputStream in = null;
        try {
            in = act.getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("无法读取所选文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > AccountCredentialCodec.MAX_IMPORT_BYTES) {
                    throw new Exception("文件超过 1 MiB 上限");
                }
                out.write(buffer, 0, read);
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString();
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static final class ProgressPopup {
        final Dialog dialog;
        final TextView message;

        ProgressPopup(Dialog dialog, TextView message) {
            this.dialog = dialog;
            this.message = message;
        }

        void update(Activity act, final String value) {
            act.runOnUiThread(new Runnable() {
                public void run() { message.setText(UiLanguage.dynamic(message.getContext(), value)); }
            });
        }

        void dismiss() {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
        }
    }

    private static ProgressPopup showProgress(Activity act, String value) {
        boolean dark = DeekseepUi.isDark(act);
        Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        LinearLayout root = makeCard(act, dark ? 0xFF2A2A2D : 0xFFFFFFFF);
        root.setPadding(dp(act, 22), dp(act, 22), dp(act, 22), dp(act, 22));
        TextView title = new TextView(act);
        title.setText("导入账号");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? 0xFFECECEC : 0xFF1A1A1A);
        root.addView(title);
        TextView message = new TextView(act);
        message.setText(UiLanguage.dynamic(act, value));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        message.setTextColor(dark ? 0xFFB0B0B4 : 0xFF666666);
        message.setPadding(0, dp(act, 12), 0, 0);
        root.addView(message);
        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.48f;
            window.setAttributes(attrs);
            window.setLayout(act.getResources().getDisplayMetrics().widthPixels - dp(act, 48),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return new ProgressPopup(dialog, message);
    }

    private static void refreshOpen(Activity act) {
        Dialog dialog = activeDialog.get();
        LinearLayout content = activeContent.get();
        if (dialog == null || content == null || !dialog.isShowing()) return;
        boolean dark = DeekseepUi.isDark(act);
        rebuild(act, dialog, content, dark,
                dark ? 0xFF2A2A2D : 0xFFFFFFFF,
                dark ? 0xFFECECEC : 0xFF1A1A1A,
                dark ? 0xFF9A9A9E : 0xFF888888,
                dark ? 0xFF3A3A3D : 0xFFEEEEEE);
    }

    private static void showResult(Activity act, String title, String message) {
        DeekseepUi.showCustomConfirm(act, title, message, null, "知道了", true, null, null);
    }

    private static String subtitleOf(AccountManager.Account a) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(a.credJson);
            String mobile = o.isNull("mobile_number") ? null : o.optString("mobile_number", null);
            String email = o.isNull("email") ? null : o.optString("email", null);
            if (mobile != null && mobile.length() > 0 && !mobile.equals(a.label)) return mobile;
            if (email != null && email.length() > 0 && !email.equals(a.label)) return email;
        } catch (Throwable ignored) {}
        return a.id != null && a.id.length() > 8 ? a.id.substring(0, 8) : a.id;
    }

    private static final int WECHAT_BG = 0xFF07C160;
    private static final int GOOGLE_BG = 0xFFEDEFF2;
    private static final int GOOGLE_FG = 0xFF5F6368;
    private static final int EMAIL_BG = 0xFFEEEAF7;
    private static final int EMAIL_FG = 0xFF665A86;
    private static final int PHONE_BG = 0xFFE5F0EE;
    private static final int PHONE_FG = 0xFF326B63;

    private static String normalizedProvider(String provider) {
        String value = provider == null ? "" : provider.trim().toUpperCase();
        if ("GOOGLE".equals(value) || "WECHAT".equals(value) || "PHONE".equals(value)) {
            return value;
        }
        return "EMAIL";
    }

    /** Uses the supplied brand assets where available; generic providers remain local vector marks. */
    private static View providerAvatar(Context context, String provider) {
        String kind = normalizedProvider(provider);
        String resource = "GOOGLE".equals(kind) ? "ic_login_google"
                : "WECHAT".equals(kind) ? "ic_login_wechat" : null;
        if (resource == null) return new ProviderGlyphView(context, kind);

        Context resourceContext = context;
        try {
            resourceContext = context.createPackageContext(
                    "com.dsmod.probe", Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable ignored) {}
        int resourceId = resourceContext.getResources().getIdentifier(resource, "drawable",
                "com.dsmod.probe");
        ImageView icon = new ImageView(context);
        if (resourceId != 0) try {
            icon.setImageDrawable(android.os.Build.VERSION.SDK_INT >= 21
                    ? resourceContext.getResources().getDrawable(resourceId,
                            resourceContext.getTheme())
                    : resourceContext.getResources().getDrawable(resourceId));
        } catch (Throwable ignored) {
            resourceId = 0;
        }
        if (resourceId == 0) {
            InputStream input = null;
            try {
                String entryName = "META-INF/com.dsmod.probe.icons/ic_category_login_"
                        + ("GOOGLE".equals(kind) ? "google" : "wechat") + ".png";
                android.graphics.Bitmap bitmap;
                if (HostCompat.isV236()) {
                    // Android 14/15 can fail ClassLoader#getResourceAsStream for an injected
                    // module. code249 therefore reads its independently enabled brand asset
                    // from the installed module APK. code257 keeps its established path.
                    bitmap = loadCode249BrandBitmap(context, entryName);
                } else {
                    ClassLoader loader = AccountUi.class.getClassLoader();
                    input = loader == null ? null : loader.getResourceAsStream(entryName);
                    bitmap = input == null ? null
                            : android.graphics.BitmapFactory.decodeStream(input);
                }
                if (bitmap == null) return new ProviderGlyphView(context, kind);
                icon.setImageDrawable(new android.graphics.drawable.BitmapDrawable(
                        context.getResources(), bitmap));
            } catch (Throwable ignored) {
                return new ProviderGlyphView(context, kind);
            } finally {
                if (input != null) try { input.close(); } catch (Throwable ignored) {}
            }
        }
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if ("WECHAT".equals(kind) && android.os.Build.VERSION.SDK_INT >= 21) {
            icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            icon.setClipToOutline(true);
        }
        icon.setContentDescription(providerLabel(kind));
        return icon;
    }

    private static android.graphics.Bitmap loadCode249BrandBitmap(
            Context context, String entryName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        try {
            Context module = context.createPackageContext(
                    "com.dsmod.probe", Context.CONTEXT_IGNORE_SECURITY);
            android.content.pm.ApplicationInfo info = module.getApplicationInfo();
            if (info.sourceDir != null) candidates.add(info.sourceDir);
            if (info.splitSourceDirs != null) {
                for (String path : info.splitSourceDirs) {
                    if (path != null) candidates.add(path);
                }
            }
        } catch (Throwable ignored) {}
        try {
            String loaderText = String.valueOf(AccountUi.class.getClassLoader());
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("/[^\\s\\\"\\],;]+\\.apk").matcher(loaderText);
            while (matcher.find()) candidates.add(matcher.group());
        } catch (Throwable ignored) {}
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/self/maps"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    int start = line.indexOf('/');
                    int end = line.indexOf(".apk", start);
                    if (start >= 0 && end > start) {
                        candidates.add(line.substring(start, end + 4));
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable ignored) {}
        for (String path : candidates) {
            if (path == null || !path.endsWith(".apk")) continue;
            ZipFile archive = null;
            InputStream input = null;
            try {
                File apk = new File(path);
                if (!apk.isFile() || !apk.canRead()) continue;
                archive = new ZipFile(apk);
                ZipEntry entry = archive.getEntry(entryName);
                if (entry == null || entry.isDirectory()
                        || entry.getSize() > 512L * 1024L) continue;
                input = archive.getInputStream(entry);
                android.graphics.Bitmap bitmap =
                        android.graphics.BitmapFactory.decodeStream(input);
                if (bitmap != null) {
                    Main.log("code249 account brand asset loaded from installed module APK");
                    return bitmap;
                }
            } catch (Throwable ignored) {
            } finally {
                if (input != null) try { input.close(); } catch (Throwable ignored) {}
                if (archive != null) try { archive.close(); } catch (Throwable ignored) {}
            }
        }
        Main.log("code249 account brand asset unavailable entry=" + entryName);
        return null;
    }

    /** A true circle + vector tick, so selection is identical on every Android font renderer. */
    private static final class SelectionMarkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tick = new Path();

        SelectionMarkView(Context context) {
            super(context);
            setContentDescription("已选择");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF252527);
            canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * .12f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFFFFFFFF);
            tick.reset();
            tick.moveTo(left + size * .27f, top + size * .52f);
            tick.lineTo(left + size * .44f, top + size * .68f);
            tick.lineTo(left + size * .74f, top + size * .34f);
            canvas.drawPath(tick, paint);
        }
    }

    /** Small source mark for generic email and phone providers. */
    private static final class ProviderGlyphView extends View {
        private final String provider;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Path materialPhone = PathParser.createPathFromPathData(
                "M6.62,10.79 c1.44,2.83 3.76,5.14 6.59,6.59 l2.2,-2.2 "
                        + "c0.27,-0.27 0.67,-0.36 1.02,-0.24 c1.12,0.37 2.33,0.57 "
                        + "3.57,0.57 c0.55,0 1,0.45 1,1 V20 c0,0.55 -0.45,1 -1,1 "
                        + "C10.61,21 3,13.39 3,4 c0,-0.55 0.45,-1 1,-1 h3.5 "
                        + "c0.55,0 1,0.45 1,1 c0,1.25 0.2,2.45 0.57,3.57 "
                        + "c0.11,0.35 0.03,0.74 -0.25,1.02 l-2.2,2.2 z");

        ProviderGlyphView(Context context, String provider) {
            super(context);
            this.provider = normalizedProvider(provider);
            setContentDescription(providerLabel(this.provider));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            bounds.set(left, top, left + size, top + size);
            if ("GOOGLE".equals(provider)) drawGoogle(canvas, size, left, top);
            else if ("WECHAT".equals(provider)) drawWechat(canvas, size, left, top);
            else if ("PHONE".equals(provider)) drawPhone(canvas, size, left, top);
            else drawMail(canvas, size, left, top);
        }

        private void circle(Canvas canvas, int color, float size, float left, float top) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint);
        }

        private void drawGoogle(Canvas canvas, float s, float l, float t) {
            circle(canvas, 0xFFFFFFFF, s, l, t);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * .115f);
            paint.setStrokeCap(Paint.Cap.BUTT);
            RectF oval = new RectF(l + s * .17f, t + s * .17f, l + s * .83f, t + s * .83f);
            paint.setColor(0xFF4285F4); canvas.drawArc(oval, -42, 95, false, paint);
            paint.setColor(0xFFEA4335); canvas.drawArc(oval, 53, 78, false, paint);
            paint.setColor(0xFFFBBC05); canvas.drawArc(oval, 131, 76, false, paint);
            paint.setColor(0xFF34A853); canvas.drawArc(oval, 207, 111, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF4285F4);
            canvas.drawRect(l + s * .50f, t + s * .45f, l + s * .86f, t + s * .56f, paint);
        }

        private void drawWechat(Canvas canvas, float s, float l, float t) {
            circle(canvas, WECHAT_BG, s, l, t);
            paint.setColor(0xFFFFFFFF); paint.setStyle(Paint.Style.FILL);
            canvas.drawOval(new RectF(l + s * .16f, t + s * .22f, l + s * .68f, t + s * .63f), paint);
            canvas.drawOval(new RectF(l + s * .40f, t + s * .43f, l + s * .83f, t + s * .76f), paint);
            paint.setColor(WECHAT_BG);
            float dot = s * .045f;
            canvas.drawCircle(l + s * .34f, t + s * .42f, dot, paint);
            canvas.drawCircle(l + s * .51f, t + s * .42f, dot, paint);
            canvas.drawCircle(l + s * .54f, t + s * .59f, dot, paint);
            canvas.drawCircle(l + s * .68f, t + s * .59f, dot, paint);
        }

        private void drawMail(Canvas canvas, float s, float l, float t) {
            circle(canvas, EMAIL_BG, s, l, t);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(s * .065f); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(EMAIL_FG);
            RectF mail = new RectF(l + s * .20f, t + s * .28f, l + s * .80f, t + s * .72f);
            canvas.drawRoundRect(mail, s * .06f, s * .06f, paint);
            Path flap = new Path();
            flap.moveTo(l + s * .22f, t + s * .31f);
            flap.lineTo(l + s * .50f, t + s * .53f);
            flap.lineTo(l + s * .78f, t + s * .31f);
            canvas.drawPath(flap, paint);
        }

        private void drawPhone(Canvas canvas, float s, float l, float t) {
            circle(canvas, PHONE_BG, s, l, t);
            if (materialPhone == null) return;
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            float glyphSize = s * .54f;
            float scale = glyphSize / 24f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(l + (s - glyphSize) * .5f, t + (s - glyphSize) * .5f);
            Path rendered = new Path();
            materialPhone.transform(matrix, rendered);
            paint.setColor(PHONE_FG);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(rendered, paint);
        }
    }

    private static String providerLabel(String provider) {
        String kind = normalizedProvider(provider);
        return "GOOGLE".equals(kind) ? "Google 登录"
                : "WECHAT".equals(kind) ? "微信登录"
                : "PHONE".equals(kind) ? "手机号登录" : "邮箱登录";
    }

    private static TextView providerTag(Activity act, String provider) {
        String kind = normalizedProvider(provider);
        String label = providerLabel(kind);
        int[] colors = avatarColors(kind);
        TextView tag = new TextView(act);
        tag.setText(label);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setTextColor(colors[1]);
        tag.setPadding(dp(act, 7), dp(act, 2), dp(act, 7), dp(act, 2));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colors[0]);
        shape.setCornerRadius(dp(act, 6));
        tag.setBackground(shape);
        tag.setContentDescription(label);
        return tag;
    }

    private static int[] avatarColors(String provider) {
        String p = normalizedProvider(provider);
        if (p.equals("WECHAT")) return new int[]{WECHAT_BG, 0xFFFFFFFF};
        if (p.equals("PHONE")) return new int[]{PHONE_BG, PHONE_FG};
        if (p.equals("EMAIL")) return new int[]{EMAIL_BG, EMAIL_FG};
        return new int[]{GOOGLE_BG, GOOGLE_FG};
    }

    private static TextView headerAction(Activity act, String label, int color) {
        TextView action = new TextView(act);
        action.setText(label);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setTextColor(color);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        action.setFocusable(true);
        return action;
    }

    private static TextView actionButton(Activity act, String label, int textColor,
                                         int backgroundColor, int strokeColor) {
        TextView button = new TextView(act);
        button.setText(UiLanguage.dynamic(act, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(act, 12));
        background.setStroke(dp(act, 1), strokeColor);
        button.setBackground(background);
        return button;
    }

    private static TextView toolbarAction(Activity act, String label, int color) {
        TextView action = new TextView(act);
        action.setText(UiLanguage.dynamic(act, label));
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setTextColor(color);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        action.setFocusable(true);
        action.setBackgroundColor(0x00000000);
        return action;
    }

    private static LinearLayout makeCard(Activity act, int card) {
        LinearLayout ll = new LinearLayout(act);
        ll.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card);
        bg.setCornerRadius(dp(act, 14));
        ll.setBackground(bg);
        return ll;
    }

    private static LinearLayout.LayoutParams cardLp(Activity act, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private static View makeDivider(Activity act, int color) {
        View v = new View(act);
        v.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(act, 14), 0, dp(act, 14), 0);
        v.setLayoutParams(lp);
        return v;
    }
}
