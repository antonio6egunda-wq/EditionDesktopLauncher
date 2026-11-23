package com.edition.desktop;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lista das aplicações que se podem lançar e os seus ícones do sistema. */
final class AppCatalog {

    static final class App {
        final String packageName;
        final String label;

        /** Uma aplicação instalada: nome do pacote e o nome que aparece ao utilizador. */
        App(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    /** Ícones de marca usados quando o sistema não devolve o ícone da aplicação. */
    private static final String[][] BRAND_ICONS = {
            {"onedrive", "ic_app_microsoft_onedrive"},
            {"google drive", "ic_app_google_drive"},
            {"play store", "ic_app_google_play_store_one"},
            {"chrome", "ic_app_google_chrome"},
            {"whatsapp", "ic_app_whatsapp"},
            {"powerpoint", "ic_app_microsoft_powerpoint"},
            {"onenote", "ic_app_microsoft_onenote"},
            {"outlook", "ic_app_microsoft_outlook"},
            {"teams", "ic_app_microsoft_teams"},
            {"solitaire", "ic_app_microsoft_solitaire"},
            {"translate", "ic_app_microsoft_translate"},
            {"office", "ic_app_microsoft_office_mobile"},
            {"word", "ic_app_microsoft_word"},
            {"sway", "ic_app_microsoft_sway"},
            {"groove", "ic_app_microsoft_groove_music_one"},
            {"vlc", "ic_app_vlc_media_player"},
            {"calculadora", "ic_app_calculator_one"},
            {"calculator", "ic_app_calculator_one"},
            {"calendario", "ic_app_calendar"},
            {"calendar", "ic_app_calendar"},
            {"ficheiros", "ic_app_file_explorer"},
            {"file explorer", "ic_app_file_explorer"},
            {"fotos", "ic_app_gallery_one"},
    };

    private final PackageManager pm;
    private final String ownPackage;
    private final List<App> apps = new ArrayList<>();
    private final Map<String, App> byPackage = new HashMap<>();
    private final Map<String, Drawable> systemIcons = new HashMap<>();

    /** Cria o catálogo de aplicações, sem as carregar ainda. Guarda o nome do próprio launcher para o excluir. */
    AppCatalog(Context context) {
        pm = context.getPackageManager();
        ownPackage = context.getPackageName();
    }

    /** Volta a ler as aplicações instaladas, ordenadas pelo nome. */
    void reload() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, String> labels = new LinkedHashMap<>();
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(ownPackage) || labels.containsKey(pkg)) continue;
            labels.put(pkg, String.valueOf(info.loadLabel(pm)));
        }
        apps.clear();
        byPackage.clear();
        systemIcons.clear();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            App app = new App(entry.getKey(), entry.getValue());
            apps.add(app);
            byPackage.put(app.packageName, app);
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    /** Lista de todas as aplicações carregadas, pela ordem alfabética. */
    List<App> all() {
        return apps;
    }

    /** Procura uma aplicação pelo nome do pacote. Devolve null se não estiver instalada. */
    App find(String pkg) {
        return byPackage.get(pkg);
    }

    /** Ícone da aplicação dado pelo sistema (guardado em cache), ou null. */
    Drawable systemIcon(String pkg) {
        if (systemIcons.containsKey(pkg)) return systemIcons.get(pkg);
        Drawable icon = null;
        try {
            icon = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {
            // A aplicação já não existe: fica sem ícone do sistema.
        }
        systemIcons.put(pkg, icon);
        return icon;
    }

    /** Texto em minúsculas e sem acentos, para comparar pesquisas («definições» = «definicoes»). */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return plain.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Nome do recurso de marca para um nome de aplicação, ou "" se não houver. */
    static String brandIconFor(String label) {
        String lower = normalize(label);
        for (String[] pair : BRAND_ICONS) {
            if (lower.contains(normalize(pair[0]))) return pair[1];
        }
        return "";
    }
}
