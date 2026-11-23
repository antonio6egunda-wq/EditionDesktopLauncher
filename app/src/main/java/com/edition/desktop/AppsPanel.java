package com.edition.desktop;

import android.content.Context;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.TypedValue;
import android.view.Gravity;
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

/** Painel de aplicações, à esquerda: perfil, pesquisa e grelha de aplicações. */
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
    private List<AppCatalog.App> shown = new ArrayList<>();

    AppsPanel(Launcher launcher) {
        this.launcher = launcher;
        this.context = launcher.activity();

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackground(Ui.box(Ui.SURFACE, dp(14), dp(1), Ui.STROKE));
        root.setClickable(true);
        root.setVisibility(View.GONE);

        // Cabeçalho: perfil (abre o perfil), definições e fechar.
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout profile = new LinearLayout(context);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setPadding(dp(6), dp(6), dp(6), dp(6));
        profile.setBackground(Ui.ripple(context));
        profile.setClickable(true);
        profile.setOnClickListener(v -> launcher.openSettings(SettingsPanel.PROFILE));

        avatar = new ImageView(context);
        avatar.setBackground(Ui.box(Ui.RAISED, dp(20), 0, 0));
        avatar.setPadding(dp(4), dp(4), dp(4), dp(4));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        name = Ui.text(context, "", 15, Ui.TEXT, true);
        name.setSingleLine(true);
        count = Ui.text(context, "", 12, Ui.MUTED, false);
        texts.addView(name);
        texts.addView(count);
        profile.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        header.addView(profile, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        header.addView(Ui.iconButton(context, "ic_settings", v -> launcher.openSettings(SettingsPanel.MAIN)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(Ui.iconButton(context, "ic_close", v -> launcher.closePanels()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Pesquisa.
        search = new EditText(context);
        search.setHint("Pesquisar aplicações");
        search.setHintTextColor(Ui.MUTED);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setSingleLine(true);
        search.setBackground(Ui.box(Ui.FIELD, dp(18), 0, 0));
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        searchParams.setMargins(dp(4), dp(10), dp(4), dp(8));
        root.addView(search, searchParams);

        empty = Ui.text(context, "", 13, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(24), dp(16), dp(24));
        empty.setVisibility(View.GONE);
        root.addView(empty, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Grelha de aplicações.
        adapter = new Adapter();
        GridView grid = new GridView(context);
        grid.setNumColumns(COLUMNS);
        grid.setVerticalSpacing(dp(4));
        grid.setHorizontalSpacing(dp(4));
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launcher.launch(shown.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            AppCatalog.App app = shown.get(position);
            launcher.showMenu(launcher.displayName(app), launcher.appMenu(app));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));
    }

    LinearLayout view() {
        return root;
    }

    void open() {
        root.setVisibility(View.VISIBLE);
        refresh();
    }

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

    private int dp(float value) {
        return Ui.dp(context, value);
    }

    /** Uma célula da grelha: ícone e nome. */
    private final class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppCatalog.App app = shown.get(position);
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(4), dp(8), dp(4), dp(6));
            cell.setBackground(Ui.ripple(context));

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(launcher.iconFor(app));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView label = Ui.text(context, launcher.displayName(app), 11.5f, Ui.TEXT, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(label, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            return cell;
        }
    }
}
