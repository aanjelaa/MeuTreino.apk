package br.com.meutreino;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Desafio 5 km:
 *  - lembretes nos dias de treino (horário escolhido no app), que continuam valendo depois de reiniciar o celular;
 *  - avisos do cronômetro de intervalos (trocar trote/caminhada), que funcionam com a tela apagada.
 */
public class Lembretes extends BroadcastReceiver {
    static final String C_LEMBRETE = "lembrete5k";
    static final String C_TROTE = "intervalo_trote";
    static final String C_CAMINHADA = "intervalo_caminhada";
    static final String C_FIM = "intervalo_fim";
    static final long[] VIB_TROTE = {0, 500, 150, 500, 150, 500};
    static final long[] VIB_CAMINHADA = {0, 900};
    static final long[] VIB_FIM = {0, 300, 150, 300, 150, 300, 150, 900};
    static final int BASE_LEMBRETE = 1000, BASE_INTERVALO = 2000;
    static final String PREFS = "desafio5k";

    static void criarCanais(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel l = new NotificationChannel(C_LEMBRETE, "Lembretes do Desafio 5 km", NotificationManager.IMPORTANCE_HIGH);
        l.setDescription("Avisa o treino de corrida do dia");
        nm.createNotificationChannel(l);
        nm.createNotificationChannel(canal(C_TROTE, "Intervalo: hora de trotar", VIB_TROTE));
        nm.createNotificationChannel(canal(C_CAMINHADA, "Intervalo: hora de caminhar", VIB_CAMINHADA));
        nm.createNotificationChannel(canal(C_FIM, "Intervalo: treino concluído", VIB_FIM));
    }

    private static NotificationChannel canal(String id, String nome, long[] vib) {
        NotificationChannel ch = new NotificationChannel(id, nome, NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setVibrationPattern(vib);
        return ch;
    }

    private static PendingIntent pi(Context c, int code, String tipo, String canal, String titulo, String texto) {
        Intent i = new Intent(c, Lembretes.class);
        i.setAction("br.com.meutreino." + tipo + "." + code);
        if (titulo != null) {
            i.putExtra("canal", canal);
            i.putExtra("titulo", titulo);
            i.putExtra("texto", texto);
            i.putExtra("id", code);
        }
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean exato(AlarmManager am) {
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    // ---------- lembretes diários ----------
    /** json: [{"quando": epochMillis, "titulo": "...", "texto": "..."}] — substitui todos os anteriores. */
    static void agendarLembretes(Context c, String json) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, 0);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int antes = sp.getInt("qtdLembretes", 0);
        for (int k = 0; k < Math.max(antes, 40); k++) am.cancel(pi(c, BASE_LEMBRETE + k, "lembrete", null, null, null));
        int n = 0;
        try {
            JSONArray a = new JSONArray(json == null ? "[]" : json);
            long agora = System.currentTimeMillis();
            for (int k = 0; k < a.length() && k < 40; k++) {
                JSONObject o = a.getJSONObject(k);
                long quando = o.getLong("quando");
                if (quando <= agora) continue;
                PendingIntent p = pi(c, BASE_LEMBRETE + k, "lembrete", C_LEMBRETE, o.getString("titulo"), o.getString("texto"));
                if (exato(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, p);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, p);
                n = k + 1;
            }
        } catch (Exception ignored) {}
        sp.edit().putString("lembretes", json == null ? "[]" : json).putInt("qtdLembretes", n).apply();
    }

    // ---------- cronômetro de intervalos ----------
    /** json: [{"quando": epochMillis, "tipo": "trote|caminhada|fim", "titulo": "...", "texto": "..."}] */
    static void agendarIntervalos(Context c, String json) {
        cancelarIntervalos(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int n = 0;
        try {
            JSONArray a = new JSONArray(json);
            long agora = System.currentTimeMillis();
            for (int k = 0; k < a.length() && k < 90; k++) {
                JSONObject o = a.getJSONObject(k);
                long quando = o.getLong("quando");
                if (quando <= agora) continue;
                String tipo = o.optString("tipo");
                String canal = "trote".equals(tipo) ? C_TROTE : "caminhada".equals(tipo) ? C_CAMINHADA : C_FIM;
                PendingIntent p = pi(c, BASE_INTERVALO + k, "intervalo", canal, o.getString("titulo"), o.getString("texto"));
                if (exato(am)) {
                    // AlarmClock: dispara no segundo certo mesmo com a tela apagada (economia de bateria não atrasa)
                    Intent abrir = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent show = PendingIntent.getActivity(c, 3, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(quando, show), p);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, p);
                }
                n = k + 1;
            }
        } catch (Exception ignored) {}
        c.getSharedPreferences(PREFS, 0).edit().putInt("qtdIntervalos", n).apply();
    }

    static void cancelarIntervalos(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, 0);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int antes = Math.max(sp.getInt("qtdIntervalos", 0), 90);
        for (int k = 0; k < antes; k++) am.cancel(pi(c, BASE_INTERVALO + k, "intervalo", null, null, null));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(BASE_INTERVALO);
        sp.edit().putInt("qtdIntervalos", 0).apply();
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        criarCanais(c);
        String acao = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(acao) || "android.intent.action.MY_PACKAGE_REPLACED".equals(acao)) {
            agendarLembretes(c, c.getSharedPreferences(PREFS, 0).getString("lembretes", "[]"));
            return;
        }
        String canal = intent.getStringExtra("canal");
        if (canal == null) return;
        boolean intervalo = !C_LEMBRETE.equals(canal);

        Intent abrir = new Intent(c, MainActivity.class);
        abrir.putExtra("abrir", "desafio5k");
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pAbrir = PendingIntent.getActivity(c, 2, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, canal) : new Notification.Builder(c);
        String texto = intent.getStringExtra("texto");
        b.setSmallIcon(R.drawable.ic_notif)
         .setContentTitle(intent.getStringExtra("titulo"))
         .setContentText(texto)
         .setStyle(new Notification.BigTextStyle().bigText(texto))
         .setAutoCancel(true)
         .setContentIntent(pAbrir)
         .setCategory(intervalo ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER);
        if (Build.VERSION.SDK_INT >= 26) {
            if (intervalo) b.setTimeoutAfter(60000);
        } else {
            long[] vib = C_TROTE.equals(canal) ? VIB_TROTE : C_CAMINHADA.equals(canal) ? VIB_CAMINHADA : VIB_FIM;
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_SOUND).setVibrate(vib);
        }
        // intervalos usam sempre o mesmo id: a troca nova substitui a anterior (sem acumular na barra)
        int id = intervalo ? BASE_INTERVALO : intent.getIntExtra("id", BASE_LEMBRETE);
        try {
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
        } catch (Exception ignored) {}
    }
}
