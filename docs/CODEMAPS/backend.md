<!-- Generated: 2026-10-07 | Files scanned: 241 | Token estimate: ~1100 -->
# Backend Architecture & Call Chains

## Security Chain & Middleware
`SecurityConfig.filterChain()` enforces:
- **Public:** `GET /`, `GET /tickets`, `GET /tickets/{id}`, `GET /reputation/**`, `/login`, `/register`, `/css/**`, `/js/**`, `/images/**`, `/files/**`, `/h2-console/**`.
- **Authenticated (`ROLE_USER` / `ROLE_ADMIN`):** `POST /tickets`, `POST /tickets/{id}/lock-deposit`, `POST /tickets/{id}/transfer`, `/profile/**`, `/payment/**`, `/wallet/**`, `/notifications/**`, `/api/notifications/**`, `/disputes/**`, `POST /api/tickets/{id}/star`.
- **Admin Only (`ROLE_ADMIN`):** `/admin/disputes/**`.
- **Smart Auth Handler:** `SmartAuthenticationSuccessHandler` bảo vệ phòng ngừa Open Redirect (CWE-601), chuyển hướng ưu tiên `SavedRequest` -> param `redirect` -> session attribute `REDIRECT_AFTER_AUTH`.
- **Exception Boundary:** `GlobalExceptionHandler` (`@ControllerAdvice`) tự động phân nhánh JSON `ErrorResponse` (AJAX/REST) hoặc Thymeleaf `error/error` view.

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
GET  /tickets                     -> TicketController.listTickets -> TicketRepository.searchListed -> "ticket/list"
GET  /tickets/suggestions         -> TicketController.suggestions -> TicketRepository.findTopSuggestions -> JSON
GET  /tickets/new                 -> TicketController.createForm -> "ticket/create"
POST /tickets                     -> TicketController.create -> FileStorageService.store(2 images) -> TransferTicketService.createListing -> "redirect:/tickets/{id}"
GET  /tickets/{id}                -> TicketController.detail -> TicketRepository.findById -> "ticket/detail"
POST /tickets/{id}/transfer       -> TicketController.markTransferred -> TransferTicketService.markTransferred -> Reveal secret QR
POST /tickets/{id}/received       -> TicketController.markReceived -> TransferTicketService.markAwaitingUse
POST /tickets/{id}/technical-issue-> TicketController.reportTechnicalIssue -> TransferTicketService.reportTechnicalIssue (UC-06b)
POST /tickets/{id}/cancel         -> TicketController.cancel -> TransferTicketService.cancelListing
SCHEDULER:
TicketExpirationScheduler (every 1m) -> TicketRepository.findExpiredListedTickets -> TicketStateMachineService.transition(CANCELLED) -> publish(TicketExpiredEvent)
DeliveryTimeoutScheduler (every 1m)   -> Auto-confirm TRANSFERRED -> AWAITING_USE after 30m (UC-06b)
TicketReminderScheduler (every 1m)    -> TicketRepository.findTicketsDueFor24hReminder -> TicketReminderService.claimAndNotify -> publish(TicketUpcomingReminderEvent)
ScheduleOverlap Guard (ticket/payment/wallet) -> TicketScheduleService.findConflictingTickets -> 2-mode Pop-up Modal & confirmOverlap guard
```

### 3. Payment & Escrow (`payment`)
```
POST /payment/escrow/buy          -> PaymentController.buyTicket -> HoldFundsUseCase.initEscrowPayment -> payUrl (/payment/transfer?ref=...&type=escrow)
POST /payment/deposit/lock        -> PaymentController.lockDeposit -> LockDepositUseCase.initDepositPayment -> payUrl (/payment/transfer?ref=...&type=deposit)
GET  /payment/transfer            -> PaymentController.showTransferPage -> renders payment/transfer.html (VietQR Sandbox)
POST /payment/transfer/confirm    -> PaymentController.confirmTransfer -> HoldFundsUseCase.confirmHeld / LockDepositUseCase.confirmLocked -> "redirect:/tickets/{id}"
POST /payment/transfer/cancel     -> PaymentController.cancelTransfer -> "redirect:/tickets/{id}"
POST /payment/wallet/pay          -> WalletPaymentController.payWithWallet -> WalletService.deductBalance -> HoldFundsUseCase.confirmHeld
POST /payment/{id}/release        -> PaymentController.releasePayment -> ReleaseFundsUseCase.release -> CommissionCalculator + TaxWithholding -> DomainEventPublisher.publish(TicketTransactionCompletedEvent)
SCHEDULER:
DepositTimeoutScheduler (every 1m) -> RefundBuyerUseCase.refundFull + Ticket CANCELLED + UpdateReputationUseCase.recordDepositTimeout (-15 pts) (UC-04b)
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

### 6. E-Wallet & Ledger (`wallet`)
```
GET  /wallet                      -> WalletController.walletHome -> WalletService.getOrCreateWallet + WalletService.getTransactionHistory -> "wallet/index"
POST /wallet/topup                -> WalletController.topUp -> WalletService.topUp (>= 10.000đ) -> WalletTransactionRepository.save -> "redirect:/wallet"
POST /payment/wallet/pay          -> WalletPaymentController.payWithWallet -> WalletPaymentGatewayAdapter.chargeEscrow
POST /payment/wallet/deposit      -> WalletPaymentController.depositWithWallet -> WalletPaymentGatewayAdapter.lockDeposit
```

### 7. In-App Notifications (`notification`)
```
GET  /notifications               -> NotificationController.listNotifications -> NotificationService.getNotifications -> "notification/list"
GET  /api/notifications/unread-count -> NotificationApiController.getUnreadCount -> NotificationService.countUnread -> JSON
GET  /api/notifications/recent    -> NotificationApiController.getRecentNotifications -> NotificationService.getRecentNotifications -> JSON
POST /api/notifications/{id}/read -> NotificationApiController.markAsRead -> NotificationService.markAsRead -> JSON
POST /api/notifications/read-all  -> NotificationApiController.markAllAsRead -> NotificationService.markAllAsRead -> JSON
ASYNC EVENT LISTENER:
@TransactionalEventListener(AFTER_COMMIT) onTicketEscrowPaid, onTicketDepositLocked, onTicketReceived, onTicketExpired, onTicketUpcomingReminder, onDisputeOpened, onDisputeResponded, onDisputeWithdrawn, onDisputeResolved, onDepositTimeout -> NotificationEventListener.createNotification
```

