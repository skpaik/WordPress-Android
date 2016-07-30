package org.wordpress.android.ui.prefs;

import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.stores.Dispatcher;
import org.wordpress.android.stores.generated.AccountActionBuilder;
import org.wordpress.android.stores.model.AccountModel;
import org.wordpress.android.stores.model.SiteModel;
import org.wordpress.android.stores.store.AccountStore;
import org.wordpress.android.stores.store.AccountStore.OnAccountChanged;
import org.wordpress.android.stores.store.AccountStore.PostAccountSettingsPayload;
import org.wordpress.android.stores.store.SiteStore;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.SiteUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.ToastUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;


@SuppressWarnings("deprecation")
public class AccountSettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private Preference mUsernamePreference;
    private EditTextPreferenceWithValidation mEmailPreference;
    private DetailListPreference mPrimarySitePreference;
    private EditTextPreferenceWithValidation mWebAddressPreference;
    private Snackbar mEmailSnackbar;

    @Inject Dispatcher mDispatcher;
    @Inject AccountStore mAccountStore;
    @Inject SiteStore mSiteStore;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getActivity().getApplication()).component().inject(this);

        setRetainInstance(true);
        addPreferencesFromResource(R.xml.account_settings);

        mUsernamePreference = findPreference(getString(R.string.pref_key_username));
        mEmailPreference = (EditTextPreferenceWithValidation) findPreference(getString(R.string.pref_key_email));
        mPrimarySitePreference = (DetailListPreference) findPreference(getString(R.string.pref_key_primary_site));
        mWebAddressPreference = (EditTextPreferenceWithValidation) findPreference(getString(R.string.pref_key_web_address));

        mEmailPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        mEmailPreference.setValidationType(EditTextPreferenceWithValidation.ValidationType.EMAIL);
        mWebAddressPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        mWebAddressPreference.setValidationType(EditTextPreferenceWithValidation.ValidationType.URL);
        mWebAddressPreference.setDialogMessage(R.string.web_address_dialog_hint);

        mEmailPreference.setOnPreferenceChangeListener(this);
        mPrimarySitePreference.setOnPreferenceChangeListener(this);
        mWebAddressPreference.setOnPreferenceChangeListener(this);

        // load site list asynchronously
        // TODO: STORES: call the site store here
        new LoadSitesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View coordinatorView = inflater.inflate(R.layout.preference_coordinator, container, false);
        CoordinatorLayout coordinator = (CoordinatorLayout) coordinatorView.findViewById(R.id.coordinator);
        View preferenceView = super.onCreateView(inflater, coordinator, savedInstanceState);
        coordinator.addView(preferenceView);
        return coordinatorView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        refreshAccountDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (NetworkUtils.isNetworkAvailable(getActivity())) {
            mDispatcher.dispatch(AccountActionBuilder.newFetchSettingsAction());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mDispatcher.register(this);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        mDispatcher.unregister(this);
        super.onStop();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (newValue == null) return false;

        if (preference == mEmailPreference) {
            updateEmail(newValue.toString());
            showPendingEmailChangeSnackbar(newValue.toString());
            mEmailPreference.setEnabled(false);
            return false;
        } else if (preference == mPrimarySitePreference) {
            changePrimaryBlogPreference(Long.parseLong(newValue.toString()));
            updatePrimaryBlog(newValue.toString());
            return false;
        } else if (preference == mWebAddressPreference) {
            mWebAddressPreference.setSummary(newValue.toString());
            updateWebAddress(newValue.toString());
            return false;
        }

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshAccountDetails() {
        AccountModel account = mAccountStore.getAccount();
        mUsernamePreference.setSummary(account.getUserName());
        mEmailPreference.setSummary(account.getEmail());
        mWebAddressPreference.setSummary(account.getWebAddress());
        changePrimaryBlogPreference(account.getPrimaryBlogId());

        checkIfEmailChangeIsPending();
    }

    private void checkIfEmailChangeIsPending() {
        AccountModel account = mAccountStore.getAccount();
        if (account.getPendingEmailChange()) {
            showPendingEmailChangeSnackbar(account.getNewEmail());
        } else if (mEmailSnackbar != null && mEmailSnackbar.isShown()){
            mEmailSnackbar.dismiss();
        }
        mEmailPreference.setEnabled(!account.getPendingEmailChange());
    }

    private void showPendingEmailChangeSnackbar(String newEmail) {
        if (getView() != null) {
            if (mEmailSnackbar == null || !mEmailSnackbar.isShown()) {
                View.OnClickListener clickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        cancelPendingEmailChange();
                    }
                };

                mEmailSnackbar = Snackbar
                        .make(getView(), "", Snackbar.LENGTH_INDEFINITE).setAction(getString(R.string.button_revert), clickListener);
                mEmailSnackbar.getView().setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.grey_dark));
                mEmailSnackbar.setActionTextColor(ContextCompat.getColor(getActivity(), R.color.blue_medium));
                TextView textView = (TextView) mEmailSnackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
                textView.setMaxLines(4);
            }
            // instead of creating a new snackbar, update the current one to avoid the jumping animation
            mEmailSnackbar.setText(getString(R.string.pending_email_change_snackbar, newEmail));
            if (!mEmailSnackbar.isShown()) {
                mEmailSnackbar.show();
            }
        }
    }

    private void changePrimaryBlogPreference(long siteRemoteId) {
        mPrimarySitePreference.setValue(String.valueOf(siteRemoteId));
        SiteModel site = mSiteStore.getSiteBySiteId(siteRemoteId);
        if (site != null) {
            mPrimarySitePreference.setSummary(StringUtils.unescapeHTML(SiteUtils.getSiteNameOrHomeURL(site)));
            mPrimarySitePreference.refreshAdapter();
        }
    }

    private void cancelPendingEmailChange() {
        PostAccountSettingsPayload payload = new PostAccountSettingsPayload();
        payload.params = new HashMap<>();
        payload.params.put("user_email_change_pending", "false");
        mDispatcher.dispatch(AccountActionBuilder.newPostSettingsAction(payload));
        if (mEmailSnackbar != null && mEmailSnackbar.isShown()) {
            mEmailSnackbar.dismiss();
        }
    }

    private void updateEmail(String newEmail) {
        PostAccountSettingsPayload payload = new PostAccountSettingsPayload();
        payload.params = new HashMap<>();
        payload.params.put("user_email", newEmail);
        mDispatcher.dispatch(AccountActionBuilder.newPostSettingsAction(payload));
    }

    private void updatePrimaryBlog(String blogId) {
        PostAccountSettingsPayload payload = new PostAccountSettingsPayload();
        payload.params = new HashMap<>();
        payload.params.put("primary_site_ID", blogId);
        mDispatcher.dispatch(AccountActionBuilder.newPostSettingsAction(payload));
    }

    public void updateWebAddress(String newWebAddress) {
        PostAccountSettingsPayload payload = new PostAccountSettingsPayload();
        payload.params = new HashMap<>();
        payload.params.put("user_URL", newWebAddress);
        mDispatcher.dispatch(AccountActionBuilder.newPostSettingsAction(payload));
    }

    @Subscribe
    public void onAccountChanged(OnAccountChanged event) {
        if (isAdded()) {
            // TODO: STORES: manage errors
            refreshAccountDetails();
        }
    }

    // TODO: STORES: manage errors - function below should be killed
    public void onEventMainThread(PrefsEvents.AccountSettingsFetchError event) {
        if (isAdded()) {
            ToastUtils.showToast(getActivity(), R.string.error_fetch_account_settings, ToastUtils.Duration.LONG);
        }
    }

    // TODO: STORES: manage errors - function below should be killed
    public void onEventMainThread(PrefsEvents.AccountSettingsPostError event) {
        if (isAdded()) {
            ToastUtils.showToast(getActivity(), R.string.error_post_account_settings, ToastUtils.Duration.LONG);

            // we optimistically show the email change snackbar, if that request fails, we should remove the snackbar
            checkIfEmailChangeIsPending();
        }
    }

    public static String[] getSiteNamesFromSites(List<SiteModel> sites) {
        List<String> blogNames = new ArrayList<>();
        for (SiteModel site : sites) {
            blogNames.add(SiteUtils.getSiteNameOrHomeURL(site));
        }
        return blogNames.toArray(new String[blogNames.size()]);
    }

    public static String[] getHomeURLOrHostNamesFromSites(List<SiteModel> sites) {
        List<String> urls = new ArrayList<>();
        for (SiteModel site : sites) {
            urls.add(SiteUtils.getHomeURLOrHostName(site));
        }
        return urls.toArray(new String[urls.size()]);
    }

    public static String[] getSiteIdsFromSites(List<SiteModel> sites) {
        List<String> ids = new ArrayList<>();
        for (SiteModel site : sites) {
            ids.add(String.valueOf(site.getSiteId()));
        }
        return ids.toArray(new String[ids.size()]);
    }


    /*
     * AsyncTask which loads sites from database for primary site preference
     */
    private class LoadSitesTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected Void doInBackground(Void... params) {
            List<SiteModel> sites = mSiteStore.getDotComSites();
            mPrimarySitePreference.setEntries(getSiteNamesFromSites(sites));
            mPrimarySitePreference.setEntryValues(getSiteIdsFromSites(sites));
            mPrimarySitePreference.setDetails(getHomeURLOrHostNamesFromSites(sites));
            return null;
        }

        @Override
        protected void onPostExecute(Void results) {
            super.onPostExecute(results);
            mPrimarySitePreference.refreshAdapter();
        }
    }
}
