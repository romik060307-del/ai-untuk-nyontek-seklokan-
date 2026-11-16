package com.ohli.floatingai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureService extends Service {
    static final String EXTRA_RESULT_CODE = "capture_result_code";
    static final String EXTRA_RESULT_DATA = "capture_result_data";
    private static final String CHANNEL_ID = "floating_ai_capture";
    private static final int NOTIFICATION_ID = 7302;

    private HandlerThread workerThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final AtomicBoolean captured = new AtomicBoolean(false);

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResult.RESULT_CANCELED);
        Intent resultData = getResultData(intent);
        if (resultData == null || resultCode != ActivityResult.RESULT_OK) { stopSelf(); return START_NOT_STICKY; }
        startCaptureForeground();
        workerThread = new HandlerThread("floating-ai-screen-capture");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("Android tidak memberikan sesi screenshot.");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { cleanupAndStop(); }
            }, worker);
            setupReader();
            worker.postDelayed(() -> {
                if (!captured.get()) {
                    notifyOverlay("Screenshot belum berhasil diambil. Coba sekali lagi.");
                    cleanupAndStop();
                }
            }, 7000);
        } catch (Exception e) {
            notifyOverlay("Gagal mengambil screenshot: " + (e.getMessage() == null ? "kesalahan Android" : e.getMessage()));
            cleanupAndStop();
        }
        return START_NOT_STICKY;
    }

    private Intent getResultData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        //noinspection deprecation
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void setupReader() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics windowMetrics = wm.getMaximumWindowMetrics();
            android.graphics.Rect bounds = windowMetrics.getBounds();
            metrics.widthPixels = bounds.width();
            metrics.heightPixels = bounds.height();
            metrics.densityDpi = getResources().getDisplayMetrics().densityDpi;
        } else {
            //noinspection deprecation
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            if (!captured.compareAndSet(false, true)) {
                Image extra = reader.acquireLatestImage();
                if (extra != null) extra.close();
                return;
            }
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) { captured.set(false); return; }
                Bitmap bitmap = imageToBitmap(image, width, height);
                File file = saveBitmap(bitmap);
                bitmap.recycle();
                Intent overlay = new Intent(this, OverlayService.class);
                overlay.setAction(OverlayService.ACTION_SHOW_IMAGE);
                overlay.putExtra("image_path", file.getAbsolutePath());
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(overlay); else startService(overlay);
            } catch (Exception e) {
                notifyOverlay("Gagal memproses screenshot: " + (e.getMessage() == null ? "kesalahan gambar" : e.getMessage()));
            } finally {
                if (image != null) image.close();
                cleanupAndStop();
            }
        }, worker);
        virtualDisplay = projection.createVirtualDisplay("AI Floating Assistant screen capture",
                width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, worker);
    }

    private Bitmap imageToBitmap(Image image, int width, int height) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private File saveBitmap(Bitmap bitmap) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(getCacheDir(), "screen_" + stamp + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
            out.flush();
        }
        return file;
    }

    private void startCaptureForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Screen capture", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifikasi saat AI mengambil screenshot atas izin pengguna.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("AI sedang menyiapkan screenshot")
                .setContentText("Tangkapan layar hanya diambil setelah kamu menyetujuinya.")
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void notifyOverlay(String message) {
        Intent overlay = new Intent(this, OverlayService.class);
        overlay.setAction(OverlayService.ACTION_CAPTURE_MESSAGE);
        overlay.putExtra("capture_message", message);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(overlay); else startService(overlay);
    }

    private void cleanupAndStop() {
        if (virtualDisplay != null) { try { virtualDisplay.release(); } catch (Exception ignored) { } virtualDisplay = null; }
        if (imageReader != null) { try { imageReader.close(); } catch (Exception ignored) { } imageReader = null; }
        if (projection != null) { try { projection.stop(); } catch (Exception ignored) { } projection = null; }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else {
            //noinspection deprecation
            stopForeground(true);
        }
        if (workerThread != null) { workerThread.quitSafely(); workerThread = null; worker = null; }
        stopSelf();
    }

    @Override public void onDestroy() {
        if (virtualDisplay != null) { try { virtualDisplay.release(); } catch (Exception ignored) { } }
        if (imageReader != null) { try { imageReader.close(); } catch (Exception ignored) { } }
        if (projection != null) { try { projection.stop(); } catch (Exception ignored) { } }
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class ActivityResult {
        static final int RESULT_CANCELED = 0;
        static final int RESULT_OK = -1;
    }
}
