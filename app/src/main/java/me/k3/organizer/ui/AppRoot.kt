package me.k3.organizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.k3.organizer.R
import me.k3.organizer.ui.day.DayScreen
import me.k3.organizer.ui.day.DayViewModel
import me.k3.organizer.ui.week.WeekScreen
import me.k3.organizer.ui.week.WeekViewModel

enum class Tab { DAY, WEEK }

/**
 * The app's frame: a bottom navigation bar switching between screens. Two tabs do not need a
 * navigation library; the open tab is plain state (saved, so it survives rotation).
 * Both ViewModels belong to the Activity, so each screen keeps its day/week while the other is shown.
 */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.DAY) }
    val dayViewModel: DayViewModel = viewModel(factory = DayViewModel.Factory)
    val weekViewModel: WeekViewModel = viewModel(factory = WeekViewModel.Factory)

    fun openWeek() {
        // The week of the day being looked at, not necessarily this week.
        weekViewModel.showWeekOf(dayViewModel.shownDay)
        tab = Tab.WEEK
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.DAY,
                    onClick = { tab = Tab.DAY },
                    icon = { Icon(painterResource(R.drawable.ic_view_day), contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_day)) },
                )
                NavigationBarItem(
                    selected = tab == Tab.WEEK,
                    onClick = { if (tab != Tab.WEEK) openWeek() },
                    icon = { Icon(painterResource(R.drawable.ic_view_week), contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_week)) },
                )
            }
        },
    ) { padding ->
        // This Scaffold handles the system bars; screens inside must not add them again.
        val content = Modifier.padding(padding).consumeWindowInsets(padding)
        when (tab) {
            Tab.DAY -> DayScreen(modifier = content, viewModel = dayViewModel)
            Tab.WEEK -> {
                // Back from the week goes to the day instead of leaving the app.
                BackHandler { tab = Tab.DAY }
                WeekScreen(
                    viewModel = weekViewModel,
                    onOpenDay = { date ->
                        dayViewModel.showDay(date)
                        tab = Tab.DAY
                    },
                    modifier = content,
                )
            }
        }
    }
}
