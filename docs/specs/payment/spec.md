# Spec: payment

> Auto-extracted by spec-miner. Last mined: 2026-10-06.
> Source: HoldFundsUseCase.java, LockDepositUseCase.java, CommissionCalculatorService.java, TaxWithholdingService.java, ReleaseFundsUseCase.java, RefundBuyerUseCase.java, DepositTimeoutScheduler.java
> Last verified: 2026-10-06 (commit d985aff)

---

### Requirement: Hold Buyer Payment in Escrow
<!-- id: HoldFundsUseCase.hold -->
<!-- entities: EscrowTransaction, Ticket -->
<!-- enforced: HoldFundsUseCase.hold() -->

The system SHALL create an EscrowTransaction record with status HELD upon receiving successful confirmation from MoMo payment gateway, updating the associated ticket status to PAYMENT_ESCROWED and recording escrowedAt.

#### Scenario: Successful Buyer Payment Callback
<!-- test: HoldFundsUseCaseTest.testSuccessfulPaymentHoldsEscrow() -->
- **WHEN** MoMo gateway sends a successful return or IPN callback with resultCode = 0 for a valid ticket
- **THEN** an EscrowTransaction is stored with status HELD, ticket transitions to PAYMENT_ESCROWED, and escrowedAt is stamped

#### Scenario: Payment Initiation on Non-Listed Ticket
<!-- test: HoldFundsUseCaseTest.testInitiatePaymentOnNonListedTicketThrows() -->
- **WHEN** a buyer attempts to initiate escrow on a ticket whose status is not LISTED
- **THEN** the system throws BusinessRuleViolationException and aborts checkout

---

### Requirement: Lock 50% Seller Deposit
<!-- id: LockDepositUseCase.lockDeposit -->
<!-- entities: Deposit, Ticket -->
<!-- enforced: LockDepositUseCase.lockDeposit() -->

The system SHALL require the seller to lock exactly 50% of the ticket sale price as a security deposit, updating the deposit record to status LOCKED and advancing the ticket to DEPOSIT_LOCKED.

#### Scenario: Seller Locks Valid Deposit
<!-- test: LockDepositUseCaseTest.testLockDepositSuccess() -->
- **WHEN** the authenticated seller locks 50% of the ticket price for a PAYMENT_ESCROWED ticket
- **THEN** a Deposit record is created with status LOCKED and the ticket advances to DEPOSIT_LOCKED

#### Scenario: Unauthorized Seller Attempts Lock
<!-- test: LockDepositUseCaseTest.testLockDepositByWrongUserThrows() -->
- **WHEN** a user who is not the seller of the ticket attempts to lock deposit
- **THEN** the system rejects the operation with BusinessRuleViolationException

---

### Requirement: Calculate 3-Tier Platform Commission
<!-- id: CommissionCalculatorService.calculate -->
<!-- entities: CommissionTier, CommissionBreakdown -->
<!-- enforced: CommissionCalculatorService.calculate() -->

The system SHALL compute platform commission using a non-progressive table lookup based on ticket sale price (<1M: 7%, <5M: 5%, <10M: 3%, >=10M: 2% with 400.000d cap), enforcing a minimum platform fee of 5.000d and splitting total commission 50/50 between buyer and seller.

#### Scenario: Mid-Tier Ticket Commission Calculation
<!-- test: CommissionCalculatorServiceTest.testMidTierCalculation() -->
- **WHEN** commission is calculated for a ticket priced at 6.000.000 VND
- **THEN** the applied tier is UNDER_10M (3%), total commission is 180.000 VND, buyer pays 90.000 VND, and seller pays 90.000 VND

#### Scenario: High-Tier Ticket Cap Application
<!-- test: CommissionCalculatorServiceTest.testCapAppliedAbove10M() -->
- **WHEN** commission is calculated for a ticket priced at 30.000.000 VND (2% would be 600.000 VND)
- **THEN** total commission is capped at 400.000 VND, capApplied is true, and buyer/seller shares are 200.000 VND each

---

### Requirement: Release Funds and Complete Payout
<!-- id: ReleaseFundsUseCase.release -->
<!-- entities: EscrowTransaction, Deposit, Ticket -->
<!-- triggers: TicketTransactionCompletedEvent -->
<!-- enforced: ReleaseFundsUseCase.release() -->

The system SHALL execute a strictly ordered 6-step payout process (calculate commission, withhold tax, payout net to seller, refund seller deposit, transition ticket to terminal state, and publish TicketTransactionCompletedEvent).

#### Scenario: Successful Transaction Settlement
<!-- test: ReleaseFundsUseCaseTest.testSuccessfulFundsRelease() -->
- **WHEN** release is invoked for an eligible ticket with HELD escrow and LOCKED deposit
- **THEN** seller receives net payout, deposit is refunded, ticket reaches terminal state, and TicketTransactionCompletedEvent is published

#### Scenario: Idempotent Release on Already Released Escrow
<!-- test: ReleaseFundsUseCaseTest.testIdempotentReleaseDoesNothing() -->
- **WHEN** release is called for a ticket whose escrow transaction is already RELEASED
- **THEN** the use case exits early without duplicate payouts or events

---

### Requirement: Enforce Seller Deposit Timeout
<!-- id: DepositTimeoutScheduler.scanDepositTimeouts -->
<!-- entities: Ticket, EscrowTransaction -->
<!-- enforced: DepositTimeoutScheduler.scanDepositTimeouts() -->

The system SHALL run a scheduled job every minute that cancels any ticket in PAYMENT_ESCROWED older than 30 minutes, automatically executing a 100% refund to the buyer and penalizing the seller with a 15-point reputation deduction.

#### Scenario: 30-Minute Seller Deposit Timeout Triggered
<!-- test: DepositTimeoutSchedulerTest.testDepositTimeoutCancelsAndRefunds() -->
- **WHEN** a ticket has been in PAYMENT_ESCROWED for more than 30 minutes without seller deposit
- **THEN** the buyer is refunded 100%, ticket status is set to CANCELLED, and seller reputation is penalized by -15 points

---

### Invariant: Deposit Amount Exactly 50%
<!-- entities: Deposit, Ticket -->
<!-- enforced: LockDepositUseCase.lockDeposit() -->
<!-- verified_by: LockDepositUseCaseTest.testDepositAmountEqualsHalfPrice() -->

The deposit amount stored in any Deposit record SHALL always equal exactly 50% of the associated ticket sale price, computed using half-up decimal rounding.

> Last verified: 2026-10-06 (commit d985aff)

---

### Invariant: Commission Split Sum Integrity
<!-- entities: CommissionBreakdown -->
<!-- enforced: CommissionCalculatorService.calculate() -->
<!-- verified_by: CommissionCalculatorServiceTest.testBuyerSellerSumEqualsTotal() -->

For any computed commission breakdown, buyerShare plus sellerShare SHALL strictly equal totalCommission down to the exact single dong, with any rounding remainder allocated deterministically.

> Last verified: 2026-10-06 (commit d985aff)
