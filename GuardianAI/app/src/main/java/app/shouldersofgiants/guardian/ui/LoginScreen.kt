package app.shouldersofgiants.guardian.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import app.shouldersofgiants.guardian.Constants
import app.shouldersofgiants.guardian.viewmodel.GuardianViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: GuardianViewModel = viewModel()
) {
    var authMode by rememberSaveable { mutableStateOf("choices") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current

    // Google Sign-In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(Constants.WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    account.idToken?.let { idToken ->
                        isLoading = true
                        viewModel.signInWithGoogle(idToken) { success ->
                            isLoading = false
                            if (success) onLoginSuccess()
                            else Toast.makeText(context, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: ApiException) {
                    Toast.makeText(context, "Google Sign-In Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Guardian AI",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Your Safety Companion",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (authMode) {
                "choices" -> {
                    AuthChoices(
                        onPhoneClick = { authMode = "phone" },
                        onEmailClick = { authMode = "email_sign_in" },
                        onGoogleClick = { 
                            launcher.launch(googleSignInClient.signInIntent) 
                        }
                    )
                }
                "phone" -> {
                    PhoneAuthUI(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { phoneNumber = it },
                        otpCode = otpCode,
                        onOtpCodeChange = { otpCode = it },
                        verificationId = verificationId,
                        isLoading = isLoading,
                        onSendCode = {
                            isLoading = true
                            viewModel.sendVerificationCode(context as Activity, phoneNumber) { vid ->
                                isLoading = false
                                if (vid != null) verificationId = vid
                                else Toast.makeText(context, "Failed to send code", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onVerifyCode = {
                            isLoading = true
                            viewModel.verifyCode(verificationId!!, otpCode) { success ->
                                isLoading = false
                                if (success) onLoginSuccess()
                                else Toast.makeText(context, "Invalid Code", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBack = { authMode = "choices"; verificationId = null }
                    )
                }
                "email_sign_in" -> {
                    EmailAuthUI(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        isLoading = isLoading,
                        isSignUp = false,
                        onAction = {
                            isLoading = true
                            viewModel.signInWithEmail(email, password) { success, errorMsg ->
                                isLoading = false
                                if (success) onLoginSuccess()
                                else Toast.makeText(context, errorMsg ?: "Login Failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSwitchMode = { authMode = "email_sign_up" },
                        onBack = { authMode = "choices" }
                    )
                }
                "email_sign_up" -> {
                    EmailAuthUI(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        isLoading = isLoading,
                        isSignUp = true,
                        onAction = {
                            isLoading = true
                            viewModel.signUpWithEmail(email, password) { success, errorMsg ->
                                isLoading = false
                                if (success) onLoginSuccess()
                                else Toast.makeText(context, errorMsg ?: "Signup Failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSwitchMode = { authMode = "email_sign_in" },
                        onBack = { authMode = "choices" }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthChoices(
    onPhoneClick: () -> Unit,
    onEmailClick: () -> Unit,
    onGoogleClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = onPhoneClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
        ) {
            Text("Phone Number", color = Color.White)
        }
        
        OutlinedButton(
            onClick = onEmailClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Email Address", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color.Gray, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onGoogleClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Sign in with Google", color = Color.Black)
        }
    }
}

@Composable
fun PhoneAuthUI(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    otpCode: String,
    onOtpCodeChange: (String) -> Unit,
    verificationId: String?,
    isLoading: Boolean,
    onSendCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (verificationId == null) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = { Text("Phone Number (+1...)", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                     focusedTextColor = Color.White,
                     unfocusedTextColor = Color.White,
                     focusedBorderColor = Color(0xFF4285F4),
                     unfocusedBorderColor = Color.Gray
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Button(
                onClick = onSendCode, 
                enabled = !isLoading && phoneNumber.isNotEmpty(), 
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White) else Text("Send Code")
            }
        } else {
            OutlinedTextField(
                value = otpCode,
                onValueChange = onOtpCodeChange,
                label = { Text("Verification Code", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth(),
                 colors = OutlinedTextFieldDefaults.colors(
                     focusedTextColor = Color.White,
                     unfocusedTextColor = Color.White
                ),
                 keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = onVerifyCode, 
                enabled = !isLoading && otpCode.isNotEmpty(), 
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White) else Text("Verify & Login")
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to Methods", color = Color.LightGray)
        }
    }
}

@Composable
fun EmailAuthUI(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    isSignUp: Boolean,
    onAction: () -> Unit,
    onSwitchMode: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email", color = Color.LightGray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                 focusedTextColor = Color.White,
                 unfocusedTextColor = Color.White,
                 focusedBorderColor = Color(0xFF4285F4),
                 unfocusedBorderColor = Color.Gray
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password", color = Color.LightGray) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                 focusedTextColor = Color.White,
                 unfocusedTextColor = Color.White,
                 focusedBorderColor = Color(0xFF4285F4),
                 unfocusedBorderColor = Color.Gray
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Button(
            onClick = onAction, 
            enabled = !isLoading && email.isNotEmpty() && password.length >= 6, 
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isSignUp) Color(0xFF4285F4) else Color(0xFF34A853))
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White) else Text(if (isSignUp) "Sign Up" else "Login")
        }
        TextButton(onClick = onSwitchMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                color = Color.White
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to Methods", color = Color.LightGray)
        }
    }
}

@Composable
fun CircularProgressIndicator(color: Color) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 2.dp,
        color = color
    )
}
