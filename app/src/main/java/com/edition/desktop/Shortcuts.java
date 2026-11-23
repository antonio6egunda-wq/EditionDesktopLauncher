package com.edition.desktop;

import android.view.KeyEvent;

/**
 * Atalhos de teclado do launcher. Quem chama é a atividade principal, antes de a tecla chegar
 * a qualquer vista. Assim os atalhos funcionam em qualquer parte do ecrã, mas as teclas de
 * texto continuam a ser entregues ao campo de pesquisa quando ele tem o foco.
 */
final class Shortcuts {

    /**
     * Lista mostrada na ajuda (F1 ou Ctrl+/). Cada linha é {tecla, descrição}.
     * Mantém-se em sincronia com {@link #handle(Launcher, KeyEvent)}.
     */
    static final String[][] HELP = {
            {"Win  ou  Ctrl+Esc", "Abrir ou fechar as Aplicações"},
            {"Ctrl+F  ou  /", "Pesquisar na barra de tarefas"},
            {"Win+A  ou  Ctrl+Shift+N", "Abrir ou fechar o centro de ações"},
            {"Win+D  ou  Ctrl+D", "Mostrar o ambiente de trabalho"},
            {"Ctrl+,", "Configurações do launcher"},
            {"Ctrl+Shift+O", "Ligar ou desligar o modo Organizar"},
            {"Setas  ou  Tab", "Mover o foco entre os ícones"},
            {"Enter", "Abrir o ícone em foco"},
            {"Shift+F10  ou  Tecla Menu", "Abrir o menu do ícone em foco"},
            {"F2", "Renomear o ícone em foco"},
            {"Esc", "Fechar o que estiver aberto"},
            {"F1  ou  Ctrl+/", "Mostrar esta ajuda"},
            {"Rato: clique direito", "Menu do ícone ou do ambiente"},
            {"Rato: passar o cursor", "Destaca o ícone ou a opção"},
    };

    private Shortcuts() { }

    /**
     * Trata uma tecla. Devolve true se o atalho foi usado (a tecla não segue para as vistas).
     * Só reage a DOWN, e ignora repetições automáticas nos atalhos de alternar (para não
     * abrir e fechar o mesmo painel sem parar).
     */
    static boolean handle(Launcher l, KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;

        int key = e.getKeyCode();
        boolean ctrl = e.isCtrlPressed();
        boolean shift = e.isShiftPressed();
        boolean meta = e.isMetaPressed();
        boolean first = e.getRepeatCount() == 0;
        boolean typing = l.isTyping();

        // Com o menu de contexto aberto, só as setas, Enter e Esc têm efeito.
        if (l.isMenuOpen()) {
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP: return l.menuMove(-1);
                case KeyEvent.KEYCODE_DPAD_DOWN: return l.menuMove(1);
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER: return l.menuActivate();
                case KeyEvent.KEYCODE_ESCAPE:
                    l.closeMenu();
                    return true;
                default: return false;
            }
        }

        // Win sozinha (ou com outra tecla) abre e fecha as Aplicações.
        if (key == KeyEvent.KEYCODE_META_LEFT || key == KeyEvent.KEYCODE_META_RIGHT) {
            if (first) l.togglePanel(Launcher.PANEL_APPS);
            return true;
        }
        if (ctrl && key == KeyEvent.KEYCODE_ESCAPE) {
            if (first) l.togglePanel(Launcher.PANEL_APPS);
            return true;
        }

        if (key == KeyEvent.KEYCODE_F1 || (ctrl && key == KeyEvent.KEYCODE_SLASH)) {
            if (first) l.showHelp();
            return true;
        }
        if (key == KeyEvent.KEYCODE_ESCAPE) {
            return l.handleBack();
        }
        if ((meta && key == KeyEvent.KEYCODE_A) || (ctrl && shift && key == KeyEvent.KEYCODE_N)) {
            if (first) l.togglePanel(Launcher.PANEL_NOTIF);
            return true;
        }
        if ((meta && key == KeyEvent.KEYCODE_D) || (ctrl && key == KeyEvent.KEYCODE_D)) {
            if (first) l.showDesktop();
            return true;
        }
        if (ctrl && key == KeyEvent.KEYCODE_COMMA) {
            if (first) l.openSettings(SettingsPanel.MAIN);
            return true;
        }
        if (ctrl && shift && key == KeyEvent.KEYCODE_O) {
            if (first) l.toggleArranging();
            return true;
        }
        if ((ctrl && (key == KeyEvent.KEYCODE_F || key == KeyEvent.KEYCODE_K))
                || (!typing && key == KeyEvent.KEYCODE_SLASH)) {
            l.focusSearch();
            return true;
        }

        // Sugestões da pesquisa: setas para escolher, Enter (tratado pelo campo) para abrir.
        if (l.isSearchFocused() && l.hasSuggestions()) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) { l.searchMove(-1); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) { l.searchMove(1); return true; }
        }

        if ((key == KeyEvent.KEYCODE_F10 && shift) || key == KeyEvent.KEYCODE_MENU) {
            return l.menuForFocused();
        }

        // A partir daqui, as teclas só atuam sobre o ambiente, se nada estiver a ser escrito.
        if (typing || l.anyPanelOpen()) return false;

        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return l.moveFocus(-1, 0);
            case KeyEvent.KEYCODE_DPAD_RIGHT: return l.moveFocus(1, 0);
            case KeyEvent.KEYCODE_DPAD_UP: return l.moveFocus(0, -1);
            case KeyEvent.KEYCODE_DPAD_DOWN: return l.moveFocus(0, 1);
            case KeyEvent.KEYCODE_TAB: return l.cycleFocus(shift ? -1 : 1);
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return l.activateFocused();
            case KeyEvent.KEYCODE_F2: return l.renameFocused();
            default: return false;
        }
    }
}
