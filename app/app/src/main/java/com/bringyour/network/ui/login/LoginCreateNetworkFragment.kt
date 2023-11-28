package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bringyour.client.LoginViewController
import com.bringyour.client.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginCreateNetworkBinding
import com.bringyour.network.databinding.FragmentLoginPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.regex.Pattern

class LoginCreateNetworkFragment : Fragment() {
    private var _binding: FragmentLoginCreateNetworkBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app: MainApplication? = null

    private var loginActivity: LoginActivity? = null

    private var loginVc: LoginViewController? = null

    private var createNetworkButton: Button? = null

    private var hasUserName: Boolean = false
    private var hasUserAuth: Boolean = false
    private var hasPassword: Boolean = false
    private var hasNetworkName: Boolean = false
    private var hasTerms: Boolean = false

    private var networkNameEdited: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginCreateNetworkBinding.inflate(inflater, container, false)
        val root: View = binding.root

        app = activity?.application as MainApplication
        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity
        // immutable shadow
        val loginActivity = loginActivity ?: return root

        loginVc = app.loginVc
        val loginVc = loginVc ?: return root

        val userName = root.findViewById<EditText>(R.id.create_user_name)
        val userAuth = root.findViewById<EditText>(R.id.create_user_auth)
        val password = root.findViewById<EditText>(R.id.create_password)
        val passwordError = root.findViewById<TextView>(R.id.create_password_error)
        val networkName = root.findViewById<EditText>(R.id.create_network_name)
        val networkNameAvailable = root.findViewById<TextView>(R.id.create_network_name_available)
        val networkNameError = root.findViewById<TextView>(R.id.create_network_name_error)
        val networkNameSpinner = root.findViewById<ProgressBar>(R.id.create_network_name_spinner)
        val terms = root.findViewById<CheckBox>(R.id.create_terms)
        createNetworkButton = root.findViewById(R.id.create_network_button)
        val createNetworkSpinner = root.findViewById<ProgressBar>(R.id.create_network_spinner)
        val createNetworkError = root.findViewById<TextView>(R.id.create_network_error)

        passwordError.visibility = View.GONE
        networkNameAvailable.visibility = View.GONE
        networkNameError.visibility = View.GONE
        createNetworkError.visibility = View.GONE

        networkNameSpinner.visibility = View.GONE
        createNetworkSpinner.visibility = View.GONE

        // user name validation
        userName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val userNameStr = userName.text.toString().trim()
                hasUserName = 0 < userNameStr.length
                syncCreateNetworkButton()

                if (!networkNameEdited) {
                    networkName.setText(userNameStr)
                }
            }
        })

        // user auth validation
        userAuth.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val userAuthStr = userAuth.text.toString().trim()
                hasUserAuth = (Patterns.EMAIL_ADDRESS.matcher(userAuthStr).matches() ||
                        Patterns.PHONE.matcher(userAuthStr).matches())
                syncCreateNetworkButton()
            }
        })

        // password validation
        password.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val passwordStr = password.text.toString().trim()
                if (passwordStr.length < 12) {
                    hasPassword = false
                    passwordError.visibility = View.VISIBLE
                } else {
                    hasPassword = true
                    passwordError.visibility = View.GONE
                }
                syncCreateNetworkButton()
            }
        })

        // network name validation
        // rules:
        // - must start with a letter
        // - alpha numeric and DNS compatible
        networkName.filters = arrayOf<InputFilter>(
            object: InputFilter {
                override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
                    if (start == end) {
                        // delete, accept
                        return null
                    } else if (dstart == 0) {
                        // must start with a letter
                        val out = StringBuilder()
                        for (i in start until end) {
                            if (Character.isLetter(source[i])) {
                                out.append(Character.toLowerCase(source[i]))
                            } else if (0 < out.length && (Character.isLetter(source[i]) || Character.isDigit(source[i]))) {
                                out.append(Character.toLowerCase(source[i]))
                            }
                        }
                        return out
                    } else {
                        val out = StringBuilder()
                        for (i in start until end) {
                            if (Character.isLetter(source[i]) || Character.isDigit(source[i])) {
                                out.append(Character.toLowerCase(source[i]))
                            }
                        }
                        return out
                    }
                }
            }
        )

        networkName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val networkNameStr = networkName.text.toString()
                if (networkNameStr.length == 0) {
                    networkNameEdited = false
                } else {
                    networkNameEdited = networkName.isFocused
                }

                networkNameAvailable.visibility = View.GONE
                networkNameError.visibility = View.GONE

                if (networkNameStr.length < 6) {
                    hasNetworkName = false
                    networkNameError.setText(R.string.network_name_length_error)
                    networkNameError.visibility = View.VISIBLE
                    syncCreateNetworkButton()
                } else {
                    networkNameSpinner.visibility = View.VISIBLE
                    loginVc.networkCheck(networkNameStr) { result, err ->
                        runBlocking(Dispatchers.Main.immediate) {
                            networkNameSpinner.visibility = View.GONE

                            if (err == null) {
                                if (result.available) {
                                    hasNetworkName = true
                                    networkNameAvailable.visibility = View.VISIBLE
                                } else {
                                    hasNetworkName = false
                                    networkNameError.setText(R.string.network_name_check_error)
                                    networkNameError.visibility = View.VISIBLE
                                }
                            } else {
                                hasNetworkName = false
                                networkNameError.setText(R.string.network_name_check_error)
                                networkNameError.visibility = View.VISIBLE
                            }
                            syncCreateNetworkButton()
                        }
                    }
                }
            }
        })

        // terms validation
        terms.setOnCheckedChangeListener { _, checked ->
            hasTerms = checked
            syncCreateNetworkButton()
        }

        terms.movementMethod = LinkMovementMethod.getInstance()

        createNetworkButton?.setOnClickListener {
            userName.isEnabled = false
            userAuth.isEnabled = false
            password.isEnabled = false
            networkName.isEnabled = false
            terms.isEnabled = false
            createNetworkButton?.isEnabled = false
            createNetworkSpinner.visibility = View.VISIBLE

            val args = NetworkCreateArgs()
            args.userName = userName.text.trim().toString()
            args.userAuth = userAuth.text.trim().toString()
            args.password = password.text.toString()
            args.networkName = networkName.text.toString()
            args.terms = terms.isChecked

            app.byApi?.networkCreate(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    userName.isEnabled = true
                    userAuth.isEnabled = true
                    password.isEnabled = true
                    networkName.isEnabled = true
                    terms.isEnabled = true
                    createNetworkButton?.isEnabled = true
                    createNetworkSpinner.visibility = View.GONE

                    if (err != null) {
                        createNetworkError.visibility = View.VISIBLE
                    } else if (result.error != null) {
                        createNetworkError.visibility = View.VISIBLE
                    } else if (result.network != null && 0 < result.network.byJwt.length) {
                        createNetworkError.visibility = View.GONE

                        app.loginClient(result.network.byJwt)

                        val intent = Intent(loginActivity, MainActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                        startActivity(intent)
                        loginActivity.finish()
                    } else if (result.verificationRequired != null) {
                        createNetworkError.visibility = View.GONE

                        val navArgs = Bundle()
                        navArgs.putString("userAuth", result.verificationRequired.userAuth)

                        val navOpts = NavOptions.Builder()
                            .setPopUpTo(R.id.navigation_initial, false, false)
                            .build()

                        findNavController().navigate(R.id.navigation_verify, navArgs, navOpts)
                    }
                }
            }
        }

        syncCreateNetworkButton()

        return root
    }

    override fun onStart() {
        super.onStart()

        // immutable shadow
        val loginActivity = loginActivity ?: return

        loginActivity.supportActionBar?.show()
    }

    private fun syncCreateNetworkButton() {
        createNetworkButton?.isEnabled = hasUserName && hasUserAuth && hasPassword && hasNetworkName && hasTerms
    }
}
