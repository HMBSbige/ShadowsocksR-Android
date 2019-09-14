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

import android.text.TextUtils;
import android.util.Base64;

import com.github.shadowsocks.database.Profile;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class Parser {

    public static final String TAG = Parser.class.getSimpleName();

    private static final String pattern_regex = "(?i)ss://([A-Za-z0-9+-/=_]+)(#(.+))?";
    private static final String decodedPattern_regex = "(?i)^((.+?)(-auth)??:(.*)@(.+?):(\\d+?))$";

    private static final String pattern_ssr_regex = "(?i)ssr://([A-Za-z0-9_=-]+)";
    private static final String decodedPattern_ssr_regex = "(?i)^((.+):(\\d+?):(.*):(.+):(.*):([^/]+))";
    private static final String decodedPattern_ssr_obfsparam_regex = "(?i)[?&]obfsparam=([A-Za-z0-9_=-]*)";
    private static final String decodedPattern_ssr_remarks_regex = "(?i)[?&]remarks=([A-Za-z0-9_=-]*)";
    private static final String decodedPattern_ssr_protocolparam_regex = "(?i)[?&]protoparam=([A-Za-z0-9_=-]*)";
    private static final String decodedPattern_ssr_groupparam_regex = "(?i)[?&]group=([A-Za-z0-9_=-]*)";

    private static Pattern getPattern(String regex) {
        return Pattern.compile(regex);
    }

    public static List<Profile> findAll(CharSequence data) {
        Pattern pattern = getPattern(pattern_regex);
        Pattern decodedPattern = getPattern(decodedPattern_regex);

        CharSequence input = null;
        if (!TextUtils.isEmpty(data)) {
            input = data;
        } else {
            input = "";
        }
        Matcher m = pattern.matcher(input);
        try {
            List<Profile> list = new ArrayList<>();
            while (m.find()) {
                Matcher ss = decodedPattern.matcher(new String(Base64.decode(m.group(1), Base64.NO_PADDING), "UTF-8"));
                if (ss.find()) {
                    Profile profile = new Profile();
                    profile.method = ss.group(2).toLowerCase();
                    if (ss.group(3) != null) {
                        profile.protocol = "verify_sha1";
                    }
                    profile.password = ss.group(4);
                    profile.name = ss.group(5);
                    profile.host = profile.name;
                    profile.remotePort = Integer.parseInt(ss.group(6));
                    if (m.group(2) != null) {
                        profile.name = URLDecoder.decode(m.group(3), "utf-8");
                    }
                    list.add(profile);
                }
            }
            return list;
        } catch (Exception e) {
            // Ignore
            VayLog.e(TAG, "findAll", e);
            app.track(e);
            return null;
        }
    }

    public static List<Profile> findAll_ssr(CharSequence data) {
        Pattern pattern_ssr = getPattern(pattern_ssr_regex);
        Pattern decodedPattern_ssr = getPattern(decodedPattern_ssr_regex);
        Pattern decodedPattern_ssr_obfsparam = getPattern(decodedPattern_ssr_obfsparam_regex);
        Pattern decodedPattern_ssr_protocolparam = getPattern(decodedPattern_ssr_protocolparam_regex);
        Pattern decodedPattern_ssr_remarks = getPattern(decodedPattern_ssr_remarks_regex);
        Pattern decodedPattern_ssr_groupparam = getPattern(decodedPattern_ssr_groupparam_regex);

        CharSequence input = null;
        if (!TextUtils.isEmpty(data)) {
            input = data;
        } else {
            input = "";
        }

        Matcher m = pattern_ssr.matcher(input);
        try {
            List<Profile> list = new ArrayList<>();
            while (m.find()) {
                String uri = new String(Base64.decode(m.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8");
                Matcher ss = decodedPattern_ssr.matcher(uri);
                if (ss.find()) {
                    Profile profile = new Profile();
                    profile.host = ss.group(2).toLowerCase();
                    profile.remotePort = Integer.parseInt(ss.group(3));
                    profile.protocol = ss.group(4).toLowerCase();
                    profile.method = ss.group(5).toLowerCase();
                    profile.obfs = ss.group(6).toLowerCase();
                    profile.password = new String(Base64.decode(ss.group(7).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8");

                    Matcher param = null;

                    param = decodedPattern_ssr_obfsparam.matcher(uri);
                    if (param.find()) {
                        profile.obfs_param = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8");
                    }

                    param = decodedPattern_ssr_protocolparam.matcher(uri);
                    if (param.find()) {
                        profile.protocol_param = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8");
                    }

                    param = decodedPattern_ssr_remarks.matcher(uri);
                    if (param.find()) {
                        profile.name = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8");
                    } else {
                        profile.name = ss.group(2).toLowerCase();
                    }

                    param = decodedPattern_ssr_groupparam.matcher(uri);
                    if (param.find()) {
                        profile.url_group = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8");
                    }

                    // add to list
                    list.add(profile);
                }
            }
            return list;
        } catch (Exception e) {
            // Ignore
            VayLog.e(TAG, "findAll", e);
            app.track(e);
            return null;
        }
    }
}
