package com.example.real_time_object

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import com.example.real_time_object.databinding.ActivityThirdBinding
import com.example.real_time_object.ml.AutoModel1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class ThirdActivity : AppCompatActivity() {

    val paint = Paint()
    private lateinit var binding : ActivityThirdBinding
    lateinit var bitmap : Bitmap
    lateinit var model : AutoModel1
    lateinit var labels : List<String>
    val imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()

    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityThirdBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        labels = FileUtil.loadLabels(this, "labels.txt")
        model = AutoModel1.newInstance(this)
        val intent = Intent()
        intent.setType("image/*")
        intent.setAction(Intent.ACTION_GET_CONTENT)
        binding.btn.setOnClickListener{
            startActivityForResult(intent, 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Releases model resources if no longer used.
        model.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 101){
            var uri = data?.data// inside uri is the location where the location is located
            //we need to access that image and store that image inside this bitmap
            bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            getPredictions()
        }
    }
    fun getPredictions(){
        // Creates inputs for reference.
        var image = TensorImage.fromBitmap(bitmap)
        image = imageProcessor.process(image)

        // Runs model inference and gets result.
        val outputs = model.process(image)
        //location of the object inside the image
        val locations = outputs.locationsAsTensorBuffer.floatArray
        ////idea of all the objects it has detected
        val classes = outputs.classesAsTensorBuffer.floatArray
        //confidence of every object it has detected
        val scores = outputs.scoresAsTensorBuffer.floatArray
        // no. of objects it is able to detect
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

        //Instead of closing the model here we will override a onDestroy method to close the model whenever our app is closed
        //converting the bitmap to mutable bitmap
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val h = mutable.height
        val w = mutable.width
        paint.textSize = h/15f
        paint.strokeWidth = h/85f
        var x=0
        x *= 4
        scores.forEachIndexed { index, fl ->
            paint.setColor(colors.get(index))
            x = index
            if(fl>0.5){
                paint.style = Paint.Style.STROKE
                canvas.drawRect(RectF(locations.get(x+1)*w, locations.get(x)*h, locations.get(x+3)*w, locations.get(x+2)*h), paint)
                paint.style = Paint.Style.FILL
                canvas.drawText(labels.get(classes.get(index).toInt())+ " " +fl.toString(), locations.get(x+1)*w, locations.get(x)*h, paint)
            }
        }
        binding.imageV.setImageBitmap(mutable)
    }
}