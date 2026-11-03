package app.niixfliix.ui;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.Excludes;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

import app.niixfliix.app.Core;

@GlideModule
@Excludes(OkHttpLibraryGlideModule.class)
public final class NiixGlideModule extends AppGlideModule {

	private static final long DISK_CACHE_BYTES = 100L << 20;
	private static final String DISK_CACHE_DIR = "pictures";

	@Override
	public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
		builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_DIR, DISK_CACHE_BYTES));
		builder.setLogLevel(Log.ERROR);
	}

	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(Core.get().http));
	}

	@Override
	public boolean isManifestParsingEnabled() {
		return false;
	}
}
