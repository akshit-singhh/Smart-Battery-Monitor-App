package com.example.batterymonitor

import com.example.batterymonitor.R
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType

class PortraitCaptureActivity : CaptureActivity() {

    override fun initializeContent(): DecoratedBarcodeView {
        // Inflate the default ZXing scanner view
        setContentView(R.layout.zxing_capture) // From ZXing embedded library
        val barcodeView = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)

        // Supported formats → faster detection
        val formats = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX)

        // Decoding hints → better for small OLED QR
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.PURE_BARCODE to false,
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.ALLOWED_LENGTHS to intArrayOf(1, 200),
            DecodeHintType.ALLOWED_EAN_EXTENSIONS to intArrayOf(2, 5)
        )
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats, hints, null, 0)

        barcodeView.barcodeView.decoderFactory =
            DefaultDecoderFactory(formats, hints, null, 0)

        // === Camera focus tuning ===
        val settings = CameraSettings().apply {
            isAutoFocusEnabled = true
            isContinuousFocusEnabled = true
            focusMode = CameraSettings.FocusMode.MACRO  // 👈 close-range sharpness
            requestedCameraId = 0 // Back camera
            isMeteringEnabled = true
        }

        barcodeView.barcodeView.cameraSettings = settings

        // Continuous decode so user can adjust until successful
        barcodeView.decodeContinuous {
            // Let IntentIntegrator handle result
        }

        return barcodeView
    }
}
