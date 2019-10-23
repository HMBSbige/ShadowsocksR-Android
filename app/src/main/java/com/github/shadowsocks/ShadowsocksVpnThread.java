package com.github.shadowsocks;


import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import androidx.annotation.NonNull;

import com.github.shadowsocks.utils.IOUtils;
import com.github.shadowsocks.utils.VayLog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

public class ShadowsocksVpnThread extends Thread {

    private static final String TAG = ShadowsocksVpnThread.class.getSimpleName();

    private static final String PATH = BaseVpnService.Companion.getProtectPath();
    private final ShadowsocksVpnService vpnService;

    private boolean isRunning = true;
    private LocalServerSocket serverSocket;
    private ScheduledExecutorService pool;

    public ShadowsocksVpnThread(ShadowsocksVpnService vpnService) {
        this.vpnService = vpnService;
        pool = new ScheduledThreadPoolExecutor(4, new ThreadFactory() {
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
                // Ignore
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
        boolean deleteFlag = new File(PATH).delete();
        VayLog.INSTANCE.d(TAG, "run() delete file = " + deleteFlag);

        if (!initServerSocket()) {
            return;
        }

        while (isRunning) {
            try {
                final LocalSocket socket = serverSocket.accept();
                // handle local socket
                handleLocalSocket(socket);
            } catch (IOException e) {
                VayLog.INSTANCE.e(TAG, "Error when accept socket", e);
                ShadowsocksApplication.app.track(e);

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

                    // check read state
                    int state = input.read();
                    VayLog.INSTANCE.d(TAG, "handleLocalSocket() read state = " + state);

                    FileDescriptor[] fds = socket.getAncillaryFileDescriptors();

                    if (fds != null && fds.length > 0) {
                        Method getIntMethod = getInt();
                        int fd = 0;
                        if (getIntMethod != null) {
                            fd = (int) getInt().invoke(fds[0]);
                        }
                        boolean ret = vpnService.protect(fd);

                        // Trick to close file decriptor
                        System.INSTANCE.jniclose(fd);

                        if (ret) {
                            output.write(0);
                        } else {
                            output.write(1);
                        }
                    }

                    // close stream
                    IOUtils.INSTANCE.close(input);
                    IOUtils.INSTANCE.close(output);
                } catch (Exception e) {
                    VayLog.INSTANCE.e(TAG, "handleLocalSocket() Error when protect socket", e);
                    ShadowsocksApplication.app.track(e);
                } finally {
                    // close socket
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // Ignore;
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
            VayLog.INSTANCE.e(TAG, "unable to bind", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    private Method getInt() {
        try {
            return FileDescriptor.class.getDeclaredMethod("getInt$");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
