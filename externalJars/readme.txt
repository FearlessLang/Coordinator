This folder needs to contain all the JARs needed by a running Fearless program.
For now, the list is:

## Skija
Skija provides Java bindings for Skia, a native C++ graphics library used for drawing text, shapes, images, paths, filters, and other two-dimensional graphics.
To find the latest Skija version, open:
https://repo.maven.apache.org/maven2/io/github/humbleui/skija-shared/maven-metadata.xml
and search for <versioning>...<release>????</release>
Those are the current last version direct download links.

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-shared/0.143.17/skija-shared-0.143.17.jar

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-macos-x64/0.143.17/skija-macos-x64-0.143.17.jar

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-macos-arm64/0.143.17/skija-macos-arm64-0.143.17.jar

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-windows-x64/0.143.17/skija-windows-x64-0.143.17.jar

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-windows-arm64/0.143.17/skija-windows-arm64-0.143.17.jar

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-linux-x64/0.143.17/skija-linux-x64-0.143.17.jar

https://repo.maven.apache.org/maven2/io/github/humbleui/skija-linux-arm64/0.143.17/skija-linux-arm64-0.143.17.jar

## HarfBuzz
HarfBuzz is a native C library that shapes text: from a text and a font it gives the glyphs and their positions, the outline of each glyph and the color layers of each emoji.
A running Fearless program takes every text measure and every glyph shape from HarfBuzz and the bundled fonts, never from the font engine of the operating system.
Each jar harfbuzz-<os>-<arch>-<version>.jar holds one library, harfbuzz/<os>/<arch>/harfbuzz.dll (Windows), libharfbuzz.so (Linux) or libharfbuzz.dylib (macOS), and the license of HarfBuzz as META-INF/LICENSE-harfbuzz.txt.
The library comes from a natives jar of LWJGL, which also holds libraries Fearless does not use.
To find the latest LWJGL version, open:
https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/maven-metadata.xml
and search for <versioning>...<release>????</release>
Those are the direct download links of LWJGL 3.4.3, holding HarfBuzz 14.3.0:

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/3.4.3/lwjgl-harfbuzz-3.4.3-natives-windows.jar

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/3.4.3/lwjgl-harfbuzz-3.4.3-natives-windows-arm64.jar

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/3.4.3/lwjgl-harfbuzz-3.4.3-natives-linux.jar

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/3.4.3/lwjgl-harfbuzz-3.4.3-natives-linux-arm64.jar

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/3.4.3/lwjgl-harfbuzz-3.4.3-natives-macos.jar

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-harfbuzz/3.4.3/lwjgl-harfbuzz-3.4.3-natives-macos-arm64.jar

The jars without an arch in the name are for x64.
The license of HarfBuzz 14.3.0:
https://raw.githubusercontent.com/harfbuzz/harfbuzz/14.3.0/COPYING

To make one jar, put the library and the license in a folder dir, as they are in the jar, then run
jar --create --date 2026-08-23T00:00:00Z --file harfbuzz-windows-x64-14.3.0.jar -C dir .

## MoltenVK
MoltenVK is a native library implementing Vulkan on Metal, the graphics API of macOS.
A running Fearless program draws with Vulkan on every system. Windows and Linux have a Vulkan loader, which comes with the graphics driver; macOS has none, so on macOS a running Fearless program loads MoltenVK in place of the Vulkan loader.
Each jar moltenvk-macos-<arch>-<version>.jar holds one library, moltenvk/macos/<arch>/libMoltenVK.dylib, and the license of MoltenVK as META-INF/LICENSE-moltenvk.txt.
The library comes from a natives jar of LWJGL.
To find the latest LWJGL version, open:
https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-vulkan/maven-metadata.xml
and search for <versioning>...<release>????</release>
Those are the direct download links of LWJGL 3.4.3, holding MoltenVK 1.4.2 (the commit 4b715bdcb1f108f2003fffe126c389ad0cca1c90 of https://github.com/KhronosGroup/MoltenVK):

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-vulkan/3.4.3/lwjgl-vulkan-3.4.3-natives-macos.jar

https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-vulkan/3.4.3/lwjgl-vulkan-3.4.3-natives-macos-arm64.jar

The jar without an arch in the name is for x64.
The license of MoltenVK:
https://raw.githubusercontent.com/KhronosGroup/MoltenVK/4b715bdcb1f108f2003fffe126c389ad0cca1c90/LICENSE

The jars are made as the HarfBuzz jars are.

## HumbleUI Types
Types is a small library providing common value types used by Skija.
To find the latest version, open:
https://repo.maven.apache.org/maven2/io/github/humbleui/types/maven-metadata.xml
and search for <versioning>...<release>????</release>

This is the current last version direct download link.
https://repo.maven.apache.org/maven2/io/github/humbleui/types/0.2.0/types-0.2.0.jar

## SQLite JDBC
SQLite JDBC allows Java programs to create and access SQLite database files through JDBC.

To find the latest version, open:
https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/maven-metadata.xml
and search for <versioning>...<release>????</release>

This is the current last version direct download link.
https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.2.1/sqlite-jdbc-3.53.2.1.jar