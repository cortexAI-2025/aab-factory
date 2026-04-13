# AAB Factory — AI-Powered Android App Builder SaaS

A production-ready SaaS platform that lets users generate, customize, preview, and export signed Android App Bundles (AAB) using AI — monetized via Stripe.

---

## Architecture Overview

```
aab-factory/
├── android/          # Kotlin + Jetpack Compose mobile app
├── backend/          # Node.js (Fastify) + Firebase + Stripe API
└── templates/        # JSON-based app templates
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Mobile | Kotlin, Jetpack Compose, Hilt, Retrofit, Coroutines |
| Backend | Node.js, Fastify, TypeScript |
| Database | Firebase Firestore |
| Auth | Firebase Authentication |
| Storage | Firebase Storage |
| AI Engine | OpenAI GPT-4 / Mistral Codestral |
| Payments | Stripe (subscriptions + one-time) |
| Build | Gradle `bundleRelease` → `.aab` |

---

## Core Features

### 1. AI App Generator
Users describe their app in plain English. The AI generates:
- App structure (screens, navigation)
- UI layout (Compose code)
- Content placeholders
- Color theme

### 2. Template System
Five ready-to-use JSON templates:
- **Business Profile** — showcase services, contact info
- **E-commerce** — product catalog, cart
- **Blog/Content** — articles, media
- **Lead Generation** — capture form, CTA
- **Local Services** — location, bookings

### 3. No-Code Visual Editor
- Change app name, logo, colors
- Edit text and images per section
- Add/remove sections
- Live preview on device

### 4. AAB Generation
- Gradle `bundleRelease` pipeline
- Output: `app-release.aab`
- Supports user keystore or system keystore
- Download link via Firebase Storage

### 5. Stripe Monetization

| Plan | Price | Features |
|---|---|---|
| Free | $0 | 3 templates, watermark, 2 builds/month |
| Pro | $29/mo | Unlimited builds, no watermark, all templates, priority queue |
| Build Pack | $9 one-time | 5 extra builds |

---

## Getting Started

### Prerequisites
- Node.js 18+
- Android Studio Hedgehog+
- Firebase project
- Stripe account
- OpenAI or Mistral API key

### Backend Setup

```bash
cd backend
cp .env.example .env
# Fill in your keys in .env
npm install
npm run dev
```

### Android Setup

```bash
cd android
cp app/google-services.json.example app/google-services.json
# Replace with your Firebase google-services.json
./gradlew assembleDebug
```

### Environment Variables

See `backend/.env.example` for all required variables.

---

## User Flow

```
1. Sign up / Log in (Firebase Auth)
       ↓
2. Choose: AI Prompt OR Template
       ↓
3. AI generates app structure
       ↓
4. Visual Editor (customize colors, text, logo)
       ↓
5. Live Preview
       ↓
6. Generate AAB (Free: watermarked | Pro: clean)
       ↓
7. Download .aab → Upload to Play Store
```

---

## AAB Build Pipeline

```bash
# Triggered via backend API
cd android
./gradlew bundleRelease \
  -Pandroid.injected.signing.store.file=$KEYSTORE_PATH \
  -Pandroid.injected.signing.store.password=$KEYSTORE_PASS \
  -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
  -Pandroid.injected.signing.key.password=$KEY_PASS

# Output
app/build/outputs/bundle/release/app-release.aab
```

---

## Stripe Webhook Events Handled

| Event | Action |
|---|---|
| `checkout.session.completed` | Activate Pro plan |
| `invoice.paid` | Renew subscription |
| `invoice.payment_failed` | Downgrade to Free |
| `customer.subscription.deleted` | Revoke Pro access |

---

## Security

- Firebase ID token verification on every request
- Stripe webhook signature validation
- Keystore files encrypted at rest
- Rate limiting on AI and build endpoints
- Input sanitization on all user data

---

## License

MIT — build your SaaS, ship your apps.
