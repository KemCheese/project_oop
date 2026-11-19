# Spec: ticket

> Auto-extracted by spec-miner. Last mined: 2026-10-06.
> Source: TicketStateMachineService.java, ValidateTicketService.java, TransferTicketService.java, DeliveryTimeoutScheduler.java, TicketController.java, StarredTicketUseCase.java
> Last verified: 2026-10-06 (commit d985aff)

---

### Requirement: Validate and Create Ticket Listing
<!-- id: ValidateTicketService.validate -->
<!-- entities: Ticket, TicketType -->
<!-- enforced: ValidateTicketService.validate() -->

The system SHALL validate ticket listing details according to ticket type (MOVIE, BUS, CONCERT), require both a public preview image and a secret check-in image, verify price is greater than zero, and persist the ticket in LISTED status.

#### Scenario: Valid Movie Ticket Listing
<!-- test: TicketControllerTest.testCreateMovieTicketSuccess() -->
- **WHEN** a seller submits a MOVIE ticket with title, cinema name, showtime, price > 0, quantity equal to seat list size, and 2 image files
- **THEN** both images are stored via FileStorageService, details are serialized to JSON, and the ticket is created with status LISTED

#### Scenario: Seat Count Mismatch
<!-- test: ValidateTicketServiceTest.testSeatMismatchThrows() -->
- **WHEN** a seller submits quantity = 2 but provides 3 seat identifiers
- **THEN** the system rejects the submission with BusinessRuleViolationException and does not persist the ticket

---

### Requirement: Enforce State Machine Transitions
<!-- id: TicketStateMachineService.transition -->
<!-- entities: Ticket, TicketStatus -->
<!-- enforced: TicketStateMachineService.transition() -->

The system SHALL restrict all ticket status changes strictly according to the defined lifecycle graph (LISTED -> PAYMENT_ESCROWED -> DEPOSIT_LOCKED -> TRANSFERRED -> AWAITING_USE -> AUTO_COMPLETE / DISPUTE_UNDER_REVIEW), rejecting any invalid or skipping transitions.

#### Scenario: Valid Forward Transition
<!-- test: TicketStateMachineServiceTest.testValidTransition() -->
- **WHEN** a ticket in LISTED status transitions to PAYMENT_ESCROWED upon buyer payment
- **THEN** the ticket status updates to PAYMENT_ESCROWED and updated timestamp is refreshed

#### Scenario: Illegal Status Transition Attempt
<!-- test: TicketStateMachineServiceTest.testIllegalTransitionThrows() -->
- **WHEN** any service attempts to transition a ticket directly from LISTED to TRANSFERRED without escrow or deposit
- **THEN** the system throws IllegalTicketTransitionException and prevents state modification

---

### Requirement: Transfer Ticket and Unlock Secret QR Image
<!-- id: TransferTicketService.transferTicket -->
<!-- entities: Ticket, StoredFileRef -->
<!-- enforced: TransferTicketService.transferTicket() -->

The system SHALL transition a ticket from DEPOSIT_LOCKED to TRANSFERRED when the seller triggers the transfer action, recording transferredAt timestamp and granting the buyer access to view the secret check-in image.

#### Scenario: Seller Confirms Transfer
<!-- test: TransferTicketServiceTest.testTransferSuccess() -->
- **WHEN** the seller of a DEPOSIT_LOCKED ticket requests ticket transfer
- **THEN** ticket status transitions to TRANSFERRED, transferredAt is set to the current instant, and the secret QR code is made available to the buyer

#### Scenario: Transfer Attempt Prior to Seller Deposit Lock
<!-- test: TransferTicketServiceTest.testTransferWithoutDepositThrows() -->
- **WHEN** a transfer is requested on a ticket still in PAYMENT_ESCROWED status
- **THEN** the system refuses the transfer with BusinessRuleViolationException

---

### Requirement: Buyer Confirmation and Technical Issue Reporting
<!-- id: TransferTicketService.confirmReceipt -->
<!-- entities: Ticket, TicketStatus -->
<!-- enforced: TransferTicketService.confirmReceipt() -->

The system SHALL allow the buyer of a TRANSFERRED ticket to confirm valid receipt (transitioning to AWAITING_USE with receivedAt = now) or flag a technical issue with the secret QR image (transitioning to TECHNICAL_ISSUE).

#### Scenario: Buyer Confirms Valid Ticket Received
<!-- test: TransferTicketServiceTest.testConfirmReceipt() -->
- **WHEN** the authenticated buyer confirms receipt of a TRANSFERRED ticket
- **THEN** status transitions to AWAITING_USE, receivedAt is stamped, and dispute window countdown begins

#### Scenario: Buyer Reports Unreadable or Corrupted QR
<!-- test: TransferTicketServiceTest.testReportTechnicalIssue() -->
- **WHEN** the buyer reports that the revealed check-in image is unreadable
- **THEN** status transitions to TECHNICAL_ISSUE, pausing auto-completion and notifying seller to re-upload

---

### Requirement: Auto-Confirm Delivery on Inaction Timeout
<!-- id: DeliveryTimeoutScheduler.scanDeliveryTimeouts -->
<!-- entities: Ticket, TicketStatus -->
<!-- enforced: DeliveryTimeoutScheduler.scanDeliveryTimeouts() -->

The system SHALL run a scheduled scanner every minute that automatically transitions any TRANSFERRED ticket older than 30 minutes to AWAITING_USE if the buyer has neither confirmed receipt nor reported a technical issue.

#### Scenario: Overdue Transferred Ticket Auto-Confirmed
<!-- test: DeliveryTimeoutSchedulerTest.testOverdueTransferredTicketAutoConfirmed() -->
- **WHEN** a ticket remains in TRANSFERRED status with transferredAt older than 30 minutes and status is not TECHNICAL_ISSUE
- **THEN** the scheduler automatically calls autoConfirmReceipt, transitioning status to AWAITING_USE and stamping receivedAt

---

### Requirement: Toggle Starred Ticket Watchlist
<!-- id: StarredTicketUseCase.toggleStar -->
<!-- entities: StarredTicket, Ticket -->
<!-- enforced: StarredTicketUseCase.toggleStar() -->

The system SHALL allow authenticated users to add or remove tickets from their personal watchlist via an idempotent toggle operation.

#### Scenario: User Stars an Unstarred Ticket
<!-- test: StarredTicketUseCaseTest.testToggleStarAdd() -->
- **WHEN** an authenticated user calls toggle star for a ticket not currently in their watchlist
- **THEN** a StarredTicket record is persisted and response returns isStarred = true

#### Scenario: User Unstars an Already Starred Ticket
<!-- test: StarredTicketUseCaseTest.testToggleStarRemove() -->
- **WHEN** an authenticated user calls toggle star for an already starred ticket
- **THEN** the corresponding StarredTicket record is deleted and response returns isStarred = false

---

### Requirement: Detect Schedule Conflict with Soft Warning
<!-- id: TicketScheduleService.findConflictingTickets -->
<!-- entities: Ticket, TimeSlot, ConflictingTicketInfo -->
<!-- enforced: TicketScheduleService.findConflictingTickets(), WalletPaymentController.payEscrowWithWallet(), PaymentController.buyTicket(), PaymentController.initEscrow() -->

The system SHALL require `eventEndAt > eventStartAt` on new ticket listings (falling back polymorphically to `TicketAdapter.defaultEventDuration()` for legacy tickets without `eventEndAt`), detect half-open `[start, end)` overlaps across the buyer's 6 active ownership statuses (`PAYMENT_ESCROWED`, `DEPOSIT_LOCKED`, `TRANSFERRED`, `AWAITING_USE`, `AUTO_COMPLETE`, `RESOLVED_SELLER_WINS`), trigger a 2-mode Pop-up Modal (`#overlapConfirmModal`) on ticket detail, and require explicit `confirmOverlap=true` at checkout endpoints.

#### Scenario: Overlapping Active Ticket Triggers Soft Warning & Guard
<!-- test: ScheduleConflictAndReminderFlowTest.testScheduleOverlapSoftWarningAndGuards() -->
- **WHEN** a buyer owns an active ticket `[13:00, 15:00)` and requests checkout on an overlapping ticket `[14:00, 16:00)` without `confirmOverlap=true`
- **THEN** the detail view omits static banners and sets `data-has-conflict="true"` on `#overlapConfirmModal`, Wallet/VietQR checkout redirects with `?overlapWarning=true` without charging, JSON API returns HTTP 400, and checkout with `confirmOverlap=true` succeeds while adjacent `[15:00, 19:00)` triggers no warning

---

### Requirement: 24-Hour Idempotent Event Reminder & Expired QR Box Hiding
<!-- id: TicketReminderService.claimAndNotify -->
<!-- entities: Ticket, TicketUpcomingReminderEvent, TicketResponse -->
<!-- enforced: TicketReminderScheduler.scanAndSendUpcomingReminders(), TransferTicketService.markDepositLocked(), TicketController.detail() -->

The system SHALL run a scheduled job dispatching `TicketUpcomingReminderEvent` at most once per ticket when `0 < eventStartAt - now <= leadHours` (updating `reminded_24h_at` atomically), trigger immediate early reminder for last-minute purchases when seller locks deposit, and hide `.qr-unlocked-box` / `.qr-locked-box` on `ticket/detail.html` for event-expired, terminal, or 30-minute QR-expired tickets.

#### Scenario: Idempotent 24h Reminder and Expired QR Hiding
<!-- test: ScheduleConflictAndReminderFlowTest.test24hReminderSchedulerAndLastMinuteDepositTrigger(), ScheduleConflictAndReminderFlowTest.testExpiredTicketHidesGreenQrBox() -->
- **WHEN** `TicketReminderScheduler` scans twice or `markDepositLocked` executes within 24h of `eventStartAt`, and when an expired `AUTO_COMPLETE` ticket is rendered
- **THEN** exactly one `UPCOMING_EVENT_REMINDER` notification is dispatched per ticket and the rendered detail page contains neither `class="qr-unlocked-box"` nor `class="qr-locked-box"`

---

### Invariant: Allowed State Transition Closure
<!-- entities: Ticket, TicketStatus -->
<!-- enforced: TicketStateMachineService.ALLOWED -->
<!-- verified_by: TicketStateMachineServiceTest.testAllowedTransitionsExhaustive() -->

The terminal states AUTO_COMPLETE, RESOLVED_BUYER_WINS, RESOLVED_SELLER_WINS, and CANCELLED SHALL have an empty set of next allowed states, guaranteeing that completed, resolved, or cancelled tickets cannot be mutated.

> Last verified: 2026-10-06 (commit d985aff)

---

### Invariant: Zero Cross-Module Foreign Key Constraints
<!-- entities: Ticket -->
<!-- enforced: Ticket.java -->

The Ticket entity SHALL store buyerId and sellerId as raw Long scalars without JPA @ManyToOne or @OneToOne annotations to the User entity, maintaining absolute decoupling between the ticket and user modules.

> Last verified: 2026-10-06 (commit d985aff)
