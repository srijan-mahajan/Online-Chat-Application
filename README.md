# NexusChat

NexusChat is a production-ready, full-stack real-time collaboration application. It enables users to communicate via private direct messaging and group chat rooms, supporting instant media sharing, database persistence, and cloud-hosted data management.

Developed as a modern web application, the project features a responsive **Glassmorphic UI** built with Vanilla HTML5/CSS3/JavaScript, communicating with a **Spring Boot** backend running raw **WebSockets** and **MongoDB Atlas** cloud storage.

---

## 🌟 Key Features

* **Real-Time Messaging**: Low-latency message delivery using raw HTML5 WebSockets.
* **Collaboration Rooms**: Create or join public group chat rooms using dynamically generated codes.
* **Direct Private Messaging**: Instant one-to-one messaging with user search and active user counters.
* **Offline Message Delivery**: Send messages and files to users listed as "Offline". Messages are securely persisted in MongoDB and fetched automatically when the recipient connects.
* **JWT-Based Email Authentication**: Secure user registration and login using email/password, hashed with **BCrypt**, issuing signed **JSON Web Tokens (JWT)**.
* **Secure WebSocket Handshake**: Custom interceptor parses and validates JWTs before upgrading connections to WebSocket channels.
* **Media & File Sharing**: Upload images, PDFs, and files up to 5MB, serializing them into Base64 binaries for WebSocket streaming with custom server-side thread buffer mapping.
* **Lazy-Loaded Chat History**: Dynamic, database-backed retrieval of historical logs when selecting rooms or direct peers.
* **Persistent Sessions & Logout**: Automatically checks local storage cache to bypass authentication on page load; includes secure logout actions.

---

## 🛠️ Tech Stack

* **Frontend**: HTML5, CSS3 (Glassmorphism layout, Keyframe animations, HSL palette), Vanilla JavaScript (ES6+).
* **Backend**: Java 22, Spring Boot 3.3.0, Spring Boot Starter WebSocket.
* **Database**: MongoDB Atlas (NoSQL cloud cluster) & Spring Data MongoDB.
* **Security & Tokens**: Spring Security Crypto (BCrypt), JSON Web Tokens (JJWT 0.12.5).
* **Build System**: Maven.

---

## 🚀 Getting Started

### 📋 Prerequisites
* **Java SDK 22** or later installed.
* **Maven** installed.
* A **MongoDB Atlas** cluster URL (or a running local MongoDB instance on `mongodb://localhost:27017`).

### 🔧 Configuration
1. Open `src/main/resources/application.properties`.
2. Add your MongoDB Atlas connection string:
   ```properties
   spring.data.mongodb.uri=mongodb+srv://<username>:<password>@<cluster>.mongodb.net/nexuschat?retryWrites=true&w=majority
   spring.data.mongodb.auto-index-creation=true
   ```

### 💻 Local Run
1. Clone the repository and navigate to the directory:
   ```bash
   cd realtime-chat-app
   ```
2. Build the project and download dependencies:
   ```bash
   mvn clean compile
   ```
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
4. Access the web interface at **[http://localhost:8081](http://localhost:8081)**.

---

## ☁️ Cloud Deployment (Render)

This application is configured for cloud deployment platforms (Render, Railway, Heroku) by automatically binding to dynamic environment ports:

1. Create a Web Service on [Render](https://render.com/).
2. Connect your GitHub repository.
3. Configure the following build/start inputs:
   * **Runtime**: `Java`
   * **Build Command**: `mvn clean package -DskipTests`
   * **Start Command**: `java -jar target/realtime-chat-app-0.0.1-SNAPSHOT.jar`
   * **Instance Type**: `Free`
4. Click **Deploy**. Render will generate a secure `https://[your-app-name].onrender.com` link.
