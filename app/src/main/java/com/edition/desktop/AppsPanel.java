package com.edition.desktop;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Painel de aplicações, à esquerda. Tem um cartão de perfil no topo, a pesquisa e a grelha de
 * aplicações. Toque abre a app; toque longo ou clique direito abre o menu da app, junto a ela.
 */
final class AppsPanel {

    private static final int COLUMNS = 4;

    private final Launcher launcher;
    private final Context context;
    private final LinearLayout root;
    private final ImageView avatar;
    private final TextView name;
    private final TextView count;
    private final EditText search;
    private final TextView empty;
    private final Adapter adapter;
    private final GridView grid;
    private List<AppCatalog.App> shown = new ArrayList<>();

    /** Constrói o painel (escondido): perfil, pesquisa, mensagem vazia e grelha. */
    AppsPanel(Launcher launcher) {
        this.launcher = launcher;
        this.context = launcher.activity();

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackground(Ui.box(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        root.setElevation(dp(18));
        root.setClickable(true);
        root.setVisibility(View.GONE);

        // Cartão de perfil: avatar, nome e contagem. Toque abre o perfil nas configurações.
        LinearLayout profile = new LinearLayout(context);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setPadding(dp(8), dp(8), dp(10), dp(8));
        profile.setBackground(Ui.box(Ui.RAISED, dp(14), 0, 0));
        profile.setClickable(true);
        profile.setBackground(Ui.box(Ui.RAISED, dp(14), 0, 0));
        Ui.pointer(profile, PointerIcon.TYPE_HAND);
        profile.setOnClickListener(v -> launcher.openSettings(SettingsPanel.PROFILE));

        avatar = new ImageView(context);
        avatar.setBackground(Ui.box(Ui.FIELD, dp(24), dp(2), Ui.ACCENT));
        avatar.setPadding(dp(4), dp(4), dp(4), dp(4));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        name = Ui.text(context, "", 15, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        count = Ui.text(context, "", 12, Ui.MUTED, false);
        texts.addView(name);
        texts.addView(count);
        profile.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        profile.addView(Ui.iconButton(context, "ic_settings", v -> launcher.openSettings(SettingsPanel.MAIN)),
                new LinearLayout.LayoutParams(dp(42), dp(42)));
        profile.addView(Ui.iconButton(context, "ic_close", v -> launcher.closePanels()),
                new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        profileParams.bottomMargin = dp(12);
        root.addView(profile, profileParams);

        // Pesquisa, com ícone de lupa à esquerda.
        LinearLayout field = new LinearLayout(context);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(Ui.box(Ui.FIELD, dp(12), 0, 0));
        field.setPadding(dp(12), 0, dp(8), 0);
        ImageView lens = new ImageView(context);
        lens.setImageDrawable(Icons.feature(context, "ic_search"));
        lens.setColorFilter(Ui.MUTED);
        field.addView(lens, new LinearLayout.LayoutParams(dp(18), dp(18)));

        search = Ui.field(context, "Pesquisar aplicações  (Ctrl+F)");
        search.setPadding(dp(10), dp(11), 0, dp(11));
        search.addTextChangedListener(new TextWatcher() {
            /** Não faz nada antes da alteração. */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            /** Filtra a grelha a cada tecla. */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }

            /** Não faz nada depois da alteração. */
            @Override
            public void afterTextChanged(Editable s) { }
        });
        field.addView(search, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        fieldParams.bottomMargin = dp(10);
        root.addView(field, fieldParams);

        // Rótulo da secção, em maiúsculas.
        TextView section = Ui.text(context, "Todas as aplicações", 11, Ui.MUTED, true);
        section.setAllCaps(true);
        section.setPadding(dp(4), 0, dp(4), dp(6));
        root.addView(section, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        empty = Ui.text(context, "", 13, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(28), dp(16), dp(28));
        empty.setVisibility(View.GONE);
        root.addView(empty, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Grelha de aplicações. Cada célula trata o seu clique (ver Adapter.getView).
        adapter = new Adapter();
        grid = new GridView(context);
        grid.setNumColumns(COLUMNS);
        grid.setVerticalSpacing(dp(4));
        grid.setHorizontalSpacing(dp(4));
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));
    }

    /** Vista raiz do painel, para o launcher o posicionar e mostrar. */
    LinearLayout view() {
        return root;
    }

    /** Mostra o painel e atualiza o conteúdo. */
    void open() {
        root.setVisibility(View.VISIBLE);
        refresh();
    }

    /** Esconde o painel. */
    void close() {
        root.setVisibility(View.GONE);
    }

    /** Volta a calcular o perfil, a contagem e a grelha (só se o painel estiver aberto). */
    void refresh() {
        if (root.getVisibility() != View.VISIBLE) return;
        Prefs prefs = launcher.prefs();
        avatar.setImageDrawable(Icons.feature(context, Icons.avatarName(prefs.avatar())));
        name.setText(prefs.profileName());

        List<AppCatalog.App> visible = launcher.visibleApps();
        count.setText(visible.size() + (visible.size() == 1 ? " aplicação" : " aplicações"));

        String term = AppCatalog.normalize(search.getText().toString()).trim();
        List<AppCatalog.App> matches = new ArrayList<>();
        for (AppCatalog.App app : visible) {
            if (AppCatalog.normalize(launcher.displayName(app)).contains(term)) matches.add(app);
        }
        Collections.sort(matches,
                (a, b) -> launcher.displayName(a).compareToIgnoreCase(launcher.displayName(b)));
        shown = matches;

        String message = null;
        if (launcher.catalog().all().isEmpty()) {
            message = "Não foi possível carregar as aplicações.";
        } else if (visible.isEmpty()) {
            message = "Todas as aplicações estão ocultas. Podes voltar a mostrá-las em Configurações → Aplicações.";
        } else if (matches.isEmpty()) {
            message = "Nenhuma aplicação corresponde à pesquisa.";
        }
        empty.setText(message == null ? "" : message);
        empty.setVisibility(message == null ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    /** Converte dp em píxeis. */
    private int dp(float value) {
        return Ui.dp(context, value);
    }

    /** Uma célula da grelha: ícone grande e nome, com destaque ao passar o rato. */
    private final class Adapter extends BaseAdapter {

        /** Número de apps visíveis na grelha. */
        @Override
        public int getCount() {
            return shown.size();
        }

        /** Devolve a app da posição indicada. */
        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        /** O identificador é a própria posição. */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Cria a célula de uma app. Clique abre a app; toque longo e clique direito abrem o
         * menu junto à célula. Ao passar o rato, o fundo fica destacado.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final AppCatalog.App app = shown.get(position);
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(4), dp(10), dp(4), dp(8));
            cell.setBackground(Ui.ripple(context));
            cell.setClickable(true);
            Ui.pointer(cell, PointerIcon.TYPE_HAND);
            cell.setOnClickListener(v -> launcher.launch(app));
            cell.setOnLongClickListener(v -> {
                launcher.showMenuFor(app, launcher.appMenu(app), v);
                return true;
            });
            cell.setOnContextClickListener(v -> {
                launcher.showMenuFor(app, launcher.appMenu(app), v);
                return true;
            });

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(launcher.iconFor(app));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

            TextView label = Ui.text(context, launcher.displayName(app), 12, Ui.TEXT, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            labelParams.topMargin = dp(6);
            cell.addView(label, labelParams);
            return cell;
        }
    }
}
