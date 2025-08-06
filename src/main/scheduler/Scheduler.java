package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

import java.util.*;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  //TODO: implement create_patient (Part 1)
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  // TODO: implement login_patient (Part 1)
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  // TODO: implement search_caregiver_schedule (Part 2)
        System.out.println("> reserve <date> <vaccine>");  // TODO: implement reserve (Part 2)
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  // TODO: implement cancel (extra credit)
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");  // TODO: implement show_appointments (Part 2)
        System.out.println("> logout");  // TODO: implement logout (Part 2)
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // TODO: Part 1
        if (tokens.length != 3) {
            System.out.println("Create patient failed");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again");
            return;
        }

        if (!validPassword(password)) {
            System.out.println("Create patient failed, please use a strong password (8+ char, at least one upper and one lower, at least one letter and one number, and at least one special character, from \"!\", \"@\", \"#\", \"?\")");
            return;
        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the patient
        try {
            Patient patient = new Patient.PatientBuilder(username, salt, hash).build();
            // save to patient information to our database
            patient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Create patient failed");
        }
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }

        if (!validPassword(password)) {
            System.out.println("Create caregiver failed, please use a strong password (8+ char, at least one upper and one lower, at least one letter and one number, and at least one special character, from \"!\", \"@\", \"#\", \"?\")");
            return;
        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build();
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    // edge cases: if password.length < 8 || (hasLowercase && hasUppercase) || (hasLetter && hasNumber) || (does not contain a special character)
    private static boolean validPassword(String password) {
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasLetter = false;
        boolean hasSpecial = false;

        if (password.length() < 8) {
            return false;
        }

        List<Character> special = new ArrayList<>();
        special.add('!');
        special.add('@');
        special.add('#');
        special.add('?');

        for (char c : password.toCharArray()) {
            if (special.contains(c)) {
                hasSpecial = true;
            }
            if (Character.isLowerCase(c)) {
                hasLower = true;
            }
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
        }
        return (hasLower && hasUpper && hasDigit && hasLetter && hasSpecial);
    }
    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // TODO: Part 1
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in, try again");
            return;
        }

        if (tokens.length != 3) {
            System.out.println("Login patient failed");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login patient failed");
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Login patient failed");
        } else {
            System.out.println("Logged in as " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        // TODO: Part 2
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Need to login first");
            return;
        }
        if (tokens.length != 2) {
            System.out.println("Search caregiver failed");
            return;
        }

        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);

            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();
            String getCaregivers = "SELECT Username FROM Availabilities WHERE Time = ? ORDER BY Username";
            String getVaccines = "SELECT Name, Doses FROM Vaccines WHERE Doses > 0 ORDER BY Name";
            try {
                // Get available caregivers
                PreparedStatement caregiverStatement = con.prepareStatement(getCaregivers);
                caregiverStatement.setDate(1, d);
                ResultSet caregiverResult = caregiverStatement.executeQuery();

                System.out.println("Available caregivers:");
                boolean hasCaregivers = false;
                while (caregiverResult.next()) {
                    System.out.println(caregiverResult.getString("Username"));
                    hasCaregivers = true;
                }
                if (!hasCaregivers) {
                    System.out.println("No caregivers available on this date.");
                }

                // Get available vaccines
                PreparedStatement vaccineStatement = con.prepareStatement(getVaccines);
                ResultSet vaccineResult = vaccineStatement.executeQuery();

                System.out.println("Available vaccines:");
                boolean hasVaccines = false;
                while (vaccineResult.next()) {
                    System.out.println(vaccineResult.getString("Name") + " " + vaccineResult.getInt("Doses"));
                    hasVaccines = true;
                }
                if (!hasVaccines) {
                    System.out.println("No vaccines available.");
                }
            } catch (SQLException e) {
                System.out.println("Error occurred when searching caregiver schedule");
                e.printStackTrace();
            } finally {
                cm.closeConnection();
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Enter a valid date");
        }
    }

    private static void reserve(String[] tokens) {
        // TODO: Part 2
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Need to login first");
            return;
        }
        if (currentCaregiver != null) {
            System.out.println("Please login as patient");
            return;
        }
        if (tokens.length != 3) {
            System.out.println("Please try again");
            return;
        }

        String date = tokens[1];
        String vaccineName = tokens[2];

        try {
            Date d = Date.valueOf(date);

            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();

            // Check if vaccine exists and has doses
            String checkVaccine = "SELECT Doses FROM Vaccines WHERE Name = ?";
            PreparedStatement vaccineStatement = con.prepareStatement(checkVaccine);
            vaccineStatement.setString(1, vaccineName);
            ResultSet vaccineResult = vaccineStatement.executeQuery();

            if (!vaccineResult.next() || vaccineResult.getInt("Doses") <= 0) {
                System.out.println("Not enough available doses!");
                cm.closeConnection();
                return;
            }

            // Find available caregiver
            String findCaregiver = "SELECT Username FROM Availabilities WHERE Time = ? ORDER BY Username LIMIT 1";
            PreparedStatement caregiverStatement = con.prepareStatement(findCaregiver);
            caregiverStatement.setDate(1, d);
            ResultSet caregiverResult = caregiverStatement.executeQuery();

            if (!caregiverResult.next()) {
                System.out.println("No Caregiver is available!");
                cm.closeConnection();
                return;
            }

            String caregiverName = caregiverResult.getString("Username");

            // Generate appointment ID
            String getMaxId = "SELECT MAX(Appointment_id) as maxId FROM Appointments";
            PreparedStatement maxIdStatement = con.prepareStatement(getMaxId);
            ResultSet maxIdResult = maxIdStatement.executeQuery();
            int appointmentId = 1;
            if (maxIdResult.next() && maxIdResult.getInt("maxId") > 0) {
                appointmentId = maxIdResult.getInt("maxId") + 1;
            }

            // Create appointment
            String createAppointment = "INSERT INTO Appointments VALUES (?, ?, ?, ?, ?)";
            PreparedStatement appointmentStatement = con.prepareStatement(createAppointment);
            appointmentStatement.setInt(1, appointmentId);
            appointmentStatement.setDate(2, d);
            appointmentStatement.setString(3, caregiverName);
            appointmentStatement.setString(4, currentPatient.getUsername());
            appointmentStatement.setString(5, vaccineName);
            appointmentStatement.executeUpdate();

            // Remove caregiver availability
            String removeAvailability = "DELETE FROM Availabilities WHERE Time = ? AND Username = ?";
            PreparedStatement removeStatement = con.prepareStatement(removeAvailability);
            removeStatement.setDate(1, d);
            removeStatement.setString(2, caregiverName);
            removeStatement.executeUpdate();

            // Decrease vaccine doses
            Vaccine vaccine = new Vaccine.VaccineGetter(vaccineName).get();
            vaccine.decreaseAvailableDoses(1);

            System.out.println("Appointment ID: " + appointmentId + ", Caregiver username: " + caregiverName);
            cm.closeConnection();

        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Please try again!");
            e.printStackTrace();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // TODO: Extra credit
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        // TODO: Part 2
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }
        boolean isPatient = (currentPatient != null);
        String username = isPatient ? currentPatient.getUsername() : currentCaregiver.getUsername();

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        PreparedStatement statement = null;
        try {
        if (isPatient) {
            String selectPatient = "SELECT a.Appointment_id, a.Vaccine, a.Time, a.Caregiver_name FROM Appointments a WHERE a.Patient_name = ? ORDER BY a.Appointment_id";
            statement = con.prepareStatement(selectPatient);
            statement.setString(1, currentPatient.getUsername());
        }
        else {
            String selectCaregiver = "SELECT a.Appointment_id, a.Vaccine, a.Time, a.Patient_name FROM Appointments a WHERE Caregiver_name = ? ORDER BY a.Appointment_id";
            statement = con.prepareStatement(selectCaregiver);
            statement.setString(1, currentCaregiver.getUsername());
        }
        ResultSet rs = statement.executeQuery();
        boolean hasAppointments = false;

        while (rs.next()) {
            hasAppointments = true;
            int appointmentId = rs.getInt("Appointment_id");
            String vaccineName = rs.getString("Name");
            Date appointmentDate = rs.getDate("Time");

            if (isPatient) {
                String caregiverName = rs.getString("Caregiver_name");
                System.out.println("Caregiver name: " + caregiverName);
            } else {
                String patientName = rs.getString("Patient_name");
                System.out.println("Patient name: " + patientName);
            }
        }
        if (!hasAppointments) {
            System.out.println("No appointments found.");
        }
        } catch (SQLException e) {
            System.out.println("Error occurred when showing appointments");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        // TODO: Part 2
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }
        if (tokens.length != 1) {
            System.out.println("Please try again");
            return;
        }
        currentCaregiver = null;
        currentPatient = null;

        System.out.println("Successfully logged out");
    }
}
