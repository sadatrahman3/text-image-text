package com.imagetextapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.imagetextapp.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var encodedString: String? = null
    private var decodedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            encodeImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickImage.setOnClickListener { pickImage() }
        binding.btnCopyText.setOnClickListener { copyText() }
        binding.btnShareText.setOnClickListener { shareText() }
        binding.btnDecodeImage.setOnClickListener { decodeImage() }
        binding.btnPasteText.setOnClickListener { pasteText() }
        binding.btnSaveImage.setOnClickListener { saveImage() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        pickImageLauncher.launch(intent)
    }

    private fun encodeImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                return
            }

            binding.imagePreview.setImageBitmap(originalBitmap)
            binding.imagePreview.visibility = android.view.View.VISIBLE

            val stream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imageBytes = stream.toByteArray()
            stream.close()

            encodedString = Base64.encodeToString(imageBytes, Base64.DEFAULT)
            binding.encodedText.setText(encodedString)

            val sizeKb = imageBytes.size / 1024
            val textLen = encodedString!!.length
            val textSizeKb = textLen / 1024
            binding.txtSizeInfo.text =
                "Image: ${sizeKb}KB → Text: ${textSizeKb}KB ($textLen chars)"
            binding.txtSizeInfo.visibility = android.view.View.VISIBLE
            binding.textInputLayout.visibility = android.view.View.VISIBLE
            binding.encodeActions.visibility = android.view.View.VISIBLE

            Toast.makeText(this, "Image encoded successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyText() {
        val text = encodedString
        if (text == null) {
            Toast.makeText(this, "No encoded text to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("encoded_image", text))
        Toast.makeText(this, "Copied to clipboard (${text.length} chars)", Toast.LENGTH_SHORT).show()
    }

    private fun shareText() {
        val text = encodedString
        if (text == null) {
            Toast.makeText(this, "No encoded text to share", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share encoded image"))
    }

    private fun decodeImage() {
        val text = binding.inputDecodeText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please paste encoded text first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val imageBytes = Base64.decode(text, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            if (bitmap == null) {
                Toast.makeText(this, "Invalid image data. Check the text.", Toast.LENGTH_SHORT).show()
                return
            }

            decodedBitmap = bitmap
            binding.decodedImage.setImageBitmap(bitmap)
            binding.decodedImage.visibility = android.view.View.VISIBLE
            binding.btnSaveImage.visibility = android.view.View.VISIBLE

            val sizeKb = imageBytes.size / 1024
            Toast.makeText(this, "Image decoded (${sizeKb}KB)", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Invalid base64 text", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteText() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (text != null) {
                binding.inputDecodeText.setText(text)
                Toast.makeText(this, "Text pasted from clipboard", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    }

    private fun saveImage() {
        val bitmap = decodedBitmap ?: return

        val filename = "Img2Txt_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Img2Txt"
                )
            }
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
            if (uri != null) {
                val outputStream = contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream?.close()
                Toast.makeText(this, "Saved to Pictures/Img2Txt", Toast.LENGTH_SHORT).show()
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val file = File(dir, filename)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            Toast.makeText(this, "Saved to $filename", Toast.LENGTH_SHORT).show()
        }
    }
}
