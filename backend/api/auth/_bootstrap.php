<?php
require_once __DIR__ . '/../config.php';
date_default_timezone_set('Asia/Kolkata');
$autoload = __DIR__ . '/../vendor/autoload.php';
if (file_exists($autoload)) {
    require_once $autoload;
}

header('Content-Type: application/json; charset=utf-8');

function json_in(): array {
    $raw = file_get_contents('php://input');
    $data = json_decode($raw ?: '{}', true);
    return is_array($data) ? $data : [];
}

function json_out($payload, int $code = 200): void {
    http_response_code($code);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES);
    exit;
}

function out($payload, int $code = 200): void {
    json_out($payload, $code);
}
function pdo(): PDO {
    static $pdo = null;
    if ($pdo) return $pdo;

    $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4';
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $pdo->exec("SET time_zone = '+05:30'");
    return $pdo;
}

function now(): string { return date('Y-m-d H:i:s'); }

function norm_email(string $email): string {
    $email = trim(strtolower($email));
    return $email;
}

function role_norm(string $role): string {
    $r = strtoupper(trim($role));
    $allowed = ['FARMER','DISTRIBUTOR','RETAILER','CONSUMER'];
    return in_array($r, $allowed, true) ? $r : '';
}

function otp_gen(int $len = 6): string {
    $min = (int)pow(10, $len - 1);
    $max = (int)pow(10, $len) - 1;
    return (string)random_int($min, $max);
}

function sha256_hex(string $s): string {
    return hash('sha256', $s);
}

function b64url(string $bin): string {
    return rtrim(strtr(base64_encode($bin), '+/', '-_'), '=');
}

function jwt_sign(array $claims): string {
    $header = ['alg' => 'HS256', 'typ' => 'JWT'];
    $iat = time();
    $exp = $iat + JWT_TTL;

    $payload = array_merge($claims, ['iat' => $iat, 'exp' => $exp]);

    $h = b64url(json_encode($header));
    $p = b64url(json_encode($payload));
    $sig = hash_hmac('sha256', "$h.$p", JWT_SECRET, true);
    return "$h.$p." . b64url($sig);
}

function audit(PDO $pdo, ?int $actor_user_id, string $action, array $meta = []): void {
    try {
        $st = $pdo->prepare("INSERT INTO audit_logs(actor_user_id, action, meta_json, ip_addr, user_agent)
                             VALUES(?, ?, ?, ?, ?)");
        $ip = $_SERVER['REMOTE_ADDR'] ?? null;
        $ua = $_SERVER['HTTP_USER_AGENT'] ?? null;
        $st->execute([
            $actor_user_id,
            $action,
            $meta ? json_encode($meta, JSON_UNESCAPED_SLASHES) : null,
            $ip,
            $ua
        ]);
    } catch (Throwable $e) {
        // never block auth due to audit failure
    }
}

/**
 * Sends OTP email. Uses PHP mail(). In DEV_MODE it’s okay if mail fails.
 */
function smtp_is_configured(): bool {
    $required = [
        'SMTP_HOST',
        'SMTP_USER',
        'SMTP_PASS',
        'SMTP_PORT',
        'SMTP_SECURE',
        'SMTP_FROM_EMAIL',
        'SMTP_FROM_NAME'
    ];

    foreach ($required as $key) {
        if (!defined($key) || trim((string)constant($key)) === '') {
            return false;
        }
    }

    $placeholders = [
        'smtp.example.com',
        'no-reply@example.com',
        'CHANGE_ME_SMTP_PASSWORD'
    ];

    foreach ($placeholders as $val) {
        if (constant('SMTP_HOST') === $val || constant('SMTP_USER') === $val || constant('SMTP_PASS') === $val) {
            return false;
        }
    }

    return true;
}

function send_email_otp(string $to, string $otp, string $purpose): bool {
    if (!smtp_is_configured()) {
        return false;
    }

    if (!class_exists(\PHPMailer\PHPMailer\PHPMailer::class)) {
        return false;
    }

    $ttlSeconds = defined('OTP_TTL_SECONDS') ? (int)OTP_TTL_SECONDS : 600;
    $ttlMinutes = (int)ceil($ttlSeconds / 60);
    $purposeText = $purpose === 'RESET_PASSWORD' ? 'Reset your password' : 'Verify your email';

    $subject = $purpose === 'RESET_PASSWORD'
        ? "AgriTrace password reset code"
        : "AgriTrace email verification code";

    $text = "Use this code to {$purposeText}: {$otp}\n"
          . "This code expires in {$ttlMinutes} minutes.\n"
          . "If you did not request this, you can ignore this email.";

    $html = otp_email_template($otp, $purposeText, $ttlMinutes);

    try {
        $mail = new \PHPMailer\PHPMailer\PHPMailer(true);
        $mail->isSMTP();
        $mail->Host = SMTP_HOST;
        $mail->SMTPAuth = true;
        $mail->Username = SMTP_USER;
        $mail->Password = SMTP_PASS;
        $mail->Port = (int)SMTP_PORT;

        $secure = strtolower((string)SMTP_SECURE);
        $mail->SMTPSecure = ($secure === 'ssl')
            ? \PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_SMTPS
            : \PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_STARTTLS;

        $mail->setFrom(SMTP_FROM_EMAIL, SMTP_FROM_NAME);
        $mail->addAddress($to);
        $mail->Subject = $subject;
        $mail->Body = $html;
        $mail->AltBody = $text;
        $mail->isHTML(true);

        return (bool)$mail->send();
    } catch (Throwable $e) {
        return false;
    }
}

function otp_email_template(string $otp, string $purposeText, int $ttlMinutes): string {
    $brand = 'AgriTrace';
    $supportEmail = defined('SUPPORT_EMAIL') ? SUPPORT_EMAIL : (defined('SMTP_FROM_EMAIL') ? SMTP_FROM_EMAIL : 'support@agritrace.local');
    $year = date('Y');

    return '<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>' . $brand . ' OTP</title>
  </head>
  <body style="margin:0;padding:0;background:#f4f6f8;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:24px 0;">
      <tr>
        <td align="center">
          <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 6px 20px rgba(0,0,0,0.08);">
            <tr>
              <td style="background:#0f5132;padding:24px 32px;color:#ffffff;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:bold;">
                ' . $brand . '
              </td>
            </tr>
            <tr>
              <td style="padding:28px 32px 8px 32px;font-family:Arial,Helvetica,sans-serif;color:#1f2933;">
                <h2 style="margin:0 0 8px 0;font-size:20px;">' . $purposeText . '</h2>
                <p style="margin:0 0 16px 0;font-size:14px;line-height:1.5;color:#52606d;">
                  Use the verification code below to continue. This code expires in ' . $ttlMinutes . ' minutes.
                </p>
              </td>
            </tr>
            <tr>
              <td align="center" style="padding:8px 32px 24px 32px;">
                <div style="display:inline-block;padding:14px 24px;border-radius:10px;background:#f0f4f8;font-family:Arial,Helvetica,sans-serif;font-size:28px;letter-spacing:4px;font-weight:bold;color:#1f2933;">
                  ' . $otp . '
                </div>
              </td>
            </tr>
            <tr>
              <td style="padding:0 32px 24px 32px;font-family:Arial,Helvetica,sans-serif;color:#52606d;font-size:12px;line-height:1.5;">
                If you did not request this, you can safely ignore this email.
              </td>
            </tr>
            <tr>
              <td style="padding:16px 32px 24px 32px;font-family:Arial,Helvetica,sans-serif;color:#7b8794;font-size:12px;border-top:1px solid #e4e7eb;">
                Need help? Contact us at <a href="mailto:' . $supportEmail . '" style="color:#0f5132;text-decoration:none;">' . $supportEmail . '</a>.
                <div style="margin-top:8px;">&copy; ' . $year . ' ' . $brand . '. All rights reserved.</div>
              </td>
            </tr>
          </table>
        </td>
      </tr>
    </table>
  </body>
</html>';
}

function require_fields(array $in, array $fields): void {
    foreach ($fields as $f) {
        if (!isset($in[$f]) || trim((string)$in[$f]) === '') {
            json_out(['ok' => false, 'error' => "Missing field: {$f}"], 400);
        }
    }
}
