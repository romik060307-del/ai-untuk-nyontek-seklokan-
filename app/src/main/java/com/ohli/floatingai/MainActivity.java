package com.ohli.floatingai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Setup screen. Gemini and DeepSeek clients are packaged in the APK. */
public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 4101;
    private static final int REQ_NOTIFICATIONS = 4102;
    private final int navy = Color.rgb(13, 34, 56);
    private final int teal = Color.rgb(32, 182, 140);
    private final int ink = Color.rgb(28, 41, 54);
    private final int muted = Color.rgb(112, 126, 138);
    private final int pale = Color.rgb(244, 247, 248);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(pale);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(28), dp(22), dp(30));
        scroll.addView(page);

        TextView eyebrow = text("FLOATING AI ASSISTANT  ·  0.3.0", 10, teal, true);
        eyebrow.setLetterSpacing(0.14f);
        page.addView(eyebrow);
        TextView title = text("Asisten AI,\nselalu di sisi layar.", 30, navy, true);
        title.setPadding(0, dp(8), 0, dp(8));
        page.addView(title);
        TextView intro = text("Tombol transparan bergaya iPhone AssistiveTouch. Geser ke tepi layar, ketuk untuk membuka Gemini atau DeepSeek tanpa menutup aplikasi yang sedang dipakai.", 14, muted, false);
        intro.setLineSpacing(dp(3), 1f);
        page.addView(intro);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(round(navy, 24));
        FrameLayout previewStage = new FrameLayout(this);
        previewStage.setBackground(round(Color.rgb(29, 54, 74), 18));
        TextView previewText = text("Assistive AI", 14, Color.rgb(218, 231, 238), true);
        previewText.setGravity(Gravity.CENTER);
        previewStage.addView(previewText, new FrameLayout.LayoutParams(-1, dp(112)));
        FrameLayout fakeOrb = new FrameLayout(this);
        GradientDrawable outer = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xC7AEB6BF, 0xB65B636D, 0xD02E343B});
        outer.setShape(GradientDrawable.OVAL);
        outer.setStroke(dp(2), 0xE6FFFFFF);
        fakeOrb.setBackground(outer);
        TextView star = text("✦", 18, Color.WHITE, true);
        star.setGravity(Gravity.CENTER);
        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(0x55FFFFFF);
        inner.setStroke(dp(1), 0xDFFFFFFF);
        star.setBackground(inner);
        FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
        fakeOrb.addView(star, starLp);
        FrameLayout.LayoutParams orbLp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        orbLp.rightMargin = dp(15);
        previewStage.addView(fakeOrb, orbLp);
        hero.addView(previewStage);
        TextView heroTitle = text("Satu ketukan. Dua pilihan AI.", 17, Color.WHITE, true);
        heroTitle.setPadding(0, dp(15), 0, dp(4));
        hero.addView(heroTitle);
        hero.addView(text("Gemini  ·  DeepSeek  ·  Analisis screenshot", 12, Color.rgb(205, 220, 230), false));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.topMargin = dp(22);
        page.addView(hero, heroLp);

        LinearLayout connection = card();
        connection.addView(text("●  Gemini dan DeepSeek siap digunakan", 14, teal, true));
        TextView connectionInfo = text(
                "Koneksi AI sudah disiapkan di dalam APK. Tidak ada halaman gateway atau kolom API key. Pilih Gemini atau DeepSeek dari panel melayang, lalu langsung mulai chat. Internet dan kuota API penyedia tetap diperlukan.",
                12, muted, false);
        connectionInfo.setPadding(0, dp(8), 0, 0);
        connectionInfo.setLineSpacing(dp(3), 1f);
        connection.addView(connectionInfo);
        LinearLayout.LayoutParams connectionLp = new LinearLayout.LayoutParams(-1, -2);
        connectionLp.topMargin = dp(22);
        page.addView(connection, connectionLp);

        TextView permissionsTitle = text("Aktifkan AssistiveTouch", 19, navy, true);
        LinearLayout.LayoutParams permissionsLp = new LinearLayout.LayoutParams(-1, -2);
        permissionsLp.topMargin = dp(24);
        page.addView(permissionsTitle, permissionsLp);
        Button start = button("Aktifkan tombol melayang", navy, Color.WHITE);
        start.setOnClickListener(v -> startFloating());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(54));
        startLp.topMargin = dp(12);
        page.addView(start, startLp);
        Button stop = button("Matikan tombol melayang", Color.WHITE, navy);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            toast("Tombol melayang dimatikan.");
        });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, dp(48));
        stopLp.topMargin = dp(9);
        page.addView(stop, stopLp);

        TextView privacy = text("Privasi layar: screenshot hanya diambil setelah kamu menekan Analisis layar dan menyetujui dialog izin Android. Screenshot hanya dikirim ketika kamu memilih analisis; gambar diproses langsung oleh penyedia AI yang dipilih.", 11, muted, false);
        privacy.setLineSpacing(dp(3), 1f);
        privacy.setPadding(dp(2), dp(18), dp(2), dp(2));
        page.addView(privacy);
        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(Color.WHITE, 18));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int background, int foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(background, 14));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void startFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }
        requestNotificationPermissionIfNeeded();
        Intent service = new Intent(this, OverlayService.class);
        service.setAction(OverlayService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        toast("Assistive AI aktif. Lingkaran transparan sudah tersedia di layar.");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) startFloating();
            else toast("Izin tampil di atas aplikasi belum diberikan.");
        }
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
