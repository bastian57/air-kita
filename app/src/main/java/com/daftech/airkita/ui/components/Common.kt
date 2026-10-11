package com.daftech.airkita.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.Amber
import com.daftech.airkita.ui.theme.AmberSoft
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Cyan
import com.daftech.airkita.ui.theme.CyanSoft
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.GreenSoft
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.ui.theme.Navy
import com.daftech.airkita.ui.theme.Red
import com.daftech.airkita.ui.theme.RedSoft

/** Header gradien navy->cyan dengan gelombang, sesuai mockup. */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String,
    slim: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Navy, AirBlue, Cyan)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = if (slim) 34.dp else 52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                trailing?.invoke()
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.BottomCenter)
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.55f)
                var x = 0f
                while (x < w) {
                    quadraticTo(x + w / 8f, 0f, x + w / 4f, h * 0.55f)
                    x += w / 4f
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path, AppBg)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = Muted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        }
    }
}

@Composable
fun StatusBadge(text: String, kind: String) {
    val (bg, fg) = when (kind) {
        "green" -> GreenSoft to Green
        "red" -> RedSoft to Red
        "amber" -> AmberSoft to Amber
        else -> CyanSoft to AirBlue
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
fun SectionTitle(text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AirBlue,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AirBottomBar(nav: NavController, current: String) {
    val tabs = listOf(
        TabItem(Routes.HOME, "Beranda", Icons.Filled.Home),
        TabItem(Routes.METER_MANUAL, "Meter", Icons.Filled.Speed),
        TabItem(Routes.BILLS, "Tagihan", Icons.Filled.ReceiptLong),
        TabItem(Routes.KAS, "Kas", Icons.Filled.Wallet)
    )
    NavigationBar(containerColor = CardBg) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    if (current != tab.route) {
                        nav.navigate(tab.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                label = { Text(tab.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AirBlue,
                    selectedTextColor = AirBlue,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                    indicatorColor = CyanSoft
                )
            )
        }
    }
}

@Composable
fun AvatarCircle(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(AirBlue, Cyan))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}
