package com.bringyour.network.ui.account

import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.api.ApiError
import circle.programmablewallet.sdk.api.Callback
import circle.programmablewallet.sdk.api.ExecuteWarning
import circle.programmablewallet.sdk.result.ExecuteResult
import com.bringyour.client.Client
import com.bringyour.client.WalletCircleTransferOutArgs
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentAccountBinding
import com.bringyour.network.databinding.FragmentWalletTransferOutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Text
import java.lang.NumberFormatException

class WalletTransferOutFragment: DialogFragment() {

    private var _binding: FragmentWalletTransferOutBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    private var app : MainApplication? = null

    private var hasAddress: Boolean = false
    private var hasTerms: Boolean = false

    var transferAmount: Double = 0.0


    var walletId: String? = null
    var walletBalanceUsdc: Double = 0.0
    var walletToken: String? = null
    var walletTokenId: String? = null
    var walletBlockchain: String? = null
    var walletBlockchainSymbol: String? = null



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentWalletTransferOutBinding.inflate(inflater, container, false)
        val root: View = binding.root

        app = activity?.application as MainApplication
        // immutable shadow
        val app = app ?: return root

        val accountVc = app.accountVc ?: return root


        walletId = arguments?.getString("walletId") ?: return root
        walletBalanceUsdc = arguments?.getDouble("walletBalance", 0.0) ?: return root
        walletToken = arguments?.getString("walletToken") ?: return root
        walletTokenId = arguments?.getString("walletTokenId") ?: return root
        walletBlockchain = arguments?.getString("walletBlockchain") ?: return root
        walletBlockchainSymbol = arguments?.getString("walletBlockchainSymbol") ?: return root


        // https://developers.circle.com/w3s/reference/createvalidateaddress-1


        binding.walletTransferOutError.visibility = View.GONE
        binding.walletTransferOutSuccess.visibility = View.GONE
        binding.walletTransferOutSpinner.visibility = View.GONE
        binding.walletTransferOutAddressSpinner.visibility = View.GONE

        binding.walletTransferOutAddressInvalid.visibility = View.GONE
        binding.walletTransferOutAddressValid.visibility = View.GONE


        binding.walletTransferAmount.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    transferAmount = binding.walletTransferAmount.text.toString().toDouble()
                } catch (e: NumberFormatException) {
                    transferAmount = 0.0
                }
                syncTransferButton()
            }
        })


        binding.walletTransferOutAddress.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.walletTransferOutAddressSpinner.visibility = View.VISIBLE
                accountVc.walletValidateAddress(binding.walletTransferOutAddress.text.toString().trim()) { result, error ->
                    runBlocking(Dispatchers.Main.immediate) {
                        binding.walletTransferOutAddressSpinner.visibility = View.GONE
                        if (error != null) {
                            binding.walletTransferOutAddressInvalid.visibility = View.VISIBLE
                            binding.walletTransferOutAddressValid.visibility = View.GONE
                            hasAddress = false
                        } else if (result.valid) {
                            binding.walletTransferOutAddressInvalid.visibility = View.GONE
                            binding.walletTransferOutAddressValid.visibility = View.VISIBLE
                            hasAddress = true
                        } else {
                            binding.walletTransferOutAddressInvalid.visibility = View.VISIBLE
                            binding.walletTransferOutAddressValid.visibility = View.GONE
                            hasAddress = false
                        }
                        syncTransferButton()
                    }
                }
            }
        })


        binding.walletTransferOutButton.setOnClickListener {

            val inProgress = { busy: Boolean ->
                if (busy) {
                    binding.walletTransferOutButton.isEnabled = false
                    binding.walletTransferOutSpinner.visibility = View.VISIBLE
                } else {
                    binding.walletTransferOutButton.isEnabled = true
                    binding.walletTransferOutSpinner.visibility = View.GONE
                }
            }

            inProgress(true)

            binding.walletTransferOutError.visibility = View.GONE
            binding.walletTransferOutSuccess.visibility = View.GONE



            val args = WalletCircleTransferOutArgs()
            args.amountUsdcNanoCents = Client.usdToNanoCents(binding.walletTransferAmount.text.toString().toDouble())
            args.toAddress = binding.walletTransferOutAddress.text.toString().trim()
            args.terms = binding.walletTransferOutTerms.isChecked


            app.byApi?.walletCircleTransferOut(args) { result, error ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    if (error != null) {
                        binding.walletTransferOutError.text = error.message
                        binding.walletTransferOutError.visibility = View.VISIBLE
                    } else if (result.error != null) {
                        binding.walletTransferOutError.text = result.error.message
                        binding.walletTransferOutError.visibility = View.VISIBLE
                    } else {
                        binding.walletTransferOutError.visibility = View.GONE

                        val userToken = result.userToken.userToken
                        val encryptionKey = result.userToken.encryptionKey
                        val challengeId = result.challengeId

                        Log.i("WalletTransferOutActivity", "userToken=${userToken}; encryptionKey=${encryptionKey}; challengeId=${challengeId}")

                        inProgress(true)

                        WalletSdk.execute(
                            activity,
                            userToken,
                            encryptionKey,
                            arrayOf<String>(challengeId),
                            object : Callback<ExecuteResult> {
                                override fun onWarning(
                                    warning: ExecuteWarning?,
                                    result: ExecuteResult?
                                ): Boolean {
                                    // FIXME toast
                                    return true
                                }

                                override fun onError(error: Throwable): Boolean {
                                    update {
                                        binding.walletTransferOutError.text = error.message
                                        binding.walletTransferOutError.visibility = View.VISIBLE

                                        inProgress(false)
                                    }

                                    if (error is ApiError) {
                                        when (error.code) {
                                            ApiError.ErrorCode.userCanceled -> return false // App won't handle next step, SDK will finish the Activity.
                                            ApiError.ErrorCode.incorrectUserPin, ApiError.ErrorCode.userPinLocked,
                                            ApiError.ErrorCode.incorrectSecurityAnswers, ApiError.ErrorCode.securityAnswersLocked,
                                            ApiError.ErrorCode.insecurePinCode, ApiError.ErrorCode.pinCodeNotMatched -> {
                                            }

                                            ApiError.ErrorCode.networkError -> {
                                                // FIXME toast
                                            }

                                            else -> {
                                                // FIXME toast
                                            }
                                        }
                                        // App will handle next step, SDK will keep the Activity.
                                        return true
                                    }
                                    // App won't handle next step, SDK will finish the Activity.
                                    return false
                                }

                                override fun onResult(result: ExecuteResult) {

                                    // success
                                    update {
                                        binding.walletTransferOutSuccess.visibility = View.VISIBLE
                                        inProgress(false)
                                    }

                                }
                            }
                        )

                    }

                }

            }

        }


        binding.walletTransferMaxAmountButton.setOnClickListener {
            binding.walletTransferAmount.setText(String.format("%f", walletBalanceUsdc))
        }

        binding.walletTransferTestAmountButton.setOnClickListener {
            binding.walletTransferAmount.setText(String.format("%f",
                0.001.coerceAtMost(walletBalanceUsdc * 0.05)
            ))
        }


        binding.walletTransferOutTerms.setOnCheckedChangeListener { _, checked ->
            hasTerms = checked
            syncTransferButton()
        }


        binding.walletTransferOutHelpButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.bringyour.com")))
        }


        sync()

        return root
    }


    override fun onResume() {
        // Set the width of the dialog proportional to 90% of the screen width
        val window = dialog!!.window
        val size = Point()
//        window!!.windowManager.currentWindowMetrics.bounds
        val display = window!!.windowManager.defaultDisplay
        display.getSize(size)
        val px = Math.round(
            TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            480.0f,
            resources.displayMetrics
        ))
        window.setLayout((size.x * 0.90).toInt().coerceAtMost(px), WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
        super.onResume()
    }


    private fun sync() {
/*

    var walletId: String? = null
    var walletBalanceUsdc: Double = 0.0
    var walletToken: String? = null
    var walletTokenId: String? = null
    var walletBlockchain: String? = null
    var walletBlockchainSymbol: String? = null

 */

        binding.walletBalance.text = String.format("%.2f USDC", walletBalanceUsdc)

        syncTransferButton()
    }

    private fun syncTransferButton() {
        val hasAmount = (0 < transferAmount && transferAmount < walletBalanceUsdc)
        binding.walletTransferOutButton.isEnabled = hasAddress && hasAmount && hasTerms
    }


    private fun update(callback: ()->Unit) {

//            val byJwt = app.asyncLocalState?.localState()?.parseByJwt()
//            byJwt?.networkName?.let { networkName ->
//                accountNetwork.text = networkName
//            }

        val app = app ?: return


        val inProgress = { busy: Boolean ->
            if (busy) {
                binding.walletTransferOutButton.isEnabled = false
                binding.walletTransferOutSpinner.visibility = View.VISIBLE
            } else {
                binding.walletTransferOutButton.isEnabled = true
                binding.walletTransferOutSpinner.visibility = View.GONE
            }
        }


        inProgress(true)

        binding.walletTransferOutError.visibility = View.GONE

        app.byApi?.walletBalance { result, error ->

            runBlocking(Dispatchers.Main.immediate) {

                inProgress(false)

                if (error != null) {
                    binding.walletTransferOutError.text = error.message
                    binding.walletTransferOutError.visibility = View.VISIBLE
                } else if (result.walletInfo != null) {
                    walletId = result.walletInfo.walletId
                    walletBalanceUsdc = Client.nanoCentsToUsd(result.walletInfo.balanceUsdcNanoCents)
                    walletToken = "USDC"
                    walletTokenId = result.walletInfo.tokenId
                    walletBlockchain = result.walletInfo.blockchain
                    walletBlockchainSymbol = result.walletInfo.blockchainSymbol

                    sync()
                } else {
                    binding.walletTransferOutError.text = "Could not update wallet balance."
                    binding.walletTransferOutError.visibility = View.VISIBLE
                }

                callback()
            }
        }

    }
}