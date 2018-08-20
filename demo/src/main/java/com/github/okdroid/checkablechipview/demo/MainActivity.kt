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
            chip.onCheckedChangeListener = { view: CheckableChipView, isChecked: Boolean ->
                Toast
                    .makeText(this@MainActivity, "${view.text} checked: $isChecked", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}