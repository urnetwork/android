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
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bringyour.client.LoginViewController
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme

class LoginCreateNetworkAuthJwtFragment : Fragment() {

    // This property is only valid between onCreateView and
    // onDestroyView.

    private var app: MainApplication? = null

    private var loginActivity: LoginActivity? = null

    private var loginVc: LoginViewController? = null

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

        loginVc = app?.loginVc
        val loginVc = loginVc

        val authJwt = arguments?.getString("authJwt") ?: ""
        val authJwtType = arguments?.getString("authJwtType") ?: ""
        val userAuthStr = arguments?.getString("userAuth") ?: ""
        val userNameStr = arguments?.getString("userName") ?: ""

        val createNetworkParams = LoginCreateNetworkParams.LoginCreateAuthJwtParams(
            userAuth = userAuthStr,
            authJwt = authJwt,
            authJwtType = authJwtType,
            userName = userNameStr,
//            byApi = app?.byApi,
//            loginVc = loginVc,
//            loginActivity = loginActivity,
//            appLogin = { byJwt ->
//                app?.login(byJwt)
//            },
//            onVerificationRequired = { userAuth ->
//                val navArgs = Bundle()
//                navArgs.putString("userAuth", userAuth)
//
//                val navOpts = NavOptions.Builder()
//                    .setPopUpTo(R.id.navigation_initial, false, false)
//                    .build()
//
//                findNavController().navigate(R.id.navigation_verify, navArgs, navOpts)
//            }
        )

        return ComposeView(requireContext()).apply {
            setContent {
                URNetworkTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
//                            LoginCreateNetwork(
//                                createNetworkParams
//                            )
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
