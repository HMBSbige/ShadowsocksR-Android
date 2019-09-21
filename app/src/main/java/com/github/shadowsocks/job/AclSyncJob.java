package com.github.shadowsocks.job;

import androidx.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;
import com.github.shadowsocks.ShadowsocksApplication;
import com.github.shadowsocks.utils.IOUtils;
import com.github.shadowsocks.utils.VayLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * @author Mygod
 */
public class AclSyncJob extends Job {

    public static final String TAG = AclSyncJob.class.getSimpleName();

    private final String route;

    public AclSyncJob(String route) {
        this.route = route;
    }

    public static int schedule(String route) {
        return new JobRequest.Builder(AclSyncJob.TAG + ':' + route)
                .setExecutionWindow(1, TimeUnit.DAYS.toMillis(28))
                .setRequirementsEnforced(true)
                .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .setUpdateCurrent(true)
                .build().schedule();
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        String filename = route + ".acl";
        InputStream is = null;
        try {
            if ("self".equals(route)) {
                // noinspection JavaAccessorMethodCalledAsEmptyParen
                is = new URL("https://raw.githubusercontent.com/HMBSbige/Text_Translation/master/ShadowsocksR/" + filename).openConnection().getInputStream();
                IOUtils.writeString(ShadowsocksApplication.app.getApplicationInfo().dataDir + '/' + filename, IOUtils.readString(is));
            }
            return Result.SUCCESS;
        } catch (IOException e) {
            VayLog.e(TAG, "onRunJob", e);
            ShadowsocksApplication.app.track(e);
            return Result.RESCHEDULE;
        } catch (Exception e) {
            // unknown failures, probably shouldn't retry
            VayLog.e(TAG, "onRunJob", e);
            ShadowsocksApplication.app.track(e);
            return Result.FAILURE;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                VayLog.e(TAG, "onRunJob", e);
                ShadowsocksApplication.app.track(e);
            }
        }
    }
}
