package com.github.okdroid.checkablechipview.demo

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.github.okdroid.checkablechipview.CheckableChipView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chips = listOf<CheckableChipView>(
            findViewById(R.id.chip0),
            findViewById(R.id.chip1),
            findViewById(R.id.chip2)
        )

        chips.forEach { chip ->
            chip.setOnClickListener {
                chip.setCheckedAnimated(!chip.isChecked) {
                    Toast.makeText(this@MainActivity, "${chip.text}: ${chip.isChecked}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}