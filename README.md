# 📰 PaperFlow

**Digital Newspaper & Magazine Publishing, Selling, and Buying Platform**

PaperFlow is a centralized platform connecting Sri Lankan publication companies with readers — replacing informal, piracy-prone distribution (Telegram/WhatsApp) with a secure marketplace and a protected, page-by-page reading experience.

---

## ✨ Key Features

- **Secure Page Reader** — Active editions are served as server-watermarked, page-by-page images via short-lived signed tokens; source PDFs are never exposed to the browser.
- **Customer Mobile App** — A React Native app for iOS and Android that lets customers register, browse the catalog, purchase editions, read purchased content, and receive account and purchase notifications.
- **Publisher Portal** — Upload editions, manage multiple titles, configure discounted bundles, and track sales and payouts.
- **Marketplace** — Single-edition purchases, custom bundles with dynamic discounts, and discounted archive editions.
- **Automated Archiving** — Editions past a configurable age automatically transition to downloadable PDF archives.
- **Financial Management** — PayPal-integrated checkout, configurable commission rates, and exportable audit reports (PDF/CSV).
- **Platform Administration** — Publisher approval workflows, catalog moderation, and centralized audit logging.

---

## 🛠️ Technology Stack

- **Backend**: Spring Boot
- **Web Frontend**: React
- **Mobile App**: React Native for customer-facing iOS and Android experiences
- **Database**: Microsoft SQL Server
- **External Integrations**: PayPal SDK for checkout, SMTP server for emails, and push notification delivery for the mobile app

---

## 📂 Project Structure

This project follows a clean, decoupled client-server architecture.

### Backend Structure (`/backend`)
```
backend/
├── .mvn 
├── .gitattributes 
├── src/
│   ├── main/
│   │   ├── java/com/paperflow/backend/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── modal/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   └── resources/
│   │       ├── application.properties
│   │       
│   │       
│   └── test/
│       └── java/com/paperflow/
│           └── PaperFlowApplicationTests.java
├── mvnw
├── mvnw.cmd
├── pom.xml
```

### Frontend Structure (`/frontend`)
```
frontend/
├── public/
│  
├── src/
│   ├── assets/                   
│   ├── App.css
│   ├── App.tsx
│   ├── index.css
│   └── main.tsx
├── index.html                    
├── package.json                 
├── tsconfig.app.json
├── tsconfig.json
├── tsconfig.node.json
└── vite.config.ts               
```

### Mobile Structure (`/mobile`)
Will be update soon


---
## 📖 API Documentation

PaperFlow's backend exposes a fully interactive **Swagger UI** (via springdoc-openapi), auto-generated from the controller layer. Use it to explore, test, and authenticate against every REST endpoint without leaving the browser.

- **Swagger UI**: [http://localhost:9000/swagger-ui/index.html](http://localhost:9000/swagger-ui/index.html)
- **OpenAPI JSON spec**: `http://localhost:9000/v3/api-docs`

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
   cd web
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

### Running the mobile

will be update with project strcture  soon.


## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
