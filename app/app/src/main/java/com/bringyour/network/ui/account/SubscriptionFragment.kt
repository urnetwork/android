package com.bringyour.network.ui.account

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentSubscriptionBinding
import com.bringyour.network.databinding.FragmentWalletTransferOutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.w3c.dom.Text


class SubscriptionFragment: DialogFragment() {

    private var _binding: FragmentSubscriptionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

//    init {
//        setCancelable(true)
//    }

//    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//        val dialog = super.onCreateDialog(savedInstanceState)
//        dialog.setCanceledOnTouchOutside(true)
//        return dialog
//    }


    var selectedPlan: String? = null

    var billingClient: BillingClient? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
//        val root = inflater.inflate(R.layout.fragment_subscription, container, false)
        _binding = FragmentSubscriptionBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val transferBalanceGib = arguments?.getLong("transferBalanceGib", 0) ?: return root
        val currentPlan = arguments?.getString("currentPlan") ?: return root



//        val plan300gibRadio = root.findViewById<RadioButton>(R.id.plan_300gib_radio)
//        val plan300gibContainer = root.findViewById<ViewGroup>(R.id.plan_300gib_container)
//        val plan300gibCurrent = root.findViewById<TextView>(R.id.plan_300gib_current)
//        val plan1tibRadio = root.findViewById<RadioButton>(R.id.plan_1tib_radio)
//        val plan1tibContainer = root.findViewById<ViewGroup>(R.id.plan_1tib_container)
//        val plan1tibCurrent = root.findViewById<TextView>(R.id.plan_1tib_current)
//        val planUltimateRadio = root.findViewById<RadioButton>(R.id.plan_ultimate_radio)
//        val planUltimateContainer = root.findViewById<ViewGroup>(R.id.plan_ultimate_container)
//        val planUltimateCurrent = root.findViewById<TextView>(R.id.plan_ultimate_current)
//        val planBasicRadio = root.findViewById<RadioButton>(R.id.plan_basic_radio)
//        val planBasicContainer = root.findViewById<ViewGroup>(R.id.plan_basic_container)
//        val planBasicCurrent = root.findViewById<TextView>(R.id.plan_basic_current)
//        val subscriptionData = root.findViewById<TextView>(R.id.subscription_data)
//        val subscriptionDataUpdated = root.findViewById<TextView>(R.id.subscription_data_updated)
//        val subscriptionPrice = root.findViewById<TextView>(R.id.subscription_price)
//        val subscriptionContinueButton = root.findViewById<Button>(R.id.subscription_continue_button)

        binding.plan300gibCurrent.visibility = if (currentPlan == Plan300Gib) View.VISIBLE else View.GONE
        binding.plan1tibCurrent.visibility = if (currentPlan == Plan1Tib) View.VISIBLE else View.GONE
        binding.planUltimateCurrent.visibility = if (currentPlan == PlanUltimate) View.VISIBLE else View.GONE
        binding.planBasicCurrent.visibility = if (currentPlan == PlanBasic) View.VISIBLE else View.GONE

        binding.subscriptionContinueSpinner.visibility = View.GONE
        binding.subscriptionError.visibility = View.GONE

        binding.subscriptionData.text = humanGib(transferBalanceGib)

        val selectPlan = { plan: String ->
            selectedPlan = plan

            if (plan == Plan300Gib) {
                binding.plan300gibRadio.isChecked = true
                binding.plan300gibContainer.setBackgroundResource(R.drawable.subscription_selected)

                binding.subscriptionPrice.text = "$3"
                if (plan == currentPlan) {
                    binding.subscriptionDataUpdated.text = humanGib(transferBalanceGib)
                } else {
                    val transferBalanceUpdatedGib = transferBalanceGib + 300
                    binding.subscriptionDataUpdated.text = humanGib(transferBalanceUpdatedGib)
                }
            } else {
                binding.plan300gibRadio.isChecked = false
                binding.plan300gibContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            if (plan == Plan1Tib) {
                binding.plan1tibRadio.isChecked = true
                binding.plan1tibContainer.setBackgroundResource(R.drawable.subscription_selected)

                binding.subscriptionPrice.text = "$6"
                if (plan == currentPlan) {
                    binding.subscriptionDataUpdated.text = humanGib(transferBalanceGib)
                } else {
                    val transferBalanceUpdatedGib = transferBalanceGib + 1024
                    binding.subscriptionDataUpdated.text = humanGib(transferBalanceUpdatedGib)
                }
            } else {
                binding.plan1tibRadio.isChecked = false
                binding.plan1tibContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            if (plan == PlanUltimate) {
                binding.planUltimateRadio.isChecked = true
                binding.planUltimateContainer.setBackgroundResource(R.drawable.subscription_selected_ultimate)

                binding.subscriptionPrice.text = "$12"
                if (plan == currentPlan) {
                    binding.subscriptionDataUpdated.text = humanGib(transferBalanceGib)
                } else {
                    val transferBalanceUpdatedGib = transferBalanceGib + 10 * 1024
                    binding.subscriptionDataUpdated.text = humanGib(transferBalanceUpdatedGib)
                }
            } else {
                binding.planUltimateRadio.isChecked = false
                binding.planUltimateContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            if (plan == PlanBasic) {
                binding.planBasicRadio.isChecked = true
                binding.planBasicContainer.setBackgroundResource(R.drawable.subscription_selected)

                binding.subscriptionPrice.text = "None"
                binding.subscriptionDataUpdated.text = humanGib(transferBalanceGib)
            } else {
                binding.planBasicRadio.isChecked = false
                binding.planBasicContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            binding.subscriptionContinueButton.isEnabled = (plan != currentPlan)
        }

        binding.plan300gibRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(Plan300Gib)
            }
        }

        binding.plan300gibContainer.setOnClickListener {
            selectPlan(Plan300Gib)
        }

        binding.plan1tibRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(Plan1Tib)
            }
        }

        binding.plan1tibContainer.setOnClickListener {
            selectPlan(Plan1Tib)
        }

        binding.planUltimateRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(PlanUltimate)
            }
        }

        binding.planUltimateContainer.setOnClickListener {
            selectPlan(PlanUltimate)
        }

        binding.planBasicRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(PlanBasic)
            }
        }

        binding.planBasicContainer.setOnClickListener {
            selectPlan(PlanBasic)
        }


        binding.subscriptionContinueButton.setOnClickListener {
            val inProgress = { busy: Boolean ->
                if (busy) {
                    binding.subscriptionContinueSpinner.visibility = View.VISIBLE
                    binding.subscriptionContinueButton.isEnabled = false
                } else {
                    binding.subscriptionContinueSpinner.visibility = View.GONE
                    binding.subscriptionContinueButton.isEnabled = true
                }
            }

            selectedPlan?.let { selectedPlan ->

                inProgress(true)

                binding.subscriptionError.visibility = View.GONE

                val purchasesUpdatedListener =
                    PurchasesUpdatedListener { billingResult, purchases ->
                        inProgress(false)
                        billingClient?.endConnection()

                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
//                            for (purchase in purchases) {
//                                handle(purchase)
//                            }
                            dismiss()
                        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                            // Handle an error caused by a user cancelling the purchase flow.

                        } else {
                            // Handle any other error codes.
                            // FIXME  show error message of billing error

                            binding.subscriptionError.text = String.format("Billing error: %d %s", billingResult.responseCode, billingResult.debugMessage)
                            binding.subscriptionError.visibility = View.VISIBLE
                        }
                    }

                billingClient?.endConnection()

                billingClient = BillingClient.newBuilder(requireContext())
                    .setListener(purchasesUpdatedListener)
                    .enablePendingPurchases()
                    .build()

                billingClient?.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            // The BillingClient is ready. You can query purchases here.
                            lifecycleScope.launch(Dispatchers.Main.immediate) {
                                purchase(selectedPlan)
                            }
                        } else {
                            // show error message of billing error
                            // FIXME show error

                            binding.subscriptionError.text = String.format("Billing error: %d %s", billingResult.responseCode, billingResult.debugMessage)
                            binding.subscriptionError.visibility = View.VISIBLE

                            inProgress(false)
                            billingClient?.endConnection()
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        // Try to restart the connection on the next request to
                        // Google Play by calling the startConnection() method.

                        binding.subscriptionError.text = String.format("Billing error: Disconnected")
                        binding.subscriptionError.visibility = View.VISIBLE

                        inProgress(false)
                        billingClient?.endConnection()
                    }
                })
            }
        }

        binding.subscriptionHelpButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.bringyour.com")))
        }

        selectPlan(currentPlan)

        return root
    }


    suspend fun purchase(plan: String) {
        val productList = mutableListOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("monthly_transfer_300gib")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),

            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("monthly_transfer_1tib")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),

            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("ultimate")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
        params.setProductList(productList)

        // leverage queryProductDetails Kotlin extension function
        val productDetailsResult: ProductDetailsResult? = withContext(Dispatchers.IO) {
            billingClient?.queryProductDetails(params.build())
        }



        // Process the result.

        // An activity reference from which the billing flow will be launched.
//        val activity : Activity = ...;


        // FIXME find the product details that correspond to the selected plan


        val productDetails = productDetailsResult?.productDetailsList?.find { productDetails: ProductDetails ->
            when (plan) {
                Plan300Gib -> productDetails.productId == "monthly_transfer_300gib"
                Plan1Tib -> productDetails.productId == "monthly_transfer_1tib"
                PlanUltimate -> productDetails.productId == "ultimate"
                else -> false
            }
        }

        Log.i("SubscriptionFragment", "FOUND PRODUCT DETAILS ${productDetails}")

        if (productDetails == null) {
            binding.subscriptionError.text = String.format("Product not found.")
            binding.subscriptionError.visibility = View.VISIBLE

            return
        }

        // just choose the first offer
        val offer = productDetails.subscriptionOfferDetails?.first()

        if (offer == null) {
            binding.subscriptionError.text = String.format("Offer not found.")
            binding.subscriptionError.visibility = View.VISIBLE

            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetails)
                // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                // for a list of offers that are available to the user
                .setOfferToken(offer.offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

// Launch the billing flow
        val billingResult = billingClient?.launchBillingFlow(requireActivity(), billingFlowParams)
    }


//    override fun onCancel(dialog: DialogInterface) {
//        super.onCancel(dialog)
//
//        dismiss()
//    }


    override fun onResume() {
        // Set the width of the dialog proportional to 90% of the screen width
        val window = dialog!!.window
        val size = Point()
//        window!!.windowManager.currentWindowMetrics.bounds
        val display = window!!.windowManager.defaultDisplay
        display.getSize(size)
        val px = Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            480.0f,
            resources.displayMetrics
        ))
        window.setLayout((size.x * 0.90).toInt().coerceAtMost(px), WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
        super.onResume()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        billingClient?.endConnection()
    }


    companion object {

        const val Plan300Gib: String = "plan_300gib"
        const val Plan1Tib: String = "plan_1tib"
        const val PlanUltimate: String = "plan_ultimate"
        const val PlanBasic: String = "plan_basic"


        fun humanGib(transferBalanceGib: Long): String {
            if (transferBalanceGib < 1024) {
                return "${transferBalanceGib}Gib"
            } else {
                return String.format("%.1fTib", transferBalanceGib / 1024f)
            }
        }
    }

}
