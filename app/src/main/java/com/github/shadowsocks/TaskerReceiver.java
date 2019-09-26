package com.github.shadowsocks;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.TaskerSettings;
import com.github.shadowsocks.utils.Utils;

/**
 * @author CzBiX
 */
public class TaskerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TaskerSettings settings = TaskerSettings.fromIntent(intent);
        Profile profile = ShadowsocksApplication.app.profileManager.getProfile(settings.profileId);

        if (profile != null) {
            ShadowsocksApplication.app.switchProfile(settings.profileId);
        }

        if (settings.switchOn) {
            Utils.INSTANCE.startSsService(context);
        } else {
            Utils.INSTANCE.stopSsService(context);
        }
    }
}
