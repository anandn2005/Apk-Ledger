package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.LedgerViewModel
import com.example.ui.theme.LedgerMateTheme
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LedgerMate", appName)
  }

  @Test
  fun `launch MainActivity and initialize FirebaseApp without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull(activity)
  }

  @Test
  fun `render LedgerApp without crashing`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    if (FirebaseApp.getApps(app).isEmpty()) {
      FirebaseApp.initializeApp(app)
    }
    val vm = LedgerViewModel(app)
    composeTestRule.setContent {
      LedgerMateTheme {
        LedgerApp(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()
  }
}


