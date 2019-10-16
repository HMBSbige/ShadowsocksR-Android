package com.github.shadowsocks.utils;

import android.text.TextUtils;
import android.util.Base64;

import com.github.shadowsocks.ShadowsocksApplication;
import com.github.shadowsocks.database.Profile;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    public static final String TAG = Parser.class.getSimpleName();

    private static final String pattern_regex = "(?i)ss://([A-Za-z0-9+-/=_]+)(#(.+))?";
    private static final String decodedPattern_regex = "(?i)^((.+?)(-auth)??:(.*)@(.+?):(\\d+?))$";

    private static final String pattern_ssr_regex = "(?i)ssr://([A-Za-z0-9_=-]+)";
    private static final String decodedPattern_ssr_regex = "(?i)^((.+):(\\d+?):(.*):(.+):(.*):([^/]+))";
    private static final String decodedPattern_ssr_obfsparam_regex = "(?i)[?&]obfsparam=([A-Za-z0-9+-/=_]*)";
    private static final String decodedPattern_ssr_remarks_regex = "(?i)[?&]remarks=([A-Za-z0-9+-/=_]*)";
    private static final String decodedPattern_ssr_protocolparam_regex = "(?i)[?&]protoparam=([A-Za-z0-9+-/=_]*)";
    private static final String decodedPattern_ssr_groupparam_regex = "(?i)[?&]group=([A-Za-z0-9+-/=_]*)";

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
                Matcher ss = decodedPattern.matcher(new String(Base64.decode(m.group(1), Base64.NO_PADDING), StandardCharsets.UTF_8));
                if (ss.find()) {
                    Profile profile = new Profile();
                    profile.setMethod(ss.group(2).toLowerCase());
                    if (ss.group(3) != null) {
                        profile.setProtocol("verify_sha1");
                    }
                    profile.setPassword(ss.group(4));
                    profile.setName(ss.group(5));
                    profile.setHost(profile.getName());
                    profile.setRemotePort(Integer.parseInt(ss.group(6)));
                    if (m.group(2) != null) {
                        profile.setName(URLDecoder.decode(m.group(3), "utf-8"));
                    }
                    list.add(profile);
                }
            }
            return list;
        } catch (Exception e) {
            // Ignore
            VayLog.INSTANCE.e(TAG, "findAll", e);
            ShadowsocksApplication.app.track(e);
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
                String uri = new String(Base64.decode(m.group(1).replaceAll("=", ""), Base64.URL_SAFE), StandardCharsets.UTF_8);
                Matcher ss = decodedPattern_ssr.matcher(uri);
                if (ss.find()) {
                    Profile profile = new Profile();
                    profile.setHost(ss.group(2).toLowerCase());
                    profile.setRemotePort(Integer.parseInt(ss.group(3)));
                    profile.setProtocol(ss.group(4).toLowerCase());
                    profile.setMethod(ss.group(5).toLowerCase());
                    profile.setObfs(ss.group(6).toLowerCase());
                    profile.setPassword(new String(Base64.decode(ss.group(7).replaceAll("=", ""), Base64.URL_SAFE), StandardCharsets.UTF_8));

                    Matcher param = null;

                    param = decodedPattern_ssr_obfsparam.matcher(uri);
                    if (param.find()) {
                        profile.setObfs_param(new String(Base64.decode(param.group(1).replace('/', '_').replace('+', '-').replace("=", ""), Base64.URL_SAFE), StandardCharsets.UTF_8));
                    }

                    param = decodedPattern_ssr_protocolparam.matcher(uri);
                    if (param.find()) {
                        profile.setProtocol_param(new String(Base64.decode(param.group(1).replace('/', '_').replace('+', '-').replace("=", ""), Base64.URL_SAFE), StandardCharsets.UTF_8));
                    }

                    param = decodedPattern_ssr_remarks.matcher(uri);
                    if (param.find()) {
                        profile.setName(new String(Base64.decode(param.group(1).replace('/', '_').replace('+', '-').replace("=", ""), Base64.URL_SAFE), StandardCharsets.UTF_8));
                    } else {
                        profile.setName(ss.group(2).toLowerCase());
                    }

                    param = decodedPattern_ssr_groupparam.matcher(uri);
                    if (param.find()) {
                        profile.setUrl_group(new String(Base64.decode(param.group(1).replace('/', '_').replace('+', '-').replace("=", ""), Base64.URL_SAFE), StandardCharsets.UTF_8));
                    }

                    // add to list
                    list.add(profile);
                }
            }
            return list;
        } catch (Exception e) {
            // Ignore
            VayLog.INSTANCE.e(TAG, "findAll", e);
            ShadowsocksApplication.app.track(e);
            return null;
        }
    }
}
