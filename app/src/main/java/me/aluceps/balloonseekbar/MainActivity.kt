package me.aluceps.balloonseekbar

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import me.aluceps.balloonseekbar.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.seekBar.setOnChangeListenr(object : OnChangeListener {
            override fun progress(percentage: Float) {
                Log.d("###", "percentage: $percentage")
            }

            override fun progress(value: Int) {
                Log.d("###", "value: $value")
            }
        })
    }
}
