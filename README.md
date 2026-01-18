**# Payment Gateway Integration (Stripe + Razorpay) — Java Spring Boot

A production-style backend service built with **Java + Spring Boot + PostgreSQL** that integrates:

✅ **Stripe Checkout** (International Payments)  
✅ **Razorpay Orders + Signature Verification** (India Payments)  
✅ Secure **Webhook Handling (Stripe + Razorpay)**  
✅ Payment lifecycle tracking in DB  
✅ Webhook audit logs + idempotency (safe for duplicate events)

This project is designed as a **portfolio-quality payment service** for Upwork clients and backend engineering interviews.

---

## 🚀 Features

### Payments
- Create internal Order
- Stripe: Create Checkout Session
- Stripe: Webhook verification + DB updates
- Razorpay: Create Razorpay Order
- Razorpay: Verify payment signature
- Razorpay: Webhook verification + audit logs

### Engineering / Production
- PostgreSQL persistence using JPA/Hibernate
- Tables: `orders`, `payments`, `webhook_events`
- Payment lifecycle: `CREATED → PENDING → PAID/FAILED`
- Webhook events stored for audit/debugging
- Webhook idempotency using `(provider + event_id)` uniqueness

---

## 🧠 Workflow Overview

### 1) Orders
1. Client creates an order (stored in DB)
2. Default order status:
   - `CREATED`

### 2) Stripe Flow
1. Backend creates Stripe Checkout Session
2. User pays on Stripe hosted page
3. Stripe sends webhook event to backend
4. Backend verifies signature and updates:
   - `payments.status = PAID`
   - `orders.status = PAID`

### 3) Razorpay Flow
1. Backend creates Razorpay order (`order_...`)
2. Frontend opens Razorpay checkout using Razorpay orderId
3. Razorpay returns payment details
4. Backend verifies signature and updates:
   - `payments.status = PAID`
   - `orders.status = PAID`

---

## 🛠️ Tech Stack
- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA (Hibernate)
- PostgreSQL
- Stripe Java SDK
- Razorpay Java SDK
- Maven

---

## 🗃️ Database Tables

### orders
- `id`
- `amount`
- `currency`
- `status` (CREATED, PAID, PAYMENT_FAILED)
- `created_at`

### payments
- `id`
- `order_id`
- `provider` (STRIPE, RAZORPAY)
- `status` (CREATED, PENDING, PAID, FAILED)
- `provider_session_id` (Stripe session: cs_test_...)
- `provider_order_id` (Razorpay order: order_...)
- `provider_payment_id` (Stripe pi_... / Razorpay pay_...)
- `created_at`

### webhook_events
- `id`
- `provider`
- `event_id`
- `payload`
- `received_at`

---

## ✅ Setup (Local)

### Prerequisites
- Java 17+
- Maven
- PostgreSQL
- Stripe CLI (for webhook forwarding)
- Ngrok (recommended for Razorpay webhooks)

---

## 1) Clone Project
```bash
git clone https://github.com/<your-username>/payment-gateway-integration-java.git
cd payment-gateway-integration-java/demo


