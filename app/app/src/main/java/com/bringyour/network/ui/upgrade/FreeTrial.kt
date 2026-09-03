package com.bringyour.network.ui.upgrade

/**
 * The free trial the annual plan starts with, in days, as printed on the plan
 * cards. The trial itself is configured where the subscription is sold, and
 * must match this number: the Stripe payment links (github flavor), the Play
 * Console subscription offer (play flavor) and the App Store Connect
 * introductory offer on iOS (note the App Store only offers fixed durations:
 * 3 days, 1 week, 2 weeks, 1 month and up).
 */
const val FREE_TRIAL_DAYS = 14
