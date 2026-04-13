
import java.sql.*;
import java.time.LocalDate;
import java.util.Scanner;

public class EmployeeSystem {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/employees";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Incorrect12";

    enum Role {
        HR_ADMIN, EMPLOYEE
    }

    static class LoggedInUser {

        int userId;
        Role role;
        Integer empid;

        LoggedInUser(int userId, Role role, Integer empid) {
            this.userId = userId;
            this.role = role;
            this.empid = empid;
        }
    }

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS); Scanner sc = new Scanner(System.in)) {

            LoggedInUser user = login(conn, sc);
            if (user == null) {
                return;
            }

            if (user.role == Role.HR_ADMIN) {
                hrMenu(conn, sc);
            } else {
                employeeMenu(conn, sc, user);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // LOGIN
    private static LoggedInUser login(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Username: ");
        String u = sc.nextLine();
        System.out.print("Password: ");
        String p = sc.nextLine();

        String sql = "SELECT user_id, password, role, empid FROM users WHERE username=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, u);
        ResultSet rs = ps.executeQuery();

        if (!rs.next()) {
            System.out.println("Invalid login.");
            return null;
        }

        if (!rs.getString("password").equals(p)) {
            System.out.println("Invalid login.");
            return null;
        }

        Role role = Role.valueOf(rs.getString("role"));
        Integer empid = rs.getInt("empid");
        if (rs.wasNull()) {
            empid = null;
        }

        System.out.println("Login successful as " + role);
        return new LoggedInUser(rs.getInt("user_id"), role, empid);
    }

    // HR ADMIN MENU
    private static void hrMenu(Connection conn, Scanner sc) throws SQLException {
        while (true) {
            System.out.println("\n=== HR ADMIN MENU ===");
            System.out.println("1. Search Employees (Edit Mode)");
            System.out.println("2. Update Employee Data");
            System.out.println("3. Update Salary by % (Range Rules)");
            System.out.println("4. Report: Total Pay by Job Title");
            System.out.println("5. Report: Total Pay by Division");
            System.out.println("6. Report: New Hires in Date Range");
            System.out.println("0. Logout");
            System.out.print("Choice: ");

            switch (sc.nextLine()) {
                case "1":
                    searchEmployees(conn, sc, true);
                    break;
                case "2":
                    updateEmployee(conn, sc);
                    break;
                case "3":
                    updateSalary(conn, sc);
                    break;
                case "4":
                    reportPayByJobTitle(conn, sc);
                    break;
                case "5":
                    reportPayByDivision(conn, sc);
                    break;
                case "6":
                    reportNewHires(conn, sc);
                    break;
                case "0":
                    return;
            }
        }
    }

    // EMPLOYEE MENU
    private static void employeeMenu(Connection conn, Scanner sc, LoggedInUser user) throws SQLException {
        while (true) {
            System.out.println("\n=== EMPLOYEE MENU ===");
            System.out.println("1. View My Demographic Data");
            System.out.println("2. View My Pay History");
            System.out.println("0. Logout");
            System.out.print("Choice: ");

            switch (sc.nextLine()) {
                case "1":
                    viewOwnData(conn, user.empid);
                    break;
                case "2":
                    viewPayHistory(conn, user.empid);
                    break;
                case "0":
                    return;
            }
        }
    }

    // SEARCH EMPLOYEES
    private static void searchEmployees(Connection conn, Scanner sc, boolean editMode) throws SQLException {
        System.out.print("Search by (name/dob/ssn/empid): ");
        String field = sc.nextLine().toLowerCase();

        String sql = "";
        PreparedStatement ps;

        switch (field) {
            case "name":
                System.out.print("Enter last name: ");
                sql = "SELECT * FROM employees WHERE Lname LIKE ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, "%" + sc.nextLine() + "%");
                break;

            case "dob":
                System.out.print("Enter DOB (YYYY-MM-DD): ");
                sql = "SELECT * FROM employees WHERE HireDate = ?";
                ps = conn.prepareStatement(sql);
                ps.setDate(1, Date.valueOf(sc.nextLine()));
                break;

            case "ssn":
                System.out.print("Enter SSN: ");
                sql = "SELECT * FROM employees WHERE SSN = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, sc.nextLine());
                break;

            case "empid":
                System.out.print("Enter empid: ");
                sql = "SELECT * FROM employees WHERE empid = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(sc.nextLine()));
                break;

            default:
                System.out.println("Invalid field.");
                return;
        }

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            System.out.println("\n--- Employee ---");
            System.out.println("ID: " + rs.getInt("empid"));
            System.out.println("Name: " + rs.getString("Fname") + " " + rs.getString("Lname"));
            System.out.println("Email: " + rs.getString("email"));
            System.out.println("HireDate: " + rs.getDate("HireDate"));
            System.out.println("Salary: " + rs.getDouble("Salary"));
            if (editMode) {
                System.out.println("SSN: " + rs.getString("SSN"));
                System.out.println("AddressID: " + rs.getInt("addressID"));
            }
        }
    }

    // UPDATE EMPLOYEE DATA
    private static void updateEmployee(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter empid to update: ");
        int id = Integer.parseInt(sc.nextLine());

        System.out.print("New email (blank = no change): ");
        String email = sc.nextLine();

        System.out.print("New addressID (blank = no change): ");
        String addr = sc.nextLine();

        String sql = "UPDATE employees SET "
                + "email = COALESCE(NULLIF(?, ''), email), "
                + "addressID = COALESCE(NULLIF(?, ''), addressID) "
                + "WHERE empid = ?";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, email);
        ps.setString(2, addr);
        ps.setInt(3, id);

        System.out.println("Rows updated: " + ps.executeUpdate());
    }

    // UPDATE SALARY WITH RANGE RULES
    private static void updateSalary(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter empid: ");
        int id = Integer.parseInt(sc.nextLine());

        String sql = "SELECT Salary FROM employees WHERE empid=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();

        if (!rs.next()) {
            System.out.println("Employee not found.");
            return;
        }

        double salary = rs.getDouble("Salary");
        System.out.println("Current Salary: " + salary);

        System.out.print("Increase %: ");
        double pct = Double.parseDouble(sc.nextLine());

        double maxPct = (salary <= 50000) ? 10 : (salary <= 100000 ? 7 : 5);
        if (pct > maxPct) {
            System.out.println("Exceeds allowed max: " + maxPct + "%");
            return;
        }

        double newSalary = salary * (1 + pct / 100);

        sql = "UPDATE employees SET Salary=? WHERE empid=?";
        ps = conn.prepareStatement(sql);
        ps.setDouble(1, newSalary);
        ps.setInt(2, id);

        ps.executeUpdate();
        System.out.println("New salary: " + newSalary);
    }

    // EMPLOYEE VIEW OWN DATA
    private static void viewOwnData(Connection conn, int empid) throws SQLException {
        String sql = "SELECT * FROM employees WHERE empid=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, empid);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            System.out.println("\n--- My Data ---");
            System.out.println("Name: " + rs.getString("Fname") + " " + rs.getString("Lname"));
            System.out.println("Email: " + rs.getString("email"));
            System.out.println("HireDate: " + rs.getDate("HireDate"));
            System.out.println("Salary: " + rs.getDouble("Salary"));
        }
    }

    // PAY HISTORY
    private static void viewPayHistory(Connection conn, int empid) throws SQLException {
        String sql = "SELECT * FROM payroll WHERE empid=? ORDER BY pay_date DESC";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, empid);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            System.out.println("\n--- Pay Statement ---");
            System.out.println("Date: " + rs.getDate("pay_date"));
            System.out.println("Earnings: " + rs.getDouble("earnings"));
            System.out.println("Fed Tax: " + rs.getDouble("fed_tax"));
            System.out.println("401k: " + rs.getDouble("retire_401k"));
        }
    }

    // REPORTS
    // Total pay by job title
    private static void reportPayByJobTitle(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter year: ");
        int year = Integer.parseInt(sc.nextLine());

        String sql
                = "SELECT jt.job_title, SUM(p.earnings) AS total "
                + "FROM payroll p "
                + "JOIN employee_job_titles ej ON p.empid = ej.empid "
                + "JOIN job_titles jt ON ej.job_title_id = jt.job_title_id "
                + "WHERE YEAR(p.pay_date)=? "
                + "GROUP BY jt.job_title";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, year);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            System.out.println(rs.getString("job_title") + ": " + rs.getDouble("total"));
        }
    }

    // Total pay by division
    private static void reportPayByDivision(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter year: ");
        int year = Integer.parseInt(sc.nextLine());

        String sql
                = "SELECT d.Name, SUM(p.earnings) AS total "
                + "FROM payroll p "
                + "JOIN employee_division ed ON p.empid = ed.empid "
                + "JOIN division d ON ed.div_ID = d.ID "
                + "WHERE YEAR(p.pay_date)=? "
                + "GROUP BY d.Name";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, year);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            System.out.println(rs.getString("Name") + ": " + rs.getDouble("total"));
        }
    }

    // New hires
    private static void reportNewHires(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Start date (YYYY-MM-DD): ");
        LocalDate start = LocalDate.parse(sc.nextLine());
        System.out.print("End date (YYYY-MM-DD): ");
        LocalDate end = LocalDate.parse(sc.nextLine());

        String sql
                = "SELECT empid, Fname, Lname, HireDate FROM employees "
                + "WHERE HireDate BETWEEN ? AND ? ORDER BY HireDate";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setDate(1, Date.valueOf(start));
        ps.setDate(2, Date.valueOf(end));

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            System.out.println(
                    rs.getInt("empid") + " | "
                    + rs.getString("Fname") + " "
                    + rs.getString("Lname") + " | "
                    + rs.getDate("HireDate")
            );
        }
    }
}
