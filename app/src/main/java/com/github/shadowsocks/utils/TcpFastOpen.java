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

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.chainfire.libsuperuser.Shell;

/**
 * @author Mygod
 */
public class TcpFastOpen {

    private static final Pattern p = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");

    /**
     * Is kernel version >= 3.7.1.
     */
    public static boolean supported() {
        Matcher m = p.matcher(System.getProperty("os.version"));
        while (m.find()) {
            int kernel = Integer.parseInt(m.group(1));
            if (kernel < 3) {
                return false;
            } else if (kernel > 3) {
                return true;
            } else {
                int major = Integer.parseInt(m.group(2));
                if (major < 7) {
                    return false;
                } else if (major > 7) {
                    return true;
                } else {
                    return Integer.parseInt(m.group(3)) >= 1;
                }
            }
        }
        return false;
    }

    public static boolean sendEnabled() {
        File file = new File("/proc/sys/net/ipv4/tcp_fastopen");
        return file.canRead() && (Integer.parseInt(Utils.readAllLines(file)) & 1) > 0;
    }

    public static String enabled(boolean value) {
        if (sendEnabled() != value) {
            boolean suAvailable = Shell.SU.available();
            if (suAvailable) {
                int valueFlag = value ? 3 : 0;
                String[] cmds = {
                        "if echo " + valueFlag + " > /proc/sys/net/ipv4/tcp_fastopen; then",
                        "  echo Success.",
                        "else",
                        "  echo Failed.",
                };

                List<String> res = Shell.run("su", cmds, null, true);
                if (res != null && !res.isEmpty()) {
                    return Utils.makeString(res, "\n");
                }
            }
        }

        return null;
    }
}
