CREATE DATABASE IF NOT EXISTS agritrace
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE agritrace;

-- ------------------------------------------------------------
-- USERS (Register + Login)
-- ------------------------------------------------------------
CREATE TABLE users (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  full_name           VARCHAR(120) NOT NULL,

  email               VARCHAR(190) NULL,
  email_norm          VARCHAR(190)
                      GENERATED ALWAYS AS (LOWER(email)) STORED,

  phone_e164          VARCHAR(20) NULL,     -- store as +911234567890 format ideally

  password_hash       VARCHAR(255) NOT NULL,

  role                ENUM('FARMER','DISTRIBUTOR','RETAILER','CONSUMER') NOT NULL,

  is_active           TINYINT(1) NOT NULL DEFAULT 1,

  email_verified_at   DATETIME NULL,
  phone_verified_at   DATETIME NULL,

  failed_login_attempts INT NOT NULL DEFAULT 0,
  locked_until        DATETIME NULL,

  token_version       INT NOT NULL DEFAULT 0,  -- bump this on password reset/logout-all

  last_login_at       DATETIME NULL,

  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),

  UNIQUE KEY uq_users_email_norm (email_norm),
  UNIQUE KEY uq_users_phone (phone_e164),

  KEY idx_users_role (role),
  KEY idx_users_active (is_active)
  -- NOTE: Removed CHECK constraint for compatibility with older MySQL versions
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- OTP/TOKEN TABLE (Forgot Password + optional verify email/phone)
-- One table supports multiple auth flows via purpose.
-- Store only hashes, never the raw OTP/token.
-- ------------------------------------------------------------
CREATE TABLE auth_otps (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  user_id       BIGINT UNSIGNED NULL,  -- may be NULL if user not resolved yet
  purpose       ENUM('RESET_PASSWORD','VERIFY_EMAIL','VERIFY_PHONE') NOT NULL,
  channel       ENUM('EMAIL','SMS') NOT NULL,

  sent_to       VARCHAR(190) NOT NULL, -- email or phone string used
  code_hash     CHAR(64) NOT NULL,     -- SHA-256 hex of OTP/token

  issued_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at    DATETIME NOT NULL,
  consumed_at   DATETIME NULL,

  attempts      INT NOT NULL DEFAULT 0,
  max_attempts  INT NOT NULL DEFAULT 5,

  request_ip    VARCHAR(64) NULL,
  user_agent    VARCHAR(255) NULL,

  PRIMARY KEY (id),

  KEY idx_otps_lookup (sent_to, purpose, expires_at),
  KEY idx_otps_user (user_id, purpose, expires_at),

  CONSTRAINT fk_otps_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- PENDING REGISTRATIONS (store unverified users until OTP confirm)
-- ------------------------------------------------------------
CREATE TABLE pending_registrations (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  full_name       VARCHAR(120) NOT NULL,

  email           VARCHAR(190) NOT NULL,
  email_norm      VARCHAR(190)
                  GENERATED ALWAYS AS (LOWER(email)) STORED,

  phone_e164      VARCHAR(20) NULL,

  password_hash   VARCHAR(255) NOT NULL,
  role            ENUM('FARMER','DISTRIBUTOR','RETAILER','CONSUMER') NOT NULL,

  otp_hash        CHAR(64) NOT NULL,
  otp_expires_at  DATETIME NOT NULL,
  otp_attempts    INT NOT NULL DEFAULT 0,
  otp_max_attempts INT NOT NULL DEFAULT 5,
  issued_at       DATETIME NOT NULL,

  request_ip      VARCHAR(64) NULL,
  user_agent      VARCHAR(255) NULL,

  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),

  UNIQUE KEY uq_pending_email_norm (email_norm),
  UNIQUE KEY uq_pending_phone (phone_e164)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- REFRESH TOKENS (optional but recommended for real sessions)
-- Store hash of refresh token. Supports rotation.
-- If you want simplest MVP, you can skip this and use only short-lived JWT.
-- ------------------------------------------------------------
CREATE TABLE auth_refresh_tokens (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id          BIGINT UNSIGNED NOT NULL,

  token_hash       CHAR(64) NOT NULL,         -- SHA-256 hex of refresh token
  family_id        CHAR(36) NOT NULL,         -- UUID string for rotation family

  issued_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at       DATETIME NOT NULL,
  revoked_at       DATETIME NULL,

  replaced_by_id   BIGINT UNSIGNED NULL,      -- points to new token row when rotated

  ip_addr          VARCHAR(64) NULL,
  user_agent       VARCHAR(255) NULL,

  PRIMARY KEY (id),

  UNIQUE KEY uq_refresh_token_hash (token_hash),
  KEY idx_refresh_user_active (user_id, revoked_at, expires_at),
  KEY idx_refresh_family (family_id),

  CONSTRAINT fk_refresh_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

  CONSTRAINT fk_refresh_replaced
    FOREIGN KEY (replaced_by_id) REFERENCES auth_refresh_tokens(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- AUDIT LOGS (highly recommended even in auth phase)
-- ------------------------------------------------------------
CREATE TABLE audit_logs (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT UNSIGNED NULL,

  action        VARCHAR(80) NOT NULL,       -- AUTH_REGISTER, AUTH_LOGIN, AUTH_RESET_REQUEST, AUTH_RESET_DONE
  meta_json     JSON NULL,

  ip_addr       VARCHAR(64) NULL,
  user_agent    VARCHAR(255) NULL,

  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_audit_actor (actor_user_id),
  KEY idx_audit_action (action),
  KEY idx_audit_created (created_at),

  CONSTRAINT fk_audit_actor
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ======================================================================
-- NEW TABLES (Crop Registration → Batch Creation → Blockchain Record → QR)
-- NOTE: All new tables are appended at the bottom (as requested).
-- ======================================================================

-- ------------------------------------------------------------
-- CROPS (Step 1 Basic Info: Crop Name + Category)
-- Each crop belongs to a FARMER user.
-- ------------------------------------------------------------
CREATE TABLE crops (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  farmer_user_id  BIGINT UNSIGNED NOT NULL,

  crop_name       VARCHAR(160) NOT NULL,     -- e.g., Organic Tomatoes
  category        VARCHAR(80)  NOT NULL,     -- e.g., Vegetables

  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_crops_farmer (farmer_user_id),
  KEY idx_crops_name (crop_name),

  CONSTRAINT fk_crops_farmer
    FOREIGN KEY (farmer_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- BATCHES (Step 1 + Step 2 Details + Used by "My Products" list)
-- Stores: quantity, harvest date, seed variety, fertilizers, irrigation, organic flag.
-- Status maps directly to UI: active / pending / sold.
-- ------------------------------------------------------------
CREATE TABLE batches (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  crop_id            BIGINT UNSIGNED NOT NULL,
  created_by_user_id BIGINT UNSIGNED NOT NULL,   -- should be the farmer

  batch_code         VARCHAR(80) NOT NULL,        -- e.g., B-2023-001 (unique)
  quantity_kg        DECIMAL(12,3) NOT NULL DEFAULT 0.000,
  harvest_date       DATE NULL,

  seed_variety       VARCHAR(120) NULL,           -- e.g., Hybrid F1
  fertilizers_used   VARCHAR(200) NULL,           -- e.g., NPK 20-20-20

  irrigation_method  ENUM('DRIP','SPRINKLER','FLOOD','RAINFED') NULL,
  is_organic         TINYINT(1) NOT NULL DEFAULT 0,

  -- UI statuses: active, pending, sold
  status             ENUM('ACTIVE','PENDING','SOLD') NOT NULL DEFAULT 'PENDING',

  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uq_batches_batch_code (batch_code),
  KEY idx_batches_crop (crop_id),
  KEY idx_batches_creator (created_by_user_id),
  KEY idx_batches_status (status, created_at),

  CONSTRAINT fk_batches_crop
    FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE RESTRICT,

  CONSTRAINT fk_batches_creator
    FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- BATCH HASH SNAPSHOTS (for DB↔Blockchain verification)
-- Canonical JSON + hash stored here; blockchain stores hash_hex.
-- ------------------------------------------------------------
CREATE TABLE batch_hash_snapshots (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_id         BIGINT UNSIGNED NOT NULL,

  version          INT NOT NULL DEFAULT 1,
  hash_algo        VARCHAR(20) NOT NULL DEFAULT 'SHA256',
  hash_hex         CHAR(64) NOT NULL,

  canonical_json   JSON NOT NULL,                 -- stable canonical payload used for hashing
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uq_batch_hash_version (batch_id, version),
  UNIQUE KEY uq_batch_hash_hex (hash_hex),
  KEY idx_batch_hash_batch (batch_id),

  CONSTRAINT fk_batch_hash_batch
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- BLOCKCHAIN EVENTS (store the "Blockchain Record" shown in UI)
-- For now: BatchCreated. Later: TransferAccepted, ReceivedByRetailer.
-- ------------------------------------------------------------
CREATE TABLE blockchain_events (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  event_name       ENUM('BatchCreated','TransferAccepted','ReceivedByRetailer') NOT NULL,
  batch_id         BIGINT UNSIGNED NOT NULL,

  chain_id         VARCHAR(40) NOT NULL,          -- e.g., sepolia, polygon-amoy
  tx_hash          VARCHAR(100) NOT NULL,
  block_number     BIGINT UNSIGNED NULL,

  payload_hash_hex CHAR(64) NOT NULL,             -- must match batch_hash_snapshots.hash_hex
  status           ENUM('SUBMITTED','CONFIRMED','FAILED') NOT NULL DEFAULT 'SUBMITTED',

  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at     DATETIME NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uq_chain_tx (chain_id, tx_hash),
  KEY idx_chain_batch_event (batch_id, event_name),
  KEY idx_chain_status (status),

  CONSTRAINT fk_chain_batch
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- BATCH TRANSFERS (assign batches between roles)
-- ------------------------------------------------------------
CREATE TABLE batch_transfers (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  batch_id         BIGINT UNSIGNED NOT NULL,
  from_user_id     BIGINT UNSIGNED NOT NULL,
  to_user_id       BIGINT UNSIGNED NOT NULL,

  status           ENUM('ASSIGNED','IN_TRANSIT','PICKED_UP','REJECTED','CANCELLED') NOT NULL DEFAULT 'ASSIGNED',

  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_transfer_batch (batch_id),
  KEY idx_transfer_to_user (to_user_id),
  KEY idx_transfer_status (status),

  CONSTRAINT fk_transfer_batch
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE,

  CONSTRAINT fk_transfer_from
    FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE RESTRICT,

  CONSTRAINT fk_transfer_to
    FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- BATCH SCAN EVENTS (verification/audit)
-- ------------------------------------------------------------
CREATE TABLE batch_scan_events (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_id      BIGINT UNSIGNED NOT NULL,
  actor_user_id BIGINT UNSIGNED NOT NULL,
  actor_role    VARCHAR(32) NOT NULL,
  event_type    VARCHAR(64) NOT NULL,
  result        VARCHAR(16) NOT NULL,
  meta_json     TEXT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_batch (batch_id),
  KEY idx_actor (actor_user_id),

  CONSTRAINT fk_scan_batch
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE,

  CONSTRAINT fk_scan_actor
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- QR CODES (stored QR payload, used in "QR Code" screen)
-- NOTE: Workflow rule says Retailer generates QR; enforce in backend (role check).
-- ------------------------------------------------------------
CREATE TABLE qr_codes (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  batch_id             BIGINT UNSIGNED NOT NULL,
  generated_by_user_id BIGINT UNSIGNED NOT NULL,  -- should be retailer (enforced in API)
  qr_payload           VARCHAR(512) NOT NULL,      -- what your QR encodes (batch + nonce + signature)
  is_active            TINYINT(1) NOT NULL DEFAULT 1,

  consumer_scan_count  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  distributor_scan_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  retailer_scan_count  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  last_scanned_at      DATETIME NULL,
  last_scanned_by_user_id BIGINT UNSIGNED NULL,
  last_scanned_role    VARCHAR(32) NULL,

  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uq_qr_payload (qr_payload),
  KEY idx_qr_batch (batch_id),
  KEY idx_qr_generated_by (generated_by_user_id),
  KEY idx_qr_last_scanned_at (last_scanned_at),
  KEY idx_qr_last_scanned_by (last_scanned_by_user_id),

  CONSTRAINT fk_qr_batch
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE,

  CONSTRAINT fk_qr_generated_by
    FOREIGN KEY (generated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- BATCH LOCATION UPDATES (distributor tracking)
-- ------------------------------------------------------------
CREATE TABLE batch_location_updates (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_id      BIGINT UNSIGNED NOT NULL,
  actor_user_id BIGINT UNSIGNED NOT NULL,

  location_text VARCHAR(200) NOT NULL,
  temperature_c DECIMAL(6,2) NULL,
  remarks       VARCHAR(255) NULL,

  recorded_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_loc_batch (batch_id, recorded_at),
  KEY idx_loc_actor (actor_user_id, recorded_at),

  CONSTRAINT fk_loc_batch
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE,

  CONSTRAINT fk_loc_actor
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
