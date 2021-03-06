package cache;

import controllers.UserController;
import model.User;
import utils.Config;

import java.util.ArrayList;

public class UserCache {

  // List of users
  private static ArrayList<User> users = new ArrayList<>();

  // Time cache should live
  private static long ttl = Config.getUserTtl();

  // Sets when the cache has been created
  private static long created;

  public static ArrayList<User> getUsers(Boolean forceUpdate) {

    //If cache is disabled -> get data from database
    if (!Config.getCache()){
      return UserController.getUsers();
    }

    // If we wish to clear cache, we can set force update.
    // Otherwise we look at the age of the cache and figure out if we should update.
    // If the list is empty we also check for new users
    if (forceUpdate
            || ((created + ttl) <= (System.currentTimeMillis() / 1000L))
            || users.isEmpty()) {
      System.out.println("Updating User Cache");

      // Get users from controller, since we wish to update.
      users = UserController.getUsers();

      // Set created timestamp
      created = System.currentTimeMillis() / 1000L;
    }

    // Return the documents
    return users;
  }
  
  public static User getUser(int id){

    //If cache is disabled -> get data from database
    if (!Config.getCache()){
      return UserController.getUser(id);
    }

    for (User user :users) {
      if (user.getId() == id){
        return user;
      }
    }

    User nonCacheUser = UserController.getUser(id);
    if ( nonCacheUser != null){
      users.add(nonCacheUser);
      return nonCacheUser;
    }

    return null;
  }

  public static void updateCache(){
    ttl = Config.getUserTtl();

    // Get users from controller, since we wish to update.
    users = UserController.getUsers();

    // Set created timestamp
    created = System.currentTimeMillis() / 1000L;
    System.out.println("Updating User Cache");
  }

}
