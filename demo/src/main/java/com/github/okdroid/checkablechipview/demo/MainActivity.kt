package com.github.okdroid.checkablechipview.demo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import com.github.okdroid.checkablechipview.CheckableChipView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chip = findViewById<CheckableChipView>(R.id.chip)
        chip.setOnClickListener {
            chip.setCheckedAnimated(!chip.isChecked) {
                Toast.makeText(this@MainActivity, "Checked: ${chip.isChecked}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}