package com.ohli.floatingai;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.animation.DecelerateInterpolator;

public class OverlayService extends Service {
    public static final String ACTION_START = "com.ohli.floatingai.START";
    public static final String ACTION_SHOW_IMAGE = "com.ohli.floatingai.SHOW_IMAGE";
    public static final String ACTION_CAPTURE_MESSAGE = "com.ohli.floatingai.CAPTURE_MESSAGE";
    private static final String CHANNEL_ID = "floating_ai_assistant";
    private static final int NOTIFICATION_ID = 7301;

    private final int navy = Color.rgb(13, 34, 56);
    private final int teal = Color.rgb(32, 182, 140);
    private final int ink = Color.rgb(28, 41, 54);
    private final int muted = Color.rgb(112, 126, 138);
    private final int pale = Color.rgb(244, 247, 248);

    private WindowManager windowManager;
    private View bubble;
    private LinearLayout panel;
    private LinearLayout messagesBox;
    private ScrollView chatScroll;
    private EditText messageInput;
    private TextView sendButton;
    private TextView geminiChip;
    private TextView deepSeekChip;
    private TextView attachmentLabel;
    private WindowManager.LayoutParams bubbleParams;
    private String selectedProvider = "Gemini";
    private File pendingImage;
    private boolean waitingForReply;
    private final ArrayList<ChatMessage> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startAsForeground();
        String savedProvider = getPreferences().getString("preferred_provider", "Gemini");
        selectedProvider = "DeepSeek".equals(savedProvider) ? "DeepSeek" : "Gemini";
        if (Settings.canDrawOverlays(this)) showBubble();
        else stopSelf();
    }

    private void startAsForeground() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("AI Floating Assistant aktif")
                .setContentText("Ketuk tombol ✦ di layar untuk membuka AI.")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "AI Floating Assistant", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Menjaga tombol AI melayang tetap aktif.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_IMAGE.equals(intent.getAction())) {
            String path = intent.getStringExtra("image_path");
            if (path != null) {
                pendingImage = new File(path);
                showBubble();
                showPanel();
                addMessage("system", "Screenshot berhasil diambil. Tulis pertanyaan untuk menganalisis layar ini.");
                updateAttachmentLabel();
            }
        } else if (intent != null && ACTION_CAPTURE_MESSAGE.equals(intent.getAction())) {
            showBubble();
            showPanel();
            addMessage("system", intent.getStringExtra("capture_message"));
        } else {
            showBubble();
        }
        return START_STICKY;
    }

    /** iOS AssistiveTouch-inspired floating control: smoked translucent glass, white rim and centered AI mark. */
    private void showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return;

        final int bubbleSizeDp = getBubbleSizeDp();
        FrameLayout orb = new FrameLayout(this);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xC7AEB6BF, 0xB65B636D, 0xD02E343B});
        glass.setShape(GradientDrawable.OVAL);
        glass.setStroke(dp(2), 0xE6FFFFFF);
        orb.setBackground(glass);
        orb.setClipToOutline(true);
        orb.setElevation(dp(10));
        orb.setAlpha(0.94f);
        orb.setContentDescription("Assistive AI. Ketuk untuk membuka Gemini dan DeepSeek.");

        FrameLayout inner = new FrameLayout(this);
        GradientDrawable innerGlass = new GradientDrawable();
        innerGlass.setShape(GradientDrawable.OVAL);
        innerGlass.setColor(0x55FFFFFF);
        innerGlass.setStroke(dp(1), 0xDFFFFFFF);
        inner.setBackground(innerGlass);
        TextView mark = new TextView(this);
        mark.setText("✦");
        mark.setTextSize(17);
        mark.setTextColor(Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams markLp = new FrameLayout.LayoutParams(-1, -1);
        inner.addView(mark, markLp);
        FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(dp(Math.max(22, bubbleSizeDp * 54 / 100)), dp(Math.max(22, bubbleSizeDp * 54 / 100)), Gravity.CENTER);
        orb.addView(inner, innerLp);

        bubbleParams = new WindowManager.LayoutParams(dp(bubbleSizeDp), dp(bubbleSizeDp), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        bubbleParams.x = Math.max(0, Math.min(getPreferences().getInt("bubble_x", dp(6)), screenW - dp(bubbleSizeDp)));
        bubbleParams.y = Math.max(dp(24), Math.min(getPreferences().getInt("bubble_y", dp(220)), screenH - dp(bubbleSizeDp + 52)));

        final int[] down = new int[2];
        final float[] start = new float[2];
        final boolean[] moved = new boolean[1];
        orb.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = bubbleParams.x;
                    down[1] = bubbleParams.y;
                    start[0] = event.getRawX();
                    start[1] = event.getRawY();
                    moved[0] = false;
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - start[0]);
                    int dy = (int) (event.getRawY() - start[1]);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved[0] = true;
                    bubbleParams.x = Math.max(0, Math.min(screenW - dp(bubbleSizeDp), down[0] + dx));
                    bubbleParams.y = Math.max(dp(24), Math.min(screenH - dp(bubbleSizeDp + 52), down[1] + dy));
                    try { windowManager.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved[0]) {
                        snapBubbleToEdge(screenW);
                        getPreferences().edit().putInt("bubble_y", bubbleParams.y).apply();
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        togglePanel();
                    }
                    return true;
            }
            return false;
        });
        bubble = orb;
        try { windowManager.addView(bubble, bubbleParams); }
        catch (Exception e) { bubble = null; stopSelf(); }
    }

    /** Snaps the button to the nearest screen edge like iOS AssistiveTouch. */
    private void snapBubbleToEdge(int screenWidth) {
        if (bubbleParams == null || bubble == null) return;
        int left = dp(3);
        int right = Math.max(left, screenWidth - (bubbleParams == null ? dp(getBubbleSizeDp()) : bubbleParams.width) - dp(3));
        int target = bubbleParams.x + bubbleParams.width / 2 < screenWidth / 2 ? left : right;
        ValueAnimator animator = ValueAnimator.ofInt(bubbleParams.x, target);
        animator.setDuration(190);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            if (bubble == null || bubbleParams == null) return;
            bubbleParams.x = (Integer) value.getAnimatedValue();
            try { windowManager.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
        });
        animator.start();
        getPreferences().edit().putInt("bubble_x", target).apply();
    }

    private void togglePanel() {
        if (panel == null) showPanel(); else removePanel();
    }

    private void showPanel() {
        if (panel != null) return;
        if (!Settings.canDrawOverlays(this)) return;
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(13), dp(15), dp(12));
        panel.setBackground(round(Color.WHITE, 23));
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(3), dp(2), dp(2), dp(10));
        LinearLayout brandCol = new LinearLayout(this);
        brandCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("AI ASSISTANT", 10, teal, true);
        title.setLetterSpacing(0.12f);
        brandCol.addView(title);
        brandCol.addView(text("Ada yang bisa kubantu?", 17, navy, true));
        header.addView(brandCol, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = actionPill("×", Color.rgb(241, 245, 246), navy);
        close.setTextSize(22);
        close.setOnClickListener(v -> removePanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        panel.addView(header);

        LinearLayout providerRow = new LinearLayout(this);
        providerRow.setOrientation(LinearLayout.HORIZONTAL);
        geminiChip = providerChip("✦  Gemini", true);
        deepSeekChip = providerChip("◈  DeepSeek", false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        chipLp.rightMargin = dp(7);
        providerRow.addView(geminiChip, chipLp);
        LinearLayout.LayoutParams chip2Lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        providerRow.addView(deepSeekChip, chip2Lp);
        panel.addView(providerRow);

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(dp(2), dp(7), dp(2), dp(2));
        TextView sizeTitle = text("Ukuran tombol", 11, muted, true);
        sizeRow.addView(sizeTitle, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView sizeMinus = actionPill("A−", pale, navy);
        sizeMinus.setTextSize(13);
        sizeRow.addView(sizeMinus, new LinearLayout.LayoutParams(dp(42), dp(32)));
        TextView sizeValue = text(getBubbleSizeDp() + " dp", 11, ink, true);
        sizeValue.setGravity(Gravity.CENTER);
        sizeRow.addView(sizeValue, new LinearLayout.LayoutParams(dp(58), dp(32)));
        TextView sizePlus = actionPill("A+", pale, navy);
        sizePlus.setTextSize(13);
        sizeRow.addView(sizePlus, new LinearLayout.LayoutParams(dp(42), dp(32)));
        sizeMinus.setOnClickListener(v -> changeBubbleSize(-4, sizeValue));
        sizePlus.setOnClickListener(v -> changeBubbleSize(4, sizeValue));
        panel.addView(sizeRow);

        attachmentLabel = text("", 11, teal, true);
        attachmentLabel.setPadding(dp(3), dp(5), dp(3), dp(2));
        panel.addView(attachmentLabel);
        updateAttachmentLabel();

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(0, dp(8), 0, dp(8));
        messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(1), dp(3), dp(1), dp(8));
        chatScroll.addView(messagesBox, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.topMargin = dp(2);
        panel.addView(chatScroll, scrollLp);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.BOTTOM);
        messageInput = new EditText(this);
        messageInput.setTextSize(14);
        messageInput.setTextColor(ink);
        messageInput.setHintTextColor(Color.rgb(145, 156, 165));
        messageInput.setHint("Tanyakan sesuatu...");
        messageInput.setMinLines(1);
        messageInput.setMaxLines(3);
        messageInput.setPadding(dp(13), dp(10), dp(13), dp(10));
        messageInput.setBackground(round(pale, 16));
        messageInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        messageInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputRow.addView(messageInput, new LinearLayout.LayoutParams(0, dp(49), 1));
        sendButton = actionPill("➤", teal, Color.WHITE);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(49), dp(49));
        sendLp.leftMargin = dp(8);
        inputRow.addView(sendButton, sendLp);
        panel.addView(inputRow);

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView screenshot = text("▣  Analisis layar", 12, navy, true);
        screenshot.setGravity(Gravity.CENTER);
        screenshot.setPadding(dp(8), dp(10), dp(8), dp(7));
        screenshot.setOnClickListener(v -> beginCapture());
        tools.addView(screenshot, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView settings = text("⚙  Pengaturan", 12, muted, true);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v -> openSettings());
        tools.addView(settings, new LinearLayout.LayoutParams(0, dp(42), 1));
        panel.addView(tools);

        sendButton.setOnClickListener(v -> sendMessage());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(22), dp(420)),
                Math.min((int) (getResources().getDisplayMetrics().heightPixels * 0.70f), dp(590)),
                overlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(25);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        try {
            windowManager.addView(panel, lp);
            refreshProviderChips();
            if (waitingForReply && sendButton != null) {
                sendButton.setText("…");
                sendButton.setEnabled(false);
            }
            if (history.isEmpty()) {
                addMessage("assistant", "Halo! Aku siap membantu. Pilih Gemini atau DeepSeek, lalu ketik pertanyaanmu. Kamu juga bisa mengambil screenshot layar.");
            } else {
                for (ChatMessage item : history) {
                    appendBubble(item.role.equals("user") ? "user" : "assistant", item.text);
                }
            }
        } catch (Exception e) {
            panel = null;
            toast("Panel tidak dapat dibuka. Pastikan izin tampil di atas aplikasi aktif.");
        }
    }

    private int getBubbleSizeDp() {
        return Math.max(42, Math.min(88, getPreferences().getInt("bubble_size", 58)));
    }

    private void changeBubbleSize(int delta, TextView label) {
        int next = Math.max(42, Math.min(88, getBubbleSizeDp() + delta));
        getPreferences().edit().putInt("bubble_size", next).apply();
        label.setText(next + " dp");
        if (bubble != null && bubbleParams != null) {
            int old = bubbleParams.width;
            int newSize = dp(next);
            bubbleParams.width = newSize;
            bubbleParams.height = newSize;
            int screenW = getResources().getDisplayMetrics().widthPixels;
            bubbleParams.x = Math.max(0, Math.min(screenW - newSize, bubbleParams.x + (old - newSize) / 2));
            if (bubble instanceof FrameLayout && ((FrameLayout) bubble).getChildCount() > 0) {
                View inner = ((FrameLayout) bubble).getChildAt(0);
                int innerSize = dp(Math.max(22, next * 54 / 100));
                FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER);
                inner.setLayoutParams(innerLp);
            }
            try { windowManager.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
            getPreferences().edit().putInt("bubble_x", bubbleParams.x).apply();
        }
    }

    private TextView providerChip(String label, boolean selected) {
        TextView chip = actionPill(label, selected ? navy : pale, selected ? Color.WHITE : muted);
        chip.setTextSize(12);
        chip.setOnClickListener(v -> {
            selectedProvider = label.contains("Gemini") ? "Gemini" : "DeepSeek";
            getPreferences().edit().putString("preferred_provider", selectedProvider).apply();
            refreshProviderChips();
        });
        return chip;
    }

    private void refreshProviderChips() {
        if (geminiChip == null || deepSeekChip == null) return;
        boolean gemini = "Gemini".equals(selectedProvider);
        geminiChip.setBackground(round(gemini ? navy : pale, 12));
        geminiChip.setTextColor(gemini ? Color.WHITE : muted);
        deepSeekChip.setBackground(round(gemini ? pale : navy, 12));
        deepSeekChip.setTextColor(gemini ? muted : Color.WHITE);
    }

    private void sendMessage() {
        if (waitingForReply || messageInput == null) return;
        String question = messageInput.getText().toString().trim();
        if (question.isEmpty() && pendingImage == null) {
            toast("Tulis pertanyaan atau ambil screenshot terlebih dahulu.");
            return;
        }
        if (question.isEmpty()) question = "Jelaskan isi screenshot ini secara lengkap dan ringkas.";
        File image = pendingImage;
        pendingImage = null;
        updateAttachmentLabel();
        messageInput.setText("");
        appendBubble("user", question + (image != null ? "\n📷 Screenshot dilampirkan" : ""));
        history.add(new ChatMessage("user", question));
        trimHistory();
        waitingForReply = true;
        sendButton.setText("…");
        sendButton.setEnabled(false);
        final String provider = selectedProvider;
        final String finalQuestion = question;
        executor.execute(() -> {
            String answer;
            String actualProvider = provider;
            try {
                AiClient.Result response = AiClient.ask(provider, finalQuestion, image,
                        new ArrayList<>(history.subList(0, Math.max(0, history.size() - 1))));
                actualProvider = response.provider;
                answer = response.usedFallback
                        ? "↪ " + provider + " bermasalah, otomatis beralih ke " + response.provider + ".\n\n" + response.text
                        : response.text;
            } catch (Exception e) {
                String errorText = safeMessage(e);
                answer = errorText.startsWith("Kedua layanan AI belum berhasil.")
                        ? errorText : "Kedua layanan AI belum berhasil.\n\n" + errorText;
            }
            final String finalAnswer = answer;
            final String successfulProvider = actualProvider;
            mainHandler.post(() -> {
                waitingForReply = false;
                if (!finalAnswer.startsWith("Kedua layanan AI belum berhasil.")) {
                    selectedProvider = successfulProvider;
                    getPreferences().edit().putString("preferred_provider", selectedProvider).apply();
                    refreshProviderChips();
                }
                if (sendButton != null) {
                    sendButton.setText("➤");
                    sendButton.setEnabled(true);
                }
                // X only closes the panel. The foreground service keeps this request alive.
                // Save the answer even while the panel is closed; show it immediately when open.
                history.add(new ChatMessage("assistant", finalAnswer));
                trimHistory();
                if (panel != null) appendBubble("assistant", finalAnswer);
            });
            if (image != null && image.exists()) image.delete();
        });
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return "Periksa koneksi internet, kuota, dan status API penyedia AI.";
        return message;
    }

    private void trimHistory() {
        while (history.size() > 12) history.remove(0);
    }

    private void appendBubble(String role, String value) {
        if (messagesBox == null) return;
        TextView bubbleText = text(value, 13, "user".equals(role) ? Color.WHITE : ink, false);
        bubbleText.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubbleText.setLineSpacing(dp(2), 1f);
        bubbleText.setBackground(round("user".equals(role) ? navy : pale, 16));
        bubbleText.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI Assistant", value));
            toast("Teks disalin.");
            return true;
        });
        LinearLayout line = new LinearLayout(this);
        line.setGravity("user".equals(role) ? Gravity.END : Gravity.START);
        line.addView(bubbleText, new LinearLayout.LayoutParams(
                Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.72f), dp(290)), -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        messagesBox.addView(line, lp);
        scrollToBottom();
    }

    private void addMessage(String role, String value) {
        if ("system".equals(role)) {
            appendBubble("assistant", value);
        } else {
            appendBubble(role, value);
            if ("assistant".equals(role)) history.add(new ChatMessage("assistant", value));
        }
    }

    private void scrollToBottom() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void updateAttachmentLabel() {
        if (attachmentLabel == null) return;
        attachmentLabel.setText(pendingImage != null && pendingImage.exists()
                ? "📎 Screenshot siap dikirim" : " ");
    }

    private void beginCapture() {
        removePanel();
        if (bubble != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
        try {
            Intent intent = new Intent(this, CapturePermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            showPanel();
            toast("Tidak dapat membuka izin screenshot: " + e.getMessage());
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void removePanel() {
        if (panel != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) { }
            panel = null;
            messagesBox = null;
            chatScroll = null;
            messageInput = null;
            sendButton = null;
            geminiChip = null;
            deepSeekChip = null;
            attachmentLabel = null;
        }
    }

    private TextView actionPill(String label, int background, int foreground) {
        TextView t = text(label, 15, foreground, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(background, 12));
        return t;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private android.content.SharedPreferences getPreferences() {
        return getSharedPreferences("floating_position", MODE_PRIVATE);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    @Override public void onDestroy() {
        removePanel();
        if (bubble != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
