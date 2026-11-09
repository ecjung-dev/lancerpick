package com.example.lancerpick.member.service;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.auth.service.OneTimeTokens;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
import com.example.lancerpick.member.dto.EmailRules;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.dto.UpdateMeRequest;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 회원 기능을 만들지 않는다 (지갑 서비스와 동일 기준)
public class MemberService {
    /** V1__init.sql의 members 이메일 UNIQUE 제약 이름 */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_members_email";

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final WalletInitializationService walletInitializationService;
    private final AccountChallengeRepository challengeRepository;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, Clock clock,
            WalletInitializationService walletInitializationService, AccountChallengeRepository challengeRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.walletInitializationService = walletInitializationService;
        this.challengeRepository = challengeRepository;
    }

    /**
     * POST /api/members — 이메일 인증을 마친 가입(3인 설계 §5.1).
     * 한 트랜잭션: 가입 증명 잠금·확인 → 회원 저장(email_verified_at 기록) → 증명 사용 처리 → 지갑·최초 지급.
     * 어느 단계든 실패하면 증명 사용 처리까지 함께 롤백되어, 같은 증명으로 다시 시도할 수 있다.
     * - 같은 증명 동시 가입: 증명 행 잠금에서 한 줄로 서고, 늦은 쪽은 "이미 사용됨"으로 400.
     * - 같은 이메일·다른 증명 동시 가입: 이메일 UNIQUE로 한 건만 성공하고 나머지는 409(그쪽 증명 사용도 롤백).
     * 잠금 순서: 증명 행 → (새) 회원 행 → 지갑. 이 시점에는 기존 회원 행을 잠그지 않는다.
     */
    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String displayName = normalizeDisplayName(request.displayName());

        byte[] proofHash = OneTimeTokens.hashOf(request.emailVerificationProof())
                .orElseThrow(() -> verificationInvalid("MALFORMED", null));
        AccountChallenge proof = challengeRepository.findByTokenHashForUpdate(proofHash)
                .orElseThrow(() -> verificationInvalid("UNKNOWN", null));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String rejection = proof.signupProofRejection(email, now);
        if (rejection != null) {
            throw verificationInvalid(rejection, proof.getId());
        }

        // 증명 확인 뒤에 중복을 알려 준다. 증명은 메일을 받은 본인만 가지므로 가입 여부가 남에게 드러나지 않는다.
        if (memberRepository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }

        Member member = Member.createVerified(email, passwordEncoder.encode(request.password()), displayName, now);

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 DB UNIQUE(uk_members_email)가 막는다 → 409로 변환.
            // 그 밖의 제약 위반은 이메일 중복이 아니므로 원래 예외를 그대로 전파한다(공통 처리에서 500).
            // 실패한 트랜잭션 안에서 재조회하지 않는다.
            if (isEmailUniqueViolation(e)) {
                throw emailAlreadyExists();
            }
            throw e;
        }

        proof.consume(now);   // 트랜잭션 종료 시 consumed_at UPDATE. 아래 지갑이 실패하면 함께 롤백된다

        // 지갑 생성 + 초기 적립금 100만원 (황준범 담당 서비스, 같은 트랜잭션).
        // try-catch로 감싸지 않는다: 지급이 실패하면 예외가 전파되어 회원 저장까지 함께 롤백된다.
        walletInitializationService.initializeForNewMember(member.getId());

        return MemberResponse.from(member, false);   // 방금 가입한 회원은 판매자 프로필이 없음
    }

    /** GET /api/me — 세션의 회원 ID로 내 정보를 조회한다. */
    @Transactional(readOnly = true)
    public MemberResponse getMe(long memberId) {
        Member member = findLoggedInMember(memberId);
        return MemberResponse.from(member, hasSellerProfile(memberId));
    }

    /** PATCH /api/me — 표시 이름만 수정한다. 같은 값이면 updated_at을 바꾸지 않는다. */
    @Transactional
    public MemberResponse updateMe(long memberId, UpdateMeRequest request) {
        String displayName = normalizeDisplayName(request.displayName());
        // 비밀번호 변경·재설정과 직렬화해, 이전 엔티티 상태가 자격 증명을 덮어쓰지 않게 한다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(MemberService::authenticationRequired);
        if (!displayName.equals(member.getDisplayName())) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            if (member.getDisplayNameChangedAt() != null
                    && now.isBefore(MemberChangePolicy.sixMonthsAfter(member.getDisplayNameChangedAt()))) {
                throw new BusinessException(HttpStatus.CONFLICT, "DISPLAY_NAME_CHANGE_NOT_ALLOWED",
                        "표시 이름은 변경 후 6개월이 지나면 다시 바꿀 수 있습니다. 내 정보를 새로고침해 변경 가능일을 확인해 주세요.");
            }
            member.changeDisplayName(displayName, now);   // 트랜잭션 종료 시 변경 감지로 UPDATE
        }
        return MemberResponse.from(member, hasSellerProfile(memberId));
    }

    /**
     * POST /api/me/password — 현재 비밀번호를 다시 확인한 뒤 새 비밀번호로 바꾼다.
     * 성공하면 auth_version이 1 올라가 이 회원의 모든 기존 세션이 폐기된다(세션 무효화는 Controller·필터가 한다).
     * 지갑·적립금은 건드리지 않는다.
     *
     * 확인 순서(실패하면 아무것도 바꾸지 않는다):
     * 1) 회원 행을 잠근다. 같은 회원의 변경이 동시에 오면 여기서 한 줄로 선다.
     * 2) 세션의 로그인 당시 버전과 DB 버전이 다르면 이미 다른 곳에서 비밀번호가 바뀐 세션이다 → 401.
     *    (동시 요청 중 늦은 쪽은 앞 요청이 올린 버전을 보고 여기서 거절된다. 앞 요청을 덮어쓰지 않는다.)
     * 3) 현재 비밀번호가 틀리면 400 CURRENT_PASSWORD_INCORRECT.
     * 4) 새 비밀번호가 현재와 같으면 400 PASSWORD_UNCHANGED.
     *
     * @param expectedAuthVersion 세션 principal의 authVersion. null이면(테스트용 principal) 2)를 건너뛴다.
     */
    @Transactional
    public void changePassword(long memberId, Long expectedAuthVersion, ChangePasswordRequest request) {
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(MemberService::authenticationRequired);

        if (expectedAuthVersion != null && expectedAuthVersion != member.getAuthVersion()) {
            throw authenticationRequired();
        }
        if (!currentPasswordMatches(request.currentPassword(), member.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "현재 비밀번호가 올바르지 않습니다.");
        }
        // 3)을 통과했으므로 현재 비밀번호 원문과 같으면 저장된 비밀번호와도 같다. 해시 비교를 한 번 더 하지 않는다.
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "새 비밀번호가 현재 비밀번호와 같습니다.");
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        member.changePassword(passwordEncoder.encode(request.newPassword()), now);   // 트랜잭션 종료 시 UPDATE
    }

    /**
     * BCrypt는 72바이트까지만 비교하고, 그보다 긴 입력은 버전에 따라 예외가 날 수 있다.
     * 저장된 비밀번호는 정책상 64바이트 이하이므로 72바이트를 넘는 입력은 비교 없이 불일치로 본다(500 방지).
     */
    private boolean currentPasswordMatches(String rawPassword, String passwordHash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    /** 세션에는 있는데 DB에 회원이 없으면(삭제 등) 로그인이 유효하지 않은 것으로 본다. */
    private Member findLoggedInMember(long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberService::authenticationRequired);
    }

    private static BusinessException authenticationRequired() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private boolean hasSellerProfile(long memberId) {
        return memberRepository.countSellerProfiles(memberId) > 0;
    }

    /** 공백 제거 → 소문자화 → 3~254자(코드 포인트) + 형식 검사. 규칙은 비밀번호 재설정과 같은 EmailRules를 쓴다. */
    private String normalizeEmail(String raw) {
        return EmailRules.normalize(raw).orElseThrow(this::invalidInput);
    }

    /** 공백 제거 → 1~30자(코드 포인트) */
    private String normalizeDisplayName(String raw) {
        String name = TextNormalizer.normalize(raw);
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > 30) {
            throw invalidInput();
        }
        return name;
    }

    /** 예외 원인 사슬에서 이메일 UNIQUE 제약 위반인지 확인한다. 이름이 확인되지 않으면 false. */
    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && containsEmailConstraint(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLIntegrityConstraintViolationException sql
                    && containsEmailConstraint(sql.getMessage())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static boolean containsEmailConstraint(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT);
    }

    private BusinessException invalidInput() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }

    /** 가입 증명 오류는 원인과 상관없이 하나로 응답하고, 원인은 안전한 코드로 로그에만 남긴다(증명 원문 X). */
    private static BusinessException verificationInvalid(String reason, Long challengeId) {
        log.info("signup proof rejected reason={} challengeId={}", reason, challengeId);
        return new BusinessException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_INVALID",
                "이메일 인증이 만료됐거나 유효하지 않습니다. 인증 메일을 다시 요청해 주세요.");
    }

    private BusinessException emailAlreadyExists() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
    }
}
