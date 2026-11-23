package com.edition.desktop;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Ecrã principal do launcher: ambiente de trabalho, barra de tarefas e ligação aos painéis.
 * Toda a interface é construída em Java, sem WebView.
 */
final class Launcher {

    static final int REQUEST_PICK_WALLPAPER = 1001;
    static final int WALLPAPER_COUNT = 5;

    static final String ZONE_DESKTOP = Prefs.DESKTOP;
    static final String ZONE_DOCK = Prefs.DOCK;

    static final int PANEL_NONE = 0;
    static final int PANEL_APPS = 1;
    static final int PANEL_NOTIF = 2;
    static final int PANEL_SETTINGS = 3;

    private static final int SEARCH_APP_LIMIT = 5;
    private static final int WALLPAPER_MAX_PX = 1600;
    private static final String CUSTOM_WALLPAPER_FILE = "wallpaper_custom.jpg";
    private static final long TICK_MS = 6000;
    private static final int[] WALLPAPERS = {
            R.drawable.wallpaper_00, R.drawable.wallpaper_01, R.drawable.wallpaper_02,
            R.drawable.wallpaper_03, R.drawable.wallpaper_04,
    };

    private final MainActivity activity;
    private final Context context;
    private final Prefs prefs;
    private final AppCatalog catalog;
    private final SystemActions system;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FrameLayout root;
    private final ImageView wallpaperView;
    private final FrameLayout desktopArea;
    private final LinearLayout desktopColumns;
    private final TextView deskHint;
    private final LinearLayout arrangeBar;
    private final LinearLayout suggestBox;
    private final List<View> barButtons = new ArrayList<>();
    private final AppsPanel appsPanel;
    private final SettingsPanel settingsPanel;
    private final NotificationPanel notificationPanel;

    private LinearLayout taskbar;
    private LinearLayout dockRow;
    private EditText searchField;
    private ImageView volumeIcon;
    private View notifDot;
    private TextView timeView;
    private TextView dateView;

    private int currentPanel = PANEL_NONE;
    private boolean arranging;
    private String selectedPkg;
    private String selectedZone;
    private List<SearchItem> suggestions = new ArrayList<>();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateTray();
            if (currentPanel == PANEL_NOTIF) notificationPanel.refresh();
            handler.postDelayed(this, TICK_MS);
        }
    };

    Launcher(MainActivity activity) {
        this.activity = activity;
        this.context = activity;
        this.prefs = new Prefs(activity);
        this.catalog = new AppCatalog(activity);
        this.system = new SystemActions(activity);

        root = new FrameLayout(activity);
        root.setBackgroundColor(Ui.BG);

        wallpaperView = new ImageView(activity);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaperView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // Espaço vazio do ambiente: toque fecha os painéis, toque longo abre o menu.
        desktopArea = new FrameLayout(activity);
        desktopArea.setOnClickListener(v -> closePanels());
        desktopArea.setOnLongClickListener(v -> onDesktopLongPress());
        root.addView(desktopArea, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        desktopColumns = new LinearLayout(activity);
        desktopColumns.setOrientation(LinearLayout.HORIZONTAL);
        desktopArea.addView(desktopColumns, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.START));

        deskHint = Ui.text(activity,
                "Mantém premido o ambiente de trabalho para abrir o menu, ou uma aplicação para a afixar aqui.",
                13, Ui.MUTED, false);
        deskHint.setGravity(Gravity.CENTER);
        deskHint.setMaxWidth(dp(360));
        desktopArea.addView(deskHint, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));

        arrangeBar = buildArrangeBar();
        desktopArea.addView(arrangeBar, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        taskbar = buildTaskbar();
        root.addView(taskbar, frame(MATCH_PARENT, prefs.barHeight(), Gravity.BOTTOM));

        suggestBox = new LinearLayout(activity);
        suggestBox.setOrientation(LinearLayout.VERTICAL);
        suggestBox.setPadding(dp(6), dp(6), dp(6), dp(6));
        suggestBox.setBackground(Ui.box(Ui.SURFACE, dp(12), dp(1), Ui.STROKE));
        suggestBox.setClickable(true);
        suggestBox.setVisibility(View.GONE);
        root.addView(suggestBox, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        appsPanel = new AppsPanel(this);
        settingsPanel = new SettingsPanel(this);
        notificationPanel = new NotificationPanel(this);
        root.addView(appsPanel.view());
        root.addView(settingsPanel.view());
        root.addView(notificationPanel.view());

        layoutPanels();
        layoutDesktop();
        applyWallpaper(prefs.wallpaper());
    }

    FrameLayout root() {
        return root;
    }

    /* ---------- Acesso para os painéis e diálogos ---------- */

    MainActivity activity() {
        return activity;
    }

    Prefs prefs() {
        return prefs;
    }

    AppCatalog catalog() {
        return catalog;
    }

    SystemActions system() {
        return system;
    }

    /** Aplicações que o utilizador não ocultou, pela ordem alfabética. */
    List<AppCatalog.App> visibleApps() {
        List<AppCatalog.App> out = new ArrayList<>();
        for (AppCatalog.App app : catalog.all()) {
            if (!prefs.isHidden(app.packageName)) out.add(app);
        }
        return out;
    }

    String displayName(AppCatalog.App app) {
        String custom = prefs.customName(app.packageName);
        return custom != null ? custom : app.label;
    }

    Drawable iconFor(AppCatalog.App app) {
        String custom = prefs.customIcon(app.packageName);
        if (custom != null) {
            Drawable icon = Icons.feature(context, custom);
            if (icon != null) return icon;
        }
        return Icons.appIcon(context, catalog, app);
    }

    /* ---------- Ciclo de vida ---------- */

    void onResume() {
        catalog.reload();
        prune();
        render();
        updateClock();
        updateTray();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    void onPause() {
        handler.removeCallbacks(ticker);
    }

    /** Retira das listas as aplicações que já não existem (ex.: desinstaladas). */
    private void prune() {
        if (catalog.all().isEmpty()) return;
        for (String key : new String[]{Prefs.DESKTOP, Prefs.DOCK, Prefs.HIDDEN, Prefs.RECENT}) {
            List<String> list = prefs.list(key);
            List<String> kept = new ArrayList<>();
            for (String pkg : list) {
                if (catalog.find(pkg) != null) kept.add(pkg);
            }
            if (kept.size() != list.size()) prefs.putList(key, kept);
        }
    }

    /* ---------- Construção ---------- */

    private int dp(float value) {
        return Ui.dp(context, value);
    }

    private int screenWidth() {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    private static FrameLayout.LayoutParams frame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(width, height, gravity);
    }

    private LinearLayout buildArrangeBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(8), dp(8));
        bar.setBackground(Ui.box(Ui.SURFACE, dp(12), dp(1), Ui.STROKE));
        bar.setClickable(true);
        bar.setVisibility(View.GONE);

        TextView text = Ui.text(context, "Toca num ícone e depois noutro para trocar de lugar.", 13, Ui.TEXT, false);
        text.setMaxWidth(dp(260));
        bar.addView(text, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        TextView done = Ui.text(context, "Concluído", 13, Ui.ACCENT, true);
        done.setPadding(dp(12), dp(8), dp(12), dp(8));
        done.setBackground(Ui.ripple(context));
        done.setClickable(true);
        done.setOnClickListener(v -> setArranging(false));
        bar.addView(done);
        return bar;
    }

    private LinearLayout buildTaskbar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.TASKBAR);
        bar.setPadding(dp(6), 0, dp(8), 0);
        bar.setClickable(true);

        ImageView start = barIcon("ic_app", dp(13), v -> togglePanel(PANEL_APPS));
        barButtons.add(start);
        bar.addView(start, new LinearLayout.LayoutParams(prefs.barHeight(), MATCH_PARENT));

        // Pesquisa: aplicações, definições ou Google.
        LinearLayout search = new LinearLayout(context);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.box(Ui.FIELD, dp(18), 0, 0));
        search.setPadding(dp(14), 0, dp(4), 0);

        searchField = new EditText(context);
        searchField.setHint("Pesquisar");
        searchField.setHintTextColor(Ui.MUTED);
        searchField.setTextColor(Ui.TEXT);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        searchField.setSingleLine(true);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setPadding(0, 0, 0, 0);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSuggest();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            submitSearch();
            return true;
        });
        search.addView(searchField, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        ImageView go = new ImageView(context);
        go.setImageDrawable(Icons.feature(context, "ic_search"));
        go.setPadding(dp(8), dp(8), dp(8), dp(8));
        go.setBackground(Ui.ripple(context));
        go.setClickable(true);
        go.setOnClickListener(v -> submitSearch());
        search.addView(go, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(280), dp(36));
        searchParams.setMargins(dp(8), 0, dp(8), 0);
        bar.addView(search, searchParams);

        // Barra: fixadas e apps em segundo plano.
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        dockRow = new LinearLayout(context);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(dockRow, frame(WRAP_CONTENT, MATCH_PARENT, Gravity.CENTER_VERTICAL));
        bar.addView(scroll, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));

        // Bandeja: volume, notificações e relógio.
        volumeIcon = barIcon("ic_volume_up", dp(11), v -> {
            system.toggleMute();
            notificationPanel.refresh();
            updateTray();
        });
        barButtons.add(volumeIcon);
        bar.addView(volumeIcon, new LinearLayout.LayoutParams(prefs.barHeight(), MATCH_PARENT));

        FrameLayout tray = new FrameLayout(context);
        tray.setBackground(Ui.ripple(context));
        tray.setClickable(true);
        tray.setOnClickListener(v -> togglePanel(PANEL_NOTIF));
        ImageView bell = new ImageView(context);
        bell.setImageDrawable(Icons.feature(context, "ic_notifications"));
        bell.setPadding(dp(11), dp(11), dp(11), dp(11));
        tray.addView(bell, frame(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER));
        notifDot = new View(context);
        notifDot.setBackground(Ui.box(Ui.ACCENT, dp(4), 0, 0));
        notifDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dotParams = frame(dp(8), dp(8), Gravity.TOP | Gravity.END);
        dotParams.setMargins(0, dp(16), dp(16), 0);
        tray.addView(notifDot, dotParams);
        barButtons.add(tray);
        bar.addView(tray, new LinearLayout.LayoutParams(prefs.barHeight(), MATCH_PARENT));

        LinearLayout clock = new LinearLayout(context);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(6), 0, dp(4), 0);
        timeView = Ui.text(context, "--:--", 13, Ui.TEXT, true);
        dateView = Ui.text(context, "", 10, Ui.MUTED, false);
        clock.addView(timeView);
        clock.addView(dateView);
        bar.addView(clock, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
        return bar;
    }

    private ImageView barIcon(String icon, int padding, View.OnClickListener listener) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(Icons.feature(context, icon));
        view.setPadding(padding, padding, padding, padding);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setBackground(Ui.ripple(context));
        view.setClickable(true);
        view.setOnClickListener(listener);
        return view;
    }

    /* ---------- Layout ---------- */

    /** Posiciona os painéis acima da barra de tarefas. */
    private void layoutPanels() {
        int gap = dp(10);
        int bar = prefs.barHeight();
        int width = screenWidth();

        FrameLayout.LayoutParams apps = frame(Math.min(dp(400), width - dp(20)), MATCH_PARENT,
                Gravity.START | Gravity.TOP);
        apps.setMargins(gap, gap, 0, bar + gap);
        appsPanel.view().setLayoutParams(apps);

        FrameLayout.LayoutParams notif = frame(Math.min(dp(380), width - dp(20)), MATCH_PARENT,
                Gravity.END | Gravity.TOP);
        notif.setMargins(0, gap, gap, bar + gap);
        notificationPanel.view().setLayoutParams(notif);

        FrameLayout.LayoutParams settings = frame(Math.min(dp(460), width - dp(24)), MATCH_PARENT, Gravity.CENTER);
        settings.setMargins(0, dp(24), 0, bar + dp(24));
        settingsPanel.view().setLayoutParams(settings);

        FrameLayout.LayoutParams suggest = frame(Math.min(dp(420), width - dp(20)), WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        suggest.setMargins(0, 0, 0, bar + dp(8));
        suggestBox.setLayoutParams(suggest);
    }

    /** Posiciona as colunas do ambiente de trabalho, a dica e a barra do modo «Organizar». */
    private void layoutDesktop() {
        int bar = prefs.barHeight();

        FrameLayout.LayoutParams columns = frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.START);
        columns.setMargins(dp(12), arranging ? dp(72) : dp(16), 0, 0);
        desktopColumns.setLayoutParams(columns);

        FrameLayout.LayoutParams hint = frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER);
        hint.setMargins(dp(24), 0, dp(24), bar);
        deskHint.setLayoutParams(hint);

        FrameLayout.LayoutParams arrange = frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        arrange.setMargins(dp(12), dp(12), dp(12), 0);
        arrangeBar.setLayoutParams(arrange);
    }

    /** Aplica as preferências de tamanho (barra, ícones) e volta a desenhar. */
    void applyDisplaySize() {
        int bar = prefs.barHeight();
        taskbar.setLayoutParams(frame(MATCH_PARENT, bar, Gravity.BOTTOM));
        for (View button : barButtons) {
            button.setLayoutParams(new LinearLayout.LayoutParams(bar, MATCH_PARENT));
        }
        layoutDesktop();
        layoutPanels();
        render();
    }

    /* ---------- Desenho do ambiente e da barra ---------- */

    /** Redesenha ambiente, barra, perfil e os painéis abertos. */
    void render() {
        renderDesktop();
        renderDock();
        appsPanel.refresh();
        settingsPanel.refresh();
    }

    private void renderDesktop() {
        desktopColumns.removeAllViews();
        int icon = prefs.deskIcon();
        int width = Math.max(dp(92), icon + dp(52));
        int rowHeight = icon + dp(30);
        int rows = Math.max(1, (screenHeight() - prefs.barHeight() - dp(72)) / (rowHeight + dp(4)));

        LinearLayout column = null;
        int inColumn = 0;
        int shown = 0;
        for (String pkg : prefs.list(ZONE_DESKTOP)) {
            AppCatalog.App app = visibleApp(pkg);
            if (app == null) continue;
            if (column == null || inColumn == rows) {
                column = newColumn();
                inColumn = 0;
            }
            column.addView(tile(app, ZONE_DESKTOP, icon, width, true, () -> appMenu(app)));
            inColumn++;
            shown++;
        }
        if (arranging) {
            if (column == null || inColumn == rows) column = newColumn();
            column.addView(endSlot(ZONE_DESKTOP, icon, width));
        }
        deskHint.setVisibility(shown > 0 || arranging ? View.GONE : View.VISIBLE);
    }

    private LinearLayout newColumn() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(6), 0);
        desktopColumns.addView(column, params);
        return column;
    }

    private void renderDock() {
        dockRow.removeAllViews();
        int icon = prefs.barIcon();
        int width = icon + dp(16);
        for (String pkg : prefs.list(ZONE_DOCK)) {
            AppCatalog.App app = visibleApp(pkg);
            if (app == null) continue;
            dockRow.addView(tile(app, ZONE_DOCK, icon, width, false, () -> appMenu(app)));
        }
        if (arranging) {
            dockRow.addView(endSlot(ZONE_DOCK, icon, width));
            return;
        }
        List<AppCatalog.App> running = runningApps();
        if (running.isEmpty()) return;
        View divider = new View(context);
        divider.setBackgroundColor(Ui.STROKE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(28));
        dividerParams.setMargins(dp(6), 0, dp(6), 0);
        dockRow.addView(divider, dividerParams);
        for (final AppCatalog.App app : running) {
            dockRow.addView(tile(app, null, icon, width, false, () -> runningMenu(app)));
        }
    }

    /** Um ícone com nome opcional. Com `zone` definida, pode ser trocado no modo «Organizar». */
    private View tile(final AppCatalog.App app, final String zone, int iconPx, int widthPx,
                      boolean showLabel, final MenuFactory menu) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(3), dp(4), dp(3), dp(4));
        boolean selected = zone != null && app.packageName.equals(selectedPkg) && zone.equals(selectedZone);
        tile.setBackground(selected ? Ui.box(Ui.SELECTED, dp(10), 0, 0) : Ui.ripple(context));
        tile.setClickable(true);
        tile.setOnClickListener(v -> onTileTap(app, zone));
        tile.setOnLongClickListener(v -> {
            if (!arranging) showMenu(displayName(app), menu.create());
            return true;
        });

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(iconFor(app));
        tile.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));

        if (showLabel) {
            TextView label = Ui.text(context, displayName(app), 11.5f, Ui.TEXT, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(label, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        tile.setLayoutParams(new LinearLayout.LayoutParams(widthPx, WRAP_CONTENT));
        return tile;
    }

    /** Espaço vazio no fim de uma zona, para mover o item selecionado para lá (modo «Organizar»). */
    private View endSlot(final String zone, int iconPx, int widthPx) {
        ImageView slot = new ImageView(context);
        slot.setImageDrawable(Icons.feature(context, "ic_add"));
        slot.setPadding(dp(14), dp(14), dp(14), dp(14));
        slot.setBackground(Ui.box(Ui.HOVER, dp(10), dp(1), Ui.STROKE));
        slot.setClickable(true);
        slot.setOnClickListener(v -> moveSelectedToEnd(zone));
        slot.setLayoutParams(new LinearLayout.LayoutParams(widthPx, iconPx + dp(30)));
        return slot;
    }

    private void onTileTap(AppCatalog.App app, String zone) {
        if (arranging) {
            if (zone != null) arrangeTap(app.packageName, zone);
            return;
        }
        launch(app);
    }

    private AppCatalog.App visibleApp(String pkg) {
        AppCatalog.App app = catalog.find(pkg);
        return app != null && !prefs.isHidden(pkg) ? app : null;
    }

    /** Apps abertas pelo launcher que não estão já fixadas na barra (máximo de três). */
    List<AppCatalog.App> runningApps() {
        List<AppCatalog.App> out = new ArrayList<>();
        List<String> dock = prefs.list(ZONE_DOCK);
        for (String pkg : prefs.list(Prefs.RECENT)) {
            AppCatalog.App app = catalog.find(pkg);
            if (app != null && !prefs.isHidden(pkg) && !dock.contains(pkg)) out.add(app);
            if (out.size() == Prefs.RECENT_LIMIT) break;
        }
        return out;
    }

    private void updateClock() {
        Date now = new Date();
        Locale pt = new Locale("pt", "PT");
        timeView.setText(new SimpleDateFormat("HH:mm", pt).format(now));
        dateView.setText(new SimpleDateFormat("dd/MM/yyyy", pt).format(now));
    }

    private void updateTray() {
        volumeIcon.setImageDrawable(Icons.feature(context, Icons.volumeIcon(system.getVolume())));
        boolean pending = system.hasNotificationAccess()
                && !DesktopNotificationListener.snapshot().isEmpty();
        notifDot.setVisibility(pending ? View.VISIBLE : View.GONE);
    }

    /* ---------- Menus ---------- */

    /** Toque longo no espaço vazio do ambiente. */
    private boolean onDesktopLongPress() {
        if (arranging) return false;
        closePanels();
        showMenu(null, desktopMenu());
        return true;
    }

    /** Menu de uma aplicação, com o estado atual das fixações. */
    List<MenuItem> appMenu(final AppCatalog.App app) {
        final String pkg = app.packageName;
        boolean onDesktop = prefs.list(ZONE_DESKTOP).contains(pkg);
        boolean onDock = prefs.list(ZONE_DOCK).contains(pkg);

        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Renomear", () -> Dialogs.rename(this, app)));
        items.add(new MenuItem("Editar ícone", () -> Dialogs.iconPicker(this, app)));
        items.add(onDesktop
                ? new MenuItem("Remover do ambiente", () -> setPinned(ZONE_DESKTOP, pkg, false))
                : new MenuItem("Afixar no ambiente", () -> setPinned(ZONE_DESKTOP, pkg, true)));
        items.add(onDock
                ? new MenuItem("Remover da barra", () -> setPinned(ZONE_DOCK, pkg, false))
                : new MenuItem("Afixar na barra", () -> setPinned(ZONE_DOCK, pkg, true)));
        if (onDesktop || onDock) {
            items.add(new MenuItem("Organizar", () -> setArranging(true)));
        }
        items.add(new MenuItem("Ocultar", () -> setHidden(pkg, true)));
        items.add(new MenuItem("Desinstalar", () -> Dialogs.confirmUninstall(this, app)));
        items.add(new MenuItem("Informação", () -> system.appInfo(pkg)));
        return items;
    }

    private List<MenuItem> runningMenu(final AppCatalog.App app) {
        final String pkg = app.packageName;
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Fixar na barra", () -> setPinned(ZONE_DOCK, pkg, true)));
        items.add(new MenuItem("Fechar app", () -> closeRunningApp(pkg)));
        return items;
    }

    private List<MenuItem> desktopMenu() {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Organizar", () -> setArranging(true)));
        items.add(new MenuItem("Fechar todas as apps", this::closeAllRunning));
        items.add(new MenuItem("Configurações do launcher", () -> openSettings(SettingsPanel.MAIN)));
        return items;
    }

    /** Lista de opções num diálogo simples. */
    void showMenu(String title, List<MenuItem> items) {
        if (items.isEmpty()) return;
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = items.get(i).label;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        if (title != null) builder.setTitle(title);
        builder.setItems(labels, (dialog, which) -> items.get(which).action.run());
        builder.show();
    }

    /* ---------- Modo «Organizar»: tocar para trocar ---------- */

    void setArranging(boolean on) {
        arranging = on;
        clearSelection();
        closePanels();
        arrangeBar.setVisibility(on ? View.VISIBLE : View.GONE);
        layoutDesktop();
        render();
    }

    private void arrangeTap(String pkg, String zone) {
        if (selectedPkg == null) {
            selectedPkg = pkg;
            selectedZone = zone;
        } else if (selectedPkg.equals(pkg) && selectedZone.equals(zone)) {
            clearSelection();
        } else {
            swapSlots(selectedPkg, selectedZone, pkg, zone);
            clearSelection();
        }
        render();
    }

    /** Troca a posição de dois itens. Entre zonas diferentes, cada um fica no lugar do outro. */
    private void swapSlots(String a, String zoneA, String b, String zoneB) {
        List<String> listA = prefs.list(zoneA);
        if (zoneA.equals(zoneB)) {
            int i = listA.indexOf(a);
            int j = listA.indexOf(b);
            if (i < 0 || j < 0) return;
            Collections.swap(listA, i, j);
            prefs.putList(zoneA, listA);
            return;
        }
        List<String> listB = prefs.list(zoneB);
        if (listA.contains(b) || listB.contains(a)) {
            // A app já está fixada nas duas zonas: evita duplicados e move só o selecionado.
            listA.remove(a);
            listB.remove(a);
            int target = listB.indexOf(b);
            if (target < 0) listB.add(a);
            else listB.add(target, a);
            prefs.putList(zoneA, listA);
            prefs.putList(zoneB, listB);
            return;
        }
        int i = listA.indexOf(a);
        int j = listB.indexOf(b);
        if (i < 0 || j < 0) return;
        listA.set(i, b);
        listB.set(j, a);
        prefs.putList(zoneA, listA);
        prefs.putList(zoneB, listB);
    }

    private void moveSelectedToEnd(String zone) {
        if (selectedPkg == null) return;
        List<String> from = prefs.list(selectedZone);
        from.remove(selectedPkg);
        prefs.putList(selectedZone, from);
        List<String> to = prefs.list(zone);
        to.remove(selectedPkg);
        to.add(selectedPkg);
        prefs.putList(zone, to);
        clearSelection();
        render();
    }

    private void clearSelection() {
        selectedPkg = null;
        selectedZone = null;
    }

    /* ---------- Ações sobre aplicações e preferências ---------- */

    /** Abre a aplicação, regista-a como recente e fecha os painéis. */
    void launch(AppCatalog.App app) {
        if (prefs.isHidden(app.packageName)) return;
        rememberRecent(app.packageName);
        closePanels();
        system.openApp(app.packageName);
    }

    private void rememberRecent(String pkg) {
        List<String> recent = prefs.list(Prefs.RECENT);
        recent.remove(pkg);
        recent.add(0, pkg);
        while (recent.size() > Prefs.RECENT_LIMIT) recent.remove(recent.size() - 1);
        prefs.putList(Prefs.RECENT, recent);
    }

    private void closeRunningApp(String pkg) {
        system.killApp(pkg);
        List<String> recent = prefs.list(Prefs.RECENT);
        recent.remove(pkg);
        prefs.putList(Prefs.RECENT, recent);
        render();
    }

    private void closeAllRunning() {
        for (String pkg : prefs.list(Prefs.RECENT)) system.killApp(pkg);
        prefs.putList(Prefs.RECENT, new ArrayList<String>());
        render();
    }

    void setPinned(String zone, String pkg, boolean on) {
        List<String> list = prefs.list(zone);
        if (on && !list.contains(pkg)) list.add(pkg);
        if (!on) list.remove(pkg);
        prefs.putList(zone, list);
        render();
    }

    /** Oculta ou mostra uma app em todo o launcher. As fixações ficam guardadas. */
    void setHidden(String pkg, boolean on) {
        List<String> list = prefs.list(Prefs.HIDDEN);
        if (on && !list.contains(pkg)) list.add(pkg);
        if (!on) list.remove(pkg);
        prefs.putList(Prefs.HIDDEN, list);
        render();
    }

    void setCustomName(String pkg, String value) {
        String clean = value == null || value.isEmpty() ? null : truncate(value, Prefs.NAME_MAX);
        prefs.setCustomName(pkg, clean);
        render();
    }

    void setCustomIcon(String pkg, String icon) {
        prefs.setCustomIcon(pkg, icon);
        render();
    }

    void setProfileName(String value) {
        String clean = value == null ? "" : value.trim();
        prefs.setProfileName(clean.isEmpty() ? null : truncate(clean, Prefs.PROFILE_NAME_MAX));
        render();
    }

    void setAvatar(int index) {
        prefs.setAvatar(index);
        render();
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    /* ---------- Painéis ---------- */

    void togglePanel(int panel) {
        if (currentPanel == panel) closePanels();
        else openPanel(panel);
    }

    void openPanel(int panel) {
        closePanels();
        currentPanel = panel;
        if (panel == PANEL_APPS) {
            appsPanel.open();
        } else if (panel == PANEL_NOTIF) {
            notificationPanel.open();
        } else if (panel == PANEL_SETTINGS) {
            settingsPanel.open(SettingsPanel.MAIN);
        }
    }

    void openSettings(String screen) {
        openPanel(PANEL_SETTINGS);
        settingsPanel.show(screen);
    }

    void closePanels() {
        appsPanel.close();
        settingsPanel.close();
        notificationPanel.close();
        currentPanel = PANEL_NONE;
        hideSuggest();
    }

    /** Botão Voltar: fecha o que estiver aberto, ou recua uma vista das configurações. */
    boolean handleBack() {
        if (suggestBox.getVisibility() == View.VISIBLE) {
            hideSuggest();
            return true;
        }
        if (currentPanel == PANEL_SETTINGS && settingsPanel.back()) return true;
        if (currentPanel != PANEL_NONE) {
            closePanels();
            return true;
        }
        if (arranging) {
            setArranging(false);
            return true;
        }
        return false;
    }

    /* ---------- Pesquisa da barra: aplicações, definições ou Google ---------- */

    private void renderSuggest() {
        String raw = searchField.getText().toString().trim();
        suggestBox.removeAllViews();
        suggestions = new ArrayList<>();
        if (raw.isEmpty()) {
            suggestBox.setVisibility(View.GONE);
            return;
        }
        String query = AppCatalog.normalize(raw);

        int apps = 0;
        for (AppCatalog.App app : visibleApps()) {
            String name = displayName(app);
            if (!AppCatalog.normalize(name).contains(query)) continue;
            suggestions.add(new SearchItem(name, "Aplicação", iconFor(app), () -> launch(app)));
            apps++;
            if (apps == SEARCH_APP_LIMIT) break;
        }
        for (SettingOption option : settingOptions()) {
            if (option.matches(query)) {
                suggestions.add(new SearchItem(option.label, "Definição",
                        Icons.feature(context, option.icon), option.action));
            }
        }
        // Só se não houver nenhuma opção é que se oferece a pesquisa na Web.
        if (suggestions.isEmpty()) {
            suggestions.add(new SearchItem("Pesquisar «" + raw + "» no Google", "",
                    Icons.feature(context, "ic_search"), () -> {
                        hideSuggest();
                        system.googleSearch(raw);
                    }));
        }
        for (SearchItem item : suggestions) suggestBox.addView(suggestRow(item));
        suggestBox.setVisibility(View.VISIBLE);
    }

    private View suggestRow(final SearchItem item) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(Ui.ripple(context));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            hideSuggest();
            searchField.setText("");
            item.action.run();
        });

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(item.icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView label = Ui.text(context, item.label, 14, Ui.TEXT, true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(label);
        if (!item.sub.isEmpty()) texts.addView(Ui.text(context, item.sub, 11, Ui.MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        return row;
    }

    private void hideSuggest() {
        suggestBox.setVisibility(View.GONE);
        suggestBox.removeAllViews();
        suggestions = new ArrayList<>();
    }

    /** Enter na pesquisa executa a primeira sugestão. */
    private void submitSearch() {
        renderSuggest();
        if (suggestions.isEmpty()) return;
        Runnable first = suggestions.get(0).action;
        hideSuggest();
        searchField.setText("");
        first.run();
    }

    private List<SettingOption> settingOptions() {
        List<SettingOption> options = new ArrayList<>();
        options.add(new SettingOption("Definições do Android", "ic_settings",
                new String[]{"definicoes", "configuracoes", "settings", "android"}, system::androidSettings));
        options.add(new SettingOption("Aplicações", "ic_app",
                new String[]{"aplicacoes", "apps", "aplicativos"}, () -> openPanel(PANEL_APPS)));
        options.add(new SettingOption("Configurações do launcher", "ic_settings",
                new String[]{"launcher", "configuracoes do launcher", "preferencias"},
                () -> openSettings(SettingsPanel.MAIN)));
        options.add(new SettingOption("Papel de parede", "ic_wallpaper",
                new String[]{"papel", "parede", "wallpaper", "fundo"},
                () -> openSettings(SettingsPanel.ASPECT)));
        options.add(new SettingOption("Wi-Fi", "ic_wifi",
                new String[]{"wifi", "wi-fi", "rede"}, system::wifiSettings));
        options.add(new SettingOption("Bluetooth", "ic_bluetooth",
                new String[]{"bluetooth"}, system::bluetoothSettings));
        options.add(new SettingOption("Localização", "ic_location",
                new String[]{"localizacao", "gps", "location"}, system::locationSettings));
        options.add(new SettingOption("Notificações", "ic_notifications",
                new String[]{"notificacoes", "notificacao"}, () -> openPanel(PANEL_NOTIF)));
        return options;
    }

    /* ---------- Papel de parede ---------- */

    /** Aplica um predefinido (0 a 4) ou a imagem da galeria (WALLPAPER_CUSTOM). */
    void applyWallpaper(int index) {
        int value = index;
        if (value == Prefs.WALLPAPER_CUSTOM && !customWallpaperFile().isFile()) value = 0;
        if (value != Prefs.WALLPAPER_CUSTOM && (value < 0 || value >= WALLPAPER_COUNT)) value = 0;
        prefs.setWallpaper(value);
        wallpaperView.setImageBitmap(loadWallpaper(value, 1));
        settingsPanel.refresh();
    }

    boolean hasCustomWallpaper() {
        return customWallpaperFile().isFile();
    }

    /** Miniatura reduzida de um predefinido ou da imagem da galeria. */
    Bitmap wallpaperThumbnail(int index) {
        return loadWallpaper(index, 4);
    }

    private Bitmap loadWallpaper(int index, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        if (index == Prefs.WALLPAPER_CUSTOM) {
            return BitmapFactory.decodeFile(customWallpaperFile().getAbsolutePath(), options);
        }
        return BitmapFactory.decodeResource(context.getResources(), WALLPAPERS[index], options);
    }

    private File customWallpaperFile() {
        return new File(context.getFilesDir(), CUSTOM_WALLPAPER_FILE);
    }

    void pickWallpaper() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            activity.startActivityForResult(Intent.createChooser(pick, "Escolher papel de parede"),
                    REQUEST_PICK_WALLPAPER);
        } catch (ActivityNotFoundException ignored) {
            // Sem aplicação de galeria não há imagem para escolher.
        }
    }

    void onWallpaperPicked(Uri uri) {
        if (uri != null && saveCustomWallpaper(uri)) {
            applyWallpaper(Prefs.WALLPAPER_CUSTOM);
        } else {
            Dialogs.info(this, "Papel de parede", "Não foi possível usar essa imagem. Experimenta outra.");
        }
    }

    /** Reduz a imagem para no máximo 1600 px e guarda-a no armazenamento interno. */
    private boolean saveCustomWallpaper(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, WALLPAPER_MAX_PX);
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return false;
            try (FileOutputStream out = new FileOutputStream(customWallpaperFile())) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            } finally {
                bitmap.recycle();
            }
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static int sampleSize(int width, int height, int maxPx) {
        int size = 1;
        while (width / (size * 2) >= maxPx && height / (size * 2) >= maxPx) size *= 2;
        return size;
    }

    /* ---------- Tipos internos ---------- */

    /** Item de um menu: texto e ação. */
    static final class MenuItem {
        final String label;
        final Runnable action;

        MenuItem(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    /** Cria o menu no momento do toque longo, quando o estado já pode ter mudado. */
    interface MenuFactory {
        List<MenuItem> create();
    }

    /** Sugestão da pesquisa. */
    private static final class SearchItem {
        final String label;
        final String sub;
        final Drawable icon;
        final Runnable action;

        SearchItem(String label, String sub, Drawable icon, Runnable action) {
            this.label = label;
            this.sub = sub;
            this.icon = icon;
            this.action = action;
        }
    }

    /** Definição que a pesquisa também encontra. As palavras-chave estão sem acentos. */
    private static final class SettingOption {
        final String label;
        final String icon;
        final String[] keys;
        final Runnable action;

        SettingOption(String label, String icon, String[] keys, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.keys = keys;
            this.action = action;
        }

        boolean matches(String query) {
            if (AppCatalog.normalize(label).contains(query)) return true;
            for (String key : keys) {
                if (key.contains(query)) return true;
            }
            return false;
        }
    }
}
