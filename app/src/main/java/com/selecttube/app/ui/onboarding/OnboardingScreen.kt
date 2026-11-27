package com.selecttube.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.ui.theme.Brand500
import com.selecttube.app.ui.theme.Brand700
import com.selecttube.app.ui.theme.Brand900
import com.selecttube.app.ui.theme.Surface0
import com.selecttube.app.ui.theme.Surface1

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onOnboardingComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App Hero Logo & Header
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Surface1),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color(0xFF2979FF),
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Tube",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Watch what matters. Zero leakage, subscription-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA1A1A6),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Card 1: Adult Mode
            ModeSelectionCard(
                title = "For Myself",
                badge = "Adult Mode",
                subtitle = "Mindful, intentional watching for digital detox.",
                icon = Icons.Default.Person,
                bulletPoints = listOf(
                    "No passwords to remember",
                    "Mandatory 2-hour wait on new channels stops binge impulses",
                    "Zero algorithms or recommendation rabbit holes",
                ),
                isSelected = state.selectedMode == AppMode.ADULT,
                accentColor = Color(0xFF2979FF),
                onClick = { viewModel.selectMode(AppMode.ADULT) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Child Mode (Parental Controls)
            ModeSelectionCard(
                title = "For My Child",
                badge = "Parental Controls",
                subtitle = "Strictly supervised, safe entertainment for children.",
                icon = Icons.Default.ChildCare,
                bulletPoints = listOf(
                    "Parent Master Password required to add channels",
                    "Block Shorts to eliminate doom-scrolling",
                    "Screen time limits and bedtime schedule locks",
                ),
                isSelected = state.selectedMode == AppMode.CHILD,
                accentColor = Color(0xFF00E676),
                onClick = { viewModel.selectMode(AppMode.CHILD) },
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = viewModel::onContinueClick,
                enabled = !state.isCompleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.selectedMode == AppMode.ADULT) Color(0xFF2979FF) else Color(0xFF00C853),
                    contentColor = Color.White,
                ),
            ) {
                if (state.isCompleting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (state.selectedMode == AppMode.ADULT) "Continue in Adult Mode" else "Set Up Parental Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Parent Master Password Setup Dialog for Child Mode
        if (state.showPasswordDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPasswordDialog,
                containerColor = Brand700,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(28.dp),
                    )
                },
                title = {
                    Text(
                        text = "Set Parent Master Password",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "This password will be required whenever you want to add channels, adjust limits, or access Settings. Your child will not be able to bypass it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAAAAA),
                        )

                        OutlinedTextField(
                            value = state.passwordInput,
                            onValueChange = viewModel::onPasswordInputChange,
                            label = { Text("Parent Master Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color(0xFF555555),
                                focusedLabelColor = Color(0xFF00E676),
                                unfocusedLabelColor = Color(0xFFAAAAAA),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = state.confirmPasswordInput,
                            onValueChange = viewModel::onConfirmPasswordInputChange,
                            label = { Text("Confirm Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { viewModel.submitParentPassword() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color(0xFF555555),
                                focusedLabelColor = Color(0xFF00E676),
                                unfocusedLabelColor = Color(0xFFAAAAAA),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (!state.passwordError.isNullOrBlank()) {
                            Text(
                                text = state.passwordError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::submitParentPassword,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    ) {
                        Text("Confirm & Start")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPasswordDialog) {
                        Text("Cancel", color = Color(0xFFAAAAAA))
                    }
                },
            )
        }
    }
}

@Composable
private fun ModeSelectionCard(
    title: String,
    badge: String,
    subtitle: String,
    icon: ImageVector,
    bulletPoints: List<String>,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Brand700 else Color(0xFF131A29),
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accentColor else Color(0xFF2C3E55),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA),
                    )
                }

                // Selection Radio Checkmark
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .then(
                            if (!isSelected) Modifier.background(Color(0xFF2C3E55), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bullet points
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bulletPoints.forEach { point ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "• ",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDDDDDD),
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}
