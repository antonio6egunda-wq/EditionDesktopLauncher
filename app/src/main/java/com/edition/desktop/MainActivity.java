package com.edition.desktop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Atividade principal: aloja o launcher, construído em Java, e encaminha os eventos do sistema.
 * É aqui que chegam primeiro as teclas e os toques, antes de as vistas os receberem.
 */
public class MainActivity extends Activity {

    private Launcher launcher;

    /** Cria o launcher, ativa o modo imersivo e mostra a interface. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        launcher = new Launcher(this);
        setContentView(launcher.root());
    }

    /** Ao voltar ao primeiro plano, atualiza o launcher e o relógio. */
    @Override
    protected void onResume() {
        super.onResume();
        launcher.onResume();
    }

    /** Ao sair do primeiro plano, para as atualizações periódicas. */
    @Override
    protected void onPause() {
        launcher.onPause();
        super.onPause();
    }

    /** Ao recuperar o foco da janela, volta a ativar o modo imersivo. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    /** Recebe a imagem escolhida na galeria (papel de parede). */
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

    /**
     * Recebe todas as teclas antes das vistas. Se um atalho do {@link Shortcuts} tratar a tecla,
     * ela não segue para mais lado; caso contrário, o sistema trata-a normalmente.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (launcher != null && Shortcuts.handle(launcher, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /**
     * Regista a posição de cada toque ou clique (também do rato) antes de chegar às vistas,
     * para que os menus do ambiente abram no sítio certo.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (launcher != null) launcher.noteTouch(event);
        return super.dispatchTouchEvent(event);
    }

    /** Esconde barras do sistema e mantém o launcher em ecrã inteiro. */
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
