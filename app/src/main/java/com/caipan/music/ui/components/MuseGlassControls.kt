package com.caipan.music.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

@Composable
fun MuseGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    backdrop: Backdrop? = null,
    enabled: Boolean = true
) {
    if (!LocalMuseLiquidGlass.current) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
        )
        return
    }
    val thumbOffset = animateDpAsState(if (checked) 25.dp else 3.dp, label = "glassSwitchThumb")
    Box(
        Modifier.size(52.dp, 30.dp)
            .museGlass(
                backdrop = backdrop,
                shape = RoundedCornerShape(15.dp),
                tint = Color.White.copy(alpha = .08f),
                borderColor = Color.White.copy(alpha = .2f),
                location = BlurLocation.CARDS
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier.offset(x = thumbOffset.value, y = 3.dp).size(24.dp)
                .museGlass(
                    backdrop = backdrop,
                    shape = CircleShape,
                    tint = if (checked) accentColor.copy(alpha = .85f) else Color.White.copy(alpha = .7f),
                    borderColor = if (checked) accentColor.copy(alpha = .7f) else Color.White.copy(alpha = .35f),
                    location = BlurLocation.CARDS
                )
        )
    }
}

@Composable
fun MuseGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    enabled: Boolean = true
) {
    val glassModifier = if (LocalMuseLiquidGlass.current) {
        modifier.museGlass(
            backdrop = backdrop,
            shape = RoundedCornerShape(24.dp),
            tint = Color.White.copy(alpha = .06f),
            borderColor = Color.White.copy(alpha = .14f),
            location = BlurLocation.CARDS
        )
    } else modifier
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = glassModifier,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = Color.White.copy(alpha = .12f)
        )
    )
}
