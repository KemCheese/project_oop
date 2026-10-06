<!-- Generated: 2026-10-06 | Files scanned: 105 | Token estimate: ~900 -->
# Backend Architecture & Call Chains

## Security Chain & Middleware
`SecurityConfig.filterChain()` enforces:
- **Public:** `GET /`, `GET /tickets`, `GET /tickets/{id}`, `GET /reputation/**`, `/login`, `/register`, `/css/**`, `/js/**`, `/images/**`, `/files/**`, `/payment/momo/ipn`, `/payment/momo/return`.
- **Authenticated (`ROLE_USER` / `ROLE_ADMIN`):** `POST /tickets`, `POST /tickets/{id}/lock-deposit`, `POST /tickets/{id}/transfer`, `/profile/**`, `/payment/**`, `/disputes/**`, `POST /api/tickets/{id}/star`.
- **Admin Only (`ROLE_ADMIN`):** `/admin/disputes/**`.
- **Exception Boundary:** `GlobalExceptionHandler` (`@ControllerAdvice`) auto-branches to JSON `ErrorResponse` (AJAX) or Thymeleaf `error/error` view.

## API Routes & Call Chains

### 1. User & Authentication (`user`)
```
GET  /login                       -> UserController.showLoginForm -> "auth/login"
GET  /register                    -> UserController.showRegisterForm -> "auth/register"
POST /register                    -> UserController.register -> RegisterUserUseCase.register -> PasswordEncoder + UserRepository.save + ReputationScore(init 100.0)
GET  /profile                     -> UserController.showProfile -> UserQueryService.findByEmail -> UserProfileService.getProfile + UpdateReputationUseCase.getOrCreate -> "user/profile"
POST /profile                     -> UserController.updateProfile -> UserProfileService.updateProfile -> UserRepository.save
POST /profile/avatar              -> UserController.uploadAvatar -> UserProfileService.updateAvatar -> FileStorageService.store("avatars")
POST /profile/bank                -> UserController.updateBankAccount -> UserProfileService.updateBankAccount -> UserRepository.save
POST /api/tickets/{ticketId}/star -> StarredTicketApiController.toggleStar -> StarredTicketUseCase.toggleStar -> StarredTicketRepository
```

### 2. Tickets & Marketplace (`ticket`)
```
GET  /tickets                     -> TicketController.listTickets -> TicketRepository.findByStatus(LISTED) -> "ticket/list"
GET  /tickets/new                 -> TicketController.showCreateForm -> "ticket/create"
POST /tickets                     -> TicketController.createTicket -> ValidateTicketService.validate -> FileStorageService.store(2 images) -> TicketStateMachineService.create -> "redirect:/tickets/{id}"
GET  /tickets/{id}                -> TicketController.showDetail -> TicketRepository.findById -> TicketAdapter.renderDetails -> "ticket/detail"
POST /tickets/{id}/lock-deposit   -> TicketController.lockDeposit -> LockDepositUseCase.lock -> TicketStateMachineService.transition
POST /tickets/{id}/transfer       -> TicketController.transferTicket -> TransferTicketService.transfer -> Reveal secret QR
```

### 3. Payment & Escrow (`payment`)
```
POST /payment/momo/initiate       -> PaymentController.initiatePayment -> HoldFundsUseCase.hold -> MomoTestGatewayAdapter.createPaymentUrl
GET  /payment/momo/return         -> PaymentController.handleMomoReturn -> HoldFundsUseCase.confirmFromGateway -> EscrowTransactionRepository.save
POST /payment/momo/ipn            -> PaymentController.handleMomoIpn -> HoldFundsUseCase.processIpn -> EscrowTransactionRepository.save
POST /payment/{id}/release        -> PaymentController.releasePayment -> ReleaseFundsUseCase.release -> CommissionCalculator + TaxWithholding -> Payout -> DomainEventPublisher.publish(TicketTransactionCompletedEvent)
```

### 4. Dispute & Arbitration (`dispute`)
```
GET  /disputes/create/{ticketId}  -> DisputeController.showCreateForm -> DisputeWindowPolicy.isEligible -> "dispute/create"
POST /disputes                    -> DisputeController.createDispute -> FileDisputeUseCase.fileDispute -> FileStorageService.store(evidence) -> DisputeRepository.save
GET  /disputes/{id}               -> DisputeController.showDetail -> DisputeRepository.findById + DisputeEvidenceRepository -> "dispute/detail"
POST /disputes/{id}/respond       -> DisputeController.respondToDispute -> RespondToDisputeUseCase.respond -> DisputeRepository.save
GET  /admin/disputes              -> DisputeReviewController.listDisputes -> DisputeRepository.findByStatus -> "admin/dispute/list"
POST /admin/disputes/{id}/resolve -> DisputeReviewController.resolveDispute -> ReviewDisputeUseCase.resolve -> Refund/Payout + DomainEventPublisher.publish(DisputeResolvedEvent)
```

### 5. Reputation & Rating (`reputation`)
```
GET  /reputation/{userId}         -> ReputationController.showUserReputation -> UpdateReputationUseCase.getOrCreate -> "reputation/detail"
GET  /reputation/top              -> ReputationController.showTopSellers -> ReputationRepository.findTopSellers -> "reputation/top"
ASYNC EVENT LISTENER:
@TransactionalEventListener(AFTER_COMMIT) TicketTransactionCompletedEvent -> UpdateReputationUseCase.onTicketTransactionCompleted (+2 pts)
@TransactionalEventListener(AFTER_COMMIT) DisputeResolvedEvent            -> UpdateReputationUseCase.onDisputeResolved (+1 win, -15 loss)
```
