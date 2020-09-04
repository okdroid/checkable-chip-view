/*
 * Copyright 2018 markushi
 * Copyright 2018 rom4ek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.okdroid.checkablechipview.demo

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.github.okdroid.checkablechipview.CheckableChipView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chips = listOf<CheckableChipView>(chip0, chip1, chip2)

        chips.forEach { chip ->
            chip.setOnCheckedChangeListener { view, isChecked ->
                Toast
                    .makeText(this@MainActivity, "${view.text} checked: $isChecked", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}