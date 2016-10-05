package m.smartlock;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialPickerConfig;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.HintRequest;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.squareup.picasso.Picasso;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int RC_READ = 500;
    private static final int RC_HINT = 501;
    private static final int RC_SAVE = 502;

    private static final String TAG = "SmartLock";

    private TextView textView;
    private Button signInButton;
    private Button saveButton;
    private ImageView imageView;

    private GoogleApiClient apiClient;

    private Credential hintCredentials;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.txt);
        signInButton = (Button) findViewById(R.id.button);
        saveButton = (Button) findViewById(R.id.save_button);
        imageView = (ImageView) findViewById(R.id.imageView);

        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveCredentials();
            }
        });

        apiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, this)
                .addApi(Auth.CREDENTIALS_API)
                .build();
        Log.d("App", "Activity created");
    }

    @Override
    protected void onStart() {
        super.onStart();
        apiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        apiClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_READ) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                onCredentialRetrieved(credential);
            } else {
                textView.setText("Credential Read Failed");
            }
        } else if (requestCode == RC_HINT) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                onCredentialsHintReceived(credential);
            } else {
                textView.setText("Hint Read Failed");
            }
        } else if (requestCode == RC_SAVE) {
            if (resultCode == RESULT_OK) {
                textView.setText("Credentials saved");
            } else {
                textView.setText("SAVE: Canceled by user");
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        signInButton.setEnabled(true);
    }

    @Override
    public void onConnectionSuspended(int i) {
        signInButton.setEnabled(false);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        signInButton.setEnabled(false);
    }

    private void signIn() {
        CredentialRequest credentialRequest = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE)
                .build();

        Auth.CredentialsApi.request(apiClient, credentialRequest).setResultCallback(
                new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(CredentialRequestResult credentialRequestResult) {
                        if (credentialRequestResult.getStatus().isSuccess()) {
                            // See "Handle successful credential requests"
                            onCredentialRetrieved(credentialRequestResult.getCredential());
                        } else {
                            // See "Handle unsuccessful and incomplete credential requests"
                            resolveResult(credentialRequestResult.getStatus());
                        }
                    }
                });
    }

    private void saveCredentials() {
        if (hintCredentials == null)
            return;

        Credential credential = new Credential.Builder(hintCredentials.getId())
                .setPassword("Silly password").build();

        Auth.CredentialsApi.save(apiClient, credential).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.d(TAG, "SAVE: OK");
                            textView.setText("Credentials saved");
                        } else {
                            if (status.hasResolution()) {
                                // Try to resolve the save request. This will prompt the user if
                                // the credential is new.
                                try {
                                    status.startResolutionForResult(MainActivity.this, RC_SAVE);
                                } catch (IntentSender.SendIntentException e) {
                                    // Could not resolve the request
                                    Log.e(TAG, "STATUS: Failed to send resolution.", e);
                                    Toast.makeText(getApplicationContext(), "Save failed", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                // Request has no resolution
                                Toast.makeText(getApplicationContext(), "Save failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void onCredentialRetrieved(Credential credential) {
        String accountType = credential.getAccountType();
        if (accountType == null) {
            // Sign the user in with information from the Credential.
            signInWithPassword(credential.getId(), credential.getPassword());
        } else if (accountType.equals(IdentityProviders.GOOGLE)) {
            // The user has previously signed in with Google Sign-In. Silently
            // sign in the user with the same ID.
            // See https://developers.google.com/identity/sign-in/android/
            GoogleSignInOptions gso =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build();
            apiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this, this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .setAccountName(credential.getId())
                    .build();
            OptionalPendingResult<GoogleSignInResult> opr =
                    Auth.GoogleSignInApi.silentSignIn(apiClient);
            // ...
        }
    }

    private void onCredentialsHintReceived(Credential credential) {
        hintCredentials = credential;
        saveButton.setEnabled(true);
        textView.setText("ID: " + credential.getId() + "\n" +
                         "Name: " + credential.getName() + "\n" +
                         "Account type: " + credential.getAccountType());
        Picasso picasso = Picasso.with(this);
        picasso.setLoggingEnabled(true);
        picasso.load(credential.getProfilePictureUri()).into(imageView);
    }

    private void resolveResult(Status status) {
        if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED) {
            // Prompt the user to choose a saved credential; do not show the hint
            // selector.
            try {
                status.startResolutionForResult(this, RC_READ);
            } catch (IntentSender.SendIntentException e) {
                textView.setText("STATUS: Failed to send resolution.");
            }
        } else {
            // The user must create an account or sign in manually.
            textView.setText("STATUS: Unsuccessful credential request. Will Try to get hint!");
            getUserHintData();
        }
    }

    private void getUserHintData() {
        HintRequest hintRequest = new HintRequest.Builder()
                .setHintPickerConfig(new CredentialPickerConfig.Builder()
                        .setShowCancelButton(true)
                        .build())
                .setEmailAddressIdentifierSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE)
                .build();

        PendingIntent intent =
                Auth.CredentialsApi.getHintPickerIntent(apiClient, hintRequest);
        try {
            startIntentSenderForResult(intent.getIntentSender(), RC_HINT, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Could not start hint picker Intent", e);
        }
    }

    private void signInWithPassword(String id, String password) {
        textView.setText("Usr: " + id + "\n" + "Pass: " + password);
    }

}
