package com.edition.desktop;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Cores, medidas e pequenas funções de construção partilhadas pela interface. */
final class Ui {

    static final int BG = 0xFF101820;
    static final int TASKBAR = 0xEB0B131A;
    static final int SURFACE = 0xF5181E28;
    static final int RAISED = 0xFF1D2530;
    static final int STROKE = 0x24FFFFFF;
    static final int FIELD = 0x1FFFFFFF;
    static final int HOVER = 0x1AFFFFFF;
    static final int SELECTED = 0x551687E8;
    static final int ACCENT = 0xFF1687E8;
    static final int TEXT = 0xFFF5F7FA;
    static final int MUTED = 0xFFAAB4C0;
    static final int DANGER = 0xFFE5484D;

    private Ui() { }

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

    static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    /** Efeito de toque do sistema, sem cor de fundo própria. */
    static Drawable ripple(Context c) {
        TypedValue value = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        return c.getDrawable(value.resourceId);
    }

    /** Botão quadrado só com ícone (o tamanho é definido por quem o adiciona). */
    static ImageView iconButton(Context c, String icon, View.OnClickListener listener) {
        ImageView view = new ImageView(c);
        view.setImageDrawable(Icons.feature(c, icon));
        view.setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11));
        view.setBackground(ripple(c));
        view.setClickable(true);
        view.setOnClickListener(listener);
        return view;
    }
}
