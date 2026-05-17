package com.farkasandrasdev.familykanbanapp.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farkasandrasdev.familykanbanapp.model.TaskPriority

@Composable
fun PriorityChip(priority: String) {
    val (label, color) = when (TaskPriority.from(priority)) {
        TaskPriority.HIGH   -> "High"   to MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> "Medium" to MaterialTheme.colorScheme.tertiary
        TaskPriority.LOW    -> "Low"    to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
