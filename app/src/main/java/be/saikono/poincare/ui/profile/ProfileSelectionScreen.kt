package be.saikono.poincare.ui.profile

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LocalAppLanguage
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.*

@Composable
fun ProfileSelectionScreen(
    viewModel: ProfileViewModel,
    onCreateProfile: () -> Unit,
    onProfileSelected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val appLanguage = LocalAppLanguage.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
            .systemNavigationMargin(),
        contentAlignment = Alignment.Center
    ) {
        if (!uiState.isLoading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "Wie gaat er mee op missie?",
                style = MaterialTheme.typography.headlineMedium,
                color = StarWhite,
                textAlign = TextAlign.Center
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(uiState.profiles) { profile ->
                    ProfileItem(
                        profile = profile,
                        isSelected = profile.id == uiState.selectedProfileId,
                        onClick = {
                            viewModel.selectProfile(profile.id, onProfileSelected)
                        }
                    )
                }

                item {
                    AddProfileItem(onClick = onCreateProfile)
                }
            }
        }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { uiState.loadingProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = PlanetGold,
                        trackColor = StarWhite.copy(alpha = 0.14f),
                        strokeWidth = 10.dp
                    )
                    Text(
                        text = "${uiState.loadingProgress.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = StarWhite,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Text(
                    text = when {
                        appLanguage.startsWith("fr") -> "Chargement des exercices ..."
                        appLanguage.startsWith("en") -> "Loading exercises ..."
                        else -> "Oefeningen laden ..."
                    },
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ProfileItem(
    profile: ChildProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(if (isSelected) StarWhite else NebulaPurple)
                .padding(if (isSelected) 4.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(NebulaPurple),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for Avatar
                Text(
                    text = profile.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = StarWhite
                )
            }
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = StarWhite
        )
    }
}

@Composable
fun AddProfileItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(NebulaPurple.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Nieuw profiel",
                modifier = Modifier.size(48.dp),
                tint = StarWhite
            )
        }
        Text(
            text = "Nieuw",
            style = MaterialTheme.typography.titleMedium,
            color = StarWhite
        )
    }
}
