package com.github.shadowsocks.database;


import com.github.shadowsocks.ShadowsocksApplication;
import com.github.shadowsocks.utils.VayLog;

import java.util.ArrayList;
import java.util.List;

public class SSRSubManager {

    private static final String TAG = SSRSubManager.class.getSimpleName();
    private final DBHelper dbHelper;
    private List<SSRSubAddedListener> mSSRSubAddedListeners;

    public SSRSubManager(DBHelper helper) {
        this.dbHelper = helper;
        mSSRSubAddedListeners = new ArrayList<>(20);
    }

    public SSRSub createSSRSub(SSRSub p) {
        SSRSub ssrsub;
        if (p == null) {
            ssrsub = new SSRSub();
        } else {
            ssrsub = p;
        }
        ssrsub.id = 0;

        try {
            dbHelper.ssrsubDao.createOrUpdate(ssrsub);
            invokeSSRSubAdded(ssrsub);
        } catch (Exception e) {
            VayLog.e(TAG, "addSSRSub", e);
            ShadowsocksApplication.app.track(e);
        }
        return ssrsub;
    }

    public boolean updateSSRSub(SSRSub ssrsub) {
        try {
            dbHelper.ssrsubDao.update(ssrsub);
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "updateSSRSub", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    public SSRSub getSSRSub(int id) {
        try {
            return dbHelper.ssrsubDao.queryForId(id);
        } catch (Exception e) {
            VayLog.e(TAG, "getSSRSub", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    public boolean delSSRSub(int id) {
        try {
            dbHelper.ssrsubDao.deleteById(id);
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "delSSRSub", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    public SSRSub getFirstSSRSub() {
        try {
            List<SSRSub> result = dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().limit(1L).prepare());
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            } else {
                return null;
            }
        } catch (Exception e) {
            VayLog.e(TAG, "getAllSSRSubs", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    public List<SSRSub> getAllSSRSubs() {
        try {
            return dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().prepare());
        } catch (Exception e) {
            VayLog.e(TAG, "getAllSSRSubs", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    public SSRSub createDefault() {
        SSRSub ssrSub = new SSRSub();
        ssrSub.url = "https://raw.githubusercontent.com/HMBSbige/Text_Translation/master/ShadowsocksR/freenodeplain.txt";
        ssrSub.url_group = "FreeSSR-public";
        return createSSRSub(ssrSub);
    }

    /**
     * add ssr sub added listener
     *
     * @param l callback
     */
    public void addSSRSubAddedListener(SSRSubAddedListener l) {
        if (mSSRSubAddedListeners == null) {
            return;
        }

        // adding listener
        if (!mSSRSubAddedListeners.contains(l)) {
            mSSRSubAddedListeners.add(l);
        }
    }

    /**
     * remove ssr sub added listener
     *
     * @param l callback
     */
    public void removeSSRSubAddedListener(SSRSubAddedListener l) {
        if (mSSRSubAddedListeners == null || mSSRSubAddedListeners.isEmpty()) {
            return;
        }

        // remove listener
        mSSRSubAddedListeners.remove(l);
    }

    /**
     * invoke ssr sub added listener
     *
     * @param ssrSub ssr sub param
     */
    private void invokeSSRSubAdded(SSRSub ssrSub) {
        if (mSSRSubAddedListeners == null || mSSRSubAddedListeners.isEmpty()) {
            return;
        }

        // iteration invoke listener
        for (SSRSubAddedListener l : mSSRSubAddedListeners) {
            if (l != null) {
                l.onSSRSubAdded(ssrSub);
            }
        }
    }

    public interface SSRSubAddedListener {

        /**
         * ssr sub added
         *
         * @param ssrSub ssr sub object
         */
        void onSSRSubAdded(SSRSub ssrSub);
    }
}
