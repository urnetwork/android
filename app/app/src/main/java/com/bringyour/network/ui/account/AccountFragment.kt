package com.bringyour.network.ui.account

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.api.ApiError
import circle.programmablewallet.sdk.api.Callback
import circle.programmablewallet.sdk.api.ExecuteEvent
import circle.programmablewallet.sdk.api.ExecuteWarning
import circle.programmablewallet.sdk.presentation.EventListener
import circle.programmablewallet.sdk.presentation.SecurityQuestion
import circle.programmablewallet.sdk.result.ExecuteResult
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentAccountBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Text

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        val root: View = binding.root

        app = activity?.application as MainApplication
        // immutable shadow
        val app = app ?: return root


        val accountNetwork = root.findViewById<TextView>(R.id.account_network_label)
        val updateButton = root.findViewById<Button>(R.id.account_update_button)
        val updateSpinner = root.findViewById<ProgressBar>(R.id.account_update_spinner)
        val subscriptionButton = root.findViewById<Button>(R.id.account_subscription_change)
        val logoutButton = root.findViewById<Button>(R.id.account_logout)
        val walletDescription = root.findViewById<TextView>(R.id.account_wallet_description)


        val update = {

//            val byJwt = app.asyncLocalState?.localState()?.parseByJwt()
//            byJwt?.networkName?.let { networkName ->
//                accountNetwork.text = networkName
//            }

            app.asyncLocalState?.parseByJwt { byJwt, ok ->
                runBlocking(Dispatchers.Main.immediate) {
                    if (ok) {
                        accountNetwork.text = byJwt.networkName
                    }
                }
            }
        }



        walletDescription.movementMethod = LinkMovementMethod.getInstance()


        subscriptionButton.setOnClickListener {
            val subscriptionFragment = SubscriptionFragment()

            val navArgs = Bundle()
            navArgs.putInt("transferBalanceGib", 0)
            navArgs.putString("currentPlan", SubscriptionFragment.PlanBasic)

            subscriptionFragment.arguments = navArgs

            subscriptionFragment.show(childFragmentManager, "dialog")
        }


        val initWallet = {

            val endpoint = ""
            // FIXME what is this?
            val addId = ""


            WalletSdk.init(
                requireContext().applicationContext,
                WalletSdk.Configuration(endpoint, addId)
            )

            WalletSdk.setSecurityQuestions(
                arrayOf(
                    SecurityQuestion("What is your favorite color?"),
                    SecurityQuestion("What is your favorite shape?"),
                    SecurityQuestion("What is your favorite animal?"),
                    SecurityQuestion("What is your favorite place?"),
                    SecurityQuestion("What is your favorite material?"),
                    SecurityQuestion("What is your favorite sound?"),
                    SecurityQuestion("What would you explore in space?"),
                    SecurityQuestion("What is your favorite way to recharge?"),
                    SecurityQuestion("Go ____."),
                    SecurityQuestion("Pick a word, any word."),
                    SecurityQuestion("Pick a date, any date.", SecurityQuestion.InputType.datePicker),
                ))

            WalletSdk.addEventListener { event: ExecuteEvent? ->
                // FIXME show a toast with the message
            }

            WalletSdk.setLayoutProvider(context?.let { CircleLayoutProvider(it) })
            WalletSdk.setViewSetterProvider(context?.let { CircleViewSetterProvider(it) })

        }

        val setupWallet = {
            val userToken = ""
            val encryptionKey = ""
            val challengeId = ""

            WalletSdk.execute(
                activity,
                userToken,
                encryptionKey,
                arrayOf<String>(challengeId),
                object : Callback<ExecuteResult> {
                    override fun onWarning(warning: ExecuteWarning?, result: ExecuteResult?): Boolean {
                        // FIXME toast
                        return true
                    }

                    override fun onError(error: Throwable): Boolean {

                        if (error is ApiError) {
                            when (error.code) {
                                ApiError.ErrorCode.userCanceled -> return false // App won't handle next step, SDK will finish the Activity.
                                ApiError.ErrorCode.incorrectUserPin, ApiError.ErrorCode.userPinLocked,
                                ApiError.ErrorCode.incorrectSecurityAnswers, ApiError.ErrorCode.securityAnswersLocked,
                                ApiError.ErrorCode.insecurePinCode, ApiError.ErrorCode.pinCodeNotMatched-> {}
                                ApiError.ErrorCode.networkError -> {
                                    // FIXME toast
                                }
                                else -> {
                                    // FIXME toast
                                }
                            }
                            return true // App will handle next step, SDK will keep the Activity.
                        }
                        return false // App won't handle next step, SDK will finish the Activity.
                    }

                    override fun onResult(result: ExecuteResult) {
                        // FIXME toast

                    }
                })
        }

        // FIXME set up wallet button, look at sample project

        // FIXME transfer out button





        logoutButton.setOnClickListener {
            (activity?.application as MainApplication).logout()

            activity?.startActivity(Intent(activity, LoginActivity::class.java))

            activity?.finish()
        }


//        val textView: TextView = binding.textAccount
//        accountViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }
        update()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}