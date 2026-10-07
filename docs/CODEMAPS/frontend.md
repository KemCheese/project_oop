<!-- Generated: 2026-10-07 | Files scanned: 241 | Token estimate: ~950 -->
# Frontend Architecture & View Hierarchy

## Template Tree (`src/main/resources/templates/`)

```
templates/
├── index.html                   # Landing page (Marketplace highlights, CTA)
├── layout/
│   ├── base.html                # Master layout (Header, Navbar with Notification Bell & Popover, Flash alerts, Footer)
│   └── fragments.html           # Reusable UI fragments (User badge, rating stars, alert boxes)
├── auth/
│   ├── login.html               # Form login (/login, credentials + error alerts)
│   └── register.html            # Registration form (/register, validation feedbacks)
├── ticket/
│   ├── list.html                # Marketplace ticket grid with category filter & star button
│   ├── create.html              # Post ticket form with 2-layer image upload (public + secret QR)
│   └── detail.html              # Ticket view, pricing box, star toggle, escrow, QR technical issue & transfer CTAs
├── user/
│   └── profile.html             # User profile (Tab 1: Info, Tab 2: Avatar, Tab 3: Bank, Tab 5: Starred)
├── payment/
│   ├── checkout.html            # MoMo payment redirection & escrow summary
│   ├── transfer.html            # VietQR Transfer Simulator Console (15:00 countdown, tactile copy, escrow guarantee)
│   ├── result.html              # Payment return/callback result (Escrow stepper 4-step progress)
│   └── deposit.html             # Seller 50% deposit lock screen
├── wallet/
│   └── index.html               # E-Wallet overview (Balance card, flexible top-up >= 10k, transaction ledger history)
├── notification/
│   └── list.html                # In-app Notification Center (Categorized alerts, mark all as read)
├── dispute/
│   ├── create.html              # Open dispute form (Category policy badge, evidence upload)
│   └── detail.html              # Dispute discussion thread & evidence gallery
├── admin/
│   └── dispute/
│       ├── list.html            # Admin pending disputes queue
│       └── review.html          # Admin arbitration review console (Buyer/Seller payout buttons, verdict modal)
├── reputation/
│   ├── detail.html              # User reputation score card & badge (0..100)
│   └── top.html                 # Top reputable sellers leaderboard
└── error/
    └── error.html               # Unified error page (HTTP 400/403/404/500)
```

## Interactive Components & AJAX Flows

| Component | UI Location | Trigger Event | Target Endpoint / Handler | Behavior |
|---|---|---|---|---|
| **Star Toggle** | `ticket/list.html` | Click star icon on card | `POST /api/tickets/{id}/star` (AJAX) | Toggles active state, stops event propagation (`stopPropagation`) |
| **Star Button** | `ticket/detail.html`| Click star in price box | `POST /api/tickets/{id}/star` (AJAX) | Toggles filled star & updates watchlist asynchronously |
| **Notification Bell** | `layout/base.html` | 25s polling or bell click | `GET /api/notifications/unread-count`, `/recent` | Updates breathing badge, renders 380px frosted popover |
| **Mark Notification Read** | `base.html` & `notification/list.html` | Click notification / Mark all | `POST /api/notifications/{id}/read`, `/read-all` | Marks read and decrements unread counter |
| **Wallet Top-Up** | `wallet/index.html` | Form submit amount | `POST /wallet/topup` | Credits balance (>= 10.000đ, accepts odd sums) and adds ledger item |
| **Report QR Issue** | `ticket/detail.html` | Click button within 30m | `POST /tickets/{id}/technical-issue` | Transitions to `TECHNICAL_ISSUE`, freezes auto-completion (UC-06b) |
| **VietQR Confirm** | `payment/transfer.html` | Click "Xác nhận chuyển khoản" | `POST /payment/transfer/confirm` | Confirms escrow payment, triggers PRG pattern redirect to ticket |
| **Escrow Checkout** | `ticket/detail.html`| Submit Buy button | `POST /payment/escrow/buy` or `/payment/wallet/pay` | Charges buyer via VietQR Transfer or Wallet balance |
| **Lock Deposit** | `ticket/detail.html`| Submit Lock button | `POST /tickets/{id}/lock-deposit` or `/wallet/deposit` | Freezes 50% deposit and reveals transfer button |
| **Reveal QR** | `ticket/detail.html`| Transfer confirmed | `POST /tickets/{id}/transfer` | Unlocks & displays hidden check-in image to buyer |
| **File Dispute** | `dispute/create.html`| Submit evidence form | `POST /disputes` (Multipart) | Uploads proof file, sets ticket to `DISPUTED` |
| **Admin Decision** | `admin/.../review.html`| Click Win/Loss button | `POST /admin/disputes/{id}/resolve` | Dispatches settlement & publishes domain event |

