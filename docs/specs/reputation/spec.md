# Spec: reputation

> Auto-extracted by spec-miner. Last mined: 2026-10-06.
> Source: UpdateReputationUseCase.java, UserReputationScore.java, ReputationRepository.java, ReputationController.java
> Last verified: 2026-10-06 (commit d985aff)

---

### Requirement: Reward Completed Transactions
<!-- id: UpdateReputationUseCase.onTicketTransactionCompleted -->
<!-- entities: UserReputationScore, TicketTransactionCompletedEvent -->
<!-- enforced: UpdateReputationUseCase.onTicketTransactionCompleted() -->

The system SHALL listen asynchronously to TicketTransactionCompletedEvent after commit and increment the reputation score of both the seller and the buyer by +2.00 points (capped at 100.00), while incrementing their total and successful transaction counters.

#### Scenario: Transaction Successfully Completed
<!-- test: UpdateReputationUseCaseTest.testTransactionCompletedAwardsPoints() -->
- **WHEN** a TicketTransactionCompletedEvent is published for seller (score 90) and buyer (score 85)
- **THEN** seller score becomes 92.00, buyer score becomes 87.00, and successfulTransactions increments for both

#### Scenario: Upper Bound Cap at 100.00
<!-- test: UpdateReputationUseCaseTest.testScoreCapAt100() -->
- **WHEN** a user with score 99.50 completes a transaction
- **THEN** their score is capped at 100.00 rather than 101.50

---

### Requirement: Adjudicate Dispute Reputation Adjustments
<!-- id: UpdateReputationUseCase.onDisputeResolved -->
<!-- entities: UserReputationScore, DisputeResolvedEvent -->
<!-- enforced: UpdateReputationUseCase.onDisputeResolved() -->

The system SHALL listen asynchronously to DisputeResolvedEvent after commit and penalize the losing party with a -15.00 point deduction (floor 0.00) while awarding the winning party +1.00 point (cap 100.00).

#### Scenario: Buyer Wins Dispute
<!-- test: UpdateReputationUseCaseTest.testBuyerWinsDisputePenalizesSeller() -->
- **WHEN** DisputeResolvedEvent with outcome BUYER_WINS is received for seller (score 80) and buyer (score 90)
- **THEN** seller score drops to 65.00 with disputesLost incremented, and buyer score increases to 91.00 with disputesWon incremented

#### Scenario: Seller Wins Dispute
<!-- test: UpdateReputationUseCaseTest.testSellerWinsDisputePenalizesBuyer() -->
- **WHEN** DisputeResolvedEvent with outcome SELLER_WINS is received for seller (score 80) and buyer (score 90)
- **THEN** seller score increases to 81.00, and buyer score drops to 75.00

#### Scenario: Lower Bound Floor at 0.00
<!-- test: UpdateReputationUseCaseTest.testScoreFloorAtZero() -->
- **WHEN** a user with score 10.00 loses a dispute (-15 points)
- **THEN** their score is clamped to 0.00 rather than negative

---

### Requirement: Penalize Deposit Timeout and Listing Cancellation
<!-- id: UpdateReputationUseCase.recordDepositTimeout -->
<!-- entities: UserReputationScore -->
<!-- enforced: UpdateReputationUseCase.recordDepositTimeout() -->

The system SHALL deduct 15.00 points when a seller abandons a transaction past the deposit timeout, and deduct 10.00 points when a seller cancels an active listing.

#### Scenario: Seller Inaction Deposit Timeout
<!-- test: UpdateReputationUseCaseTest.testRecordDepositTimeout() -->
- **WHEN** DepositTimeoutScheduler triggers recordDepositTimeout for a delinquent seller
- **THEN** the seller's score is immediately reduced by 15.00 points (floor 0.00)

#### Scenario: Voluntary Listing Cancellation
<!-- test: UpdateReputationUseCaseTest.testRecordCancelledListing() -->
- **WHEN** a seller voluntarily cancels a listed ticket
- **THEN** the seller's score is reduced by 10.00 points and cancelledListings counter increments

---

### Requirement: Query Top Reputable Sellers with Clamped Limits
<!-- id: ReputationController.showTopSellers -->
<!-- entities: UserReputationScore -->
<!-- enforced: ReputationController.showTopSellers() -->

The system SHALL query the top reputable sellers ordered by score descending, clamping requested limit parameters to the safe range between 1 and 100.

#### Scenario: Normal Top Sellers Query
<!-- test: ReputationControllerTest.testTopSellersDefaultLimit() -->
- **WHEN** an unauthenticated visitor requests GET /reputation/top with limit=10
- **THEN** top 10 sellers are returned rendered in view "reputation/top"

#### Scenario: Excessive Limit Parameter Clamped
<!-- test: ReputationControllerTest.testTopSellersLimitClamped() -->
- **WHEN** a request arrives with limit=9999
- **THEN** the limit is clamped to 100, protecting the database from denial-of-service queries

---

### Invariant: Reputation Score Boundary [0.00, 100.00]
<!-- entities: UserReputationScore -->
<!-- enforced: UserReputationScore.setScore() -->
<!-- verified_by: UpdateReputationUseCaseTest.testClampScoreBoundary() -->

A user's reputation score SHALL ALWAYS stay within the strict bounds of 0.00 and 100.00 inclusive, regardless of penalties or rewards applied.

> Last verified: 2026-10-06 (commit d985aff)

---

### Invariant: Post-Commit Event Handling (No Transaction Leakage)
<!-- entities: UpdateReputationUseCase -->
<!-- enforced: UpdateReputationUseCase.java -->

All domain event listener methods in the reputation module SHALL specify @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT), ensuring failures in reputation logging can never rollback currency transactions.

> Last verified: 2026-10-06 (commit d985aff)
