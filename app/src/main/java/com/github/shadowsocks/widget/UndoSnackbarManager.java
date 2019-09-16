package com.github.shadowsocks.widget;
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

import android.util.SparseArray;
import android.view.View;

import com.github.shadowsocks.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * @author Mygod
 */
public class UndoSnackbarManager<T> {

    private final View view;
    private OnUndoListener<T> undo;
    private OnCommitListener<T> commit;

    private SparseArray<T> recycleBin;
    private Snackbar last;

    private Snackbar.Callback removedCallback = new Snackbar.Callback() {
        @Override
        public void onDismissed(Snackbar transientBottomBar, int event) {
            if (event == Snackbar.Callback.DISMISS_EVENT_SWIPE || event == Snackbar.Callback.DISMISS_EVENT_MANUAL ||
                    event == Snackbar.Callback.DISMISS_EVENT_TIMEOUT) {
                if (commit != null) {
                    commit.onCommit(recycleBin);
                }
                recycleBin.clear();
            }
            last = null;
        }
    };

    /**
     * @param view   The view to find a parent from.
     * @param undo   Callback for undoing removals.
     * @param commit Callback for committing removals.
     * @tparam T Item type.
     */
    public UndoSnackbarManager(View view, OnUndoListener<T> undo, OnCommitListener<T> commit) {
        recycleBin = new SparseArray<>();
        this.view = view;
        this.undo = undo;
        this.commit = commit;
    }

    public void remove(int index, T item) {
        recycleBin.append(index, item);
        int count = recycleBin.size();
        last = Snackbar.make(view, view.getResources().getQuantityString(R.plurals.removed, count, count), Snackbar.LENGTH_LONG)
                .setCallback(removedCallback)
                .setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (undo != null) {
                            undo.onUndo(recycleBin);
                        }
                        recycleBin.clear();
                    }
                });
        last.show();
    }

    public void flush() {
        if (last != null) {
            last.dismiss();
        }
    }

    public interface OnCommitListener<T> {

        /**
         * commit recycle bin
         *
         * @param commit commit list
         */
        void onCommit(SparseArray<T> commit);
    }

    public interface OnUndoListener<T> {

        /**
         * undo recycle bin
         *
         * @param undo undo list
         */
        void onUndo(SparseArray<T> undo);
    }
}
