package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthLoginWithPasswordArgs
import com.bringyour.client.AuthNetworkClientArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.ActivityLoginWithPasswordBinding
import com.bringyour.network.databinding.FragmentLoginPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginPasswordFragment : Fragment() {

    private var _binding: FragmentLoginPasswordBinding? = null

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
        _binding = FragmentLoginPasswordBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity

        // immutable shadow
        val loginActivity = loginActivity ?: return root


        loginActivity.supportActionBar?.show()




        val userAuth = arguments?.getString("userAuth")

        val loginPassword = root.findViewById<EditText>(R.id.login_password)

        root.findViewById<TextView>(R.id.login_description).text = "Log in using " + userAuth

        val loginSpinner = root.findViewById<ProgressBar>(R.id.login_password_spinner)
        loginSpinner.visibility = View.GONE

        val loginButton = root.findViewById<Button>(R.id.login_password_button)
        loginButton.setOnClickListener {

            val password = loginPassword.text.toString()

            var args = AuthLoginWithPasswordArgs()
            args.userAuth = userAuth
            args.password = password

            Log.i("LoginWithPasswordActivity", "LOGIN WITH PASSWORD " + userAuth + " " + password)

            loginPassword.isEnabled = false
            loginButton.isEnabled = false
            loginSpinner.visibility = VISIBLE

            app.byApi?.authLoginWithPassword(args, { result, err ->
                if (err == null) {
                    Log.i("LoginWithPasswordActivity", "GOT RESULT " + it)

                    runBlocking(Dispatchers.Main.immediate) {

                        loginPassword.isEnabled = true
                        loginButton.isEnabled = true
                        loginSpinner.visibility = GONE

                        if (result.network != null) {
                            // now create a client id for the network

                            app.login(result.network.byJwt)

                            var authArgs = AuthNetworkClientArgs()
                            authArgs.description = "test"
                            authArgs.description = "test"

                            app.byApi?.authNetworkClient(authArgs, { result, err ->
                                Log.i(
                                    "LoginWithPasswordActivity",
                                    "GOT CREATE CLIENT RESULT " + result
                                )

                                if (result != null) {

                                    app.loginClient(result.byJwt)


                                    val intent = Intent(loginActivity, MainActivity::class.java)
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                                    startActivity(intent)
                                    loginActivity.finish()
                                }

                            })

                        }

                    }
                }
            })

        }


        val forgotPasswordButton = root.findViewById<Button>(R.id.login_forgot_password_button)
        forgotPasswordButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_password_reset)
        }


        return root
    }


    override fun onStart() {
        super.onStart()


        // immutable shadow
        val loginActivity = loginActivity ?: return

        loginActivity.supportActionBar?.show()
    }
}