package com.github.shadowsocks;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.Utils;

/**
 * @author Mygod
 */
public class QuickToggleShortcut extends Activity {

    private ServiceBoundContext mServiceBoundContext;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mServiceBoundContext = new ServiceBoundContext(newBase) {
            @Override
            protected void onServiceConnected() {
                try {
                    int state = bgService.getState();
                    switch (state) {
                        case Constants.State.STOPPED:
                            ToastUtils.showShort(R.string.loading);
                            Utils.INSTANCE.startSsService(this);
                            break;
                        case Constants.State.CONNECTED:
                            Utils.INSTANCE.stopSsService(this);
                            break;
                        default:
                            // ignore
                            break;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                finish();
            }
        };
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();

        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            setResult(Activity.RESULT_OK, new Intent()
                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, QuickToggleShortcut.class))
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.quick_toggle))
                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)));
            finish();
        } else {
            mServiceBoundContext.attachService();
            if (Build.VERSION.SDK_INT >= 25) {
                ShortcutManager service = getSystemService(ShortcutManager.class);
                if (service != null) {
                    service.reportShortcutUsed("toggle");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        mServiceBoundContext.detachService();
        super.onDestroy();
    }
}
