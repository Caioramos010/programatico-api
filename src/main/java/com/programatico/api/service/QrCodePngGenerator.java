package com.programatico.api.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;

final class QrCodePngGenerator {

    private QrCodePngGenerator() {}

    static byte[] gerar(String conteudo, int largura, int altura) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, largura, altura);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o QR code TOTP.", e);
        }
    }
}
