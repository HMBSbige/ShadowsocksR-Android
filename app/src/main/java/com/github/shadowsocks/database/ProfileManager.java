package com.github.shadowsocks.database;


import com.github.shadowsocks.ShadowsocksApplication;
import com.github.shadowsocks.utils.VayLog;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProfileManager {

    private static final String TAG = ProfileManager.class.getSimpleName();
    private final DBHelper dbHelper;

    private List<ProfileAddedListener> mProfileAddedListeners;

    public ProfileManager(DBHelper helper) {
        this.dbHelper = helper;
        this.mProfileAddedListeners = new ArrayList<>(20);
    }

    /**
     * create profile
     */
    public Profile createProfile() {
        return createProfile(null);
    }

    /**
     * create profile
     */
    public Profile createProfile(Profile p) {
        Profile profile;
        if (p == null) {
            profile = new Profile();
        } else {
            profile = p;
        }
        profile.id = 0;
        Profile oldProfile = ShadowsocksApplication.app.currentProfile();
        if (oldProfile != null) {
            // Copy Feature Settings from old profile
            profile.route = oldProfile.route;
            profile.ipv6 = oldProfile.ipv6;
            profile.proxyApps = oldProfile.proxyApps;
            profile.bypass = oldProfile.bypass;
            profile.individual = oldProfile.individual;
            profile.udpdns = oldProfile.udpdns;
        }
        try {
            String[] last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)")
                    .prepareStatementString()).getFirstResult();
            // set user order
            if (last != null && last.length == 1 && last[0] != null) {
                profile.userOrder = Integer.parseInt(last[0]) + 1;
            }
            // create or update
            dbHelper.profileDao.createOrUpdate(profile);
            invokeProfileAdded(profile);
        } catch (SQLException e) {
            VayLog.e(TAG, "createProfile", e);
            ShadowsocksApplication.app.track(e);
        }
        return profile;
    }

    /**
     * create profile dr
     *
     * @param p profile
     */
    public Profile createProfileDr(Profile p) {
        Profile profile;
        if (p == null) {
            profile = new Profile();
        } else {
            profile = p;
        }
        profile.id = 0;
        Profile oldProfile = ShadowsocksApplication.app.currentProfile();
        if (oldProfile != null) {
            // Copy Feature Settings from old profile
            profile.route = oldProfile.route;
            profile.ipv6 = oldProfile.ipv6;
            profile.proxyApps = oldProfile.proxyApps;
            profile.bypass = oldProfile.bypass;
            profile.individual = oldProfile.individual;
            profile.udpdns = oldProfile.udpdns;
            profile.dns = oldProfile.dns;
            profile.china_dns = oldProfile.china_dns;
        }

        try {
            String[] last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)")
                    .prepareStatementString()).getFirstResult();
            if (last != null && last.length == 1 && last[0] != null) {
                profile.userOrder = Integer.parseInt(last[0]) + 1;
            }

            Profile last_exist = dbHelper.profileDao.queryBuilder()
                    .where().eq("name", profile.name)
                    .and().eq("host", profile.host)
                    .and().eq("remotePort", profile.remotePort)
                    .and().eq("password", profile.password)
                    .and().eq("protocol", profile.protocol)
                    .and().eq("protocol_param", profile.protocol_param)
                    .and().eq("obfs", profile.obfs)
                    .and().eq("obfs_param", profile.obfs_param)
                    .and().eq("url_group", profile.url_group)
                    .and().eq("method", profile.method).queryForFirst();
            if (last_exist == null) {
                dbHelper.profileDao.createOrUpdate(profile);
                invokeProfileAdded(profile);
            }
        } catch (SQLException e) {
            VayLog.e(TAG, "createProfileDr", e);
            ShadowsocksApplication.app.track(e);
        }
        return profile;
    }

    /**
     * create profile sub
     *
     * @return create failed return 0, create success return id.
     */
    public int createProfileSub(Profile p) {
        Profile profile;
        if (p == null) {
            profile = new Profile();
        } else {
            profile = p;
        }
        profile.id = 0;
        Profile oldProfile = ShadowsocksApplication.app.currentProfile();
        if (oldProfile != null) {
            // Copy Feature Settings from old profile
            profile.route = oldProfile.route;
            profile.ipv6 = oldProfile.ipv6;
            profile.proxyApps = oldProfile.proxyApps;
            profile.bypass = oldProfile.bypass;
            profile.individual = oldProfile.individual;
            profile.udpdns = oldProfile.udpdns;
            profile.dns = oldProfile.dns;
            profile.china_dns = oldProfile.china_dns;
        }

        try {
            String[] last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)")
                    .prepareStatementString()).getFirstResult();
            if (last != null && last.length == 1 && last[0] != null) {
                profile.userOrder = Integer.parseInt(last[0]) + 1;
            }

            Profile last_exist = dbHelper.profileDao.queryBuilder()
                    .where().eq("name", profile.name)
                    .and().eq("host", profile.host)
                    .and().eq("remotePort", profile.remotePort)
                    .and().eq("password", profile.password)
                    .and().eq("protocol", profile.protocol)
                    .and().eq("protocol_param", profile.protocol_param)
                    .and().eq("obfs", profile.obfs)
                    .and().eq("obfs_param", profile.obfs_param)
                    .and().eq("url_group", profile.url_group)
                    .and().eq("method", profile.method).queryForFirst();
            if (last_exist == null) {
                dbHelper.profileDao.createOrUpdate(profile);
                return 0;
            } else {
                return last_exist.id;
            }
        } catch (SQLException e) {
            VayLog.e(TAG, "createProfileSub", e);
            ShadowsocksApplication.app.track(e);
            return 0;
        }
    }

    /**
     * update profile
     */
    public boolean updateProfile(Profile profile) {
        try {
            dbHelper.profileDao.update(profile);
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "updateProfile", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    /**
     * update all profile by string
     *
     * @param key   profile key
     * @param value profile value
     * @return update failed return false.
     */
    public boolean updateAllProfileByString(String key, String value) {
        try {
            dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET " + key + " = '" + value + "';");
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "updateAllProfileByString", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    /**
     * update all profile by boolean
     *
     * @param key   profile key
     * @param value profile value
     * @return update failed return false.
     */
    public boolean updateAllProfileByBoolean(String key, boolean value) {
        try {
            if (value) {
                dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET " + key + " = '1';");
            } else {
                dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET " + key + " = '0';");
            }
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "updateAllProfileByBoolean", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    /**
     * get profile by id
     *
     * @param id profile id
     */
    public Profile getProfile(int id) {
        try {
            return dbHelper.profileDao.queryForId(id);
        } catch (Exception e) {
            VayLog.e(TAG, "getProfile", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    /**
     * del profile by id
     *
     * @param id profile id
     * @return del failed return false.
     */
    public boolean delProfile(int id) {
        try {
            dbHelper.profileDao.deleteById(id);
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "delProfile", e);
            ShadowsocksApplication.app.track(e);
            return false;
        }
    }

    /**
     * get first profile
     *
     * @return get failed return null.
     */
    public Profile getFirstProfile() {
        try {
            List<Profile> result = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().limit(1L).prepare());
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            }
            // list is empty, return null;
            return null;
        } catch (Exception e) {
            VayLog.e(TAG, "getFirstProfile", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    /**
     * get all profiles
     *
     * @return get failed return null.
     */
    public List<Profile> getAllProfiles() {
        try {
            return dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("url_group", true).orderBy("name", true).prepare());
        } catch (Exception e) {
            VayLog.e(TAG, "getAllProfiles", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    /**
     * get all profiles by group
     *
     * @param group group name
     * @return get failed return null.
     */
    public List<Profile> getAllProfilesByGroup(String group) {
        try {
            return dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("name", true).where().like("url_group", group + "%").prepare());
        } catch (Exception e) {
            VayLog.e(TAG, "getAllProfilesByGroup", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    /**
     * get all profiles by elapsed
     *
     * @return get failed return null.
     */
    public List<Profile> getAllProfilesByElapsed() {
        try {
            List<Profile> notlist = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().not().eq("elapsed", 0).prepare());
            List<Profile> eqList = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("elapsed", 0).prepare());

            // merge list
            List<Profile> result = new ArrayList<>();
            result.addAll(notlist);
            result.addAll(eqList);
            return result;
        } catch (Exception e) {
            VayLog.e(TAG, "getAllProfilesByElapsed", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    public List<Profile> getAllProfilesByGroupOrderbyElapse(String groupname) {
        try {
            List<Profile> notlist = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("url_group", groupname).and().not().eq("elapsed", 0).prepare());
            List<Profile> eqList = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("url_group", groupname).and().eq("elapsed", 0).prepare());

            // merge list
            List<Profile> result = new ArrayList<>();
            result.addAll(notlist);
            result.addAll(eqList);
            return result;
        } catch (Exception e) {
            VayLog.e(TAG, "getAllProfilesByElapsed", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }


    /**
     * create default profile
     */
    public Profile createDefault() {
        Profile profile = new Profile();
        profile.name = "ShadowsocksR";
        profile.host = "1.1.1.1";
        profile.remotePort = 80;
        profile.password = "androidssr";
        profile.protocol = "auth_chain_a";
        profile.obfs = "http_simple";
        profile.method = "none";
        profile.url_group = "ShadowsocksR";
        return createProfile(profile);
    }

    /**
     * add profile added listener
     *
     * @param l listener callback
     */
    public void addProfileAddedListener(ProfileAddedListener l) {
        if (mProfileAddedListeners == null) {
            return;
        }

        // adding listener
        if (!mProfileAddedListeners.contains(l)) {
            mProfileAddedListeners.add(l);
        }
    }

    /**
     * remove profile added listener
     *
     * @param l listener callback
     */
    public void removeProfileAddedListener(ProfileAddedListener l) {
        if (mProfileAddedListeners == null || mProfileAddedListeners.isEmpty()) {
            return;
        }

        // remove listener
        mProfileAddedListeners.remove(l);
    }

    /**
     * invoke profile added listener
     *
     * @param profile profile param
     */
    private void invokeProfileAdded(Profile profile) {
        if (mProfileAddedListeners == null || mProfileAddedListeners.isEmpty()) {
            return;
        }

        // iteration invoke listener
        for (ProfileAddedListener l : mProfileAddedListeners) {
            if (l != null) {
                l.onProfileAdded(profile);
            }
        }
    }

    public List<String> getGroupNames() {
        try {
            List<Profile> groupdistinktprofile = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().selectColumns("url_group").distinct().prepare());
            List<String> groupnames = new ArrayList<String>();
            for (Profile profile : groupdistinktprofile) {
                groupnames.add(profile.url_group);
            }
            return groupnames;
        } catch (Exception e) {
            VayLog.e(TAG, "getAllProfilesByGroup", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }

    /**
     * pro file added listener
     */
    public interface ProfileAddedListener {

        /**
         * profile added
         *
         * @param profile profile object
         */
        void onProfileAdded(Profile profile);
    }


}
