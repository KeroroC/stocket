package com.stocket.notification.internal.channel;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class PublicEndpointPolicy {

    public void requirePublic(String rawUrl) {
        resolve(rawUrl);
    }

    ResolvedEndpoint resolve(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getHost().toLowerCase(Locale.ROOT).endsWith(".local")) {
                throw new InvalidEndpointException();
            }
            List<String> addresses = java.util.Arrays.stream(InetAddress.getAllByName(uri.getHost()))
                    .peek(this::requirePublic)
                    .map(InetAddress::getHostAddress)
                    .sorted()
                    .distinct()
                    .toList();
            if (addresses.isEmpty()) throw new InvalidEndpointException();
            return new ResolvedEndpoint(uri.toString(), addresses);
        } catch (InvalidEndpointException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidEndpointException();
        }
    }

    void requireStable(String rawUrl, Object configuredAddresses) {
        ResolvedEndpoint current = resolve(rawUrl);
        if (!(configuredAddresses instanceof List<?> values)) throw new InvalidEndpointException();
        List<String> expected = values.stream().filter(String.class::isInstance).map(String.class::cast)
                .sorted().distinct().toList();
        if (!current.addresses().equals(expected)) throw new InvalidEndpointException();
    }

    private void requirePublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
            throw new InvalidEndpointException();
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    record ResolvedEndpoint(String url, List<String> addresses) { }

    static final class InvalidEndpointException extends RuntimeException { }
}
