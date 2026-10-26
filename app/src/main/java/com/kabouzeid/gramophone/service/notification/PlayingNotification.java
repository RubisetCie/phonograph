package com.kabouzeid.gramophone.service.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.service.MusicService;

import static android.content.Context.NOTIFICATION_SERVICE;

public abstract class PlayingNotification {

    private static final int NOTIFICATION_ID = 1;
    static final String NOTIFICATION_CHANNEL_ID = "playing_notification";

    private static final int NOTIFY_MODE_FOREGROUND = 1;
    private static final int NOTIFY_MODE_BACKGROUND = 0;

    private int notifyMode = NOTIFY_MODE_BACKGROUND;

    private NotificationManager notificationManager;
    protected MusicService service;
    boolean stopped;
    private Notification lastNotification;

    public synchronized void init(MusicService service) {
        this.service = service;
        notificationManager = (NotificationManager) service.getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }

    public abstract void update();

    public synchronized void stop() {
        stopped = true;
        service.stopForeground(true);
        notificationManager.cancel(NOTIFICATION_ID);
        notifyMode = NOTIFY_MODE_BACKGROUND;
        lastNotification = null;
    }

    /**
     * A service started with startForegroundService() (widgets, media buttons) gets killed by Android
     * unless it calls startForeground() promptly, even if the command doesn't end up starting playback.
     */
    public synchronized void ensureForeground() {
        if (notifyMode == NOTIFY_MODE_FOREGROUND) return;
        Notification notification = lastNotification;
        if (notification == null) {
            notification = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(service.getString(R.string.app_name))
                    .build();
        }
        service.startForeground(NOTIFICATION_ID, notification);
        notifyMode = NOTIFY_MODE_FOREGROUND;
    }

    /**
     * Leaves the foreground state entered by {@link #ensureForeground()} if nothing is playing.
     */
    public synchronized void leaveForegroundIfNotPlaying() {
        if (notifyMode == NOTIFY_MODE_FOREGROUND && !service.isPlaying()) {
            service.stopForeground(lastNotification == null);
            notifyMode = NOTIFY_MODE_BACKGROUND;
        }
    }

    void updateNotifyModeAndPostNotification(Notification notification) {
        lastNotification = notification;
        int newNotifyMode;
        if (service.isPlaying()) {
            newNotifyMode = NOTIFY_MODE_FOREGROUND;
        } else {
            newNotifyMode = NOTIFY_MODE_BACKGROUND;
        }

        if (notifyMode != newNotifyMode && newNotifyMode == NOTIFY_MODE_BACKGROUND) {
            service.stopForeground(false);
        }

        if (newNotifyMode == NOTIFY_MODE_FOREGROUND) {
            service.startForeground(NOTIFICATION_ID, notification);
        } else if (newNotifyMode == NOTIFY_MODE_BACKGROUND) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }

        notifyMode = newNotifyMode;
    }

    @RequiresApi(26)
    private void createNotificationChannel() {
        NotificationChannel notificationChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
        if (notificationChannel == null) {
            notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, service.getString(R.string.playing_notification_name), NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription(service.getString(R.string.playing_notification_description));
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);

            notificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
