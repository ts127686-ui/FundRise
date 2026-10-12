package com.fundrise;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet("/api/*")
public class FundRiseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Change these to match your local PostgreSQL setup.
    // You can override these with environment variables, or edit the defaults below.
    private static final String DB_URL = envOrDefault("FUNDRISE_DB_URL", "jdbc:postgresql://localhost:5432/fundrise");
    private static final String DB_USER = envOrDefault("FUNDRISE_DB_USER", "postgres");
    private static final String DB_PASSWORD = envOrDefault("FUNDRISE_DB_PASSWORD", "postgres");

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private final Gson gson = new Gson();

    @Override
    public void init() throws javax.servlet.ServletException {
        try {
            Class.forName("org.postgresql.Driver");
            createTables();
        } catch (Exception e) {
            throw new javax.servlet.ServletException("Could not initialize FundRise database", e);
        }
    }

    private Connection db() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private void createTables() throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(150) UNIQUE NOT NULL, password_hash TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS campaigns (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), title VARCHAR(200) NOT NULL, description TEXT NOT NULL, category VARCHAR(80) NOT NULL, goal_amount NUMERIC(12,2) NOT NULL CHECK(goal_amount > 0), image_url TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS donations (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), campaign_id INTEGER NOT NULL REFERENCES campaigns(id), amount NUMERIC(12,2) NOT NULL CHECK(amount > 0), payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
        };
        try (Connection c = db(); Statement s = c.createStatement()) {
            for (String q : ddl) s.executeUpdate(q);
        }
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        route(req, resp, "GET");
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        route(req, resp, "POST");
    }
    @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        route(req, resp, "DELETE");
    }

    private void route(HttpServletRequest req, HttpServletResponse resp, String method) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String p = req.getPathInfo();
        if (p == null || "/".equals(p)) { send(resp, 200, msg("FundRise backend is running")); return; }

        try {
            if ("GET".equals(method) && "/db-test".equals(p)) {
                try (Connection c = db(); Statement s = c.createStatement();
                     ResultSet r = s.executeQuery("SELECT current_database()")) {
                    r.next(); Map<String,Object> out = msg("PostgreSQL connected");
                    out.put("database", r.getString(1)); send(resp, 200, out);
                }
            } else if ("POST".equals(method) && "/auth/register".equals(p)) register(req, resp);
            else if ("POST".equals(method) && "/auth/login".equals(p)) login(req, resp);
            else if ("POST".equals(method) && "/auth/logout".equals(p)) logout(req, resp);
            else if ("GET".equals(method) && "/users/me".equals(p)) profile(req, resp);
            else if ("GET".equals(method) && "/campaigns".equals(p)) listCampaigns(req, resp);
            else if ("POST".equals(method) && "/campaigns".equals(p)) createCampaign(req, resp);
            else if ("GET".equals(method) && "/campaigns/my".equals(p)) myCampaigns(req, resp);
            else if (p.matches("/campaigns/\\d+") && "GET".equals(method)) campaign(req, resp, id(p));
            else if (p.matches("/campaigns/\\d+") && "DELETE".equals(method)) deleteCampaign(req, resp, id(p));
            else if ("POST".equals(method) && "/donations".equals(p)) donate(req, resp);
            else if ("GET".equals(method) && "/donations/my".equals(p)) myDonations(req, resp);
            else send(resp, 404, err("Endpoint not found: " + p));
        } catch (IllegalArgumentException e) {
            send(resp, 400, err(e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            send(resp, 500, err("Database error. Check Eclipse Console and database settings."));
        } catch (Exception e) {
            e.printStackTrace();
            send(resp, 500, err("Server error"));
        }
    }

    private JsonObject body(HttpServletRequest req) throws IOException {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = req.getReader()) { String line; while ((line=r.readLine())!=null) b.append(line); }
        try { return new JsonParser().parse(b.toString()).getAsJsonObject(); }
        catch (Exception e) { throw new IllegalArgumentException("Send a valid JSON request body"); }
    }
    private String str(JsonObject j, String k) {
        if (j == null || !j.has(k) || j.get(k).isJsonNull()) throw new IllegalArgumentException(k+" is required");
        String v=j.get(k).getAsString().trim();
        if(v.isEmpty()) throw new IllegalArgumentException(k+" is required");
        return v;
    }
    private int integer(JsonObject j, String k) {
        try { int n=Integer.parseInt(str(j,k)); if(n<=0) throw new NumberFormatException(); return n; }
        catch(NumberFormatException e){throw new IllegalArgumentException(k+" must be a positive integer");}
    }
    private BigDecimal amount(JsonObject j, String k) {
        try { BigDecimal a=new BigDecimal(str(j,k)); if(a.compareTo(BigDecimal.ZERO)<=0) throw new NumberFormatException(); return a; }
        catch(NumberFormatException e){throw new IllegalArgumentException(k+" must be a positive amount");}
    }
    private int id(String p) {
        try { return Integer.parseInt(p.split("/")[2]); }
        catch(Exception e){throw new IllegalArgumentException("Invalid ID");}
    }
    private void send(HttpServletResponse r,int status,Object o)throws IOException { r.setStatus(status); r.getWriter().write(gson.toJson(o)); }
    private Map<String,Object> msg(String s){Map<String,Object> m=new LinkedHashMap<>();m.put("message",s);return m;}
    private Map<String,Object> err(String s){Map<String,Object> m=new LinkedHashMap<>();m.put("error",s);return m;}
    private int userId(HttpServletRequest req) {
        HttpSession s=req.getSession(false);
        if(s==null || !(s.getAttribute("userId") instanceof Integer)) throw new IllegalArgumentException("Please log in first");
        return (Integer)s.getAttribute("userId");
    }
    private byte[] derive(char[] pass,byte[] salt,int rounds,int bits)throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pass,salt,rounds,bits);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
    }
    private String hash(String password)throws Exception {
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        return "pbkdf2$120000$"+Base64.getEncoder().encodeToString(salt)+"$"+
            Base64.getEncoder().encodeToString(derive(password.toCharArray(),salt,120000,256));
    }
    private boolean verify(String password,String stored)throws Exception {
        String[] a=stored.split("\\$");if(a.length!=4||!"pbkdf2".equals(a[0]))return false;
        byte[] salt=Base64.getDecoder().decode(a[2]), expected=Base64.getDecoder().decode(a[3]);
        return MessageDigest.isEqual(expected,derive(password.toCharArray(),salt,Integer.parseInt(a[1]),expected.length*8));
    }

    private void register(HttpServletRequest req,HttpServletResponse resp)throws Exception {
        JsonObject j=body(req);String name=str(j,"name"),email=str(j,"email").toLowerCase(Locale.ROOT),password=str(j,"password");
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Invalid email");
        if(password.length()<8)throw new IllegalArgumentException("Password must be at least 8 characters");
        try(Connection c=db();PreparedStatement s=c.prepareStatement("INSERT INTO users(name,email,password_hash) VALUES(?,?,?) RETURNING id")){
            s.setString(1,name);s.setString(2,email);s.setString(3,hash(password));
            try(ResultSet r=s.executeQuery()){r.next();int uid=r.getInt(1);req.getSession(true).setAttribute("userId",uid);
                Map<String,Object> out=msg("Registration successful");out.put("id",uid);out.put("name",name);out.put("email",email);send(resp,201,out);}
        }catch(SQLException e){if("23505".equals(e.getSQLState()))send(resp,409,err("Email already registered"));else throw e;}
    }
    private void login(HttpServletRequest req,HttpServletResponse resp)throws Exception {
        JsonObject j=body(req);String email=str(j,"email").toLowerCase(Locale.ROOT),password=str(j,"password");
        try(Connection c=db();PreparedStatement s=c.prepareStatement("SELECT id,name,email,password_hash FROM users WHERE email=?")){
            s.setString(1,email);try(ResultSet r=s.executeQuery()){
                if(!r.next()||!verify(password,r.getString("password_hash"))){send(resp,401,err("Invalid email or password"));return;}
                HttpSession old=req.getSession(false);if(old!=null)old.invalidate();
                HttpSession session=req.getSession(true);session.setAttribute("userId",r.getInt("id"));session.setMaxInactiveInterval(1800);
                Map<String,Object> out=msg("Login successful");out.put("id",r.getInt("id"));out.put("name",r.getString("name"));out.put("email",r.getString("email"));send(resp,200,out);
            }
        }
    }
    private void logout(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        HttpSession s=req.getSession(false);if(s!=null)s.invalidate();send(resp,200,msg("Logged out"));
    }
    private void profile(HttpServletRequest req,HttpServletResponse resp)throws SQLException,IOException {
        int uid;try{uid=userId(req);}catch(IllegalArgumentException e){send(resp,401,err(e.getMessage()));return;}
        try(Connection c=db();PreparedStatement s=c.prepareStatement("SELECT id,name,email,created_at FROM users WHERE id=?")){
            s.setInt(1,uid);try(ResultSet r=s.executeQuery()){if(!r.next()){send(resp,404,err("User not found"));return;}
                Map<String,Object> out=new LinkedHashMap<>();out.put("id",r.getInt("id"));out.put("name",r.getString("name"));out.put("email",r.getString("email"));out.put("createdAt",r.getTimestamp("created_at"));send(resp,200,out);}
        }
    }

    private void listCampaigns(HttpServletRequest req,HttpServletResponse resp)throws SQLException,IOException {
        String category=req.getParameter("category"),search=req.getParameter("search");
        StringBuilder q=new StringBuilder("SELECT c.id,c.user_id,c.title,c.description,c.category,c.goal_amount,c.image_url,c.created_at,u.name creator,COALESCE(SUM(CASE WHEN d.payment_status='SUCCESS' THEN d.amount ELSE 0 END),0) raised FROM campaigns c JOIN users u ON u.id=c.user_id LEFT JOIN donations d ON d.campaign_id=c.id WHERE 1=1 ");
        List<String> f=new ArrayList<>();
        if(category!=null&&!category.trim().isEmpty()){q.append("AND LOWER(c.category)=LOWER(?) ");f.add(category.trim());}
        if(search!=null&&!search.trim().isEmpty()){q.append("AND (c.title ILIKE ? OR c.description ILIKE ?) ");f.add("%"+search.trim()+"%");f.add("%"+search.trim()+"%");}
        q.append("GROUP BY c.id,u.name ORDER BY c.created_at DESC");
        try(Connection c=db();PreparedStatement s=c.prepareStatement(q.toString())){
            for(int i=0;i<f.size();i++)s.setString(i+1,f.get(i));
            List<Map<String,Object>> out=new ArrayList<>();try(ResultSet r=s.executeQuery()){while(r.next())out.add(campaignRow(r));}send(resp,200,out);
        }
    }
    private Map<String,Object> campaignRow(ResultSet r)throws SQLException {
        Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getInt("id"));m.put("userId",r.getInt("user_id"));m.put("title",r.getString("title"));m.put("description",r.getString("description"));m.put("category",r.getString("category"));m.put("goalAmount",r.getBigDecimal("goal_amount"));m.put("raisedAmount",r.getBigDecimal("raised"));m.put("imageUrl",r.getString("image_url"));m.put("creator",r.getString("creator"));m.put("createdAt",r.getTimestamp("created_at"));return m;
    }
    private void createCampaign(HttpServletRequest req,HttpServletResponse resp)throws SQLException,IOException {
        int uid;try{uid=userId(req);}catch(IllegalArgumentException e){send(resp,401,err(e.getMessage()));return;}
        JsonObject j=body(req);String title=str(j,"title"),description=str(j,"description"),category=str(j,"category");BigDecimal goal=amount(j,"goalAmount");
        String image=j.has("imageUrl")&&!j.get("imageUrl").isJsonNull()?j.get("imageUrl").getAsString():null;
        try(Connection c=db();PreparedStatement s=c.prepareStatement("INSERT INTO campaigns(user_id,title,description,category,goal_amount,image_url) VALUES(?,?,?,?,?,?) RETURNING id")){
            s.setInt(1,uid);s.setString(2,title);s.setString(3,description);s.setString(4,category);s.setBigDecimal(5,goal);s.setString(6,image);
            try(ResultSet r=s.executeQuery()){r.next();Map<String,Object> out=msg("Campaign created");out.put("id",r.getInt(1));send(resp,201,out);}
        }
    }
    private void campaign(HttpServletRequest req,HttpServletResponse resp,int cid)throws SQLException,IOException {
        String q="SELECT c.id,c.user_id,c.title,c.description,c.category,c.goal_amount,c.image_url,c.created_at,u.name creator,COALESCE(SUM(CASE WHEN d.payment_status='SUCCESS' THEN d.amount ELSE 0 END),0) raised FROM campaigns c JOIN users u ON u.id=c.user_id LEFT JOIN donations d ON d.campaign_id=c.id WHERE c.id=? GROUP BY c.id,u.name";
        try(Connection c=db();PreparedStatement s=c.prepareStatement(q)){s.setInt(1,cid);try(ResultSet r=s.executeQuery()){if(!r.next()){send(resp,404,err("Campaign not found"));return;}send(resp,200,campaignRow(r));}}
    }
    private void myCampaigns(HttpServletRequest req,HttpServletResponse resp)throws SQLException,IOException {
        int uid;try{uid=userId(req);}catch(IllegalArgumentException e){send(resp,401,err(e.getMessage()));return;}
        String q="SELECT c.id,c.user_id,c.title,c.description,c.category,c.goal_amount,c.image_url,c.created_at,u.name creator,COALESCE(SUM(CASE WHEN d.payment_status='SUCCESS' THEN d.amount ELSE 0 END),0) raised FROM campaigns c JOIN users u ON u.id=c.user_id LEFT JOIN donations d ON d.campaign_id=c.id WHERE c.user_id=? GROUP BY c.id,u.name ORDER BY c.created_at DESC";
        try(Connection c=db();PreparedStatement s=c.prepareStatement(q)){s.setInt(1,uid);List<Map<String,Object>> out=new ArrayList<>();try(ResultSet r=s.executeQuery()){while(r.next())out.add(campaignRow(r));}send(resp,200,out);}
    }
    private void deleteCampaign(HttpServletRequest req,HttpServletResponse resp,int cid)throws SQLException,IOException {
        int uid;try{uid=userId(req);}catch(IllegalArgumentException e){send(resp,401,err(e.getMessage()));return;}
        try(Connection c=db();PreparedStatement s=c.prepareStatement("DELETE FROM campaigns WHERE id=? AND user_id=?")){s.setInt(1,cid);s.setInt(2,uid);if(s.executeUpdate()==0)send(resp,404,err("Campaign not found or not owned by you"));else send(resp,200,msg("Campaign deleted"));}
    }
    private void donate(HttpServletRequest req,HttpServletResponse resp)throws SQLException,IOException {
        int uid;try{uid=userId(req);}catch(IllegalArgumentException e){send(resp,401,err(e.getMessage()));return;}
        JsonObject j=body(req);int cid=integer(j,"campaignId");BigDecimal a=amount(j,"amount");
        try(Connection c=db()){
            try(PreparedStatement check=c.prepareStatement("SELECT id FROM campaigns WHERE id=?")){check.setInt(1,cid);try(ResultSet r=check.executeQuery()){if(!r.next()){send(resp,404,err("Campaign not found"));return;}}}
            try(PreparedStatement s=c.prepareStatement("INSERT INTO donations(user_id,campaign_id,amount,payment_status) VALUES(?,?,?,'PENDING') RETURNING id")){
                s.setInt(1,uid);s.setInt(2,cid);s.setBigDecimal(3,a);try(ResultSet r=s.executeQuery()){r.next();Map<String,Object> out=msg("Donation recorded as PENDING; no payment was taken");out.put("id",r.getInt(1));out.put("amount",a);out.put("paymentStatus","PENDING");send(resp,201,out);}
            }
        }
    }
    private void myDonations(HttpServletRequest req,HttpServletResponse resp)throws SQLException,IOException {
        int uid;try{uid=userId(req);}catch(IllegalArgumentException e){send(resp,401,err(e.getMessage()));return;}
        String q="SELECT d.id,d.campaign_id,d.amount,d.payment_status,d.created_at,c.title FROM donations d JOIN campaigns c ON c.id=d.campaign_id WHERE d.user_id=? ORDER BY d.created_at DESC";
        try(Connection c=db();PreparedStatement s=c.prepareStatement(q)){s.setInt(1,uid);List<Map<String,Object>> out=new ArrayList<>();try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getInt("id"));m.put("campaignId",r.getInt("campaign_id"));m.put("campaignTitle",r.getString("title"));m.put("amount",r.getBigDecimal("amount"));m.put("paymentStatus",r.getString("payment_status"));m.put("createdAt",r.getTimestamp("created_at"));out.add(m);}}send(resp,200,out);}
    }
}
