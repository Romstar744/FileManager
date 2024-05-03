package com.starostin.filemanager

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileListAdapter(
    private val context: Context,
    private val onItemClick: (FileItem) -> Unit,
    private val onDeleteClick: () -> Unit,
    private val onRenameClick: (FileItem, String) -> Unit
) : ListAdapter<FileItem, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    private var isSelectionModeEnabled = false
    private val selectedItems = mutableSetOf<FileItem>()
    private lateinit var btnCancelSelection: Button

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileItem = getItem(position)
        holder.bind(fileItem)

        holder.itemView.setOnLongClickListener {
            toggleSelectionModeAndSelect(fileItem)
            true
        }

        holder.itemView.setOnClickListener {
            if (isSelectionModeEnabled) {
                toggleSelection(fileItem)
                holder.checkBox.isChecked = selectedItems.contains(fileItem)
            } else {
                onItemClick(fileItem)
            }
        }

        holder.checkBox.visibility = if (isSelectionModeEnabled) View.VISIBLE else View.GONE
        holder.itemView.isActivated = selectedItems.contains(fileItem)
        holder.checkBox.isChecked = selectedItems.contains(fileItem)
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)
        val fileDetailsTextView: TextView = itemView.findViewById(R.id.fileDetailsTextView)
        val fileIconImageView: ImageView = itemView.findViewById(R.id.fileIconImageView)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkboxx)

        fun bind(fileItem: FileItem) {
            fileNameTextView.text = fileItem.name

            if (fileItem.isDirectory) {
                fileIconImageView.setImageResource(R.drawable.folder)
            } else {
                val fileExtension = getFileExtension(fileItem.name)
                val iconResource = getIconForFileExtension(fileExtension)
                fileIconImageView.setImageResource(iconResource)
            }

            CoroutineScope(Dispatchers.Main).launch {
                val file = withContext(Dispatchers.IO) {
                    File(fileItem.path)
                }

                val fileDetails = withContext(Dispatchers.IO) {
                    if (file.isDirectory) {
                        val folderSize = calculateFolderSize(file)
                        val numItems = countFilesInFolder(file)
                        "$folderSize | Items: $numItems"
                    } else {
                        val fileSize = getFileSize(file)
                        val lastModified = getLastModifiedDate(file)
                        "$fileSize | $lastModified"
                    }
                }
                fileDetailsTextView.text = fileDetails
            }
        }

        private fun getFileExtension(fileName: String): String {
            val dotIndex = fileName.lastIndexOf('.')
            return if (dotIndex != -1) {
                fileName.substring(dotIndex + 1)
            } else {
                ""
            }
        }

        private fun getIconForFileExtension(extension: String): Int {
            return when (extension.toLowerCase(Locale.getDefault())) {
                "jpg", "jpeg", "png", "gif" -> R.drawable.image
                "mp3", "wav", "flac" -> R.drawable.mp3
                "mp4", "avi", "mkv" -> R.drawable.mp4
                "txt", "log" -> R.drawable.text
                "pdf" -> R.drawable.pdf
                else -> R.drawable.text
            }
        }
    }

    private fun getFolderSize(folder: File): Long {
        var size: Long = 0
        val files = folder.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) {
                    getFolderSize(file) // Recursive call for directories
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    private fun formatSize(size: Long): String {
        val kilobyte = 1024
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024

        return when {
            size < kilobyte -> "$size B"
            size < megabyte -> String.format("%.2f KB", size.toDouble() / kilobyte)
            size < gigabyte -> String.format("%.2f MB", size.toDouble() / megabyte)
            else -> String.format("%.2f GB", size.toDouble() / gigabyte)
        }
    }

    private  fun calculateFolderSize(folder: File): String {
        var size: Long = 0
        val files = folder.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) {
                    getFolderSize(file)
                } else {
                    file.length()
                }
            }
        }
        return formatSize(size)
    }

    private  fun countFilesInFolder(folder: File): Int {
        val files = folder.listFiles()
        return files?.count { it.isFile } ?: 0
    }


    private fun toggleSelectionMode() {
        isSelectionModeEnabled = !isSelectionModeEnabled
        notifyDataSetChanged()
    }

    private fun toggleSelectionModeAndSelect(fileItem: FileItem) {
        isSelectionModeEnabled = !isSelectionModeEnabled
        selectedItems.clear()
        selectedItems.add(fileItem)
        notifyDataSetChanged()

    }

    fun enableSelectionMode(cancelButton: Button) {
        isSelectionModeEnabled = true
        btnCancelSelection = cancelButton
        btnCancelSelection.visibility = View.VISIBLE
        notifyDataSetChanged()
    }

    fun disableSelectionMode() {
        isSelectionModeEnabled = false
        selectedItems.clear()
        btnCancelSelection.visibility = View.GONE
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<FileItem> {
        return selectedItems.toList()
    }

    fun showDetailsForSelectedItem() {
        if (selectedItems.isEmpty()) {
            showToast("No items selected")
            return
        }

        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setTitle("Details")

        val detailsBuilder = StringBuilder()

        selectedItems.forEach { selectedItem ->
            val file = File(selectedItem.path)

            val itemName = selectedItem.name
            val itemPath = selectedItem.path
            val isDirectory = file.isDirectory
            val fileSize = if (isDirectory) {
                getFolderSize(file)
            } else {
                file.length()
            }
            val formattedFileSize = formatSize(fileSize)
            val lastModified = getLastModifiedDate(file)

            detailsBuilder.append("Name: $itemName\n")
            detailsBuilder.append("Path: $itemPath\n")
            detailsBuilder.append("Is Directory: $isDirectory\n")
            detailsBuilder.append("File Size: $formattedFileSize\n")
            detailsBuilder.append("Last Modified: $lastModified\n\n")
        }

        dialogBuilder.setMessage(detailsBuilder.toString())
        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = dialogBuilder.create()
        dialog.show()
    }


    private fun getFileSize(file: File): String {
        val fileSizeInBytes = file.length().toDouble()
        val kiloByte = 1024.0
        val megaByte = kiloByte * 1024
        val gigaByte = megaByte * 1024

        return when {
            fileSizeInBytes < kiloByte -> String.format("%.2f Bytes", fileSizeInBytes)
            fileSizeInBytes < megaByte -> String.format("%.2f KB", fileSizeInBytes / kiloByte)
            fileSizeInBytes < gigaByte -> String.format("%.2f MB", fileSizeInBytes / megaByte)
            else -> String.format("%.2f GB", fileSizeInBytes / gigaByte)
        }
    }


    private fun getLastModifiedDate(file: File): String {
        val lastModified = file.lastModified()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(lastModified))
    }

    fun deleteSelectedItems() {
        if (selectedItems.isEmpty()) {
            showToastMessage("No items selected for deletion")
            return
        }

        val builder = AlertDialog.Builder(context)
        builder.apply {
            setTitle("Confirm Deletion")
            setMessage("Are you sure you want to delete the selected items?")
            setPositiveButton("Delete") { _, _ ->
                deleteSelectedItemsConfirmed()
            }
            setNegativeButton("Cancel") { _, _ ->
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun deleteSelectedItemsConfirmed() {
        val selectedFiles = selectedItems.map { File(it.path) }
        var deleteSuccess = true
        selectedFiles.forEach { file ->
            if (file.exists()) {
                val success = file.delete()
                if (!success) {
                    deleteSuccess = false
                    showToastMessage("Failed to delete ${file.name}")
                }
            }
        }

        val newList = currentList.toMutableList()
        selectedItems.forEach { selectedItem ->
            newList.removeAll { it.path == selectedItem.path }
        }

        submitList(newList)

        if (deleteSuccess) {
            showToastMessage("Selected items deleted successfully")
        }
        selectedItems.clear()
    }


    private fun showToastMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleSelection(fileItem: FileItem) {
        if (isSelectionModeEnabled) {
            if (selectedItems.contains(fileItem)) {
                selectedItems.remove(fileItem)
            } else {
                selectedItems.add(fileItem)
            }
            notifyItemChanged(currentList.indexOf(fileItem))
        } else {
            selectedItems.clear()
            selectedItems.add(fileItem)
            notifyDataSetChanged()
        }
    }


    fun moveSelectedItems(destinationDirectory: File) {
        val selectedFiles = selectedItems.map { File(it.path) }
        val successList = mutableListOf<File>()
        val failureList = mutableListOf<File>()

        selectedFiles.forEach { file ->
            val newFile = File(destinationDirectory, file.name)
            try {
                if (file.exists() && file.isFile && !newFile.exists()) {
                    val success = file.renameTo(newFile)
                    if (success) {
                        successList.add(file)
                    } else {
                        failureList.add(file)
                    }
                } else {
                    failureList.add(file)
                }
            } catch (e: Exception) {
                Log.e("FileMove", "Failed to move: ${e.message}", e)
                failureList.add(file)
            }
        }

        if (successList.isNotEmpty()) {
            showToastMessage("Selected items moved successfully")
        }

        if (failureList.isNotEmpty()) {
            showToastMessage("Failed to move some items")
        }

        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun pasteCopiedItems(destinationDirectory: File) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val copiedFileUri = clipData.getItemAt(0).uri
            val copiedFile = File(getRealPathFromURI(copiedFileUri))
            val newFile = File(destinationDirectory, copiedFile.name)
            try {
                if (copiedFile.exists() && copiedFile.isFile && !newFile.exists()) {
                    copiedFile.copyTo(newFile)
                    showToastMessage("File copied successfully")
                } else {
                    showToastMessage("Failed to paste: Either the source file doesn't exist or the destination file already exists")
                }
            } catch (e: Exception) {
                showToastMessage("Failed to paste: ${e.message}")
            }
        } else {
            showToastMessage("Clipboard is empty or contains unsupported data")
        }
    }

    private fun getRealPathFromURI(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        var filePath = ""
        if (cursor != null) {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            filePath = cursor.getString(idx)
            cursor.close()
        }
        return filePath
    }

    fun selectAllItems() {
        selectedItems.clear()
        selectedItems.addAll(currentList)
        notifyDataSetChanged()
    }


    fun isSelectionModeActive(): Boolean {
        return isSelectionModeEnabled
    }

    fun cancelSelectionMode() {
        isSelectionModeEnabled = false
        selectedItems.clear()
        notifyDataSetChanged()
    }


    fun renameSelectedItems() {
        if (selectedItems.isEmpty()) {
            showToast("No items selected for renaming")
            return
        }
        if (selectedItems.size > 1) {
            showToast("Cannot rename multiple items at once. Please select only one item.")
            return
        }

        val selectedItem = selectedItems.first()
        val editText = EditText(context)
        editText.setText(selectedItem.name)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Rename Item")
            .setView(editText)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = editText.text.toString()
                if (newName.isEmpty()) {
                    showToast("Please enter a new name")
                } else if (newName == selectedItem.name) {
                    showToast("The new name is the same as the current name")
                } else {
                    val oldFile = File(selectedItem.path)
                    val newFile = File(oldFile.parent, newName)
                    try {
                        if (oldFile.exists() && oldFile.isFile && !newFile.exists()) {
                            val success = oldFile.renameTo(newFile)
                            if (success) {
                                val message = "File renamed successfully: ${oldFile.path} to ${newFile.path}"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                val newList = currentList.toMutableList()
                                val index = currentList.indexOf(selectedItem)
                                if (index != -1) {
                                    val updatedItem = selectedItem.copy(name = newName, path = newFile.absolutePath)
                                    newList[index] = updatedItem
                                    submitList(newList)
                                }
                            } else {
                                val errorMessage = "Failed to rename: Unknown error"
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val errorMessage = when {
                                !oldFile.exists() -> "Failed to rename: File does not exist"
                                !oldFile.isFile -> "Failed to rename: Not a file"
                                newFile.exists() -> "Failed to rename: File with the new name already exists"
                                else -> "Failed to rename: Unknown error"
                            }
                            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("FileRename", "Failed to rename: ${e.message}", e)
                        Toast.makeText(context, "Failed to rename: Unknown error", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}

class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
    override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
        return oldItem == newItem
    }
}