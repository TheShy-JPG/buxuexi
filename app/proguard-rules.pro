# 保留 kotlinx.serialization 生成的序列化器（发布构建开启 R8 压缩时需要）
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.zhuxue.zhuxuebao.**$$serializer { *; }
-keepclassmembers class com.zhuxue.zhuxuebao.** {
    *** Companion;
}
-keepclasseswithmembers class com.zhuxue.zhuxuebao.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# PDFBox 的 JPEG2000 解码器是可选依赖，本项目未引入
-dontwarn com.gemalto.jp2.JP2Decoder
