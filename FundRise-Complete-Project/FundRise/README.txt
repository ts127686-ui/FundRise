FundRise — complete Eclipse / Maven / Tomcat 9 / PostgreSQL project

WHAT IS INCLUDED
- Original FundRise UI in src/main/webapp/index.html, connected to the Java servlet API
- Java Servlet backend and session-based registration/login
- PostgreSQL persistence for users, campaigns, and donation records
- Maven configuration that packages PostgreSQL JDBC and Gson in the WAR

REQUIREMENTS
- JDK 8 or later
- Eclipse IDE with Maven support
- Apache Tomcat 9.x (not Tomcat 10/11, because this project uses javax.servlet)
- PostgreSQL and pgAdmin
- Internet access on first Maven build so Maven can download dependencies

SETUP (FOLLOW IN ORDER)
1. In pgAdmin, connect to your local PostgreSQL server and create a database named: fundrise
2. Extract this ZIP to a normal folder.
3. In Eclipse choose File > Import > Maven > Existing Maven Projects.
4. Browse to the extracted FundRise folder, select its pom.xml, and click Finish.
5. Right-click FundRise project > Maven > Update Project. Select Force Update if available, then OK.
6. Open src/main/java/com/fundrise/FundRiseServlet.java.
7. Change DB_PASSWORD from CHANGE_ME to your PostgreSQL password, OR set the environment variable FUNDRISE_DB_PASSWORD before starting Eclipse. If your DB username is not postgres, change DB_USER or set FUNDRISE_DB_USER. Default database URL is jdbc:postgresql://localhost:5432/fundrise.
8. Confirm Maven Dependencies contains postgresql-42.7.3.jar and gson-2.11.0.jar.
9. Right-click project > Properties > Targeted Runtimes and select Tomcat 9. Confirm Project Facets uses Java 8 or a compatible installed JDK.
10. In Servers view, add this project to Tomcat 9, start/restart the server, and check the Eclipse Console for errors.
11. Open http://localhost:8080/FundRise/
12. Test the database connection at http://localhost:8080/FundRise/api/db-test . A successful response includes "PostgreSQL connected" and database "fundrise".

IF CAMPAIGNS DO NOT SAVE
- You must register/login first; creating a campaign requires an active session.
- Open browser Developer Tools (F12) > Network, submit a campaign, and inspect POST /FundRise/api/campaigns.
- 201 = saved. 401 = log in again/session issue. 500 = inspect Eclipse Console and /api/db-test. 404 = wrong context path or app not deployed.
- In pgAdmin, select the fundrise database and run: SELECT * FROM campaigns ORDER BY id DESC;
- If the console says "No suitable driver found", right-click project > Maven > Update Project, confirm PostgreSQL JAR is under Maven Dependencies, then Project > Clean and republish/restart Tomcat. The servlet explicitly loads org.postgresql.Driver; the Maven WAR includes the PostgreSQL driver dependency.

IMPORTANT
- Donation records are stored as PENDING only. This demo does not charge money or integrate a payment gateway.
- This is a learning project, not production-ready. Do not deploy publicly without security review, HTTPS, CSRF protections, rate limiting, and proper secrets management.
