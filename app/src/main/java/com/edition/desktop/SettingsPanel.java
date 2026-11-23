package com.edition.desktop;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Configurações do launcher, num painel centrado com vistas por categoria. */
final class SettingsPanel {

    static final String MAIN = "main";
    static final String PROFILE = "profile";
    static final String ASPECT = "aspect";
    static final String BAR = "bar";
    static final String APPS = "apps";
    static final String SYSTEM = "system";

    private interface Setter {
        void set(int value);
    }

    private final Launcher launcher;
    private final Context context;
    private final LinearLayout root;
    private final TextView back;
    private final TextView title;
    private final LinearLayout body;
    private String screen = MAIN;

    /** Constrói o painel de configurações (escondido): cabeçalho com botão Voltar e área de conteúdo com deslocamento. */
    SettingsPanel(Launcher launcher) {
        this.launcher = launcher;
        this.context = launcher.activity();

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.box(Ui.SURFACE, dp(14), dp(1), Ui.STROKE));
        root.setClickable(true);
        root.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(8), dp(8));

        back = Ui.text(context, "\u2039", 28, Ui.TEXT, false);
        back.setPadding(dp(8), 0, dp(12), 0);
        back.setClickable(true);
        back.setBackground(Ui.ripple(context));
        back.setOnClickListener(v -> show(MAIN));
        back.setVisibility(View.GONE);
        header.addView(back);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(context, "Launcher", 12, Ui.MUTED, false));
        title = Ui.text(context, "Configurações", 20, Ui.TEXT, true);
        texts.addView(title);
        header.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        header.addView(Ui.iconButton(context, "ic_close", v -> launcher.closePanels()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(16));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));
    }

    /** Vista raiz do painel, para o launcher o posicionar. */
    LinearLayout view() {
        return root;
    }

    /** Mostra o painel na vista indicada. */
    void open(String name) {
        root.setVisibility(View.VISIBLE);
        show(name);
    }

    /** Esconde o painel. */
    void close() {
        root.setVisibility(View.GONE);
    }

    /** Recua uma vista. Devolve false se já estiver nas categorias. */
    boolean back() {
        if (MAIN.equals(screen)) return false;
        show(MAIN);
        return true;
    }

    /** Muda para a vista indicada, atualiza o título e o botão Voltar e reconstrói o conteúdo. */
    void show(String name) {
        screen = name;
        title.setText(titleOf(name));
        back.setVisibility(MAIN.equals(name) ? View.GONE : View.VISIBLE);
        build();
    }

    /** Reconstrói a vista atual. A barra não se reconstrói para não interromper os controlos. */
    void refresh() {
        if (root.getVisibility() == View.VISIBLE && !BAR.equals(screen)) build();
    }

    /** Devolve o título de cada vista, mostrado no cabeçalho. */
    private static String titleOf(String name) {
        switch (name) {
            case PROFILE: return "Perfil";
            case ASPECT: return "Aspeto";
            case BAR: return "Barra e ícones";
            case APPS: return "Aplicações";
            case SYSTEM: return "Sistema";
            default: return "Configurações";
        }
    }

    /** Limpa a área de conteúdo, desenha a vista atual e atualiza os cursores das vistas novas. */
    private void build() {
        body.removeAllViews();
        switch (screen) {
            case PROFILE: buildProfile(); break;
            case ASPECT: buildAspect(); break;
            case BAR: buildBar(); break;
            case APPS: buildApps(); break;
            case SYSTEM: buildSystem(); break;
            default: buildMain(); break;
        }
        launcher.refreshPointers();
    }

    /** Vista principal: lista das categorias de configuração. */
    private void buildMain() {
        LinearLayout card = card();
        addRow(card, row(null, "Perfil", "Nome e avatar", () -> show(PROFILE)));
        addRow(card, row(null, "Aspeto", "Papel de parede", () -> show(ASPECT)));
        addRow(card, row(null, "Barra e ícones", "Tamanho da barra, dos ícones e do ambiente", () -> show(BAR)));
        addRow(card, row(null, "Aplicações", "Editar ou ocultar aplicações instaladas", () -> show(APPS)));
        addRow(card, row(null, "Sistema", "Definições do Android", () -> show(SYSTEM)));
    }

    /** Vista do perfil: avatar e nome de utilizador. */
    private void buildProfile() {
        Prefs prefs = launcher.prefs();
        LinearLayout card = card();
        ImageView avatarImage = new ImageView(context);
        avatarImage.setImageDrawable(Icons.feature(context, Icons.avatarName(prefs.avatar())));
        addRow(card, row(avatarImage, "Avatar", "Escolher entre os avatares",
                () -> Dialogs.avatarPicker(launcher)));
        addRow(card, row(null, "Nome de utilizador", prefs.profileName(),
                () -> Dialogs.name(launcher)));
    }

    /** Vista do aspeto: miniaturas dos papéis de parede, botão da galeria e nota de ajuda. */
    private void buildAspect() {
        body.addView(section("Papel de parede"));
        Prefs prefs = launcher.prefs();

        List<View> cells = new ArrayList<>();
        for (int i = 0; i < Launcher.WALLPAPER_COUNT; i++) {
            cells.add(thumbnail(i, i == prefs.wallpaper()));
        }
        if (launcher.hasCustomWallpaper()) {
            cells.add(thumbnail(Prefs.WALLPAPER_CUSTOM, prefs.wallpaper() == Prefs.WALLPAPER_CUSTOM));
        }
        cells.add(galleryButton());

        LinearLayout line = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % 3 == 0) {
                line = new LinearLayout(context);
                body.addView(line, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(72), 1f);
            cell.setMargins(dp(4), dp(4), dp(4), dp(4));
            line.addView(cells.get(i), cell);
        }
        body.addView(note("Escolhe um dos predefinidos ou usa uma imagem da tua galeria. "
                + "Uma nova imagem da galeria substitui a anterior."));
    }

    /** Miniatura de um papel de parede. A que está em uso tem contorno azul. */
    private View thumbnail(final int index, boolean selected) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = launcher.wallpaperThumbnail(index);
        image.setImageBitmap(bitmap);
        image.setBackground(Ui.box(Ui.RAISED, dp(10), selected ? dp(2) : 0, Ui.ACCENT));
        image.setClipToOutline(true);
        image.setClickable(true);
        image.setOnClickListener(v -> launcher.applyWallpaper(index));
        return image;
    }

    /** Botão «+» para escolher uma imagem da galeria como papel de parede. */
    private View galleryButton() {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.box(Ui.RAISED, dp(10), 0, 0));
        button.setClickable(true);
        button.setOnClickListener(v -> launcher.pickWallpaper());
        button.addView(Ui.text(context, "+", 24, Ui.TEXT, false));
        button.addView(Ui.text(context, "Galeria", 11, Ui.MUTED, false));
        return button;
    }

    /** Vista da barra e dos ícones: três controlos de tamanho e o botão para repor as predefinições. */
    private void buildBar() {
        Prefs prefs = launcher.prefs();
        LinearLayout card = card();
        addRow(card, slider("Altura da barra", Prefs.BAR_HEIGHT_MIN, Prefs.BAR_HEIGHT_MAX,
                prefs.barHeight(), " px", prefs::setBarHeight));
        addRow(card, slider("Ícones na barra", Prefs.BAR_ICON_MIN, Prefs.BAR_ICON_MAX,
                prefs.barIcon(), " px", prefs::setBarIcon));
        addRow(card, slider("Ícones no ambiente de trabalho", Prefs.DESK_ICON_MIN, Prefs.DESK_ICON_MAX,
                prefs.deskIcon(), " px", prefs::setDeskIcon));

        TextView reset = Ui.text(context, "Repor predefinições", 13, Ui.ACCENT, true);
        reset.setPadding(dp(14), dp(10), dp(14), dp(12));
        reset.setBackground(Ui.ripple(context));
        reset.setClickable(true);
        reset.setOnClickListener(v -> {
            prefs.resetBar();
            launcher.applyDisplaySize();
            build();
        });
        addRow(card, reset);
    }

    /** Controlo de tamanho com rótulo, valor atual e barra de deslize que aplica a mudança em tempo real. */
    private View slider(String label, final int min, int max, int value, final String unit, final Setter setter) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout head = new LinearLayout(context);
        head.addView(Ui.text(context, label, 14, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        final TextView output = Ui.text(context, value + unit, 12, Ui.MUTED, false);
        head.addView(output);
        box.addView(head);

        SeekBar seek = new SeekBar(context);
        seek.setMax(max - min);
        seek.setProgress(value - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int next = progress + min;
                output.setText(next + unit);
                setter.set(next);
                launcher.applyDisplaySize();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) { }

            @Override
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        box.addView(seek, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return box;
    }

    /** Vista das aplicações instaladas: uma linha por app, com editar e ocultar. */
    private void buildApps() {
        body.addView(note("Todas as aplicações instaladas. O botão de visibilidade oculta ou mostra "
                + "cada uma no launcher."));
        LinearLayout card = card();
        List<AppCatalog.App> all = launcher.catalog().all();
        if (all.isEmpty()) {
            addRow(card, note("Não foi possível carregar as aplicações."));
            return;
        }
        for (AppCatalog.App app : all) addRow(card, appRow(app));
    }

    /** Linha de uma app: ícone, nome, botão de editar (nome e ícone) e botão de mostrar ou ocultar. */
    private View appRow(final AppCatalog.App app) {
        final String pkg = app.packageName;
        final boolean hidden = launcher.prefs().isHidden(pkg);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(6), dp(6));

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(launcher.iconFor(app));
        icon.setAlpha(hidden ? 0.4f : 1f);
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView name = Ui.text(context, launcher.displayName(app), 14, hidden ? Ui.MUTED : Ui.TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        nameParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(name, nameParams);

        row.addView(actionIcon("ic_edit", v -> launcher.showMenu(launcher.displayName(app), Arrays.asList(
                new Launcher.MenuItem("Renomear", () -> Dialogs.rename(launcher, app)),
                new Launcher.MenuItem("Editar ícone", () -> Dialogs.iconPicker(launcher, app))))));

        ImageView toggle = actionIcon(hidden ? "ic_visibility_off" : "ic_visibility",
                v -> launcher.setHidden(pkg, !hidden));
        if (hidden) toggle.setColorFilter(Ui.DANGER);
        row.addView(toggle);
        return row;
    }

    /** Vista do sistema: atalho para as definições do Android. */
    private void buildSystem() {
        LinearLayout card = card();
        addRow(card, row(null, "Definições do Android", "Abrir as definições do sistema",
                () -> launcher.system().androidSettings()));
    }

    /* ---------- Blocos de construção ---------- */

    /** Cartão arredondado que recebe linhas. Entra diretamente no ecrã atual. */
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.box(Ui.RAISED, dp(12), 0, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        body.addView(card, params);
        return card;
    }

    /** Junta uma linha ao cartão, com um separador antes, exceto na primeira. */
    private void addRow(LinearLayout card, View row) {
        if (card.getChildCount() > 0) {
            View divider = new View(context);
            divider.setBackgroundColor(Ui.STROKE);
            card.addView(divider, new LinearLayout.LayoutParams(MATCH_PARENT, dp(1)));
        }
        card.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    }

    /** Linha com ícone opcional, título, subtítulo opcional e seta se for clicável. */
    private View row(View leading, String title, String subtitle, final Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(Ui.ripple(context));
        if (onClick != null) {
            row.setClickable(true);
            row.setOnClickListener(v -> onClick.run());
        }
        if (leading != null) {
            row.addView(leading, new LinearLayout.LayoutParams(dp(32), dp(32)));
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = Ui.text(context, title, 14.5f, Ui.TEXT, true);
        titleView.setSingleLine(true);
        texts.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = Ui.text(context, subtitle, 11.5f, Ui.MUTED, false);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitleView);
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        textParams.setMargins(leading != null ? dp(12) : 0, 0, dp(8), 0);
        row.addView(texts, textParams);

        if (onClick != null) {
            row.addView(Ui.text(context, "\u203A", 20, Ui.MUTED, false));
        }
        return row;
    }

    /** Botão pequeno com ícone, usado nas linhas das aplicações. */
    private ImageView actionIcon(String icon, View.OnClickListener listener) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(Icons.feature(context, icon));
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setBackground(Ui.ripple(context));
        view.setClickable(true);
        view.setOnClickListener(listener);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        return view;
    }

    /** Título de uma secção dentro de uma vista. */
    private TextView section(String text) {
        TextView view = Ui.text(context, text, 12, Ui.MUTED, true);
        view.setPadding(dp(4), dp(10), dp(4), dp(6));
        return view;
    }

    /** Texto de ajuda cinzento dentro de uma vista. */
    private TextView note(String text) {
        TextView view = Ui.text(context, text, 12, Ui.MUTED, false);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        return view;
    }

    /** Converte dp em píxeis. */
    private int dp(float value) {
        return Ui.dp(context, value);
    }
}
