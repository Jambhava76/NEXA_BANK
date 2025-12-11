# 📘 **NexaBank – Online Banking System (Admin + User Portal)**

A full-stack **Java Spring Boot** banking simulation system featuring secure authentication, user and admin portals, transaction management, loan modules, analytics dashboards, PDF reporting, and complaint handling — designed to mimic real-world digital banking workflows.

This project is a major upgrade from the earlier **Java + MySQL console-based system**, now rebuilt using modern frameworks, layered architecture, and enterprise-level best practices.

---

# 🚀 **Features**

## 👤 **User Portal**

* ✔ Create account & login with **Email OTP**
* ✔ Deposit, withdraw, transfer money
* ✔ View transaction history & statements
* ✔ Apply for loans
* ✔ Raise complaints
* ✔ Submit feedback
* ✔ Request account deletion
* ✔ Receive admin notices & updates

---

## 🛡️ **Admin Portal**

* ✔ Secure **Admin Login with OTP**
* ✔ View all accounts & transactions
* ✔ **Freeze / Unfreeze** user accounts
* ✔ Approve / Reject loan applications
* ✔ Manage complaints (reply, resolve)
* ✔ Analytics Dashboard with:

  * 📊 Activity heatmap
  * 📈 Transaction volume charts
  * 💼 Account metrics
* ✔ Generate & export **CSV / PDF reports**
* ✔ Manage account deletion workflow
* ✔ View **admin activity logs**
* ✔ View customer feedback
* ✔ Send notices to users

---

# 🧩 **Project Structure**

```
NexaBank/
│
├── src/main/java/com/nexabank/
│   ├── controller/          # User + Admin controllers
│   ├── service/             # Business logic layer
│   ├── repository/          # Spring Data JPA repositories
│   ├── model/               # Entities: Account, Transaction, Loan, Complaint, etc.
│   ├── security/            # OTP, encryption, session security
│   ├── util/                # PDF, CSV, mail utilities
│   └── NexaBankApplication.java
│
├── src/main/resources/
│   ├── templates/           # Thymeleaf HTML pages
│   ├── static/              # CSS, JS, images
│   ├── application.properties
│   └── schema.sql           # MySQL schema and table definitions
│
├── README.md
└── pom.xml
```

---

# 🗄️ **Database Schema Overview**

### **1. accounts**

Stores user details, KYC info, balance, status.

### **2. transactions**

Records deposits, withdrawals, transfers.

### **3. loans**

Stores loan requests, status, messages.

### **4. complaints**

Tracks user complaints and admin replies.

### **5. feedback**

Stores user ratings and suggestions.

### **6. notices**

Admin announcements sent to users.

### **7. admin_logs**

Tracks all admin actions for auditing.

---

# 🏗️ **Tech Stack**

## **Backend**

| Component       | Technology                  |
| --------------- | --------------------------- |
| Language        | Java 21                     |
| Framework       | Spring Boot 3               |
| Architecture    | MVC + Service Layer         |
| ORM             | Spring Data JPA + Hibernate |
| Database        | MySQL                       |
| Template Engine | Thymeleaf                   |
| Reporting       | iText / Flying Saucer (PDF) |
| Email           | Jakarta Mail                |
| Connection Pool | HikariCP                    |

---

## **Frontend**

* HTML5 / CSS3
* Thymeleaf templating
* JavaScript
* Chart.js (Analytics Dashboard)

---

## **Other Tools**

* Maven
* Lombok
* BCrypt (for password hashing)
* MySQL Workbench
* Git & GitHub

---

# ⚙️ **Setup Instructions**

### **1. Clone the Repository**

```bash
git clone https://github.com/<your-username>/NexaBank.git
cd NexaBank
```

### **2. Create MySQL Database**

```sql
CREATE DATABASE nexabank;
USE nexabank;
```

Run the tables provided in `schema.sql`.

---

### **3. Update Database Credentials**

In `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/nexabank
spring.datasource.username=root
spring.datasource.password=yourpassword

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

---

### **4. Run the Project**

```bash
mvn spring-boot:run
```

Access:

* **User Portal:** `http://localhost:8080/`
* **Admin Portal:** `http://localhost:8080/admin/login`

---

# 🔐 **Security Features**

* OTP login for users & admins
* BCrypt encrypted passwords
* Role-based authorization
* Session timeout & protection
* Account freezing system
* Input validation (server + client side)
* Activity logs for admin actions

---

# 🌱 **Future Enhancements**

* Mobile App (Flutter / React Native)
* AI-based fraud detection
* Multi-currency support
* Mini-statement SMS gateway
* UPI-style QR payments
* Dark mode UI

---

# 👤 **Author**

**Buchigalla Jambava Dattudu**
📧 Email: jambhava76@gmail.com
🌐 GitHub: https://github.com/Jambhava76
📝 Licensed under **MIT License** – free to use, modify, and distribute.

---

# 🏁 **Conclusion**

**NexaBank** demonstrates the full power of **Spring Boot**, **MySQL**, and **Thymeleaf** in building an enterprise-level web banking system.
It integrates authentication, transaction handling, admin governance, analytics, reporting, and real-time communication — making it a strong foundation for a real-world banking application.

---
