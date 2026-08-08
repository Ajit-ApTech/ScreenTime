package com.screentime.admin.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.screentime.admin.R
import com.screentime.admin.models.CallRecord

object AdminDialogs {

    fun showEditTextDialog(
        context: Context,
        title: String,
        hint: String,
        initialText: String,
        onSave: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_edit_text, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvSheetTitle).text = title
        val inputLayout = view.findViewById<TextInputLayout>(R.id.inputLayout)
        inputLayout.hint = hint

        val etInput = view.findViewById<TextInputEditText>(R.id.etInput)
        etInput.setText(initialText)
        etInput.requestFocus()

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val input = etInput.text.toString().trim()
            if (input.isNotEmpty()) {
                onSave(input)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    fun showConfirmDeleteDialog(
        context: Context,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_confirm_delete, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvSheetTitle).text = title
        view.findViewById<TextView>(R.id.tvSheetMessage).text = message

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnConfirmDelete).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showEditUsageDialog(
        context: Context,
        currentSeconds: Long,
        onSave: (Long) -> Unit
    ) {
        showEditTextDialog(
            context = context,
            title = "Edit Usage Duration",
            hint = "Total Usage Time (in seconds)",
            initialText = currentSeconds.toString()
        ) { text ->
            val seconds = text.toLongOrNull() ?: currentSeconds
            onSave(seconds)
        }
    }

    fun showEditCallDialog(
        context: Context,
        callRecord: CallRecord,
        onSave: (contactName: String, durationSeconds: Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_edit_call, null)
        dialog.setContentView(view)

        val etName = view.findViewById<TextInputEditText>(R.id.etContactName)
        val etDuration = view.findViewById<TextInputEditText>(R.id.etDuration)

        etName.setText(callRecord.contactName)
        etDuration.setText(callRecord.durationSeconds.toString())

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim().ifEmpty { callRecord.contactName }
            val dur = etDuration.text.toString().toIntOrNull() ?: callRecord.durationSeconds
            onSave(name, dur)
            dialog.dismiss()
        }

        dialog.show()
    }
}
