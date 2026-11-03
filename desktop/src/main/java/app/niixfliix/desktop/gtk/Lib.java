package app.niixfliix.desktop.gtk;

import java.lang.foreign.SymbolLookup;

import app.niixfliix.desktop.platform.Native;

final class Lib {

	static final SymbolLookup GLIB = Native.library("libglib-2.0.so.0");
	static final SymbolLookup GOBJECT = Native.library("libgobject-2.0.so.0");
	static final SymbolLookup GIO = Native.library("libgio-2.0.so.0");
	static final SymbolLookup GTK = Native.library("libgtk-4.so.1");
	static final SymbolLookup ADW = Native.library("libadwaita-1.so.0");

	private Lib() {
	}
}
