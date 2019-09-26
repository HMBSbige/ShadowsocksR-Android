package com.github.shadowsocks;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;

import com.github.shadowsocks.database.DBHelper;

public class ShadowsocksBackupAgent extends BackupAgentHelper {

    /**
     * The names of the SharedPreferences groups that the application maintains.  These
     * are the same strings that are passed to getSharedPreferences(String, int).
     */
    private static final String PREFS_DISPLAY = "com.github.shadowsocks_preferences";

    /**
     * An arbitrary string used within the BackupAgentHelper implementation to
     * identify the SharedPreferencesBackupHelper's data.
     */
    private static final String MY_PREFS_BACKUP_KEY = "com.github.shadowsocks";

    private static final String DATABASE = "com.github.shadowsocks.database.profile";

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, PREFS_DISPLAY);
        addHelper(MY_PREFS_BACKUP_KEY, helper);
        addHelper(DATABASE, new FileBackupHelper(this, "../databases/" + DBHelper.PROFILE));
    }
}
