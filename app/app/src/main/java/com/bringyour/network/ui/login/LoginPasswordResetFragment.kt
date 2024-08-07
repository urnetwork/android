package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.client.AuthVerifyArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginPasswordResetFragment : Fragment() {

    private var app: MainApplication? = null

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        app = activity?.application as MainApplication

        // immutable shadow
        val app = app

        loginActivity = activity as LoginActivity

        val userAuthStr = arguments?.getString("userAuth") ?: ""

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
                            LoginPasswordReset(
                                userAuth = userAuthStr,
                                byApi = app?.byApi,
                                onResetLinkSuccess = { userAuth ->

                                    val navArgs = Bundle()
                                    navArgs.putString("userAuth", userAuth)

                                    val navOpts = NavOptions.Builder()
                                        .setPopUpTo(R.id.navigation_initial, false, false)
                                        .build()

                                    findNavController().navigate(
                                        R.id.navigation_password_reset_after_send,
                                        navArgs,
                                        navOpts
                                    )
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
        loginActivity.supportActionBar?.show()
    }
}