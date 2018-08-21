

# CheckableChipView

[![Travis (.org)](https://img.shields.io/travis/markushi/checkable-chip-view.svg?style=for-the-badge)](https://travis-ci.org/okdroid/checkable-chip-view) [![Bintray](https://img.shields.io/bintray/v/markushi/maven/checkablechipview.svg?style=for-the-badge)](https://bintray.com/markushi/maven/checkablechipview)

A checkable widget for Android. Based on the [EventFilterView from the Google I/O 2018 app](https://github.com/google/iosched/blob/2696fc7e06826ba2db72de243f0d63f83f4a29b5/mobile/src/main/java/com/google/samples/apps/iosched/ui/schedule/filters/EventFilterView.kt).

![](demo.gif)

## Setup
Make sure you have the jcenter repo in your project level `build.gradle`  
```gradle
allprojects {
    repositories {
	    jcenter()
    }
}
```

Add the dependency to your lib/app `build.gradle`  
```gradle
dependencies {
    implementation 'com.github.okdroid:checkablechipview:1.0.0'
}
```

## Usage
Include the widget into your xml layout like this
```xml
<com.github.okdroid.checkablechipview.CheckableChipView
        android:id="@+id/chip"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Pie Cake" />
```

The following custom attributes are supported

| Attribute                 | Description                                                  |
| ------------------------- | ------------------------------------------------------------ |
| `ccv_outlineColor`        | Color of the outline                                         |
| `ccv_outlineCornerRadius` | Corner radius of the outline, in dp. If not set defaults to a pill shape. |
| `ccv_outlineWidth`        | The stroke width of the outline, in dp                       |
| `ccv_checkedTextColor`    | the text color when the widget is checked                    |

The state of the widget can be observed like this
```kotlin
chip.onCheckedChangeListener = { view: CheckableChipView, isChecked: Boolean ->
    // do your logic here
}
```

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.