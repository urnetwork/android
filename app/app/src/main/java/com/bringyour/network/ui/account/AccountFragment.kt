package com.bringyour.network.ui.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.api.ApiError
import circle.programmablewallet.sdk.api.Callback
import circle.programmablewallet.sdk.api.ExecuteEvent
import circle.programmablewallet.sdk.api.ExecuteWarning
import circle.programmablewallet.sdk.presentation.SecurityQuestion
import circle.programmablewallet.sdk.presentation.SettingsManagement
import circle.programmablewallet.sdk.result.ExecuteResult
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.bringyour.client.CircleWalletInfo
import com.bringyour.client.Client
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentAccountBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null


    private var transferBalanceGib: Long = 0
    private var currentPlan: String? = null
    private var walletInfo: CircleWalletInfo? = null


    private var billingClient: BillingClient? = null


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


        binding.accountNetworkName.text = ""
        binding.accountUpdateSpinner.visibility = View.GONE

        clear()



        binding.accountWalletDescription.movementMethod = LinkMovementMethod.getInstance()


        binding.accountUpdateButton.setOnClickListener {
            update {}
        }

        binding.accountSubscriptionChangeButton.setOnClickListener {
            val subscriptionFragment = SubscriptionFragment()

            val navArgs = Bundle()
            navArgs.putLong("transferBalanceGib", transferBalanceGib)
            navArgs.putString("currentPlan", currentPlan)

            subscriptionFragment.arguments = navArgs

            subscriptionFragment.show(childFragmentManager, "dialog")
        }



        binding.accountWalletInitButton.setOnClickListener {

            val inProgress = { busy: Boolean ->
                if (busy) {
                    binding.accountWalletInitButton.isEnabled = false
                    binding.accountWalletInitSpinner.visibility = View.VISIBLE
                } else {
                    binding.accountWalletInitButton.isEnabled = true
                    binding.accountWalletInitSpinner.visibility = View.GONE
                }
            }

            inProgress(true)

            app.byApi?.walletCircleInit { result, error ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    if (error != null) {
                        binding.accountWalletError.text = error.message
                        binding.accountWalletError.visibility = View.VISIBLE
                    } else if (result.error != null) {
                        binding.accountWalletError.text = result.error.message
                        binding.accountWalletError.visibility = View.VISIBLE
                    } else {
                        binding.accountWalletError.visibility = View.GONE

                        val userToken = result.userToken.userToken
                        val encryptionKey = result.userToken.encryptionKey
                        val challengeId = result.challengeId

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
                                    complete()
                                    // FIXME toast
                                    return false
                                }

                                override fun onError(error: Throwable): Boolean {
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
                                    complete()
                                }

                                fun complete() {
                                    if (!app.hasBiometric) {
                                        update {}
                                    } else {
                                        // enable biometrics
                                        WalletSdk.setBiometricsPin(
                                            activity,
                                            userToken,
                                            encryptionKey,
                                            object : Callback<ExecuteResult?> {
                                                override fun onError(error: Throwable): Boolean {
                                                    update {}

                                                    error.printStackTrace()
                                                    if (error is ApiError) {
                                                        when (error.code) {
                                                            ApiError.ErrorCode.incorrectUserPin, ApiError.ErrorCode.userPinLocked, ApiError.ErrorCode.securityAnswersLocked, ApiError.ErrorCode.incorrectSecurityAnswers, ApiError.ErrorCode.pinCodeNotMatched, ApiError.ErrorCode.insecurePinCode -> return true // App will handle next step, SDK will keep the Activity.
                                                            else -> return false
                                                        }
                                                    }
                                                    return false // App won't handle next step, SDK will finish the Activity.
                                                }

                                                override fun onResult(executeResult: ExecuteResult?) {
                                                    //success
                                                    update {}
                                                }

                                                override fun onWarning(
                                                    warning: ExecuteWarning,
                                                    executeResult: ExecuteResult?
                                                ): Boolean {
                                                    return false // App won't handle next step, SDK will finish the Activity.
                                                }
                                            })

                                    }
                                }
                            }
                        )

                    }

                }

            }

        }

        binding.accountWalletTransferOutButton.setOnClickListener {

            val inProgress = { busy: Boolean ->
                if (busy) {
                    binding.accountWalletTransferOutButton.isEnabled = false
                    binding.accountWalletTransferOutSpinner.visibility = View.VISIBLE
                } else {
                    binding.accountWalletTransferOutButton.isEnabled = true
                    binding.accountWalletTransferOutSpinner.visibility = View.GONE
                }
            }

            inProgress(true)

            update {
                inProgress(false)

                walletInfo?.let { walletInfo ->
                    val walletTransferOutFragment = WalletTransferOutFragment()

                    val navArgs = Bundle()
                    navArgs.putString("walletId", walletInfo.walletId)
                    navArgs.putDouble("walletBalance", Client.nanoCentsToUsd(walletInfo.balanceUsdcNanoCents))
                    navArgs.putString("walletToken", "USDC")
                    navArgs.putString("walletTokenId", walletInfo.tokenId)
                    navArgs.putString("walletBlockchain", walletInfo.blockchain)
                    navArgs.putString("walletBlockchainSymbol", walletInfo.blockchainSymbol)

                    walletTransferOutFragment.arguments = navArgs

                    walletTransferOutFragment.show(childFragmentManager, "dialog")
                }
            }
        }





        binding.accountLogoutButton.setOnClickListener {
            (activity?.application as MainApplication).logout()

            activity?.startActivity(Intent(activity, LoginActivity::class.java))

            activity?.finish()
        }

        binding.accountHelpButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.bringyour.com")))
        }

        binding.accountDeleteButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.bringyour.com")))
        }


        binding.accountLegal.movementMethod = LinkMovementMethod.getInstance()

        update {}

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()

        billingClient?.endConnection()
    }


    override fun onResume() {
        super.onResume()

        update {}
    }






    private fun clear() {
        binding.accountBalanceSummary.text = ""
//        binding.accountSubscriptionSummary.text = ""
//        binding.accountSubscriptionPrice.visibility = View.GONE
//        binding.accountSubscriptionPriceDescription.visibility = View.GONE
//        binding.accountSubscriptionChangeButton.isEnabled = false
        binding.accountWalletSummary.text = ""

        binding.accountWalletInitButton.visibility = View.GONE
        binding.accountWalletInitSpinner.visibility = View.GONE
        binding.accountWalletBalanceLabel.visibility = View.GONE
        binding.accountWalletBalanceSummary.visibility = View.GONE
        binding.accountWalletTransferOutButton.visibility = View.GONE
        binding.accountWalletTransferOutSpinner.visibility = View.GONE
        binding.accountWalletError.visibility = View.GONE

        binding.accountWalletPendingBalanceSummary.text = ""
    }

    private fun update(callback: ()->Unit) {

//            val byJwt = app.asyncLocalState?.localState()?.parseByJwt()
//            byJwt?.networkName?.let { networkName ->
//                accountNetwork.text = networkName
//            }

        val app = app ?: return


        val inProgress = { busy: Boolean ->
            if (busy) {
                binding.accountUpdateButton.isEnabled = false
                binding.accountUpdateSpinner.visibility = View.VISIBLE
            } else {
                binding.accountUpdateButton.isEnabled = true
                binding.accountUpdateSpinner.visibility = View.GONE
            }
        }


        // FIXME show spinner

        inProgress(true)

        app.byApi?.subscriptionBalance { result, error ->

            runBlocking(Dispatchers.Main.immediate) {

                inProgress(false)

                Log.i("AccountFragment", "GOT UPDATE RESULT ${result} ${error}")

                if (error != null) {
                    clear()
                    transferBalanceGib = 0
//                    currentPlan = null
                    walletInfo = null
                } else {
                    transferBalanceGib = Math.round(result.balanceByteCount / 1024.0)

                    binding.accountBalanceSummary.text = "${transferBalanceGib}"

                    if (result.walletInfo == null) {
                        walletInfo = null
                        binding.accountWalletSummary.text = "None"

                        binding.accountWalletInitButton.visibility = View.VISIBLE
                        binding.accountWalletInitSpinner.visibility = View.GONE
                        binding.accountWalletBalanceLabel.visibility = View.GONE
                        binding.accountWalletBalanceSummary.visibility = View.GONE
                        binding.accountWalletTransferOutButton.visibility = View.GONE
                        binding.accountWalletTransferOutSpinner.visibility = View.GONE

                        binding.accountWalletInitButton.isEnabled = true
                    } else {
                        walletInfo = result.walletInfo
                        binding.accountWalletSummary.text = String.format(
                            "Circle USDC self-custody (%s %s)",
                            result.walletInfo.blockchain,
                            result.walletInfo.blockchainSymbol
                        )

                        binding.accountWalletInitButton.visibility = View.GONE
                        binding.accountWalletInitSpinner.visibility = View.GONE
                        binding.accountWalletBalanceLabel.visibility = View.VISIBLE
                        binding.accountWalletBalanceSummary.visibility = View.VISIBLE
                        binding.accountWalletTransferOutButton.visibility = View.VISIBLE
                        binding.accountWalletTransferOutSpinner.visibility = View.GONE

                        binding.accountWalletBalanceSummary.text = String.format(
                            "%.2f USDC",
                            Client.nanoCentsToUsd(result.walletInfo.balanceUsdcNanoCents)
                        )
                        binding.accountWalletTransferOutButton.isEnabled = true

                    }

                    binding.accountWalletError.visibility = View.GONE

                    binding.accountWalletPendingBalanceSummary.text = String.format(
                        "%.2f USDC",
                        Client.nanoCentsToUsd(result.pendingPayoutUsdNanoCents)
                    )
                }

                callback()
            }
        }

        app.asyncLocalState?.parseByJwt { byJwt, ok ->
            runBlocking(Dispatchers.Main.immediate) {
                if (ok) {
                    binding.accountNetworkName.text = byJwt.networkName
                } else {
                    binding.accountNetworkName.text = ""
                }
            }
        }

        // use the google billing library to get the current plan
        syncGoogleBilling()
    }

    private fun syncGoogleBilling() {

        // FIXME plan spinner
        // FIXME plan error

        val inProgress = { busy: Boolean ->
            if (busy) {
                binding.accountSubscriptionChangeSpinner.visibility = View.VISIBLE
                binding.accountSubscriptionChangeButton.isEnabled = false
            } else {
                binding.accountSubscriptionChangeSpinner.visibility = View.GONE
                binding.accountSubscriptionChangeButton.isEnabled = true
            }
        }


        val planError = {
            currentPlan = null
            binding.accountSubscriptionSummary.text = ""
            binding.accountSubscriptionPrice.visibility = View.GONE
            binding.accountSubscriptionPriceDescription.visibility = View.GONE
            binding.accountSubscriptionChangeButton.isEnabled = false
        }

//        binding.accountSubscriptionChangeButton.isEnabled = false


        binding.accountSubscriptionError.visibility = View.GONE

        inProgress(true)


        billingClient?.endConnection()

        billingClient = BillingClient.newBuilder(requireContext())
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                // ignore. The purchases are fetched once below.
            }
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    lifecycleScope.launch(Dispatchers.Main.immediate) {
                        queryPurchase()

                        inProgress(false)
                        billingClient?.endConnection()
                    }
                } else {
                    // show error message of billing error
                    // FIXME show error



                    binding.accountSubscriptionError.text = String.format("Billing error: %d %s", billingResult.responseCode, billingResult.debugMessage)
                    binding.accountSubscriptionError.visibility = View.VISIBLE

                    inProgress(false)
                    billingClient?.endConnection()

                    planError()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.

                binding.accountSubscriptionError.text = String.format("Billing error: Disconnected")
                binding.accountSubscriptionError.visibility = View.VISIBLE

                inProgress(false)
                billingClient?.endConnection()

                planError()
            }
        })
    }

    private suspend fun queryPurchase() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)

// uses queryPurchasesAsync Kotlin extension function
        val purchasesResult = withContext(Dispatchers.IO) {
            billingClient?.queryPurchasesAsync(params.build())
        }

        val planPurchases = purchasesResult?.purchasesList?.filter { purchase ->
            "monthly_transfer_300gib" in purchase.products ||
                    "monthly_transfer_1tib" in purchase.products ||
                    "ultimate" in purchase.products
        }

        if (planPurchases.isNullOrEmpty()) {
            currentPlan = SubscriptionFragment.PlanBasic

            binding.accountSubscriptionSummary.text = "Basic"
            binding.accountSubscriptionPrice.text = "Free"
            binding.accountSubscriptionPrice.visibility = View.VISIBLE
            binding.accountSubscriptionPriceDescription.visibility = View.GONE
            return
        }

        val mostRecentPurchase = planPurchases.maxBy { purchase -> purchase.purchaseTime }

        binding.accountSubscriptionPrice.visibility = View.VISIBLE
        binding.accountSubscriptionPriceDescription.visibility = View.VISIBLE

        if ("ultimate" in mostRecentPurchase.products) {
            currentPlan = SubscriptionFragment.PlanUltimate
            binding.accountSubscriptionSummary.text = "Ultimate"
            binding.accountSubscriptionPrice.text = "$12"
        } else if ("monthly_transfer_1tib" in mostRecentPurchase.products) {
            currentPlan = SubscriptionFragment.Plan1Tib
            binding.accountSubscriptionSummary.text = "1TiB Monthly"
            binding.accountSubscriptionPrice.text = "$6"
        } else if ("monthly_transfer_300gib" in mostRecentPurchase.products) {
            currentPlan = SubscriptionFragment.Plan300Gib
            binding.accountSubscriptionSummary.text = "300GiB Monthly"
            binding.accountSubscriptionPrice.text = "$3"
        } else {
            // should not reach here
            // check the conditions in the filter above
            currentPlan = null
            binding.accountSubscriptionSummary.text = "Unknown"
            binding.accountSubscriptionPrice.visibility = View.GONE
            binding.accountSubscriptionPriceDescription.visibility = View.GONE
        }


    }


    // FIXME use billing client to fetch current subscription with auto renew enabled
    // https://developer.android.com/google/play/billing/integrate#groovy

}