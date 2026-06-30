package com.programatico.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodePngGeneratorTest {

    @Test
    void gerarDeveProduzirBytesPngValidos() {
        byte[] png = QrCodePngGenerator.gerar("otpauth://totp/test?secret=ABC", 200, 200);

        assertNotNull(png);
        assertTrue(png.length > 8);
        assertTrue(png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
    }
}
