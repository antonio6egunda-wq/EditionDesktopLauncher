package com.edition.desktop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.widget.FrameLayout;
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

/**
 * Configurações do launcher, num painel centrado com vistas por categoria. A vista principal
 * lista as categorias com um ícone colorido; cada vista usa cartões com linhas, miniaturas,
 * controlos de tamanho ou a lista de aplicações.
 */
final class SettingsPanel {

    static final String MAIN = "main";
    static final String PROFILE = "profile";
    static final String ASPECT = "aspect";
    static final String BAR = "bar";
    static final String APPS = "apps";
    static final String SYSTEM = "system";

    /** Cores suaves dos quadrados de ícone de cada categoria. */
    private static final int TINT_BLUE = 0x333B82F6;
    private static final int TINT_PURPLE = 0x338B5CF6;
    private static final int TINT_TEAL = 0x3314B8A6;
    private static final int TINT_AMBER = 0x33F59E0B;
    private static final int TINT_SLATE = 0x3394A3B8;

    /** Recebe o novo valor de um controlo de tamanho. */
    private interface Setter {
        /** Guarda o valor escolhido. */
        void set(int value);
    }

    private final Launcher launcher;
    private final Context context;
    private final LinearLayout root;
    private final TextView back;
    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout body;
    private String screen = MAIN;

    /**
     * Constrói o painel de configurações (escondido): cabeçalho com botão Voltar, título e
     * fechar, e uma área de conteúdo com deslocamento.
     */
    SettingsPanel(Launcher launcher) {
        this.launcher = launcher;
        this.context = launcher.activity();

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.box(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        root.setElevation(dp(18));
        root.setClickable(true);
        root.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(14), dp(10), dp(10));

        // Botão Voltar: um chevron de texto, que só aparece fora das categorias.
        back = Ui.text(context, "\u2039", 28, Ui.TEXT, false);
        back.setPadding(dp(10), 0, dp(10), 0);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setBackground(Ui.ripple(context));
        Ui.pointer(back, PointerIcon.TYPE_HAND);
        back.setOnClickListener(v -> show(MAIN));
        back.setVisibility(View.GONE);
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(4), 0, 0, 0);
        TextView kicker = Ui.text(context, "Launcher", 11, Ui.MUTED, true);
        kicker.setAllCaps(true);
        texts.addView(kicker);
        title = Ui.text(context, "Configurações", 20, Ui.TEXT, true);
        texts.addView(title);
        header.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        header.addView(Ui.iconButton(context, "ic_close", v -> launcher.closePanels()),
                new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(18));
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

    /** Recua para as categorias. Devolve false se já estiver nelas. */
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

    /** Reconstrói a vista atual se o painel estiver aberto. A barra não é reconstruída, para não interromper os controlos. */
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

    /** Vista principal: as cinco categorias, cada uma com ícone colorido, nome e descrição. */
    private void buildMain() {
        LinearLayout card = card();
        addRow(card, row(iconTile("ic_account", TINT_BLUE), "Perfil", "Nome e avatar", () -> show(PROFILE)));
        addRow(card, row(iconTile("ic_wallpaper", TINT_PURPLE), "Aspeto", "Papel de parede", () -> show(ASPECT)));
        addRow(card, row(iconTile("ic_display_settings", TINT_TEAL), "Barra e ícones",
                "Tamanho da barra, dos ícones e do ambiente", () -> show(BAR)));
        addRow(card, row(iconTile("ic_app", TINT_AMBER), "Aplicações",
                "Editar ou ocultar aplicações instaladas", () -> show(APPS)));
        addRow(card, row(iconTile("ic_settings", TINT_SLATE), "Sistema", "Definições do Android", () -> show(SYSTEM)));
    }

    /** Vista do perfil: avatar em destaque e nome de utilizador. */
    private void buildProfile() {
        Prefs prefs = launcher.prefs();

        // Cartão de destaque com o avatar grande.
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(16), dp(22), dp(16), dp(18));
        hero.setBackground(Ui.box(Ui.RAISED, dp(16), 0, 0));
        ImageView big = new ImageView(context);
        big.setImageDrawable(Icons.feature(context, Icons.avatarName(prefs.avatar())));
        big.setBackground(Ui.box(Ui.FIELD, dp(48), dp(2), Ui.ACCENT));
        big.setPadding(dp(8), dp(8), dp(8), dp(8));
        hero.addView(big, new LinearLayout.LayoutParams(dp(92), dp(92)));
        TextView nameText = Ui.text(context, prefs.profileName(), 17, Ui.TEXT, true);
        nameText.setPadding(0, dp(12), 0, 0);
        hero.addView(nameText);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        heroParams.topMargin = dp(6);
        body.addView(hero, heroParams);

        LinearLayout card = card();
        addRow(card, row(iconTile("ic_account", TINT_BLUE), "Avatar", "Escolher entre os avatares",
                () -> Dialogs.avatarPicker(launcher)));
        addRow(card, row(iconTile("ic_edit", TINT_PURPLE), "Nome de utilizador", prefs.profileName(),
                () -> Dialogs.name(launcher)));
    }

    /** Vista do aspeto: miniaturas dos papéis de parede (a usada tem marca), galeria e nota. */
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
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(84), 1f);
            cell.setMargins(dp(4), dp(4), dp(4), dp(4));
            line.addView(cells.get(i), cell);
        }
        body.addView(note("Escolhe um dos predefinidos ou usa uma imagem da tua galeria. "
                + "Uma nova imagem da galeria substitui a anterior."));
    }

    /**
     * Miniatura de um papel de parede. A que está em uso tem contorno azul e um visto no canto.
     * Toque aplica o papel de parede imediatamente.
     */
    private View thumbnail(final int index, boolean selected) {
        FrameLayout frame = new FrameLayout(context);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = launcher.wallpaperThumbnail(index);
        image.setImageBitmap(bitmap);
        image.setBackground(Ui.box(Ui.RAISED, dp(12), selected ? dp(2) : 0, Ui.ACCENT));
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        if (selected) {
            TextView check = Ui.text(context, "\u2713", 12, Color.WHITE, true);
            check.setGravity(Gravity.CENTER);
            check.setBackground(Ui.box(Ui.ACCENT, dp(11), 0, 0));
            FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(22), dp(22),
                    Gravity.TOP | Gravity.END);
            badge.setMargins(0, dp(6), dp(6), 0);
            frame.addView(check, badge);
        }

        frame.setClickable(true);
        Ui.pointer(frame, PointerIcon.TYPE_HAND);
        frame.setOnClickListener(v -> launcher.applyWallpaper(index));
        return frame;
    }

    /** Botão «+» para escolher uma imagem da galeria como papel de parede. */
    private View galleryButton() {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.box(Ui.RAISED, dp(12), dp(1), Ui.STROKE));
        button.setClickable(true);
        Ui.pointer(button, PointerIcon.TYPE_HAND);
        button.setOnClickListener(v -> launcher.pickWallpaper());
        button.addView(Ui.text(context, "+", 24, Ui.TEXT, false));
        button.addView(Ui.text(context, "Galeria", 11, Ui.MUTED, false));
        return button;
    }

    /** Vista da barra e dos ícones: três controlos de tamanho e o botão para repor predefinições. */
    private void buildBar() {
        Prefs prefs = launcher.prefs();
        LinearLayout card = card();
        addRow(card, slider("Altura da barra", Prefs.BAR_HEIGHT_MIN, Prefs.BAR_HEIGHT_MAX,
                prefs.barHeight(), " px", prefs::setBarHeight));
        addRow(card, slider("Ícones na barra", Prefs.BAR_ICON_MIN, Prefs.BAR_ICON_MAX,
                prefs.barIcon(), " px", prefs::setBarIcon));
        addRow(card, slider("Ícones no ambiente de trabalho", Prefs.DESK_ICON_MIN, Prefs.DESK_ICON_MAX,
                prefs.deskIcon(), " px", prefs::setDeskIcon));

        TextView reset = Ui.button(context, "Repor predefinições", false, () -> {
            prefs.resetBar();
            launcher.applyDisplaySize();
            build();
        });
        reset.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        reset.setTextColor(Ui.ACCENT);
        reset.setPadding(dp(14), dp(12), dp(14), dp(12));
        addRow(card, reset);
    }

    /**
     * Controlo de tamanho: rótulo, valor atual numa pílula e barra de deslize. Cada mudança é
     * aplicada logo, para se ver o efeito enquanto se arrasta.
     */
    private View slider(String label, final int min, int max, int value, final String unit, final Setter setter) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(10));

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(context, label, 14, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        final TextView output = Ui.text(context, value + unit, 12, Ui.TEXT, true);
        output.setPadding(dp(8), dp(3), dp(8), dp(3));
        output.setBackground(Ui.box(Ui.FIELD, dp(8), 0, 0));
        head.addView(output);
        box.addView(head);

        SeekBar seek = new SeekBar(context);
        seek.setMax(max - min);
        seek.setProgress(value - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            /** Atualiza o valor mostrado e aplica-o ao tamanho quando o utilizador arrasta. */
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int next = progress + min;
                output.setText(next + unit);
                setter.set(next);
                launcher.applyDisplaySize();
            }

            /** Não faz nada ao começar a arrastar. */
            @Override
            public void onStartTrackingTouch(SeekBar bar) { }

            /** Não faz nada ao acabar de arrastar. */
            @Override
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        box.addView(seek, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return box;
    }

    /** Vista das aplicações instaladas: uma linha por app, com o estado e os botões de editar e ocultar. */
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

    /**
     * Linha de uma app: ícone, nome e estado («Visível» ou «Oculta»), depois o botão de editar
     * (nome e ícone) e o de mostrar ou ocultar. Apps ocultas ficam esbatidas.
     */
    private View appRow(final AppCatalog.App app) {
        final String pkg = app.packageName;
        final boolean hidden = launcher.prefs().isHidden(pkg);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(launcher.iconFor(app));
        icon.setAlpha(hidden ? 0.4f : 1f);
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(context, launcher.displayName(app), 14, hidden ? Ui.MUTED : Ui.TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        texts.addView(Ui.text(context, hidden ? "Oculta" : "Visível", 11, Ui.MUTED, false));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        textParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(texts, textParams);

        // Editar abre um menu junto ao botão, com cabeçalho da app (ícone e nome).
        row.addView(actionIcon("ic_edit", v -> launcher.showMenuFor(app, Arrays.asList(
                new Launcher.MenuItem("Renomear", "ic_edit", "F2", false, () -> Dialogs.rename(launcher, app)),
                new Launcher.MenuItem("Editar ícone", "ic_category", null, false,
                        () -> Dialogs.iconPicker(launcher, app))), v)));

        ImageView toggle = actionIcon(hidden ? "ic_visibility_off" : "ic_visibility",
                v -> launcher.setHidden(pkg, !hidden));
        if (hidden) toggle.setColorFilter(Ui.DANGER);
        row.addView(toggle);
        return row;
    }

    /** Vista do sistema: atalho para as definições do Android. */
    private void buildSystem() {
        LinearLayout card = card();
        addRow(card, row(iconTile("ic_settings", TINT_SLATE), "Definições do Android",
                "Abrir as definições do sistema", () -> launcher.system().androidSettings()));
    }

    /* ---------- Blocos de construção ---------- */

    /** Cartão arredondado na área de conteúdo. Recebe as linhas e devolve-o para as juntar. */
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.box(Ui.RAISED, dp(14), 0, 0));
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, dp(1));
            params.setMargins(dp(60), 0, 0, 0);
            card.addView(divider, params);
        }
        card.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    }

    /** Quadrado de ícone colorido para uma categoria. */
    private View iconTile(String icon, int tint) {
        return Ui.iconTile(context, icon, tint, 36, 20);
    }

    /**
     * Linha com ícone opcional, título, subtítulo e uma seta quando é clicável. Ao passar o rato
     * ou tocar, o fundo fica destacado.
     */
    private View row(View leading, String title, String subtitle, final Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackground(Ui.ripple(context));
        if (onClick != null) {
            row.setClickable(true);
            Ui.pointer(row, PointerIcon.TYPE_HAND);
            row.setOnClickListener(v -> onClick.run());
        }
        if (leading != null) {
            row.addView(leading, new LinearLayout.LayoutParams(dp(36), dp(36)));
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
            row.addView(Ui.text(context, "\u203A", 22, Ui.MUTED, false));
        }
        return row;
    }

    /** Botão pequeno com ícone, usado nas linhas das aplicações, com efeito de toque e cursor de mão. */
    private ImageView actionIcon(String icon, View.OnClickListener listener) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(Icons.feature(context, icon));
        view.setPadding(dp(9), dp(9), dp(9), dp(9));
        view.setBackground(Ui.ripple(context));
        view.setClickable(true);
        Ui.pointer(view, PointerIcon.TYPE_HAND);
        view.setOnClickListener(listener);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return view;
    }

    /** Título de uma secção dentro da vista, em maiúsculas. */
    private TextView section(String text) {
        TextView view = Ui.text(context, text, 11, Ui.MUTED, true);
        view.setAllCaps(true);
        view.setPadding(dp(4), dp(12), dp(4), dp(6));
        return view;
    }

    /** Texto de ajuda cinzento dentro da vista. */
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
