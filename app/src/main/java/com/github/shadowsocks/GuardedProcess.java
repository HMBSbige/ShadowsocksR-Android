package com.github.shadowsocks;

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
                    if (process!=null) {
                        process.destroy();
                    }
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
        if (process!=null){
            process.destroy();
        }
        try {
            guardThread.join();
        } catch (InterruptedException e) {
            // Ignored
        }
    }

    public void restart() {
        synchronized (this) {
            isRestart = true;
            if (process!=null){
                process.destroy();
            }
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
