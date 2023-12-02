package com.bringyour.network.ui.account

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Point
import android.os.Bundle
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
import com.bringyour.network.R
import org.w3c.dom.Text


class SubscriptionFragment: DialogFragment() {

//    init {
//        setCancelable(true)
//    }

//    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//        val dialog = super.onCreateDialog(savedInstanceState)
//        dialog.setCanceledOnTouchOutside(true)
//        return dialog
//    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_subscription, container, false)

        val transferBalanceGib = arguments?.getInt("transferBalanceGib", 0) ?: return root
        val currentPlan = arguments?.getString("currentPlan") ?: return root



        val plan300gibRadio = root.findViewById<RadioButton>(R.id.plan_300gib_radio)
        val plan300gibContainer = root.findViewById<ViewGroup>(R.id.plan_300gib_container)
        val plan300gibCurrent = root.findViewById<TextView>(R.id.plan_300gib_current)
        val plan1tibRadio = root.findViewById<RadioButton>(R.id.plan_1tib_radio)
        val plan1tibContainer = root.findViewById<ViewGroup>(R.id.plan_1tib_container)
        val plan1tibCurrent = root.findViewById<TextView>(R.id.plan_1tib_current)
        val planUltimateRadio = root.findViewById<RadioButton>(R.id.plan_ultimate_radio)
        val planUltimateContainer = root.findViewById<ViewGroup>(R.id.plan_ultimate_container)
        val planUltimateCurrent = root.findViewById<TextView>(R.id.plan_ultimate_current)
        val planBasicRadio = root.findViewById<RadioButton>(R.id.plan_basic_radio)
        val planBasicContainer = root.findViewById<ViewGroup>(R.id.plan_basic_container)
        val planBasicCurrent = root.findViewById<TextView>(R.id.plan_basic_current)
        val subscriptionData = root.findViewById<TextView>(R.id.subscription_data)
        val subscriptionDataUpdated = root.findViewById<TextView>(R.id.subscription_data_updated)
        val subscriptionPrice = root.findViewById<TextView>(R.id.subscription_price)
        val subscriptionContinueButton = root.findViewById<Button>(R.id.subscription_continue_button)

        subscriptionData.text = humanGib(transferBalanceGib)

        val selectPlan = { plan: String ->
            if (plan == Plan300Gib) {
                plan300gibRadio.isChecked = true
                plan300gibContainer.setBackgroundResource(R.drawable.subscription_selected)

                subscriptionPrice.text = "$3"
                if (plan == currentPlan) {
                    subscriptionDataUpdated.text = humanGib(transferBalanceGib)
                } else {
                    val transferBalanceUpdatedGib = transferBalanceGib + 300
                    subscriptionDataUpdated.text = humanGib(transferBalanceUpdatedGib)
                }
            } else {
                plan300gibRadio.isChecked = false
                plan300gibContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            if (plan == Plan1Tib) {
                plan1tibRadio.isChecked = true
                plan1tibContainer.setBackgroundResource(R.drawable.subscription_selected)

                subscriptionPrice.text = "$6"
                if (plan == currentPlan) {
                    subscriptionDataUpdated.text = humanGib(transferBalanceGib)
                } else {
                    val transferBalanceUpdatedGib = transferBalanceGib + 1024
                    subscriptionDataUpdated.text = humanGib(transferBalanceUpdatedGib)
                }
            } else {
                plan1tibRadio.isChecked = false
                plan1tibContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            if (plan == PlanUltimate) {
                planUltimateRadio.isChecked = true
                planUltimateContainer.setBackgroundResource(R.drawable.subscription_selected_ultimate)

                subscriptionPrice.text = "$12"
                if (plan == currentPlan) {
                    subscriptionDataUpdated.text = humanGib(transferBalanceGib)
                } else {
                    val transferBalanceUpdatedGib = transferBalanceGib + 10 * 1024
                    subscriptionDataUpdated.text = humanGib(transferBalanceUpdatedGib)
                }
            } else {
                planUltimateRadio.isChecked = false
                planUltimateContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            if (plan == PlanBasic) {
                planBasicRadio.isChecked = true
                planBasicContainer.setBackgroundResource(R.drawable.subscription_selected)

                subscriptionPrice.text = "None"
                subscriptionDataUpdated.text = humanGib(transferBalanceGib)
            } else {
                planBasicRadio.isChecked = false
                planBasicContainer.setBackgroundResource(R.drawable.subscription_unselected)
            }

            subscriptionContinueButton.isEnabled = (plan != currentPlan)
        }

        plan300gibRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(Plan300Gib)
            }
        }

        plan300gibContainer.setOnClickListener {
            selectPlan(Plan300Gib)
        }

        plan1tibRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(Plan1Tib)
            }
        }

        plan1tibContainer.setOnClickListener {
            selectPlan(Plan1Tib)
        }

        planUltimateRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(PlanUltimate)
            }
        }

        planUltimateContainer.setOnClickListener {
            selectPlan(PlanUltimate)
        }

        planBasicRadio.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectPlan(PlanBasic)
            }
        }

        planBasicContainer.setOnClickListener {
            selectPlan(PlanBasic)
        }


        subscriptionContinueButton.setOnClickListener {
            // FIXME submit to play billing
        }



        selectPlan(currentPlan)
        plan300gibCurrent.visibility = if (currentPlan == Plan300Gib) View.VISIBLE else View.GONE
        plan1tibCurrent.visibility = if (currentPlan == Plan1Tib) View.VISIBLE else View.GONE
        planUltimateCurrent.visibility = if (currentPlan == PlanUltimate) View.VISIBLE else View.GONE
        planBasicCurrent.visibility = if (currentPlan == PlanBasic) View.VISIBLE else View.GONE





        return root
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
        window.setLayout(Math.min((size.x * 0.90).toInt(), px), WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
        super.onResume()
    }


    companion object {

        const val Plan300Gib: String = "plan_300gib"
        const val Plan1Tib: String = "plan_1tib"
        const val PlanUltimate: String = "plan_ultimate"
        const val PlanBasic: String = "plan_basic"


        fun humanGib(transferBalanceGib: Int): String {
            if (transferBalanceGib < 1024) {
                return "${transferBalanceGib}Gib"
            } else {
                return String.format("%.1fTib", transferBalanceGib / 1024f)
            }
        }
    }

}
