package com.edition.desktop;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Sistema visual do launcher: paleta, medidas e funções de construção partilhadas.
 * Todas as cores e formas passam por aqui, para que a interface fique coerente.
 */
final class Ui {

    // Fundos e superfícies.
    static final int BG = 0xFF0B1016;          // fundo do ambiente de trabalho (sem papel de parede)
    static final int TASKBAR = 0xE8111820;     // barra de tarefas, ligeiramente translúcida
    static final int SURFACE = 0xF7151B25;     // painéis (aplicações, centro de ações, definições)
    static final int MENU = 0xFA1C2430;        // menus de contexto, um tom acima dos painéis
    static final int RAISED = 0xFF1E2632;      // cartões dentro dos painéis
    static final int FIELD = 0x14FFFFFF;       // campos de texto e chips
    static final int STROKE = 0x1FFFFFFF;      // contornos finos e separadores

    // Estados de interação.
    static final int HOVER = 0x1AFFFFFF;       // cursor por cima de um elemento (rato)
    static final int PRESSED = 0x2EFFFFFF;     // elemento a ser premido
    static final int SELECTED = 0x553B82F6;    // item selecionado (modo Organizar)
    static final int SELECTED_SOFT = 0x263B82F6; // seleção suave (teclado, sugestões)

    // Cor de destaque e texto.
    static final int ACCENT = 0xFF3B82F6;      // azul principal
    static final int FOCUS = 0xFF7DB4FF;       // contorno do foco do teclado
    static final int TEXT = 0xFFF2F5FA;        // texto principal
    static final int MUTED = 0xFF97A3B3;       // texto secundário
    static final int DANGER = 0xFFF0585B;      // ações destrutivas (desinstalar, ocultar)

    private Ui() { }

    /** Converte dp em píxeis, de acordo com a densidade do ecrã. */
    static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    /** Retângulo arredondado. Com `strokePx` 0 não há contorno. */
    static GradientDrawable box(int color, int radiusPx, int strokePx, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radiusPx);
        if (strokePx > 0) shape.setStroke(strokePx, strokeColor);
        return shape;
    }

    /** Texto com tamanho em sp, cor e negrito opcionais. */
    static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    /**
     * Fundo interativo de um elemento clicável: transparente em repouso, com destaque ao
     * passar o rato (hover) e mais forte ao premir. Os cantos são arredondados.
     */
    static StateListDrawable ripple(Context c) {
        float radius = dp(c, 10);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(PRESSED, radius));
        states.addState(new int[]{android.R.attr.state_hovered}, rounded(HOVER, radius));
        states.addState(new int[]{}, rounded(Color.TRANSPARENT, radius));
        return states;
    }

    /** Fundo de um elemento com o foco do teclado: contorno azul claro e fundo suave. */
    static Drawable focusBox(Context c) {
        return box(SELECTED_SOFT, dp(c, 10), dp(c, 2), FOCUS);
    }

    /** Pequeno retângulo arredondado de uma só cor (usado nos estados do «ripple»). */
    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    /** Botão quadrado só com ícone (o tamanho é definido por quem o adiciona). */
    static ImageView iconButton(Context c, String icon, View.OnClickListener listener) {
        ImageView view = new ImageView(c);
        view.setImageDrawable(Icons.feature(c, icon));
        view.setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11));
        view.setBackground(ripple(c));
        view.setClickable(true);
        view.setOnClickListener(listener);
        pointer(view, PointerIcon.TYPE_HAND);
        return view;
    }

    /**
     * Botão de texto para diálogos. O principal é preenchido a azul; o secundário é só texto.
     * Não define margens: quem o adiciona a uma linha decide o espaçamento.
     */
    static TextView button(Context c, String label, boolean primary, Runnable action) {
        TextView view = text(c, label, 14, primary ? Color.WHITE : MUTED, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        view.setBackground(primary ? box(ACCENT, dp(c, 10), 0, 0) : ripple(c));
        view.setClickable(true);
        view.setOnClickListener(v -> action.run());
        pointer(view, PointerIcon.TYPE_HAND);
        return view;
    }

    /**
     * Diálogo com o mesmo aspeto do resto do launcher: cartão escuro e arredondado, título,
     * texto de apoio, conteúdo opcional e botões. Se o conteúdo for um campo de texto,
     * Enter confirma. Esc e o botão de cancelar fecham sem guardar.
     *
     * @param content     vista extra (campo de texto, lista…) ou null
     * @param okLabel     texto do botão principal, ou null para não o mostrar
     * @param onOk        ação do botão principal (depois o diálogo fecha)
     * @param cancelLabel texto do botão secundário, ou null
     */
    static AlertDialog dialog(Context ctx, String title, String message, View content,
                              String okLabel, Runnable onOk, String cancelLabel) {
        final AlertDialog[] holder = new AlertDialog[1];
        Runnable confirm = () -> {
            if (onOk != null) onOk.run();
            holder[0].dismiss();
        };

        int pad = dp(ctx, 22);
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, dp(ctx, 16));
        panel.setBackground(box(SURFACE, dp(ctx, 18), dp(ctx, 1), STROKE));

        panel.addView(text(ctx, title, 18, TEXT, true));
        if (message != null && !message.isEmpty()) {
            TextView body = text(ctx, message, 13, MUTED, false);
            body.setLineSpacing(dp(ctx, 2), 1f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.topMargin = dp(ctx, 6);
            panel.addView(body, params);
        }
        if (content != null) {
            // Listas longas (atalhos) ganham altura fixa e rolam, para caberem em ecrãs pequenos.
            int height = content instanceof android.widget.ScrollView ? dp(ctx, 340) : WRAP_CONTENT;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, height);
            params.topMargin = dp(ctx, 16);
            panel.addView(content, params);
            if (content instanceof EditText) {
                // Enter num campo de texto confirma, como num diálogo de sistema.
                ((EditText) content).setOnEditorActionListener((v, actionId, event) -> {
                    confirm.run();
                    return true;
                });
            }
        }

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        row.topMargin = dp(ctx, 20);
        if (cancelLabel != null) {
            buttons.addView(button(ctx, cancelLabel, false, () -> holder[0].dismiss()));
        }
        if (okLabel != null) {
            TextView ok = button(ctx, okLabel, true, confirm);
            LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            okParams.leftMargin = dp(ctx, 8);
            buttons.addView(ok, okParams);
        }
        panel.addView(buttons, row);

        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(panel).create();
        holder[0] = dialog;
        if (dialog.getWindow() != null) {
            // O cartão desenha o seu próprio fundo; o do diálogo fica transparente.
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    /**
     * Define o cursor do rato para uma vista: mão para elementos clicáveis, seta para o resto.
     * Evita que o Android mostre o círculo de «toque» ao passar o cursor. Só existe a partir do Android 7.
     */
    static void pointer(View view, int type) {
        if (Build.VERSION.SDK_INT >= 24) {
            view.setPointerIcon(PointerIcon.getSystemIcon(view.getContext(), type));
        }
    }

    /**
     * Percorre a árvore de vistas e define o cursor de cada uma. Chama-se depois de construir
     * ou reconstruir a interface, porque as vistas novas não herdam o cursor da vista-mãe.
     */
    static void pointers(View view) {
        pointer(view, view.isClickable() ? PointerIcon.TYPE_HAND : PointerIcon.TYPE_ARROW);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                pointers(group.getChildAt(i));
            }
        }
    }

    /** Texto que aparece ao pousar o cursor sobre a vista (Android 8 ou superior). */
    static void tooltip(View view, CharSequence text) {
        if (Build.VERSION.SDK_INT >= 26) view.setTooltipText(text);
    }

    /** Campo de texto de uma linha, sem contorno, para pesquisas. */
    static EditText field(Context c, String hint) {
        EditText field = new EditText(c);
        field.setHint(hint);
        field.setHintTextColor(MUTED);
        field.setTextColor(TEXT);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setPadding(0, 0, 0, 0);
        return field;
    }

    /**
     * Ícone num quadrado arredondado de cor suave, usado nas listas de definições.
     * `tint` é a cor do quadrado (com transparência) e o ícone fica branco.
     */
    static FrameLayout iconTile(Context c, String icon, int tint, int sizeDp, int iconDp) {
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(box(tint, dp(c, 10), 0, 0));
        ImageView image = new ImageView(c);
        image.setImageDrawable(Icons.feature(c, icon));
        int pad = dp(c, (sizeDp - iconDp) / 2f);
        image.setPadding(pad, pad, pad, pad);
        tile.addView(image, new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp), Gravity.CENTER));
        return tile;
    }

    /**
     * Ajusta a altura de um painel ao seu conteúdo. A área com deslocamento (`content`) ocupa
     * a altura desejada (`desiredPx`, ou WRAP_CONTENT para medir o conteúdo), mas nunca mais do
     * que o painel pode ter (`maxPanelPx`). Se não couber, só essa área encolhe e passa a rolar.
     * Deve ser chamada depois de o painel ter largura definida pelo launcher.
     */
    static void fitPanel(LinearLayout panel, View content, int desiredPx, int maxPanelPx) {
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params == null) return;
        if (params instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) params).weight = 0;
        }
        params.height = desiredPx;
        content.setLayoutParams(params);

        int width = panel.getLayoutParams() != null ? panel.getLayoutParams().width : 0;
        if (width <= 0) return;
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int total = panel.getMeasuredHeight();
        if (total > maxPanelPx) {
            int contentH = content.getMeasuredHeight() - (total - maxPanelPx);
            params.height = Math.max(dp(panel.getContext(), 120), contentH);
            content.setLayoutParams(params);
        }
    }
}
