package com.bringyour.network.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.theme.URNetworkTheme

class LoginVerifyFragment : Fragment() {

    // This property is only valid between onCreateView and
    // onDestroyView.

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

        // immutable shadow
        val loginActivity = loginActivity

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
                            LoginVerify(
                                userAuth = userAuthStr,
                                byApi = app?.byApi,
                                appLogin = { byJwt ->
                                    app?.login(byJwt)
                                },
                                loginActivity = loginActivity,
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