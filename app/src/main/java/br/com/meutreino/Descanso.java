package br.com.meutreino;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Agenda e mostra o aviso "Descanso acabou!", mesmo com o app em segundo plano ou a tela apagada. */
public class Descanso extends BroadcastReceiver {
    static final String CANAL = "descanso";
    static final int ID = 1;

    static void criarCanal(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Fim do descanso", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisa quando o cronômetro de descanso termina");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 250, 150, 250});
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private static PendingIntent alarme(Context c, String texto) {
        Intent i = new Intent(c, Descanso.class);
        if (texto != null) i.putExtra("texto", texto);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void agendar(Context c, int segundos, String texto) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long quando = System.currentTimeMillis() + segundos * 1000L;
        PendingIntent p = alarme(c, texto);
        am.cancel(p);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, p);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, p);
        }
    }

    static void cancelar(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(alarme(c, null));
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        criarCanal(c);
        String texto = intent.getStringExtra("texto");
        if (texto == null || texto.isEmpty()) texto = "Hora da próxima série 💪";

        Intent abrir = new Intent(c, MainActivity.class);
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pAbrir = PendingIntent.getActivity(c, 0, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif)
         .setContentTitle("⏱ Descanso acabou!")
         .setContentText(texto)
         .setAutoCancel(true)
         .setContentIntent(pAbrir)
         .setCategory(Notification.CATEGORY_REMINDER);
        if (Build.VERSION.SDK_INT >= 26) {
            b.setTimeoutAfter(20000);
        } else {
            b.setPriority(Notification.PRIORITY_HIGH)
             .setDefaults(Notification.DEFAULT_SOUND)
             .setVibrate(new long[]{0, 250, 150, 250});
        }
        try {
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID, b.build());
        } catch (Exception ignored) {}
    }
}
