# 📰 PaperFlow

**Digital Newspaper & Magazine Publishing, Selling, and Buying Platform**

PaperFlow is a centralized platform connecting Sri Lankan publication companies with readers — replacing informal, piracy-prone distribution (Telegram/WhatsApp) with a secure marketplace and a protected, page-by-page reading experience.

---

## ✨ Key Features

- **Secure Page Reader** — Active editions are served as server-watermarked, page-by-page images via short-lived signed tokens; source PDFs are never exposed to the browser.
- **Publisher Portal** — Upload editions, manage multiple titles, configure discounted bundles, and track sales and payouts.
- **Marketplace** — Single-edition purchases, custom bundles with dynamic discounts, and discounted archive editions.
- **Automated Archiving** — Editions past a configurable age automatically transition to downloadable PDF archives.
- **Financial Management** — PayPal-integrated checkout, configurable commission rates, and exportable audit reports (PDF/CSV).
- **Platform Administration** — Publisher approval workflows, catalog moderation, and centralized audit logging.

---

## 🛠️ Technology Stack

- **Backend**: SpringBoot
- **Frontend**: React
- **Database**: MySQL
- **External Integrations**: PayPal SDK for checkout, SMTP Server for emails and websocket push use to in platform notifications

---

## 📂 Project Structure

This project follows a clean, decoupled client-server architecture.

### Backend Structure (`/backend`)
will be update soon

### Frontend Structure (`/frontend`)
will be update soon

---

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK) 21** or higher
- **Node.js** (v22  or higher) & **npm**
- **Maven** (optional, wrapper provided)

### Running the Backend
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Configure your properties in `src/main/resources/application.properties` (Database credentials, SMTP, PayPal sandbox credentials).
3. Run the Spring Boot application:
   ```bash
   ./mvnw spring-boot:run
   ```

### Running the Frontend
1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run the development server:
   ```bash
   npm run dev
   ```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
