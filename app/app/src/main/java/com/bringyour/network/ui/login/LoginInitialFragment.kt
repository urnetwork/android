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
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthLoginArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.LoginInitialActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.MainService
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginInitialBinding
import com.bringyour.network.ui.theme.URNetworkTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Text


class LoginInitialFragment : Fragment() {
    // private var _binding: FragmentLoginInitialBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    // private val binding get() = _binding!!

    private var app : MainApplication? = null

    private var loginActivity: LoginActivity? = null

    // private var videoView: VideoView? = null

    private var loginButton: Button? = null
    private var hasUserAuth: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val api = app?.byApi

        // val appLogin = app?.login

        // getString(R.string.login_error_auth_allowed, authAllowed.joinToString(","))

//        _binding = FragmentLoginInitialBinding.inflate(inflater, container, false)
//        val root: View = binding.root
//
//        app = activity?.application as MainApplication
//        // immutable shadow
//        val app = app ?: return root
//
//        loginActivity = activity as LoginActivity

//        val googleSignInButton = root.findViewById<SignInButton>(R.id.google_sign_in_button)
//        googleSignInButton.setSize(SignInButton.SIZE_STANDARD)
//        googleSignInButton.setColorScheme(SignInButton.COLOR_LIGHT)
//
//        val userAuth = root.findViewById<EditText>(R.id.login_user_auth)
//        loginButton = root.findViewById<Button>(R.id.login_user_auth_button)
//        val loginSpinner = root.findViewById<ProgressBar>(R.id.login_user_auth_spinner)
//        val loginError = root.findViewById<TextView>(R.id.login_error)
//
//        loginButton?.isEnabled = false
//
//        loginSpinner.visibility = View.GONE
//        loginError.visibility = View.GONE
//

//        userAuth.addTextChangedListener(object: TextWatcher {
//            override fun afterTextChanged(s: Editable?) {
//            }
//
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
//            }
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                val userAuthStr = userAuth.text.toString().trim()
//                hasUserAuth = (Patterns.EMAIL_ADDRESS.matcher(userAuthStr).matches() ||
//                                Patterns.PHONE.matcher(userAuthStr).matches())
//                syncLoginButton()
//            }
//        })
//
//        userAuth?.setOnEditorActionListener { _, _, keyEvent ->
//            if (keyEvent == null) {
//                false
//            } else {
//                when (keyEvent.keyCode) {
//                    KeyEvent.KEYCODE_ENTER -> {
//                        if (loginButton?.isEnabled == true) {
//                            login()
//                            true
//                        } else {
//                            false
//                        }
//                    }
//
//                    else -> false
//                }
//            }
//        }


        // return root
        return ComposeView(requireContext()).apply {
            setContent {
                URNetworkTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            LoginInitialActivity(
                                byApi = api,
                                appLogin = { byJwt ->
                                    app?.login(byJwt)
                                },
                                loginActivity = loginActivity,
                                navigate = { id, navArgs ->
                                    findNavController().navigate(id, navArgs)
                                }
                            )
                        }
                    }
                }
            }
        }

    }

    override fun onStart() {
        super.onStart()

        // immutable shadow
        val loginActivity = loginActivity ?: return
        loginActivity.supportActionBar?.hide()
    }

}