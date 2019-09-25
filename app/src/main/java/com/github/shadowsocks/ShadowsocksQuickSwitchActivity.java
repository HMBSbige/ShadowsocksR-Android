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

import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Lucas on 3/10/16.
 */
public class ShadowsocksQuickSwitchActivity extends AppCompatActivity {

    private ProfilesAdapter profilesAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_quick_switch);
        profilesAdapter = new ProfilesAdapter();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.quick_switch);

        RecyclerView profilesList = findViewById(R.id.profilesList);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        profilesList.setLayoutManager(lm);
        profilesList.setItemAnimator(new DefaultItemAnimator());
        profilesList.setAdapter(profilesAdapter);
        if (ShadowsocksApplication.app.profileId() >= 0) {
            int position = 0;
            List<Profile> profiles = profilesAdapter.profiles;
            for (int i = 0; i < profiles.size(); i++) {
                Profile profile = profiles.get(i);
                if (profile.getId() == ShadowsocksApplication.app.profileId()) {
                    position = i + 1;
                    break;
                }
            }
            lm.scrollToPosition(position);
        }

        if (Build.VERSION.SDK_INT >= 25) {
            ShortcutManager service = getSystemService(ShortcutManager.class);
            if (service != null) {
                service.reportShortcutUsed("switch");
            }
        }
    }

    private class ProfileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Profile item;
        private CheckedTextView text;

        public ProfileViewHolder(View view) {
            super(view);
            TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
            view.setBackgroundResource(typedArray.getResourceId(0, 0));
            typedArray.recycle();

            text = itemView.findViewById(android.R.id.text1);
            itemView.setOnClickListener(this);
        }

        public void bind(Profile item) {
            this.item = item;
            text.setText(item.getName());
            text.setChecked(item.getId() == ShadowsocksApplication.app.profileId());
        }

        @Override
        public void onClick(View v) {
            ShadowsocksApplication.app.switchProfile(item.getId());
            Utils.startSsService(ShadowsocksQuickSwitchActivity.this);
            finish();
        }
    }

    private class ProfilesAdapter extends RecyclerView.Adapter<ProfileViewHolder> {

        private final String name;
        private List<Profile> profiles;

        public ProfilesAdapter() {
            List<Profile> profiles = ShadowsocksApplication.app.profileManager.getAllProfiles();
            if (profiles == null || profiles.isEmpty()) {
                this.profiles = new ArrayList<>();
            } else {
                this.profiles = profiles;
            }

            String version = Build.VERSION.SDK_INT >= 21 ? "material" : "holo";
            name = "select_dialog_singlechoice_" + version;
        }

        @Override
        public ProfileViewHolder onCreateViewHolder(ViewGroup vg, int viewType) {
            View view = LayoutInflater.from(vg.getContext()).inflate(Resources.getSystem().getIdentifier(name, "layout", "android"), vg, false);
            return new ProfileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ProfileViewHolder vh, int i) {
            vh.bind(profiles.get(i));
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }
    }

}
