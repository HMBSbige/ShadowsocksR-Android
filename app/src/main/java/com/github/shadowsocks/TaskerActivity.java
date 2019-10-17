package com.github.shadowsocks;


import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.TaskerSettings;

import java.util.List;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class TaskerActivity extends AppCompatActivity {

    private TaskerSettings taskerOption;
    private Switch mSwitch;
    private ProfilesAdapter profilesAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_tasker);

        profilesAdapter = new ProfilesAdapter();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        taskerOption = TaskerSettings.Companion.fromIntent(getIntent());
        mSwitch = findViewById(R.id.serviceSwitch);
        mSwitch.setChecked(taskerOption.getSwitchOn());
        RecyclerView profilesList = findViewById(R.id.profilesList);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        profilesList.setLayoutManager(lm);
        profilesList.setItemAnimator(new DefaultItemAnimator());
        profilesList.setAdapter(profilesAdapter);

        if (taskerOption.getProfileId() >= 0) {
            int position = 0;
            List<Profile> profiles = profilesAdapter.profiles;
            for (int i = 0; i < profiles.size(); i++) {
                Profile profile = profiles.get(i);
                if (profile.getId() == taskerOption.getProfileId()) {
                    position = i + 1;
                    break;
                }
            }
            lm.scrollToPosition(position);
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

        public void bindDefault() {
            item = null;
            text.setText(R.string.profile_default);
            text.setChecked(taskerOption.getProfileId() < 0);
        }

        public void bind(Profile item) {
            this.item = item;
            text.setText(item.getName());
            text.setChecked(taskerOption.getProfileId() == item.getId());
        }

        @Override
        public void onClick(View v) {
            taskerOption.setSwitchOn(mSwitch.isChecked());
            taskerOption.setProfileId(item == null ? -1 : item.getId());
            setResult(RESULT_OK, taskerOption.toIntent(TaskerActivity.this));
            finish();
        }
    }

    private class ProfilesAdapter extends RecyclerView.Adapter<ProfileViewHolder> {

        private List<Profile> profiles;
        private String name;

        public ProfilesAdapter() {
            profiles = app.profileManager.getAllProfiles();

            String version = Build.VERSION.SDK_INT >= 21 ? "material" : "holo";
            name = "select_dialog_singlechoice_" + version;
        }

        public List<Profile> profiles() {
            return app.profileManager.getAllProfiles();
        }

        @Override
        public int getItemCount() {
            return 1 + profiles().size();
        }

        @Override
        public void onBindViewHolder(ProfileViewHolder vh, int i) {
            if (i == 0) {
                vh.bindDefault();
            } else {
                vh.bind(profiles().get(i - 1));
            }
        }

        @Override
        public ProfileViewHolder onCreateViewHolder(ViewGroup vg, int i) {
            View view = LayoutInflater.from(vg.getContext())
                    .inflate(Resources.getSystem().getIdentifier(name, "layout", "android"), vg, false);
            return new ProfileViewHolder(view);
        }
    }
}
