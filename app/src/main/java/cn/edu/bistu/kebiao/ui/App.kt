package cn.edu.bistu.kebiao.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.edu.bistu.kebiao.data.ScheduleRepository
import cn.edu.bistu.kebiao.ui.editor.ScheduleEditorScreen
import cn.edu.bistu.kebiao.ui.editor.ScheduleEditorViewModel
import cn.edu.bistu.kebiao.ui.importer.ImportScreen
import cn.edu.bistu.kebiao.ui.importer.ImportViewModel
import cn.edu.bistu.kebiao.ui.timetable.TimetableScreen
import cn.edu.bistu.kebiao.ui.timetable.TimetableViewModel

@Composable
fun KebiaoApp(repository: ScheduleRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "timetable") {
        composable("timetable") {
            val viewModel: TimetableViewModel = viewModel(
                factory = TimetableViewModel.factory(repository),
            )
            TimetableScreen(
                viewModel = viewModel,
                onImport = { navController.navigate("import") },
                onManageSchedule = { navController.navigate("schedule-editor") },
            )
        }
        composable("import") {
            val viewModel: ImportViewModel = viewModel(
                factory = ImportViewModel.factory(repository),
            )
            ImportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onImported = { navController.popBackStack() },
            )
        }
        composable("schedule-editor") {
            val viewModel: ScheduleEditorViewModel = viewModel(
                factory = ScheduleEditorViewModel.factory(repository),
            )
            ScheduleEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
