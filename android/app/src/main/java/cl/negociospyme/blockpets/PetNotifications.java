package cl.negociospyme.blockpets;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/** Local-only reminders. The WebView remains the authoritative game save. */
public final class PetNotifications {
    private static final String PREFS = "pet-reminders-v1", CHANNEL = "pet-care";
    private static final int JOB_ID = 1401, NOTICE_ID = 1402, TEST_ID = 1403;
    private static final long SIX_HOURS = 6L * 60 * 60 * 1000;
    static volatile boolean foreground = false;
    private PetNotifications() {}
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Cuidado de tus mascotas", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Recordatorios de comida, limpieza, cariño, juego y descanso");
            c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }
    static String status(Context c) {
        return "{\"enabled\":" + enabled(c) + ",\"allowed\":" + allowed(c) + "}";
    }
    static void setEnabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).apply();
        if (value) schedule(c); else {
            c.getSystemService(JobScheduler.class).cancel(JOB_ID);
            c.getSystemService(NotificationManager.class).cancel(NOTICE_ID);
            c.getSystemService(NotificationManager.class).cancel(TEST_ID);
        }
    }
    static void schedule(Context c) {
        if (!enabled(c)) return;
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        for (JobInfo job : scheduler.getAllPendingJobs()) if (job.getId() == JOB_ID) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, PetReminderService.class))
                .setPeriodic(15L * 60 * 1000).setPersisted(true).build();
        scheduler.schedule(job);
    }
    static void snapshot(Context c, String raw) {
        if (raw == null || raw.length() > 12000) return;
        try {
            JSONObject object = new JSONObject(raw);
            JSONArray pets = object.getJSONArray("pets");
            if (pets.length() != 4 || !object.has("last")) return;
            prefs(c).edit().putString("snapshot", object.toString()).apply();
            // Remove a stale reminder when the relevant pet no longer needs care.
            int notified = prefs(c).getInt("notifiedPet", -1);
            if (notified >= 0 && notified < pets.length() && need(pets.getJSONObject(notified), 0) == null)
                c.getSystemService(NotificationManager.class).cancel(NOTICE_ID);
        } catch (Exception ignored) { /* A malformed snapshot never replaces the game save. */ }
    }
    private static double value(JSONObject p, String key, double elapsed, boolean sleeping) {
        double initial = p.optDouble(key, 80);
        if (Double.isNaN(initial) || Double.isInfinite(initial)) initial = 80;
        double delta = key.equals("energy") ? (sleeping ? 4 : -.04) : -.06;
        return Math.max(key.equals("energy") ? 0 : 15, Math.min(100, initial + elapsed * delta));
    }
    static String need(JSONObject p, double elapsed) {
        boolean sleeping = p.optBoolean("sleeping", false);
        String[] keys = {"food", "clean", "love", "fun", "energy"};
        String[] labels = {"comer", "un baño", "cariño", "jugar", "descansar"};
        double lowest = 30.000001; String action = null;
        for (int i = 0; i < keys.length; i++) {
            if (sleeping && (keys[i].equals("fun") || keys[i].equals("energy"))) continue;
            double v = value(p, keys[i], elapsed, sleeping);
            if (v < lowest) { lowest = v; action = labels[i]; }
        }
        return action;
    }
    static void check(Context c) {
        if (foreground || !enabled(c) || !allowed(c)) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 9 || hour >= 21) return;
        long now = System.currentTimeMillis();
        long lastNotice = prefs(c).getLong("lastNotice", 0);
        if (lastNotice > 0 && now - lastNotice < SIX_HOURS) return;
        try {
            JSONObject saved = new JSONObject(prefs(c).getString("snapshot", "{}"));
            JSONArray pets = saved.getJSONArray("pets");
            double minutes = Math.max(0, Math.min(1440, (now - saved.getLong("last")) / 60000.0));
            // Rotate after each reminder so the other pets also get attention.
            int previous = prefs(c).getInt("notifiedPet", -1);
            for (int offset = 1; offset <= pets.length(); offset++) {
                int i = (previous + offset) % pets.length();
                JSONObject pet = pets.getJSONObject(i);
                String action = need(pet, minutes);
                if (action == null) continue;
                String name = pet.optString("name", "Tu mascota");
                if (name.length() > 18) name = name.substring(0, 18);
                String message = name + " necesita " + action + ". " + (pet.optBoolean("sleeping", false) ? "Despiértala para cuidarla." : "Ven a cuidarla.");
                if (post(c, "Tu mascota te necesita", message, i, NOTICE_ID))
                    prefs(c).edit().putLong("lastNotice", now).putInt("notifiedPet", i).apply();
                return;
            }
            c.getSystemService(NotificationManager.class).cancel(NOTICE_ID);
        } catch (Exception ignored) { /* Wait for a valid local snapshot. */ }
    }
    static boolean test(Context c) {
        return enabled(c) && post(c, "Mundo Mascotas", "¡Los avisos están listos! Toca para visitar a tu mascota.", 0, TEST_ID);
    }
    private static boolean post(Context c, String title, String message, int pet, int id) {
        if (!allowed(c)) return false;
        createChannel(c);
        Intent open = new Intent(c, MainActivity.class).putExtra("petIndex", pet)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification notification = builder.setSmallIcon(R.drawable.ic_pet_notification)
                .setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pending).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_DEFAULT).build();
        try { c.getSystemService(NotificationManager.class).notify(id, notification); return true; }
        catch (SecurityException denied) { return false; }
    }
}
