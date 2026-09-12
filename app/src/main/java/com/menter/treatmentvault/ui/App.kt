package com.menter.treatmentvault.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.*
import com.menter.treatmentvault.ui.screens.*

@Composable fun TreatmentVaultApp(){ MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF176B5B),secondary=Color(0xFF4F7F75))){ val nav=rememberNavController(); NavHost(nav,"home"){
    composable("home"){HomeScreen{nav.navigate(it)}}
    composable("supplies"){SupplyScreen{nav.popBackStack()}}
    composable("exams"){ExamScreen{nav.popBackStack()}}
    composable("treatment"){TreatmentScreen{nav.popBackStack()}}
    composable("symptoms"){SymptomScreen{nav.popBackStack()}}
    composable("questions"){QuestionScreen{nav.popBackStack()}}
    composable("expenses"){ExpenseScreen{nav.popBackStack()}}
    composable("timeline"){TimelineScreen{nav.popBackStack()}}
    composable("import"){ImportScreen{nav.popBackStack()}}
    composable("settings"){SettingsScreen{nav.popBackStack()}}
} } }
