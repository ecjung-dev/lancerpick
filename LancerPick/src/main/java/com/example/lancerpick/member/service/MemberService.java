package com.example.lancerpick.member.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
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
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 회원 기능을 만들지 않는다 (지갑 서비스와 동일 기준)
public class MemberService {
    /**
     * 이메일 형식 (API 10절). 소문자화 후 검사한다.
     * - 로컬 파트: 영문·숫자·허용 특수문자, 점은 맨 앞·맨 뒤·연속 불가, 최대 64자
     * - 도메인: 점으로 구분된 라벨 2개 이상, 라벨은 영문·숫자·하이픈(앞뒤 하이픈 불가) 1~63자, 마지막은 영문 2자 이상
     * 예) review@domain..com, .a@b.com, a@-b.com, a@b → 거절
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=[^@]{1,64}@)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    /** V1__init.sql의 members 이메일 UNIQUE 제약 이름 */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_members_email";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final WalletInitializationService walletInitializationService;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, Clock clock,
            WalletInitializationService walletInitializationService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.walletInitializationService = walletInitializationService;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String displayName = normalizeDisplayName(request.displayName());

        if (memberRepository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        Member member = Member.create(email, passwordEncoder.encode(request.password()), displayName, now);

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
        Member member = findLoggedInMember(memberId);
        if (!displayName.equals(member.getDisplayName())) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
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

    /** 공백 제거 → 소문자화 → 3~254자(코드 포인트) + 형식 검사 */
    private String normalizeEmail(String raw) {
        String email = TextNormalizer.normalize(raw).toLowerCase(Locale.ROOT);
        int length = email.codePointCount(0, email.length());
        if (length < 3 || length > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw invalidInput();
        }
        return email;
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

    private BusinessException emailAlreadyExists() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
    }
}
