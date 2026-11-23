package com.edition.desktop;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Centro de ações, à direita: botões de estado, som, brilho e notificações recentes. */
final class NotificationPanel {

    /** Botão de estado (Wi-Fi, Bluetooth, localização): azul quando ligado. */
    private static final class Tile {
        final LinearLayout view;
        final ImageView icon;
        final TextView state;

        Tile(LinearLayout view, ImageView icon, TextView state) {
            this.view = view;
            this.icon = icon;
            this.state = state;
        }
    }

    private static final String NO_SIGNATURE = "\u0000";

    private final Launcher launcher;
    private final Context context;
    private final LinearLayout root;
    private final Tile wifi;
    private final Tile bluetooth;
    private final Tile location;
    private final ImageView muteIcon;
    private final SeekBar volume;
    private final SeekBar brightness;
    private final TextView volumeLabel;
    private final TextView brightnessLabel;
    private final LinearLayout list;
    private final LinearLayout permission;
    private String lastSignature = NO_SIGNATURE;
    private boolean draggingVolume;
    private boolean draggingBrightness;

    NotificationPanel(Launcher launcher) {
        this.launcher = launcher;
        this.context = launcher.activity();

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackground(Ui.box(Ui.SURFACE, dp(14), dp(1), Ui.STROKE));
        root.setClickable(true);
        root.setVisibility(View.GONE);

        // Cabeçalho.
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(context, "Centro de ações", 12, Ui.MUTED, false));
        titles.addView(Ui.text(context, "Notificações", 20, Ui.TEXT, true));
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        header.addView(Ui.iconButton(context, "ic_close", v -> launcher.closePanels()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Botões de estado.
        LinearLayout tiles = new LinearLayout(context);
        tiles.setPadding(0, dp(8), 0, dp(8));
        wifi = tile(tiles, "Wi-Fi", v -> {
            launcher.system().toggleWifi();
            root.postDelayed(this::refresh, 700);
        });
        bluetooth = tile(tiles, "Bluetooth", v -> {
            launcher.system().toggleBluetooth();
            root.postDelayed(this::refresh, 700);
        });
        location = tile(tiles, "Localização", v -> launcher.system().locationSettings());
        root.addView(tiles, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Som.
        LinearLayout volumeRow = new LinearLayout(context);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        muteIcon = new ImageView(context);
        muteIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        muteIcon.setBackground(Ui.ripple(context));
        muteIcon.setClickable(true);
        muteIcon.setOnClickListener(v -> {
            launcher.system().toggleMute();
            refresh();
        });
        volumeRow.addView(muteIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout volumeBox = new LinearLayout(context);
        volumeBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout volumeHead = new LinearLayout(context);
        volumeHead.addView(Ui.text(context, "Volume", 13, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        volumeLabel = Ui.text(context, "", 12, Ui.MUTED, false);
        volumeHead.addView(volumeLabel);
        volumeBox.addView(volumeHead);
        volume = slider(volumeBox, 100, new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) launcher.system().setVolume(progress);
                volumeLabel.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                draggingVolume = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                draggingVolume = false;
            }
        });
        volumeRow.addView(volumeBox, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        root.addView(volumeRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Brilho.
        LinearLayout brightnessRow = new LinearLayout(context);
        brightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView sun = new ImageView(context);
        sun.setImageDrawable(Icons.feature(context, "ic_light_mode"));
        sun.setPadding(dp(8), dp(8), dp(8), dp(8));
        brightnessRow.addView(sun, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout brightnessBox = new LinearLayout(context);
        brightnessBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout brightnessHead = new LinearLayout(context);
        brightnessHead.addView(Ui.text(context, "Brilho", 13, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        brightnessLabel = Ui.text(context, "", 12, Ui.MUTED, false);
        brightnessHead.addView(brightnessLabel);
        brightnessBox.addView(brightnessHead);
        brightness = slider(brightnessBox, 100, new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) launcher.system().setBrightness(progress);
                brightnessLabel.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                draggingBrightness = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                draggingBrightness = false;
            }
        });
        brightnessRow.addView(brightnessBox, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        root.addView(brightnessRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Título da lista, com limpeza total.
        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, dp(12), 0, dp(6));
        TextView headingText = Ui.text(context, "Notificações", 14, Ui.TEXT, true);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        TextView clear = Ui.text(context, "Limpar tudo", 12, Ui.ACCENT, true);
        clear.setPadding(dp(8), dp(6), dp(8), dp(6));
        clear.setBackground(Ui.ripple(context));
        clear.setClickable(true);
        clear.setOnClickListener(v -> {
            launcher.system().clearNotifications();
            lastSignature = NO_SIGNATURE;
            refresh();
        });
        heading.addView(clear);
        root.addView(heading, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Lista com deslocamento.
        ScrollView scroll = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));

        // Pedido de acesso às notificações (só quando falta).
        permission = new LinearLayout(context);
        permission.setGravity(Gravity.CENTER);
        permission.setPadding(dp(12), dp(12), dp(12), dp(12));
        permission.setBackground(Ui.box(Ui.ACCENT, dp(12), 0, 0));
        permission.setClickable(true);
        permission.setOnClickListener(v -> launcher.system().notificationAccess());
        ImageView bell = new ImageView(context);
        bell.setImageDrawable(Icons.feature(context, "ic_notifications_active"));
        permission.addView(bell, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView permissionText = Ui.text(context, "Ativar acesso às notificações", 13, Ui.TEXT, true);
        permissionText.setPadding(dp(8), 0, 0, 0);
        permission.addView(permissionText);
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        permissionParams.setMargins(0, dp(8), 0, 0);
        root.addView(permission, permissionParams);
    }

    LinearLayout view() {
        return root;
    }

    void open() {
        root.setVisibility(View.VISIBLE);
        lastSignature = NO_SIGNATURE;
        refresh();
    }

    void close() {
        root.setVisibility(View.GONE);
    }

    /** Atualiza estados, valores e a lista (só se o painel estiver aberto). */
    void refresh() {
        if (root.getVisibility() != View.VISIBLE) return;
        SystemActions system = launcher.system();

        boolean wifiOn = system.isWifiEnabled();
        setTile(wifi, wifiOn, wifiOn ? "ic_wifi" : "ic_wifi_off");
        setTile(bluetooth, system.isBluetoothEnabled(), "ic_bluetooth");
        setTile(location, system.isLocationEnabled(), "ic_location");

        if (!draggingVolume) volume.setProgress(system.getVolume());
        volumeLabel.setText(volume.getProgress() + "%");
        muteIcon.setImageDrawable(Icons.feature(context, Icons.volumeIcon(volume.getProgress())));

        if (!draggingBrightness) brightness.setProgress(system.getBrightness());
        brightnessLabel.setText(brightness.getProgress() + "%");

        boolean access = system.hasNotificationAccess();
        permission.setVisibility(access ? View.GONE : View.VISIBLE);
        List<DesktopNotificationListener.Item> items = access
                ? DesktopNotificationListener.snapshot()
                : new ArrayList<DesktopNotificationListener.Item>();
        String signature = access ? signatureOf(items) : "sem-acesso";
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            renderList(items, access);
        }
    }

    private void setTile(Tile tile, boolean on, String icon) {
        tile.view.setBackground(Ui.box(on ? Ui.ACCENT : Ui.RAISED, dp(12), 0, 0));
        tile.icon.setImageDrawable(Icons.feature(context, icon));
        tile.state.setText(on ? "Ligado" : "Desligado");
    }

    private Tile tile(LinearLayout parent, String label, View.OnClickListener onClick) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(10), dp(6), dp(10));
        box.setClickable(true);
        box.setOnClickListener(onClick);

        ImageView icon = new ImageView(context);
        box.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        box.addView(Ui.text(context, label, 12, Ui.TEXT, true));
        TextView state = Ui.text(context, "Desligado", 10, Ui.MUTED, false);
        box.addView(state);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        parent.addView(box, params);
        return new Tile(box, icon, state);
    }

    private SeekBar slider(LinearLayout parent, int max, SeekBar.OnSeekBarChangeListener listener) {
        SeekBar bar = new SeekBar(context);
        bar.setMax(max);
        bar.setOnSeekBarChangeListener(listener);
        parent.addView(bar, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return bar;
    }

    private void renderList(List<DesktopNotificationListener.Item> items, boolean access) {
        list.removeAllViews();
        if (!access) {
            list.addView(note("Autoriza o acesso às notificações nas definições do Android para as veres aqui."));
            return;
        }
        if (items.isEmpty()) {
            list.addView(note("Não existem notificações ativas."));
            return;
        }
        for (DesktopNotificationListener.Item item : items) list.addView(card(item));
    }

    /** Uma notificação: tocar abre a aplicação de origem, o × dispensa só esta. */
    private View card(final DesktopNotificationListener.Item item) {
        final AppCatalog.App app = launcher.catalog().find(item.packageName);

        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(12), dp(10), dp(6), dp(10));
        card.setBackground(Ui.box(Ui.RAISED, dp(10), 0, 0));
        if (app != null) {
            card.setClickable(true);
            card.setOnClickListener(v -> launcher.launch(app));
        }
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        String appName = app != null ? launcher.displayName(app) : item.packageName;
        texts.addView(Ui.text(context, appName, 11, Ui.MUTED, false));
        TextView title = Ui.text(context, item.title, 14, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        if (!item.text.isEmpty()) {
            TextView body = Ui.text(context, item.text, 12.5f, Ui.MUTED, false);
            body.setMaxLines(3);
            body.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(body);
        }
        card.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        ImageView dismiss = new ImageView(context);
        dismiss.setImageDrawable(Icons.feature(context, "ic_close"));
        dismiss.setPadding(dp(6), dp(6), dp(6), dp(6));
        dismiss.setBackground(Ui.ripple(context));
        dismiss.setClickable(true);
        dismiss.setOnClickListener(v -> {
            launcher.system().dismissNotification(item.key);
            lastSignature = NO_SIGNATURE;
            refresh();
        });
        card.addView(dismiss, new LinearLayout.LayoutParams(dp(30), dp(30)));
        return card;
    }

    private TextView note(String text) {
        TextView view = Ui.text(context, text, 13, Ui.MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(24), dp(8), dp(24));
        return view;
    }

    private static String signatureOf(List<DesktopNotificationListener.Item> items) {
        StringBuilder signature = new StringBuilder();
        for (DesktopNotificationListener.Item item : items) {
            signature.append(item.key).append('|').append(item.title).append('|')
                    .append(item.text).append('\n');
        }
        return signature.toString();
    }

    private int dp(float value) {
        return Ui.dp(context, value);
    }
}
