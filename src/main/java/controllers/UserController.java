package controllers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import cache.UserCache;
import model.User;
import utils.Hashing;
import utils.Log;

public class UserController {

  private static DatabaseController dbCon;

  public UserController() {
    dbCon = new DatabaseController();
  }

  public static User getUser(int id) {

    // Check for connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    // Build the query for DB
    String sql = "SELECT * FROM user where id=" + id;

    // Actually do the query
    ResultSet rs = dbCon.query(sql);
    User user = null;

    try {
      // Get first object, since we only have one
      if (rs.next()) {
        user =
            new User(
                rs.getInt("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("password"),
                rs.getString("email"),
                rs.getString("salt"));

        // return the create object
        return user;
      } else {
        System.out.println("No user found");
      }
    } catch (SQLException ex) {
      System.out.println(ex.getMessage());
    }

    // Return null
    return user;
  }

  /**
   * Get all users in database
   *
   * @return
   */
  public static ArrayList<User> getUsers() {

    // Check for DB connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    // Build SQL
    String sql = "SELECT * FROM user";

    // Do the query and initialyze an empty list for use if we don't get results
    ResultSet rs = dbCon.query(sql);
    ArrayList<User> users = new ArrayList<User>();

    try {
      // Loop through DB Data
      while (rs.next()) {
        User user =
            new User(
                rs.getInt("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("password"),
                rs.getString("email"),
                rs.getString("salt"));

        // Add element to list
        users.add(user);
      }
    } catch (SQLException ex) {
      System.out.println(ex.getMessage());
    }

    // Return the list of users
    return users;
  }

  public static User createUser(User user) {

    // Write in log that we've reach this step
    Log.writeLog(UserController.class.getName(), user, "Actually creating a user in DB", 0);

    // Set creation time for user.
    user.setCreatedTime(System.currentTimeMillis() / 1000L);

    // Check for DB Connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    //Add server side salt to the client side salt
    user.setSalt(user.getSalt() + new Random().nextDouble());

    //Hash the password with md5
    user.setPassword(Hashing.md5(user.getPassword(), user.getSalt()));

    // Insert the user in the DB
    // TODO: Hash the user password before saving it. DONE
    int userID = dbCon.insert(
        "INSERT INTO user(first_name, last_name, password, email, created_at, salt) VALUES('"
            + user.getFirstname()
            + "', '"
            + user.getLastname()
            + "', '"
            + user.getPassword()
            + "', '"
            + user.getEmail()
            + "', '"
            + user.getCreatedTime()
            + "', '"
            + user.getSalt()
            + "')");

    if (userID != 0) {
      //Update the userid of the user before returning
      user.setId(userID);
    } else{
      // Return null if user has not been inserted into database
      return null;
    }

    // Return user
    return user;
  }

  public static String authenticateUser (User user){
    String salt = "";
    int id = 0;
    String newAuthToken = "";


    // Check for DB Connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    //Check if authToken is active and valid
    if (user.getAuthToken() != null){
      try {
        ResultSet res = dbCon.query("SELECT * FROM user WHERE authToken = \'" + user.getAuthToken() + "\' AND email = \'" + user.getEmail() + "\'");
        if (res.next()){
          return res.getString("authToken");
        }
      } catch (SQLException err){
        err.printStackTrace();
      }
    }

    //Retrieve salt
    try{
      ResultSet res = dbCon.query("SELECT salt FROM user WHERE email = \'" + user.getEmail() + "\'");
      if (res.next()){
        salt = res.getString("salt");
      } else {
        //return null if email does not exist
        return null;
      }
    } catch (SQLException err){
      err.printStackTrace();
    }

    //Authenticate email and password
    try {
      ResultSet res = dbCon.query("SELECT id FROM user WHERE " +
              "email = " + "\'" + user.getEmail() + "\' " +
              "AND password = " + "\'" + Hashing.md5(user.getPassword(), salt) + "\'" );
      if (res.next()){
        id = res.getInt("id");
      }
    } catch (SQLException err){
      err.printStackTrace();
    }

    //Generate new authToken from current date and a random double
    newAuthToken = Hashing.sha(new Date().toString(), Double.toString(new Random().nextDouble()));
    try {
      dbCon.update("UPDATE user SET " +
              "authtoken = \'" + newAuthToken + "\' " +
              "WHERE id = \'" + id + "\'");
      return newAuthToken;
    } catch (SQLException err){
      err.printStackTrace();
    }

    //If there are no existing users matching
    return null;
  }

  public static boolean deleteUser (User user){
    // Check for DB Connection
    if (dbCon == null) {
      dbCon = new DatabaseController();
    }

    try {
      ResultSet res = dbCon.query("SELECT id FROM user where id = " + user.getId());
      if (res.next()){
        dbCon.update("DELETE FROM user where id = " + user.getId());
        UserCache.updateCache();
      } else {
        return false;
      }
    } catch (SQLException err) {
      err.printStackTrace();
      return false;
    }

    return true;

  }

  public static boolean updateUser (User user){
  }

}
