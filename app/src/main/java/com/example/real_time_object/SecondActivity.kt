package com.example.real_time_object

import android.Manifest
import android.content.Context
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.real_time_object.databinding.ActivitySecondBinding
import com.example.real_time_object.ml.AutoModel1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class SecondActivity : AppCompatActivity() {
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    lateinit var labels: List<String>
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var textureView : TextureView
    lateinit var cameraManager: CameraManager
    lateinit var model : AutoModel1
    private lateinit var binding : ActivitySecondBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getPermission()

        labels = FileUtil.loadLabels(this, "labels.txt")

        //The bitmap is of size 300x300, so in order to show it on your screen you would
        //have to resize the image before pass it to val outputs
        //this line below is for line 81
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = AutoModel1.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        binding.textureView.surfaceTextureListener= object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                bitmap = binding.textureView.bitmap!!



                // Creates inputs for reference.
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                // Runs model inference and gets result.
                val outputs = model.process(image)
                //we would have to convert them to float array so that we can access them by index
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                //we will not close the model here, we will close it once the app is destroyed

                var mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                var canvas = Canvas(mutableBitmap)

                val h = mutableBitmap.height
                val w = mutableBitmap.width
                paint.textSize = h/15f
                paint.strokeWidth = h/85f
                var x = 0
                //In this we are iterating through scores to find the prediction percentage
                //The reason we converted the above vals to floatArray is solely for this purpose
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if(fl > 0.5){
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations.get(x+1)*w, locations.get(x)*h, locations.get(x+3)*w, locations.get(x+2)*h), paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels.get(classes.get(index).toInt())+" "+fl.toString(), locations.get(x+1)*w, locations.get(x)*h, paint)
                    }
                }
                //We are showing the image to the user using this imageView
                binding.imageView.setImageBitmap(mutableBitmap)

            }

        }

        //opening the camera and 'as' is used to typecast the getSystemService to CameraManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    override fun onDestroy() {
        super.onDestroy()
        // Releases model resources if no longer used.
        model.close()
    }
    fun openCamera(){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            getPermission()
            return
        }
        cameraManager.openCamera(cameraManager.cameraIdList[0], object:CameraDevice.StateCallback(){
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera

                var surfaceTexture = binding.textureView.surfaceTexture
                var surface = Surface(surfaceTexture)
                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW /*because we want to create a preview request*/)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequest.build()/*because it returns a buider*/, null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }

                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {


            }

        }, handler)
    }

    fun getPermission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            getPermission()
        }
    }
}