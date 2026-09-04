package com.bringyour.network.ui.upgrade

/**
 * The plan prices the pickers print until the store has answered, or when it
 * never does (a build the store does not recognise, a failed query): the
 * figures the onboarding, the upgrade screen and the web show for Pro. The
 * real price always comes from the store once its offers have loaded; these
 * only keep the picker in its standard two-plan layout in the meantime. They
 * must match the configured prices: the Play base plans, the Stripe payment
 * links and the App Store products.
 */
const val FALLBACK_MONTHLY_PRICE = "$5.00"
const val FALLBACK_YEARLY_PRICE = "$40.00"
