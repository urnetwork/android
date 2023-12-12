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
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.client.AuthVerifyArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginPasswordResetBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginPasswordResetFragment : Fragment() {

    private var _binding: FragmentLoginPasswordResetBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app: MainApplication? = null

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginPasswordResetBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity

        // immutable shadow
        val loginActivity = loginActivity ?: return root

        val userAuthStr = arguments?.getString("userAuth")

        val userAuth = root.findViewById<EditText>(R.id.password_reset_user_auth)
        val passwordResetButton = root.findViewById<Button>(R.id.password_reset_button)
        val passwordResetSpinner = root.findViewById<ProgressBar>(R.id.password_reset_spinner)
        val passwordResetError = root.findViewById<TextView>(R.id.password_reset_error)

        passwordResetSpinner.visibility = View.GONE
        passwordResetError.visibility = View.GONE

        // validate code
        userAuth.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val userAuthStr = userAuth.text.toString().trim()
                passwordResetButton.isEnabled = (Patterns.EMAIL_ADDRESS.matcher(userAuthStr).matches() ||
                        Patterns.PHONE.matcher(userAuthStr).matches())
            }
        })

        val inProgress = { busy: Boolean ->
            if (busy) {
                userAuth.isEnabled = false
                passwordResetButton.isEnabled = false
                passwordResetSpinner.visibility = View.VISIBLE
            } else {
                userAuth.isEnabled = true
                passwordResetButton.isEnabled = true
                passwordResetSpinner.visibility = View.GONE
            }
        }

        passwordResetButton.setOnClickListener {
            inProgress(true)

            val args = AuthPasswordResetArgs()
            args.userAuth = userAuth.text.toString().trim()

            app.byApi?.authPasswordReset(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    if (err != null) {
                        passwordResetError.visibility = View.VISIBLE
                        passwordResetError.text = err.message
                    } else {
                        passwordResetError.visibility = View.GONE

                        val navArgs = Bundle()
                        navArgs.putString("userAuth", result.userAuth)

                        val navOpts = NavOptions.Builder()
                            .setPopUpTo(R.id.navigation_initial, false, false)
                            .build()

                        findNavController().navigate(
                            R.id.navigation_password_reset_after_send,
                            navArgs,
                            navOpts
                        )
                    }
                }
            }

            // }
        }

        passwordResetButton.isEnabled = false

        if (userAuthStr != null) {
            userAuth.setText(userAuthStr)
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