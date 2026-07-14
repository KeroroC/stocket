package com.stocket.notification;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.stocket.notification.internal.channel.WebPushMessageEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushMessageEncoderTest {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    @Test
    void createsDecryptableAes128GcmPayloadAndValidVapidAuthorization() throws Exception {
        KeyPair client = keyPair();
        KeyPair vapid = keyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String endpoint = "https://push.example.com/send/subscription";
        String payload = "{\"title\":\"Stocket\",\"body\":\"低库存提醒\"}";

        WebPushMessageEncoder.EncodedPush encoded = new WebPushMessageEncoder().encode(
                endpoint, BASE64_URL.encodeToString(publicKey(client)), BASE64_URL.encodeToString(auth),
                payload.getBytes(StandardCharsets.UTF_8), BASE64_URL.encodeToString(publicKey(vapid)),
                BASE64_URL.encodeToString(privateKey(vapid)), "mailto:admin@example.com",
                Instant.parse("2026-07-14T04:00:00Z"));

        assertThat(encoded.contentEncoding()).isEqualTo("aes128gcm");
        assertThat(decrypt(encoded.body(), client.getPrivate(), publicKey(client), auth))
                .isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
        verifyVapid(encoded.authorization(), vapid.getPublic(), endpoint);
    }

    private byte[] decrypt(byte[] body, java.security.PrivateKey clientPrivate,
                           byte[] clientPublic, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        int recordSize = buffer.getInt();
        int keyLength = Byte.toUnsignedInt(buffer.get());
        byte[] serverPublic = new byte[keyLength];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        assertThat(recordSize).isEqualTo(4096);
        assertThat(keyLength).isEqualTo(65);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(clientPrivate);
        agreement.doPhase(decodePublicKey(serverPublic), true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] authPrk = hkdfExtract(auth, sharedSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), clientPublic, serverPublic);
        byte[] ikm = hkdfExpand(authPrk, keyInfo, 32);
        byte[] prk = hkdfExtract(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plaintext = cipher.doFinal(ciphertext);
        assertThat(plaintext[plaintext.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(plaintext, plaintext.length - 1);
    }

    @SuppressWarnings("unchecked")
    private void verifyVapid(String authorization, java.security.PublicKey publicKey, String endpoint)
            throws Exception {
        assertThat(authorization).startsWith("vapid t=").contains(", k=");
        String token = authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
        String advertisedKey = authorization.substring(authorization.indexOf(", k=") + 4);
        assertThat(advertisedKey).isEqualTo(BASE64_URL.encodeToString(publicKey(publicKey)));
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        var claims = new ObjectMapper().readValue(BASE64_URL_DECODER.decode(parts[1]), java.util.Map.class);
        assertThat(claims.get("aud")).isEqualTo("https://push.example.com");
        assertThat(claims.get("sub")).isEqualTo("mailto:admin@example.com");
        assertThat(((Number) claims.get("exp")).longValue()).isEqualTo(1784044800L);

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(joseToDer(BASE64_URL_DECODER.decode(parts[2])))).isTrue();
        assertThat(URI.create(endpoint).getHost()).isEqualTo("push.example.com");
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private java.security.PublicKey decodePublicKey(byte[] encoded) throws Exception {
        ECParameterSpec params = parameters();
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, params));
    }

    private ECParameterSpec parameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private byte[] publicKey(KeyPair pair) {
        return publicKey(pair.getPublic());
    }

    private byte[] publicKey(java.security.PublicKey key) {
        ECPublicKey publicKey = (ECPublicKey) key;
        return concat(new byte[]{4}, fixed(publicKey.getW().getAffineX()), fixed(publicKey.getW().getAffineY()));
    }

    private byte[] privateKey(KeyPair pair) {
        return fixed(((ECPrivateKey) pair.getPrivate()).getS());
    }

    private byte[] fixed(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        System.arraycopy(bytes, Math.max(0, bytes.length - 32), result, Math.max(0, 32 - bytes.length),
                Math.min(32, bytes.length));
        return result;
    }

    private byte[] hkdfExtract(byte[] salt, byte[] input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(input);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private byte[] concat(byte[]... values) {
        int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        Arrays.stream(values).forEach(buffer::put);
        return buffer.array();
    }

    private byte[] joseToDer(byte[] signature) {
        byte[] r = unsignedInteger(Arrays.copyOfRange(signature, 0, 32));
        byte[] s = unsignedInteger(Arrays.copyOfRange(signature, 32, 64));
        ByteBuffer buffer = ByteBuffer.allocate(6 + r.length + s.length);
        buffer.put((byte) 0x30).put((byte) (4 + r.length + s.length));
        buffer.put((byte) 0x02).put((byte) r.length).put(r);
        buffer.put((byte) 0x02).put((byte) s.length).put(s);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private byte[] unsignedInteger(byte[] value) {
        int first = 0;
        while (first < value.length - 1 && value[first] == 0) first++;
        byte[] trimmed = Arrays.copyOfRange(value, first, value.length);
        if ((trimmed[0] & 0x80) == 0) return trimmed;
        return concat(new byte[]{0}, trimmed);
    }
}
