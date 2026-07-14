package com.stocket.system.operations.ratelimit;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import com.stocket.identity.ClientAddressProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver implements ClientAddressProvider {
    private final List<Cidr> trusted;

    @Autowired
    public ClientAddressResolver(@Value("${stocket.trusted-proxy-cidrs:}") String cidrs) {
        this(split(cidrs));
    }

    ClientAddressResolver(List<String> cidrs) {
        this.trusted = cidrs.stream().filter(value -> !value.isBlank()).map(Cidr::parse).toList();
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String peer = numeric(request.getRemoteAddr());
        if (peer == null || trusted.stream().noneMatch(cidr -> cidr.contains(peer))) return fallback(peer);
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.contains(",")) return fallback(peer);
        String candidate = numeric(forwarded.strip());
        return candidate == null ? fallback(peer) : candidate;
    }

    private String fallback(String peer) { return peer == null ? "unknown" : peer; }
    private static List<String> split(String value) { return value == null ? List.of() : Arrays.asList(value.split(",")); }
    private static String numeric(String value) {
        if (value == null || value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception error) { return null; }
    }

    private record Cidr(byte[] network, int prefix) {
        static Cidr parse(String value) {
            try {
                String[] parts = value.strip().split("/", -1);
                InetAddress address = InetAddress.getByName(parts[0]);
                int bits = address.getAddress().length * 8;
                int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("invalid prefix");
                return new Cidr(address.getAddress(), prefix);
            } catch (Exception error) { throw new IllegalArgumentException("Invalid trusted proxy CIDR", error); }
        }
        boolean contains(String value) {
            try {
                byte[] address = InetAddress.getByName(value).getAddress();
                if (address.length != network.length) return false;
                int full = prefix / 8; int remaining = prefix % 8;
                for (int index = 0; index < full; index++) if (address[index] != network[index]) return false;
                if (remaining == 0) return true;
                int mask = 0xff << (8 - remaining);
                return (address[full] & mask) == (network[full] & mask);
            } catch (Exception error) { return false; }
        }
    }
}
