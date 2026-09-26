package com.tabletap.kitchen;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Turns a docket into ESC/POS bytes, the command language of almost every kitchen receipt printer
 * (Epson TM series and compatibles on port 9100).
 */
public final class EscPos {
    private static final byte ESC = 0x1B, GS = 0x1D;

    private EscPos() {}

    public static byte[] encode(Docket docket, boolean utf8) {
        // Latin text prints on any printer; non-Latin kitchen names need a printer that accepts UTF-8.
        Charset cs = utf8 ? StandardCharsets.UTF_8 : Charset.forName("CP437");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{ESC, '@'});                      // reset
        for (Docket.Line line : docket.lines()) {
            out.writeBytes(new byte[]{GS, '!', (byte) (line.big() ? 0x11 : 0x00)}); // double width+height
            out.writeBytes(line.text().getBytes(cs));
            out.write('\n');
        }
        out.writeBytes(new byte[]{GS, '!', 0x00, '\n', '\n', '\n'});
        out.writeBytes(new byte[]{GS, 'V', 66, 0});                 // feed and cut
        return out.toByteArray();
    }
}
