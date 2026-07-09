package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.GlassTokens
import java.util.Locale

@Composable
fun CategoryGlassIcon(
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val iconInfo = getIconForCategory(category)
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(iconInfo.color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconInfo.icon,
            contentDescription = category,
            tint = iconInfo.color,
            modifier = Modifier.size(iconSize)
        )
    }
}

private data class CategoryIconInfo(
    val icon: ImageVector,
    val color: Color
)

private fun getIconForCategory(category: String): CategoryIconInfo {
    return when (category.lowercase(Locale.ROOT).trim()) {
        "housing", "rent", "mortgage" -> CategoryIconInfo(Icons.Default.Home, GlassTokens.VioletLight)
        "food", "dining", "groceries", "restaurants" -> CategoryIconInfo(Icons.Default.Restaurant, GlassTokens.CyanBright)
        "transport", "transportation", "car", "gas", "fuel", "uber", "lyft" -> CategoryIconInfo(Icons.Default.DirectionsCar, GlassTokens.Cyan)
        "utilities", "electric", "water", "internet", "phone" -> CategoryIconInfo(Icons.Default.Power, GlassTokens.VioletLight)
        "entertainment", "subscriptions", "netflix", "gaming" -> CategoryIconInfo(Icons.Default.PlayCircle, GlassTokens.Cyan)
        "health", "medical", "pharmacy", "fitness", "gym" -> CategoryIconInfo(Icons.Default.Favorite, GlassTokens.PositiveGreen)
        "shopping", "clothing", "amazon" -> CategoryIconInfo(Icons.Default.ShoppingBag, GlassTokens.CyanBright)
        "income", "paycheck", "salary" -> CategoryIconInfo(Icons.Default.AttachMoney, GlassTokens.PositiveGreen)
        "investment", "stocks", "crypto" -> CategoryIconInfo(Icons.AutoMirrored.Filled.TrendingUp, GlassTokens.PositiveGreen)
        "insurance" -> CategoryIconInfo(Icons.Default.Shield, GlassTokens.VioletLight)
        "debt", "loan", "credit card" -> CategoryIconInfo(Icons.Default.CreditCard, GlassTokens.ErrorRed)
        "education" -> CategoryIconInfo(Icons.Default.School, GlassTokens.Cyan)
        "gifts", "charity", "donations" -> CategoryIconInfo(Icons.Default.Redeem, GlassTokens.CyanBright)
        "travel", "vacation" -> CategoryIconInfo(Icons.Default.Flight, GlassTokens.Cyan)
        "personal care", "beauty", "barber" -> CategoryIconInfo(Icons.Default.Face, GlassTokens.CyanBright)
        "pets" -> CategoryIconInfo(Icons.Default.Pets, GlassTokens.Cyan)
        "bank transfer", "transfer" -> CategoryIconInfo(Icons.Default.SyncAlt, GlassTokens.TextDim)
        else -> CategoryIconInfo(Icons.Default.Category, GlassTokens.TextDim)
    }
}
