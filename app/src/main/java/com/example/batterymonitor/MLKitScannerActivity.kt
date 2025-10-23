package com.example.batterymonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MLKitScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var barcodeOverlay: BarcodeOverlayView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var scanning = true

    private val scanner = BarcodeScanning.getClient()

    companion object {
        const val SCAN_RESULT = "SCAN_RESULT"
        const val TAG = "MLKitScanner"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mlkit_scanner)

        previewView = findViewById(R.id.previewView)
        barcodeOverlay = findViewById(R.id.barcodeOverlay)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(1280, 720))
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val analysisUseCase = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(scanner, imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, analysisUseCase
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            Log.d(TAG, "Image resolution: ${image.width} x ${image.height}")

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val boxes = mutableListOf<RectF>()
                    var found = false

                    for (barcode in barcodes) {
                        val box = barcode.boundingBox
                        if (box != null) {
                            val rect = translateRect(
                                box,
                                previewView,
                                image.height,
                                image.width,
                                rotationDegrees
                            )
                            boxes.add(rect)

                            if (!found && scanning && barcode.rawValue != null) {
                                found = true
                                scanning = false
                                val resultIntent = Intent()
                                resultIntent.putExtra(SCAN_RESULT, barcode.rawValue)
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            }
                        }
                    }

                    runOnUiThread {
                        updateOverlayWithRectFs(boxes)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scan failed", e)
                    runOnUiThread {
                        barcodeOverlay.updateBoundingBoxes(emptyList())
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun updateOverlayWithRectFs(rectFs: List<RectF>) {
        val rects = rectFs.map { rectF ->
            Rect(
                rectF.left.toInt(),
                rectF.top.toInt(),
                rectF.right.toInt(),
                rectF.bottom.toInt()
            )
        }
        barcodeOverlay.updateBoundingBoxes(rects)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close() // Optional: release MLKit resources
    }

    private fun translateRect(
        rect: Rect,
        view: PreviewView,
        imageHeight: Int,
        imageWidth: Int,
        rotation: Int
    ): RectF {
        val previewWidth = view.width.toFloat()
        val previewHeight = view.height.toFloat()

        val imageAspectRatio = if (rotation == 0 || rotation == 180) {
            imageWidth.toFloat() / imageHeight
        } else {
            imageHeight.toFloat() / imageWidth
        }

        val viewAspectRatio = previewWidth / previewHeight

        val scaleX: Float
        val scaleY: Float
        var offsetX = 0f
        var offsetY = 0f

        if (viewAspectRatio > imageAspectRatio) {
            // previewView is wider, letterboxing on X axis
            scaleY = previewHeight / if (rotation == 0 || rotation == 180) imageHeight.toFloat() else imageWidth.toFloat()
            scaleX = scaleY
            val scaledWidth = imageAspectRatio * previewHeight
            offsetX = (previewWidth - scaledWidth) / 2f
        } else {
            // previewView is taller, letterboxing on Y axis
            scaleX = previewWidth / if (rotation == 0 || rotation == 180) imageWidth.toFloat() else imageHeight.toFloat()
            scaleY = scaleX
            val scaledHeight = previewWidth / imageAspectRatio
            offsetY = (previewHeight - scaledHeight) / 2f
        }

        return RectF(
            rect.left * scaleX + offsetX,
            rect.top * scaleY + offsetY,
            rect.right * scaleX + offsetX,
            rect.bottom * scaleY + offsetY
        )
    }
}
