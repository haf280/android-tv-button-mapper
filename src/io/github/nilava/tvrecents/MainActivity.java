package io.github.nilava.tvrecents;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import java.util.List;

/**
 * The home screen tile. With the Recents accessibility service on, it shows the switcher over
 * the screen and steps aside. Without it, it shows the same switcher full screen, and closing an
 * app opens its App info (Android 14 only lets the service press Force stop).
 */
public class MainActivity extends Activity {
    @Override protected void onResume() {
        super.onResume();
        if (!SetupActivity.complete(this)) {          // first run, or a switch was turned off
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        if (RecentsService.instance != null) {
            RecentsService.instance.showPanel(null);
            finish();
            return;
        }
        List<AppList.Item> items = AppList.load(this, 12);
        setContentView(new Panel(this).build(items, null,
                "Turn on the Recents service in Settings > Accessibility to close apps from here", new Panel.Actions() {
            @Override public void open(AppList.Item it) {
                MainActivity.this.open(new Intent(it.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }
            @Override public void close(AppList.Item it) {
                MainActivity.this.open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.pkg, null)));
            }
            @Override public void closeAll(List<AppList.Item> all) {
                MainActivity.this.open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            @Override public void dismiss() { finish(); }
        }));
        getWindow().getDecorView().setBackgroundColor(0xFF0B0B0D);
    }

    void open(Intent i) {
        try { startActivity(i); } catch (Exception x) { Toast.makeText(this, "Couldn't open that screen", Toast.LENGTH_SHORT).show(); }
    }
}
