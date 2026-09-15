package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Profile("v3")
@Service
public class V3MfaQrCodeService {

    public String createDataUrl(String content, int size) {
        try {
            var matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1
                    )
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new V3AuthException(
                    "MFA_QR_GENERATION_FAILED",
                    "The authenticator setup QR code could not be generated.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
