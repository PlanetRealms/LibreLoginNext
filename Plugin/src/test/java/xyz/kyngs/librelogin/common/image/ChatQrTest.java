/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.helger.totp.qr.QrData;
import com.helger.totp.secret.DefaultSecretGenerator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;
import xyz.kyngs.librelogin.lib.adventure.text.format.TextColor;

class ChatQrTest {

    private static final TextColor BLACK = TextColor.color(0x000000);

    /** Reads the glyphs and colours back into modules, the way a player's eyes would. */
    private static boolean[][] readBack(List<Component> lines) {
        var rows = new ArrayList<boolean[]>();
        for (var line : lines) {
            var glyphs = new StringBuilder();
            var colors = new ArrayList<TextColor>();
            collect(line, glyphs, colors);

            var top = new boolean[glyphs.length()];
            var bottom = new boolean[glyphs.length()];
            for (int i = 0; i < glyphs.length(); i++) {
                var color = colors.get(i);
                switch (glyphs.charAt(i)) {
                    case ChatQr.BOTH_DARK -> {
                        assertEquals(BLACK, color, "a dark cell must be black");
                        top[i] = true;
                        bottom[i] = true;
                    }
                    case ChatQr.LOWER_LIGHT -> {
                        assertNull(color, "a light cell keeps the default chat colour");
                        top[i] = true;
                        bottom[i] = false;
                    }
                    case ChatQr.UPPER_LIGHT -> {
                        assertNull(color, "a light cell keeps the default chat colour");
                        top[i] = false;
                        bottom[i] = true;
                    }
                    case ChatQr.BOTH_LIGHT -> {
                        assertNull(color, "a light cell keeps the default chat colour");
                        top[i] = false;
                        bottom[i] = false;
                    }
                    default -> throw new AssertionError("unexpected glyph " + glyphs.charAt(i));
                }
            }
            rows.add(top);
            rows.add(bottom);
        }
        return rows.toArray(boolean[][]::new);
    }

    private static void collect(Component component, StringBuilder glyphs, List<TextColor> colors) {
        if (component instanceof TextComponent text) {
            for (char c : text.content().toCharArray()) {
                glyphs.append(c);
                colors.add(text.color());
            }
        }
        for (var child : component.children()) {
            collect(child, glyphs, colors);
        }
    }

    private static String scan(boolean[][] modules) throws Exception {
        int scale = 6;
        var image =
                new BufferedImage(
                        modules[0].length * scale,
                        modules.length * scale,
                        BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, modules[y / scale][x / scale] ? 0x000000 : 0xFFFFFF);
            }
        }
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new QRCodeReader().decode(bitmap).getText();
    }

    @Test
    void renderedCodeScansBackToTheSameText() throws Exception {
        var secret = new DefaultSecretGenerator().generate();
        var uri = ChatQr.otpauthUri("Steve", secret, "PlanetRealms.com");

        assertEquals(uri, scan(readBack(ChatQr.render(uri))));
    }

    @Test
    void everyLineHasTheSameWidth() {
        var lines = ChatQr.render("otpauth://totp/Steve?secret=ABC&issuer=Test");
        var widths =
                lines.stream()
                        .map(
                                line -> {
                                    var glyphs = new StringBuilder();
                                    collect(line, glyphs, new ArrayList<>());
                                    return glyphs.length();
                                })
                        .distinct()
                        .toList();
        assertEquals(1, widths.size(), "cells would not line up: " + widths);
    }

    @Test
    void largestCodeFitsTheDefaultChatWindow() {
        // Longest account name, 32-character secret (the default) and a 16-character issuer.
        var secret = new DefaultSecretGenerator().generate();
        var uri = ChatQr.otpauthUri("Abcdefghijklmnop", secret, "PlanetRealms.com");

        int lines = ChatQr.render(uri).size();
        assertTrue(lines <= 20, "QR code needs " + lines + " chat lines, the window shows 20");
    }

    @Test
    void compactUriIsAnAcceptedPrefixOfTheFullUri() {
        var secret = new DefaultSecretGenerator().generate();
        var full =
                new QrData.Builder()
                        .label("Steve Smith")
                        .issuer("Planet Realms")
                        .secret(secret)
                        .build()
                        .getUri();

        // The dropped parameters must be the defaults every authenticator app assumes.
        var compact = ChatQr.otpauthUri("Steve Smith", secret, "Planet Realms");
        assertTrue(full.startsWith(compact), full + " does not start with " + compact);
        var dropped = full.substring(compact.length());
        assertEquals("&algorithm=SHA1&digits=6&period=30", dropped);
    }
}
