package com.bringyour.network.ui.login

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
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
import com.bringyour.network.MainActivity
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
import org.w3c.dom.Text


class LoginInitialFragment : Fragment() {
    private var _binding: FragmentLoginInitialBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null

    private var loginActivity: LoginActivity? = null

    private var videoView: VideoView? = null

    private var loginButton: Button? = null
    private var hasUserAuth: Boolean = false

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

        videoView = root.findViewById<VideoView>(R.id.video_view)

        videoView?.setOnPreparedListener {
            it.isLooping = true
        }

        val path = "android.resource://" + app.packageName + "/" + R.raw.login
        videoView?.setVideoURI(Uri.parse(path))


        val googleSignInButton = root.findViewById<SignInButton>(R.id.google_sign_in_button)
        googleSignInButton.setSize(SignInButton.SIZE_STANDARD)
        googleSignInButton.setColorScheme(SignInButton.COLOR_LIGHT)

        val userAuth = root.findViewById<EditText>(R.id.login_user_auth)
        loginButton = root.findViewById<Button>(R.id.login_user_auth_button)
        val loginSpinner = root.findViewById<ProgressBar>(R.id.login_user_auth_spinner)
        val loginError = root.findViewById<TextView>(R.id.login_error)

        loginButton?.isEnabled = false

        loginSpinner.visibility = View.GONE
        loginError.visibility = View.GONE

        val inProgress = { busy: Boolean ->
            if (busy) {
                googleSignInButton.isEnabled = false
                userAuth.isEnabled = false
                loginButton?.isEnabled = false
                loginSpinner.visibility = View.VISIBLE
            } else {
                googleSignInButton.isEnabled = true
                userAuth.isEnabled = true
                loginSpinner.visibility = View.GONE
                syncLoginButton()
            }
        }

        userAuth.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val userAuthStr = userAuth.text.toString().trim()
                hasUserAuth = (Patterns.EMAIL_ADDRESS.matcher(userAuthStr).matches() ||
                                Patterns.PHONE.matcher(userAuthStr).matches())
                syncLoginButton()
            }
        })

        val login = {
            inProgress(true)

            val args = AuthLoginArgs()
            args.userAuth = userAuth.text.toString().trim()

            app.byApi?.authLogin(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    Log.i("LoginInitialFragment", "GOT RESULT " + result)

                    if (err != null) {
                        loginError.visibility = View.VISIBLE
                        loginError.text = err.message
                    } else if (result.error != null) {
                        loginError.visibility = View.VISIBLE
                        loginError.text = result.error.message
                    } else if (result.authAllowed != null) {
                        if (result.authAllowed.contains("password")) {
                            loginError.visibility = View.GONE
                            val navArgs = Bundle()
                            navArgs.putString("userAuth", result.userAuth)

                            findNavController().navigate(R.id.navigation_password, navArgs)
                        } else {
                            val authAllowed = mutableListOf<String>()
                            for (i in 0 until result.authAllowed.len()) {
                                authAllowed.add(result.authAllowed.get(i))
                            }

                            loginError.visibility = View.VISIBLE
                            loginError.text = getString(R.string.login_error_auth_allowed, authAllowed.joinToString(","))
                        }
                    } else {
                        // new network

                        val navArgs = Bundle()
                        navArgs.putString("userAuth", result.userAuth)

                        findNavController().navigate(R.id.navigation_create_network, navArgs)
                    }
                }
            }
        }

        userAuth?.setOnEditorActionListener { _, _, keyEvent ->
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    if (loginButton?.isEnabled == true) {
                        login()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        loginButton?.setOnClickListener {
            login()
        }

        val createButton = root.findViewById<Button>(R.id.login_create_network_button)
        createButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_create_network)
        }

        val googleLogin = { account: GoogleSignInAccount ->
            Log.i("LoginInitialFragment", "GOOGLE LOGIN")

            inProgress(true)

            val args = AuthLoginArgs()
            args.authJwt = account.idToken
            args.authJwtType = "google"

            app.byApi?.authLogin(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    if (err != null) {
                        loginError.visibility = View.VISIBLE
                        loginError.text = err.message
                    } else if (result.error != null) {
                        loginError.visibility = View.VISIBLE
                        loginError.text = result.error.message
                    } else if (result.network != null && 0 < result.network.byJwt.length) {
                        loginError.visibility = View.GONE

                        app.login(result.network.byJwt)

                        inProgress(true)

                        loginActivity?.authClientAndFinish { error ->
                            inProgress(false)

                            if (error == null) {
                                loginError.visibility = View.GONE
                            } else {
                                loginError.visibility = View.VISIBLE
                                loginError.text = error
                            }
                        }
                    } else if (result.authAllowed != null) {
                        val authAllowed = mutableListOf<String>()
                        for (i in 0 until result.authAllowed.len()) {
                            authAllowed.add(result.authAllowed.get(i))
                        }

                        loginError.visibility = View.VISIBLE
                        loginError.text = getString(R.string.login_error_auth_allowed, authAllowed.joinToString(","))
                    } else {
                        loginError.visibility = View.GONE

                        val navArgs = Bundle()
                        navArgs.putString("authJwtType", "google")
                        navArgs.putString("authJwt", account.idToken)
                        navArgs.putString("userName", result.userName)
                        navArgs.putString("userAuth", account.email)

                        findNavController().navigate(R.id.navigation_create_network_auth_jwt, navArgs)
                    }
                }
            }
        }

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null) {
            setGoogleSignInButtonText(googleSignInButton, "Continue as ${account.email}")
            googleSignInButton.setOnClickListener {
                googleLogin(account)
            }
        } else {
            val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_client_id))
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(requireContext(), googleSignInOpts)

            val launcher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.i("LoginInitialFragment", "GOT GOOGLE RESULT")
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    googleLogin(account)
                } catch (e: ApiException) {
                    Log.w("LoginInitialFragment", "signInResult:failed code=" + e.statusCode)
                }
            }

            setGoogleSignInButtonText(googleSignInButton, "Continue with Google")
            googleSignInButton.setOnClickListener {
                launcher.launch(googleSignInClient.signInIntent)
            }
        }

        return root
    }

    override fun onStart() {
        super.onStart()

        // immutable shadow
        val loginActivity = loginActivity ?: return
        loginActivity.supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()

        videoView?.start()
    }

    override fun onPause() {
        super.onPause()

        videoView?.stopPlayback()
    }

    private fun syncLoginButton() {
        loginButton?.isEnabled = hasUserAuth
    }


    companion object {
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
    }
}