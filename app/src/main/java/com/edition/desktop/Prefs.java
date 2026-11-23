package com.edition.desktop;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Preferências do launcher, guardadas em SharedPreferences. */
final class Prefs {

    /** Pacotes afixados no ambiente de trabalho (a chave é também o nome da zona). */
    static final String DESKTOP = "desktop";
    /** Pacotes afixados na barra. */
    static final String DOCK = "dock";
    /** Pacotes ocultos em todo o launcher. */
    static final String HIDDEN = "hidden";
    /** Apps abertas pelo launcher, a mais recente primeiro. */
    static final String RECENT = "recent";

    static final int RECENT_LIMIT = 3;
    static final int WALLPAPER_CUSTOM = -1;
    static final String DEFAULT_NAME = "Utilizador";
    static final int PROFILE_NAME_MAX = 24;
    static final int NAME_MAX = 40;

    static final int BAR_HEIGHT_DEFAULT = 68;
    static final int BAR_HEIGHT_MIN = 56;
    static final int BAR_HEIGHT_MAX = 84;
    static final int BAR_ICON_DEFAULT = 40;
    static final int BAR_ICON_MIN = 20;
    static final int BAR_ICON_MAX = 44;
    static final int DESK_ICON_DEFAULT = 40;
    static final int DESK_ICON_MIN = 32;
    static final int DESK_ICON_MAX = 72;

    private static final String FILE = "desktop-launcher";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Lista de pacotes guardada como texto separado por vírgulas. Devolve sempre uma cópia editável. */
    List<String> list(String key) {
        List<String> out = new ArrayList<>();
        String raw = sp.getString(key, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    void putList(String key, List<String> values) {
        sp.edit().putString(key, TextUtils.join(",", values)).apply();
    }

    boolean isHidden(String pkg) {
        return list(HIDDEN).contains(pkg);
    }

    String customName(String pkg) {
        return sp.getString("name:" + pkg, null);
    }

    void setCustomName(String pkg, String value) {
        put("name:" + pkg, value);
    }

    String customIcon(String pkg) {
        return sp.getString("icon:" + pkg, null);
    }

    void setCustomIcon(String pkg, String value) {
        put("icon:" + pkg, value);
    }

    String profileName() {
        return sp.getString("profile_name", DEFAULT_NAME);
    }

    /** Com valor `null` volta ao nome por omissão. */
    void setProfileName(String value) {
        put("profile_name", value);
    }

    int avatar() {
        return sp.getInt("profile_avatar", 0);
    }

    void setAvatar(int index) {
        sp.edit().putInt("profile_avatar", index).apply();
    }

    int barHeight() {
        return clamp(sp.getInt("bar_height", BAR_HEIGHT_DEFAULT), BAR_HEIGHT_MIN, BAR_HEIGHT_MAX);
    }

    void setBarHeight(int value) {
        sp.edit().putInt("bar_height", clamp(value, BAR_HEIGHT_MIN, BAR_HEIGHT_MAX)).apply();
    }

    int barIcon() {
        return clamp(sp.getInt("bar_icon", BAR_ICON_DEFAULT), BAR_ICON_MIN, BAR_ICON_MAX);
    }

    void setBarIcon(int value) {
        sp.edit().putInt("bar_icon", clamp(value, BAR_ICON_MIN, BAR_ICON_MAX)).apply();
    }

    int deskIcon() {
        return clamp(sp.getInt("desk_icon", DESK_ICON_DEFAULT), DESK_ICON_MIN, DESK_ICON_MAX);
    }

    void setDeskIcon(int value) {
        sp.edit().putInt("desk_icon", clamp(value, DESK_ICON_MIN, DESK_ICON_MAX)).apply();
    }

    void resetBar() {
        sp.edit().remove("bar_height").remove("bar_icon").remove("desk_icon").apply();
    }

    /** Índice 0 a 4 de um predefinido, ou WALLPAPER_CUSTOM para a imagem da galeria. */
    int wallpaper() {
        return sp.getInt("wallpaper", 0);
    }

    void setWallpaper(int index) {
        sp.edit().putInt("wallpaper", index).apply();
    }

    private void put(String key, String value) {
        SharedPreferences.Editor editor = sp.edit();
        if (value == null) editor.remove(key);
        else editor.putString(key, value);
        editor.apply();
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
