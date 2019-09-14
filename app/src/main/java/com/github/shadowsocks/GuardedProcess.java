package com.github.shadowsocks;
/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

import android.util.Log;

import com.github.shadowsocks.utils.VayLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.System;
import java.util.List;
import java.util.concurrent.Semaphore;

public class GuardedProcess {

    private static final String TAG = GuardedProcess.class.getSimpleName();
    private final List<String> cmd;

    private Thread guardThread;
    private boolean isDestroyed;
    private Process process;
    private boolean isRestart = false;

    public GuardedProcess(List<String> cmd) {
        this.cmd = cmd;
    }

    public GuardedProcess start() throws InterruptedException {
        return start(null);
    }

    public GuardedProcess start(final RestartCallback onRestartCallback) throws InterruptedException {
        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        guardThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    RestartCallback callback = null;
                    while (!isDestroyed) {
                        VayLog.i(TAG, "start process: " + cmd);
                        long startTime = System.currentTimeMillis();

                        process = new ProcessBuilder(cmd).redirectErrorStream(true).start();

                        InputStream is = process.getInputStream();
                        new StreamLogger(is, TAG).start();

                        if (callback == null) {
                            callback = onRestartCallback;
                        } else {
                            callback.onRestart();
                        }

                        semaphore.release();
                        process.waitFor();

                        synchronized (this) {
                            if (isRestart) {
                                isRestart = false;
                            } else {
                                if (System.currentTimeMillis() - startTime < 1000) {
                                    Log.w(TAG, "process exit too fast, stop guard: " + cmd);
                                    isDestroyed = true;
                                }
                            }
                        }

                    }
                } catch (Exception ignored) {
                    VayLog.i(TAG, "thread interrupt, destroy process: " + cmd);
                    process.destroy();
                } finally {
                    semaphore.release();
                }
            }
        }, "GuardThread-" + cmd);

        guardThread.start();
        semaphore.acquire();
        return this;
    }

    public void destroy() {
        isDestroyed = true;
        guardThread.interrupt();
        process.destroy();
        try {
            guardThread.join();
        } catch (InterruptedException e) {
            // Ignored
        }
    }

    public void restart() {
        synchronized (this) {
            isRestart = true;
            process.destroy();
        }
    }

    public int waitFor() throws InterruptedException {
        guardThread.join();
        return 0;
    }

    /**
     * restart callback
     */
    public interface RestartCallback {

        /**
         *  restart callback
         */
        void onRestart();
    }

    public class StreamLogger extends Thread {

        private InputStream is;
        private String tag;

        public StreamLogger(InputStream is, String tag) {
            this.is = is;
            this.tag = tag;
        }

        @Override
        public void run() {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(is));
                String temp = null;
                while ((temp = bufferedReader.readLine()) != null) {
                    VayLog.e(tag, temp);
                }
            } catch (Exception e) {
                int i = 0;
                // Ignore
            } finally {
                try {
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}
