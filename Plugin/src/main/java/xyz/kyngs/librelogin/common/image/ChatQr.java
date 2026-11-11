/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;
import xyz.kyngs.librelogin.lib.adventure.text.format.TextColor;

/**
 * Renders a QR code as chat text, so 2FA can be set up without any packet library (Protocolize).
 *
 * <p>The rendering is the one NavAuth used. Each text column is one QR column and two module rows
 * share one chat line. Light modules are drawn with narrow Unifont block glyphs in the default
 * (white) chat colour, dark modules as a black blank, so it is a normal dark-on-light code:
 *
 * <ul>
 *   <li>both modules light: {@code ▋}
 *   <li>upper dark, lower light: {@code ▖}
 *   <li>upper light, lower dark: {@code ▘}
 *   <li>both dark: a black braille blank, which keeps the column width
 * </ul>
 *
 * The block glyphs of a wider shape ({@code █ ▀ ▄}) are too wide in Minecraft's font: a 39 column
 * QR code then wraps in the default chat window.
 */
public final class ChatQr {

    /**
     * Light border around the code, in modules. Smaller than the QR specification asks for, so the
     * largest code this plugin produces fits the 20 lines of Minecraft's default chat window.
     */
    public static final int QUIET_ZONE = 1;

    /** Both modules light. */
    static final char BOTH_LIGHT = '▋';

    /** Upper module dark, lower module light. */
    static final char LOWER_LIGHT = '▖';

    /** Upper module light, lower module dark. */
    static final char UPPER_LIGHT = '▘';

    /** Both modules dark: a braille blank, drawn black. */
    static final char BOTH_DARK = '⠀';

    /** Colour of the dark cells; the light cells use the default chat colour. */
    static final TextColor BLACK = TextColor.color(0x000000);

    // Error correction L keeps the code small enough to fit in the chat window.
    private static final Map<EncodeHintType, Object> HINTS =
            Map.of(
                    EncodeHintType.MARGIN,
                    QUIET_ZONE,
                    EncodeHintType.ERROR_CORRECTION,
                    ErrorCorrectionLevel.L,
                    EncodeHintType.CHARACTER_SET,
                    StandardCharsets.UTF_8.name());

    private ChatQr() {}

    /**
     * Builds a compact {@code otpauth://} URI. The default algorithm (SHA1), digits (6) and period
     * (30) are left out, which every authenticator app assumes, because each extra character makes
     * the QR code taller.
     *
     * @param label the account name shown in the authenticator app
     * @param secret the Base32 TOTP secret
     * @param issuer the issuer shown in the authenticator app
     * @return the URI to encode
     */
    public static String otpauthUri(String label, String secret, String issuer) {
        return "otpauth://totp/"
                + encode(label)
                + "?secret="
                + encode(secret)
                + "&issuer="
                + encode(issuer);
    }

    /**
     * Encodes the content as a QR code.
     *
     * @param content the text to encode
     * @return the square module grid including the quiet zone, {@code true} meaning a dark module
     * @throws IllegalStateException if the content cannot be encoded
     */
    public static boolean[][] modules(String content) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, HINTS);
        } catch (WriterException e) {
            throw new IllegalStateException("Could not encode the 2FA QR code", e);
        }

        var grid = new boolean[matrix.getHeight()][matrix.getWidth()];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                grid[y][x] = matrix.get(x, y);
            }
        }
        return grid;
    }

    /**
     * Renders the content as QR code lines for chat.
     *
     * @param content the text to encode
     * @return one component per chat line
     * @throws IllegalStateException if the content cannot be encoded
     */
    public static List<Component> render(String content) {
        var grid = modules(content);
        var lines = new ArrayList<Component>((grid.length + 1) / 2);
        for (int row = 0; row < grid.length; row += 2) {
            lines.add(line(grid, row));
        }
        return lines;
    }

    private static Component line(boolean[][] grid, int row) {
        TextComponent.Builder line = Component.text();
        var run = new StringBuilder();
        TextColor runColor = null;

        for (int col = 0; col < grid[row].length; col++) {
            boolean top = grid[row][col];
            // An odd grid height leaves the last line without a bottom row; that is light.
            boolean bottom = row + 1 < grid.length && grid[row + 1][col];

            char glyph;
            if (top && bottom) {
                glyph = BOTH_DARK;
            } else if (top) {
                glyph = LOWER_LIGHT;
            } else if (bottom) {
                glyph = UPPER_LIGHT;
            } else {
                glyph = BOTH_LIGHT;
            }

            // Only the blanks are coloured; everything else keeps the default chat colour.
            TextColor color = glyph == BOTH_DARK ? BLACK : null;
            if (run.length() > 0 && !Objects.equals(color, runColor)) {
                line.append(text(run.toString(), runColor));
                run.setLength(0);
            }
            runColor = color;
            run.append(glyph);
        }

        if (run.length() > 0) {
            line.append(text(run.toString(), runColor));
        }
        return line.build();
    }

    private static Component text(String content, TextColor color) {
        return color == null ? Component.text(content) : Component.text(content, color);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
