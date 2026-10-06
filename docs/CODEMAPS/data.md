<!-- Generated: 2026-10-06 | Files scanned: 105 | Token estimate: ~850 -->
# Database Schema & Data Models

## Migration History (`src/main/resources/db/migration/`)

| Version | Migration Script | Purpose |
|---|---|---|
| `V1` | `V1__init_schema.sql` | Initial schema: `users`, `tickets`, `escrow_transactions`, `deposits`, `disputes`, `dispute_evidences`, `user_reputation_scores` |
| `V2` | `V2__add_version_to_users.sql` | Adds `@Version` optimistic locking column `version BIGINT DEFAULT 0` to `users` |
| `V3` | `V3__create_user_starred_tickets.sql` | Creates `user_starred_tickets` table for buyer watchlists with unique index `(user_id, ticket_id)` |
| `V4` | `V4__add_timeout_and_gate_dispute_fields.sql` | Adds `escrowed_at`, `received_at`, and `TECHNICAL_ISSUE` enum state to `tickets` for timeout policies |

## Primary Database Tables & Schemas

### 1. `users`
- Columns: `id` (PK, AUTO_INCREMENT), `email` (UNIQUE), `password_hash`, `full_name`, `phone_number`, `role` (`USER`,`ADMIN`), `status` (`ACTIVE`,`PENDING_VERIFICATION`,`SUSPENDED`,`BANNED`), `avatar_storage_key`, `bank_name`, `bank_account_number`, `bank_account_holder`, `version`, `created_at`, `updated_at`.

### 2. `tickets`
- Columns: `id` (PK), `seller_id` (BIGINT, unconstrained), `buyer_id` (BIGINT, nullable), `title`, `ticket_type` (`MOVIE`,`BUS`,`CONCERT`), `price` (DECIMAL), `status` (`DRAFT`,`LISTED`,`ESCROWED`,`DELIVERED`,`COMPLETED`,`DISPUTED`,`CANCELLED`,`EXPIRED`,`TECHNICAL_ISSUE`), `public_image_key`, `secret_image_key`, `escrowed_at`, `received_at`, `details` (JSON / text attributes), `created_at`, `updated_at`.

### 3. `escrow_transactions`
- Columns: `id` (PK), `ticket_id` (BIGINT), `buyer_id` (BIGINT), `seller_id` (BIGINT), `amount` (DECIMAL), `status` (`HELD`,`RELEASED`,`REFUNDED`,`FORFEITED`), `gateway_reference`, `gateway_payment_url`, `created_at`, `updated_at`.

### 4. `deposits`
- Columns: `id` (PK), `ticket_id` (BIGINT), `seller_id` (BIGINT), `deposit_amount` (DECIMAL, 50% of price), `status` (`PENDING`,`LOCKED`,`RELEASED`,`FORFEITED`), `created_at`, `updated_at`.

### 5. `disputes` & `dispute_evidences`
- `disputes`: `id` (PK), `ticket_id` (BIGINT), `opened_by_user_id` (BIGINT), `seller_id` (BIGINT), `reason` (`INVALID_CODE`,`EVENT_CANCELLED`,`SELLER_NO_SHOW`), `status` (`OPEN`,`SELLER_RESPONDED`,`RESOLVED`,`AUTO_RESOLVED`), `resolution` (`BUYER_WINS`,`SELLER_WINS`,`SPLIT`,`WITHDRAWN`), `created_at`, `updated_at`.
- `dispute_evidences`: `id` (PK), `dispute_id` (FK to `disputes.id`), `uploaded_by_user_id` (BIGINT), `evidence_type` (`IMAGE`,`VIDEO`,`DOCUMENT`), `storage_key`, `created_at`.

### 6. `user_reputation_scores`
- Columns: `id` (PK), `user_id` (BIGINT, UNIQUE), `score` (DECIMAL, 0.00..100.00, default 100.00), `total_transactions`, `successful_transactions`, `disputes_won`, `disputes_lost`, `cancelled_listings`, `version`, `updated_at`.

### 7. `user_starred_tickets`
- Columns: `id` (PK), `user_id` (BIGINT), `ticket_id` (BIGINT), `starred_at`. Unique constraint: `(user_id, ticket_id)`.

## Inter-Module Relationship Rules
- **No Foreign Keys across modules:** `ticket_id`, `buyer_id`, `seller_id` in payment, dispute, reputation are plain `BIGINT` without DB foreign key constraints.
- **Intra-Module FKs allowed:** `dispute_evidences.dispute_id` -> `disputes.id` (same dispute module).
- **Concurrency control:** `users` and `user_reputation_scores` use JPA `@Version` for optimistic concurrency.
