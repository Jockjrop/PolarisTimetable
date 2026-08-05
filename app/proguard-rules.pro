# PDFBox uses reflection to create encryption handlers. Keep their constructors
# and document-interchange implementations when R8 shrinks release builds.
-keep,allowobfuscation class * extends com.tom_roush.pdfbox.pdmodel.encryption.SecurityHandler {
    public <init>(...);
}
-keep,allowobfuscation class com.tom_roush.pdfbox.pdmodel.documentinterchange.** { *; }

# Optional JPEG 2000 support is loaded only when the Gemalto decoder is present.
-dontwarn com.gemalto.jp2.JP2Decoder
