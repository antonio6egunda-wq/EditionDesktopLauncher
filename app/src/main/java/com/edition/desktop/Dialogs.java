package com.edition.desktop;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Diálogos do launcher, todos com o mesmo aspeto (ver {@link Ui#dialog}): renomear, ícone,
 * avatar, nome, desinstalar, atalhos de teclado e informação.
 */
final class Dialogs {

    private Dialogs() { }

    /** Diálogo para renomear uma app. Campo vazio repõe o nome original. */
    static void rename(final Launcher launcher, final AppCatalog.App app) {
        final Context ctx = launcher.activity();
        final EditText input = singleLine(ctx, launcher.displayName(app), Prefs.NAME_MAX);
        Ui.dialog(ctx, "Renomear",
                "Deixa o campo vazio para voltar ao nome original («" + app.label + "»).",
                input, "Guardar",
                () -> {
                    String value = input.getText().toString().trim();
                    boolean original = value.isEmpty() || value.equals(app.label);
                    launcher.setCustomName(app.packageName, original ? null : value);
                },
                "Cancelar").show();
    }

    /** Diálogo para o nome de utilizador que aparece no topo do painel de aplicações. */
    static void name(final Launcher launcher) {
        final Context ctx = launcher.activity();
        final EditText input = singleLine(ctx, launcher.prefs().profileName(), Prefs.PROFILE_NAME_MAX);
        Ui.dialog(ctx, "Nome de utilizador",
                "Aparece no topo do painel de aplicações. Se o deixares vazio, volta a «"
                        + Prefs.DEFAULT_NAME + "».",
                input, "Guardar",
                () -> launcher.setProfileName(input.getText().toString()),
                "Cancelar").show();
    }

    /** Diálogo com os avatares disponíveis; o escolhido fica destacado com um contorno azul. */
    static void avatarPicker(final Launcher launcher) {
        final Context ctx = launcher.activity();
        final int[] selected = {launcher.prefs().avatar()};
        final ImageView[] views = new ImageView[Icons.AVATARS.length];

        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < views.length; i++) {
            final int index = i;
            ImageView view = new ImageView(ctx);
            view.setImageDrawable(Icons.feature(ctx, Icons.AVATARS[i]));
            view.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
            view.setClickable(true);
            Ui.pointer(view, android.view.PointerIcon.TYPE_HAND);
            view.setOnClickListener(v -> {
                selected[0] = index;
                markAvatars(ctx, views, selected[0]);
            });
            views[i] = view;
            row.addView(view, new LinearLayout.LayoutParams(dp(ctx, 84), dp(ctx, 84)));
        }
        markAvatars(ctx, views, selected[0]);

        Ui.dialog(ctx, "Editar avatar", "Escolhe o avatar do teu perfil.", row,
                "Guardar", () -> launcher.setAvatar(selected[0]), "Cancelar").show();
    }

    /**
     * Grelha de ícones para uma app. A posição 0 é «Original» (sem ícone personalizado);
     * as restantes são os ícones de {@link Icons#CHOICES}.
     */
    static void iconPicker(final Launcher launcher, final AppCatalog.App app) {
        final Context ctx = launcher.activity();
        final String[] chosen = {launcher.prefs().customIcon(app.packageName)};
        final String[] choices = Icons.CHOICES;

        final BaseAdapter adapter = new BaseAdapter() {
            /** Número de células: o ícone original mais os ícones disponíveis. */
            @Override
            public int getCount() {
                return choices.length + 1;
            }

            /** Não há objeto de dados por posição. */
            @Override
            public Object getItem(int position) {
                return null;
            }

            /** O identificador é a própria posição. */
            @Override
            public long getItemId(int position) {
                return position;
            }

            /** Desenha uma célula; a escolhida fica com fundo e contorno azuis. */
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                String icon = position == 0 ? null : choices[position - 1];
                boolean on = icon == null ? chosen[0] == null : icon.equals(chosen[0]);
                FrameLayout cell = new FrameLayout(ctx);
                cell.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
                cell.setBackground(Ui.box(on ? Ui.SELECTED : Color.TRANSPARENT, dp(ctx, 12),
                        on ? dp(ctx, 2) : 0, Ui.ACCENT));
                // Não é clicável: o toque é tratado pela GridView, que chama o OnItemClickListener.
                Ui.pointer(cell, android.view.PointerIcon.TYPE_HAND);
                if (icon == null) {
                    TextView label = Ui.text(ctx, "Original", 11, Ui.TEXT, false);
                    label.setGravity(Gravity.CENTER);
                    cell.addView(label, new FrameLayout.LayoutParams(dp(ctx, 52), dp(ctx, 52), Gravity.CENTER));
                } else {
                    ImageView img = new ImageView(ctx);
                    img.setImageDrawable(Icons.feature(ctx, icon));
                    cell.addView(img, new FrameLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44), Gravity.CENTER));
                }
                return cell;
            }
        };

        GridView grid = new GridView(ctx);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(ctx, 4));
        grid.setHorizontalSpacing(dp(ctx, 4));
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            chosen[0] = position == 0 ? null : choices[position - 1];
            adapter.notifyDataSetChanged();
        });
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(grid, new FrameLayout.LayoutParams(MATCH_PARENT, dp(ctx, 320)));

        Ui.dialog(ctx, "Editar ícone",
                "Escolhe um ícone para «" + launcher.displayName(app) + "».", wrap,
                "Guardar", () -> launcher.setCustomIcon(app.packageName, chosen[0]),
                "Cancelar").show();
    }

    /** Pede confirmação antes de desinstalar. O sistema pede a confirmação final. */
    static void confirmUninstall(final Launcher launcher, final AppCatalog.App app) {
        Ui.dialog(launcher.activity(), "Desinstalar",
                "Queres desinstalar " + launcher.displayName(app)
                        + "? O Android vai pedir a confirmação final.",
                null, "Desinstalar",
                () -> launcher.system().uninstall(app.packageName),
                "Cancelar").show();
    }

    /** Janela de atalhos de teclado (F1 ou Ctrl+/). Lista tecla e descrição, por linhas. */
    static void shortcuts(Launcher launcher) {
        Context ctx = launcher.activity();
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        for (String[] item : Shortcuts.HELP) {
            LinearLayout row = new LinearLayout(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(ctx, 5), 0, dp(ctx, 5));

            TextView key = Ui.text(ctx, item[0], 12, Ui.TEXT, true);
            key.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4));
            key.setBackground(Ui.box(Ui.FIELD, dp(ctx, 6), 0, 0));
            row.addView(key, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            TextView desc = Ui.text(ctx, item[1], 13, Ui.MUTED, false);
            desc.setPadding(dp(ctx, 12), 0, 0, 0);
            row.addView(desc, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
            list.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(list);

        Ui.dialog(ctx, "Atalhos de teclado",
                "Funcionam com teclado físico. O rato também tem atalhos (clique direito).",
                scroll, null, null, "Fechar").show();
    }

    /** Mensagem simples com um único botão OK. */
    static void info(Launcher launcher, String title, String message) {
        Ui.dialog(launcher.activity(), title, message, null, "OK", null, null).show();
    }

    /** Destaca o avatar escolhido e tira o destaque aos restantes. */
    private static void markAvatars(Context ctx, ImageView[] views, int selected) {
        for (int i = 0; i < views.length; i++) {
            boolean on = i == selected;
            views[i].setBackground(Ui.box(on ? Ui.SELECTED : Color.TRANSPARENT, dp(ctx, 14),
                    on ? dp(ctx, 2) : 0, Ui.ACCENT));
        }
    }

    /** Campo de uma linha, com o texto selecionado e um limite de caracteres. */
    private static EditText singleLine(Context ctx, String value, int max) {
        EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setBackground(Ui.box(Ui.FIELD, dp(ctx, 10), 0, 0));
        input.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        return input;
    }

    /** Converte dp em píxeis. */
    private static int dp(Context ctx, float value) {
        return Ui.dp(ctx, value);
    }
}
