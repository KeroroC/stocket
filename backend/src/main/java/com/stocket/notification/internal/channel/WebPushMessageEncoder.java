package com.stocket.notification.internal.channel;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WebPushMessageEncoder {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final int RECORD_SIZE = 4096;

    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EncodedPush encode(String endpoint, String clientPublicKey, String authSecret, byte[] payload,
                              String vapidPublicKey, String vapidPrivateKey, String subject, Instant now) {
        try {
            byte[] clientPublic = BASE64_URL_DECODER.decode(clientPublicKey);
            byte[] auth = BASE64_URL_DECODER.decode(authSecret);
            byte[] vapidPublic = BASE64_URL_DECODER.decode(vapidPublicKey);
            requireLength(clientPublic, 65);
            requireLength(auth, 16);
            requireLength(vapidPublic, 65);

            KeyPair ephemeral = keyPair();
            byte[] serverPublic = encodePublic((ECPublicKey) ephemeral.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(decodePublic(clientPublic), true);
            byte[] sharedSecret = agreement.generateSecret();

            byte[] authPrk = extract(auth, sharedSecret);
            byte[] ikm = expand(authPrk, concat(KEY_INFO_PREFIX, clientPublic, serverPublic), 32);
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            byte[] prk = extract(salt, ikm);
            byte[] cek = expand(prk, CEK_INFO, 16);
            byte[] nonce = expand(prk, NONCE_INFO, 12);
            byte[] record = concat(payload, new byte[]{2});
            if (record.length + 16 > RECORD_SIZE) throw new IllegalArgumentException("Push payload too large");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(record);
            ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length + ciphertext.length);
            body.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic).put(ciphertext);

            String jwt = vapidToken(endpoint, vapidPrivateKey, subject, now);
            return new EncodedPush(body.array(), "aes128gcm",
                    "vapid t=" + jwt + ", k=" + BASE64_URL.encodeToString(vapidPublic));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode Web Push message", exception);
        }
    }

    private String vapidToken(String endpoint, String privateKey, String subject, Instant now) throws Exception {
        String header = BASE64_URL.encodeToString(
                objectMapper.writeValueAsBytes(Map.of("typ", "JWT", "alg", "ES256")));
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String claims = BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                "aud", audience, "exp", now.plusSeconds(12 * 60 * 60).getEpochSecond(), "sub", subject)));
        String unsigned = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(decodePrivate(BASE64_URL_DECODER.decode(privateKey)), random);
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + BASE64_URL.encodeToString(derToJose(signature.sign()));
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), random);
        return generator.generateKeyPair();
    }

    private java.security.PublicKey decodePublic(byte[] encoded) throws Exception {
        requireLength(encoded, 65);
        if (encoded[0] != 4) throw new IllegalArgumentException("Invalid uncompressed EC public key");
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, parameters()));
    }

    private PrivateKey decodePrivate(byte[] encoded) throws Exception {
        requireLength(encoded, 32);
        return KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(new BigInteger(1, encoded), parameters()));
    }

    private ECParameterSpec parameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private byte[] encodePublic(ECPublicKey key) {
        return concat(new byte[]{4}, fixed(key.getW().getAffineX()), fixed(key.getW().getAffineY()));
    }

    private byte[] fixed(BigInteger value) {
        byte[] encoded = value.toByteArray();
        byte[] result = new byte[32];
        System.arraycopy(encoded, Math.max(0, encoded.length - 32), result,
                Math.max(0, 32 - encoded.length), Math.min(32, encoded.length));
        return result;
    }

    private byte[] extract(byte[] salt, byte[] input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(input);
    }

    private byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private byte[] derToJose(byte[] signature) {
        int offset = 1;
        int sequenceLength = signature[offset++] & 0xff;
        if ((sequenceLength & 0x80) != 0) offset += sequenceLength & 0x7f;
        if (signature[offset++] != 2) throw new IllegalArgumentException("Invalid ECDSA signature");
        int rLength = signature[offset++] & 0xff;
        byte[] r = Arrays.copyOfRange(signature, offset, offset + rLength);
        offset += rLength;
        if (signature[offset++] != 2) throw new IllegalArgumentException("Invalid ECDSA signature");
        int sLength = signature[offset++] & 0xff;
        byte[] s = Arrays.copyOfRange(signature, offset, offset + sLength);
        return concat(toFixedInteger(r), toFixedInteger(s));
    }

    private byte[] toFixedInteger(byte[] value) {
        int first = 0;
        while (first < value.length - 1 && value[first] == 0) first++;
        byte[] result = new byte[32];
        int length = value.length - first;
        if (length > 32) throw new IllegalArgumentException("Invalid ECDSA integer");
        System.arraycopy(value, first, result, 32 - length, length);
        return result;
    }

    private byte[] concat(byte[]... values) {
        int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        Arrays.stream(values).forEach(buffer::put);
        return buffer.array();
    }

    private void requireLength(byte[] value, int length) {
        if (value.length != length) throw new IllegalArgumentException("Invalid Web Push key material");
    }

    public record EncodedPush(byte[] body, String contentEncoding, String authorization) {
        public EncodedPush {
            body = Arrays.copyOf(body, body.length);
        }

        @Override
        public byte[] body() {
            return Arrays.copyOf(body, body.length);
        }
    }
}
