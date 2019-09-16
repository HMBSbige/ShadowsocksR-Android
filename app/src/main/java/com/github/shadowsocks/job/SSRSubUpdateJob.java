package com.github.shadowsocks.job;


import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import com.github.shadowsocks.R;
import com.github.shadowsocks.database.SSRSub;
import com.github.shadowsocks.network.ssrsub.SubUpdateCallback;
import com.github.shadowsocks.network.ssrsub.SubUpdateHelper;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.VayLog;
import com.github.shadowsocks.ShadowsocksApplication;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

/**
 * @author Mygod
 */
public class SSRSubUpdateJob extends Job {

    public static final String TAG = SSRSubUpdateJob.class.getSimpleName();

    public static int schedule() {
        return new JobRequest.Builder(SSRSubUpdateJob.TAG)
                .setPeriodic(TimeUnit.HOURS.toMillis(1))
                .setRequirementsEnforced(true)
                .setRequiresCharging(false)
                .setUpdateCurrent(true)
                .build().schedule();
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        if (ShadowsocksApplication.app.settings.getInt(Constants.Key.ssrsub_autoupdate, 0) == 1) {
            List<SSRSub> subs = ShadowsocksApplication.app.ssrsubManager.getAllSSRSubs();
            SubUpdateHelper.Companion.instance().updateSub(subs,  new SubUpdateCallback() {
                @Override
                public void onSuccess(String subname) {
                    VayLog.d(TAG, "onRunJob() update sub success!");
                    ToastUtils.showShort(getContext().getString(R.string.sub_autoupdate_success, subname));
                    Log.i("sub", subname);
                }

                @Override
                public void onFailed() {
                    VayLog.e(TAG, "onRunJob() update sub failed!");
                }
            });
            return Result.SUCCESS;
        } else {
            return Result.RESCHEDULE;
        }
    }
}
