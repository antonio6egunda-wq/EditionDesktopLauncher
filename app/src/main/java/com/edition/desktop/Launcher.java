package com.edition.desktop;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Ecrã principal do launcher: ambiente de trabalho, barra de tarefas e ligação aos painéis.
 * Toda a interface é construída em Java, sem WebView. Também guarda o estado do foco do
 * teclado e do menu de contexto.
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
    private final ContextMenu contextMenu;
    private final Map<String, View> tileViews = new HashMap<>();

    private LinearLayout taskbar;
    private LinearLayout dockRow;
    private EditText searchField;
    private ImageView startButton;
    private ImageView volumeIcon;
    private FrameLayout trayButton;
    private View notifDot;
    private TextView timeView;
    private TextView dateView;

    private int currentPanel = PANEL_NONE;
    private boolean arranging;
    private String selectedPkg;
    private String selectedZone;
    private String focusZone;
    private String focusPkg;
    private int suggestIndex;
    private float touchX;
    private float touchY;
    private List<SearchItem> suggestions = new ArrayList<>();

    /** Atualiza relógio, bandeja e cursores de seis em seis segundos, enquanto o launcher estiver ativo. */
    private final Runnable ticker = new Runnable() {
        /** Uma passagem do ciclo: atualiza relógio e bandeja e agenda a próxima. */
        @Override
        public void run() {
            updateClock();
            updateTray();
            if (currentPanel == PANEL_NOTIF) notificationPanel.refresh();
            handler.postDelayed(this, TICK_MS);
        }
    };

    /** Constrói todo o ecrã: fundo, ambiente, barra, painéis e menu de contexto (por cima de tudo). */
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

        // Espaço vazio do ambiente: toque fecha os painéis, toque longo ou clique direito abre o menu.
        desktopArea = new FrameLayout(activity);
        desktopArea.setOnClickListener(v -> {
            closePanels();
            if (focusZone != null) setFocus(null, null);
        });
        desktopArea.setOnLongClickListener(v -> onDesktopLongPress());
        desktopArea.setOnContextClickListener(v -> {
            onDesktopLongPress();
            return true;
        });
        root.addView(desktopArea, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        desktopColumns = new LinearLayout(activity);
        desktopColumns.setOrientation(LinearLayout.HORIZONTAL);
        desktopArea.addView(desktopColumns, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.START));

        deskHint = Ui.text(activity,
                "Mantém premido o ambiente para abrir o menu, ou uma aplicação para a afixar aqui.\n"
                        + "Prime Ctrl+/ para ver os atalhos de teclado.",
                13, Ui.MUTED, false);
        deskHint.setGravity(Gravity.CENTER);
        deskHint.setLineSpacing(dp(3), 1f);
        deskHint.setMaxWidth(dp(360));
        desktopArea.addView(deskHint, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));

        arrangeBar = buildArrangeBar();
        desktopArea.addView(arrangeBar, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        taskbar = buildTaskbar();
        root.addView(taskbar, frame(MATCH_PARENT, prefs.barHeight(), Gravity.BOTTOM));

        suggestBox = new LinearLayout(activity);
        suggestBox.setOrientation(LinearLayout.VERTICAL);
        suggestBox.setPadding(dp(6), dp(6), dp(6), dp(6));
        suggestBox.setBackground(Ui.box(Ui.MENU, dp(14), dp(1), Ui.STROKE));
        suggestBox.setElevation(dp(12));
        suggestBox.setClickable(true);
        suggestBox.setVisibility(View.GONE);
        root.addView(suggestBox, frame(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        appsPanel = new AppsPanel(this);
        settingsPanel = new SettingsPanel(this);
        notificationPanel = new NotificationPanel(this);
        root.addView(appsPanel.view());
        root.addView(settingsPanel.view());
        root.addView(notificationPanel.view());

        // Último para ficar por cima de tudo.
        contextMenu = new ContextMenu(this, root);

        layoutPanels();
        layoutDesktop();
        applyWallpaper(prefs.wallpaper());
        refreshPointers();
    }

    /** Vista raiz do launcher, que é o conteúdo da atividade. */
    FrameLayout root() {
        return root;
    }

    /* ---------- Acesso para os painéis e diálogos ---------- */

    /** Atividade principal (necessária para diálogos e intenções). */
    MainActivity activity() {
        return activity;
    }

    /** Preferências guardadas no dispositivo. */
    Prefs prefs() {
        return prefs;
    }

    /** Lista de aplicações instaladas. */
    AppCatalog catalog() {
        return catalog;
    }

    /** Ações do sistema (abrir apps, Wi-Fi, volume…). */
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

    /** Nome a mostrar para uma app: o nome personalizado, se existir, senão o original. */
    String displayName(AppCatalog.App app) {
        String custom = prefs.customName(app.packageName);
        return custom != null ? custom : app.label;
    }

    /** Ícone a mostrar para uma app: o personalizado, senão o do sistema ou da marca. */
    Drawable iconFor(AppCatalog.App app) {
        String custom = prefs.customIcon(app.packageName);
        if (custom != null) {
            Drawable icon = Icons.feature(context, custom);
            if (icon != null) return icon;
        }
        return Icons.appIcon(context, catalog, app);
    }

    /* ---------- Ciclo de vida ---------- */

    /** Chamado quando a atividade fica visível: recarrega apps, redesenha e inicia o relógio. */
    void onResume() {
        catalog.reload();
        prune();
        render();
        updateClock();
        updateTray();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    /** Chamado quando a atividade deixa de estar visível: para o relógio. */
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

    /** Atalho para converter dp em píxeis. */
    private int dp(float value) {
        return Ui.dp(context, value);
    }

    /** Largura do ecrã em píxeis. */
    private int screenWidth() {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /** Altura do ecrã em píxeis. */
    private int screenHeight() {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /** Cria parâmetros de layout para um FrameLayout, com tamanho e alinhamento. */
    private static FrameLayout.LayoutParams frame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(width, height, gravity);
    }

    /** Barra flutuante do modo «Organizar», com instruções e o botão «Concluído». */
    private LinearLayout buildArrangeBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(8), dp(8));
        bar.setBackground(Ui.box(Ui.MENU, dp(14), dp(1), Ui.STROKE));
        bar.setElevation(dp(10));
        bar.setClickable(true);
        bar.setVisibility(View.GONE);

        TextView text = Ui.text(context, "Toca num ícone e depois noutro para trocar de lugar.", 13, Ui.TEXT, false);
        text.setMaxWidth(dp(280));
        bar.addView(text, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        TextView done = Ui.button(context, "Concluído", true, () -> setArranging(false));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        doneParams.leftMargin = dp(12);
        bar.addView(done, doneParams);
        return bar;
    }

    /**
     * Barra de tarefas: botão Aplicações, pesquisa, barra de apps fixadas e em execução,
     * bandeja (volume, notificações) e relógio. Cada botão tem uma dica ao passar o cursor.
     */
    private LinearLayout buildTaskbar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.TASKBAR);
        bar.setPadding(dp(6), 0, dp(8), 0);
        bar.setClickable(true);

        startButton = barIcon("ic_app", dp(13), v -> togglePanel(PANEL_APPS));
        Ui.tooltip(startButton, "Aplicações (Win)");
        barButtons.add(startButton);
        bar.addView(startButton, new LinearLayout.LayoutParams(prefs.barHeight(), MATCH_PARENT));

        // Pesquisa: aplicações, definições ou Google.
        LinearLayout search = new LinearLayout(context);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.box(Ui.FIELD, dp(18), 0, 0));
        search.setPadding(dp(14), 0, dp(4), 0);

        searchField = Ui.field(context, "Pesquisar  (Ctrl+F)");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.addTextChangedListener(new TextWatcher() {
            /** Não faz nada antes da alteração. */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            /** Recalcula as sugestões a cada tecla. */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSuggest();
            }

            /** Não faz nada depois da alteração. */
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
        Ui.pointer(go, android.view.PointerIcon.TYPE_HAND);
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
        Ui.tooltip(volumeIcon, "Volume (clique para silenciar)");
        barButtons.add(volumeIcon);
        bar.addView(volumeIcon, new LinearLayout.LayoutParams(prefs.barHeight(), MATCH_PARENT));

        trayButton = new FrameLayout(context);
        trayButton.setBackground(Ui.ripple(context));
        trayButton.setClickable(true);
        trayButton.setOnClickListener(v -> togglePanel(PANEL_NOTIF));
        Ui.tooltip(trayButton, "Centro de ações (Win+A)");
        ImageView bell = new ImageView(context);
        bell.setImageDrawable(Icons.feature(context, "ic_notifications"));
        bell.setPadding(dp(11), dp(11), dp(11), dp(11));
        trayButton.addView(bell, frame(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER));
        notifDot = new View(context);
        notifDot.setBackground(Ui.box(Ui.ACCENT, dp(4), 0, 0));
        notifDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dotParams = frame(dp(8), dp(8), Gravity.TOP | Gravity.END);
        dotParams.setMargins(0, dp(16), dp(16), 0);
        trayButton.addView(notifDot, dotParams);
        barButtons.add(trayButton);
        bar.addView(trayButton, new LinearLayout.LayoutParams(prefs.barHeight(), MATCH_PARENT));

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

    /** Botão quadrado da barra, só com ícone, que reage ao rato e ao toque. */
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
        renderActiveButtons();
        appsPanel.refresh();
        settingsPanel.refresh();
        refreshPointers();
    }

    /**
     * Aplica o cursor a toda a interface: mão sobre o que se pode clicar, seta no resto.
     * Chamado sempre que a árvore de vistas muda, para o cursor não cair no círculo do sistema.
     */
    void refreshPointers() {
        Ui.pointers(root);
    }

    /** Desenha as apps afixadas no ambiente, em colunas, com o nome por baixo de cada ícone. */
    private void renderDesktop() {
        desktopColumns.removeAllViews();
        int icon = prefs.deskIcon();
        int width = Math.max(dp(92), icon + dp(52));
        int rows = rowsPerColumn();

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

    /** Número de ícones que cabem numa coluna do ambiente, conforme a altura do ecrã. */
    private int rowsPerColumn() {
        int icon = prefs.deskIcon();
        int rowHeight = icon + dp(30);
        return Math.max(1, (screenHeight() - prefs.barHeight() - dp(72)) / (rowHeight + dp(4)));
    }

    /** Cria uma nova coluna vertical no ambiente de trabalho. */
    private LinearLayout newColumn() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(6), 0);
        desktopColumns.addView(column, params);
        return column;
    }

    /**
     * Desenha a barra: apps fixadas, depois uma linha separadora e as apps em segundo plano.
     * No modo «Organizar» acrescenta um espaço vazio para mover o item selecionado para o fim.
     */
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

    /** Atualiza o destaque dos botões da barra conforme o painel aberto (Aplicações, Notificações). */
    private void renderActiveButtons() {
        setActive(startButton, currentPanel == PANEL_APPS);
        setActive(trayButton, currentPanel == PANEL_NOTIF);
    }

    /** Pinta o fundo de um botão da barra como ativo (com cor) ou normal (transparente). */
    private void setActive(View view, boolean on) {
        if (view == null) return;
        view.setBackground(on ? Ui.box(Ui.SELECTED_SOFT, dp(10), 0, 0) : Ui.ripple(context));
    }

    /**
     * Um ícone de app, com nome opcional. Com `zone` definida pode ser trocado no modo
     * «Organizar». Destaca-se se for a seleção do modo Organizar ou o foco do teclado.
     * Toque abre a app; toque longo ou clique direito abre o menu.
     */
    private View tile(final AppCatalog.App app, final String zone, int iconPx, int widthPx,
                      boolean showLabel, final MenuFactory menu) {
        final LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(3), dp(4), dp(3), dp(4));
        boolean selected = zone != null && app.packageName.equals(selectedPkg) && zone.equals(selectedZone);
        boolean focused = zone != null && app.packageName.equals(focusPkg) && zone.equals(focusZone);
        if (selected) {
            tile.setBackground(Ui.box(Ui.SELECTED, dp(10), 0, 0));
        } else if (focused) {
            tile.setBackground(Ui.focusBox(context));
        } else {
            tile.setBackground(Ui.ripple(context));
        }
        tile.setClickable(true);
        tile.setOnClickListener(v -> onTileTap(app, zone));
        tile.setOnLongClickListener(v -> {
            if (!arranging) showMenu(displayName(app), menu.create(), tile);
            return true;
        });
        tile.setOnContextClickListener(v -> {
            if (!arranging) showMenu(displayName(app), menu.create(), tile);
            return true;
        });
        if (zone != null) tileViews.put(zone + "|" + app.packageName, tile);

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(iconFor(app));
        tile.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));

        if (showLabel) {
            TextView label = Ui.text(context, displayName(app), 11.5f, Ui.TEXT, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(label, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        } else {
            Ui.tooltip(tile, displayName(app));
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

    /** Toque num ícone: no modo Organizar troca de lugar; fora dele, abre a app. */
    private void onTileTap(AppCatalog.App app, String zone) {
        if (arranging) {
            if (zone != null) arrangeTap(app.packageName, zone);
            return;
        }
        launch(app);
    }

    /** Devolve a app se ainda existir e não estiver oculta; senão devolve null. */
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

    /** Atualiza a hora e a data (formato português de Portugal). */
    private void updateClock() {
        Date now = new Date();
        Locale pt = new Locale("pt", "PT");
        timeView.setText(new SimpleDateFormat("HH:mm", pt).format(now));
        dateView.setText(new SimpleDateFormat("dd/MM/yyyy", pt).format(now));
    }

    /** Atualiza o ícone de volume e o ponto de notificação pendente. */
    private void updateTray() {
        volumeIcon.setImageDrawable(Icons.feature(context, Icons.volumeIcon(system.getVolume())));
        boolean pending = system.hasNotificationAccess()
                && !DesktopNotificationListener.snapshot().isEmpty();
        notifDot.setVisibility(pending ? View.VISIBLE : View.GONE);
        refreshPointers();
    }

    /* ---------- Entrada: toques e rato ---------- */

    /**
     * Guarda a posição do último toque ou clique (inclusive do rato). Assim o menu do
     * ambiente abre onde o utilizador clicou com o botão direito ou fez toque longo.
     */
    void noteTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchX = event.getX();
            touchY = event.getY();
        }
    }

    /* ---------- Menus ---------- */

    /** Toque longo ou clique direito no espaço vazio do ambiente: abre o menu do ambiente. */
    private boolean onDesktopLongPress() {
        if (arranging) return false;
        closePanels();
        int x = Math.round(touchX);
        int y = Math.round(touchY);
        showMenu(null, desktopMenu(), new Rect(x, y, x, y));
        return true;
    }

    /** Menu de uma aplicação no ambiente ou na barra, com o estado atual das fixações e atalhos. */
    List<MenuItem> appMenu(final AppCatalog.App app) {
        final String pkg = app.packageName;
        boolean onDesktop = prefs.list(ZONE_DESKTOP).contains(pkg);
        boolean onDock = prefs.list(ZONE_DOCK).contains(pkg);

        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Renomear", "ic_edit", "F2", false, () -> Dialogs.rename(this, app)));
        items.add(new MenuItem("Editar ícone", "ic_category", null, false, () -> Dialogs.iconPicker(this, app)));
        items.add(onDesktop
                ? new MenuItem("Remover do ambiente", "ic_close", null, false,
                () -> setPinned(ZONE_DESKTOP, pkg, false))
                : new MenuItem("Afixar no ambiente", "ic_pin", null, false,
                () -> setPinned(ZONE_DESKTOP, pkg, true)));
        items.add(onDock
                ? new MenuItem("Remover da barra", "ic_close", null, false,
                () -> setPinned(ZONE_DOCK, pkg, false))
                : new MenuItem("Afixar na barra", "ic_pin", null, false,
                () -> setPinned(ZONE_DOCK, pkg, true)));
        if (onDesktop || onDock) {
            items.add(new MenuItem("Organizar", "ic_display_settings", "Ctrl+Shift+O", false,
                    () -> setArranging(true)));
        }
        items.add(new MenuItem("Ocultar", "ic_visibility_off", null, true, () -> setHidden(pkg, true)));
        items.add(new MenuItem("Desinstalar", "ic_remove", null, true,
                () -> Dialogs.confirmUninstall(this, app)));
        items.add(new MenuItem("Informação", "ic_help", null, false, () -> system.appInfo(pkg)));
        return items;
    }

    /** Menu de uma app em segundo plano (na barra, à direita das fixadas). */
    private List<MenuItem> runningMenu(final AppCatalog.App app) {
        final String pkg = app.packageName;
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Fixar na barra", "ic_pin", null, false,
                () -> setPinned(ZONE_DOCK, pkg, true)));
        items.add(new MenuItem("Fechar app", "ic_power_settings", null, true, () -> closeRunningApp(pkg)));
        return items;
    }

    /** Menu do ambiente de trabalho (clique direito ou toque longo no espaço vazio). */
    private List<MenuItem> desktopMenu() {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Organizar", "ic_display_settings", "Ctrl+Shift+O", false,
                () -> setArranging(true)));
        items.add(new MenuItem("Mostrar atalhos de teclado", "ic_keyboard", "F1", false, this::showHelp));
        items.add(new MenuItem("Fechar todas as apps", "ic_power_settings", null, true, this::closeAllRunning));
        items.add(new MenuItem("Configurações do launcher", "ic_settings", "Ctrl+,", false,
                () -> openSettings(SettingsPanel.MAIN)));
        return items;
    }

    /** Abre o menu de contexto com um título, centrado no meio do ecrã (uso raro). */
    void showMenu(String title, List<MenuItem> items) {
        int x = screenWidth() / 2;
        int y = screenHeight() / 2;
        showMenu(title, items, new Rect(x, y, x, y));
    }

    /** Abre o menu de contexto junto a uma vista (um ícone, por exemplo). */
    void showMenu(String title, List<MenuItem> items, View anchor) {
        showMenu(title, items, rectOf(anchor));
    }

    /** Abre o menu de contexto junto a um retângulo, em coordenadas do launcher. */
    void showMenu(String title, List<MenuItem> items, Rect anchor) {
        if (items == null || items.isEmpty()) return;
        contextMenu.show(title, items, anchor);
    }

    /** Retângulo de uma vista em coordenadas do launcher (usado para posicionar o menu). */
    private Rect rectOf(View view) {
        int[] viewAt = new int[2];
        int[] rootAt = new int[2];
        view.getLocationInWindow(viewAt);
        root.getLocationInWindow(rootAt);
        int left = viewAt[0] - rootAt[0];
        int top = viewAt[1] - rootAt[1];
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }

    /** Indica se algum menu de contexto está aberto. */
    boolean isMenuOpen() {
        return contextMenu.isOpen();
    }

    /** Fecha o menu de contexto, se estiver aberto. */
    void closeMenu() {
        contextMenu.close();
    }

    /** Move a seleção do menu (teclado). */
    boolean menuMove(int delta) {
        return contextMenu.moveSelection(delta);
    }

    /** Executa a opção escolhida no menu (teclado). */
    boolean menuActivate() {
        return contextMenu.activateSelected();
    }

    /* ---------- Modo «Organizar»: tocar para trocar ---------- */

    /** Liga ou desliga o modo «Organizar» e fecha os painéis e a seleção anterior. */
    void setArranging(boolean on) {
        arranging = on;
        clearSelection();
        closePanels();
        arrangeBar.setVisibility(on ? View.VISIBLE : View.GONE);
        layoutDesktop();
        render();
    }

    /** Alterna o modo «Organizar» (atalho Ctrl+Shift+O). */
    void toggleArranging() {
        setArranging(!arranging);
    }

    /** Primeiro toque seleciona; o segundo, noutra app, troca os dois de lugar. */
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

    /** Move o item selecionado para o fim da zona indicada. */
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

    /** Limpa a seleção do modo «Organizar». */
    private void clearSelection() {
        selectedPkg = null;
        selectedZone = null;
    }

    /* ---------- Foco do teclado nos ícones ---------- */

    /** Um ícone que pode receber o foco: a zona (ambiente ou barra) e o nome do pacote. */
    private static final class Focus {
        final String zone;
        final String pkg;

        /** Cria um foco para a zona e o pacote indicados. */
        Focus(String zone, String pkg) {
            this.zone = zone;
            this.pkg = pkg;
        }
    }

    /** Lista, pela ordem visual, todos os ícones que podem receber o foco (primeiro o ambiente). */
    private List<Focus> focusOrder() {
        List<Focus> out = new ArrayList<>();
        for (String pkg : prefs.list(ZONE_DESKTOP)) {
            if (visibleApp(pkg) != null) out.add(new Focus(ZONE_DESKTOP, pkg));
        }
        for (String pkg : prefs.list(ZONE_DOCK)) {
            if (visibleApp(pkg) != null) out.add(new Focus(ZONE_DOCK, pkg));
        }
        return out;
    }

    /** Posição do foco atual na lista de ícones, ou -1 se não houver foco. */
    private int focusIndex(List<Focus> order) {
        for (int i = 0; i < order.size(); i++) {
            Focus f = order.get(i);
            if (f.zone.equals(focusZone) && f.pkg.equals(focusPkg)) return i;
        }
        return -1;
    }

    /** Define o ícone com foco (null, null remove o foco) e redesenha para mostrar o contorno. */
    private void setFocus(String zone, String pkg) {
        focusZone = zone;
        focusPkg = pkg;
        render();
    }

    /** Devolve o ícone com foco, ou null se não houver ou se já não existir. */
    private Focus currentFocus() {
        List<Focus> order = focusOrder();
        int i = focusIndex(order);
        return i < 0 ? null : order.get(i);
    }

    /**
     * Move o foco com as setas. No ambiente, cima e baixo andam dentro da coluna e esquerda
     * e direita trocam de coluna; na barra, só esquerda e direita. Devolve true (tecla consumida).
     */
    boolean moveFocus(int dx, int dy) {
        if (currentPanel != PANEL_NONE) return false;
        List<Focus> order = focusOrder();
        if (order.isEmpty()) return false;
        int i = focusIndex(order);
        if (i < 0) {
            setFocus(order.get(0).zone, order.get(0).pkg);
            return true;
        }
        int deskCount = 0;
        for (Focus f : order) {
            if (ZONE_DESKTOP.equals(f.zone)) deskCount++;
        }
        int target = -1;
        if (ZONE_DESKTOP.equals(order.get(i).zone)) {
            int rows = rowsPerColumn();
            if (dy != 0) {
                int t = i + dy;
                if (t >= 0 && t < deskCount && t / rows == i / rows) target = t;
            } else if (dx != 0) {
                int t = i + dx * rows;
                if (t >= 0 && t < deskCount) target = t;
            }
        } else if (dx != 0) {
            int t = i + dx;
            if (t >= deskCount && t < order.size()) target = t;
        }
        if (target >= 0) setFocus(order.get(target).zone, order.get(target).pkg);
        return true;
    }

    /** Passa o foco para o ícone seguinte (step 1) ou anterior (step -1), como o Tab. */
    boolean cycleFocus(int step) {
        if (currentPanel != PANEL_NONE) return false;
        List<Focus> order = focusOrder();
        if (order.isEmpty()) return false;
        int i = focusIndex(order);
        int next = i < 0 ? (step > 0 ? 0 : order.size() - 1) : (i + step + order.size()) % order.size();
        setFocus(order.get(next).zone, order.get(next).pkg);
        return true;
    }

    /** Abre a app que tem o foco (Enter). Devolve false se não houver foco. */
    boolean activateFocused() {
        Focus f = currentFocus();
        if (f == null) return false;
        AppCatalog.App app = catalog.find(f.pkg);
        if (app == null) return false;
        launch(app);
        return true;
    }

    /** Abre o menu do ícone com foco, junto a ele (Shift+F10 ou tecla Menu). */
    boolean menuForFocused() {
        Focus f = currentFocus();
        if (f == null) return false;
        AppCatalog.App app = catalog.find(f.pkg);
        View view = tileViews.get(f.zone + "|" + f.pkg);
        if (app == null || view == null) return false;
        showMenu(displayName(app), appMenu(app), view);
        return true;
    }

    /** Renomeia o ícone com foco (F2). */
    boolean renameFocused() {
        Focus f = currentFocus();
        if (f == null) return false;
        AppCatalog.App app = catalog.find(f.pkg);
        if (app == null) return false;
        Dialogs.rename(this, app);
        return true;
    }

    /* ---------- Ações sobre aplicações e preferências ---------- */

    /** Abre a aplicação, regista-a como recente e fecha os painéis. */
    void launch(AppCatalog.App app) {
        if (prefs.isHidden(app.packageName)) return;
        rememberRecent(app.packageName);
        closePanels();
        system.openApp(app.packageName);
    }

    /** Coloca a app no topo da lista de recentes, mantendo o limite de apps em execução. */
    private void rememberRecent(String pkg) {
        List<String> recent = prefs.list(Prefs.RECENT);
        recent.remove(pkg);
        recent.add(0, pkg);
        while (recent.size() > Prefs.RECENT_LIMIT) recent.remove(recent.size() - 1);
        prefs.putList(Prefs.RECENT, recent);
    }

    /** Fecha a app em segundo plano e tira-a da lista de recentes. */
    private void closeRunningApp(String pkg) {
        system.killApp(pkg);
        List<String> recent = prefs.list(Prefs.RECENT);
        recent.remove(pkg);
        prefs.putList(Prefs.RECENT, recent);
        render();
    }

    /** Fecha todas as apps em segundo plano e esvazia a lista de recentes. */
    private void closeAllRunning() {
        for (String pkg : prefs.list(Prefs.RECENT)) system.killApp(pkg);
        prefs.putList(Prefs.RECENT, new ArrayList<String>());
        render();
    }

    /** Afixa ou desafixa uma app numa zona (ambiente ou barra). */
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

    /** Guarda um nome personalizado para a app (vazio ou null repõe o original). */
    void setCustomName(String pkg, String value) {
        String clean = value == null || value.isEmpty() ? null : truncate(value, Prefs.NAME_MAX);
        prefs.setCustomName(pkg, clean);
        render();
    }

    /** Guarda um ícone personalizado para a app (null repõe o original). */
    void setCustomIcon(String pkg, String icon) {
        prefs.setCustomIcon(pkg, icon);
        render();
    }

    /** Guarda o nome de utilizador (vazio repõe o predefinido). */
    void setProfileName(String value) {
        String clean = value == null ? "" : value.trim();
        prefs.setProfileName(clean.isEmpty() ? null : truncate(clean, Prefs.PROFILE_NAME_MAX));
        render();
    }

    /** Guarda o avatar escolhido. */
    void setAvatar(int index) {
        prefs.setAvatar(index);
        render();
    }

    /** Corta um texto ao comprimento máximo indicado. */
    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    /* ---------- Painéis ---------- */

    /** Abre o painel se estiver fechado, ou fecha-o se já estiver aberto. */
    void togglePanel(int panel) {
        if (currentPanel == panel) closePanels();
        else openPanel(panel);
    }

    /** Fecha os outros painéis e abre o indicado. */
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
        renderActiveButtons();
        refreshPointers();
    }

    /** Abre as configurações numa vista específica (principal, perfil, aspeto…). */
    void openSettings(String screen) {
        openPanel(PANEL_SETTINGS);
        settingsPanel.show(screen);
    }

    /** Fecha todos os painéis, o menu de sugestões da pesquisa e o contorno de foco da barra. */
    void closePanels() {
        appsPanel.close();
        settingsPanel.close();
        notificationPanel.close();
        currentPanel = PANEL_NONE;
        hideSuggest();
        renderActiveButtons();
    }

    /** Indica se algum painel (Aplicações, Notificações ou Configurações) está aberto. */
    boolean anyPanelOpen() {
        return currentPanel != PANEL_NONE;
    }

    /** Mostra o ambiente de trabalho: fecha painéis e sugestões (Win+D). */
    void showDesktop() {
        closePanels();
        closeMenu();
    }

    /** Abre a janela de atalhos de teclado (F1 ou Ctrl+/). */
    void showHelp() {
        closeMenu();
        Dialogs.shortcuts(this);
    }

    /**
     * Botão Voltar e tecla Esc: fecha o menu, as sugestões, o painel ou o modo Organizar,
     * por esta ordem. Nunca sai do launcher. Devolve false se não houver nada para fechar.
     */
    boolean handleBack() {
        if (contextMenu.isOpen()) {
            contextMenu.close();
            return true;
        }
        if (suggestBox.getVisibility() == View.VISIBLE) {
            hideSuggest();
            searchField.clearFocus();
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
        if (focusZone != null) {
            setFocus(null, null);
            return true;
        }
        return false;
    }

    /* ---------- Teclado: estado dos campos ---------- */

    /** Indica se algum campo de texto tem o foco (nesse caso as teclas são para escrever). */
    boolean isTyping() {
        View focus = activity.getCurrentFocus();
        return focus instanceof EditText;
    }

    /** Indica se o campo de pesquisa da barra tem o foco. */
    boolean isSearchFocused() {
        return searchField != null && searchField.hasFocus();
    }

    /** Indica se a lista de sugestões está visível e tem pelo menos uma opção. */
    boolean hasSuggestions() {
        return suggestBox.getVisibility() == View.VISIBLE && !suggestions.isEmpty();
    }

    /** Coloca o cursor no campo de pesquisa da barra e mostra o teclado (Ctrl+F). */
    void focusSearch() {
        if (searchField == null) return;
        searchField.requestFocus();
        searchField.selectAll();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
    }

    /** Move a seleção entre as sugestões da pesquisa (setas para cima e para baixo). */
    void searchMove(int delta) {
        if (suggestions.isEmpty()) return;
        int n = suggestions.size();
        suggestIndex = (suggestIndex + delta + n) % n;
        highlightSuggest();
    }

    /* ---------- Pesquisa da barra: aplicações, definições ou Google ---------- */

    /** Recalcula as sugestões para o texto atual da pesquisa e mostra-as acima da barra. */
    private void renderSuggest() {
        String raw = searchField.getText().toString().trim();
        suggestBox.removeAllViews();
        suggestions = new ArrayList<>();
        suggestIndex = 0;
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
        highlightSuggest();
    }

    /** Destaca a sugestão selecionada (com o teclado ou o rato). */
    private void highlightSuggest() {
        for (int i = 0; i < suggestBox.getChildCount(); i++) {
            View row = suggestBox.getChildAt(i);
            row.setBackground(i == suggestIndex
                    ? Ui.box(Ui.SELECTED_SOFT, dp(10), 0, 0)
                    : Ui.ripple(context));
        }
    }

    /** Uma linha de sugestão: ícone, nome e tipo (Aplicação, Definição). Clicar executa-a. */
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

    /** Esconde e limpa a lista de sugestões. */
    private void hideSuggest() {
        suggestBox.setVisibility(View.GONE);
        suggestBox.removeAllViews();
        suggestions = new ArrayList<>();
        suggestIndex = 0;
    }

    /** Enter na pesquisa executa a sugestão selecionada (por omissão, a primeira). */
    private void submitSearch() {
        renderSuggest();
        if (suggestions.isEmpty()) return;
        Runnable chosen = suggestions.get(Math.min(suggestIndex, suggestions.size() - 1)).action;
        hideSuggest();
        searchField.setText("");
        chosen.run();
    }

    /** Lista de definições que a pesquisa também encontra, com palavras-chave sem acentos. */
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

    /** Indica se existe uma imagem da galeria guardada. */
    boolean hasCustomWallpaper() {
        return customWallpaperFile().isFile();
    }

    /** Miniatura reduzida de um predefinido ou da imagem da galeria. */
    Bitmap wallpaperThumbnail(int index) {
        return loadWallpaper(index, 4);
    }

    /** Carrega um papel de parede, com uma amostragem que reduz a memória (sampleSize). */
    private Bitmap loadWallpaper(int index, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        if (index == Prefs.WALLPAPER_CUSTOM) {
            return BitmapFactory.decodeFile(customWallpaperFile().getAbsolutePath(), options);
        }
        return BitmapFactory.decodeResource(context.getResources(), WALLPAPERS[index], options);
    }

    /** Ficheiro onde se guarda a imagem escolhida na galeria. */
    private File customWallpaperFile() {
        return new File(context.getFilesDir(), CUSTOM_WALLPAPER_FILE);
    }

    /** Abre o seletor de imagens do sistema para escolher um papel de parede. */
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

    /** Recebe a imagem escolhida: guarda-a e aplica-a, ou avisa que não foi possível usá-la. */
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

    /** Fator de redução (potência de 2) para que a imagem fique abaixo de maxPx. */
    private static int sampleSize(int width, int height, int maxPx) {
        int size = 1;
        while (width / (size * 2) >= maxPx && height / (size * 2) >= maxPx) size *= 2;
        return size;
    }

    /* ---------- Tipos internos ---------- */

    /**
     * Item de um menu: texto, ícone opcional, atalho de teclado mostrado à direita (opcional),
     * indicação de ação perigosa e a ação a executar.
     */
    static final class MenuItem {
        final String label;
        final String icon;
        final String hint;
        final boolean danger;
        final Runnable action;

        /** Opção simples, sem ícone nem atalho. */
        MenuItem(String label, Runnable action) {
            this(label, null, null, false, action);
        }

        /** Opção completa, com ícone, atalho e destaque para ações destrutivas. */
        MenuItem(String label, String icon, String hint, boolean danger, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.hint = hint;
            this.danger = danger;
            this.action = action;
        }
    }

    /** Cria o menu no momento do toque longo, quando o estado já pode ter mudado. */
    interface MenuFactory {
        /** Devolve as opções atuais do menu. */
        List<MenuItem> create();
    }

    /** Sugestão da pesquisa: nome, tipo, ícone e a ação ao escolher. */
    private static final class SearchItem {
        final String label;
        final String sub;
        final Drawable icon;
        final Runnable action;

        /** Cria uma sugestão com nome, tipo, ícone e ação. */
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

        /** Cria uma definição pesquisável com nome, ícone, palavras-chave e ação. */
        SettingOption(String label, String icon, String[] keys, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.keys = keys;
            this.action = action;
        }

        /** Indica se a consulta (sem acentos) corresponde ao nome ou a uma palavra-chave. */
        boolean matches(String query) {
            if (AppCatalog.normalize(label).contains(query)) return true;
            for (String key : keys) {
                if (key.contains(query)) return true;
            }
            return false;
        }
    }
}
