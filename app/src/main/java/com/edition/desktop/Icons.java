package com.edition.desktop;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Nomes dos recursos de ícones e funções para os obter. */
final class Icons {

    /** Ícones que o utilizador pode atribuir a uma aplicação (res/drawable-nodpi). */
    static final String[] CHOICES = {
            "ic_app_calculator_one", "ic_app_calendar", "ic_app_file_explorer", "ic_app_gallery_one",
            "ic_app_google_chrome", "ic_app_google_drive", "ic_app_google_play_store_one",
            "ic_app_microsoft_groove_music_one", "ic_app_microsoft_office_mobile", "ic_app_microsoft_onedrive",
            "ic_app_microsoft_onenote", "ic_app_microsoft_outlook", "ic_app_microsoft_powerpoint",
            "ic_app_microsoft_solitaire", "ic_app_microsoft_sway", "ic_app_microsoft_teams",
            "ic_app_microsoft_translate", "ic_app_microsoft_word", "ic_app_settings_one",
            "ic_app_vlc_media_player", "ic_app_whatsapp",
    };

    static final String[] AVATARS = {"ic_avatar_00", "ic_avatar_01"};

    private Icons() { }

    /** Ícone do próprio launcher pelo nome do recurso, ou null se não existir. */
    static Drawable feature(Context context, String name) {
        if (name == null || name.isEmpty()) return null;
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return id == 0 ? null : context.getDrawable(id);
    }

    /** Ícone de uma aplicação: o do sistema, depois a marca conhecida, e por fim o genérico. */
    static Drawable appIcon(Context context, AppCatalog catalog, AppCatalog.App app) {
        Drawable system = catalog.systemIcon(app.packageName);
        if (system != null) return system;
        Drawable brand = feature(context, AppCatalog.brandIconFor(app.label));
        return brand != null ? brand : feature(context, "ic_app");
    }

    /** Nome do recurso do avatar para um índice (limitado aos avatares existentes). */
    static String avatarName(int index) {
        return AVATARS[Prefs.clamp(index, 0, AVATARS.length - 1)];
    }

    /** Nome do ícone de volume conforme o nível: silêncio, baixo ou alto. */
    static String volumeIcon(int level) {
        if (level <= 0) return "ic_volume_off";
        return level < 50 ? "ic_volume_down" : "ic_volume_up";
    }
}
