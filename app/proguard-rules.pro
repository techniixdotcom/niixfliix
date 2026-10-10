# Gson models
-keep class app.niixfliix.**.model.** { *; }

# libtorrent4j JNI
-keep class org.libtorrent4j.swig.** { *; }

# no debug/verbose logs in release
-assumenosideeffects class android.util.Log {
	public static int v(...);
	public static int d(...);
}

# FFmpeg audio decoder JNI (same as upstream's rules, in case the AAR ships without them)
-keepclasseswithmembernames class androidx.media3.decoder.ffmpeg.** {
	native <methods>;
}
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
	private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}
