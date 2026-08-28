package com.example.specclash.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Prompts the user for a manual street-price override (in USD) for one
 * device. Confirming with a blank field clears the override, reverting the
 * value-for-money calculation back to the upstream-scraped price.
 */
@Composable
fun PriceOverrideDialog(
    deviceName: String?,
    currentValue: Double?,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        mutableStateOf(
            currentValue?.let { v ->
                if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            } ?: "",
        )
    }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deviceName != null) "Override price — $deviceName" else "Override price") },
        text = {
            Column {
                Text(
                    text = "Enter a custom street price in USD. Leave blank to clear the override and use the listed price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        isError = false
                    },
                    label = { Text("Price (USD)") },
                    leadingIcon = { Text("$") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (isError) {
                        { Text("Enter a valid positive number, or leave blank to clear") }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    onConfirm(null)
                    return@TextButton
                }
                val parsed = trimmed.toDoubleOrNull()
                if (parsed == null || parsed <= 0.0) {
                    isError = true
                } else {
                    onConfirm(parsed)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
