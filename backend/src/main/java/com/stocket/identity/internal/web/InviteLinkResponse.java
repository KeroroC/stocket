package com.stocket.identity.internal.web;

import java.util.UUID;

public record InviteLinkResponse(
        UUID id,
        String inviteLink
) {
}
