package com.edition.desktop;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/** Ações e leituras do sistema: aplicações, Wi-Fi, Bluetooth, localização, som, brilho e notificações. */
final class SystemActions {

    private final Context context;

    /** Guarda o contexto da aplicação, usado para abrir atividades e aceder aos serviços do sistema. */
    SystemActions(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Abre uma atividade do sistema. Se nenhuma aplicação a conseguir abrir, não faz nada. */
    private void start(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Nenhuma aplicação sabe abrir esta ação: não há nada a fazer.
        }
    }

    /* ---------- Aplicações e definições ---------- */

    /** Abre uma aplicação pelo nome do pacote, se tiver uma atividade de arranque. */
    void openApp(String pkg) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) start(intent);
    }

    /** Abre a página de informação da aplicação nas definições do Android. */
    void appInfo(String pkg) {
        start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    /** Pede a desinstalação; o próprio Android mostra a confirmação final. */
    void uninstall(String pkg) {
        start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }

    /** Fecha os processos em segundo plano. Uma app visível pode continuar aberta. */
    void killApp(String pkg) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) am.killBackgroundProcesses(pkg);
    }

    /** Abre as definições do Android. */
    void androidSettings() {
        start(new Intent(Settings.ACTION_SETTINGS));
    }

    /** Abre o ecrã onde se ativa o acesso às notificações para o launcher. */
    void notificationAccess() {
        start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    /** Abre as definições de localização do sistema. */
    void locationSettings() {
        start(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    /** Abre as definições de Bluetooth do sistema. */
    void bluetoothSettings() {
        start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    /** Abre o painel de Wi-Fi (Android 10 ou superior) ou as definições de Wi-Fi (versões anteriores). */
    void wifiSettings() {
        start(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? new Intent(Settings.Panel.ACTION_WIFI)
                : new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    /** Abre uma pesquisa no Google no navegador, com o texto indicado. */
    void googleSearch(String query) {
        start(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim()))));
    }

    /* ---------- Wi-Fi, Bluetooth e localização ---------- */

    /** Obtém o serviço de Wi-Fi do sistema. */
    private WifiManager wifi() {
        return (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    /** Indica se o Wi-Fi está ligado. */
    boolean isWifiEnabled() {
        WifiManager wifi = wifi();
        return wifi != null && wifi.isWifiEnabled();
    }

    /** Liga ou desliga diretamente só até Android 9. Depois abre o painel oficial. */
    void toggleWifi() {
        WifiManager wifi = wifi();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && wifi != null) {
            wifi.setWifiEnabled(!wifi.isWifiEnabled());
        } else {
            wifiSettings();
        }
    }

    /** Indica se o Bluetooth está ligado. */
    boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /** Liga ou desliga diretamente só até Android 12. Depois abre as definições. */
    void toggleBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothSettings();
            return;
        }
        try {
            if (adapter.isEnabled()) adapter.disable();
            else adapter.enable();
        } catch (SecurityException e) {
            bluetoothSettings();
        }
    }

    /** Indica se a localização do sistema está ativa. */
    boolean isLocationEnabled() {
        try {
            int mode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            return mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    /* ---------- Som e brilho ---------- */

    /** Obtém o serviço de áudio do sistema. */
    private AudioManager audio() {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /** Volume da música em percentagem (0 a 100). */
    int getVolume() {
        AudioManager audio = audio();
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (max == 0) return 0;
        return Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
    }

    /** Define o volume da música em percentagem. Valores fora de 0 a 100 são limitados. */
    void setVolume(int percent) {
        AudioManager audio = audio();
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = Math.max(0, Math.min(100, percent));
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * value / 100f), 0);
    }

    /** Silencia o volume, ou repõe metade do máximo se já estiver em silêncio. */
    void toggleMute() {
        AudioManager audio = audio();
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            int half = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, half, 0);
        } else {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        }
    }

    /** Devolve o brilho do ecrã em percentagem. Se não for possível ler, devolve 50. */
    int getBrightness() {
        try {
            int raw = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return Math.round(raw * 100f / 255f);
        } catch (Settings.SettingNotFoundException e) {
            return 50;
        }
    }

    /** Indica se a aplicação pode alterar as definições do sistema (necessário para o brilho). */
    boolean canWriteSettings() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context);
    }

    /** Sem a autorização «Modificar definições do sistema» abre o ecrã onde a conceder. */
    void setBrightness(int percent) {
        if (!canWriteSettings()) {
            requestWriteSettings();
            return;
        }
        int value = Math.max(1, Math.min(100, percent));
        try {
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, Math.round(value * 255f / 100f));
        } catch (SecurityException ignored) {
            requestWriteSettings();
        }
    }

    /** Abre o ecrã onde se concede a autorização «Modificar definições do sistema». */
    void requestWriteSettings() {
        start(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + context.getPackageName())));
    }

    /* ---------- Notificações ---------- */

    /** Indica se o launcher tem acesso às notificações do sistema. */
    boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) {
            return false;
        }
        ComponentName listener = new ComponentName(context, DesktopNotificationListener.class);
        return enabled.contains(listener.flattenToString())
                || enabled.contains(listener.flattenToShortString());
    }


    /** Dispensa uma notificação (no sistema e na lista do launcher). */
    void dismissNotification(String key) {
        DesktopNotificationListener.dismiss(key);
    }

    /** Dispensa todas as notificações do launcher. */
    void clearNotifications() {
        DesktopNotificationListener.clearAll();
    }
}
