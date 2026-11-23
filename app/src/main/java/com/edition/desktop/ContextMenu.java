package com.edition.desktop;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Menu de contexto do launcher. Abre junto ao elemento tocado ou ao cursor e tem um cabeçalho
 * com o ícone e o nome do elemento, seguido das opções com ícones e atalhos de teclado.
 * Funciona com toque, rato (clique direito) e teclado (setas, Enter, Esc).
 */
final class ContextMenu {

    private static final int WIDTH_DP = 264;
    private static final int MARGIN_DP = 8;
    private static final int GAP_DP = 6;
    private static final int ROW_HEIGHT_DP = 44;
    private static final int PAD_H_DP = 12;

    private final Launcher launcher;
    private final Context context;
    private final FrameLayout overlay;
    private final LinearLayout card;
    private final List<Launcher.MenuItem> items = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();
    private int selected = -1;
    private boolean open;

    /**
     * Cria o menu (escondido) sobre o ecrã. O overlay cobre tudo e apanha os toques fora do menu,
     * que o fecham. O cartão é clicável, para que tocar nele não o feche.
     */
    ContextMenu(Launcher launcher, FrameLayout host) {
        this.launcher = launcher;
        this.context = host.getContext();

        overlay = new FrameLayout(context);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> close());
        overlay.setOnContextClickListener(v -> {
            close();
            return true;
        });
        host.addView(overlay, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setBackground(Ui.box(Ui.MENU, dp(16), dp(1), Ui.STROKE));
        card.setElevation(dp(16));
        card.setClickable(true);
        overlay.addView(card, new FrameLayout.LayoutParams(dp(WIDTH_DP), WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
    }

    /** Indica se o menu está aberto neste momento. */
    boolean isOpen() {
        return open;
    }

    /**
     * Mostra o menu com cabeçalho (ícone, nome e subtítulo, todos opcionais). A posição vem do
     * retângulo âncora: abre para baixo se couber, senão para cima, sem sair do ecrã nem ficar
     * por baixo da barra de tarefas.
     *
     * @param title    nome do elemento, ou null para não mostrar cabeçalho
     * @param subtitle linha pequena por baixo do nome, ou null
     * @param icon     ícone do elemento, ou null
     * @param list     opções a mostrar
     * @param anchor   retângulo de referência, em coordenadas do launcher
     */
    void show(String title, String subtitle, Drawable icon, List<Launcher.MenuItem> list, Rect anchor) {
        if (list == null || list.isEmpty()) return;
        card.removeAllViews();
        items.clear();
        rows.clear();
        items.addAll(list);
        selected = -1;

        if (title != null && !title.isEmpty()) {
            card.addView(header(title, subtitle, icon));
            card.addView(divider(), dividerParams());
        }
        for (Launcher.MenuItem item : list) {
            View row = row(item);
            rows.add(row);
            card.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, dp(ROW_HEIGHT_DP)));
        }

        // Mede o cartão antes de o posicionar, porque a altura depende do número de opções.
        int width = dp(WIDTH_DP);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = card.getMeasuredHeight();

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int margin = dp(MARGIN_DP);
        int bottomLimit = metrics.heightPixels - launcher.prefs().barHeight() - margin;

        int x = clamp(anchor.left, margin, metrics.widthPixels - width - margin);
        int below = anchor.bottom + dp(GAP_DP);
        int above = anchor.top - dp(GAP_DP) - height;
        int y;
        if (below + height <= bottomLimit) {
            y = below;
        } else if (above >= margin) {
            y = above;
        } else {
            y = Math.max(margin, bottomLimit - height);
        }

        card.setX(x);
        card.setY(y);
        card.setPivotX(0);
        card.setPivotY(0);
        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        overlay.setVisibility(View.VISIBLE);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
        Ui.pointers(overlay);
        open = true;
    }

    /** Fecha o menu e liberta as opções. Não faz nada se já estiver fechado. */
    void close() {
        if (!open) return;
        open = false;
        overlay.setVisibility(View.GONE);
        card.removeAllViews();
        items.clear();
        rows.clear();
        selected = -1;
    }

    /**
     * Move a seleção do teclado para cima (-1) ou para baixo (+1). A seleção dá a volta no fim
     * da lista. Devolve false se o menu estiver fechado.
     */
    boolean moveSelection(int delta) {
        if (!open || rows.isEmpty()) return false;
        int n = rows.size();
        if (selected < 0) {
            selected = delta > 0 ? 0 : n - 1;
        } else {
            selected = (selected + delta + n) % n;
        }
        for (int i = 0; i < n; i++) {
            rows.get(i).setBackground(i == selected
                    ? Ui.box(Ui.SELECTED_SOFT, dp(10), 0, 0)
                    : Ui.ripple(context));
        }
        return true;
    }

    /**
     * Executa a opção selecionada pelo teclado, fechando o menu antes. Devolve false se nenhuma
     * opção estiver selecionada.
     */
    boolean activateSelected() {
        if (!open || selected < 0 || selected >= items.size()) return false;
        Launcher.MenuItem item = items.get(selected);
        close();
        item.action.run();
        return true;
    }

    /** Cabeçalho: ícone do elemento (se houver), nome em destaque e subtítulo por baixo. */
    private View header(String title, String subtitle, Drawable icon) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(PAD_H_DP), dp(10), dp(PAD_H_DP), dp(10));

        if (icon != null) {
            ImageView image = new ImageView(context);
            image.setImageDrawable(icon);
            header.addView(image, new LinearLayout.LayoutParams(dp(34), dp(34)));
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(icon != null ? dp(12) : 0, 0, 0, 0);
        TextView name = Ui.text(context, title, 14, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.text(context, subtitle, 11, Ui.MUTED, false);
            sub.setSingleLine(true);
            texts.addView(sub);
        }
        header.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        return header;
    }

    /** Linha fina que separa o cabeçalho das opções. */
    private View divider() {
        View view = new View(context);
        view.setBackgroundColor(Ui.STROKE);
        return view;
    }

    /** Margens do separador: alinhado com o conteúdo, com espaço acima e abaixo. */
    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, dp(1));
        params.setMargins(dp(8), dp(4), dp(8), dp(6));
        return params;
    }

    /**
     * Uma opção: ícone num quadrado suave, nome e atalho de teclado à direita (se existir).
     * Opções perigosas ficam a vermelho. Ao ser escolhida, fecha o menu e executa a ação.
     */
    private View row(final Launcher.MenuItem item) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(PAD_H_DP), 0, dp(PAD_H_DP), 0);
        row.setBackground(Ui.ripple(context));
        row.setClickable(true);
        Ui.pointer(row, PointerIcon.TYPE_HAND);

        if (item.icon != null) {
            FrameLayout badge = new FrameLayout(context);
            badge.setBackground(Ui.box(item.danger ? 0x26F0585B : Ui.FIELD, dp(8), 0, 0));
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(Icons.feature(context, item.icon));
            icon.setColorFilter(item.danger ? Ui.DANGER : Ui.TEXT);
            icon.setPadding(dp(6), dp(6), dp(6), dp(6));
            badge.addView(icon, new FrameLayout.LayoutParams(dp(30), dp(30)));
            row.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));
        }

        TextView label = Ui.text(context, item.label, 14, item.danger ? Ui.DANGER : Ui.TEXT, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        labelParams.setMarginStart(dp(item.icon != null ? 12 : 2));
        row.addView(label, labelParams);

        if (item.hint != null) {
            TextView hint = Ui.text(context, item.hint, 11, Ui.MUTED, false);
            hint.setPadding(dp(7), dp(2), dp(7), dp(2));
            hint.setBackground(Ui.box(Ui.FIELD, dp(6), 0, 0));
            row.addView(hint);
        }

        // Fecha primeiro e só depois executa a ação, para o menu não ficar por cima do resultado.
        row.setOnClickListener(v -> {
            close();
            item.action.run();
        });
        return row;
    }

    /** Converte dp em píxeis. */
    private int dp(float value) {
        return Ui.dp(context, value);
    }

    /** Limita um valor ao intervalo de min a max (se o intervalo for inválido, devolve min). */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
