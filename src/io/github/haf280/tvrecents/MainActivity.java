package io.github.haf280.tvrecents;

import android.app.Activity;
import android.content.Intent;

/**
 * The home screen tile. Shows the switcher over the screen and steps aside, or opens setup if the
 * Recents accessibility service isn't on yet.
 */
public class MainActivity extends Activity {
    @Override protected void onResume() {
        super.onResume();
        if (!SetupActivity.complete()) startActivity(new Intent(this, SetupActivity.class));
        else RecentsService.instance.showPanel(null);
        finish();
    }
}
