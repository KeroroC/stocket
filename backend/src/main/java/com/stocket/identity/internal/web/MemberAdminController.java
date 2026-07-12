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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.identity.internal.member.MemberAdminService;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.security.IdentityPrincipal;

@RestController
@RequestMapping("/api/v1/admin/members")
class MemberAdminController {

    private final MemberAdminService memberAdminService;
    private final HouseholdMemberRepository householdMemberRepository;

    MemberAdminController(MemberAdminService memberAdminService,
                          HouseholdMemberRepository householdMemberRepository) {
        this.memberAdminService = memberAdminService;
        this.householdMemberRepository = householdMemberRepository;
    }

    @PostMapping
    ResponseEntity<?> createMember(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @Valid @RequestBody CreateMemberRequest request) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        Instant now = Instant.now();

        try {
            MemberResponse response = memberAdminService.createMember(
                    householdId,
                    request.username(),
                    request.displayName(),
                    request.role(),
                    now);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (MemberAdminService.DuplicateUsernameException e) {
            return conflictProblem("DUPLICATE_USERNAME", e.getMessage());
        }
    }

    @GetMapping
    ResponseEntity<List<MemberResponse>> listMembers(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        List<MemberResponse> members = memberAdminService.listMembers(householdId);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/{memberId}")
    ResponseEntity<MemberResponse> getMember(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID memberId) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        MemberResponse response = memberAdminService.getMember(householdId, memberId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{memberId}/role")
    ResponseEntity<?> updateRole(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRequest request) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        Instant now = Instant.now();

        try {
            MemberResponse response = memberAdminService.updateRole(householdId, memberId, request.role(), now);
            return ResponseEntity.ok(response);
        } catch (MemberAdminService.LastAdminRequiredException e) {
            return conflictProblem("LAST_ADMIN_REQUIRED", e.getMessage());
        }
    }

    @PostMapping("/{memberId}/disable")
    ResponseEntity<?> disableMember(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID memberId) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        Instant now = Instant.now();

        try {
            memberAdminService.disableMember(householdId, memberId, now);
            return ResponseEntity.noContent().build();
        } catch (MemberAdminService.LastAdminRequiredException e) {
            return conflictProblem("LAST_ADMIN_REQUIRED", e.getMessage());
        }
    }

    @PostMapping("/{memberId}/reset-password")
    ResponseEntity<?> resetPassword(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID memberId) {
        UUID householdId = resolveHouseholdId(principal.accountId());
        Instant now = Instant.now();

        try {
            String temporaryPassword = memberAdminService.resetPassword(
                    householdId, principal.accountId(), memberId, now);
            return ResponseEntity.ok(new TemporaryPasswordResponse(temporaryPassword));
        } catch (MemberAdminService.ResetPasswordRateLimitedException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
            problem.setTitle("Too many password reset attempts");
            problem.setProperty("code", "RATE_LIMITED");
            problem.setProperty("retryable", true);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
        }
    }

    private UUID resolveHouseholdId(UUID accountId) {
        return householdMemberRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException("Account is not a household member"))
                .getHousehold()
                .getId();
    }

    private ResponseEntity<ProblemDetail> conflictProblem(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle(message);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
