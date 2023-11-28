package com.bringyour.network.ui.login

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthLoginArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.MainService
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginInitialBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking


class LoginInitialFragment : Fragment(), ActivityResultCallback<ActivityResult> {
    private var _binding: FragmentLoginInitialBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null

    private var loginActivity: LoginActivity? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginInitialBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity







        val videoView = root.findViewById<VideoView>(R.id.video_view)
        videoView.setOnPreparedListener {
            it.isLooping = true
        }

        val path = "android.resource://" + app.packageName + "/" + R.raw.login
        videoView.setVideoURI(Uri.parse(path))
        videoView.start()


        // Check for existing Google Sign In account, if the user is already signed in
// the GoogleSignInAccount will be non-null.
        // Check for existing Google Sign In account, if the user is already signed in
// the GoogleSignInAccount will be non-null.

        val googleSignInButton = root.findViewById<SignInButton>(R.id.google_sign_in_button)
        googleSignInButton.setSize(SignInButton.SIZE_STANDARD)
        googleSignInButton.setColorScheme(SignInButton.COLOR_LIGHT)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.google_client_id))
            .requestEmail()
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        val mGoogleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        val launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            this
        )


        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        // TODO show continue with X button
//        updateUI(account)

        if (account != null) {
            // FIXME show sign in as X button


            setGoogleSignInButtonText(googleSignInButton, "Continue as ${account.email}")

            googleSignInButton.setOnClickListener {
                googleLogin(account)
            }
        } else {

            setGoogleSignInButtonText(googleSignInButton, "Continue with Google")

            googleSignInButton.setOnClickListener {

                launcher.launch(mGoogleSignInClient.signInIntent)
            }
        }






        // Set the dimensions of the sign-in button.
        // Set the dimensions of the sign-in button.



        val userAuth = root.findViewById<EditText>(R.id.login_user_auth)

        val loginSpinner = root.findViewById<ProgressBar>(R.id.login_user_auth_spinner)
        loginSpinner.visibility = GONE

        val loginButton = root.findViewById<Button>(R.id.login_user_auth_button)
        loginButton.setOnClickListener {


            Log.i("LoginActivity", "GOT USER AUTH " + userAuth)

            googleSignInButton.isEnabled = false
            userAuth.isEnabled = false
            loginButton.isEnabled = false
            loginSpinner.visibility = VISIBLE

            val args = AuthLoginArgs()
            args.userAuth = userAuth.text.toString()


            app.byApi?.authLogin(args, { result, err ->
                Log.i("LoginActivity", "GOT LOGIN RESULT " + result)

                runBlocking(Dispatchers.Main.immediate) {

                    googleSignInButton.isEnabled = true
                    userAuth.isEnabled = true
                    loginButton.isEnabled = true
                    loginSpinner.visibility = GONE

                    if (err == null) {
                        if (result.authAllowed != null && result.authAllowed.contains("password")) {


                            val args = Bundle()
                            args.putString("userAuth", result.userAuth)

                            findNavController().navigate(R.id.navigation_password, args)


    //                        var intent = Intent(activity, LoginWithPasswordActivity::class.java)
    //                        intent.putExtra("userAuth", userAuth)
    //                        startActivity(intent)
                        }
                    }

                }
            })

        }

        val createButton = root.findViewById<Button>(R.id.login_create_button)
        createButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_create_network)
        }

        return root
    }

    override fun onStart() {
        super.onStart()


        // immutable shadow
        val loginActivity = loginActivity ?: return

        loginActivity.supportActionBar?.hide()
    }




    override fun onActivityResult(result: ActivityResult) {
        Log.i("Main","ACTIVITY RESULT")
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);

            // The Task returned from this call is always completed, no need to attach
            // a listener.
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)

            googleLogin(account)
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w("LoginInitialFragment", "signInResult:failed code=" + e.statusCode)

        }
    }


    fun setGoogleSignInButtonText(googleSignInButton: SignInButton, text: String) {
        // set the text on the first text view in the button
        for (i in 0 until googleSignInButton.childCount) {
            val v = googleSignInButton.getChildAt(i)
            if (v is TextView) {
                v.text = text
                return
            }
        }
    }

    fun googleLogin(account: GoogleSignInAccount) {
        val args = Bundle()
        args.putString("jwtType", "Google")
        args.putString("jwt", account.idToken)
        args.putString("userName", account.displayName)
        args.putString("userAuth", account.email)


        findNavController().navigate(R.id.navigation_create_network_auth_jwt, args)
    }
}