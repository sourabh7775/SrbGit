# Add project specific ProGuard rules here.
-keepattributes *Annotation*, Signature, InnerClasses
-keep class * extends android.webkit.WebChromeClient { *; }
-keep class * extends android.webkit.WebViewClient { *; }
