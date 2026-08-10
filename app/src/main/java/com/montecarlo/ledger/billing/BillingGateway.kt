package com.montecarlo.ledger.billing

import android.app.Activity

/**
 * Planned Play Billing integration point.
 *
 * **NOT YET IMPLEMENTED.** Before shipping with IAP:
 * 1. Implement this interface with [com.android.billingclient.api.BillingClient]
 * 2. Add `<uses-permission android:name="com.android.vending.BILLING" />` to AndroidManifest.xml
 * 3. Ensure [acknowledgePurchase] is called within 3 days of purchase (Google policy)
 * 4. Register SKU product IDs in Google Play Console
 *
 * See: https://developer.android.com/google/play/billing/integrate
 */
interface BillingGateway {
    suspend fun connect(): Boolean
    suspend fun queryPurchasesAsync(): List<String>
    suspend fun buyPro(activity: Activity): Boolean
    suspend fun acknowledgePurchase(token: String): Boolean
}