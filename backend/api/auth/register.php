<?php
require_once __DIR__ . '/_bootstrap.php';

$in = json_in();

$full_name = trim((string)($in['full_name'] ?? ''));
$emailRaw  = trim((string)($in['email'] ?? ''));
$phoneRaw  = trim((string)($in['phone_e164'] ?? ''));
$password  = (string)($in['password'] ?? '');
$role      = role_norm((string)($in['role'] ?? ''));

if ($full_name === '') json_out(['ok'=>false,'error'=>'Full name required'], 400);
if ($role === '')      json_out(['ok'=>false,'error'=>'Invalid role'], 400);
if (strlen($password) < 6) json_out(['ok'=>false,'error'=>'Password must be at least 6 characters'], 400);

// Require email (because your Android expects email OTP)
if ($emailRaw === '') json_out(['ok'=>false,'error'=>'Email is required for OTP verification'], 400);

$email = norm_email($emailRaw);
$phone = $phoneRaw !== '' ? $phoneRaw : null;

$pdo = pdo();

// duplicates
$st = $pdo->prepare("SELECT id FROM users WHERE email_norm = ? LIMIT 1");
$st->execute([$email]);
if ($st->fetch()) json_out(['ok'=>false,'error'=>'Email already registered'], 409);

if ($phone) {
    $st = $pdo->prepare("SELECT id FROM users WHERE phone_e164 = ? LIMIT 1");
    $st->execute([$phone]);
    if ($st->fetch()) json_out(['ok'=>false,'error'=>'Phone already registered'], 409);
}

$hash = password_hash($password, PASSWORD_BCRYPT);

// prevent duplicates in pending registrations
$st = $pdo->prepare("SELECT id FROM pending_registrations WHERE email_norm=? LIMIT 1");
$st->execute([$email]);
$pending = $st->fetch();

if ($phone) {
    $st = $pdo->prepare("SELECT id FROM pending_registrations WHERE phone_e164=? AND email_norm<>? LIMIT 1");
    $st->execute([$phone, $email]);
    if ($st->fetch()) json_out(['ok'=>false,'error'=>'Phone already registered'], 409);
}

try {
    $pdo->beginTransaction();

    $purpose = 'VERIFY_EMAIL';
    $otp = otp_gen(6);
    $code_hash = sha256_hex($otp);
    $expires_at = date('Y-m-d H:i:s', time() + OTP_TTL_SECONDS);
    $issued_at = now();

    if ($pending) {
        $st = $pdo->prepare("SELECT issued_at FROM pending_registrations WHERE email_norm=? LIMIT 1");
        $st->execute([$email]);
        $last = $st->fetch();
        if ($last) {
            $lastTs = strtotime($last['issued_at']);
            if ($lastTs && (time() - $lastTs) < OTP_MIN_RESEND_SECONDS) {
                $pdo->rollBack();
                json_out(['ok'=>true,'message'=>'OTP recently sent. Please check your email.'], 200);
            }
        }

        $st = $pdo->prepare("UPDATE pending_registrations
            SET full_name=?, email=?, email_norm=?, phone_e164=?, password_hash=?, role=?,
                otp_hash=?, otp_expires_at=?, otp_attempts=0, otp_max_attempts=?,
                issued_at=?, request_ip=?, user_agent=?
            WHERE email_norm=?");
        $st->execute([
            $full_name, $email, $email, $phone, $hash, $role,
            $code_hash, $expires_at, OTP_MAX_ATTEMPTS,
            $issued_at, ($_SERVER['REMOTE_ADDR'] ?? null), ($_SERVER['HTTP_USER_AGENT'] ?? null),
            $email
        ]);
    } else {
        $st = $pdo->prepare("INSERT INTO pending_registrations
            (full_name,email,email_norm,phone_e164,password_hash,role,otp_hash,otp_expires_at,otp_attempts,otp_max_attempts,issued_at,created_at,request_ip,user_agent)
            VALUES(?,?,?,?,?,?,?,?,0,?,?,?, ?, ?)");
        $st->execute([
            $full_name, $email, $email, $phone, $hash, $role,
            $code_hash, $expires_at, OTP_MAX_ATTEMPTS, $issued_at, $issued_at,
            ($_SERVER['REMOTE_ADDR'] ?? null), ($_SERVER['HTTP_USER_AGENT'] ?? null)
        ]);
    }

    $sent = send_email_otp($email, $otp, $purpose);
    if (!$sent) {
        $pdo->rollBack();
        json_out(['ok'=>false,'error'=>'Email service not configured or unavailable.'], 500);
    }

    $pdo->commit();

    json_out([
        'ok' => true,
        'message' => 'Verification code sent.',
        'email' => $email
    ]);

} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    json_out(['ok'=>false,'error'=>'Something went wrong. Please try again.'], 500);
}
