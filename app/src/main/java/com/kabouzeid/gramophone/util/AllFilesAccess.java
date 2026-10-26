package com.kabouzeid.gramophone.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.afollestad.materialdialogs.MaterialDialog;
import com.kabouzeid.gramophone.R;

import java.lang.ref.WeakReference;

/**
 * Since Android 11 an app may only modify the media files and playlists it created itself,
 * unless the user grants it "All files access". This asks for it and runs the action that
 * needed it once the user comes back from the settings screen.
 */
public final class AllFilesAccess {

    @Nullable
    private static Runnable pendingAction;
    @Nullable
    private static WeakReference<Activity> pendingActivity;

    private AllFilesAccess() {
    }

    public static boolean isGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    /**
     * Runs the action right away if access is granted, otherwise asks for it first.
     */
    public static void runWithAccess(@NonNull Context context, @StringRes int reason, @NonNull Runnable action) {
        if (isGranted()) {
            action.run();
        } else {
            request(context, reason, action);
        }
    }

    /**
     * @return false if there was no activity to show the request from
     */
    public static boolean request(@NonNull Context context, @StringRes int reason, @Nullable Runnable action) {
        final Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing()) return false;
        new MaterialDialog.Builder(activity)
                .title(R.string.all_files_access_title)
                .content(reason)
                .positiveText(R.string.action_grant)
                .negativeText(android.R.string.cancel)
                .onPositive((dialog, which) -> {
                    pendingAction = action;
                    pendingActivity = new WeakReference<>(activity);
                    openSettings(activity);
                })
                .show();
        return true;
    }

    /**
     * To be called from every activity's onResume().
     */
    public static void onActivityResumed(@NonNull Activity activity) {
        if (pendingActivity == null || pendingActivity.get() != activity) return;
        final Runnable action = pendingAction;
        pendingAction = null;
        pendingActivity = null;
        if (action != null && isGranted()) {
            action.run();
        }
    }

    private static void openSettings(@NonNull Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (ActivityNotFoundException e) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
