package com.jubahir.arrowroute

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Debug aid: shows the error of the previous crash once, so it can be screenshotted and reported. */
@Composable
fun CrashScreen(text: String, onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1004))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text("Last error (send a screenshot of this)", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            color = Color(0xFFFFD27A),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3FBF2C))
                .clickable { onContinue() }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Continue", color = Color.White, fontSize = 16.sp)
        }
    }
}
