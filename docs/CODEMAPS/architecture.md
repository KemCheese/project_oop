<!-- Generated: 2026-10-06 | Files scanned: 105 | Token estimate: ~750 -->
# TicketPass System Architecture

## Overview
TicketPass is a Spring Boot Modular Monolith applying Hexagonal Architecture (Ports & Adapters) for a P2P secondary ticket marketplace with economic protection mechanisms (Escrow + 50% Seller Deposit + Dispute Window).

## High-Level Topology

```
[ Client Browser / Guest / User / Admin ]
                  │
                  ▼
  ┌────────────────────────────────────────────────────────┐
  │         Spring Security (Session-based RBAC)           │
  │    Public: /tickets, /reputation, /login, /register    │
  │    Auth Required: /profile, /payment, /disputes        │
  │    Admin Required: /admin/disputes/**                  │
  └──────────────────────────┬─────────────────────────────┘
                             │
  ┌──────────────────────────▼─────────────────────────────┐
  │                 Web Layer (Controllers)                │
  │  TicketCtrl | PaymentCtrl | DisputeCtrl | UserCtrl ... │
  └──────────┬───────────────────┬──────────────────┬──────┘
             │                   │                  │
  ┌──────────▼──────────┐ ┌──────▼──────────┐ ┌─────▼──────┐
  │    ticket module    │ │ payment module  │ │dispute mod │
  │ StateMachineService │ │ HoldFunds /     │ │File/Respond│
  │ Validate / Transfer │ │ Lock / Release  │ │Review /Auto│
  └──────────┬──────────┘ └──────┬──────────┘ └─────┬──────┘
             │ (Spring Event)    │ (Domain Event)   │
             │                   ▼                  │
             │     TicketTransactionCompleted       │
             │     DisputeResolvedEvent             │
             │                   │                  │
             │                   ▼ (AFTER_COMMIT)   │
             │        ┌──────────────────────┐      │
             │        │  reputation module   │◄─────┘
             │        │UpdateReputationUseCase│
             │        └──────────────────────┘
             ▼
  ┌────────────────────────────────────────────────────────┐
  │            shared Infrastructure & Storage             │
  │ FileStorageService (LocalFileStorageAdapter -> ./data) │
  │ MySQL 8.0 (Flyway V1..V4) / H2 in-memory (Tests)       │
  └────────────────────────────────────────────────────────┘
```

## Module Boundaries & Ownership

| Module | Core Responsibility | Patterns | Owner |
|---|---|---|---|
| `ticket` | Ticket lifecycle, 2-layer image handling, category adapters | State, Adapter, Repository | Dev A |
| `payment` | MoMo sandbox escrow (100%), seller deposit lock (50%), commission, payout | Strategy, Adapter, Repository | Dev B |
| `dispute` | Category-specific dispute window policies, evidence handling, arbitration | Strategy, Repository | Dev B |
| `user` | Account registration, profile, security auth, starred watchlist | Repository, Adapter | Dev C |
| `reputation` | Reputation scores (0.00..100.00), async transaction listener | Observer, Idempotent Worker | Dev C |
| `shared` | File storage adapter, security config, global exception handling, events | Ports & Adapters | Dev C |

## 4 Core Architectural Principles (Scalability Rules)

1. **Zero Cross-Module JPA Relations:** Entities reference foreign domains via raw `Long entityId` only. Never use `@ManyToOne` or `@JoinColumn` across module boundaries.
2. **Asynchronous Decoupling via Domain Events:** Inter-module side effects (e.g., reputation updates) execute via `@TransactionalEventListener(phase = AFTER_COMMIT)`.
3. **Storage Port & Adapter:** All file operations route through `FileStorageService` port (`LocalFileStorageAdapter`), isolating business logic from storage media.
4. **Centralized Defensive Security:** Session-based authentication, path traversal guards on file uploads, bank account data masking (last 4 digits only).
