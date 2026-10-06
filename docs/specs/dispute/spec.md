# Spec: dispute

> Auto-extracted by spec-miner. Last mined: 2026-10-06.
> Source: FileDisputeUseCase.java, RespondToDisputeUseCase.java, ReviewDisputeUseCase.java, DisputeWindowPolicy.java, AutoCompleteScheduler.java
> Last verified: 2026-10-06 (commit d985aff)

---

### Requirement: File Dispute Within Category Policy Window
<!-- id: FileDisputeUseCase.fileDispute -->
<!-- entities: Dispute, DisputeEvidence, Ticket -->
<!-- enforced: FileDisputeUseCase.fileDispute() -->

The system SHALL allow the buyer of an AWAITING_USE ticket to open a formal dispute with mandatory reason and evidence attachments, provided the current timestamp is strictly within the category dispute window computed by the applicable DisputeWindowPolicy.

#### Scenario: Buyer Files Dispute Within Window
<!-- test: FileDisputeUseCaseTest.testFileDisputeSuccess() -->
- **WHEN** the buyer submits a dispute for an AWAITING_USE ticket before the window closes, with reason INVALID_CODE and proof screenshot
- **THEN** a Dispute is created with status OPEN, evidence is stored via FileStorageService, and ticket transitions to DISPUTE_UNDER_REVIEW

#### Scenario: Dispute Attempt After Window Expiration
<!-- test: FileDisputeUseCaseTest.testFileDisputePastDeadlineThrows() -->
- **WHEN** a buyer attempts to file a dispute after the window closing instant has passed
- **THEN** the system rejects the filing with BusinessRuleViolationException and leaves the ticket untouched

---

### Requirement: Compute Dynamic Dispute Window Boundary
<!-- id: DisputeWindowPolicy.computeWindowClose -->
<!-- entities: DisputeWindowPolicy, TicketType -->
<!-- enforced: DisputeWindowPolicy.computeWindowClose() -->

The system SHALL determine the closing instant of a dispute window as the earlier instant between (transferredAt + windowDuration) and eventStartAt, using category-specific durations (MOVIE: 2h, CONCERT: 24h, BUS: 3h).

#### Scenario: Normal Window Duration Applies
<!-- test: DisputeWindowPolicyTest.testNormalWindowBeforeEvent() -->
- **WHEN** a MOVIE ticket is transferred at 10:00 and the movie starts at 15:00
- **THEN** the window closes at 12:00 (10:00 + 2h)

#### Scenario: Event Start Truncates Window Duration
<!-- test: DisputeWindowPolicyTest.testEventStartClampsWindow() -->
- **WHEN** a CONCERT ticket (24h window) is transferred at 18:00 and the concert starts at 19:30
- **THEN** the window closes at 19:30 (eventStartAt), preventing post-event fraudulent disputes

---

### Requirement: Submit Seller Dispute Response
<!-- id: RespondToDisputeUseCase.respond -->
<!-- entities: Dispute, DisputeEvidence -->
<!-- enforced: RespondToDisputeUseCase.respond() -->

The system SHALL permit the seller to submit a counter-explanation and supporting evidence within 48 hours of dispute opening, updating dispute status to SELLER_RESPONDED.

#### Scenario: Seller Responds Within Deadline
<!-- test: RespondToDisputeUseCaseTest.testSellerResponseSuccess() -->
- **WHEN** the ticket's seller submits rebuttal text and verification files while dispute status is OPEN and deadline has not passed
- **THEN** the dispute status updates to SELLER_RESPONDED and evidence is appended to the case file

#### Scenario: Response Attempt Past 48-Hour Deadline
<!-- test: RespondToDisputeUseCaseTest.testSellerResponsePastDeadlineThrows() -->
- **WHEN** the seller attempts to submit a response after the 48-hour sellerResponseDeadline
- **THEN** the system throws BusinessRuleViolationException

---

### Requirement: Admin Adjudicate Dispute Resolution
<!-- id: ReviewDisputeUseCase.resolve -->
<!-- entities: Dispute, EscrowTransaction, Deposit -->
<!-- triggers: DisputeResolvedEvent -->
<!-- enforced: ReviewDisputeUseCase.resolve() -->

The system SHALL require an administrator to evaluate evidence and execute a formal ruling (BUYER_WINS, SELLER_WINS, SPLIT, or WITHDRAWN), executing corresponding fund dispersals and publishing DisputeResolvedEvent.

#### Scenario: Ruling in Favor of Buyer
<!-- test: ReviewDisputeUseCaseTest.testResolveBuyerWins() -->
- **WHEN** admin rules BUYER_WINS on an open dispute
- **THEN** buyer receives full refund from escrow, seller deposit is forfeited to compensate buyer/platform, and DisputeResolvedEvent(BUYER_WINS) is published

#### Scenario: Ruling in Favor of Seller
<!-- test: ReviewDisputeUseCaseTest.testResolveSellerWins() -->
- **WHEN** admin rules SELLER_WINS on an open dispute
- **THEN** seller receives escrow payout, seller deposit is unlocked, and DisputeResolvedEvent(SELLER_WINS) is published

---

### Requirement: Auto-Complete Uncontested Tickets and Expire Overdue Responses
<!-- id: AutoCompleteScheduler.autoCompleteExpiredTickets -->
<!-- entities: Ticket, Dispute -->
<!-- enforced: AutoCompleteScheduler.autoCompleteExpiredTickets() -->

The system SHALL run a scheduled job every 10 minutes that automatically completes any AWAITING_USE ticket whose dispute window has expired with zero open disputes, while expiring overdue seller responses every hour.

#### Scenario: Ticket Without Dispute Auto-Completes
<!-- test: AutoCompleteSchedulerTest.testAutoCompleteEligibleTicket() -->
- **WHEN** a ticket in AWAITING_USE has passed its dispute window close time and has no record in DisputeRepository
- **THEN** ticket transitions to AUTO_COMPLETE and releaseFundsUseCase is called to payout seller and refund deposit

#### Scenario: Active Dispute Blocks Auto-Completion
<!-- test: AutoCompleteSchedulerTest.testActiveDisputeBlocksAutoComplete() -->
- **WHEN** a ticket in AWAITING_USE has passed its window close time but has an OPEN dispute
- **THEN** the scheduler bypasses the ticket, leaving funds securely in escrow pending admin review

---

### Invariant: Dispute Window Clamped to Event Start
<!-- entities: DisputeWindowPolicy -->
<!-- enforced: DisputeWindowPolicy.computeWindowClose() -->
<!-- verified_by: DisputeWindowPolicyTest.testWindowNeverExceedsEventStart() -->

The computed dispute window closing instant SHALL NEVER occur after eventStartAt, guaranteeing that once an event has begun, no new dispute can be initiated against the ticket.

> Last verified: 2026-10-06 (commit d985aff)

---

### Invariant: Mutual Exclusion of Escrow Release During Dispute
<!-- entities: Ticket, Dispute, EscrowTransaction -->
<!-- enforced: AutoCompleteScheduler.autoCompleteExpiredTickets() -->
<!-- verified_by: AutoCompleteSchedulerTest.testNoReleaseWhileDisputed() -->

An escrow transaction SHALL NEVER be released to the seller while a dispute associated with that ticket exists in OPEN or SELLER_RESPONDED status.

> Last verified: 2026-10-06 (commit d985aff)
