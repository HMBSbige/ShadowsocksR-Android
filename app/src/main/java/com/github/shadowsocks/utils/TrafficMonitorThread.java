package com.github.shadowsocks.utils;
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

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import androidx.annotation.NonNull;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class TrafficMonitorThread extends Thread {

    private static final String TAG = TrafficMonitorThread.class.getSimpleName();

    private final String PATH;
    private ScheduledExecutorService pool;
    private LocalServerSocket serverSocket;
    private boolean isRunning = true;

    public TrafficMonitorThread(Context context) {
        PATH = context.getApplicationInfo().dataDir + File.separator + "stat_path";
        pool = new ScheduledThreadPoolExecutor(3, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(TAG);
                return thread;
            }
        });
    }

    public void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                // ignore
            }
            serverSocket = null;
        }
    }

    public void stopThread() {
        isRunning = false;
        closeServerSocket();
    }

    @Override
    public void run() {
        boolean deleteResult = new File(PATH).delete();
        VayLog.d(TAG, "run() delete file = " + deleteResult);

        if (!initServerSocket()) {
            return;
        }

        while (isRunning) {
            try {
                final LocalSocket socket = serverSocket.accept();
                // handle socket
                handleLocalSocket(socket);
            } catch (Exception e) {
                VayLog.e(TAG, "Error when accept socket", e);
                app.track(e);

                initServerSocket();
            }
        }
    }

    /**
     * handle local socket
     *
     * @param socket local socket object
     */
    private void handleLocalSocket(final LocalSocket socket) {
        pool.execute(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                OutputStream output = null;
                try {
                    input = socket.getInputStream();
                    output = socket.getOutputStream();

                    byte[] buffer = new byte[16];
                    if (input.read(buffer) != 16) {
                        throw new IOException("Unexpected traffic stat length");
                    }
                    ByteBuffer stat = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
                    TrafficMonitor.update(stat.getLong(0), stat.getLong(8));
                    output.write(0);
                    output.flush();

                    // close stream
                    IOUtils.close(input);
                    IOUtils.close(output);
                } catch (Exception e) {
                    VayLog.e(TAG, "handleLocalSocket() Error when recv traffic stat", e);
                    app.track(e);
                } finally {
                    // close socket
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        });
    }

    /**
     * init server socket
     *
     * @return init failed return false.
     */
    private boolean initServerSocket() {
        // if not running, do not init
        if (!isRunning) {
            return false;
        }

        try {
            LocalSocket localSocket = new LocalSocket();
            localSocket.bind(new LocalSocketAddress(PATH, LocalSocketAddress.Namespace.FILESYSTEM));
            serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
            return true;
        } catch (IOException e) {
            VayLog.e(TAG, "unable to bind", e);
            return false;
        }
    }
}
