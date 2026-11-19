<!-- Generated: 2026-10-07 | Files scanned: 241 | Token estimate: ~850 -->
# TicketPass System Architecture

## Overview
TicketPass is a Spring Boot Modular Monolith applying Hexagonal Architecture (Ports & Adapters) for a P2P secondary ticket marketplace with economic protection mechanisms (Escrow + 50% Seller Deposit + Dispute Window + E-Wallet Ledger + Multi-channel In-app Notifications).

## High-Level Topology

```
[ Client Browser / Guest / User / Admin ]
                  │
                  ▼
  ┌────────────────────────────────────────────────────────┐
  │         Spring Security (Session-based RBAC)           │
  │    Public: /tickets, /reputation, /login, /register    │
  │    Auth Required: /profile, /payment, /wallet,         │
  │                   /notifications, /disputes            │
  │    Admin Required: /admin/disputes/**                  │
  └──────────────────────────┬─────────────────────────────┘
                             │
  ┌──────────────────────────▼─────────────────────────────┐
  │                 Web Layer (Controllers)                │
  │ TicketCtrl | PaymentCtrl | DisputeCtrl | UserCtrl      │
  │ WalletCtrl | WalletPaymentCtrl | NotificationCtrl      │
  └───────┬──────────────┬──────────────┬────────────┬─────┘
          │              │              │            │
  ┌───────▼──────┐┌──────▼──────┐┌──────▼─────┐┌─────▼──────┐
  │ticket module ││payment mod  ││wallet mod  ││dispute mod │
  │StateMachine  ││Hold / Lock  ││Ledger/TopUp││File/Respond│
  │Auto-Delist   ││Release/Gate ││WalletGate  ││Review /Auto│
  └───────┬──────┘└──────┬──────┘└──────┬─────┘└─────┬──────┘
          │              │              │            │
          │ (Spring Events via ApplicationEventPublisher)
          │ ├── TicketTransactionCompletedEvent      │
          │ ├── DisputeResolvedEvent / DisputeOpened │
          │ ├── TicketExpiredEvent / ReceivedEvent   │
          │ ├── TicketUpcomingReminderEvent        │
          │ └── DepositTimeoutEvent                  │
          ▼                                          ▼
  ┌────────────────────────────────────────────────────────┐
  │          Async Event Observers (AFTER_COMMIT)          │
  │  reputation module        │  notification module       │
  │  UpdateReputationUseCase  │  NotificationEventListener │
  └──────────────────────────┬─────────────────────────────┘
                             │
                             ▼
  ┌────────────────────────────────────────────────────────┐
  │            Shared Infrastructure & Storage             │
  │ FileStorageService (LocalFileStorageAdapter -> ./data) │
  │ MySQL 8.0 (Flyway V1..V7) / H2 in-memory (Tests)       │
  └────────────────────────────────────────────────────────┘
```

## Module Boundaries & Ownership (8 Modules)

| Module | Core Responsibility | Patterns | Owner |
|---|---|---|---|
| **`ticket`** | Ticket lifecycle, 2-layer image handling, category adapters, expiration scheduler | State, Adapter, Repository | Dev A |
| **`web / layout`** | Master layout Thymeleaf, responsive styling, notification bell UI, `HomeController` | Composite View, SSR | Dev A |
| **`payment`** | MoMo sandbox escrow (100%), seller deposit lock (50%), commission, payout, deposit timeout | Strategy, Adapter, Repository | Dev B |
| **`dispute`** | Category-specific dispute window policies, evidence handling, arbitration console | Strategy, Repository | Dev B |
| **`user`** | Account registration, profile, security auth, starred watchlist (`user_starred_tickets`) | Repository, Adapter | Dev C |
| **`reputation`** | Reputation scores (0.00..100.00), idempotent async transaction listener (`AFTER_COMMIT`) | Observer, Optimistic Lock | Dev C |
| **`shared`** | Storage adapter, security config & handlers, global exception handling, shared domain events | Ports & Adapters | Dev C |
| **`wallet`** | Internal e-wallet ledger (`WalletTransaction`), balance tracking, top-up/withdraw, payment gateway | Adapter, Double-entry Ledger | Dev B & C |
| **`notification`**| Real-time in-app alerts, unread counter, navbar popover, multi-channel event listener | Observer, Repository | Dev C & A |

## 4 Core Architectural Principles (Scalability Rules)

1. **Zero Cross-Module JPA Relations:** Entities reference foreign domains via raw `Long entityId` only. Never use `@ManyToOne` or `@JoinColumn` across module boundaries.
2. **Asynchronous Decoupling via Domain Events:** Inter-module side effects (reputation score calculation, notification broadcast) execute via `@TransactionalEventListener(phase = AFTER_COMMIT)`.
3. **Storage Port & Adapter:** All file operations route through `FileStorageService` port (`LocalFileStorageAdapter`), isolating business logic from storage media.
4. **Centralized Defensive Security:** Session-based authentication, path traversal guards on file uploads, bank account data masking (last 4 digits only), and defense-in-depth against open redirects (CWE-601).

