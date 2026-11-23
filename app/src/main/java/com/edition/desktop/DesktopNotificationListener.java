package com.edition.desktop;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda as notificações recentes para o centro de ações. Cada notificação é identificada
 * pela sua chave, por isso várias mensagens da mesma aplicação ficam visíveis. As contínuas
 * (música, navegação) são ignoradas.
 */
public class DesktopNotificationListener extends NotificationListenerService {

    private static final int MAX_ITEMS = 40;
    private static final List<Item> ITEMS = new ArrayList<>();
    private static volatile DesktopNotificationListener instance;

    /** Uma notificação guardada. */
    static final class Item {
        final String key;
        final String packageName;
        final String title;
        final String text;

        /** Uma notificação guardada: chave única, pacote da app, título e texto. */
        Item(String key, String packageName, String title, String text) {
            this.key = key;
            this.packageName = packageName;
            this.title = title;
            this.text = text;
        }
    }

    @Override
    /** Chamado pelo sistema quando o acesso às notificações fica ativo. Guarda as notificações que já estavam no ecrã. */
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) add(sbn);
            }
        } catch (SecurityException ignored) {
            // Sem autorização o Android não entrega as notificações ativas.
        }
    }

    @Override
    /** Chamado quando o serviço termina. Deixa de haver uma instância ativa. */
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    /** Chamado quando chega uma notificação nova. Guarda-a na lista. */
    public void onNotificationPosted(StatusBarNotification sbn) {
        add(sbn);
    }

    @Override
    /** Chamado quando o sistema remove uma notificação. Retira-a da lista. */
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) removeKey(sbn.getKey());
    }

    /** Acrescenta uma notificação à lista (sem repetir, a mais recente primeiro), ignorando as permanentes e as vazias. */
    private static void add(StatusBarNotification sbn) {
        if (sbn == null || sbn.isOngoing()) return;
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;
        String title = text(notification.extras, Notification.EXTRA_TITLE);
        String body = text(notification.extras, Notification.EXTRA_TEXT);
        if (title.isEmpty() && body.isEmpty()) return;
        Item item = new Item(sbn.getKey(), sbn.getPackageName(),
                title.isEmpty() ? "Notificação" : title, body);
        synchronized (ITEMS) {
            removeKeyLocked(sbn.getKey());
            ITEMS.add(0, item);
            while (ITEMS.size() > MAX_ITEMS) ITEMS.remove(ITEMS.size() - 1);
        }
    }

    /** Cópia das notificações guardadas, a mais recente primeiro. */
    static List<Item> snapshot() {
        synchronized (ITEMS) {
            return new ArrayList<>(ITEMS);
        }
    }

    /** Remove a notificação do sistema (se o serviço estiver ligado) e da lista. */
    static void dismiss(String key) {
        DesktopNotificationListener listener = instance;
        if (listener != null && key != null) {
            try {
                listener.cancelNotification(key);
            } catch (SecurityException ignored) {
                // Sem autorização não se consegue cancelar: fica só fora da lista.
            }
        }
        removeKey(key);
    }

    /** Cancela todas as notificações no sistema (se possível) e esvazia a lista. */
    static void clearAll() {
        DesktopNotificationListener listener = instance;
        if (listener != null) {
            try {
                listener.cancelAllNotifications();
            } catch (SecurityException ignored) {
                // Ver dismiss().
            }
        }
        synchronized (ITEMS) {
            ITEMS.clear();
        }
    }

    /** Remove da lista a notificação com a chave indicada, de forma segura entre threads. */
    private static void removeKey(String key) {
        synchronized (ITEMS) {
            removeKeyLocked(key);
        }
    }

    /** Chamar sempre dentro de synchronized (ITEMS). */
    private static void removeKeyLocked(String key) {
        if (key == null) return;
        for (int i = ITEMS.size() - 1; i >= 0; i--) {
            if (key.equals(ITEMS.get(i).key)) ITEMS.remove(i);
        }
    }

    /** Lê um texto dos extras da notificação e devolve-o sem espaços, ou texto vazio. */
    private static String text(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }
}
