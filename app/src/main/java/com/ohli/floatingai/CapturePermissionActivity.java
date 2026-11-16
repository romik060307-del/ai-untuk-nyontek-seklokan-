package com.ohli.floatingai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/** Transparent activity used only to display Android's explicit screen-capture consent dialog. */
public class CapturePermissionActivity extends Activity {
    private static final int REQUEST_CAPTURE = 6107;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "Fitur screenshot layar tidak tersedia di perangkat ini.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa meminta izin screenshot: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        } else {
            Intent overlay = new Intent(this, OverlayService.class);
            overlay.setAction(OverlayService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(overlay); else startService(overlay);
            Toast.makeText(this, "Izin screenshot dibatalkan.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
