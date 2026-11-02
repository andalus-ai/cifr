package tech.kayys.cifr.ide;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CifrIdeClientTest {
    @Test
    void readsContentLengthFramedUtf8PayloadByByteLength() throws Exception {
        byte[] payload = "{\"label\":\"café 東京\"}".getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + payload.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[header.length + payload.length];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(payload, 0, frame, header.length, payload.length);

        assertArrayEquals(payload, CifrIdeClient.readFrame(new ByteArrayInputStream(frame)));
    }

    @Test
    void rejectsMissingContentLength() {
        byte[] frame = "\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IOException.class,
                () -> CifrIdeClient.readFrame(new ByteArrayInputStream(frame)));
    }

    @Test
    void rejectsTruncatedPayload() {
        byte[] frame = "Content-Length: 5\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII);
        assertThrows(EOFException.class,
                () -> CifrIdeClient.readFrame(new ByteArrayInputStream(frame)));
    }
}
