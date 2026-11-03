package com.musarum.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isSelected: (String?) -> Boolean
)

// Orden fijo: Inicio · Buscar · Biblioteca · Perfil.
private val navItems = listOf(
    NavItem(Routes.HOME, "Inicio", Icons.Filled.Home) {
        it == Routes.HOME ||
            it == Routes.SONGS ||
            it == Routes.ALBUMS ||
            it == Routes.ARTISTS ||
            it?.startsWith("album/") == true ||
            it?.startsWith("artist/") == true
    },
    NavItem(Routes.SEARCH, "Buscar", Icons.Filled.Search) { it == Routes.SEARCH },
    NavItem(Routes.LIBRARY, "Biblioteca", Icons.Filled.Bookmarks) {
        it == Routes.LIBRARY ||
            it == Routes.FAVORITES ||
            it?.startsWith("playlist/") == true
    },
    NavItem(Routes.PROFILE, "Perfil", Icons.Filled.Person) { it == Routes.PROFILE }
)

@Composable
fun MusarumBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        navItems.forEach { item ->
            val selected = item.isSelected(currentRoute)
            // Si el usuario está en un hijo de la sección (p. ej. Canciones
            // dentro de Inicio) un toque sobre el ítem le lleva de vuelta al
            // destino raíz; solo se ignora cuando ya está EXACTAMENTE en él.
            NavigationBarItem(
                selected = selected,
                onClick = { if (currentRoute != item.route) onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}
