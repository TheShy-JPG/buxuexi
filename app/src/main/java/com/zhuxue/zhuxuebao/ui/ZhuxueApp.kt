package com.zhuxue.zhuxuebao.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhuxue.zhuxuebao.di.AppContainer
import com.zhuxue.zhuxuebao.ui.screens.AnalysisScreen
import com.zhuxue.zhuxuebao.ui.screens.MainScreen
import com.zhuxue.zhuxuebao.ui.screens.RecordDetailScreen
import com.zhuxue.zhuxuebao.ui.screens.SettingsScreen
import com.zhuxue.zhuxuebao.ui.screens.SubjectReviewScreen
import com.zhuxue.zhuxuebao.ui.vm.AppTab
import com.zhuxue.zhuxuebao.ui.vm.AppVmFactory
import com.zhuxue.zhuxuebao.ui.vm.MainViewModel
import com.zhuxue.zhuxuebao.ui.vm.PracticeViewModel
import com.zhuxue.zhuxuebao.ui.vm.UploadViewModel

@Composable
fun ZhuxueApp(container: AppContainer) {
    val factory = remember(container) { AppVmFactory(container) }
    val mainVm: MainViewModel = viewModel(factory = factory)
    val uploadVm: UploadViewModel = viewModel(factory = factory)
    val practiceVm: PracticeViewModel = viewModel(factory = factory)
    val records by mainVm.records.collectAsStateWithLifecycle()
    val stats by mainVm.knowledgeStats.collectAsStateWithLifecycle()
    val uiPrefs by mainVm.uiPrefs.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                mainVm = mainVm,
                uploadVm = uploadVm,
                practiceVm = practiceVm,
                onOpenRecord = { id -> navController.navigate("record/$id") },
                onOpenSubject = { subject -> navController.navigate("subject/${Uri.encode(subject)}") },
                onOpenAnalysis = { navController.navigate("analysis") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable(
            route = "subject/{subject}",
            arguments = listOf(navArgument("subject") { type = NavType.StringType })
        ) { entry ->
            val subject = entry.arguments?.getString("subject").orEmpty()
            SubjectReviewScreen(
                subject = subject,
                records = records,
                stats = stats,
                knowledgeOrder = uiPrefs.knowledgeOrder,
                wrongOrder = uiPrefs.wrongOrder,
                onBack = { navController.popBackStack() },
                onPractice = { targetSubject, point ->
                    practiceVm.preset(targetSubject, point)
                    mainVm.setTab(AppTab.PRACTICE)
                    navController.popBackStack("main", false)
                },
                onOpenRecord = { id -> navController.navigate("record/$id") },
                onSaveKnowledgeOrder = mainVm::saveKnowledgeOrder,
                onSaveWrongOrder = mainVm::saveWrongOrder,
                onDeleteKnowledge = mainVm::deleteKnowledge,
                onDeleteWrongQuestions = mainVm::deleteWrongQuestions
            )
        }

        composable("analysis") {
            AnalysisScreen(
                uploadVm = uploadVm,
                onBack = {
                    uploadVm.discard()
                    navController.popBackStack()
                },
                onOpenSettings = { navController.navigate("settings") },
                onSaved = { recordId ->
                    navController.navigate("record/$recordId") {
                        popUpTo("main")
                    }
                    uploadVm.reset()
                }
            )
        }

        composable(
            route = "record/{recordId}",
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { entry ->
            val recordId = entry.arguments?.getLong("recordId") ?: 0L
            RecordDetailScreen(
                recordId = recordId,
                records = records,
                onBack = { navController.popBackStack() },
                onStudy = { subject, point ->
                    mainVm.openLearn(subject, point)
                    navController.popBackStack("main", false)
                },
                onPractice = { subject, point ->
                    practiceVm.preset(subject, point)
                    mainVm.setTab(AppTab.PRACTICE)
                    navController.popBackStack("main", false)
                },
                onDelete = { record ->
                    mainVm.deleteRecord(record)
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = mainVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
