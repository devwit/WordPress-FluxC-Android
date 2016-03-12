package org.wordpress.android.stores.network.rest.wpcom.account;

import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;

import org.wordpress.android.stores.Dispatcher;
import org.wordpress.android.stores.Payload;
import org.wordpress.android.stores.action.AccountAction;
import org.wordpress.android.stores.model.AccountModel;
import org.wordpress.android.stores.network.UserAgent;
import org.wordpress.android.stores.network.rest.wpcom.BaseWPComRestClient;
import org.wordpress.android.stores.network.rest.wpcom.WPComGsonRequest;
import org.wordpress.android.stores.network.rest.wpcom.auth.AccessToken;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AccountRestClient extends BaseWPComRestClient {
    public static final String WPCOM_ACCOUNT_ENDPOINT = "/me";
    public static final String WPCOM_ACCOUNT_SETTINGS_ENDPOINT = "/me/settings";

    public static class AccountError implements Payload {
        private VolleyError mError;

        public AccountError(VolleyError error) {
            mError = error;
        }

        public VolleyError getError() {
            return mError;
        }
    }

    @Inject
    public AccountRestClient(Dispatcher dispatcher, RequestQueue requestQueue,
                             AccessToken accessToken, UserAgent userAgent) {
        super(dispatcher, requestQueue, accessToken, userAgent);
    }

    /**
     * Performs an HTTP GET call to the {@link #WPCOM_ACCOUNT_ENDPOINT} endpoint. If the call
     * succeeds a {@link AccountAction#FETCHED} action is dispatched with the response data stored
     * in the payload as {@link AccountModel}.
     *
     * If the call fails a {@link AccountAction#ERROR_FETCH_ACCOUNT} action is dispatched with
     * an {@link AccountError} payload.
     */
    public void fetchAccount() {
        String url = WPCOM_PREFIX_V1_1 + WPCOM_ACCOUNT_ENDPOINT;
        add(new WPComGsonRequest<>(Method.GET, url, null, AccountResponse.class,
                new Listener<AccountResponse>() {
                    @Override
                    public void onResponse(AccountResponse response) {
                        AccountModel accountModel = responseToAccountModel(response);
                        mDispatcher.dispatch(AccountAction.FETCHED_ACCOUNT, accountModel);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dispatchError(AccountAction.ERROR_FETCH_ACCOUNT, error);
                    }
                }
        ));
    }

    /**
     * Performs an HTTP GET call to the {@link #WPCOM_ACCOUNT_SETTINGS_ENDPOINT} endpoint. If the
     * call succeeds a {@link AccountAction#FETCHED_SETTINGS} action is dispatched with the response
     * data stored in the payload as {@link AccountModel}.
     *
     * If the call fails a {@link AccountAction#ERROR_FETCH_ACCOUNT_SETTINGS} action is dispatched
     * with an {@link AccountError} payload.
     */
    public void fetchAccountSettings() {
        String url = WPCOM_PREFIX_V1_1 + WPCOM_ACCOUNT_SETTINGS_ENDPOINT;
        add(new WPComGsonRequest<>(Method.GET, url, null, AccountSettingsResponse.class,
                new Listener<AccountSettingsResponse>() {
                    @Override
                    public void onResponse(AccountSettingsResponse response) {
                        AccountModel settings = responseToAccountSettingsModel(response);
                        mDispatcher.dispatch(AccountAction.FETCHED_SETTINGS, settings);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dispatchError(AccountAction.ERROR_FETCH_ACCOUNT_SETTINGS, error);
                    }
                }
        ));
    }

    /**
     * Performs an HTTP POST call to the {@link #WPCOM_ACCOUNT_SETTINGS_ENDPOINT} endpoint. If the
     * call succeeds a {@link AccountAction#POSTED_SETTINGS} action is dispatched with the response
     * data stored in the payload as {@link AccountModel}.
     *
     * If the call fails a {@link AccountAction#ERROR_POST_ACCOUNT_SETTINGS} action is dispatched
     * with an {@link AccountError} payload.
     *
     * No HTTP POST call is made if the given parameter map is null or contains no entries.
     */
    public void postAccountSettings(Map<String, String> params) {
        if (params == null || params.isEmpty()) return;
        String url = WPCOM_PREFIX_V1_1 + WPCOM_ACCOUNT_SETTINGS_ENDPOINT;
        add(new WPComGsonRequest<>(Method.POST, url, params, AccountSettingsResponse.class,
                new Listener<AccountSettingsResponse>() {
                    @Override
                    public void onResponse(AccountSettingsResponse response) {
                        AccountModel settings = responseToAccountSettingsModel(response);
                        mDispatcher.dispatch(AccountAction.POSTED_SETTINGS, settings);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dispatchError(AccountAction.ERROR_POST_ACCOUNT_SETTINGS, error);
                    }
                }
        ));
    }

    private void dispatchError(AccountAction action, VolleyError error) {
        AppLog.e(T.API, "Account Volley error", error);
        mDispatcher.dispatch(action, new AccountError(error));
    }

    private AccountModel responseToAccountModel(AccountResponse from) {
        AccountModel account = new AccountModel();
        account.setUserName(from.username);
        account.setUserId(from.ID);
        account.setDisplayName(from.display_name);
        account.setProfileUrl(from.profile_URL);
        account.setAvatarUrl(from.avatar_URL);
        account.setPrimaryBlogId(from.primary_blog);
        account.setSiteCount(from.site_count);
        account.setVisibleSiteCount(from.visible_site_count);
        account.setEmail(from.email);
        return account;
    }

    private AccountModel responseToAccountSettingsModel(AccountSettingsResponse from) {
        AccountModel accountSettings = new AccountModel();
        accountSettings.setUserName(from.user_login);
        accountSettings.setPrimaryBlogId(from.primary_site_ID);
        accountSettings.setFirstName(from.first_name);
        accountSettings.setLastName(from.last_name);
        accountSettings.setAboutMe(from.description);
        accountSettings.setDate(from.date);
        accountSettings.setNewEmail(from.new_user_email);
        accountSettings.setPendingEmailChange(from.user_email_change_pending);
        accountSettings.setWebAddress(from.user_URL);
        return accountSettings;
    }
}
