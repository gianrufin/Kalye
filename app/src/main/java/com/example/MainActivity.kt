package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthManager
import com.example.auth.AuthScreen
import com.example.data.remote.KalyeFirestoreDataSource
import com.example.data.repository.KalyeRepository
import com.example.location.KalyeLocationTracker
import com.example.maps.OfflineTileCacheManager
import com.example.ui.KalyeViewModel
import com.example.ui.navigation.KalyeMainScaffold
import com.example.ui.theme.KalyeTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KalyeAppRoot()
        }
    }
}

@Composable
fun KalyeAppRoot() {
    val context = LocalContext.current
    val isFirebaseConfigured = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }

    if (!isFirebaseConfigured) {
        KalyeTheme {
            AuthScreen(
                onAuthSuccess = {},
                isFirebaseConfigured = false
            )
        }
        return
    }

    val auth = Firebase.auth
    val currentUser by AuthManager.authStateFlow(auth)
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        KalyeTheme {
            AuthScreen(
                onAuthSuccess = {},
                isFirebaseConfigured = true
            )
        }
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: KalyeViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val remote = KalyeFirestoreDataSource(db, Firebase.auth)
                        val repository = KalyeRepository(app, remote)
                        val tileManager = OfflineTileCacheManager(app)
                        val tracker = KalyeLocationTracker(app)
                        KalyeViewModel(
                            repository = repository,
                            tileManager = tileManager,
                            locationTracker = tracker,
                            currentUserId = uid,
                            userDisplayName = user.displayName,
                            userEmail = user.email
                        )
                    }
                }
            )
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            KalyeTheme(themeMode = preferences.themeMode) {
                KalyeMainScaffold(
                    viewModel = viewModel,
                    onSignedOut = {}
                )
            }
        }
    }
}
