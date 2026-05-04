package com.example.app.billing

import android.app.Activity

interface BillingGateway {
    suspend fun connect(): Boolean
    suspend fun queryPurchasesAsync(): List<String>
    suspend fun buyPro(activity: Activity): Boolean
    suspend fun acknowledgePurchase(token: String): Boolean
}