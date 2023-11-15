package com.bringyour.network

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bringyour.client.AuthLoginArgs
import com.bringyour.network.databinding.ActivityLoginBinding
import com.google.android.gms.common.SignInButton
import kotlin.math.sign


class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("BY", "LOGIN ACTIVITY")

        if ((application as MainApplication).byDevice != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // else set up the activity as normal


        // FIXME ui is one box to enter client jwt, and submit

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)



        val videoView = findViewById<VideoView>(R.id.video_view)
        videoView.setOnPreparedListener {
            it.isLooping = true
        }

        val path = "android.resource://" + packageName + "/" + R.raw.login
        videoView.setVideoURI(Uri.parse(path))
        videoView.start()


        /*
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Check for existing Google Sign In account, if the user is already signed in
// the GoogleSignInAccount will be non-null.
        // Check for existing Google Sign In account, if the user is already signed in
// the GoogleSignInAccount will be non-null.
        val account = GoogleSignIn.getLastSignedInAccount(this)
//        updateUI(account)
*/

        // Set the dimensions of the sign-in button.
        // Set the dimensions of the sign-in button.
        val signInButton = findViewById<SignInButton>(R.id.google_sign_in_button)
        signInButton.setSize(SignInButton.SIZE_WIDE)
        signInButton.setColorScheme(SignInButton.COLOR_LIGHT)


        val loginButton = findViewById<Button>(R.id.login_user_auth_button)
        loginButton.setOnClickListener {
            val userAuth = findViewById<EditText>(R.id.login_user_auth).text.toString()

            var args = AuthLoginArgs()
            args.userAuth = userAuth

            Log.i("LoginActivity", "GOT USER AUTH " + userAuth)

            (application as MainApplication).byApi?.authLogin(args, { result, err ->
                Log.i("LoginActivity", "GOT LOGIN RESULT " + result)

                if (err == null) {
                    if (result.authAllowed != null && result.authAllowed.contains("password")) {
                        var intent = Intent(this, LoginWithPasswordActivity::class.java)
                        intent.putExtra("userAuth", userAuth)
                        startActivity(intent)
                    }
                }
            })

        }

    }
}