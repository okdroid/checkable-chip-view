/*
 * Copyright 2018 Google LLC
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

package com.github.okdroid.checkablechipview

import android.text.StaticLayout

/**
 * Calculated the widest line in a [StaticLayout].
 */
internal fun StaticLayout.textWidth(): Int {
    var width = 0f
    for (i in 0 until lineCount) {
        width = width.coerceAtLeast(getLineWidth(i))
    }
    return width.toInt()
}

/**
 * Linearly interpolate between two values.
 */
internal fun lerp(a: Float, b: Float, t: Float): Float {
    return a + (b - a) * t
}