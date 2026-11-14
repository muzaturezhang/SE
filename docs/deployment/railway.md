# Railway Deployment Guide

This repository can be deployed as a Docker service on Railway and connected to a Railway MySQL 8 service.

## 1. Create the project

1. Sign in to Railway with GitHub.
2. Create a new project.
3. Choose **Deploy from GitHub repo** and select `muzaturezhang/SE`.
4. Add a **MySQL** service to the same project.
5. Add a public domain to the application service.

## 2. Application variables

Set these on the **application service**:

```text
SPRING_PROFILES_ACTIVE=mysql
SERVER_ADDRESS=0.0.0.0
SERVER_FORWARD_HEADERS_STRATEGY=framework
PAYMENT_PROVIDER=sandbox-payment
NOTIFICATION_FROM_ADDRESS=no-reply@example.invalid

MYSQL_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=${{MySQL.MYSQLUSER}}
MYSQL_PASSWORD=${{MySQL.MYSQLPASSWORD}}

STAFF_USERNAME=group6staff
STAFF_PASSWORD=<set a random local-only password of at least 16 characters>
```

Railway provides `PORT` to the container. The Dockerfile passes it to Spring Boot.

Do not commit `STAFF_PASSWORD`, database credentials, or a real SMTP password.

## 3. Deploy

Railway detects the Dockerfile. The expected flow is:

1. Build the JAR with Java 17.
2. Start Spring Boot with the `mysql` profile.
3. Run Flyway V1–V8 against MySQL.
4. Expose `/actuator/health`.
5. Serve the application through the Railway HTTPS domain.

## 4. Smoke test

After deployment, check:

```powershell
$base = 'https://<your-service>.up.railway.app'
Invoke-RestMethod "$base/actuator/health"
```

Expected:

```json
{"status":"UP"}
```

Then test:

- `/account/register`
- `/account/login`
- `/rooms`
- `/stays/new`
- `/stays`
- `/admin/rooms`
- `/admin/stays`

Use fictional data only. The payment provider remains sandbox-only.

## 5. Known deployment boundaries

- Real payment settlement is not integrated.
- External SMTP delivery is not configured.
- H2 is not used in the deployed `mysql` profile.
- Q5/Q7 participant evidence still must be collected separately.