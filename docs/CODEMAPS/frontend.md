<!-- Generated: 2026-10-06 | Files scanned: 105 | Token estimate: ~700 -->
# Frontend Architecture & View Hierarchy

## Template Tree (`src/main/resources/templates/`)

```
templates/
├── index.html                   # Landing page (Marketplace highlights, CTA)
├── layout/
│   ├── base.html                # Master layout (Header, Navbar, Flash alerts, Footer)
│   └── fragments.html           # Reusable UI fragments (User badge, rating stars, alert boxes)
├── auth/
│   ├── login.html               # Form login (/login, credentials + error alerts)
│   └── register.html            # Registration form (/register, validation feedbacks)
├── ticket/
│   ├── list.html                # Marketplace ticket grid with category filter & star button
│   ├── create.html              # Post ticket form with 2-layer image upload (public + secret QR)
│   └── detail.html              # Ticket view, pricing box, star toggle, escrow & transfer CTAs
├── user/
│   └── profile.html             # User profile (Tab 1: Info, Tab 2: Avatar, Tab 3: Bank, Tab 5: Starred)
├── payment/
│   ├── checkout.html            # MoMo payment redirection & escrow summary
│   ├── result.html              # Payment return/callback result
│   └── deposit.html             # Seller 50% deposit lock screen
├── dispute/
│   ├── create.html              # Open dispute form (Category policy badge, evidence upload)
│   └── detail.html              # Dispute discussion thread & evidence gallery
├── admin/
│   └── dispute/
│       ├── list.html            # Admin pending disputes queue
│       └── review.html          # Admin arbitration review console (Buyer/Seller payout buttons)
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
| **Escrow Checkout** | `ticket/detail.html`| Submit Buy button | `POST /payment/momo/initiate` | Redirects buyer to MoMo Test Gateway |
| **Lock Deposit** | `ticket/detail.html`| Submit Lock button | `POST /tickets/{id}/lock-deposit` | Freezes 50% deposit and reveals transfer button |
| **Reveal QR** | `ticket/detail.html`| Transfer confirmed | `POST /tickets/{id}/transfer` | Unlocks & displays hidden check-in image to buyer |
| **File Dispute** | `dispute/create.html`| Submit evidence form | `POST /disputes` (Multipart) | Uploads proof file, sets ticket to `DISPUTED` |
| **Admin Decision** | `admin/.../review.html`| Click Win/Loss button | `POST /admin/disputes/{id}/resolve` | Dispatches settlement & publishes domain event |
