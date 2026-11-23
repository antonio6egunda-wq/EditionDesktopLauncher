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
import android.widget.TextView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/** Diálogos do launcher: renomear, ícone, avatar, nome, desinstalar e informação. */
final class Dialogs {

    private Dialogs() { }

    static void rename(final Launcher launcher, final AppCatalog.App app) {
        final Context ctx = launcher.activity();
        final EditText input = singleLine(ctx, launcher.displayName(app), Prefs.NAME_MAX);
        new AlertDialog.Builder(ctx)
                .setTitle("Renomear")
                .setMessage("Deixa o campo vazio para voltar ao nome original («" + app.label + "»).")
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String value = input.getText().toString().trim();
                    boolean original = value.isEmpty() || value.equals(app.label);
                    launcher.setCustomName(app.packageName, original ? null : value);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void name(final Launcher launcher) {
        final Context ctx = launcher.activity();
        final EditText input = singleLine(ctx, launcher.prefs().profileName(), Prefs.PROFILE_NAME_MAX);
        new AlertDialog.Builder(ctx)
                .setTitle("Nome de utilizador")
                .setMessage("Aparece no topo do painel de aplicações. Se o deixares vazio, volta a «"
                        + Prefs.DEFAULT_NAME + "».")
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> launcher.setProfileName(input.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void avatarPicker(final Launcher launcher) {
        final Context ctx = launcher.activity();
        final int[] selected = {launcher.prefs().avatar()};
        final ImageView[] views = new ImageView[Icons.AVATARS.length];

        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
        for (int i = 0; i < views.length; i++) {
            final int index = i;
            ImageView view = new ImageView(ctx);
            view.setImageDrawable(Icons.feature(ctx, Icons.AVATARS[i]));
            view.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
            view.setOnClickListener(v -> {
                selected[0] = index;
                markAvatars(ctx, views, selected[0]);
            });
            views[i] = view;
            row.addView(view, new LinearLayout.LayoutParams(dp(ctx, 84), dp(ctx, 84)));
        }
        markAvatars(ctx, views, selected[0]);

        new AlertDialog.Builder(ctx)
                .setTitle("Editar avatar")
                .setMessage("Escolhe o avatar do teu perfil.")
                .setView(row)
                .setPositiveButton("Guardar", (d, w) -> launcher.setAvatar(selected[0]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void iconPicker(final Launcher launcher, final AppCatalog.App app) {
        final Context ctx = launcher.activity();
        final String[] chosen = {launcher.prefs().customIcon(app.packageName)};
        final String[] choices = Icons.CHOICES;

        // Posição 0 = ícone original (null); as seguintes são os ícones de Icons.CHOICES.
        final BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return choices.length + 1;
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                String icon = position == 0 ? null : choices[position - 1];
                boolean on = icon == null ? chosen[0] == null : icon.equals(chosen[0]);
                FrameLayout cell = new FrameLayout(ctx);
                cell.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
                cell.setBackground(Ui.box(on ? Ui.SELECTED : Color.TRANSPARENT, dp(ctx, 10),
                        on ? dp(ctx, 2) : 0, Ui.ACCENT));
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

        new AlertDialog.Builder(ctx)
                .setTitle("Editar ícone")
                .setMessage("Escolhe um ícone para «" + launcher.displayName(app) + "».")
                .setView(wrap)
                .setPositiveButton("Guardar", (d, w) -> launcher.setCustomIcon(app.packageName, chosen[0]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void confirmUninstall(final Launcher launcher, final AppCatalog.App app) {
        new AlertDialog.Builder(launcher.activity())
                .setTitle("Desinstalar")
                .setMessage("Queres desinstalar " + launcher.displayName(app)
                        + "? O Android vai pedir a confirmação final.")
                .setPositiveButton("Desinstalar", (d, w) -> launcher.system().uninstall(app.packageName))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void info(Launcher launcher, String title, String message) {
        new AlertDialog.Builder(launcher.activity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private static void markAvatars(Context ctx, ImageView[] views, int selected) {
        for (int i = 0; i < views.length; i++) {
            boolean on = i == selected;
            views[i].setBackground(Ui.box(on ? Ui.SELECTED : Color.TRANSPARENT, dp(ctx, 14),
                    on ? dp(ctx, 2) : 0, Ui.ACCENT));
        }
    }

    private static EditText singleLine(Context ctx, String value, int max) {
        EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        return input;
    }

    private static int dp(Context ctx, float value) {
        return Ui.dp(ctx, value);
    }
}
