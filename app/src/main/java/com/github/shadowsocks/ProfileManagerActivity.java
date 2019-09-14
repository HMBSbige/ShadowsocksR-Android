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
import android.os.RemoteException;
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
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

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

import static com.github.shadowsocks.ShadowsocksApplication.app;

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

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.layout_profiles);

        initToolbar();
        initFab();

        app.profileManager.addProfileAddedListener(this);

        final RecyclerView profilesList = (RecyclerView) findViewById(R.id.profilesList);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        profilesList.setLayoutManager(layoutManager);
        profilesList.setItemAnimator(new DefaultItemAnimator());
        profilesList.setAdapter(profilesAdapter);
        profilesList.postDelayed(new Runnable() {
            @Override
            public void run() {
                // scroll to position
                int position = getCurrentProfilePosition();
                profilesList.scrollToPosition(position);
            }
        }, 100);

        undoManager = new UndoSnackbarManager<>(profilesList, new UndoSnackbarManager.OnUndoListener<Profile>() {
            @Override
            public void onUndo(SparseArray<Profile> undo) {
                profilesAdapter.undo(undo);
            }
        }, new UndoSnackbarManager.OnCommitListener<Profile>() {
            @Override
            public void onCommit(SparseArray<Profile> commit) {
                profilesAdapter.commit(commit);
            }
        });

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
            public void stateChanged(int state, String profileName, String msg) throws RemoteException {
                // Ignored
            }

            @Override
            public void trafficUpdated(long txRate, long rxRate, long txTotal, long rxTotal) throws RemoteException {
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

    /**
     * init toolbar
     */
    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.profiles);
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = getParentActivityIntent();
                if (shouldUpRecreateTask(intent) || isTaskRoot()) {
                    TaskStackBuilder.create(ProfileManagerActivity.this).addNextIntentWithParentStack(intent).startActivities();
                } else {
                    finish();
                }
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
            if (profile.id == app.profileId()) {
                position = i;
            }
        }
        return position;
    }

    /**
     * show profile tips dialog
     */
    private void showProfileTipDialog() {
        if (app.settings.getBoolean(Constants.Key.profileTip, true)) {
            app.editor.putBoolean(Constants.Key.profileTip, false).apply();
            new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
                    .setTitle(R.string.profile_manager_dialog)
                    .setMessage(R.string.profile_manager_dialog_content)
                    .setPositiveButton(R.string.gotcha, null)
                    .create().show();
        }
    }

    @SuppressLint("RestrictedApi")
    public void initFab() {
        menu = (FloatingActionMenu) findViewById(R.id.menu);
        menu.setClosedOnTouchOutside(true);
        AppCompatDrawableManager dm = AppCompatDrawableManager.get();
        FloatingActionButton manualAddFAB = (FloatingActionButton) findViewById(R.id.fab_manual_add);
        manualAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_create));
        manualAddFAB.setOnClickListener(this);
        final FloatingActionButton qrcodeAddFAB = (FloatingActionButton) findViewById(R.id.fab_qrcode_add);
        qrcodeAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_image_camera_alt));
        qrcodeAddFAB.setOnClickListener(this);
        FloatingActionButton nfcAddFAB = (FloatingActionButton) findViewById(R.id.fab_nfc_add);
        nfcAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_device_nfc));
        nfcAddFAB.setOnClickListener(this);
        FloatingActionButton importAddFAB = (FloatingActionButton) findViewById(R.id.fab_import_add);
        importAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_paste));
        importAddFAB.setOnClickListener(this);
        FloatingActionButton ssrsubAddFAB = (FloatingActionButton) findViewById(R.id.fab_ssr_sub);
        ssrsubAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_rss));
        ssrsubAddFAB.setOnClickListener(this);
        menu.setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
            @Override
            public void onMenuToggle(boolean opened) {
                if (opened) {
                    int visible = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) ? View.VISIBLE : View.GONE;
                    qrcodeAddFAB.setVisibility(visible);
                }
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
            /*val dialog = new AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
              .setTitle(R.string.scan_qrcode_install_title)
              .setPositiveButton(android.R.string.yes, ((_, _) => {
                  val marketUri = Uri.parse("market://details?id=com.google.zxing.client.android")
                  val marketIntent = new Intent(Intent.ACTION_VIEW, marketUri)
                  startActivity(marketIntent)
                }
              ): DialogInterface.OnClickListener)
              .setNeutralButton(R.string.scan_qrcode_direct_download_text, ((_, _) => {
                  val marketUri = Uri.parse("https://breakwa11.github.io/download/BarcodeScanner.apk")
                  val marketIntent = new Intent(Intent.ACTION_VIEW, marketUri)
                  startActivity(marketIntent)
                }
              ): DialogInterface.OnClickListener)
              .setNegativeButton(android.R.string.no, ((_, _) => finish()): DialogInterface.OnClickListener)
              .setMessage(R.string.scan_qrcode_install_text)
              .create()
            dialog.show()*/
            menu.toggle(false);
            startActivity(new Intent(this, ScannerActivity.class));
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_manual_add:
                menu.toggle(true);
                Profile profile = app.profileManager.createProfile();
                app.profileManager.updateProfile(profile);
                app.switchProfile(profile.id);
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
            List<Profile> profiles_normal = Parser.findAll(clipboard.getPrimaryClip().getItemAt(0).getText());
            List<Profile> profiles_ssr = Parser.findAll_ssr(clipboard.getPrimaryClip().getItemAt(0).getText());
            final List<Profile> profiles = new ArrayList<>();
            if (profiles_normal != null && !profiles_normal.isEmpty()) {
                profiles.addAll(profiles_normal);
            }
            if (profiles_ssr != null && !profiles_ssr.isEmpty()) {
                profiles.addAll(profiles_ssr);
            }

            if (!profiles.isEmpty()) {
                showUrlAddProfileDialog(profiles);
                return;
            }
        }
        ToastUtils.showShort(R.string.action_import_err);
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
                            app.profileManager.createProfile(item);
                        }
                    }
                })
                .setNeutralButton(R.string.dr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile item : profiles) {
                            app.profileManager.createProfileDr(item);
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
        Switch subAutoUpdateEnable = (Switch) view.findViewById(R.id.sw_ssr_sub_autoupdate_enable);

        // adding listener
        app.ssrsubManager.addSSRSubAddedListener(this);

        RecyclerView ssusubsList = (RecyclerView) view.findViewById(R.id.ssrsubList);
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

        subAutoUpdateEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = prefs.edit();
                if (isChecked) {
                    editor.putInt(Constants.Key.ssrsub_autoupdate, 1);
                } else {
                    editor.putInt(Constants.Key.ssrsub_autoupdate, 0);
                }
                editor.apply();
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_profile_methods_ssr_sub))
                .setPositiveButton(R.string.ssrsub_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmWithUpdateSub();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .setNeutralButton(R.string.ssrsub_add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showAddSSRSubAddrDialog();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // remove listener
                        app.ssrsubManager.removeSSRSubAddedListener(ProfileManagerActivity.this);
                    }
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
                .setPositiveButton(R.string.ssrsub_remove_tip_direct, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ssrsubAdapter.remove(index);
                        app.ssrsubManager.delSSRSub(((SSRSubViewHolder) viewHolder).item.id);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ssrsubAdapter.notifyDataSetChanged();
                    }
                })
                .setNeutralButton(R.string.ssrsub_remove_tip_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String group = ((SSRSubViewHolder) viewHolder).item.url_group;
                        List<Profile> deleteProfiles = app.profileManager.getAllProfilesByGroup(group);

                        for (Profile profile : deleteProfiles) {
                            if (profile.id != app.profileId()) {
                                app.profileManager.delProfile(profile.id);
                            }
                        }

                        int index = viewHolder.getAdapterPosition();
                        ssrsubAdapter.remove(index);
                        app.ssrsubManager.delSSRSub(((SSRSubViewHolder) viewHolder).item.id);

                        finish();
                        startActivity(new Intent(getIntent()));
                    }
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
        List<SSRSub> subs = app.ssrsubManager.getAllSSRSubs();
        SubUpdateHelper.instance().updateSub(subs, 0, new SubUpdateCallback() {
            @Override
            public void onFailed() {
                ToastUtils.showShort(R.string.ssrsub_error);
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
        final EditText urlAddEdit = new EditText(ProfileManagerActivity.this);
        new AlertDialog.Builder(ProfileManagerActivity.this)
                .setTitle(getString(R.string.ssrsub_add))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // add ssr sub by url
                        String subUrl = urlAddEdit.getText().toString();
                        addSSRSubByUrl(subUrl);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ssrsubDialog();
                    }
                })
                .setView(urlAddEdit)
                .create()
                .show();
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
            RequestHelper.instance().get(subUrl, new RequestCallback() {
                @Override
                public void onSuccess(int code, String response) {
                    SSRSub ssrsub = SubUpdateHelper.parseSSRSub(subUrl, response);
                    app.ssrsubManager.createSSRSub(ssrsub);
                }

                @Override
                public void onFailed(int code, String msg) {
                    ToastUtils.showShort(getString(R.string.ssrsub_error));
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

        final List<Profile> profiles = Utils.mergeList(Parser.findAll(sharedStr), Parser.findAll_ssr(sharedStr));

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
                            app.profileManager.createProfile(profile);
                        }
                    }
                })
                .setNeutralButton(R.string.dr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile profile : profiles) {
                            app.profileManager.createProfileDr(profile);
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
                final List<Profile> profiles = Utils.mergeList(Parser.findAll(contents), Parser.findAll_ssr(contents));
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
                                    app.profileManager.createProfile(profile);
                                }
                            }
                        })
                        .setNeutralButton(R.string.dr, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                for (Profile profile : profiles) {
                                    app.profileManager.createProfileDr(profile);
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
        app.profileManager.removeProfileAddedListener(this);
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

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_export:
                List<Profile> allProfiles = app.profileManager.getAllProfiles();
                if (allProfiles != null && !allProfiles.isEmpty()) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, makeString(allProfiles, "\n")));
                    ToastUtils.showShort(R.string.action_export_msg);
                } else {
                    ToastUtils.showShort(R.string.action_export_err);
                }
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
        List<Profile> profiles = app.profileManager.getAllProfiles();
        // start test
        PingHelper.instance().pingAll(this, profiles, new PingCallback() {

            @Override
            public void onSuccess(Profile profile, long elapsed) {
                profile.elapsed = elapsed;
                app.profileManager.updateProfile(profile);

                // set progress message
                setProgressMessage(profile.name + " " + getResultMsg());
            }

            @Override
            public void onFailed(Profile profile) {
                profile.elapsed = -1;
                app.profileManager.updateProfile(profile);

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
                PingHelper.instance().releaseTempActivity();
            }
        });
    }

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

    private class ProfileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnKeyListener {

        private Profile item;
        private CheckedTextView text;

        public ProfileViewHolder(View view) {
            super(view);
            text = (CheckedTextView) itemView.findViewById(android.R.id.text1);
            itemView.setOnClickListener(this);
            itemView.setOnKeyListener(this);

            initShareBtn();
            initPingBtn();
        }

        /**
         * init share btn
         */
        private void initShareBtn() {
            final ImageView shareBtn = (ImageView) itemView.findViewById(R.id.share);
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
                            .withSize(Utils.dpToPx(ProfileManagerActivity.this, 250), Utils.dpToPx(ProfileManagerActivity.this, 250)))
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
                        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                            }
                        });
                    } else {
                        dialog.setMessage(getString(R.string.share_message));
                        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                nfcAdapter.setNdefPushMessageCallback(null, ProfileManagerActivity.this);
                            }
                        });
                    }
                    dialog.show();
                }
            });
            shareBtn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Utils.positionToast(Toast.makeText(ProfileManagerActivity.this, R.string.share, Toast.LENGTH_SHORT), shareBtn,
                            getWindow(), 0, Utils.dpToPx(ProfileManagerActivity.this, 8)).show();
                    return true;
                }
            });
        }

        /**
         * init ping btn
         */
        private void initPingBtn() {
            final ImageView pingBtn = (ImageView) itemView.findViewById(R.id.ping_single);
            pingBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final ProgressDialog singleTestProgressDialog = ProgressDialog.show(ProfileManagerActivity.this, getString(R.string.tips_testing), getString(R.string.tips_testing), false, true);
                    PingHelper.instance().ping(ProfileManagerActivity.this, item, new PingCallback() {
                        @Override
                        public void onSuccess(Profile profile, long elapsed) {
                            profile.elapsed = elapsed;
                            app.profileManager.updateProfile(profile);
                            updateText(0, 0, elapsed);
                        }

                        @Override
                        public void onFailed(Profile profile) {
                        }

                        @Override
                        public void onFinished(Profile profile) {
                            Snackbar.make(findViewById(android.R.id.content), getResultMsg(), Snackbar.LENGTH_LONG).show();
                            singleTestProgressDialog.dismiss();
                            PingHelper.instance().releaseTempActivity();
                        }
                    });
                }
            });

            pingBtn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Utils.positionToast(Toast.makeText(ProfileManagerActivity.this, R.string.ping, Toast.LENGTH_SHORT),
                            pingBtn,
                            getWindow(),
                            0,
                            Utils.dpToPx(ProfileManagerActivity.this, 8))
                            .show();
                    return true;
                }
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
            long tx = item.tx + txTotal;
            long rx = item.rx + rxTotal;
            long elapsed = item.elapsed;
            if (elapsedInput != -1) {
                elapsed = elapsedInput;
            }
            builder.append(item.name);
            if (tx != 0 || rx != 0 || elapsed != 0 || item.url_group != "") {
                int start = builder.length();
                builder.append(getString(R.string.stat_profiles,
                        TrafficMonitor.formatTraffic(tx), TrafficMonitor.formatTraffic(rx), String.valueOf(elapsed), item.url_group));
                builder.setSpan(new TextAppearanceSpan(ProfileManagerActivity.this, android.R.style.TextAppearance_Small),
                        start + 1, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            handler.post(new Runnable() {
                @Override
                public void run() {
                    text.setText(builder);
                }
            });
        }

        public void bind(Profile item) {
            this.item = item;
            updateText();
            if (item.id == app.profileId()) {
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
            app.switchProfile(item.id);
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
                List<Profile> list = app.profileManager.getAllProfilesByElapsed();
                if (list != null && !list.isEmpty()) {
                    profiles = list;
                }
            } else {
                List<Profile> list = app.profileManager.getAllProfiles();
                if (list != null && !list.isEmpty()) {
                    profiles = list;
                }
            }

            if (profiles == null) {
                profiles = new ArrayList<>();
            }
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
            long previousOrder = profiles.get(from).userOrder;
            for (int i = from; i < to; i += step) {
                Profile next = profiles.get(i + step);
                long order = next.userOrder;
                next.userOrder = previousOrder;
                previousOrder = order;
                profiles.set(i, next);
                app.profileManager.updateProfile(next);
            }
            first.userOrder = previousOrder;
            profiles.set(to, first);
            app.profileManager.updateProfile(first);
            notifyItemMoved(from, to);
        }

        public void remove(int pos) {
            Profile remove = profiles.remove(pos);
            app.profileManager.delProfile(remove.id);
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
                    app.profileManager.delProfile(item.id);
                    if (item.id == app.profileId()) {
                        app.profileId(-1);
                    }
                }
            }
        }
    }

    private class SSRSubViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnKeyListener {

        private SSRSub item;
        private TextView text;

        public SSRSubViewHolder(View view) {
            super(view);
            text = (TextView) itemView.findViewById(android.R.id.text2);
            itemView.setOnClickListener(this);
        }

        public void updateText() {
            updateText(false);
        }

        public void updateText(boolean isShowUrl) {
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(this.item.url_group).append("\n");
            if (isShowUrl) {
                int start = builder.length();
                builder.append(this.item.url);
                builder.setSpan(new TextAppearanceSpan(ProfileManagerActivity.this, android.R.style.TextAppearance_Small),
                        start,
                        builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    text.setText(builder);
                }
            });
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
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            return true;
        }
    }

    private class SSRSubAdapter extends RecyclerView.Adapter<SSRSubViewHolder> {

        private List<SSRSub> profiles;

        public SSRSubAdapter() {
            List<SSRSub> all = app.ssrsubManager.getAllSSRSubs();
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
