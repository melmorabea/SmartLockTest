package m.smartlock;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialPickerConfig;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.HintRequest;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import static android.app.Activity.RESULT_OK;

/**
 * Created by mahmoudelmorabea on 07/09/16.
 */

public class SmartLockManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "SmartLockManager";

    private static final int RC_READ = 500;
    private static final int RC_HINT = 501;
    private static final int RC_SAVE = 502;

    private FragmentActivity activity;

    private GoogleApiClient apiClient;

    public SmartLockManager(FragmentActivity activity) {
        this.activity = activity;
    }

    private GoogleApiClient getAuthenticationClient() {
        return new GoogleApiClient.Builder(activity)
                .addConnectionCallbacks(this)
                .enableAutoManage(activity, this)
                .addApi(Auth.CREDENTIALS_API)
                .build();
    }

    private CredentialRequest getSignInCredentialsRequest() {
        return new CredentialRequest.Builder()
                .setPasswordLoginSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE)
                .build();
    }

    private HintRequest getUserDataHintRequest() {
        return new HintRequest.Builder()
                .setHintPickerConfig(new CredentialPickerConfig.Builder()
                        .setShowCancelButton(true)
                        .build())
                .setEmailAddressIdentifierSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE)
                .build();
    }

    private void requestCredentials() {
        Auth.CredentialsApi.request(apiClient, getSignInCredentialsRequest()).setResultCallback(
                new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(@NonNull CredentialRequestResult credentialRequestResult) {
                        if (credentialRequestResult.getStatus().isSuccess()) {
                            onCredentialRetrieved(credentialRequestResult.getCredential());
                        } else {
                            resolveCredentialsRequestResult(credentialRequestResult.getStatus());
                        }
                    }
                });
    }

    private void onCredentialRetrieved(Credential credential) {
        // TODO Notify client that credentials has been retrieved
    }

    private void onCredentialsHintReceived(Credential credential) {
        // TODO Notify client that hint data has been retrieved
    }

    private void resolveCredentialsRequestResult(Status status) {
        if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED) {
            // Prompt the user to choose a saved credential; do not show the hint
            // selector.
            try {
                status.startResolutionForResult(activity, RC_READ);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "STATUS: Failed to send resolution.");
            }
        } else {
            // The user must create an account or sign in manually.
            Log.e(TAG, "STATUS: Unsuccessful credential request. Will Try to get hint!");
            requestUserHintData();
        }
    }

    private void requestUserHintData() {
        PendingIntent intent = Auth.CredentialsApi.getHintPickerIntent(apiClient, getUserDataHintRequest());
        try {
            activity.startIntentSenderForResult(intent.getIntentSender(), RC_HINT, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Could not start hint picker Intent", e);
        }
    }

    private void saveCredentials(String id, String password) {
        Credential credential = new Credential.Builder(id).setPassword(password).build();
        Auth.CredentialsApi.save(apiClient, credential).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            // TODO notify client that credentials has been saved
                        } else {
                            if (status.hasResolution()) {
                                // Try to resolve the save request. This will prompt the user if
                                // the credential is new.
                                try {
                                    status.startResolutionForResult(activity, RC_SAVE);
                                } catch (IntentSender.SendIntentException e) {
                                    // Could not resolve the request
                                    Log.e(TAG, "STATUS: Failed to send resolution.", e);
                                }
                            } else {
                                // Request has no resolution
                                // TODO notify client that credentials failed to be saved
                            }
                        }
                    }
                });
    }

    // API

    public void start() {
        if (apiClient == null)
            apiClient = getAuthenticationClient();

        requestCredentials();
    }

    public void stop() {

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_READ) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                onCredentialRetrieved(credential);
            } else {
                Log.e(TAG, "Credential Read Failed");
            }
        } else if (requestCode == RC_HINT) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                onCredentialsHintReceived(credential);
            } else {
                Log.e(TAG, "Hint Read Failed");
            }
        } else if (requestCode == RC_SAVE) {
            if (resultCode == RESULT_OK) {
                // TODO Notify client that credentials has been saved
            } else {
                // TODO Notify client that credentials saving has been cancelled
            }
        }
    }

    // Callbacks

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

}
