package com.stocket.identity.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.stocket.identity.internal.invite.InviteService;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.security.IdentityPrincipal;

@RestController
@RequestMapping("/api/v1/admin/invites")
class InviteAdminController {

    private final InviteService inviteService;
    private final HouseholdMemberRepository householdMemberRepository;

    InviteAdminController(InviteService inviteService,
                          HouseholdMemberRepository householdMemberRepository) {
        this.inviteService = inviteService;
        this.householdMemberRepository = householdMemberRepository;
    }

    @PostMapping
    ResponseEntity<?> createInvite(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @Valid @RequestBody CreateInviteRequest request) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        Instant now = Instant.now();

        try {
            InviteService.InviteCreationResult result = inviteService.createInvite(
                    householdId, request.role(), request.expiresAt(),
                    principal.accountId(), now);

            String inviteLink = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/invite/")
                    .path(result.rawToken())
                    .toUriString();

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new InviteLinkResponse(result.inviteId(), inviteLink));
        } catch (InviteService.InvalidExpiryException e) {
            return badRequestProblem("INVALID_EXPIRY", e.getMessage());
        }
    }

    @GetMapping
    ResponseEntity<List<InviteResponse>> listInvites(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        List<InviteService.InviteInfo> invites = inviteService.listInvites(householdId);

        List<InviteResponse> response = invites.stream()
                .map(info -> new InviteResponse(
                        info.id(),
                        info.role(),
                        info.expiresAt(),
                        info.acceptedAt(),
                        info.revokedAt(),
                        info.createdAt()))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{inviteId}/revoke")
    ResponseEntity<?> revokeInvite(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID inviteId) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        Instant now = Instant.now();

        try {
            inviteService.revokeInvite(householdId, inviteId, now);
            return ResponseEntity.noContent().build();
        } catch (InviteService.InviteAlreadyAcceptedException e) {
            return conflictProblem("INVITE_ALREADY_ACCEPTED", e.getMessage());
        }
    }

    private UUID resolveHouseholdId(UUID accountId) {
        return householdMemberRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException("Account is not a household member"))
                .getHousehold()
                .getId();
    }

    private ResponseEntity<ProblemDetail> badRequestProblem(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(message);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private ResponseEntity<ProblemDetail> conflictProblem(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle(message);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
