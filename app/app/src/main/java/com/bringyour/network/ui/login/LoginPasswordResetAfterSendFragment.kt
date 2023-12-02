package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.client.AuthVerifyArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginPasswordResetAfterSendBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginPasswordResetAfterSendFragment : Fragment() {

    private var _binding: FragmentLoginPasswordResetAfterSendBinding? = null

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
        _binding = FragmentLoginPasswordResetAfterSendBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity

        // immutable shadow
        val loginActivity = loginActivity ?: return root

        val userAuthStr = arguments?.getString("userAuth")

        val passwordResetDescription = root.findViewById<TextView>(R.id.password_reset_description)
        val passwordResetResendButton = root.findViewById<Button>(R.id.password_reset_resend_button)
        val passwordResetResendSpinner = root.findViewById<ProgressBar>(R.id.password_reset_resend_spinner)

        passwordResetDescription.text = getString(R.string.password_reset_after_send_description, userAuthStr)

        passwordResetResendSpinner.visibility = View.GONE

        passwordResetResendButton.setOnClickListener {
            passwordResetResendButton.isEnabled = false

            passwordResetResendSpinner.visibility = View.VISIBLE

            val args = AuthPasswordResetArgs()
            args.userAuth = userAuthStr

            app.byApi?.authPasswordReset(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    if (err != null) {
                        passwordResetResendButton.text = getString(R.string.password_reset_send_error)
                    } else {
                        passwordResetResendButton.text = getString(R.string.password_reset_sent)
                    }

                    passwordResetResendSpinner.visibility = View.GONE
                    // allow sending another code after a delay

                    Handler(Looper.getMainLooper()).postDelayed({
                        context?.let {
                            passwordResetResendButton.isEnabled = true
                            passwordResetResendButton.text =
                                getString(R.string.password_reset_resend)
                        }
                    }, 15 * 1000)
                }
            }
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