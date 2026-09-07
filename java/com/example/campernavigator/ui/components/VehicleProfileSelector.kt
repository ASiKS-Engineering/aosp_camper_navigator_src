package com.example.campernavigator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.campernavigator.model.VehicleProfile
import com.example.campernavigator.model.VehicleType

@Composable
fun VehicleProfileSelector(
    selectedProfile: VehicleProfile,
    onProfileSelected: (VehicleProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = listOf(
        VehicleProfile("Wohnmobil", VehicleType.CAMPER, 3.2, 2.3, 6.0, 3.5),
        VehicleProfile("Transporter", VehicleType.VAN, 2.5, 2.0, 5.0, 2.8),
        VehicleProfile("LKW", VehicleType.TRUCK, 4.0, 2.5, 12.0, 18.0)
    )

    Column(modifier = modifier.padding(8.dp)) {
        Text(text = "Fahrzeugprofil wählen")
        Row {
            profiles.forEach { profile ->
                FilterChip(
                    selected = selectedProfile.type == profile.type,
                    onClick = { onProfileSelected(profile) },
                    label = { Text(profile.name) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
