package com.github.shadowsocks;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatDrawableManager;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.TaskStackBuilder;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.database.SSRSub;
import com.github.shadowsocks.database.SSRSubManager;
import com.github.shadowsocks.network.ping.PingCallback;
import com.github.shadowsocks.network.ping.PingHelper;
import com.github.shadowsocks.network.request.RequestCallback;
import com.github.shadowsocks.network.request.RequestHelper;
import com.github.shadowsocks.network.ssrsub.SubUpdateCallback;
import com.github.shadowsocks.network.ssrsub.SubUpdateHelper;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.Parser;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.widget.UndoSnackbarManager;
import com.google.android.material.snackbar.Snackbar;

import net.glxn.qrgen.android.QRCode;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ProfileManagerActivity extends AppCompatActivity implements View.OnClickListener, Toolbar.OnMenuItemClickListener, NfcAdapter.CreateNdefMessageCallback, ProfileManager.ProfileAddedListener, SSRSubManager.SSRSubAddedListener {

    private static final String TAG = ProfileManagerActivity.class.getSimpleName();
    private static final int MSG_FULL_TEST_FINISH = 1;

    private ProfileViewHolder selectedItem;
    private Handler handler = new Handler();

    private FloatingActionMenu menu;

    private ProfilesAdapter profilesAdapter;
    private SSRSubAdapter ssrsubAdapter;
    private UndoSnackbarManager<Profile> undoManager;

    private ClipboardManager clipboard;

    private NfcAdapter nfcAdapter;
    private byte[] nfcShareItem;
    private boolean isNfcAvailable;
    private boolean isNfcEnabled;
    private boolean isNfcBeamEnabled;

    private ProgressDialog testProgressDialog;
    private boolean isTesting;
    private GuardedProcess ssTestProcess;

    private int REQUEST_QRCODE = 1;
    private boolean is_sort = false;

    private ServiceBoundContext mServiceBoundContext;
    /**
     * progress handler
     */
    private Handler mProgressHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FULL_TEST_FINISH:
                    if (testProgressDialog != null) {
                        testProgressDialog.dismiss();
                        testProgressDialog = null;
                    }

                    finish();
                    startActivity(new Intent(getIntent()));
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * use string divider list value
     *
     * @param list    list
     * @param divider divider string
     * @return list is empty, return null.
     */
    public static String makeString(List<Profile> list, String divider) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Profile item = list.get(i);
            if (i > 0) {
                sb.append(divider);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mServiceBoundContext = new ServiceBoundContext(newBase) {

        };
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profilesAdapter = new ProfilesAdapter();
        ssrsubAdapter = new SSRSubAdapter();

        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        String action = getIntent().getAction();
        if (action != null && action.equals(Constants.Action.SCAN)) {
            qrcodeScan();
        }

        if (action != null && action.equals(Constants.Action.SORT)) {
            is_sort = true;
        }

        setContentView(R.layout.layout_profiles);


        initToolbar();
        initFab();
        initGroupSpinner();

        ShadowsocksApplication.app.profileManager.addProfileAddedListener(this);

        final RecyclerView profilesList = findViewById(R.id.profilesList);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        profilesList.setLayoutManager(layoutManager);
        profilesList.setItemAnimator(new DefaultItemAnimator());
        profilesList.setAdapter(profilesAdapter);
        profilesList.postDelayed(() -> {
            // scroll to position
            int position = getCurrentProfilePosition();
            profilesList.scrollToPosition(position);
        }, 100);

        undoManager = new UndoSnackbarManager<>(profilesList, undo -> profilesAdapter.undo(undo), commit -> profilesAdapter.commit(commit));

        if (!is_sort) {
            new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.START | ItemTouchHelper.END) {
                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                    int index = viewHolder.getAdapterPosition();
                    profilesAdapter.remove(index);
                    undoManager.remove(index, ((ProfileViewHolder) viewHolder).item);
                }

                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                    profilesAdapter.move(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                    return true;
                }

            }).attachToRecyclerView(profilesList);
        }

        mServiceBoundContext.attachService(new IShadowsocksServiceCallback.Stub() {

            @Override
            public void stateChanged(int state, String profileName, String msg) {
                // Ignored
            }

            @Override
            public void trafficUpdated(long txRate, long rxRate, long txTotal, long rxTotal) {
                if (selectedItem != null) {
                    selectedItem.updateText(txTotal, rxTotal);
                }
            }
        });

        showProfileTipDialog();

        Intent intent = getIntent();
        if (intent != null) {
            handleShareIntent(intent);
        }
    }

    private void initGroupSpinner() {
        Spinner spinner = findViewById(R.id.group_choose_spinner);
        List<String> groups_name = ShadowsocksApplication.app.profileManager.getGroupNames();
        groups_name.add(0, getString(R.string.allgroups));
        ArrayAdapter<String> _Adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, groups_name);
        spinner.setAdapter(_Adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                String str = parent.getItemAtPosition(position).toString();
                profilesAdapter.onGroupChange(str);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * init toolbar
     */
    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.profiles);
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close);
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = getParentActivityIntent();
            if (shouldUpRecreateTask(intent) || isTaskRoot()) {
                TaskStackBuilder.create(ProfileManagerActivity.this).addNextIntentWithParentStack(intent).startActivities();
            } else {
                finish();
            }
        });
        toolbar.inflateMenu(R.menu.profile_manager_menu);
        toolbar.setOnMenuItemClickListener(this);
    }

    private int getCurrentProfilePosition() {
        int position = -1;
        List<Profile> profiles = profilesAdapter.profiles;
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = profiles.get(i);
            if (profile.getId() == ShadowsocksApplication.app.profileId()) {
                position = i;
            }
        }
        return position;
    }

    /**
     * show profile tips dialog
     */
    private void showProfileTipDialog() {
        if (ShadowsocksApplication.app.settings.getBoolean(Constants.Key.profileTip, true)) {
            ShadowsocksApplication.app.editor.putBoolean(Constants.Key.profileTip, false).apply();
            new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
                    .setTitle(R.string.profile_manager_dialog)
                    .setMessage(R.string.profile_manager_dialog_content)
                    .setPositiveButton(R.string.gotcha, null)
                    .create().show();
        }
    }

    @SuppressLint("RestrictedApi")
    public void initFab() {
        menu = findViewById(R.id.menu);
        menu.setClosedOnTouchOutside(true);
        AppCompatDrawableManager dm = AppCompatDrawableManager.get();
        FloatingActionButton manualAddFAB = findViewById(R.id.fab_manual_add);
        manualAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_create));
        manualAddFAB.setOnClickListener(this);
        final FloatingActionButton qrcodeAddFAB = findViewById(R.id.fab_qrcode_add);
        qrcodeAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_image_camera_alt));
        qrcodeAddFAB.setOnClickListener(this);
        FloatingActionButton nfcAddFAB = findViewById(R.id.fab_nfc_add);
        nfcAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_device_nfc));
        nfcAddFAB.setOnClickListener(this);
        FloatingActionButton importAddFAB = findViewById(R.id.fab_import_add);
        importAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_paste));
        importAddFAB.setOnClickListener(this);
        FloatingActionButton ssrsubAddFAB = findViewById(R.id.fab_ssr_sub);
        ssrsubAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_rss));
        ssrsubAddFAB.setOnClickListener(this);
        menu.setOnMenuToggleListener(opened -> {
            if (opened) {
                int visible = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) ? View.VISIBLE : View.GONE;
                qrcodeAddFAB.setVisibility(visible);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNfcState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    public void qrcodeScan() {
        try {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");

            startActivityForResult(intent, REQUEST_QRCODE);
        } catch (Throwable e) {
            if (menu != null) {
                menu.toggle(false);
            }
            startActivity(new Intent(this, ScannerActivity.class));
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_manual_add:
                menu.toggle(true);
                Profile profile = ShadowsocksApplication.app.profileManager.createProfile();
                ShadowsocksApplication.app.profileManager.updateProfile(profile);
                ShadowsocksApplication.app.switchProfile(profile.getId());
                finish();
                break;
            case R.id.fab_qrcode_add:
                menu.toggle(false);
                qrcodeScan();
                break;
            case R.id.fab_nfc_add:
                NfcAdd();
                break;
            case R.id.fab_import_add:
                clipboardImportAdd();
                break;
            case R.id.fab_ssr_sub:
                menu.toggle(true);
                ssrsubDialog();
                break;
            default:
                break;
        }
    }

    /**
     * add config by nfc
     */
    private void NfcAdd() {
        menu.toggle(true);
        AlertDialog dialog = new AlertDialog.Builder(ProfileManagerActivity.this, R.style.Theme_Material_Dialog_Alert)
                .setCancelable(true)
                .setPositiveButton(R.string.gotcha, null)
                .setTitle(R.string.add_profile_nfc_hint_title)
                .create();
        if (!isNfcBeamEnabled) {
            dialog.setMessage(getString(R.string.share_message_nfc_disabled));
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                }
            });
        } else {
            dialog.setMessage(getString(R.string.add_profile_nfc_hint));
        }
        dialog.show();
    }

    /**
     * add config by clipboard
     */
    private void clipboardImportAdd() {
        menu.toggle(true);
        if (clipboard.hasPrimaryClip()) {
            List<Profile> profiles_normal = Parser.INSTANCE.findAllSs(clipboard.getPrimaryClip().getItemAt(0).getText());
            List<Profile> profiles_ssr = Parser.INSTANCE.findAllSsr(clipboard.getPrimaryClip().getItemAt(0).getText());
            final List<Profile> profiles = new ArrayList<>();
            if (!profiles_normal.isEmpty()) {
                profiles.addAll(profiles_normal);
            }
            if (!profiles_ssr.isEmpty()) {
                profiles.addAll(profiles_ssr);
            }

            if (!profiles.isEmpty()) {
                showUrlAddProfileDialog(profiles);
                return;
            }
        }
        ToastUtils.INSTANCE.showShort(R.string.action_import_err);
    }

    /**
     * show url add profile dialog
     *
     * @param profiles
     */
    private void showUrlAddProfileDialog(final List<Profile> profiles) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.add_profile_dialog)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile item : profiles) {
                            ShadowsocksApplication.app.profileManager.createProfile(item);
                        }
                    }
                })
                .setNeutralButton(R.string.dr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile item : profiles) {
                            ShadowsocksApplication.app.profileManager.createProfileDr(item);
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).setMessage(makeString(profiles, "\n")).create();
        dialog.show();
    }

    public void ssrsubDialog() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        View view = View.inflate(this, R.layout.layout_ssr_sub, null);
        Switch subAutoUpdateEnable = view.findViewById(R.id.sw_ssr_sub_autoupdate_enable);

        // adding listener
        ShadowsocksApplication.app.ssrsubManager.addSSRSubAddedListener(this);

        RecyclerView ssusubsList = view.findViewById(R.id.ssrsubList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        ssusubsList.setLayoutManager(layoutManager);
        ssusubsList.setItemAnimator(new DefaultItemAnimator());
        ssusubsList.setAdapter(ssrsubAdapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, int direction) {
                delSubDialog(viewHolder);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return true;
            }
        }).attachToRecyclerView(ssusubsList);

        if (prefs.getInt(Constants.Key.ssrsub_autoupdate, 0) == 1) {
            subAutoUpdateEnable.setChecked(true);
        }

        subAutoUpdateEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            if (isChecked) {
                editor.putInt(Constants.Key.ssrsub_autoupdate, 1);
            } else {
                editor.putInt(Constants.Key.ssrsub_autoupdate, 0);
            }
            editor.apply();
        });

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_profile_methods_ssr_sub))
                .setPositiveButton(R.string.ssrsub_ok, (dialog, which) -> confirmWithUpdateSub())
                .setNegativeButton(android.R.string.no, null)
                .setNeutralButton(R.string.ssrsub_add, (dialog, which) -> showAddSSRSubAddrDialog())
                .setOnCancelListener(dialog -> {
                    // remove listener
                    ShadowsocksApplication.app.ssrsubManager.removeSSRSubAddedListener(ProfileManagerActivity.this);
                })
                .setView(view)
                .create()
                .show();
    }

    /**
     * del sub confirm dialog
     */
    private void delSubDialog(final RecyclerView.ViewHolder viewHolder) {
        final int index = viewHolder.getAdapterPosition();
        new AlertDialog.Builder(ProfileManagerActivity.this)
                .setTitle(getString(R.string.ssrsub_remove_tip_title))
                .setPositiveButton(R.string.ssrsub_remove_tip_direct, (dialog, which) -> {
                    ssrsubAdapter.remove(index);
                    ShadowsocksApplication.app.ssrsubManager.delSSRSub(((SSRSubViewHolder) viewHolder).item.getId());
                })
                .setNegativeButton(android.R.string.no, (dialog, which) -> ssrsubAdapter.notifyDataSetChanged())
                .setNeutralButton(R.string.ssrsub_remove_tip_delete, (dialog, which) -> {
                    String group = ((SSRSubViewHolder) viewHolder).item.getUrl_group();
                    List<Profile> deleteProfiles = ShadowsocksApplication.app.profileManager.getAllProfilesByGroup(group);

                    for (Profile profile : deleteProfiles) {
                        if (profile.getId() != ShadowsocksApplication.app.profileId()) {
                            ShadowsocksApplication.app.profileManager.delProfile(profile.getId());
                        }
                    }

                    int index1 = viewHolder.getAdapterPosition();
                    ssrsubAdapter.remove(index1);
                    ShadowsocksApplication.app.ssrsubManager.delSSRSub(((SSRSubViewHolder) viewHolder).item.getId());

                    finish();
                    startActivity(new Intent(getIntent()));
                })
                .setMessage(getString(R.string.ssrsub_remove_tip))
                .setCancelable(false)
                .create()
                .show();
    }

    /**
     * config with update
     */
    private void confirmWithUpdateSub() {
        testProgressDialog = ProgressDialog.show(ProfileManagerActivity.this,
                getString(R.string.ssrsub_progres),
                getString(R.string.ssrsub_progres_text),
                false,
                true);

        // start update sub
        List<SSRSub> subs = ShadowsocksApplication.app.ssrsubManager.getAllSSRSubs();
        SubUpdateHelper.Companion.instance().updateSub(subs, 0, new SubUpdateCallback() {
            @Override
            public void onFailed() {
                ToastUtils.INSTANCE.showShort(R.string.ssrsub_error);
            }

            @Override
            public void onFinished() {
                if (testProgressDialog != null) {
                    testProgressDialog.dismiss();
                }

                finish();
                startActivity(new Intent(getIntent()));
            }
        });
    }

    private void showAddSSRSubAddrDialog() {
        LayoutInflater li = LayoutInflater.from(this);
        final View myView = li.inflate(R.layout.layout_edittext, null);
        AlertDialog.Builder cDialog = new AlertDialog.Builder(ProfileManagerActivity.this);
        cDialog.setView(myView)
                .setTitle(getString(R.string.ssrsub_add))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        EditText editText1 = myView.findViewById(R.id.editTextInput);
                        // add ssr sub by url
                        String subUrl = editText1.getText().toString();
                        addSSRSubByUrl(subUrl);
                    }
                })
                .setNegativeButton(android.R.string.no, (dialog, which) -> ssrsubDialog());
        AlertDialog dialog = cDialog.create();
        dialog.show();
    }

    /**
     * add ssr sub by url
     *
     * @param subUrl sub url
     */
    private void addSSRSubByUrl(final String subUrl) {
        if (!TextUtils.isEmpty(subUrl)) {
            // show progress dialog
            testProgressDialog = ProgressDialog.show(ProfileManagerActivity.this,
                    getString(R.string.ssrsub_progres),
                    getString(R.string.ssrsub_progres_text),
                    false,
                    true);

            // request sub content
            RequestHelper.Companion.instance().get(subUrl, new RequestCallback() {
                @Override
                public void onSuccess(int code, String response) {
                    SSRSub ssrsub = SubUpdateHelper.Companion.parseSSRSub(subUrl, response);
                    ShadowsocksApplication.app.ssrsubManager.createSSRSub(ssrsub);
                }

                @Override
                public void onFailed(int code, String msg) {
                    ToastUtils.INSTANCE.showShort(getString(R.string.ssrsub_error));
                }

                @Override
                public void onFinished() {
                    testProgressDialog.dismiss();
                    ssrsubDialog();
                }
            });
        } else {
            ssrsubDialog();
        }
    }

    private void updateNfcState() {
        isNfcAvailable = false;
        isNfcEnabled = false;
        isNfcBeamEnabled = false;
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            isNfcAvailable = true;
            if (nfcAdapter.isEnabled()) {
                isNfcEnabled = true;
                if (nfcAdapter.isNdefPushEnabled()) {
                    isNfcBeamEnabled = true;
                    nfcAdapter.setNdefPushMessageCallback(null, ProfileManagerActivity.this);
                }
            }
        }
    }

    private void handleShareIntent(Intent intent) {
        String sharedStr = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            sharedStr = intent.getData().toString();
        } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null && rawMsgs.length > 0) {
                sharedStr = new String(((NdefMessage) rawMsgs[0]).getRecords()[0].getPayload());
            }
        }

        if (TextUtils.isEmpty(sharedStr)) {
            return;
        }

        final List<Profile> profiles = Utils.INSTANCE.mergeList(Parser.INSTANCE.findAllSs(sharedStr), Parser.INSTANCE.findAllSsr(sharedStr));

        if (profiles.isEmpty()) {
            finish();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.add_profile_dialog)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile profile : profiles) {
                            ShadowsocksApplication.app.profileManager.createProfile(profile);
                        }
                    }
                })
                .setNeutralButton(R.string.dr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile profile : profiles) {
                            ShadowsocksApplication.app.profileManager.createProfileDr(profile);
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setMessage(makeString(profiles, "\n"))
                .create();
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_QRCODE) {
            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                if (TextUtils.isEmpty(contents)) {
                    return;
                }
                final List<Profile> profiles = Utils.INSTANCE.mergeList(Parser.INSTANCE.findAllSs(contents), Parser.INSTANCE.findAllSsr(contents));
                if (profiles.isEmpty()) {
                    finish();
                    return;
                }
                AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
                        .setTitle(R.string.add_profile_dialog)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                for (Profile profile : profiles) {
                                    ShadowsocksApplication.app.profileManager.createProfile(profile);
                                }
                            }
                        })
                        .setNeutralButton(R.string.dr, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                for (Profile profile : profiles) {
                                    ShadowsocksApplication.app.profileManager.createProfileDr(profile);
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setMessage(makeString(profiles, "\n"))
                        .create();
                dialog.show();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                //handle cancel
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mServiceBoundContext.registerCallback();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mServiceBoundContext.unregisterCallback();
    }

    @Override
    public void onProfileAdded(Profile profile) {
        profilesAdapter.add(profile);
    }

    @Override
    public void onSSRSubAdded(SSRSub ssrSub) {
        ssrsubAdapter.add(ssrSub);
    }

    @Override
    protected void onDestroy() {
        mServiceBoundContext.detachService();

        if (ssTestProcess != null) {
            ssTestProcess.destroy();
            ssTestProcess = null;
        }

        undoManager.flush();
        ShadowsocksApplication.app.profileManager.removeProfileAddedListener(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (menu.isOpened()) {
            menu.close(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent nfcEvent) {
        return new NdefMessage(new NdefRecord[]{new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, nfcShareItem, new byte[]{}, nfcShareItem)});
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_export:
                List<Profile> allProfiles = ShadowsocksApplication.app.profileManager.getAllProfiles();
                if (allProfiles != null && !allProfiles.isEmpty()) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, makeString(allProfiles, "\n")));
                    ToastUtils.INSTANCE.showShort(R.string.action_export_msg);
                } else {
                    ToastUtils.INSTANCE.showShort(R.string.action_export_err);
                }
                return true;
            case R.id.action_sort:
                finish();
                startActivity(new Intent(Constants.Action.SORT));
                return true;
            case R.id.action_full_test:
                pingAll();
                return true;
            default:
                break;
        }
        return false;
    }

    private void pingAll() {
        // reject repeat operation
        if (isTesting) {
            return;
        }

        isTesting = true;
        testProgressDialog = ProgressDialog.show(this,
                getString(R.string.tips_testing),
                getString(R.string.tips_testing),
                false,
                true, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // TODO Auto-generated method stub
                        // Do something...
                        if (testProgressDialog != null) {
                            testProgressDialog = null;
                        }

                        isTesting = false;

                        finish();
                        startActivity(new Intent(getIntent()));
                    }
                });

        // get profile list
        List<Profile> profiles = profilesAdapter.profiles;
        // start test
        PingHelper.Companion.instance().pingAll(this, profiles, new PingCallback() {

            @Override
            public void onSuccess(Profile profile, long elapsed) {
                profile.setElapsed(elapsed);
                ShadowsocksApplication.app.profileManager.updateProfile(profile);

                // set progress message
                setProgressMessage(profile.getName() + "\n" + getResultMsg());
            }

            @Override
            public void onFailed(Profile profile) {
                profile.setElapsed(-1);
                ShadowsocksApplication.app.profileManager.updateProfile(profile);

                // set progress message
                setProgressMessage(getResultMsg());
            }

            /**
             * set progress message
             *
             * @param message tips message
             */
            private void setProgressMessage(String message) {
                if (testProgressDialog != null) {
                    testProgressDialog.setMessage(message);
                }
            }

            @Override
            public void onFinished(Profile profile) {
                mProgressHandler.sendEmptyMessageDelayed(MSG_FULL_TEST_FINISH, 2000);
                PingHelper.Companion.instance().releaseTempActivity();
            }
        });
    }

    private class ProfileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnKeyListener {

        private Profile item;
        private CheckedTextView text;

        public ProfileViewHolder(View view) {
            super(view);
            text = itemView.findViewById(android.R.id.text1);
            itemView.setOnClickListener(this);
            itemView.setOnKeyListener(this);

            initShareBtn();
            initPingBtn();
        }

        /**
         * init share btn
         */
        private void initShareBtn() {
            final ImageView shareBtn = itemView.findViewById(R.id.share);
            shareBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String url = item.toString();
                    if (isNfcBeamEnabled) {
                        nfcAdapter.setNdefPushMessageCallback(ProfileManagerActivity.this, ProfileManagerActivity.this);
                        nfcShareItem = url.getBytes(Charset.forName("UTF-8"));
                    }
                    ImageView image = new ImageView(ProfileManagerActivity.this);
                    image.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
                    Bitmap qrcode = ((QRCode) QRCode.from(url)
                            .withSize(Utils.INSTANCE.dpToPx(ProfileManagerActivity.this, 250), Utils.INSTANCE.dpToPx(ProfileManagerActivity.this, 250)))
                            .bitmap();
                    image.setImageBitmap(qrcode);

                    AlertDialog dialog = new AlertDialog.Builder(ProfileManagerActivity.this, R.style.Theme_Material_Dialog_Alert)
                            .setCancelable(true)
                            .setPositiveButton(R.string.close, null)
                            .setNegativeButton(R.string.copy_url, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText(null, url));
                                }
                            })
                            .setView(image)
                            .setTitle(R.string.share)
                            .create();
                    if (!isNfcAvailable) {
                        dialog.setMessage(getString(R.string.share_message_without_nfc));
                    } else if (!isNfcBeamEnabled) {
                        dialog.setMessage(getString(R.string.share_message_nfc_disabled));
                        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc), (dialog12, which) -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
                    } else {
                        dialog.setMessage(getString(R.string.share_message));
                        dialog.setOnDismissListener(dialog1 -> nfcAdapter.setNdefPushMessageCallback(null, ProfileManagerActivity.this));
                    }
                    dialog.show();
                }
            });

            shareBtn.setOnLongClickListener(v -> {
                Utils.INSTANCE.positionToast(Toast.makeText(ProfileManagerActivity.this, R.string.share, Toast.LENGTH_SHORT), shareBtn,
                        getWindow(), 0, Utils.INSTANCE.dpToPx(ProfileManagerActivity.this, 8)).show();
                return true;
            });
        }

        /**
         * init ping btn
         */
        private void initPingBtn() {
            final ImageView pingBtn = itemView.findViewById(R.id.ping_single);
            pingBtn.setOnClickListener(v -> {
                item.setElapsed(0);
                final ProgressDialog singleTestProgressDialog = ProgressDialog.show(ProfileManagerActivity.this, getString(R.string.tips_testing), getString(R.string.tips_testing), false, true);
                PingHelper.Companion.instance().ping(ProfileManagerActivity.this, item, new PingCallback() {
                    @Override
                    public void onSuccess(@NotNull Profile profile, long elapsed) {
                        if (profile.getElapsed() == 0) {
                            profile.setElapsed(elapsed);
                        } else if (profile.getElapsed() > elapsed) {
                            profile.setElapsed(elapsed);
                        }

                        ShadowsocksApplication.app.profileManager.updateProfile(profile);
                        updateText(profile.getTx(), profile.getRx(), elapsed);
                    }

                    @Override
                    public void onFailed(Profile profile) {
                    }

                    @Override
                    public void onFinished(Profile profile) {
                        Snackbar.make(findViewById(android.R.id.content), getResultMsg(), Snackbar.LENGTH_LONG).show();
                        singleTestProgressDialog.dismiss();
                        PingHelper.Companion.instance().releaseTempActivity();
                    }
                });
            });

            pingBtn.setOnLongClickListener(v -> {
                Utils.INSTANCE.positionToast(Toast.makeText(ProfileManagerActivity.this, R.string.ping, Toast.LENGTH_SHORT),
                        pingBtn,
                        getWindow(),
                        0,
                        Utils.INSTANCE.dpToPx(ProfileManagerActivity.this, 8))
                        .show();
                return true;
            });
        }

        public void updateText() {
            updateText(0, 0);
        }

        public void updateText(long txTotal, long rxTotal) {
            updateText(txTotal, rxTotal, -1);
        }

        public void updateText(long txTotal, long rxTotal, long elapsedInput) {
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            long tx = item.getTx() + txTotal;
            long rx = item.getRx() + rxTotal;
            long elapsed = item.getElapsed();
            if (elapsedInput != -1) {
                elapsed = elapsedInput;
            }
            builder.append(item.getName());
            if (tx != 0 || rx != 0 || elapsed != 0 || item.getUrl_group() != "") {
                int start = builder.length();
                builder.append(getString(R.string.stat_profiles,
                        TrafficMonitor.INSTANCE.formatTraffic(tx), TrafficMonitor.INSTANCE.formatTraffic(rx), String.valueOf(elapsed), item.getUrl_group()));
                builder.setSpan(new TextAppearanceSpan(ProfileManagerActivity.this, android.R.style.TextAppearance_Small),
                        start + 1, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            handler.post(() -> text.setText(builder));
        }

        public void bind(Profile item) {
            this.item = item;
            updateText();
            if (item.getId() == ShadowsocksApplication.app.profileId()) {
                text.setChecked(true);
                selectedItem = this;
            } else {
                text.setChecked(false);
                if (this.equals(selectedItem)) {
                    selectedItem = null;
                }
            }
        }

        @Override
        public void onClick(View v) {
            ShadowsocksApplication.app.switchProfile(item.getId());
            finish();
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        int index = getAdapterPosition();
                        if (index >= 0) {
                            profilesAdapter.remove(index);
                            undoManager.remove(index, item);
                            return true;
                        } else {
                            return false;
                        }
                    default:
                        return false;
                }
            } else {
                return false;
            }
        }
    }

    private class ProfilesAdapter extends RecyclerView.Adapter<ProfileViewHolder> {

        private List<Profile> profiles;

        public ProfilesAdapter() {
            if (is_sort) {
                List<Profile> list = ShadowsocksApplication.app.profileManager.getAllProfilesByElapsed();
                if (list != null && !list.isEmpty()) {
                    profiles = list;
                }
            } else {
                List<Profile> list = ShadowsocksApplication.app.profileManager.getAllProfiles();
                if (list != null && !list.isEmpty()) {
                    profiles = list;
                }
            }

            if (profiles == null) {
                profiles = new ArrayList<>();
            }
        }


        public void onGroupChange(String groupname) {
            List<Profile> list;
            if (groupname.equals("All Groups") || groupname.equals("全部群组")) {
                if (is_sort) {
                    list = ShadowsocksApplication.app.profileManager.getAllProfilesByElapsed();
                } else {
                    list = ShadowsocksApplication.app.profileManager.getAllProfiles();
                }
            } else {
                if (is_sort) {
                    list = ShadowsocksApplication.app.profileManager.getAllProfilesByGroupOrderbyElapse(groupname);

                } else {
                    list = ShadowsocksApplication.app.profileManager.getAllProfilesByGroup(groupname);
                }
            }
            if (list != null && !list.isEmpty()) {
                profiles = list;
            }
            if (profiles == null) {
                profiles = new ArrayList<>();
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return profiles == null ? 0 : profiles.size();
        }

        @Override
        public void onBindViewHolder(ProfileViewHolder vh, int i) {
            vh.bind(profiles.get(i));
        }

        @Override
        public ProfileViewHolder onCreateViewHolder(ViewGroup vg, int viewType) {
            View view = LayoutInflater.from(vg.getContext()).inflate(R.layout.layout_profiles_item, vg, false);
            return new ProfileViewHolder(view);
        }

        public void add(Profile item) {
            undoManager.flush();
            int pos = getItemCount();
            profiles.add(item);
            notifyItemInserted(pos);
        }

        public void move(int from, int to) {
            undoManager.flush();
            int step = from < to ? 1 : -1;
            Profile first = profiles.get(from);
            long previousOrder = profiles.get(from).getUserOrder();
            for (int i = from; i < to; i += step) {
                Profile next = profiles.get(i + step);
                long order = next.getUserOrder();
                next.setUserOrder(previousOrder);
                previousOrder = order;
                profiles.set(i, next);
                ShadowsocksApplication.app.profileManager.updateProfile(next);
            }
            first.setUserOrder(previousOrder);
            profiles.set(to, first);
            ShadowsocksApplication.app.profileManager.updateProfile(first);
            notifyItemMoved(from, to);
        }

        public void remove(int pos) {
            Profile remove = profiles.remove(pos);
            ShadowsocksApplication.app.profileManager.delProfile(remove.getId());
            notifyItemRemoved(pos);

        }

        public void undo(SparseArray<Profile> actions) {
            for (int index = 0; index < actions.size(); index++) {
                Profile item = actions.get(index);
                if (item != null) {
                    profiles.add(index, item);
                    notifyItemInserted(index);
                }
            }
        }

        public void commit(SparseArray<Profile> actions) {
            for (int index = 0; index < actions.size(); index++) {
                Profile item = actions.get(index);
                if (item != null) {
                    ShadowsocksApplication.app.profileManager.delProfile(item.getId());
                    if (item.getId() == ShadowsocksApplication.app.profileId()) {
                        ShadowsocksApplication.app.profileId(-1);
                    }
                }
            }
        }
    }

    private class SSRSubViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnKeyListener, View.OnLongClickListener {

        private SSRSub item;
        private TextView text;

        public SSRSubViewHolder(View view) {
            super(view);
            text = itemView.findViewById(android.R.id.text2);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        public void updateText() {
            updateText(false);
        }

        public void updateText(boolean isShowUrl) {
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(this.item.getUrl_group()).append("\n");
            if (isShowUrl) {
                int start = builder.length();
                builder.append(this.item.getUrl());
                builder.setSpan(new TextAppearanceSpan(ProfileManagerActivity.this, android.R.style.TextAppearance_Small),
                        start,
                        builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            handler.post(() -> text.setText(builder));
        }

        public void copyText() {
            String subUrl = this.item.getUrl();
            if (!"".equals(subUrl)) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, subUrl));
                ToastUtils.INSTANCE.showShort(R.string.action_export_msg);
            } else {
                ToastUtils.INSTANCE.showShort(R.string.action_export_err);
            }
        }

        public void bind(SSRSub item) {
            this.item = item;
            updateText();
        }

        @Override
        public void onClick(View v) {
            updateText(true);
        }

        @Override
        public boolean onLongClick(View v) {
            copyText();
            return true;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            return true;
        }
    }

    private class SSRSubAdapter extends RecyclerView.Adapter<SSRSubViewHolder> {

        private List<SSRSub> profiles;

        public SSRSubAdapter() {
            List<SSRSub> all = ShadowsocksApplication.app.ssrsubManager.getAllSSRSubs();
            if (all != null && !all.isEmpty()) {
                profiles = all;
            } else {
                profiles = new ArrayList<>();
            }
        }

        @Override
        public int getItemCount() {
            return profiles == null ? 0 : profiles.size();
        }

        @Override
        public void onBindViewHolder(SSRSubViewHolder vh, int i) {
            vh.bind(profiles.get(i));
        }

        @Override
        public SSRSubViewHolder onCreateViewHolder(ViewGroup vg, int viewType) {
            View view = LayoutInflater.from(vg.getContext()).inflate(R.layout.layout_ssr_sub_item, vg, false);
            return new SSRSubViewHolder(view);
        }

        public void add(SSRSub item) {
            undoManager.flush();
            int pos = getItemCount();
            profiles.add(item);
            notifyItemInserted(pos);
        }

        public void remove(int pos) {
            profiles.remove(pos);
            notifyItemRemoved(pos);
        }
    }
}
