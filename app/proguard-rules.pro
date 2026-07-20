# Application-specific rules are intentionally added as the capture and OCR stack is introduced.

# ML Kit Text Recognition loads its on-device model/runtime via reflection and dynamic feature
# delivery; keep its public API surface so R8 doesn't strip classes only referenced at runtime.
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-dontwarn com.google.mlkit.**
