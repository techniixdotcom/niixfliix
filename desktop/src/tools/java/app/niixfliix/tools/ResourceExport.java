package app.niixfliix.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public final class ResourceExport {

	private static final String ANDROID = "http://schemas.android.com/apk/res/android";
	private static final String OUT_PACKAGE = "app/niixfliix/desktop/";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private ResourceExport() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			throw new IllegalArgumentException("ResourceExport <android res> <desktop strings> <desktop icons> <output>");
		}
		File res = new File(args[0]);
		File desktopStrings = new File(args[1]);
		File desktopIcons = new File(args[2]);
		File out = new File(args[3], OUT_PACKAGE);
		exportStrings(res, desktopStrings, new File(out, "strings"));
		exportIcons(new File[] {new File(res, "drawable"), desktopIcons}, new File(out, "icons"));
		exportAppIcon(res, new File(out, "app-icon.svg"));
	}

	private static void exportStrings(File res, File desktop, File outDir) throws Exception {
		Map<String, JsonObject> byLanguage = new TreeMap<>();
		for (File base : new File[] {res, desktop}) {
			File[] dirs = base.listFiles(f -> f.isDirectory() && f.getName().matches("values(-[a-z]{2}(-r[A-Z]{2})?)?"));
			if (dirs == null) {
				throw new IOException("Not a resource directory: " + base);
			}
			Arrays.sort(dirs);
			for (File dir : dirs) {
				File[] files = dir.listFiles(f -> f.getName().startsWith("strings") && f.getName().endsWith(".xml"));
				if (files == null) {
					continue;
				}
				Arrays.sort(files);
				JsonObject target = byLanguage.computeIfAbsent(languageOf(dir.getName()), k -> emptyTable());
				for (File file : files) {
					readStrings(file, target);
				}
			}
		}
		JsonObject english = byLanguage.get("en");
		if (english == null) {
			throw new IOException("No default strings");
		}
		Files.createDirectories(outDir.toPath());
		for (Map.Entry<String, JsonObject> e : byLanguage.entrySet()) {
			write(new File(outDir, e.getKey() + ".json"), GSON.toJson(e.getValue()));
		}
	}

	private static JsonObject emptyTable() {
		JsonObject table = new JsonObject();
		table.add("strings", new JsonObject());
		table.add("plurals", new JsonObject());
		table.add("arrays", new JsonObject());
		return table;
	}

	static String languageOf(String dirName) {
		if (dirName.equals("values")) {
			return "en";
		}
		String rest = dirName.substring("values-".length());
		return rest.replace("-r", "-");
	}

	private static void readStrings(File file, JsonObject target) throws Exception {
		Element root = parse(file).getDocumentElement();
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (!(children.item(i) instanceof Element)) {
				continue;
			}
			Element e = (Element) children.item(i);
			String name = e.getAttribute("name");
			switch (e.getTagName()) {
				case "string":
					put(target.getAsJsonObject("strings"), name, unescape(text(e)), file);
					break;
				case "plurals": {
					JsonObject forms = new JsonObject();
					for (Element item : elements(e, "item")) {
						forms.addProperty(item.getAttribute("quantity"), unescape(text(item)));
					}
					put(target.getAsJsonObject("plurals"), name, forms, file);
					break;
				}
				case "string-array": {
					JsonArray items = new JsonArray();
					for (Element item : elements(e, "item")) {
						items.add(unescape(text(item)));
					}
					put(target.getAsJsonObject("arrays"), name, items, file);
					break;
				}
				default:
					break;
			}
		}
	}

	private static void put(JsonObject table, String name, Object value, File file) {
		if (table.has(name)) {
			throw new IllegalStateException(name + " is defined twice (again in " + file + ")");
		}
		if (value instanceof String) {
			table.addProperty(name, (String) value);
		} else {
			table.add(name, (com.google.gson.JsonElement) value);
		}
	}

	private static String text(Element e) {
		StringBuilder out = new StringBuilder();
		NodeList nodes = e.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node n = nodes.item(i);
			if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
				out.append(n.getNodeValue());
			} else if (n instanceof Element) {
				out.append(text((Element) n));
			}
		}
		return out.toString();
	}

	static String unescape(String raw) {
		StringBuilder out = new StringBuilder(raw.length());
		boolean quoted = false;
		boolean space = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\\' && i + 1 < raw.length()) {
				char next = raw.charAt(++i);
				space = false;
				switch (next) {
					case 'n':
						out.append('\n');
						break;
					case 't':
						out.append('\t');
						break;
					case 'u':
						if (i + 4 < raw.length()) {
							out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
							i += 4;
						}
						break;
					default:
						out.append(next);
						break;
				}
				continue;
			}
			if (c == '"') {
				quoted = !quoted;
				continue;
			}
			if (!quoted && Character.isWhitespace(c)) {
				if (!space) {
					out.append(' ');
					space = true;
				}
				continue;
			}
			space = false;
			out.append(c);
		}
		String s = out.toString();
		int start = 0;
		int end = s.length();
		if (!raw.isEmpty() && Character.isWhitespace(raw.charAt(0)) && s.startsWith(" ")) {
			start = 1;
		}
		if (!raw.isEmpty() && Character.isWhitespace(raw.charAt(raw.length() - 1)) && s.endsWith(" ") && end > start) {
			end--;
		}
		return s.substring(start, end);
	}

	private static void exportIcons(File[] dirs, File outDir) throws Exception {
		List<String> names = new ArrayList<>();
		for (File dir : dirs) {
			File[] files = dir.listFiles(f -> f.getName().startsWith("ic_") && f.getName().endsWith(".xml")
					&& !f.getName().startsWith("ic_launcher"));
			if (files == null) {
				throw new IOException("No icons in " + dir);
			}
			Arrays.sort(files);
			for (File file : files) {
				String name = file.getName().substring(3, file.getName().length() - 4);
				if (names.contains(name)) {
					throw new IllegalStateException("Icon " + name + " is defined twice (again in " + file + ")");
				}
				names.add(name);
				write(new File(outDir, name + ".svg"), symbolic(file));
			}
		}
		write(new File(outDir, "index.txt"), String.join("\n", names) + "\n");
	}

	static String symbolic(File file) throws Exception {
		Element root = parse(file).getDocumentElement();
		if (!root.getTagName().equals("vector")) {
			throw new IOException(file + " is not a vector drawable");
		}
		String width = number(root.getAttributeNS(ANDROID, "viewportWidth"));
		String height = number(root.getAttributeNS(ANDROID, "viewportHeight"));
		StringBuilder svg = new StringBuilder();
		svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"")
				.append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");
		for (Element child : children(root)) {
			if (!child.getTagName().equals("path")) {
				throw new IOException(file + ": only plain paths are supported, found <" + child.getTagName() + ">");
			}
			String fill = color(child.getAttributeNS(ANDROID, "fillColor"), child.getAttributeNS(ANDROID, "fillAlpha"));
			double opacity = Integer.parseInt(fill.substring(7), 16) / 255.0;
			svg.append("\t<path fill=\"#000000\"");
			if (opacity < 1) {
				svg.append(" fill-opacity=\"").append(String.format(Locale.ROOT, "%.3f", opacity)).append('"');
			}
			if ("evenOdd".equals(child.getAttributeNS(ANDROID, "fillType"))) {
				svg.append(" fill-rule=\"evenodd\"");
			}
			svg.append(" d=\"").append(escape(child.getAttributeNS(ANDROID, "pathData"))).append("\"/>\n");
		}
		svg.append("</svg>\n");
		return svg.toString();
	}

	private static String number(String value) {
		double v = Double.parseDouble(value);
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
	}

	static String color(String value, String alpha) {
		String v = value.trim();
		int argb;
		if (v.isEmpty() || v.equals("@android:color/white")) {
			argb = 0xFFFFFFFF;
		} else if (v.equals("@android:color/black")) {
			argb = 0xFF000000;
		} else if (v.startsWith("#")) {
			String hex = v.substring(1);
			if (hex.length() == 3 || hex.length() == 4) {
				StringBuilder full = new StringBuilder();
				for (char c : hex.toCharArray()) {
					full.append(c).append(c);
				}
				hex = full.toString();
			}
			if (hex.length() == 6) {
				hex = "FF" + hex;
			}
			if (hex.length() != 8) {
				throw new IllegalArgumentException("Bad colour " + value);
			}
			argb = (int) Long.parseLong(hex, 16);
		} else {
			throw new IllegalArgumentException("Unsupported colour " + value);
		}
		int a = argb >>> 24;
		if (!alpha.isEmpty()) {
			a = Math.round(a * Float.parseFloat(alpha));
		}
		return String.format(Locale.ROOT, "#%06X%02X", argb & 0xFFFFFF, a);
	}

	private static void exportAppIcon(File res, File out) throws Exception {
		Element foreground = parse(new File(res, "drawable/ic_launcher_foreground.xml")).getDocumentElement();
		String background = backgroundColor(new File(res, "values/colors.xml"));
		StringBuilder svg = new StringBuilder();
		svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128\" height=\"128\" viewBox=\"18 18 72 72\">\n")
				.append("\t<rect x=\"18\" y=\"18\" width=\"72\" height=\"72\" rx=\"16\" fill=\"")
				.append(background).append("\"/>\n");
		for (Element path : children(foreground)) {
			if (!path.getTagName().equals("path")) {
				throw new IOException("Launcher foreground: only plain paths are supported");
			}
			String fill = color(path.getAttributeNS(ANDROID, "fillColor"), path.getAttributeNS(ANDROID, "fillAlpha"));
			svg.append("\t<path fill=\"").append(fill, 0, 7).append("\" fill-opacity=\"")
					.append(String.format(Locale.ROOT, "%.3f", Integer.parseInt(fill.substring(7), 16) / 255.0))
					.append("\" d=\"").append(escape(path.getAttributeNS(ANDROID, "pathData"))).append("\"/>\n");
		}
		svg.append("</svg>\n");
		write(out, svg.toString());
	}

	private static String backgroundColor(File colors) throws Exception {
		for (Element color : children(parse(colors).getDocumentElement())) {
			if ("background".equals(color.getAttribute("name"))) {
				return color(text(color), "").substring(0, 7);
			}
		}
		throw new IOException("No background colour in " + colors);
	}

	private static Document parse(File file) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setExpandEntityReferences(false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(file);
	}

	private static List<Element> children(Element parent) {
		List<Element> out = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i) instanceof Element) {
				out.add((Element) nodes.item(i));
			}
		}
		return out;
	}

	private static List<Element> elements(Element parent, String tag) {
		List<Element> out = new ArrayList<>();
		for (Element e : children(parent)) {
			if (e.getTagName().equals(tag)) {
				out.add(e);
			}
		}
		return out;
	}

	private static void write(File file, String text) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}
}
