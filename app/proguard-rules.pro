-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class app.niixfliix.**.model.** { *; }

-keep class org.libtorrent4j.swig.** { *; }

-assumenosideeffects class android.util.Log {
	public static int v(...);
	public static int d(...);
}

-keepclasseswithmembernames class androidx.media3.decoder.ffmpeg.** {
	native <methods>;
}
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
	private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}
