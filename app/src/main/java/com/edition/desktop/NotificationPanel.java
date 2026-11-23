package com.edition.desktop;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Centro de ações, à direita: botões de estado (Wi-Fi, Bluetooth, localização), volume e brilho,
 * e a lista de notificações recentes. Tudo o que mostra é lido do sistema ao abrir e ao atualizar.
 */
final class NotificationPanel {

    /** Botão de estado: o cartão, o círculo com o ícone e o texto de estado (para os atualizar). */
    private static final class Tile {
        final LinearLayout view;
        final FrameLayout circle;
        final ImageView icon;
        final TextView state;

        /** Guarda as vistas de um botão de estado, para as poder atualizar mais tarde. */
        Tile(LinearLayout view, FrameLayout circle, ImageView icon, TextView state) {
            this.view = view;
            this.circle = circle;
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
    private final TextView countPill;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final LinearLayout permission;
    private String lastSignature = NO_SIGNATURE;
    private boolean draggingVolume;
    private boolean draggingBrightness;

    /**
     * Constrói o centro de ações (escondido): cabeçalho, botões de estado, cartão de controlos,
     * lista de notificações com deslocamento e o pedido de acesso (quando falta).
     */
    NotificationPanel(Launcher launcher) {
        this.launcher = launcher;
        this.context = launcher.activity();

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(Ui.box(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        root.setElevation(dp(18));
        root.setClickable(true);
        root.setVisibility(View.GONE);

        // Cabeçalho: rótulo pequeno, título e fechar.
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView kicker = Ui.text(context, "Centro de ações", 11, Ui.MUTED, true);
        kicker.setAllCaps(true);
        titles.addView(kicker);
        titles.addView(Ui.text(context, "Ações rápidas", 18, Ui.TEXT, true));
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        header.addView(Ui.iconButton(context, "ic_close", v -> launcher.closePanels()),
                new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        headerParams.bottomMargin = dp(12);
        root.addView(header, headerParams);

        // Botões de estado: três cartões iguais em linha.
        LinearLayout tiles = new LinearLayout(context);
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

        // Cartão dos controlos: volume e brilho, um por linha.
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(10));
        controls.setBackground(Ui.box(Ui.RAISED, dp(16), 0, 0));

        LinearLayout volumeRow = new LinearLayout(context);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        muteIcon = new ImageView(context);
        muteIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        muteIcon.setBackground(Ui.box(Ui.FIELD, dp(12), 0, 0));
        muteIcon.setClickable(true);
        Ui.pointer(muteIcon, PointerIcon.TYPE_HAND);
        muteIcon.setOnClickListener(v -> {
            launcher.system().toggleMute();
            refresh();
        });
        volumeRow.addView(muteIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout volumeBox = new LinearLayout(context);
        volumeBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout volumeHead = new LinearLayout(context);
        volumeHead.setGravity(Gravity.CENTER_VERTICAL);
        volumeHead.addView(Ui.text(context, "Volume", 13, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        volumeLabel = valuePill();
        volumeHead.addView(volumeLabel);
        volumeBox.addView(volumeHead);
        volume = slider(volumeBox, new SeekBar.OnSeekBarChangeListener() {
            /** Atualiza o valor e aplica o volume enquanto o utilizador arrasta. */
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) launcher.system().setVolume(progress);
                volumeLabel.setText(progress + "%");
            }

            /** Marca que o utilizador começou a arrastar, para não redesenhar o controlo a meio. */
            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                draggingVolume = true;
            }

            /** Marca o fim do arrasto e volta a sincronizar com o valor real do sistema. */
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                draggingVolume = false;
            }
        });
        volumeRow.addView(volumeBox, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        controls.addView(volumeRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(Ui.STROKE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(1));
        dividerParams.setMargins(dp(52), dp(8), 0, dp(8));
        controls.addView(divider, dividerParams);

        LinearLayout brightnessRow = new LinearLayout(context);
        brightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView sun = new ImageView(context);
        sun.setImageDrawable(Icons.feature(context, "ic_light_mode"));
        sun.setPadding(dp(9), dp(9), dp(9), dp(9));
        sun.setBackground(Ui.box(Ui.FIELD, dp(12), 0, 0));
        brightnessRow.addView(sun, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout brightnessBox = new LinearLayout(context);
        brightnessBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout brightnessHead = new LinearLayout(context);
        brightnessHead.setGravity(Gravity.CENTER_VERTICAL);
        brightnessHead.addView(Ui.text(context, "Brilho", 13, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        brightnessLabel = valuePill();
        brightnessHead.addView(brightnessLabel);
        brightnessBox.addView(brightnessHead);
        brightness = slider(brightnessBox, new SeekBar.OnSeekBarChangeListener() {
            /** Atualiza o valor e aplica o brilho enquanto o utilizador arrasta. */
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) launcher.system().setBrightness(progress);
                brightnessLabel.setText(progress + "%");
            }

            /** Marca que o utilizador começou a arrastar, para não redesenhar o controlo a meio. */
            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                draggingBrightness = true;
            }

            /** Marca o fim do arrasto e volta a sincronizar com o valor real do sistema. */
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                draggingBrightness = false;
            }
        });
        brightnessRow.addView(brightnessBox, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        controls.addView(brightnessRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        controlsParams.setMargins(0, dp(10), 0, 0);
        root.addView(controls, controlsParams);

        // Título da lista, com contador e limpeza total.
        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(4), dp(16), dp(2), dp(8));
        heading.addView(Ui.text(context, "Notificações", 15, Ui.TEXT, true));
        countPill = valuePill();
        countPill.setVisibility(View.GONE);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        countParams.setMargins(dp(8), 0, 0, 0);
        heading.addView(countPill, countParams);
        heading.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView clear = Ui.text(context, "Limpar tudo", 12, Ui.ACCENT, true);
        clear.setPadding(dp(10), dp(6), dp(10), dp(6));
        clear.setBackground(Ui.ripple(context));
        clear.setClickable(true);
        Ui.pointer(clear, PointerIcon.TYPE_HAND);
        clear.setOnClickListener(v -> {
            launcher.system().clearNotifications();
            lastSignature = NO_SIGNATURE;
            refresh();
        });
        heading.addView(clear);
        root.addView(heading, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Lista com deslocamento. A altura acompanha o conteúdo (ver refresh).
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Pedido de acesso às notificações (só quando falta).
        permission = new LinearLayout(context);
        permission.setGravity(Gravity.CENTER_VERTICAL);
        permission.setPadding(dp(14), dp(12), dp(14), dp(12));
        permission.setBackground(Ui.box(Ui.SELECTED_SOFT, dp(14), dp(1), Ui.ACCENT));
        permission.setClickable(true);
        Ui.pointer(permission, PointerIcon.TYPE_HAND);
        permission.setOnClickListener(v -> launcher.system().notificationAccess());
        FrameLayout bellCircle = new FrameLayout(context);
        bellCircle.setBackground(Ui.box(Ui.ACCENT, dp(18), 0, 0));
        ImageView bell = new ImageView(context);
        bell.setImageDrawable(Icons.feature(context, "ic_notifications_active"));
        bell.setPadding(dp(8), dp(8), dp(8), dp(8));
        bellCircle.addView(bell, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        permission.addView(bellCircle, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout permissionTexts = new LinearLayout(context);
        permissionTexts.setOrientation(LinearLayout.VERTICAL);
        permissionTexts.setPadding(dp(12), 0, 0, 0);
        permissionTexts.addView(Ui.text(context, "Ativar acesso às notificações", 13, Ui.TEXT, true));
        permissionTexts.addView(Ui.text(context, "Toca para abrir as definições", 11, Ui.MUTED, false));
        permission.addView(permissionTexts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        permissionParams.setMargins(0, dp(12), 0, 0);
        root.addView(permission, permissionParams);
    }

    /** Vista raiz do painel, para o launcher o posicionar à direita. */
    LinearLayout view() {
        return root;
    }

    /** Mostra o painel e atualiza todos os controlos com o estado atual do sistema. */
    void open() {
        root.setVisibility(View.VISIBLE);
        lastSignature = NO_SIGNATURE;
        refresh();
    }

    /** Esconde o painel. */
    void close() {
        root.setVisibility(View.GONE);
    }

    /**
     * Atualiza estados, valores, contador e a lista, e ajusta a altura ao conteúdo. Só faz
     * alguma coisa se o painel estiver aberto. A lista só é redesenhada se algo mudou.
     */
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

        countPill.setText(String.valueOf(items.size()));
        countPill.setVisibility(access && !items.isEmpty() ? View.VISIBLE : View.GONE);

        // A lista ocupa o que precisa, até ao limite do ecrã; depois disso rola.
        Ui.fitPanel(root, scroll, WRAP_CONTENT, launcher.panelMaxHeight());
    }

    /**
     * Pinta um botão de estado. Ligado: cartão azul e ícone branco. Desligado: cartão neutro e
     * ícone esbatido. O texto mostra «Ligado» ou «Desligado».
     */
    private void setTile(Tile tile, boolean on, String icon) {
        tile.view.setBackground(Ui.box(on ? Ui.ACCENT : Ui.RAISED, dp(16), 0, 0));
        tile.circle.setBackground(Ui.box(on ? 0x33FFFFFF : Ui.FIELD, dp(20), 0, 0));
        tile.icon.setImageDrawable(Icons.feature(context, icon));
        tile.icon.setColorFilter(on ? Color.WHITE : Ui.MUTED);
        tile.state.setText(on ? "Ligado" : "Desligado");
        tile.state.setTextColor(on ? Color.WHITE : Ui.MUTED);
    }

    /**
     * Cria um botão de estado (círculo com ícone, nome e estado) e junta-o à linha. Toque
     * executa a ação indicada.
     */
    private Tile tile(LinearLayout parent, String label, View.OnClickListener onClick) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(8), dp(14), dp(8), dp(12));
        box.setClickable(true);
        Ui.pointer(box, PointerIcon.TYPE_HAND);
        box.setOnClickListener(onClick);

        FrameLayout circle = new FrameLayout(context);
        ImageView icon = new ImageView(context);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        circle.addView(icon, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        box.addView(circle, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView name = Ui.text(context, label, 12, Ui.TEXT, true);
        name.setPadding(0, dp(8), 0, 0);
        box.addView(name);
        TextView state = Ui.text(context, "Desligado", 10.5f, Ui.MUTED, false);
        box.addView(state);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(box, params);
        return new Tile(box, circle, icon, state);
    }

    /** Pílula pequena com um valor (percentagem ou contador). */
    private TextView valuePill() {
        TextView pill = Ui.text(context, "", 11.5f, Ui.TEXT, true);
        pill.setPadding(dp(8), dp(2), dp(8), dp(2));
        pill.setBackground(Ui.box(Ui.FIELD, dp(8), 0, 0));
        return pill;
    }

    /** Barra de deslize de 0 a 100, sem valor inicial, com o listener indicado. */
    private SeekBar slider(LinearLayout parent, SeekBar.OnSeekBarChangeListener listener) {
        SeekBar bar = new SeekBar(context);
        bar.setMax(100);
        bar.setOnSeekBarChangeListener(listener);
        parent.addView(bar, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return bar;
    }

    /**
     * Desenha a lista de notificações. Sem acesso, mostra como o ativar; sem notificações,
     * mostra uma mensagem simples; caso contrário, um cartão por notificação.
     */
    private void renderList(List<DesktopNotificationListener.Item> items, boolean access) {
        list.removeAllViews();
        if (!access) {
            list.addView(note("Autoriza o acesso às notificações nas definições do Android para as veres aqui."));
            return;
        }
        if (items.isEmpty()) {
            list.addView(note("Estás em dia. Não existem notificações ativas."));
            return;
        }
        for (DesktopNotificationListener.Item item : items) list.addView(card(item));
    }

    /**
     * Um cartão de notificação: ícone da app, nome da app, título, texto (até duas linhas) e um
     * botão para a dispensar. Tocar no cartão abre a aplicação de origem.
     */
    private View card(final DesktopNotificationListener.Item item) {
        final AppCatalog.App app = launcher.catalog().find(item.packageName);

        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(8), dp(10));
        card.setBackground(Ui.box(Ui.RAISED, dp(14), 0, 0));
        if (app != null) {
            card.setClickable(true);
            Ui.pointer(card, PointerIcon.TYPE_HAND);
            card.setOnClickListener(v -> launcher.launch(app));
        }
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);

        ImageView icon = new ImageView(context);
        Drawable appIcon = app != null ? launcher.iconFor(app) : Icons.feature(context, "ic_app");
        icon.setImageDrawable(appIcon);
        card.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(4), 0);
        String appName = app != null ? launcher.displayName(app) : item.packageName;
        TextView source = Ui.text(context, appName, 10.5f, Ui.MUTED, true);
        source.setAllCaps(true);
        source.setSingleLine(true);
        source.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(source);
        TextView title = Ui.text(context, item.title, 14, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        if (!item.text.isEmpty()) {
            TextView body = Ui.text(context, item.text, 12.5f, Ui.MUTED, false);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(body);
        }
        card.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        ImageView dismiss = new ImageView(context);
        dismiss.setImageDrawable(Icons.feature(context, "ic_close"));
        dismiss.setPadding(dp(8), dp(8), dp(8), dp(8));
        dismiss.setBackground(Ui.ripple(context));
        dismiss.setClickable(true);
        Ui.pointer(dismiss, PointerIcon.TYPE_HAND);
        dismiss.setOnClickListener(v -> {
            launcher.system().dismissNotification(item.key);
            lastSignature = NO_SIGNATURE;
            refresh();
        });
        card.addView(dismiss, new LinearLayout.LayoutParams(dp(32), dp(32)));
        return card;
    }

    /** Texto de apoio centrado, para mensagens vazias ou de ajuda. */
    private TextView note(String text) {
        TextView view = Ui.text(context, text, 13, Ui.MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(28), dp(16), dp(28));
        return view;
    }

    /** Assinatura textual da lista. Permite saber se mudou, evitando redesenhar sem necessidade. */
    private static String signatureOf(List<DesktopNotificationListener.Item> items) {
        StringBuilder signature = new StringBuilder();
        for (DesktopNotificationListener.Item item : items) {
            signature.append(item.key).append('|').append(item.title).append('|')
                    .append(item.text).append('\n');
        }
        return signature.toString();
    }

    /** Converte dp em píxeis. */
    private int dp(float value) {
        return Ui.dp(context, value);
    }
}
