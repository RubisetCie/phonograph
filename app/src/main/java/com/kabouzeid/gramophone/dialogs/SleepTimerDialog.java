package com.kabouzeid.gramophone.dialogs;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.ThemeSingleton;
import com.kabouzeid.gramophone.App;
import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.helper.MusicPlayerRemote;
import com.kabouzeid.gramophone.service.MusicService;
import com.kabouzeid.gramophone.util.MusicUtil;
import com.kabouzeid.gramophone.util.PreferenceUtil;
import com.triggertrap.seekarc.SeekArc;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class SleepTimerDialog extends DialogFragment {
    @BindView(R.id.seek_arc)
    SeekArc seekArc;
    @BindView(R.id.timer_display)
    TextView timerDisplay;
    @BindView(R.id.should_finish_last_song)
    CheckBox shouldFinishLastSong;

    private int seekArcProgress;
    private MaterialDialog materialDialog;
    private TimerUpdater timerUpdater;

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        timerUpdater.cancel();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        timerUpdater = new TimerUpdater();
        materialDialog = new MaterialDialog.Builder(getActivity())
                .title(getActivity().getResources().getString(R.string.action_sleep_timer))
                .positiveText(R.string.action_set)
                .onPositive((dialog, which) -> {
                    if (getActivity() == null) {
                        return;
                    }

                    PreferenceUtil.getInstance(getActivity()).setSleepTimerFinishMusic(shouldFinishLastSong.isChecked());

                    final int minutes = seekArcProgress;

                    cancelTimer();
                    PendingIntent pi = makeTimerPendingIntent(shouldFinishLastSong.isChecked(), PendingIntent.FLAG_CANCEL_CURRENT);

                    final long nextSleepTimerElapsedTime = SystemClock.elapsedRealtime() + minutes * 60 * 1000;
                    PreferenceUtil.getInstance(getActivity()).setNextSleepTimerElapsedRealtime(nextSleepTimerElapsedTime);
                    AlarmManager am = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
                    // set() is inexact and may fire long after the chosen time, especially in Doze
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextSleepTimerElapsedTime, pi);
                    } else {
                        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextSleepTimerElapsedTime, pi);
                    }

                    Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sleep_timer_set, minutes), Toast.LENGTH_SHORT).show();
                })
                .onNeutral((dialog, which) -> {
                    if (getActivity() == null) {
                        return;
                    }
                    if (cancelTimer()) {
                        Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sleep_timer_canceled), Toast.LENGTH_SHORT).show();
                    }

                    MusicService musicService = MusicPlayerRemote.musicService;
                    if (musicService != null && musicService.pendingQuit) {
                        musicService.pendingQuit = false;
                        Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sleep_timer_canceled), Toast.LENGTH_SHORT).show();
                    }
                })
                .showListener(dialog -> {
                    if (makeTimerPendingIntent(false, PendingIntent.FLAG_NO_CREATE) != null
                            || makeTimerPendingIntent(true, PendingIntent.FLAG_NO_CREATE) != null) {
                        timerUpdater.start();
                    }
                })
                .customView(R.layout.dialog_sleep_timer, false)
                .build();

        if (getActivity() == null || materialDialog.getCustomView() == null) {
            return materialDialog;
        }

        ButterKnife.bind(this, materialDialog.getCustomView());

        boolean finishMusic = PreferenceUtil.getInstance(getActivity()).getSleepTimerFinishMusic();
        shouldFinishLastSong.setChecked(finishMusic);

        seekArc.setProgressColor(ThemeSingleton.get().positiveColor.getDefaultColor());
        seekArc.setThumbColor(ThemeSingleton.get().positiveColor.getDefaultColor());

        seekArc.post(() -> {
            int width = seekArc.getWidth();
            int height = seekArc.getHeight();
            int small = Math.min(width, height);

            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(seekArc.getLayoutParams());
            layoutParams.height = small;
            seekArc.setLayoutParams(layoutParams);
        });

        seekArcProgress = PreferenceUtil.getInstance(getActivity()).getLastSleepTimerValue();
        updateTimeDisplayTime();
        seekArc.setProgress(seekArcProgress);

        seekArc.setOnSeekArcChangeListener(new SeekArc.OnSeekArcChangeListener() {
            @Override
            public void onProgressChanged(@NonNull SeekArc seekArc, int i, boolean b) {
                if (i < 1) {
                    seekArc.setProgress(1);
                    return;
                }
                seekArcProgress = i;
                updateTimeDisplayTime();
            }

            @Override
            public void onStartTrackingTouch(SeekArc seekArc) {

            }

            @Override
            public void onStopTrackingTouch(SeekArc seekArc) {
                PreferenceUtil.getInstance(getActivity()).setLastSleepTimerValue(seekArcProgress);
            }
        });

        return materialDialog;
    }

    private void updateTimeDisplayTime() {
        timerDisplay.setText(seekArcProgress + " min");
    }

    /**
     * Cancels the scheduled timer of either kind, the checkbox may have been toggled since it was set.
     *
     * @return whether a timer was scheduled
     */
    private boolean cancelTimer() {
        boolean canceled = false;
        AlarmManager am = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
        for (boolean finishLastSong : new boolean[]{false, true}) {
            final PendingIntent previous = makeTimerPendingIntent(finishLastSong, PendingIntent.FLAG_NO_CREATE);
            if (previous != null) {
                am.cancel(previous);
                previous.cancel();
                canceled = true;
            }
        }
        return canceled;
    }

    private PendingIntent makeTimerPendingIntent(boolean finishLastSong, int flag) {
        return PendingIntent.getService(getActivity(), 0, makeTimerIntent(finishLastSong), flag);
    }

    private Intent makeTimerIntent(boolean finishLastSong) {
        Intent intent = new Intent(getActivity(), MusicService.class);
        if (finishLastSong) {
            return intent.setAction(MusicService.ACTION_PENDING_QUIT);
        }
        return intent.setAction(MusicService.ACTION_QUIT);
    }

    private void updateCancelButton() {
        MusicService musicService = MusicPlayerRemote.musicService;
        if (musicService != null && musicService.pendingQuit) {
            materialDialog.setActionButton(DialogAction.NEUTRAL, materialDialog.getContext().getString(R.string.cancel_current_timer));
        } else {
            materialDialog.setActionButton(DialogAction.NEUTRAL, null);
        }
    }

    private class TimerUpdater extends CountDownTimer {
        public TimerUpdater() {
            super(PreferenceUtil.getInstance(getActivity()).getNextSleepTimerElapsedRealTime() - SystemClock.elapsedRealtime(), 1000);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            materialDialog.setActionButton(DialogAction.NEUTRAL, materialDialog.getContext().getString(R.string.cancel_current_timer) + " (" + MusicUtil.getReadableDurationString(millisUntilFinished) + ")");
        }

        @Override
        public void onFinish() {
            updateCancelButton();
        }
    }
}
