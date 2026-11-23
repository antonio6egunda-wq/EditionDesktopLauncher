package com.edition.desktop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/** Atividade principal: aloja o launcher, construído em Java, e encaminha os eventos do sistema. */
public class MainActivity extends Activity {

    private Launcher launcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        launcher = new Launcher(this);
        setContentView(launcher.root());
    }

    @Override
    protected void onResume() {
        super.onResume();
        launcher.onResume();
    }

    @Override
    protected void onPause() {
        launcher.onPause();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Launcher.REQUEST_PICK_WALLPAPER && resultCode == RESULT_OK) {
            launcher.onWallpaperPicked(data != null ? data.getData() : null);
        }
    }

    /** Num launcher o Voltar só fecha o que estiver aberto. Nunca sai da página inicial. */
    @Override
    public void onBackPressed() {
        launcher.handleBack();
    }

    @SuppressWarnings("deprecation")
    private void enterImmersiveMode() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
