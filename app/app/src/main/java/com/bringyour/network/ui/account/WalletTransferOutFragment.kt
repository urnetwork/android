package com.bringyour.network.ui.account

import android.graphics.Point
import android.os.Bundle
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
import com.bringyour.network.R
import org.w3c.dom.Text

class WalletTransferOutFragment: DialogFragment() {



    private var hasAddress: Boolean = false
    private var hasAmount: Boolean = false
    private var hasTerms: Boolean = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_wallet_transfer_out, container, false)

        val walletId = arguments?.getString("walletId") ?: return root
        val walletBalanceUsdc = arguments?.getDouble("walletBalance", 0.0) ?: return root
        val walletToken = arguments?.getString("walletToken") ?: return root
        val walletTokenId = arguments?.getString("walletToken") ?: return root
        val walletChain = arguments?.getString("walletChain") ?: return root
        val walletChainSymbol = arguments?.getString("walletChainSymbol") ?: return root


        val walletBalance = root.findViewById<TextView>(R.id.wallet_balance)
        val walletTransferOutDescription = root.findViewById<TextView>(R.id.wallet_transfer_out_description)
        val walletTransferOutAddress = root.findViewById<EditText>(R.id.wallet_transfer_out_address)
        val walletTransferOutAddressValid = root.findViewById<TextView>(R.id.wallet_transfer_out_address_valid)
        val walletTransferOutAddressInvalid = root.findViewById<TextView>(R.id.wallet_transfer_out_address_invalid)
        val walletTransferOutAddressSpinner = root.findViewById<ProgressBar>(R.id.wallet_transfer_out_address_spinner)
        val walletTransferAmount = root.findViewById<EditText>(R.id.wallet_transfer_amount)
        val walletTransferMaxAmountButton = root.findViewById<Button>(R.id.wallet_transfer_max_amount_button)
        val walletTransferTestAmountButton = root.findViewById<Button>(R.id.wallet_transfer_test_amount_button)
        val walletTransferOutTerms = root.findViewById<CheckBox>(R.id.wallet_transfer_out_terms)
        val walletTransferOutButton = root.findViewById<Button>(R.id.wallet_transfer_out_button)
        val walletTransferOutSpinner = root.findViewById<ProgressBar>(R.id.wallet_transfer_out_spinner)
        val walletTransferOutSuccess = root.findViewById<TextView>(R.id.wallet_transfer_out_success)
        val walletTransferOutError = root.findViewById<TextView>(R.id.wallet_transfer_out_error)


        // https://developers.circle.com/w3s/reference/createvalidateaddress-1







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
}